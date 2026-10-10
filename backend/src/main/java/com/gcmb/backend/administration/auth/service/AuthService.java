package com.gcmb.backend.administration.auth.service;

import com.gcmb.backend.administration.auth.entity.Account;
import com.gcmb.backend.administration.auth.entity.Reset;
import com.gcmb.backend.administration.auth.dto.response.Identity;
import com.gcmb.backend.administration.auth.dto.response.Recovery;
import com.gcmb.backend.administration.auth.repository.AuthRepository;
import com.gcmb.backend.administration.auth.mapper.AuthMapper;

import com.gcmb.backend.administration.auth.security.AuthCrypto;
import com.gcmb.backend.administration.auth.validator.AuthValidation;
import com.gcmb.backend.administration.auth.exception.AuthFailure;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final AuthRepository repository;
    private final AuthCrypto crypto;
    private final RecoveryMailer mailer;
    private final String dummyHash;
    public AuthService(AuthRepository repository, AuthCrypto crypto, RecoveryMailer mailer) {
        this.repository = repository; this.crypto = crypto; this.mailer = mailer;
        this.dummyHash = crypto.encode(crypto.token());
    }
    private void active(Account account) {
        if ("LOCKED".equals(account.status())) throw new AuthFailure(403, "form", "Tài khoản đã bị khóa. Vui lòng liên hệ quản lý");
        if (!"ACTIVE".equals(account.status())) throw new AuthFailure(403, "form", "Tài khoản đã ngừng hoạt động. Vui lòng liên hệ quản lý");
    }
    @Transactional(noRollbackFor = AuthFailure.class)
    public LoginResult login(String username, String password, boolean remember) {
        username = AuthValidation.required(username, "username", "tên đăng nhập", 100, true);
        AuthValidation.required(password, "password", "mật khẩu", Integer.MAX_VALUE, false);
        Account account = repository.findByUsername(username);
        if (account == null) { crypto.matches(password, dummyHash); throw new AuthFailure(401, "form", "Tên đăng nhập hoặc mật khẩu không đúng"); }
        active(account);
        Instant now = Instant.now();
        if (!crypto.matches(password, account.hash())) {
            throw new AuthFailure(401, "form", "Tên đăng nhập hoặc mật khẩu không đúng");
        }
        String target = AuthMapper.destination(account.role());
        repository.recordLogin(account.id(), now);
        // No application timeout under the requested simplified rules. A normal
        // login uses a browser-session cookie; remember-me persists until logout,
        // reset, account revocation, or the browser's own cookie-retention limit.
        long seconds = remember ? Integer.MAX_VALUE : -1;
        String token = crypto.token();
        repository.createSession(account.id(), AuthCrypto.hash(token));
        return new LoginResult(new Identity(account.id(), account.username(), account.role(), target), token, seconds);
    }
    @Transactional(noRollbackFor = AuthFailure.class)
    public Recovery sendCode(String email) {
        email = AuthValidation.email(email);
        Account account = repository.findByEmail(email);
        if (account == null) throw new AuthFailure(404, "email", "Email không tồn tại trong hệ thống");
        active(account);
        Instant now = Instant.now();
        String challenge = crypto.token();
        String code = crypto.code();
        String codeHash = crypto.encode(email + ":" + code);
        repository.revokePendingResets(account.id(), now);
        repository.createReset(account.id(), AuthCrypto.hash(challenge), codeHash, now);
        // SMTP failure rolls back insertion AND invalidation of the previous code.
        mailer.send(account.email(), code);
        return new Recovery(challenge, email);
    }
    @Transactional(noRollbackFor = AuthFailure.class)
    public void reset(String email, String challenge, String code, String password, String confirmation) {
        email = AuthValidation.email(email);
        AuthValidation.password(password, confirmation);
        AuthValidation.code(code);
        if (challenge == null || !challenge.matches("[A-Za-z0-9_-]{43}")) throw new AuthFailure(400, "code", "Yêu cầu khôi phục không hợp lệ. Vui lòng gửi lại mã");
        Account account = repository.findByEmail(email);
        if (account == null) throw new AuthFailure(400, "code", "Mã xác nhận không đúng");
        active(account);
        Reset reset = repository.findReset(account.id(), AuthCrypto.hash(challenge));
        if (reset == null) throw new AuthFailure(400, "code", "Mã xác nhận không đúng");
        if (reset.usedAt() != null) throw new AuthFailure(400, "code", "Mã xác nhận đã được sử dụng");
        if (reset.revokedAt() != null) throw new AuthFailure(400, "code", "Mã xác nhận đã hết hiệu lực. Vui lòng dùng mã mới nhất");
        Instant now = Instant.now();
        if (!crypto.matches(email + ":" + code, reset.hash())) {
            throw new AuthFailure(400, "code", "Mã xác nhận không đúng");
        }
        repository.changePassword(account.id(), crypto.encode(password), now);
        repository.consumeReset(reset.id(), now);
        repository.revokeTokens(account.id(), now);
    }
    @Transactional(readOnly = true)
    public Identity authenticate(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return null;
        return repository.findSession(AuthCrypto.hash(token));
    }
    public void logout(String token) {
        if (token != null) repository.revokeSession(AuthCrypto.hash(token));
    }
}
