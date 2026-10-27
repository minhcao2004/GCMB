/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: Đọc hồ sơ cá nhân, cập nhật liên hệ và ghi nhật ký trong cùng giao dịch.
 */
package com.gcmb.backend.accounts.repository;

import com.gcmb.backend.accounts.dto.AccountProfileDtos.Profile;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountProfileRepository {
    private final JdbcTemplate db;
    private final AccountRepository accounts;

    public AccountProfileRepository(JdbcTemplate db, AccountRepository accounts) {
        this.db = db;
        this.accounts = accounts;
    }

    public Profile find(UUID id) {
        var account = accounts.find(id);
        var employees = db.query(
                "SELECT full_name,phone FROM gcmb.employee WHERE user_id=?",
                (rs, row) -> new Profile(account, rs.getString("full_name"), rs.getString("phone"), true),
                id
        );
        return employees.isEmpty() ? new Profile(account, null, null, false) : employees.get(0);
    }

    public String lockActiveAccount(UUID id) {
        var hashes = db.queryForList("""
                SELECT password_hash FROM gcmb.user_account
                WHERE id=? AND status='ACTIVE' FOR UPDATE
                """, String.class, id);
        db.queryForList("SELECT id FROM gcmb.employee WHERE user_id=? FOR UPDATE", UUID.class, id);
        return hashes.isEmpty() ? null : hashes.get(0);
    }

    public boolean emailTaken(UUID id, String email) {
        return Boolean.TRUE.equals(db.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM gcmb.user_account WHERE id<>? AND lower(email)=lower(?))
                """, Boolean.class, id, email));
    }

    public void update(UUID id, String fullName, String phone, String email, boolean emailChanged) {
        db.update("UPDATE gcmb.user_account SET email=? WHERE id=?", email, id);
        db.update("UPDATE gcmb.employee SET full_name=?,phone=? WHERE user_id=?", fullName, phone, id);
        if (emailChanged) {
            // Mã khôi phục gửi đến email cũ không được tiếp tục sử dụng.
            db.update("""
                    UPDATE gcmb.auth_token SET revoked_at=CURRENT_TIMESTAMP
                    WHERE user_id=? AND kind='PASSWORD_RESET' AND revoked_at IS NULL
                    """, id);
        }
    }

    public void audit(Profile before, Profile after) {
        // Chỉ ghi thông tin được sửa; không lưu mật khẩu xác nhận trong lịch sử.
        db.update("""
                INSERT INTO gcmb.audit_event(
                    actor_id,branch_id,action,entity_type,entity_id,before_data,after_data
                ) VALUES (?,?,'UPDATE_ACCOUNT_INFORMATION','USER_ACCOUNT',?,
                    jsonb_build_object('username',CAST(? AS text),'fullName',CAST(? AS text),
                        'phone',CAST(? AS text),'email',CAST(? AS text)),
                    jsonb_build_object('username',CAST(? AS text),'fullName',CAST(? AS text),
                        'phone',CAST(? AS text),'email',CAST(? AS text),
                        'actorRole',(SELECT r.code FROM gcmb.user_account u JOIN gcmb.role r ON r.id=u.role_id WHERE u.id=?)))
                """,
                after.account().id(), after.account().branchId(), after.account().id(),
                before.account().username(), before.fullName(), before.phone(), before.account().email(),
                after.account().username(), after.fullName(), after.phone(), after.account().email(), after.account().id()
        );
    }
}
