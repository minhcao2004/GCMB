/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 26/10/2026
 * Updated: 27/10/2026
 * Description: API quản lý hồ sơ nhân viên và phân công cơ sở (UC-77–82).
 */

package com.gcmb.backend.workforce.controller;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.workforce.dto.StaffDtos.*;
import com.gcmb.backend.workforce.service.StaffService;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({ "/api/v1/head-office/staff", "/api/v1/branch-management/staff" })
public class StaffController {

    private final StaffService service;

    public StaffController(StaffService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> list(
        @AuthenticationPrincipal Identity actor,
        @RequestParam(defaultValue = "") String q,
        @RequestParam(defaultValue = "") String position,
        @RequestParam(defaultValue = "") String status,
        @RequestParam(required = false) UUID branchId,
        @RequestParam(defaultValue = "false") boolean unassigned,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        return service.list(actor, q, position, status, branchId, unassigned, page, size);
    }

    @GetMapping("/options")
    public Map<String, Object> options(@AuthenticationPrincipal Identity actor) {
        return service.options(actor);
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(
        @AuthenticationPrincipal Identity actor,
        @PathVariable UUID id
    ) {
        return service.detail(actor, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(
        @AuthenticationPrincipal Identity actor,
        @RequestBody StaffCommand input
    ) {
        return service.create(actor, input);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(
        @AuthenticationPrincipal Identity actor,
        @PathVariable UUID id,
        @RequestBody StaffCommand input
    ) {
        return service.update(actor, id, input);
    }

    @PostMapping("/{id}/status")
    public Map<String, Object> status(
        @AuthenticationPrincipal Identity actor,
        @PathVariable UUID id,
        @RequestBody StatusCommand input
    ) {
        return service.status(actor, id, input);
    }

    @PostMapping("/{id}/branch")
    public Map<String, Object> assign(
        @AuthenticationPrincipal Identity actor,
        @PathVariable UUID id,
        @RequestBody AssignmentCommand input
    ) {
        return service.assign(actor, id, input, false);
    }

    @PostMapping("/{id}/transfers")
    public Map<String, Object> transfer(
        @AuthenticationPrincipal Identity actor,
        @PathVariable UUID id,
        @RequestBody AssignmentCommand input
    ) {
        return service.assign(actor, id, input, true);
    }
}
