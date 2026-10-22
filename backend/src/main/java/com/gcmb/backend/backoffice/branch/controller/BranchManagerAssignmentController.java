/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 14/10/2026
 * Description: API UC-09–13 quản lý thông tin, trạng thái và quản lý phụ trách cơ sở.
 */
package com.gcmb.backend.administration.branch.controller;

import com.gcmb.backend.administration.audit.service.MutationAudit;
import com.gcmb.backend.administration.auth.dto.response.Identity;
import com.gcmb.backend.administration.branch.dto.BranchDtos.BranchCommand;
import com.gcmb.backend.administration.branch.dto.BranchDtos.StatusCommand;
import com.gcmb.backend.administration.branch.dto.BranchManagerAssignmentCommand;
import com.gcmb.backend.administration.branch.service.BranchAdministrationService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/head-office/branches")
public class BranchManagerAssignmentController {
    private final BranchAdministrationService service;
    private final MutationAudit audit;

    public BranchManagerAssignmentController(BranchAdministrationService service, MutationAudit audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping
    public List<Map<String, Object>> branches() {
        return service.list();
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody BranchCommand body) {
        return audit.record("CREATE_BRANCH", "BRANCH", null,
            () -> service.create(body), result -> (UUID) result.get("branchId"));
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable UUID id, @RequestBody BranchCommand body) {
        return audit.record("UPDATE_BRANCH", "BRANCH", id,
            () -> service.update(id, body), result -> id);
    }

    @GetMapping("/{id}/blockers")
    public List<Map<String, Object>> blockers(@PathVariable UUID id) {
        return service.blockers(id);
    }

    @PostMapping("/{id}/deactivate")
    public Map<String, Object> deactivate(@PathVariable UUID id, @RequestBody StatusCommand body) {
        return audit.record("DEACTIVATE_BRANCH", "BRANCH", id,
            () -> service.deactivate(id, body), result -> id);
    }

    @GetMapping("/managers")
    public List<Map<String, Object>> managers() {
        return service.managers();
    }

    @PutMapping("/{branchId}/manager")
    public Map<String, Object> assign(@PathVariable UUID branchId,
            @RequestBody BranchManagerAssignmentCommand body, @AuthenticationPrincipal Identity who) {
        return service.assign(branchId, body == null ? null : body.userId(), who);
    }

    @GetMapping("/{id}/staff")
    public List<Map<String, Object>> staff(@PathVariable UUID id) {
        return service.staff(id);
    }

    @GetMapping("/{id}/history")
    public List<Map<String, Object>> history(@PathVariable UUID id) {
        return service.history(id);
    }
}
