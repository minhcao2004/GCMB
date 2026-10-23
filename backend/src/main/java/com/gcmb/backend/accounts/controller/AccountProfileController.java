/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: API thông tin cá nhân cho mọi vai trò đã đăng nhập.
 */
package com.gcmb.backend.accounts.controller;

import com.gcmb.backend.accounts.dto.AccountProfileDtos.*;
import com.gcmb.backend.accounts.service.AccountProfileService;
import com.gcmb.backend.auth.dto.Identity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/account/profile")
public class AccountProfileController {
    private final AccountProfileService service;

    public AccountProfileController(AccountProfileService service) {
        this.service = service;
    }

    @GetMapping
    public Profile view(@AuthenticationPrincipal Identity actor) {
        return service.view(actor.id());
    }

    @PostMapping
    public Profile update(
            @AuthenticationPrincipal Identity actor,
            @RequestBody UpdateProfile input) {
        return service.update(actor.id(), input);
    }
}
