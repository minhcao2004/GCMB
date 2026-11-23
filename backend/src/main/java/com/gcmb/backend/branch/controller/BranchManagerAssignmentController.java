/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 14/10/2026
 * Description: API UC-09–13 quản lý thông tin, trạng thái và quản lý phụ trách cơ sở.
 */
package com.gcmb.backend.branch.controller;

import com.gcmb.backend.audit.service.MutationAudit;
import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.branch.dto.BranchDtos.BranchCommand;
import com.gcmb.backend.branch.dto.BranchDtos.StatusCommand;
import com.gcmb.backend.branch.dto.BranchManagerAssignmentCommand;
import com.gcmb.backend.branch.service.BranchAdministrationService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/head-office/branches")
public class BranchManagerAssignmentController {

    private final BranchAdministrationService branchAdministrationService;
    private final MutationAudit mutationAudit;

    public BranchManagerAssignmentController(
        BranchAdministrationService branchAdministrationService,
        MutationAudit mutationAudit
    ) {
        this.branchAdministrationService = branchAdministrationService;
        this.mutationAudit = mutationAudit;
    }

    @GetMapping
    public List<Map<String, Object>> branches() {
        return branchAdministrationService.list();
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable UUID id) {
        return branchAdministrationService.get(id);
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody BranchCommand body) {
        return mutationAudit.record(
            "CREATE_BRANCH",
            "BRANCH",
            null,
            () -> branchAdministrationService.create(body),
            result -> (UUID) result.get("branchId")
        );
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable UUID id, @RequestBody BranchCommand body) {
        return mutationAudit.record(
            "UPDATE_BRANCH",
            "BRANCH",
            id,
            () -> branchAdministrationService.update(id, body),
            result -> id
        );
    }

    @GetMapping("/{id}/blockers")
    public List<Map<String, Object>> blockers(@PathVariable UUID id) {
        return branchAdministrationService.blockers(id);
    }

    @PostMapping("/{id}/deactivate")
    public Map<String, Object> deactivate(@PathVariable UUID id, @RequestBody StatusCommand body) {
        return mutationAudit.record(
            "DEACTIVATE_BRANCH",
            "BRANCH",
            id,
            () -> branchAdministrationService.deactivate(id, body),
            result -> id
        );
    }

    @PostMapping("/{id}/reactivate")
    public Map<String, Object> reactivate(@PathVariable UUID id) {
        return mutationAudit.record(
            "REACTIVATE_BRANCH",
            "BRANCH",
            id,
            () -> branchAdministrationService.reactivate(id),
            result -> id
        );
    }

    @GetMapping("/managers")
    public List<Map<String, Object>> managers() {
        return branchAdministrationService.managers();
    }

    @PutMapping("/{branchId}/manager")
    public Map<String, Object> assign(
        @PathVariable UUID branchId,
        @RequestBody BranchManagerAssignmentCommand body,
        @AuthenticationPrincipal Identity who
    ) {
        return branchAdministrationService.assign(branchId, body == null ? null : body.userId(), who);
    }

    @GetMapping("/{id}/staff")
    public List<Map<String, Object>> staff(@PathVariable UUID id) {
        return branchAdministrationService.staff(id);
    }

    @GetMapping("/{id}/history")
    public List<Map<String, Object>> history(@PathVariable UUID id) {
        return branchAdministrationService.history(id);
    }
}
