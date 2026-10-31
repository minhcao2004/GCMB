/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: API đổi vai trò, thu hồi truy cập và xem quyền được cấu hình theo vai trò.
 */
package com.gcmb.backend.accounts.controller;

import com.gcmb.backend.accounts.dto.AccountAccessDtos.*;
import com.gcmb.backend.accounts.dto.AccountDtos.AccountView;
import com.gcmb.backend.accounts.service.AccountAccessService;
import com.gcmb.backend.auth.dto.Identity;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({ "/api/v1/head-office/accounts", "/api/v1/branch-management/accounts" })
public class AccountAccessController {

    private final AccountAccessService service;

    public AccountAccessController(AccountAccessService service) {
        this.service = service;
    }

    @GetMapping("/permissions")
    public List<PermissionView> permissions(@RequestParam String role) {
        return service.permissions(role);
    }

    @PostMapping("/{id}/role")
    public AccountView setRole(
        @PathVariable UUID id,
        @RequestBody SetRole input,
        @AuthenticationPrincipal Identity actor
    ) {
        return service.setRole(id, input, actor.id());
    }

    @PostMapping("/{id}/deactivate")
    public AccountView deactivate(
        @PathVariable UUID id,
        @RequestBody Deactivate input,
        @AuthenticationPrincipal Identity actor
    ) {
        return service.changeStatus(id, input, actor, false);
    }

    @PostMapping("/{id}/reactivate")
    public AccountView reactivate(@PathVariable UUID id, @AuthenticationPrincipal Identity actor) {
        return service.changeStatus(id, null, actor, true);
    }
}
