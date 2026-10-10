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
    public SessionFilter(AuthService auth) { this.auth = auth; }
    public static String token(HttpServletRequest request) {
        if (request.getCookies() != null) for (var cookie : request.getCookies()) if ("GCMB_SESSION".equals(cookie.getName())) return cookie.getValue();
        return null;
    }
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
