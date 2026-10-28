/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: Cung cấp API xem danh sách tài khoản, lấy dữ liệu lựa chọn và tạo tài khoản nhân viên.
 */

package com.gcmb.backend.accounts.controller;

import com.gcmb.backend.accounts.dto.AccountDtos.*;
import com.gcmb.backend.accounts.service.AccountService;
import com.gcmb.backend.auth.dto.Identity;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({ "/api/v1/head-office/accounts", "/api/v1/branch-management/accounts" })
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @GetMapping
    public AccountPage list(
        @RequestParam(defaultValue = "") String q,
        @RequestParam(defaultValue = "") String role,
        @RequestParam(required = false) UUID branchId,
        @RequestParam(defaultValue = "") String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @AuthenticationPrincipal Identity actor
    ) {
        return service.list(q, role, branchId, status, page, size, actor);
    }

    @GetMapping("/options")
    public Options options(@AuthenticationPrincipal Identity actor) {
        return service.options(actor);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountView create(
        @RequestBody CreateAccount input,
        @AuthenticationPrincipal Identity actor
    ) {
        return service.create(input, actor);
    }
}
