package com.gcmb.backend.administration.auth.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.gcmb.backend.administration.auth.entity.Account;
import com.gcmb.backend.administration.auth.entity.Reset;
import com.gcmb.backend.administration.auth.dto.response.Identity;
import com.gcmb.backend.administration.auth.mapper.AuthMapper;

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
        // Tên cột chỉ đến từ hằng nội bộ, không lấy trực tiếp từ request.
        List<Account> result = db.query("SELECT u.*, r.code AS role FROM gcmb.user_account u JOIN gcmb.role r ON r.id=u.role_id WHERE lower(u." + column + ")=lower(?) FOR UPDATE OF u", AuthMapper::account, value);
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
    // Lưu challenge và mã xác nhận ở dạng hash.
    public void createReset(UUID userId, String tokenHash, String codeHash, Instant now) {
        db.update("INSERT INTO gcmb.auth_token(user_id, token_hash, kind, code_hash, expires_at, created_at) VALUES (?, ?, 'PASSWORD_RESET', ?, 'infinity', ?)", userId, tokenHash, codeHash, ts(now));
    }
    // Khóa hàng mã khôi phục để tránh hai yêu cầu đổi mật khẩu cùng thành công.
    public Reset findReset(UUID userId, String tokenHash) {
        List<Reset> resets = db.query("SELECT * FROM gcmb.auth_token WHERE user_id=? AND token_hash=? AND kind='PASSWORD_RESET' FOR UPDATE", AuthMapper::reset, userId, tokenHash);
        return resets.isEmpty() ? null : resets.get(0);
    }
    // Lưu hash mật khẩu mới và thời điểm đổi.
    public void changePassword(UUID userId, String passwordHash, Instant now) {
        db.update("UPDATE gcmb.user_account SET password_hash=?, password_changed_at=?, failed_login_count=0, locked_until=NULL WHERE id=?", passwordHash, ts(now), userId);
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
            SELECT u.id, u.username, r.code FROM gcmb.auth_token t
            JOIN gcmb.user_account u ON u.id=t.user_id JOIN gcmb.role r ON r.id=u.role_id
            WHERE t.token_hash=? AND t.kind='SESSION' AND t.revoked_at IS NULL AND t.expires_at>CURRENT_TIMESTAMP AND u.status='ACTIVE'
            """, AuthMapper::identity, tokenHash);
        return users.isEmpty() ? null : users.get(0);
    }
    // Thu hồi một phiên khi đăng xuất.
    public void revokeSession(String tokenHash) {
        db.update("UPDATE gcmb.auth_token SET revoked_at=CURRENT_TIMESTAMP WHERE token_hash=? AND kind='SESSION'", tokenHash);
    }
}
