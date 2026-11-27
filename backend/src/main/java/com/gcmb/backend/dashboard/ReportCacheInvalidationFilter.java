/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 27/11/2026
 * Description: Xóa cache báo cáo trước và sau mỗi request ghi dữ liệu để dashboard không hiển thị số liệu cũ.
 */

package com.gcmb.backend.dashboard;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ReportCacheInvalidationFilter extends OncePerRequestFilter {

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final ReportCache reportCache;

    public ReportCacheInvalidationFilter(ReportCache reportCache) {
        this.reportCache = reportCache;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Đăng nhập và hỏi trợ lý AI dùng POST nhưng không đổi dữ liệu nghiệp vụ.
        return READ_METHODS.contains(request.getMethod())
            || !path.startsWith("/api/")
            || path.startsWith("/api/v1/auth/")
            || path.endsWith("/business-assistant/chat");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        reportCache.clear();
        try {
            chain.doFilter(request, response);
        } finally {
            reportCache.clear();
        }
    }
}
