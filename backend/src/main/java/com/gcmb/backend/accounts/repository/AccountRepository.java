/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: Truy vấn tài khoản và dữ liệu lựa chọn; lưu tài khoản, liên kết hồ sơ nhân viên và ghi nhật ký thao tác bằng JDBC.
 */

package com.gcmb.backend.accounts.repository;

import com.gcmb.backend.accounts.dto.AccountDtos.*;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {
    private final JdbcTemplate db;

    public AccountRepository(JdbcTemplate db) {
        this.db = db;
    }
    private static final String JOINS = """
        FROM gcmb.user_account u JOIN gcmb.role r ON r.id=u.role_id
        LEFT JOIN gcmb.employee e ON e.user_id=u.id
        LEFT JOIN gcmb.employee_assignment a ON a.employee_id=e.id AND a.valid_from<=CURRENT_TIMESTAMP
          AND (a.valid_to IS NULL OR a.valid_to>CURRENT_TIMESTAMP)
        LEFT JOIN gcmb.branch b ON b.id=COALESCE(u.branch_id,a.branch_id)
        """;
    private static final String BRANCH = "CASE WHEN r.code IN ('HEAD_OFFICE_MANAGER','ACCOUNTANT') THEN NULL ELSE b.id END";
    private static final String SELECT = "SELECT u.id, COALESCE(e.full_name,u.username) AS employee_name, e.code AS employee_code, "
            + "u.username,u.email,r.code AS role," + BRANCH + " AS branch_id, CASE WHEN r.code IN ('HEAD_OFFICE_MANAGER','ACCOUNTANT') THEN 'Toàn chuỗi' ELSE "
            + "COALESCE(CASE WHEN r.code='BRANCH_MANAGER' THEN "
            + "(SELECT string_agg(mb.name, ', ' ORDER BY mb.name,mb.id) FROM gcmb.branch_manager_assignment ma "
            + "JOIN gcmb.branch mb ON mb.id=ma.branch_id WHERE ma.user_id=u.id) ELSE b.name END,'Chưa phân công') END AS branch_name,u.status, "
            + "ARRAY(SELECT ma.branch_id FROM gcmb.branch_manager_assignment ma WHERE ma.user_id=u.id ORDER BY ma.branch_id) AS managed_branch_ids " + JOINS;

    private static AccountView row(java.sql.ResultSet rs, int index) throws java.sql.SQLException {
        return new AccountView(
            rs.getObject("id", UUID.class),
            rs.getString("employee_name"),
            rs.getString("employee_code"),
            rs.getString("username"),
            rs.getString("email"),
            rs.getString("role"),
            rs.getObject("branch_id", UUID.class),
            rs.getString("branch_name"),
            rs.getString("status"),
            java.util.Arrays.asList((UUID[]) rs.getArray("managed_branch_ids").getArray())
        );
    }

    public AccountPage list(String q, String role, UUID branch, String status, int page, int size) {
        String where = " WHERE (strpos(lower(u.username || ' ' || u.email || ' ' || "
                + "COALESCE(e.full_name,'')),lower(?))>0) AND (?='' OR r.code=?) AND (?='' OR u.status=?)";
        var args = new java.util.ArrayList<Object>(List.of(q, role, role, status, status));
        if (branch != null) {
            where += " AND (CASE WHEN r.code='BRANCH_MANAGER' THEN EXISTS(SELECT 1 FROM gcmb.branch_manager_assignment ma WHERE ma.user_id=u.id AND ma.branch_id=?) ELSE " + BRANCH + "=? END)";
            args.add(branch);
            args.add(branch);
        }
        long total = Objects.requireNonNull(
                db.queryForObject("SELECT count(*) " + JOINS + where, Long.class, args.toArray()),
                "Account count query returned null"
        );
        args.add(size);
        args.add((long) page * size);
        return new AccountPage(
            db.query(
                SELECT + where + " ORDER BY u.created_at DESC,u.id LIMIT ? OFFSET ?",
                AccountRepository::row,
                args.toArray()
            ),
            total,
            page,
            size
        );
    }

    public AccountView find(UUID id) {
        return db.queryForObject(SELECT + " WHERE u.id=?", AccountRepository::row, id);
    }

    public Options options() {
        return new Options(
            db.query(
                "SELECT id,full_name,code FROM gcmb.employee WHERE status='ACTIVE' AND user_id IS NULL ORDER BY full_name,id",
                (rs, n) -> new EmployeeOption(
                    rs.getObject("id", UUID.class),
                    rs.getString("full_name"),
                    rs.getString("code")
                )
            ),
            db.query(
                "SELECT code,name FROM gcmb.role ORDER BY name",
                (rs, n) -> new Option(
                    rs.getString(1),
                    rs.getString(2)
                )
            ),
            db.query(
                "SELECT id,name FROM gcmb.branch WHERE status='ACTIVE' ORDER BY name",
                (rs, n) -> new Option(
                    rs.getString(1),
                    rs.getString(2)
                )
            )
        );
    }

    // UC-04/05/07, BR-132, BR-177: xác minh cơ sở từ phân công server, không tin bộ lọc client.
    public boolean manages(UUID actor, UUID branch, boolean writing) {
        if (writing) db.execute("SELECT pg_advisory_xact_lock(1300261013)");
        return !db.queryForList("""
            SELECT u.id FROM gcmb.user_account u
            JOIN gcmb.role r ON r.id=u.role_id AND r.code='BRANCH_MANAGER'
            JOIN gcmb.branch_manager_assignment ma ON ma.user_id=u.id
            JOIN gcmb.branch b ON b.id=ma.branch_id
            WHERE u.id=? AND u.status='ACTIVE' AND b.id=?
              AND (b.status='ACTIVE' OR (?=false AND b.status='SUSPENDED'))
            """ + (writing ? " FOR SHARE OF u,b,ma" : ""), UUID.class, actor, branch, writing).isEmpty();
    }

    public boolean employeeInBranch(UUID employee, UUID branch) {
        return Boolean.TRUE.equals(db.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM gcmb.employee_assignment a WHERE a.employee_id=? AND a.branch_id=?
                AND a.valid_from<=CURRENT_TIMESTAMP AND (a.valid_to IS NULL OR a.valid_to>CURRENT_TIMESTAMP))
            """, Boolean.class, employee, branch));
    }

    public Options branchOptions(UUID branch) {
        return new Options(db.query("""
            SELECT e.id,e.full_name,e.code FROM gcmb.employee e WHERE e.status='ACTIVE' AND e.user_id IS NULL
              AND EXISTS(SELECT 1 FROM gcmb.employee_assignment a WHERE a.employee_id=e.id AND a.branch_id=?
                AND a.valid_from<=CURRENT_TIMESTAMP AND (a.valid_to IS NULL OR a.valid_to>CURRENT_TIMESTAMP))
            ORDER BY e.full_name,e.id
            """, (rs,n) -> new EmployeeOption(rs.getObject("id",UUID.class),rs.getString("full_name"),rs.getString("code")), branch),
            List.of(new Option("RECEPTIONIST", "Lễ tân")),
            db.query("SELECT id,name FROM gcmb.branch WHERE id=?", (rs,n) -> new Option(rs.getString(1),rs.getString(2)), branch));
    }

    // BR-131, UC-07: không mở lại tài khoản của nhân viên đã ngừng hoạt động.
    public boolean canReactivate(UUID id) {
        return Boolean.TRUE.equals(db.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM gcmb.employee WHERE user_id=? AND status='ACTIVE')
            """, Boolean.class, id));
    }

    public boolean lockAvailableEmployee(UUID id) {
        db.execute("SELECT pg_advisory_xact_lock(1300261013)");
        return !db.query(
                "SELECT id FROM gcmb.employee WHERE id=? AND status='ACTIVE' AND user_id IS NULL FOR UPDATE",
                (rs, n) -> rs.getObject("id", UUID.class),
                id
        ).isEmpty();
    }

    public UUID roleId(String code) {
        var ids = db.query("SELECT id FROM gcmb.role WHERE code=?",
                (rs, n) -> rs.getObject("id", UUID.class), code);
        return ids.isEmpty() ? null : ids.get(0);
    }

    public boolean activeBranch(UUID branch) {
        return !db.query(
                "SELECT id FROM gcmb.branch WHERE id=? AND status='ACTIVE' FOR SHARE",
                (rs, n) -> rs.getObject("id", UUID.class),
                branch
        ).isEmpty();
    }

    public boolean exists(String column, String value) {
        if (!List.of("username", "email").contains(column)) {
            throw new IllegalArgumentException();
        }
        return Objects.requireNonNull(db.queryForObject(
                "SELECT count(*) FROM gcmb.user_account WHERE lower(" + column + ")=lower(?)",
                Integer.class,
                value
        ), "Account existence count query returned null") > 0;
    }

    public UUID insert(CreateAccount request, String username, String email, String hash, UUID role, UUID actor) {
        UUID id = UUID.randomUUID();
        db.update(
                "INSERT INTO gcmb.user_account(id,username,email,password_hash,role_id,branch_id,status,failed_login_count) VALUES (?,?,?,?,?,?,'ACTIVE',0)",
                id,
                username,
                email,
                hash,
                role,
                request.branchId()
        );
        db.update("UPDATE gcmb.employee SET user_id=? WHERE id=?", id, request.employeeId());
        db.update(
                """
                INSERT INTO gcmb.audit_event(actor_id,branch_id,action,entity_type,entity_id,after_data)
                SELECT ?,u.branch_id,'CREATE_USER_ACCOUNT','USER_ACCOUNT',u.id,
                    jsonb_build_object('username',u.username,'email',u.email,'role',r.code,'status',u.status,
                        'actorRole',(SELECT ar.code FROM gcmb.user_account au JOIN gcmb.role ar ON ar.id=au.role_id WHERE au.id=?))
                FROM gcmb.user_account u JOIN gcmb.role r ON r.id=u.role_id WHERE u.id=?
                """, actor, actor, id
        );
        return id;
    }
}
