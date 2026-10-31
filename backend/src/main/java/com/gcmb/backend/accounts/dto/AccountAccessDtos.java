/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: Dữ liệu thay đổi vai trò, thu hồi truy cập và quyền theo vai trò.
 */
package com.gcmb.backend.accounts.dto;

import java.util.UUID;
import java.util.List;

public final class AccountAccessDtos {

    private AccountAccessDtos() {}

    public record SetRole(String role, UUID branchId, List<UUID> branchIds) {
        public SetRole(String role, UUID branchId) {
            this(role, branchId, null);
        }
    }

    public record Deactivate(String reason) {}

    public record PermissionView(String code, String name, String scope) {}
}
