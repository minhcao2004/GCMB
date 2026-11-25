package com.gcmb.backend.branch.repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
// Truy vấn cơ sở, phân công quản lý cơ sở và lịch sử thay đổi của cơ sở.
public class BranchRepository {

    // Khóa dùng chung với luồng phân quyền tài khoản để tránh gán người vừa bị thu hồi quyền.
    private static final long MANAGER_ASSIGNMENT_LOCK = 1300261013L;

    private static final String SELECT_BRANCH = """
        SELECT b.id AS "branchId", b.code, b.name AS "branchName", b.address, b.phone,
            b.notes, b.status, b.status_reason AS "statusReason",
            b.created_at AS "createdAt", b.updated_at AS "updatedAt",
            managers.names AS "managerName", managers.usernames AS "managerUsername",
            managers.codes AS "managerCode", managers.total AS "managerCount"
        FROM gcmb.branch b
        LEFT JOIN LATERAL (
            SELECT string_agg(COALESCE(e.full_name,u.username), ', ' ORDER BY u.username) AS names,
                string_agg(u.username, ', ' ORDER BY u.username) AS usernames,
                string_agg(e.code, ', ' ORDER BY u.username) AS codes, count(*) AS total
            FROM gcmb.branch_manager_assignment a
            JOIN gcmb.user_account u ON u.id=a.user_id
            LEFT JOIN gcmb.employee e ON e.user_id=u.id WHERE a.branch_id=b.id
        ) managers ON TRUE
        """;

    private final JdbcTemplate jdbcTemplate;

    public BranchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> findAll() {
        return jdbcTemplate.queryForList(SELECT_BRANCH + " ORDER BY b.name,b.id");
    }

    public List<Map<String, Object>> findById(UUID id) {
        return jdbcTemplate.queryForList(SELECT_BRANCH + " WHERE b.id=?", id);
    }

    public boolean codeExists(String code, UUID excludedId) {
        if (excludedId == null) {
            return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM gcmb.branch WHERE upper(code)=?)", Boolean.class, code));
        }
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM gcmb.branch WHERE upper(code)=? AND id<>?)", Boolean.class, code, excludedId));
    }

    public void insert(UUID id, String code, String name, String address, String phone, String notes) {
        jdbcTemplate.update("""
            INSERT INTO gcmb.branch(id,code,name,address,phone,notes,timezone,status,opened_on)
            VALUES (?,?,?,?,?,?,'Asia/Ho_Chi_Minh','ACTIVE',CURRENT_DATE)
            """, id, code, name, address, phone, notes);
    }

    public void update(UUID id, String code, String name, String address, String phone, String notes) {
        jdbcTemplate.update("""
            UPDATE gcmb.branch SET code=?,name=?,address=?,phone=?,notes=?,updated_at=CURRENT_TIMESTAMP
            WHERE id=?
            """, code, name, address, phone, notes, id);
    }

    public List<Map<String, Object>> findOpenWork(UUID branchId) {
        return jdbcTemplate.queryForList("""
            SELECT id,code,'BOOKING' AS kind,status FROM gcmb.booking
                WHERE branch_id=? AND status IN ('DRAFT','CONFIRMED','IN_HOUSE')
            UNION ALL
            SELECT a.id,b.code,'ROOM_SESSION',a.state FROM gcmb.room_allocation a
                JOIN gcmb.booking b ON b.id=a.booking_id
                WHERE a.branch_id=? AND a.state IN ('RESERVED','OCCUPIED')
            UNION ALL
            SELECT id,number,'BILL',status FROM gcmb.bill
                WHERE branch_id=? AND status IN ('DRAFT','ISSUED')
            ORDER BY kind,code
            """, branchId, branchId, branchId);
    }

    public void updateStatus(UUID id, String status, String reason) {
        jdbcTemplate.update("""
            UPDATE gcmb.branch SET status=?,status_reason=?,updated_at=CURRENT_TIMESTAMP,
                closed_on=CASE WHEN ?='CLOSED' THEN CURRENT_DATE ELSE closed_on END WHERE id=?
            """, status, reason, status, id);
    }

    public void reactivate(UUID id) {
        jdbcTemplate.update("""
            UPDATE gcmb.branch SET status='ACTIVE',status_reason='',updated_at=CURRENT_TIMESTAMP WHERE id=?
            """, id);
    }

    public List<Map<String, Object>> findManagerCandidates() {
        return jdbcTemplate.queryForList("""
            SELECT u.id AS "userId",u.username,u.email,COALESCE(e.full_name,u.username) AS "fullName",
                e.code AS "employeeCode", managed.names AS "branchName"
            FROM gcmb.user_account u JOIN gcmb.role r ON r.id=u.role_id
            LEFT JOIN gcmb.employee e ON e.user_id=u.id
            LEFT JOIN LATERAL (
                SELECT string_agg(b.name, ', ' ORDER BY b.name,b.id) AS names
                FROM gcmb.branch_manager_assignment a JOIN gcmb.branch b ON b.id=a.branch_id WHERE a.user_id=u.id
            ) managed ON TRUE
            WHERE u.status='ACTIVE' AND r.code='BRANCH_MANAGER'
                AND (e.id IS NULL OR e.status='ACTIVE')
            ORDER BY COALESCE(e.full_name,u.username),u.id
            """);
    }

    public void lockManagerAssignments() {
        jdbcTemplate.execute("SELECT pg_advisory_xact_lock(" + MANAGER_ASSIGNMENT_LOCK + ")");
    }

    public boolean lockActiveManagerAccount(UUID userId) {
        return !jdbcTemplate.queryForList("""
            SELECT u.id FROM gcmb.user_account u JOIN gcmb.role r ON r.id=u.role_id
            WHERE u.id=? AND u.status='ACTIVE' AND r.code='BRANCH_MANAGER'
                AND NOT EXISTS(SELECT 1 FROM gcmb.employee e WHERE e.user_id=u.id AND e.status<>'ACTIVE')
            FOR UPDATE OF u
            """, UUID.class, userId).isEmpty();
    }

    public List<UUID> findManagedBranchIds(UUID userId) {
        return jdbcTemplate.queryForList("SELECT branch_id FROM gcmb.branch_manager_assignment WHERE user_id=?",
            UUID.class, userId);
    }

    public int countManagers(UUID branchId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM gcmb.branch_manager_assignment WHERE branch_id=?", Integer.class, branchId);
        return count == null ? 0 : count;
    }

    public void touch(UUID branchId) {
        jdbcTemplate.update("UPDATE gcmb.branch SET updated_at=CURRENT_TIMESTAMP WHERE id=?", branchId);
    }

    public void insertManagerAssignment(UUID branchId, UUID userId, UUID actorId) {
        jdbcTemplate.update("INSERT INTO gcmb.branch_manager_assignment(branch_id,user_id,assigned_by) VALUES(?,?,?)",
            branchId, userId, actorId);
    }

    public void setDefaultBranchIfMissing(UUID userId, UUID branchId) {
        jdbcTemplate.update("UPDATE gcmb.user_account SET branch_id=COALESCE(branch_id,?) WHERE id=?", branchId, userId);
    }

    public void auditAssignManager(UUID actorId, UUID userId, String actorRole, UUID branchId) {
        jdbcTemplate.update("""
            INSERT INTO gcmb.audit_event(actor_id,branch_id,action,entity_type,entity_id,after_data)
            SELECT ?,b.id,'ASSIGN_BRANCH_MANAGER','BRANCH',b.id,
                jsonb_build_object('name',b.name,'managerId',CAST(? AS uuid),'actorRole',CAST(? AS text))
            FROM gcmb.branch b WHERE b.id=?
            """, actorId, userId, actorRole, branchId);
    }

    public String findRoleCode(UUID userId) {
        return jdbcTemplate.queryForObject(
            "SELECT r.code FROM gcmb.user_account u JOIN gcmb.role r ON r.id=u.role_id WHERE u.id=?", String.class, userId);
    }

    public void auditUnassignManager(UUID actorId, UUID userId, String actorRole, UUID branchId) {
        jdbcTemplate.update("""
            INSERT INTO gcmb.audit_event(actor_id,branch_id,action,entity_type,entity_id,before_data,after_data)
            SELECT ?,id,'UNASSIGN_BRANCH_MANAGER','BRANCH',id,
                jsonb_build_object('name',name,'managerId',CAST(? AS uuid)),
                jsonb_build_object('name',name,'actorRole',CAST(? AS text)) FROM gcmb.branch WHERE id=?
            """, actorId, userId, actorRole, branchId);
    }

    public void deleteManagerAssignment(UUID userId, UUID branchId) {
        jdbcTemplate.update("DELETE FROM gcmb.branch_manager_assignment WHERE user_id=? AND branch_id=?", userId, branchId);
    }

    public Map<String, Object> findAccountRole(UUID userId) {
        return jdbcTemplate.queryForMap("""
            SELECT u.branch_id,u.status,r.code FROM gcmb.user_account u
            JOIN gcmb.role r ON r.id=u.role_id WHERE u.id=?
            """, userId);
    }

    public void removeAllManagerAssignments(UUID actorId, UUID userId) {
        jdbcTemplate.update("""
            INSERT INTO gcmb.audit_event(actor_id,branch_id,action,entity_type,entity_id,before_data,after_data)
            SELECT ?,b.id,'UNASSIGN_BRANCH_MANAGER','BRANCH',b.id,
                jsonb_build_object('name',b.name,'managerId',a.user_id),
                jsonb_build_object('name',b.name,'managerId',NULL)
            FROM gcmb.branch_manager_assignment a JOIN gcmb.branch b ON b.id=a.branch_id WHERE a.user_id=?
            """, actorId, userId);
        jdbcTemplate.update("""
            UPDATE gcmb.branch SET updated_at=CURRENT_TIMESTAMP WHERE id IN
                (SELECT branch_id FROM gcmb.branch_manager_assignment WHERE user_id=?)
            """, userId);
        jdbcTemplate.update("DELETE FROM gcmb.branch_manager_assignment WHERE user_id=?", userId);
    }

    public List<Map<String, Object>> findCurrentStaff(UUID branchId) {
        return jdbcTemplate.queryForList("""
            SELECT DISTINCT e.id,e.code,e.full_name AS "fullName",e.status
            FROM gcmb.employee e JOIN gcmb.employee_assignment a ON a.employee_id=e.id
            WHERE a.branch_id=? AND a.valid_from<=CURRENT_TIMESTAMP
                AND (a.valid_to IS NULL OR a.valid_to>CURRENT_TIMESTAMP)
            ORDER BY e.code
            """, branchId);
    }

    public List<Map<String, Object>> findHistory(UUID branchId) {
        return jdbcTemplate.queryForList("""
            SELECT a.id,a.action,a.created_at AS "createdAt",COALESCE(e.full_name,u.username) AS actor,
                COALESCE(a.reason,CASE WHEN a.action='DEACTIVATE_BRANCH'
                    THEN a.after_data->>'status_reason' END,'') AS reason
            FROM gcmb.audit_event a LEFT JOIN gcmb.user_account u ON u.id=a.actor_id
            LEFT JOIN gcmb.employee e ON e.user_id=u.id
            WHERE a.entity_type='BRANCH' AND a.entity_id=? ORDER BY a.created_at DESC,a.id
            """, branchId);
    }

    public boolean lockById(UUID id) {
        return !jdbcTemplate.queryForList("SELECT id FROM gcmb.branch WHERE id=? FOR UPDATE", UUID.class, id).isEmpty();
    }
}
