/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 14/10/2026
 * Description: Quản lý cơ sở, phân công quản lý và kiểm tra điều kiện ngừng vận hành.
 */
package com.gcmb.backend.branch.service;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.branch.dto.BranchDtos.BranchCommand;
import com.gcmb.backend.branch.dto.BranchDtos.StatusCommand;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BranchAdministrationService {
    private final JdbcTemplate db;

    private static final String SELECT = """
        SELECT b.id AS "branchId", b.code, b.name AS "branchName", b.address, b.phone,
            b.notes, b.status, b.status_reason AS "statusReason",
            b.created_at AS "createdAt", b.updated_at AS "updatedAt",
            u.id AS "managerId", u.username AS "managerUsername",
            COALESCE(e.full_name,u.username) AS "managerName", e.code AS "managerCode"
        FROM gcmb.branch b
        LEFT JOIN gcmb.branch_manager_assignment a ON a.branch_id=b.id
        LEFT JOIN gcmb.user_account u ON u.id=a.user_id
        LEFT JOIN gcmb.employee e ON e.user_id=u.id
        """;

    public BranchAdministrationService(JdbcTemplate db) {
        this.db = db;
    }

    public List<Map<String, Object>> list() {
        return db.queryForList(SELECT + " ORDER BY b.name,b.id");
    }

    public Map<String, Object> get(UUID id) {
        var rows = db.queryForList(SELECT + " WHERE b.id=?", id);
        if (rows.isEmpty()) {
            throw failure(HttpStatus.NOT_FOUND, "Không tìm thấy cơ sở.");
        }
        return rows.get(0);
    }

    @Transactional
    public Map<String, Object> create(BranchCommand input) {
        validate(input);
        if (Boolean.TRUE.equals(db.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM gcmb.branch WHERE upper(code)=?)", Boolean.class,
                input.code().trim().toUpperCase(Locale.ROOT)))) {
            throw failure(HttpStatus.CONFLICT, "Mã cơ sở đã tồn tại.");
        }
        UUID id = UUID.randomUUID();
        try {
            db.update("""
                INSERT INTO gcmb.branch(id,code,name,address,phone,notes,timezone,status,opened_on)
                VALUES (?,?,?,?,?,?,'Asia/Ho_Chi_Minh','ACTIVE',CURRENT_DATE)
                """, id, input.code().trim().toUpperCase(Locale.ROOT), input.name().trim(),
                input.address().trim(), clean(input.phone()), clean(input.notes()));
        } catch (DataIntegrityViolationException e) {
            throw failure(HttpStatus.CONFLICT, "Mã cơ sở đã tồn tại hoặc dữ liệu không hợp lệ.");
        }
        return get(id);
    }

    @Transactional
    public Map<String, Object> update(UUID id, BranchCommand input) {
        validate(input);
        lock(id);
        String code = input.code().trim().toUpperCase(Locale.ROOT);
        if (Boolean.TRUE.equals(db.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM gcmb.branch WHERE upper(code)=? AND id<>?)", Boolean.class, code, id))) {
            throw failure(HttpStatus.CONFLICT, "Mã cơ sở đã tồn tại.");
        }
        try {
            db.update("""
                UPDATE gcmb.branch SET code=?,name=?,address=?,phone=?,notes=?,updated_at=CURRENT_TIMESTAMP
                WHERE id=?
                """, code, input.name().trim(), input.address().trim(), clean(input.phone()), clean(input.notes()), id);
        } catch (DataIntegrityViolationException e) {
            throw failure(HttpStatus.CONFLICT, "Mã cơ sở đã tồn tại hoặc dữ liệu không hợp lệ.");
        }
        return get(id);
    }

    // Danh sách này dùng cho cả màn kiểm tra và kiểm tra lại trong giao dịch xác nhận.
    public List<Map<String, Object>> blockers(UUID id) {
        get(id);
        return db.queryForList("""
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
            """, id, id, id);
    }

    @Transactional
    public Map<String, Object> deactivate(UUID id, StatusCommand input) {
        if (input == null || input.status() == null
                || !List.of("SUSPENDED", "CLOSED").contains(input.status())) {
            throw failure(HttpStatus.BAD_REQUEST, "Chọn Tạm ngừng hoặc Đã đóng.");
        }
        if (clean(input.reason()).isEmpty() || clean(input.reason()).length() > 2000) {
            throw failure(HttpStatus.BAD_REQUEST, "Nhập lý do thay đổi trạng thái (tối đa 2000 ký tự).");
        }
        lock(id);
        String current = (String) get(id).get("status");
        if (current.equals(input.status())) {
            return get(id);
        }
        if (current.equals("CLOSED")) {
            throw failure(HttpStatus.CONFLICT, "Cơ sở đã đóng, không thể chuyển trạng thái này.");
        }
        if (!blockers(id).isEmpty()) {
            throw failure(HttpStatus.CONFLICT,
                "Còn booking, phiên sử dụng hoặc hóa đơn chưa xử lý. Chọn Kiểm tra cơ sở trước khi ngừng hoạt động.");
        }
        db.update("""
            UPDATE gcmb.branch SET status=?,status_reason=?,updated_at=CURRENT_TIMESTAMP,
                closed_on=CASE WHEN ?='CLOSED' THEN CURRENT_DATE ELSE closed_on END WHERE id=?
            """, input.status(), input.reason().trim(), input.status(), id);
        return get(id);
    }

    @Transactional
    public Map<String, Object> reactivate(UUID id) {
        lock(id);
        String current = (String) get(id).get("status");
        if ("ACTIVE".equals(current)) return get(id);
        if (!"SUSPENDED".equals(current)) {
            throw failure(HttpStatus.CONFLICT, "Chỉ mở lại cơ sở đang tạm ngừng.");
        }
        db.update("""
            UPDATE gcmb.branch SET status='ACTIVE',status_reason='',updated_at=CURRENT_TIMESTAMP WHERE id=?
            """, id);
        return get(id);
    }

    public List<Map<String, Object>> managers() {
        return db.queryForList("""
            SELECT u.id AS "userId",u.username,u.email,COALESCE(e.full_name,u.username) AS "fullName",
                e.code AS "employeeCode",b.id AS "branchId",b.name AS "branchName"
            FROM gcmb.user_account u JOIN gcmb.role r ON r.id=u.role_id
            LEFT JOIN gcmb.employee e ON e.user_id=u.id
            LEFT JOIN gcmb.branch_manager_assignment a ON a.user_id=u.id
            LEFT JOIN gcmb.branch b ON b.id=a.branch_id
            WHERE u.status='ACTIVE' AND r.code='BRANCH_MANAGER'
                AND (e.id IS NULL OR e.status='ACTIVE')
            ORDER BY COALESCE(e.full_name,u.username),u.id
            """);
    }

    @Transactional
    public Map<String, Object> assign(UUID branchId, UUID userId, Identity actor) {
        return assign(branchId, userId, actor.id(), actor.role());
    }

    private Map<String, Object> assign(UUID branchId, UUID userId, UUID actorId, String actorRole) {
        if (userId == null) {
            throw failure(HttpStatus.BAD_REQUEST, "Chọn tài khoản quản lý cơ sở.");
        }
        // Dùng cùng khóa với phân quyền tài khoản, tránh gán người vừa bị thu hồi quyền.
        db.execute("SELECT pg_advisory_xact_lock(1300261013)");
        var users = db.queryForList("""
            SELECT u.id FROM gcmb.user_account u JOIN gcmb.role r ON r.id=u.role_id
            WHERE u.id=? AND u.status='ACTIVE' AND r.code='BRANCH_MANAGER'
                AND NOT EXISTS(SELECT 1 FROM gcmb.employee e WHERE e.user_id=u.id AND e.status<>'ACTIVE')
            FOR UPDATE OF u
            """, UUID.class, userId);
        if (users.isEmpty()) {
            throw failure(HttpStatus.CONFLICT, "Quản lý phải có tài khoản đang hoạt động và vai trò Quản lý cơ sở.");
        }
        lock(branchId);
        if (!"ACTIVE".equals(get(branchId).get("status"))) {
            throw failure(HttpStatus.CONFLICT, "Chỉ gán quản lý cho cơ sở đang hoạt động.");
        }
        if (userId.equals(get(branchId).get("managerId"))) {
            db.update("UPDATE gcmb.user_account SET branch_id=? WHERE id=?", branchId, userId);
            return get(branchId);
        }
        // Chuyển quản lý phải ghi cả cơ sở được gán và cơ sở mất phân công trước đó.
        db.update("""
            INSERT INTO gcmb.audit_event(actor_id,branch_id,action,entity_type,entity_id,before_data,after_data)
            SELECT ?,b.id,'ASSIGN_BRANCH_MANAGER','BRANCH',b.id,
                jsonb_build_object('name',b.name,'managerId',a.user_id),
                jsonb_build_object('name',b.name,'managerId',CASE WHEN b.id=? THEN CAST(? AS uuid) ELSE NULL END,
                    'actorRole',CAST(? AS text))
            FROM gcmb.branch b LEFT JOIN gcmb.branch_manager_assignment a ON a.branch_id=b.id
            WHERE (b.id=? OR a.user_id=?) AND a.user_id IS DISTINCT FROM
                CASE WHEN b.id=? THEN CAST(? AS uuid) ELSE NULL END
            """, actorId, branchId, userId, actorRole, branchId, userId, branchId, userId);
        // Xóa phạm vi dự phòng cũ để quản lý bị thay không còn quyền vào cơ sở cũ.
        db.update("""
            UPDATE gcmb.user_account SET branch_id=NULL WHERE id IN
                (SELECT user_id FROM gcmb.branch_manager_assignment WHERE branch_id=? OR user_id=?)
                OR (branch_id=? AND role_id=(SELECT id FROM gcmb.role WHERE code='BRANCH_MANAGER'))
            """, branchId, userId, branchId);
        db.update("""
            UPDATE gcmb.branch SET updated_at=CURRENT_TIMESTAMP WHERE id=? OR id IN
                (SELECT branch_id FROM gcmb.branch_manager_assignment WHERE user_id=?)
            """, branchId, userId);
        db.update("DELETE FROM gcmb.branch_manager_assignment WHERE branch_id=? OR user_id=?", branchId, userId);
        db.update("INSERT INTO gcmb.branch_manager_assignment(branch_id,user_id,assigned_by) VALUES(?,?,?)",
            branchId, userId, actorId);
        db.update("UPDATE gcmb.user_account SET branch_id=? WHERE id=?", branchId, userId);
        return get(branchId);
    }

    // Hai màn tài khoản và cơ sở dùng chung một luồng phân công trong cùng giao dịch.
    @Transactional
    public void syncAccountManager(UUID userId, UUID actorId) {
        db.execute("SELECT pg_advisory_xact_lock(1300261013)");
        var account = db.queryForMap("""
            SELECT u.branch_id,u.status,r.code FROM gcmb.user_account u
            JOIN gcmb.role r ON r.id=u.role_id WHERE u.id=?
            """, userId);
        if ("BRANCH_MANAGER".equals(account.get("code")) && !"REVOKED".equals(account.get("status"))
                && account.get("branch_id") != null) {
            String actorRole = db.queryForObject("""
                SELECT r.code FROM gcmb.user_account u JOIN gcmb.role r ON r.id=u.role_id WHERE u.id=?
                """, String.class, actorId);
            assign((UUID) account.get("branch_id"), userId, actorId, actorRole);
            return;
        }
        db.update("""
            INSERT INTO gcmb.audit_event(actor_id,branch_id,action,entity_type,entity_id,before_data,after_data)
            SELECT ?,b.id,'UNASSIGN_BRANCH_MANAGER','BRANCH',b.id,
                jsonb_build_object('name',b.name,'managerId',a.user_id),
                jsonb_build_object('name',b.name,'managerId',NULL)
            FROM gcmb.branch_manager_assignment a JOIN gcmb.branch b ON b.id=a.branch_id WHERE a.user_id=?
            """, actorId, userId);
        db.update("""
            UPDATE gcmb.branch SET updated_at=CURRENT_TIMESTAMP WHERE id IN
                (SELECT branch_id FROM gcmb.branch_manager_assignment WHERE user_id=?)
            """, userId);
        db.update("DELETE FROM gcmb.branch_manager_assignment WHERE user_id=?", userId);
    }

    public List<Map<String, Object>> staff(UUID id) {
        get(id);
        return db.queryForList("""
            SELECT DISTINCT e.id,e.code,e.full_name AS "fullName",e.status
            FROM gcmb.employee e JOIN gcmb.employee_assignment a ON a.employee_id=e.id
            WHERE a.branch_id=? AND a.valid_from<=CURRENT_TIMESTAMP
                AND (a.valid_to IS NULL OR a.valid_to>CURRENT_TIMESTAMP)
            ORDER BY e.code
            """, id);
    }

    public List<Map<String, Object>> history(UUID id) {
        get(id);
        return db.queryForList("""
            SELECT a.id,a.action,a.created_at AS "createdAt",COALESCE(e.full_name,u.username) AS actor,
                COALESCE(a.reason,CASE WHEN a.action='DEACTIVATE_BRANCH'
                    THEN a.after_data->>'status_reason' END,'') AS reason
            FROM gcmb.audit_event a LEFT JOIN gcmb.user_account u ON u.id=a.actor_id
            LEFT JOIN gcmb.employee e ON e.user_id=u.id
            WHERE a.entity_type='BRANCH' AND a.entity_id=? ORDER BY a.created_at DESC,a.id
            """, id);
    }

    private void lock(UUID id) {
        if (db.queryForList("SELECT id FROM gcmb.branch WHERE id=? FOR UPDATE", UUID.class, id).isEmpty()) {
            throw failure(HttpStatus.NOT_FOUND, "Không tìm thấy cơ sở.");
        }
    }

    private static void validate(BranchCommand input) {
        if (input == null || !clean(input.code()).toUpperCase(Locale.ROOT).matches("[A-Z0-9_-]{2,60}")) {
            throw failure(HttpStatus.BAD_REQUEST, "Mã cơ sở gồm 2–60 chữ cái, số, gạch ngang/gạch dưới; không có khoảng trắng.");
        }
        if (clean(input.name()).isEmpty() || clean(input.name()).length() > 200) {
            throw failure(HttpStatus.BAD_REQUEST, "Nhập tên cơ sở (tối đa 200 ký tự).");
        }
        if (clean(input.address()).isEmpty() || clean(input.address()).length() > 2000) {
            throw failure(HttpStatus.BAD_REQUEST, "Nhập địa chỉ cơ sở (tối đa 2000 ký tự).");
        }
        if (!clean(input.phone()).isEmpty() && !clean(input.phone()).matches("0[0-9]{6,14}")) {
            throw failure(HttpStatus.BAD_REQUEST, "Số điện thoại phải bắt đầu bằng 0 và gồm 7–15 chữ số, không chứa dấu hoặc khoảng trắng.");
        }
        if (clean(input.notes()).length() > 4000) {
            throw failure(HttpStatus.BAD_REQUEST, "Ghi chú tối đa 4000 ký tự.");
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static ResponseStatusException failure(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
