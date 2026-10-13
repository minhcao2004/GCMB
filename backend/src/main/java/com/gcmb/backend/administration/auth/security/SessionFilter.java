/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 10/10/2026
 * Description: Đọc cookie phiên đăng nhập và thiết lập thông tin xác thực trong Spring Security.
 */

package com.gcmb.backend.administration.auth.security;

import com.gcmb.backend.administration.auth.dto.response.Identity;

import com.gcmb.backend.administration.auth.service.AuthService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class SessionFilter extends OncePerRequestFilter {
    private final AuthService auth;
    // Dùng service để xác thực token trên từng request được bảo vệ.
    public SessionFilter(AuthService auth) { this.auth = auth; }
    // Lấy token phiên từ cookie mà không đưa token vào URL hoặc log.
    public static String token(HttpServletRequest request) {
        if (request.getCookies() != null) for (var cookie : request.getCookies()) if ("GCMB_SESSION".equals(cookie.getName())) return cookie.getValue();
        return null;
    }
    // Nạp danh tính và quyền vào SecurityContext trước khi request tới controller.
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        try {
            Identity identity = auth.authenticate(token(request));
            if (identity != null) SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(identity, null, List.of(new SimpleGrantedAuthority("ROLE_" + identity.role()))));
        } catch (Exception e) {
            response.setStatus(503); response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"Không thể kiểm tra phiên đăng nhập. Vui lòng thử lại\"}"); return;
        }
        chain.doFilter(request, response);
    }
}
