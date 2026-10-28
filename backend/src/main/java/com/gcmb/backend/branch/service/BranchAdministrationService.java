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
    // BR-136: mã cơ sở phải duy nhất trong toàn hệ thống.
    public Map<String, Object> create(BranchCommand input) {
        validate(input);
        if (Boolean.TRUE.equals(db.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM gcmb.branch WHERE upper(code)=?)", Boolean.class,
                input.code().trim().toUpperCase(Locale.ROOT)))) {
            throw failure(HttpStatus.CONFLICT, "Mã cơ sở “" + input.code().trim().toUpperCase(Locale.ROOT) + "” đã tồn tại trong hệ thống. Vui lòng nhập mã khác.");
        }
        UUID id = UUID.randomUUID();
        try {
            db.update("""
                INSERT INTO gcmb.branch(id,code,name,address,phone,notes,timezone,status,opened_on)
                VALUES (?,?,?,?,?,?,'Asia/Ho_Chi_Minh','ACTIVE',CURRENT_DATE)
                """, id, input.code().trim().toUpperCase(Locale.ROOT), input.name().trim(),
                input.address().trim(), clean(input.phone()), clean(input.notes()));
        } catch (DataIntegrityViolationException e) {
            throw failure(HttpStatus.CONFLICT, "Mã cơ sở “" + input.code().trim().toUpperCase(Locale.ROOT) + "” đã tồn tại trong hệ thống. Vui lòng nhập mã khác.");
        }
        return get(id);
    }

    @Transactional
    // BR-136: cập nhật mã nhưng vẫn phải giữ duy nhất giữa các cơ sở.
    public Map<String, Object> update(UUID id, BranchCommand input) {
        validate(input);
        lock(id);
        String code = input.code().trim().toUpperCase(Locale.ROOT);
        if (Boolean.TRUE.equals(db.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM gcmb.branch WHERE upper(code)=? AND id<>?)", Boolean.class, code, id))) {
            throw failure(HttpStatus.CONFLICT, "Mã cơ sở “" + code + "” đã tồn tại trong hệ thống. Vui lòng nhập mã khác.");
        }
        try {
            db.update("""
                UPDATE gcmb.branch SET code=?,name=?,address=?,phone=?,notes=?,updated_at=CURRENT_TIMESTAMP
                WHERE id=?
                """, code, input.name().trim(), input.address().trim(), clean(input.phone()), clean(input.notes()), id);
        } catch (DataIntegrityViolationException e) {
            throw failure(HttpStatus.CONFLICT, "Mã cơ sở “" + code + "” đã tồn tại trong hệ thống. Vui lòng nhập mã khác.");
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
    // BR-149: không chuyển trạng thái cơ sở khi còn booking, phiên sử dụng hoặc bill chưa xử lý.
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
            String branchName = (String) get(id).get("branchName");
            throw failure(HttpStatus.CONFLICT,
                "Chưa thể ngừng hoạt động cơ sở " + branchName + " vì còn booking chờ nhận phòng, khách đang sử dụng phòng hoặc bill chưa thanh toán. Vui lòng xử lý các mục còn tồn tại.");
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
        var current = db.queryForList("SELECT branch_id FROM gcmb.branch_manager_assignment WHERE user_id=?", UUID.class, userId);
        if (current.contains(branchId)) return get(branchId);
        Integer count = db.queryForObject("SELECT count(*) FROM gcmb.branch_manager_assignment WHERE branch_id=?", Integer.class, branchId);
        if (count != null && count >= 2) {
            throw failure(HttpStatus.CONFLICT, "Cơ sở đã có đủ 2 quản lý. Hãy chuyển hoặc thu hồi phân công một quản lý trước.");
        }
        // Thêm phân công, giữ nguyên các cơ sở người này đang quản lý.
        db.update("UPDATE gcmb.branch SET updated_at=CURRENT_TIMESTAMP WHERE id=?", branchId);
        db.update("INSERT INTO gcmb.branch_manager_assignment(branch_id,user_id,assigned_by) VALUES(?,?,?)",
            branchId, userId, actorId);
        db.update("UPDATE gcmb.user_account SET branch_id=COALESCE(branch_id,?) WHERE id=?", branchId, userId);
        db.update("""
            INSERT INTO gcmb.audit_event(actor_id,branch_id,action,entity_type,entity_id,after_data)
            SELECT ?,b.id,'ASSIGN_BRANCH_MANAGER','BRANCH',b.id,
                jsonb_build_object('name',b.name,'managerId',CAST(? AS uuid),'actorRole',CAST(? AS text))
            FROM gcmb.branch b WHERE b.id=?
            """, actorId, userId, actorRole, branchId);
        return get(branchId);
    }

    @Transactional
    public void replaceAccountManagers(UUID userId, List<UUID> branchIds, UUID actorId) {
        db.execute("SELECT pg_advisory_xact_lock(1300261013)");
        String actorRole = db.queryForObject("SELECT r.code FROM gcmb.user_account u JOIN gcmb.role r ON r.id=u.role_id WHERE u.id=?", String.class, actorId);
        for (UUID branchId : branchIds) assign(branchId, userId, actorId, actorRole);
        var current = db.queryForList("SELECT branch_id FROM gcmb.branch_manager_assignment WHERE user_id=?", UUID.class, userId);
        for (UUID branchId : current) {
            if (branchIds.contains(branchId)) continue;
            db.update("""
                INSERT INTO gcmb.audit_event(actor_id,branch_id,action,entity_type,entity_id,before_data,after_data)
                SELECT ?,id,'UNASSIGN_BRANCH_MANAGER','BRANCH',id,
                    jsonb_build_object('name',name,'managerId',CAST(? AS uuid)),
                    jsonb_build_object('name',name,'actorRole',CAST(? AS text)) FROM gcmb.branch WHERE id=?
                """, actorId, userId, actorRole, branchId);
            db.update("DELETE FROM gcmb.branch_manager_assignment WHERE user_id=? AND branch_id=?", userId, branchId);
            db.update("UPDATE gcmb.branch SET updated_at=CURRENT_TIMESTAMP WHERE id=?", branchId);
        }
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

    // BR-111, BR-135, BR-179–181: kiểm tra trường bắt buộc, định dạng mã và độ dài thông tin cơ sở.
    private static void validate(BranchCommand input) {
        if (input == null) throw failure(HttpStatus.BAD_REQUEST, "Vui lòng bổ sung thông tin cơ sở.");
        if (clean(input.code()).isEmpty()) throw failure(HttpStatus.BAD_REQUEST, "Vui lòng bổ sung mã cơ sở.");
        if (!clean(input.code()).toUpperCase(Locale.ROOT).matches("[A-Z0-9_-]{2,60}")) {
            throw failure(HttpStatus.BAD_REQUEST, "Mã cơ sở phải có từ 2 đến 60 ký tự và chỉ được dùng chữ cái không dấu, chữ số, dấu gạch dưới (_) hoặc dấu gạch nối (-).");
        }
        if (clean(input.name()).isEmpty()) throw failure(HttpStatus.BAD_REQUEST, "Vui lòng bổ sung tên cơ sở.");
        if (clean(input.name()).length() > 200) {
            throw failure(HttpStatus.BAD_REQUEST, "Tên cơ sở chỉ được chứa tối đa 200 ký tự.");
        }
        if (clean(input.address()).isEmpty()) throw failure(HttpStatus.BAD_REQUEST, "Vui lòng bổ sung địa chỉ cơ sở.");
        if (clean(input.address()).length() > 2000) {
            throw failure(HttpStatus.BAD_REQUEST, "Địa chỉ cơ sở chỉ được chứa tối đa 2000 ký tự.");
        }
        com.gcmb.backend.auth.service.AuthValidation.phone(input.phone());
        if (clean(input.notes()).length() > 4000) {
            throw failure(HttpStatus.BAD_REQUEST, "Ghi chú chỉ được chứa tối đa 4000 ký tự.");
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static ResponseStatusException failure(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
