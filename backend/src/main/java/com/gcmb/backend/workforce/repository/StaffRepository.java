/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 26/10/2026
 * Updated: 27/10/2026
 * Description: Lưu hồ sơ nhân viên, lịch sử cơ sở và nhật ký thay đổi.
 */

package com.gcmb.backend.workforce.repository;

import com.gcmb.backend.workforce.dto.StaffDtos.StaffCommand;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class StaffRepository {

    private final JdbcTemplate db;
    private final ObjectMapper json;

    public StaffRepository(JdbcTemplate db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    private static final String FROM = """
        FROM gcmb.employee e
        LEFT JOIN gcmb.user_account u ON u.id=e.user_id
        LEFT JOIN gcmb.role r ON r.id=u.role_id
        LEFT JOIN LATERAL (SELECT a.* FROM gcmb.employee_assignment a
            WHERE a.employee_id=e.id AND a.valid_from<=clock_timestamp()
              AND (a.valid_to IS NULL OR a.valid_to>clock_timestamp())
            ORDER BY a.valid_from DESC LIMIT 1) home ON true
        LEFT JOIN gcmb.branch b ON b.id=home.branch_id
        """;
    private static final String SELECT =
        """
        SELECT e.id,e.code,e.full_name AS "fullName",e.phone,e.position,
          e.contact_email AS "contactEmail",e.identity_number AS "identityNumber",e.address,e.notes,
          e.hired_on::text AS "hiredOn",e.resigned_on::text AS "resignedOn",e.status,e.version,
          e.user_id AS "userId",u.username,u.status AS "accountStatus",r.code AS "accountRole",
          home.branch_id AS "branchId",b.name AS "branchName",b.code AS "branchCode",b.status AS "branchStatus",
          (SELECT string_agg(mb.name, ', ' ORDER BY mb.name) FROM gcmb.branch_manager_assignment ma
              JOIN gcmb.branch mb ON mb.id=ma.branch_id WHERE ma.user_id=e.user_id) AS "managedBranches"
        """ + FROM;

    public Map<String, Object> find(UUID id) {
        var rows = db.queryForList(SELECT + " WHERE e.id=?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, Object> list(
        String q,
        String position,
        String status,
        UUID branch,
        boolean unassigned,
        int page,
        int size
    ) {
        String where =
            " WHERE (?='' OR strpos(lower(e.full_name),lower(?))>0 OR strpos(lower(e.code),lower(?))>0 OR strpos(COALESCE(e.phone,''),?)>0)" +
            " AND (?='' OR e.position=?) AND (?='' OR e.status=?)";
        var args = new ArrayList<Object>(
            Arrays.asList(q, q, q, q, position, position, status, status)
        );
        if (branch != null) {
            where += " AND home.branch_id=?";
            args.add(branch);
        }
        if (unassigned) where += " AND home.branch_id IS NULL";
        long total = db.queryForObject(
            "SELECT count(*) " + FROM + where,
            Long.class,
            args.toArray()
        );
        args.add(size);
        args.add((long) page * size);
        // List results deliberately omit identification numbers, bank fields and account identifiers.
        var rows = db.queryForList(
            "SELECT e.id,e.code,e.full_name AS \"fullName\",e.phone,e.position,e.status,b.name AS \"branchName\",home.branch_id AS \"branchId\" " +
                FROM +
                where +
                " ORDER BY e.full_name,e.id LIMIT ? OFFSET ?",
            args.toArray()
        );
        return Map.of("items", rows, "total", total, "page", page, "size", size);
    }

    public List<Map<String, Object>> branches(UUID scope) {
        return scope == null
            ? db.queryForList("SELECT id,code,name,status FROM gcmb.branch ORDER BY name,id")
            : db.queryForList("SELECT id,code,name,status FROM gcmb.branch WHERE id=?", scope);
    }

    public void serialize() {
        db.execute("SELECT pg_advisory_xact_lock(1300261013)");
    }

    public boolean actorActive(UUID id, String role) {
        return Boolean.TRUE.equals(
            db.queryForObject(
                """
            SELECT EXISTS(SELECT 1 FROM gcmb.user_account u JOIN gcmb.role r ON r.id=u.role_id
                WHERE u.id=? AND u.status='ACTIVE' AND r.code=?)
            """,
                Boolean.class,
                id,
                role
            )
        );
    }

    public boolean lock(UUID id) {
        return !db
            .queryForList("SELECT id FROM gcmb.employee WHERE id=? FOR UPDATE", UUID.class, id)
            .isEmpty();
    }

    public boolean activeBranch(UUID id) {
        return !db
            .queryForList(
                "SELECT id FROM gcmb.branch WHERE id=? AND status='ACTIVE' FOR SHARE",
                UUID.class,
                id
            )
            .isEmpty();
    }

    public String branchName(UUID id) {
        var names = db.queryForList("SELECT name FROM gcmb.branch WHERE id=?", String.class, id);
        return names.isEmpty() ? id.toString() : names.get(0);
    }

    public boolean duplicate(String field, String value, UUID except) {
        if (
            !Set.of("code", "identity_number").contains(field)
        ) throw new IllegalArgumentException();
        return Boolean.TRUE.equals(
            db.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM gcmb.employee WHERE " +
                    field +
                    "=? AND id IS DISTINCT FROM CAST(? AS uuid))",
                Boolean.class,
                value,
                except
            )
        );
    }

    public void create(UUID id, StaffCommand c) {
        db.update(
            """
            INSERT INTO gcmb.employee(id,code,full_name,phone,position,contact_email,identity_number,address,notes,hired_on,status)
            VALUES (?,?,?,?,?,?,?,?,?,?,'ACTIVE')
            """,
            id,
            c.code(),
            c.fullName(),
            c.phone(),
            c.position(),
            c.contactEmail(),
            c.identityNumber(),
            c.address(),
            c.notes(),
            c.hiredOn()
        );
    }

    public void update(UUID id, StaffCommand c) {
        db.update(
            """
            UPDATE gcmb.employee SET code=?,full_name=?,phone=?,position=?,contact_email=?,identity_number=?,address=?,notes=?,
                hired_on=?,resigned_on=?,version=version+1 WHERE id=?
            """,
            c.code(),
            c.fullName(),
            c.phone(),
            c.position(),
            c.contactEmail(),
            c.identityNumber(),
            c.address(),
            c.notes(),
            c.hiredOn(),
            c.resignedOn(),
            id
        );
    }

    public void status(UUID id, String status, java.time.LocalDate resigned) {
        db.update(
            "UPDATE gcmb.employee SET status=?,resigned_on=?,version=version+1 WHERE id=?",
            status,
            resigned,
            id
        );
    }

    public void assignment(UUID employee, UUID branch, UUID actor, String reason) {
        // UC-81/82: close old interval and create new one atomically, preserving history.
        Instant at = db.queryForObject(
            """
            SELECT GREATEST(clock_timestamp(),COALESCE(max(valid_from)+interval '1 microsecond',clock_timestamp()))
            FROM gcmb.employee_assignment WHERE employee_id=?
            """,
            (rs, n) -> rs.getTimestamp(1).toInstant(),
            employee
        );
        db.update(
            "UPDATE gcmb.employee_assignment SET valid_to=? WHERE employee_id=? AND valid_to IS NULL",
            Timestamp.from(at),
            employee
        );
        db.update(
            """
            INSERT INTO gcmb.employee_assignment(employee_id,branch_id,valid_from,is_branch_manager,assigned_by,reason)
            VALUES (?,?,?,false,?,?)
            """,
            employee,
            branch,
            Timestamp.from(at),
            actor,
            reason
        );
        db.update("UPDATE gcmb.employee SET version=version+1 WHERE id=?", employee);
    }

    public boolean futureAssignment(UUID employee) {
        return Boolean.TRUE.equals(
            db.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM gcmb.employee_assignment WHERE employee_id=? AND (valid_from>clock_timestamp() OR valid_to>clock_timestamp()))",
                Boolean.class,
                employee
            )
        );
    }

    public void moveReceptionAccess(UUID user, UUID branch) {
        // Home branch follows the receptionist; management responsibilities remain UC-12.
        db.update(
            """
            UPDATE gcmb.user_account u SET branch_id=? FROM gcmb.role r
            WHERE u.id=? AND u.role_id=r.id AND r.code='RECEPTIONIST'
            """,
            branch,
            user
        );
        db.update(
            "UPDATE gcmb.auth_token SET revoked_at=CURRENT_TIMESTAMP WHERE user_id=? AND revoked_at IS NULL",
            user
        );
    }

    public List<Map<String, Object>> history(UUID employee) {
        return db.queryForList(
            """
            SELECT h.id,h.valid_from AS "createdAt",h.valid_to AS "validTo",h.reason,b.name AS "branchName",b.code AS "branchCode",
              lag(b.name) OVER (ORDER BY h.valid_from,h.id) AS "previousBranchName",
              COALESCE(a.full_name,u.username,'Hệ thống') AS actor
            FROM gcmb.employee_assignment h JOIN gcmb.branch b ON b.id=h.branch_id
            LEFT JOIN gcmb.user_account u ON u.id=h.assigned_by LEFT JOIN gcmb.employee a ON a.user_id=u.id
            WHERE h.employee_id=? ORDER BY h.valid_from DESC,h.id DESC
            """,
            employee
        );
    }

    public void audit(
        UUID actor,
        UUID employee,
        String action,
        Map<String, Object> before,
        Map<String, Object> after,
        String reason
    ) {
        db.update(
            "INSERT INTO gcmb.audit_event(actor_id,branch_id,action,entity_type,entity_id,before_data,after_data,reason) VALUES (?,?,?,'EMPLOYEE',?,CAST(? AS jsonb),CAST(? AS jsonb),?)",
            actor,
            after.get("branchId"),
            action,
            employee,
            before == null ? null : json.writeValueAsString(before),
            json.writeValueAsString(after),
            reason
        );
    }
}
