/**
 * Copyright (c) 2026 SEP490-G12.
 *

 * Author: HuyDV
 * Created: 10/10/2026
 * Description: Truy vấn và cập nhật tài khoản, phiên đăng nhập và yêu cầu đặt lại mật khẩu bằng JDBC.

 */

package com.gcmb.backend.auth.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.gcmb.backend.auth.repository.model.Account;
import com.gcmb.backend.auth.repository.model.Reset;
import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.repository.AuthMapper;

@Repository
public class AuthRepository {
    private final JdbcTemplate db;
    // JDBC truy vấn trực tiếp các bảng xác thực trong schema gcmb.
    public AuthRepository(JdbcTemplate db) { this.db = db; }
    // Đổi Instant sang tham số timestamp của JDBC.
    private static Timestamp ts(Instant value) { return Timestamp.from(value); }
    // Tìm tài khoản bằng username cho đăng nhập.
    public Account findByUsername(String username) { return account("username", username); }
    // Tìm tài khoản bằng email cho khôi phục mật khẩu.
    public Account findByEmail(String email) { return account("email", email); }
    // Dùng cột đã chọn nội bộ để đọc thông tin tài khoản và vai trò.
    private Account account(String column, String value) {
// Tên cột chỉ đến từ hằng nội bộ, không lấy từ request.
String sql = """
    SELECT u.id, u.username, u.email, u.password_hash, u.status,
           r.code AS role,
           CASE WHEN r.code='BRANCH_MANAGER' THEN bma.branch_id ELSE u.branch_id END AS branch_id,
           b.name AS branch_name
    FROM gcmb.user_account u
    JOIN gcmb.role r ON r.id = u.role_id
    LEFT JOIN LATERAL (
        SELECT a.branch_id FROM gcmb.branch_manager_assignment a JOIN gcmb.branch mb ON mb.id=a.branch_id
        WHERE a.user_id=u.id AND r.code='BRANCH_MANAGER' AND mb.status IN ('ACTIVE','SUSPENDED')
        ORDER BY (a.branch_id=u.branch_id) DESC NULLS LAST,mb.name,mb.id LIMIT 1
    ) bma ON TRUE
    LEFT JOIN gcmb.branch b ON b.id = CASE WHEN r.code='BRANCH_MANAGER' THEN bma.branch_id ELSE u.branch_id END
    WHERE lower(u.%s) = lower(?)
    FOR UPDATE OF u
    """.formatted(column);
List<Account> result = db.query(sql, AuthMapper::account, value);
        return result.isEmpty() ? null : result.get(0);
    }
    // Lưu thời điểm đăng nhập thành công.
    public void recordLogin(UUID userId, Instant now) {
        db.update("UPDATE gcmb.user_account SET failed_login_count=0, locked_until=NULL, last_login_at=? WHERE id=?", ts(now), userId);
    }
    // Lưu hash của token phiên, không lưu token gốc.
    public void createSession(UUID userId, String tokenHash) {
        db.update("INSERT INTO gcmb.auth_token(user_id, token_hash, kind, expires_at) VALUES (?, ?, 'SESSION', 'infinity')", userId, tokenHash);
    }
    // Vô hiệu các mã khôi phục chưa dùng trước khi phát mã mới.
    public void revokePendingResets(UUID userId, Instant now) {
        db.update("UPDATE gcmb.auth_token SET revoked_at=? WHERE user_id=? AND kind='PASSWORD_RESET' AND used_at IS NULL AND revoked_at IS NULL", ts(now), userId);
    }
    // BR-126: mã reset có hiệu lực đúng 10 phút; token phiên là chính sách riêng.
    public void createReset(UUID userId, String tokenHash, String codeHash, Instant now) {
        db.update("INSERT INTO gcmb.auth_token(user_id, token_hash, kind, code_hash, expires_at, created_at) VALUES (?, ?, 'PASSWORD_RESET', ?, ?, ?)", userId, tokenHash, codeHash, ts(now.plusSeconds(600)), ts(now));
    }
    // BR-126: áp dụng cả mã cũ từng lưu expires_at=infinity, không cần sửa dữ liệu lịch sử.
    public boolean resetExpired(UUID id) {
        return Boolean.TRUE.equals(db.queryForObject("SELECT LEAST(expires_at,created_at+interval '10 minutes')<=clock_timestamp() FROM gcmb.auth_token WHERE id=? AND kind='PASSWORD_RESET'", Boolean.class, id));
    }
    // Khóa hàng mã khôi phục để tránh hai yêu cầu đổi mật khẩu cùng thành công.
    public Reset findReset(UUID userId, String tokenHash) {
        List<Reset> resets = db.query("SELECT * FROM gcmb.auth_token WHERE user_id=? AND token_hash=? AND kind='PASSWORD_RESET' FOR UPDATE", AuthMapper::reset, userId, tokenHash);
        return resets.isEmpty() ? null : resets.get(0);
    }
    // Lưu hash mật khẩu mới và thời điểm đổi.
    public void changePassword(UUID userId, String passwordHash, Instant now) {
        db.update("UPDATE gcmb.user_account SET password_hash=?, password_changed_at=?, failed_login_count=0, locked_until=NULL WHERE id=?", passwordHash, ts(now), userId);
        db.update("""
            INSERT INTO gcmb.audit_event(actor_id,branch_id,action,entity_type,entity_id,after_data)
            SELECT u.id,u.branch_id,'CHANGE_PASSWORD','USER_ACCOUNT',u.id,
                jsonb_build_object('username',u.username,'actorRole',r.code)
            FROM gcmb.user_account u JOIN gcmb.role r ON r.id=u.role_id WHERE u.id=?
            """,userId);
    }
    // Đánh dấu mã khôi phục đã sử dụng.
    public void consumeReset(UUID resetId, Instant now) {
        db.update("UPDATE gcmb.auth_token SET used_at=? WHERE id=?", ts(now), resetId);
    }
    // Thu hồi mọi phiên sau khi đặt lại mật khẩu.
    public void revokeTokens(UUID userId, Instant now) {
        db.update("UPDATE gcmb.auth_token SET revoked_at=? WHERE user_id=? AND revoked_at IS NULL", ts(now), userId);
    }
    // Đọc danh tính qua hash token nếu tài khoản và phiên còn hiệu lực.
    public Identity findSession(String tokenHash) {
        List<Identity> users = db.query("""
            SELECT u.id, u.username, r.code, CASE WHEN r.code='BRANCH_MANAGER' THEN bma.branch_id ELSE u.branch_id END AS branch_id, b.name AS branch_name,b.status AS branch_status FROM gcmb.auth_token t
            JOIN gcmb.user_account u ON u.id=t.user_id JOIN gcmb.role r ON r.id=u.role_id
            LEFT JOIN LATERAL (
                SELECT a.branch_id FROM gcmb.branch_manager_assignment a JOIN gcmb.branch mb ON mb.id=a.branch_id
                WHERE a.user_id=u.id AND r.code='BRANCH_MANAGER' AND mb.status IN ('ACTIVE','SUSPENDED')
                ORDER BY (a.branch_id=t.selected_branch_id) DESC NULLS LAST,
                    (a.branch_id=u.branch_id) DESC NULLS LAST,mb.name,mb.id LIMIT 1
            ) bma ON TRUE
            LEFT JOIN gcmb.branch b ON b.id=CASE WHEN r.code='BRANCH_MANAGER' THEN bma.branch_id ELSE u.branch_id END
            WHERE t.token_hash=? AND t.kind='SESSION' AND t.revoked_at IS NULL AND t.expires_at>CURRENT_TIMESTAMP AND u.status='ACTIVE'
              AND (r.code <> 'BRANCH_MANAGER' OR bma.branch_id IS NOT NULL)
            """, AuthMapper::identity, tokenHash);
        return users.isEmpty() ? null : users.get(0);
    }
    public List<java.util.Map<String, Object>> managedBranches(UUID userId) {
        return db.queryForList("""
            SELECT b.id,b.name,b.status FROM gcmb.branch_manager_assignment a JOIN gcmb.branch b ON b.id=a.branch_id
            WHERE a.user_id=? AND b.status IN ('ACTIVE','SUSPENDED') ORDER BY b.name,b.id
            """, userId);
    }

    public String managedBranchName(UUID userId, UUID branchId) {
        var names = db.queryForList("""
            SELECT b.name FROM gcmb.branch_manager_assignment a JOIN gcmb.branch b ON b.id=a.branch_id
            WHERE a.user_id=? AND a.branch_id=? AND b.status IN ('ACTIVE','SUSPENDED')
            """, String.class, userId, branchId);
        return names.isEmpty() ? null : names.get(0);
    }

    public boolean selectBranch(UUID userId, String tokenHash, UUID branchId) {
        return db.update("""
            UPDATE gcmb.auth_token t SET selected_branch_id=? WHERE t.user_id=? AND t.token_hash=?
                AND t.kind='SESSION' AND t.revoked_at IS NULL AND t.expires_at>CURRENT_TIMESTAMP
                AND EXISTS(SELECT 1 FROM gcmb.branch_manager_assignment a JOIN gcmb.branch b ON b.id=a.branch_id
                    WHERE a.user_id=t.user_id AND a.branch_id=? AND b.status IN ('ACTIVE','SUSPENDED'))
            """, branchId, userId, tokenHash, branchId) == 1;
    }

    // Thu hồi một phiên khi đăng xuất.
    public void revokeSession(String tokenHash) {
        db.update("UPDATE gcmb.auth_token SET revoked_at=CURRENT_TIMESTAMP WHERE token_hash=? AND kind='SESSION'", tokenHash);
    }
}
