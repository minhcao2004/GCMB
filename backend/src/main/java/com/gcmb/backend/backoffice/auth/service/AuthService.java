/**
 * Copyright (c) 2026 SEP490-G12.
 *

 * Author: HuyDV
 * Created: 10/10/2026
 * Description: Xử lý đăng nhập, gửi mã khôi phục, đặt lại mật khẩu, xác thực phiên và đăng xuất.

 */

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
    // Chuẩn bị hash giả để đường xử lý username không tồn tại vẫn kiểm tra mật khẩu.
    public AuthService(AuthRepository repository, AuthCrypto crypto, RecoveryMailer mailer) {
        this.repository = repository; this.crypto = crypto; this.mailer = mailer;
        this.dummyHash = crypto.encode(crypto.token());
    }
    // Chặn tài khoản đã khóa hoặc ngừng hoạt động trước mọi thao tác xác thực.
    private void active(Account account) {
        if ("LOCKED".equals(account.status())) throw new AuthFailure(403, "form", "Tài khoản đã bị khóa. Vui lòng liên hệ quản lý");
        if (!"ACTIVE".equals(account.status())) throw new AuthFailure(403, "form", "Tài khoản đã ngừng hoạt động. Vui lòng liên hệ quản lý");
    }
    @Transactional(noRollbackFor = AuthFailure.class)
    // Xác thực username và mật khẩu, tạo phiên lưu bằng hash token.
    public LoginResult login(String username, String password, boolean remember) {
        username = AuthValidation.required(username, "username", "tên đăng nhập", 100, true);
        AuthValidation.required(password, "password", "mật khẩu", Integer.MAX_VALUE, false);
        Account account = repository.findByUsername(username);
        if (account == null) { crypto.matches(password, dummyHash); throw new AuthFailure(401, "form", "Tên đăng nhập hoặc mật khẩu không đúng"); }
        active(account);
        if ("BRANCH_MANAGER".equals(account.role()) && account.branchId() == null) {
            throw new AuthFailure(403, "form", "Tài khoản quản lý cơ sở chưa được gán cơ sở. Vui lòng liên hệ quản lý cấp cao");
        }
        Instant now = Instant.now();
        if (!crypto.matches(password, account.hash())) {
            throw new AuthFailure(401, "form", "Tên đăng nhập hoặc mật khẩu không đúng");
        }
        String target = AuthMapper.destination(account.role());
        repository.recordLogin(account.id(), now);
        // Chính sách hiện tại không tự hết hạn phiên ở ứng dụng. Cookie thường hết khi đóng
        // trình duyệt; ghi nhớ đăng nhập kéo dài đến khi đăng xuất, đặt lại mật khẩu,
        // thu hồi tài khoản hoặc đến giới hạn lưu cookie của trình duyệt.
        long seconds = remember ? Integer.MAX_VALUE : -1;
        String token = crypto.token();
        repository.createSession(account.id(), AuthCrypto.hash(token));
        return new LoginResult(new Identity(account.id(), account.username(), account.role(), target, account.branchId(), account.branchName()), token, seconds);
    }
    @Transactional(noRollbackFor = AuthFailure.class)
    // Vô hiệu mã cũ và gửi mã mới; lỗi SMTP sẽ hoàn tác cả giao dịch.
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
        // SMTP lỗi sẽ hoàn tác cả mã mới lẫn thao tác vô hiệu mã cũ.
        mailer.send(account.email(), code);
        return new Recovery(challenge, email);
    }
    @Transactional(noRollbackFor = AuthFailure.class)
    // Đổi mật khẩu, tiêu thụ mã và thu hồi mọi phiên trong cùng giao dịch.
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
    @Transactional
    public void changePassword(Identity identity, String currentPassword, String password, String confirmation) {
        String current = AuthValidation.required(currentPassword, "currentPassword", "mật khẩu hiện tại", Integer.MAX_VALUE, false);
        AuthValidation.password(password, confirmation);
        Account account = repository.findByUsername(identity.username());
        if (account == null || !account.id().equals(identity.id())) throw new AuthFailure(401, "form", "Vui lòng đăng nhập lại");
        active(account);
        if (!crypto.matches(current, account.hash())) throw new AuthFailure(400, "currentPassword", "Mật khẩu hiện tại không đúng");
        if (crypto.matches(password, account.hash())) throw new AuthFailure(400, "password", "Mật khẩu mới phải khác mật khẩu hiện tại");
        Instant now = Instant.now();
        repository.changePassword(account.id(), crypto.encode(password), now);
        repository.revokePendingResets(account.id(), now);
    }

    @Transactional(readOnly = true)
    // Kiểm tra token cookie qua hash đã lưu và trả danh tính còn hiệu lực.
    public Identity authenticate(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return null;
        return repository.findSession(AuthCrypto.hash(token));
    }
    // Thu hồi phiên hiện tại nếu request có token.
    public void logout(String token) {
        if (token != null) repository.revokeSession(AuthCrypto.hash(token));
    }
}
