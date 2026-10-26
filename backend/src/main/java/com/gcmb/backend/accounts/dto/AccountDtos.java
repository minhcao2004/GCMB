/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: Định nghĩa dữ liệu yêu cầu và phản hồi cho tài khoản, hồ sơ nhân viên, vai trò, cơ sở và phân trang.
 */

package com.gcmb.backend.accounts.dto;

import java.util.List;
import java.util.UUID;

public final class AccountDtos {
    private AccountDtos() {
    }

    public record CreateAccount(
        UUID employeeId,
        String username,
        String password,
        String email,
        String role,
        UUID branchId
    ) {
    }

    public record AccountView(
        UUID id,
        String employeeName,
        String employeeCode,
        String username,
        String email,
        String role,
        UUID branchId,
        String branchName,
        String status,
        List<UUID> managedBranchIds
    ) {
        public AccountView(UUID id, String employeeName, String employeeCode, String username,
                String email, String role, UUID branchId, String branchName, String status) {
            this(id, employeeName, employeeCode, username, email, role, branchId, branchName, status, List.of());
        }
    }

    public record Option(String id, String name) {
    }

    public record EmployeeOption(UUID id, String name, String code) {
    }

    public record Options(List<EmployeeOption> employees, List<Option> roles, List<Option> branches) {
    }

    public record AccountPage(List<AccountView> items, long total, int page, int size) {
    }
}
