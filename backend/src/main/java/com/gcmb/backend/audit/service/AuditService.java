/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: Kiểm tra bộ lọc và cung cấp nhật ký hệ thống chỉ đọc.
 */
package com.gcmb.backend.audit.service;

import com.gcmb.backend.audit.dto.AuditDtos.*;
import com.gcmb.backend.audit.repository.AuditRepository;
import com.gcmb.backend.auth.exception.AuthFailure;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuditService {

    private final AuditRepository repository;

    public AuditService(AuditRepository repository) {
        this.repository = repository;
    }

    public AuditPage list(
        String q,
        String action,
        UUID branchId,
        LocalDate from,
        LocalDate to,
        int page,
        int size
    ) {
        if (page < 0 || size < 1 || size > 100 || q.length() > 200 || action.length() > 60) {
            throw new AuthFailure(400, "form", "Bộ lọc hoặc phân trang không hợp lệ");
        }
        // BR-163; MSG106.
        if (from != null && to != null && from.isAfter(to)) {
            throw new AuthFailure(
                400,
                "form",
                "Ngày bắt đầu lọc phải bằng hoặc trước ngày kết thúc lọc."
            );
        }
        if (to != null && to.getYear() > 9999) {
            throw new AuthFailure(400, "form", "Ngày kết thúc không hợp lệ");
        }
        return repository.list(q.trim(), action, branchId, from, to, page, size);
    }

    public List<String> actions() {
        return repository.actions();
    }

    public AuditDetail detail(UUID id) {
        AuditDetail detail = repository.detail(id);
        if (detail == null) {
            throw new AuthFailure(404, "form", "Không tìm thấy nhật ký");
        }
        return detail;
    }
}
