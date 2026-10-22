/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: Dữ liệu thay đổi vai trò, thu hồi truy cập và quyền theo vai trò.
 */
package com.gcmb.backend.administration.accounts.dto;

import java.util.UUID;

public final class AccountAccessDtos {
    private AccountAccessDtos() {
    }

    public record SetRole(String role, UUID branchId) {
    }

    public record Deactivate(String reason) {
    }

    public record PermissionView(String code, String name, String scope) {
    }
}
