/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: API xem danh sách và chi tiết nhật ký dành cho quản lý cấp cao.
 */
package com.gcmb.backend.audit.controller;

import com.gcmb.backend.audit.dto.AuditDtos.*;
import com.gcmb.backend.audit.service.AuditService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/head-office/audit-logs")
public class AuditController {

    private final AuditService service;

    public AuditController(AuditService service) {
        this.service = service;
    }

    @GetMapping
    public AuditPage list(
        @RequestParam(defaultValue = "") String q,
        @RequestParam(defaultValue = "") String action,
        @RequestParam(required = false) UUID branchId,
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        return service.list(q, action, branchId, from, to, page, size);
    }

    @GetMapping("/actions")
    public List<String> actions() {
        return service.actions();
    }

    @GetMapping("/{id}")
    public AuditDetail detail(@PathVariable UUID id) {
        return service.detail(id);
    }
}
