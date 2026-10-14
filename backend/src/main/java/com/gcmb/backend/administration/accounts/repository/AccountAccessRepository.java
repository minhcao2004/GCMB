/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: Cập nhật quyền, thu hồi token và ghi lịch sử thay đổi tài khoản.
 */
package com.gcmb.backend.administration.accounts.repository;

import com.gcmb.backend.administration.accounts.dto.AccountAccessDtos.PermissionView;
import com.gcmb.backend.administration.accounts.dto.AccountDtos.AccountView;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountAccessRepository {
    private final JdbcTemplate db;

    public AccountAccessRepository(JdbcTemplate db) {
        this.db = db;
    }

    public boolean lockManagement(UUID actor) {
        // Tuần tự hóa thay đổi quyền để hai quản lý không đồng thời thu hồi nhau.
        db.execute("SELECT pg_advisory_xact_lock(1300261013)");
        return !db.queryForList("""
                SELECT u.id FROM gcmb.user_account u
                JOIN gcmb.role r ON r.id=u.role_id
                WHERE u.id=? AND u.status='ACTIVE' AND r.code='HEAD_OFFICE_MANAGER'
                FOR UPDATE OF u
                """, UUID.class, actor).isEmpty();
    }

    public boolean lockAccount(UUID id) {
        return !db.queryForList(
                "SELECT id FROM gcmb.user_account WHERE id=? FOR UPDATE",
                UUID.class,
                id
        ).isEmpty();
    }

    public void setRole(UUID id, UUID roleId, UUID branchId) {
        db.update(
                "UPDATE gcmb.user_account SET role_id=?,branch_id=? WHERE id=?",
                roleId,
                branchId,
                id
        );
    }

    public void deactivate(UUID id) {
        // Giữ hồ sơ nhân viên và tài khoản để truy xuất lịch sử.
        db.update("UPDATE gcmb.user_account SET status='REVOKED' WHERE id=?", id);
    }

    public void revokeTokens(UUID id) {
        db.update("""
                UPDATE gcmb.auth_token SET revoked_at=CURRENT_TIMESTAMP
                WHERE user_id=? AND revoked_at IS NULL
                """, id);
    }

    public List<PermissionView> permissions(String role) {
        return db.query("""
                SELECT p.code,p.name,rp.scope FROM gcmb.role_permission rp
                JOIN gcmb.role r ON r.id=rp.role_id
                JOIN gcmb.permission p ON p.id=rp.permission_id
                WHERE r.code=? ORDER BY p.name,p.code
                """,
                (rs, row) -> new PermissionView(
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("scope")
                ),
                role
        );
    }

    public void audit(UUID actor, String action, AccountView before, AccountView after, String reason) {
        // Chỉ ghi các trường được phép; tuyệt đối không đưa mật khẩu hoặc token vào audit.
        db.update("""
                INSERT INTO gcmb.audit_event(
                    actor_id,branch_id,action,entity_type,entity_id,before_data,after_data,reason
                ) VALUES (?,?,?,'USER_ACCOUNT',?,
                    jsonb_build_object('username',CAST(? AS text),'role',CAST(? AS text),
                        'branchName',CAST(? AS text),'status',CAST(? AS text)),
                    jsonb_build_object('username',CAST(? AS text),'role',CAST(? AS text),
                        'branchName',CAST(? AS text),'status',CAST(? AS text),
                        'actorRole',(SELECT r.code FROM gcmb.user_account u JOIN gcmb.role r ON r.id=u.role_id WHERE u.id=?)),?)
                """,
                actor, after.branchId(), action, after.id(),
                before.username(), before.role(), before.branchName(), before.status(),
                after.username(), after.role(), after.branchName(), after.status(), actor, reason
        );
    }
}
