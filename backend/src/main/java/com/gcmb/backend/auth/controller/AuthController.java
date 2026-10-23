/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 10/10/2026
 * Description: Cung cấp API xác thực, khôi phục mật khẩu và quản lý cookie phiên đăng nhập.
 */

package com.gcmb.backend.auth.controller;

import com.gcmb.backend.auth.dto.LoginRequest;
import com.gcmb.backend.auth.dto.EmailRequest;
import com.gcmb.backend.auth.dto.ResetRequest;
import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.dto.Recovery;
import com.gcmb.backend.auth.dto.LoginResult;

import com.gcmb.backend.auth.service.AuthService;
import com.gcmb.backend.auth.security.SessionFilter;
import com.gcmb.backend.auth.security.AuthSettings;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;
    private final AuthSettings policy;
    // Tách nghiệp vụ xác thực và chính sách cookie khỏi lớp điều khiển HTTP.
    public AuthController(AuthService auth, AuthSettings policy) { this.auth = auth; this.policy = policy; }
    // Cấp token CSRF cho các thao tác ghi từ trình duyệt.
    @GetMapping("/csrf") public Map<String, String> csrf(CsrfToken token) { return Map.of("token", token.getToken(), "headerName", token.getHeaderName()); }
    // Trả trạng thái sẵn sàng cho lệnh khởi động local.
    @GetMapping("/health") public Map<String, String> health() { return Map.of("status", "UP"); }
    // Đặt token phiên vào cookie HttpOnly và chỉ trả danh tính trong JSON.
    @PostMapping("/login") public Identity login(@RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        LoginResult result = auth.login(body.username, body.password, body.remember);
        cookie(response, result.token(), body.remember ? result.seconds() : -1);
        return result.user();
    }
    // Phát challenge và gửi mã khôi phục qua email đã đăng ký.
    @PostMapping("/forgot-password") public Recovery forgot(@RequestBody EmailRequest body, HttpServletRequest request) {
        return auth.sendCode(body.email);
    }
    // Đổi mật khẩu thành công sẽ xóa cookie phiên hiện tại.
    @PostMapping("/reset-password") public Map<String, String> reset(@RequestBody ResetRequest body, HttpServletRequest request, HttpServletResponse response) {
        auth.reset(body.email, body.challenge, body.code, body.password, body.confirmation);
        cookie(response, "", 0);
        return Map.of("message", "Đặt lại mật khẩu thành công");
    }
    public record ChangePasswordRequest(String currentPassword, String password, String confirmation) {}

    @PostMapping("/change-password")
    public Map<String, String> changePassword(@AuthenticationPrincipal Identity identity,
            @RequestBody ChangePasswordRequest body) {
        auth.changePassword(identity, body.currentPassword(), body.password(), body.confirmation());
        return Map.of("message", "Đổi mật khẩu thành công.");
    }

    // Trả danh tính đã được bộ lọc phiên xác thực.
    @GetMapping("/me") public Identity me(@AuthenticationPrincipal Identity identity) { return identity; }
    // Thu hồi token phía server và xóa cookie phía trình duyệt.
    @PostMapping("/logout") public Map<String, String> logout(HttpServletRequest request, HttpServletResponse response) {
        auth.logout(SessionFilter.token(request)); cookie(response, "", 0); return Map.of("message", "Đã đăng xuất");
    }
    // Cookie chỉ gửi trên đường /api, HttpOnly và Secure theo cấu hình môi trường.
    private void cookie(HttpServletResponse response, String value, long seconds) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("GCMB_SESSION", value).httpOnly(true).secure(policy.secureCookie()).sameSite("Lax").path("/api").maxAge(seconds).build().toString());
    }
}
