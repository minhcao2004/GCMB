package com.gcmb.backend.administration.auth.controller;

import com.gcmb.backend.administration.auth.dto.request.LoginRequest;
import com.gcmb.backend.administration.auth.dto.request.EmailRequest;
import com.gcmb.backend.administration.auth.dto.request.ResetRequest;
import com.gcmb.backend.administration.auth.dto.response.Identity;
import com.gcmb.backend.administration.auth.dto.response.Recovery;
import com.gcmb.backend.administration.auth.service.LoginResult;

import com.gcmb.backend.administration.auth.service.AuthService;
import com.gcmb.backend.administration.auth.security.SessionFilter;
import com.gcmb.backend.administration.auth.config.AuthSettings;

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
    public AuthController(AuthService auth, AuthSettings policy) { this.auth = auth; this.policy = policy; }
    @GetMapping("/csrf") public Map<String, String> csrf(CsrfToken token) { return Map.of("token", token.getToken(), "headerName", token.getHeaderName()); }
    @GetMapping("/health") public Map<String, String> health() { return Map.of("status", "UP"); }
    @PostMapping("/login") public Identity login(@RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        LoginResult result = auth.login(body.username, body.password, body.remember);
        cookie(response, result.token(), body.remember ? result.seconds() : -1);
        return result.user();
    }
    @PostMapping("/forgot-password") public Recovery forgot(@RequestBody EmailRequest body, HttpServletRequest request) {
        return auth.sendCode(body.email);
    }
    @PostMapping("/reset-password") public Map<String, String> reset(@RequestBody ResetRequest body, HttpServletRequest request, HttpServletResponse response) {
        auth.reset(body.email, body.challenge, body.code, body.password, body.confirmation);
        cookie(response, "", 0);
        return Map.of("message", "Đặt lại mật khẩu thành công");
    }
    @GetMapping("/me") public Identity me(@AuthenticationPrincipal Identity identity) { return identity; }
    @PostMapping("/logout") public Map<String, String> logout(HttpServletRequest request, HttpServletResponse response) {
        auth.logout(SessionFilter.token(request)); cookie(response, "", 0); return Map.of("message", "Đã đăng xuất");
    }
    private void cookie(HttpServletResponse response, String value, long seconds) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("GCMB_SESSION", value).httpOnly(true).secure(policy.secureCookie()).sameSite("Lax").path("/api").maxAge(seconds).build().toString());
    }
}
