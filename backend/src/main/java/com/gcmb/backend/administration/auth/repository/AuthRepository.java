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
    public AuthRepository(JdbcTemplate db) { this.db = db; }
    private static Timestamp ts(Instant value) { return Timestamp.from(value); }
    public Account findByUsername(String username) { return account("username", username); }
    public Account findByEmail(String email) { return account("email", email); }
    private Account account(String column, String value) {
        // column is only supplied by internal constants, never request data.
        List<Account> result = db.query("SELECT u.*, r.code AS role FROM gcmb.user_account u JOIN gcmb.role r ON r.id=u.role_id WHERE lower(u." + column + ")=lower(?) FOR UPDATE OF u", AuthMapper::account, value);
        return result.isEmpty() ? null : result.get(0);
    }
    public void recordLogin(UUID userId, Instant now) {
        db.update("UPDATE gcmb.user_account SET failed_login_count=0, locked_until=NULL, last_login_at=? WHERE id=?", ts(now), userId);
    }
    public void createSession(UUID userId, String tokenHash) {
        db.update("INSERT INTO gcmb.auth_token(user_id, token_hash, kind, expires_at) VALUES (?, ?, 'SESSION', 'infinity')", userId, tokenHash);
    }
    public void revokePendingResets(UUID userId, Instant now) {
        db.update("UPDATE gcmb.auth_token SET revoked_at=? WHERE user_id=? AND kind='PASSWORD_RESET' AND used_at IS NULL AND revoked_at IS NULL", ts(now), userId);
    }
    public void createReset(UUID userId, String tokenHash, String codeHash, Instant now) {
        db.update("INSERT INTO gcmb.auth_token(user_id, token_hash, kind, code_hash, expires_at, created_at) VALUES (?, ?, 'PASSWORD_RESET', ?, 'infinity', ?)", userId, tokenHash, codeHash, ts(now));
    }
    public Reset findReset(UUID userId, String tokenHash) {
        List<Reset> resets = db.query("SELECT * FROM gcmb.auth_token WHERE user_id=? AND token_hash=? AND kind='PASSWORD_RESET' FOR UPDATE", AuthMapper::reset, userId, tokenHash);
        return resets.isEmpty() ? null : resets.get(0);
    }
    public void changePassword(UUID userId, String passwordHash, Instant now) {
        db.update("UPDATE gcmb.user_account SET password_hash=?, password_changed_at=?, failed_login_count=0, locked_until=NULL WHERE id=?", passwordHash, ts(now), userId);
    }
    public void consumeReset(UUID resetId, Instant now) {
        db.update("UPDATE gcmb.auth_token SET used_at=? WHERE id=?", ts(now), resetId);
    }
    public void revokeTokens(UUID userId, Instant now) {
        db.update("UPDATE gcmb.auth_token SET revoked_at=? WHERE user_id=? AND revoked_at IS NULL", ts(now), userId);
    }
    public Identity findSession(String tokenHash) {
        List<Identity> users = db.query("""
            SELECT u.id, u.username, r.code FROM gcmb.auth_token t
            JOIN gcmb.user_account u ON u.id=t.user_id JOIN gcmb.role r ON r.id=u.role_id
            WHERE t.token_hash=? AND t.kind='SESSION' AND t.revoked_at IS NULL AND t.expires_at>CURRENT_TIMESTAMP AND u.status='ACTIVE'
            """, AuthMapper::identity, tokenHash);
        return users.isEmpty() ? null : users.get(0);
    }
    public void revokeSession(String tokenHash) {
        db.update("UPDATE gcmb.auth_token SET revoked_at=CURRENT_TIMESTAMP WHERE token_hash=? AND kind='SESSION'", tokenHash);
    }
}
