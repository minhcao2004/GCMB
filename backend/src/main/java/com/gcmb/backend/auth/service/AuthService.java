/**
 * Copyright (c) 2026 SEP490-G12.
 *

 * Author: HuyDV
 * Created: 10/10/2026
 * Description: Xử lý đăng nhập, gửi mã khôi phục, đặt lại mật khẩu, xác thực phiên và đăng xuất.

 */

package com.gcmb.backend.auth.service;

import com.gcmb.backend.auth.dto.LoginResult;

import com.gcmb.backend.auth.repository.model.Account;
import com.gcmb.backend.auth.repository.model.Reset;
import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.dto.Recovery;
import com.gcmb.backend.auth.repository.AuthRepository;
import com.gcmb.backend.auth.repository.AuthMapper;

import com.gcmb.backend.auth.security.AuthCrypto;
import com.gcmb.backend.auth.service.AuthValidation;
import com.gcmb.backend.auth.exception.AuthFailure;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
        this.repository = repository;
        this.crypto = crypto;
        this.mailer = mailer;
        this.dummyHash = crypto.encode(crypto.token());
    }

    // Chặn tài khoản đã khóa hoặc ngừng hoạt động trước mọi thao tác xác thực.
    private void active(Account account) {
        // BR-131; MSG33.
        if ("LOCKED".equals(account.status())) throw new AuthFailure(
            403,
            "form",
            "Tài khoản đã bị khóa. Vui lòng liên hệ quản lý"
        );
        if (!"ACTIVE".equals(account.status())) throw new AuthFailure(
            403,
            "form",
            "Tài khoản đã bị vô hiệu hóa. Vui lòng liên hệ Quản lý cấp cao để được hỗ trợ."
        );
    }

    @Transactional(noRollbackFor = AuthFailure.class)
    // Xác thực username và mật khẩu, tạo phiên lưu bằng hash token.
    public LoginResult login(String username, String password, boolean remember) {
        username = AuthValidation.required(username, "username", "tên đăng nhập", 100, true);
        AuthValidation.required(password, "password", "mật khẩu", Integer.MAX_VALUE, false);
        Account account = repository.findByUsername(username);
        if (account == null) {
            crypto.matches(password, dummyHash);
            throw new AuthFailure(401, "form", "Tên đăng nhập hoặc mật khẩu không đúng. Vui lòng kiểm tra và thử lại.");
        }
        active(account);
        if ("BRANCH_MANAGER".equals(account.role()) && account.branchId() == null) {
            throw new AuthFailure(
                403,
                "form",
                "Tài khoản quản lý cơ sở chưa được gán cơ sở. Vui lòng liên hệ quản lý cấp cao"
            );
        }
        Instant now = Instant.now();
        if (!crypto.matches(password, account.hash())) {
            throw new AuthFailure(401, "form", "Tên đăng nhập hoặc mật khẩu không đúng. Vui lòng kiểm tra và thử lại.");
        }
        String target = AuthMapper.destination(account.role());
        repository.recordLogin(account.id(), now);
        // Chính sách hiện tại không tự hết hạn phiên ở ứng dụng. Cookie thường hết khi đóng
        // trình duyệt; ghi nhớ đăng nhập kéo dài đến khi đăng xuất, đặt lại mật khẩu,
        // thu hồi tài khoản hoặc đến giới hạn lưu cookie của trình duyệt.
        long seconds = remember ? Integer.MAX_VALUE : -1;
        String token = crypto.token();
        repository.createSession(account.id(), AuthCrypto.hash(token));
        return new LoginResult(
            new Identity(
                account.id(),
                account.username(),
                account.role(),
                target,
                account.branchId(),
                account.branchName()
            ),
            token,
            seconds
        );
    }

    @Transactional(noRollbackFor = AuthFailure.class)
    // Vô hiệu mã cũ và gửi mã mới; lỗi SMTP sẽ hoàn tác cả giao dịch.
    public Recovery sendCode(String email) {
        email = AuthValidation.email(email);
        Account account = repository.findByEmail(email);
        if (account == null) throw new AuthFailure(
            404,
            "email",
            "Email không tồn tại trong hệ thống"
        );
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
    public void reset(
        String email,
        String challenge,
        String code,
        String password,
        String confirmation
    ) {
        email = AuthValidation.email(email);
        AuthValidation.password(password, confirmation);
        AuthValidation.code(code);
        if (challenge == null || !challenge.matches("[A-Za-z0-9_-]{43}")) throw new AuthFailure(
            400,
            "code",
            "Yêu cầu khôi phục không hợp lệ. Vui lòng gửi lại mã"
        );
        Account account = repository.findByEmail(email);
        if (account == null) throw new AuthFailure(
            400,
            "code",
            "Mã xác minh không đúng. Vui lòng kiểm tra mã được gửi đến email của bạn."
        );
        active(account);
        Reset reset = repository.findReset(account.id(), AuthCrypto.hash(challenge));
        if (reset == null) throw new AuthFailure(
            400,
            "code",
            "Mã xác minh không đúng. Vui lòng kiểm tra mã được gửi đến email của bạn."
        );
        // BR-126; MSG32. Kiểm tra hạn tại server, không dựa vào đồng hồ trình duyệt.
        if (
            reset.usedAt() != null ||
            reset.revokedAt() != null ||
            repository.resetExpired(reset.id())
        ) throw new AuthFailure(
            400,
            "code",
            "Mã xác minh đã hết hiệu lực. Vui lòng yêu cầu mã mới để tiếp tục."
        );
        Instant now = Instant.now();
        if (!crypto.matches(email + ":" + code, reset.hash())) {
            throw new AuthFailure(
                400,
                "code",
                "Mã xác minh không đúng. Vui lòng kiểm tra mã được gửi đến email của bạn."
            );
        }
        repository.changePassword(account.id(), crypto.encode(password), now);
        repository.consumeReset(reset.id(), now);
        repository.revokeTokens(account.id(), now);
    }

    // BR-114/120/121/130; MSG06/28/29/30.
    @Transactional
    public void changePassword(
        Identity identity,
        String currentPassword,
        String password,
        String confirmation
    ) {
        String current = AuthValidation.required(
            currentPassword,
            "currentPassword",
            "mật khẩu hiện tại",
            Integer.MAX_VALUE,
            false
        );
        AuthValidation.password(password, confirmation);
        Account account = repository.findByUsername(identity.username());
        if (account == null || !account.id().equals(identity.id())) throw new AuthFailure(
            401,
            "form",
            "Vui lòng đăng nhập lại"
        );
        active(account);
        if (!crypto.matches(current, account.hash())) throw new AuthFailure(
            400,
            "currentPassword",
            "Mật khẩu hiện tại không đúng. Vui lòng nhập lại."
        );
        if (crypto.matches(password, account.hash())) throw new AuthFailure(
            400,
            "password",
            "Mật khẩu mới phải khác mật khẩu hiện tại."
        );
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

    @Transactional(readOnly = true)
    public List<Map<String, Object>> managedBranches(Identity identity) {
        requireManager(identity);
        return repository.managedBranches(identity.id());
    }

    @Transactional
    public Identity switchBranch(Identity identity, String token, UUID branchId) {
        requireManager(identity);
        if (branchId == null) throw new AuthFailure(400, "branchId", "Chọn cơ sở đang quản lý");
        String tokenHash = AuthCrypto.hash(token);
        if (!repository.selectBranch(identity.id(), tokenHash, branchId)) throw new AuthFailure(
            403,
            "branchId",
            "Bạn không được quản lý cơ sở này hoặc cơ sở đã ngừng hoạt động"
        );
        return repository.findSession(tokenHash);
    }

    @Transactional(readOnly = true)
    public Identity forBranch(Identity identity, String branch) {
        requireManager(identity);
        UUID branchId;
        try {
            branchId = UUID.fromString(branch);
        } catch (IllegalArgumentException e) {
            throw new AuthFailure(400, "branchId", "Cơ sở không hợp lệ");
        }
        String name = repository.managedBranchName(identity.id(), branchId);
        if (name == null) throw new AuthFailure(
            403,
            "branchId",
            "Bạn không còn quyền tại cơ sở đang chọn"
        );
        boolean readOnly = repository
            .managedBranches(identity.id())
            .stream()
            .anyMatch(b -> branchId.equals(b.get("id")) && "SUSPENDED".equals(b.get("status")));
        return new Identity(
            identity.id(),
            identity.username(),
            identity.role(),
            identity.destination(),
            branchId,
            name,
            readOnly
        );
    }

    private static void requireManager(Identity identity) {
        if (identity == null || !"BRANCH_MANAGER".equals(identity.role())) throw new AuthFailure(
            403,
            "form",
            "Chỉ quản lý cơ sở được chọn cơ sở làm việc"
        );
    }

    // Thu hồi phiên hiện tại nếu request có token.
    public void logout(String token) {
        if (token != null) repository.revokeSession(AuthCrypto.hash(token));
    }
}
