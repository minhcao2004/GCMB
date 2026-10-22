/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: Cung cấp API xem danh sách tài khoản, lấy dữ liệu lựa chọn và tạo tài khoản nhân viên.
 */

package com.gcmb.backend.administration.accounts.controller;

import com.gcmb.backend.administration.accounts.dto.AccountDtos.*;
import com.gcmb.backend.administration.accounts.service.AccountService;
import com.gcmb.backend.administration.auth.dto.response.Identity;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/head-office/accounts")
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
            @RequestParam(defaultValue = "10") int size) {
        return service.list(q, role, branchId, status, page, size);
    }

    @GetMapping("/options")
    public Options options() {
        return service.options();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountView create(
            @RequestBody CreateAccount input,
            @AuthenticationPrincipal Identity actor) {
        return service.create(input, actor.id());
    }
}
