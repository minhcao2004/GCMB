/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 27/10/2026
 * Description: Reuses User Account identities and labels seeded by Flyway V14 and V15.
 */
package com.gcmb.backend.manual.useraccount;

import com.gcmb.backend.accounts.dto.AccountDtos.AccountView;
import java.util.List;
import java.util.UUID;

public final class UserAccountDemoData {
    public static final UUID RECEPTIONIST_ID = UUID.fromString("47570000-0000-4000-8000-000000000001");
    public static final UUID BRANCH_MANAGER_ID = UUID.fromString("47570000-0000-4000-8000-000000000003");
    public static final UUID HEAD_OFFICE_MANAGER_ID = UUID.fromString("47570000-0000-4000-8000-000000000005");
    public static final UUID ACCOUNTANT_ID = UUID.fromString("47570000-0000-4000-8000-000000000006");
    public static final UUID NEW_ACCOUNT_ID = UUID.fromString("47570000-0000-4000-8000-000000000101");
    public static final UUID AVAILABLE_EMPLOYEE_ID = UUID.fromString("47570000-0000-4000-8000-000000000111");
    public static final UUID HOA_BANG_BRANCH_ID = UUID.fromString("47570000-0000-4000-8000-000000000201");
    public static final UUID NLB_BRANCH_ID = UUID.fromString("47570000-0000-4000-8000-000000000202");
    public static final UUID INACTIVE_BRANCH_ID = UUID.fromString("34370000-0000-4000-8000-000000000001");
    public static final UUID ROLE_ID = UUID.fromString("47570000-0000-4000-8000-000000000301");

    private UserAccountDemoData() {
    }

    public static AccountView receptionist() {
        return account(RECEPTIONIST_ID, "Lễ tân - Hoa Bằng", "GENZ-REC-HOA-BANG",
                "letan.hoabang", "letan.hoabang@example.invalid", "RECEPTIONIST",
                HOA_BANG_BRANCH_ID, "GenZ Cinema Hoa Bằng", "ACTIVE", List.of());
    }

    public static AccountView branchManager() {
        return account(BRANCH_MANAGER_ID, "Quản lý cơ sở - Hoa Bằng", "GENZ-MGR-HOA-BANG",
                "qlcs.hoabang", "qlcs.hoabang@example.invalid", "BRANCH_MANAGER",
                HOA_BANG_BRANCH_ID, "GenZ Cinema Hoa Bằng", "ACTIVE", List.of(HOA_BANG_BRANCH_ID));
    }

    public static AccountView headOfficeManager() {
        return account(HEAD_OFFICE_MANAGER_ID, "Quản lý cấp cao", "GENZ-HOM",
                "qlcc", "qlcc@example.invalid", "HEAD_OFFICE_MANAGER",
                null, "Toàn chuỗi", "ACTIVE", List.of());
    }

    public static AccountView accountant() {
        return account(ACCOUNTANT_ID, "Kế toán", "GENZ-ACC",
                "ketoan", "ketoan@example.invalid", "ACCOUNTANT",
                null, "Toàn chuỗi", "ACTIVE", List.of());
    }

    public static AccountView account(UUID id, String employeeName, String employeeCode,
            String username, String email, String role, UUID branchId, String branchName,
            String status, List<UUID> managedBranches) {
        return new AccountView(id, employeeName, employeeCode, username, email, role,
                branchId, branchName, status, managedBranches);
    }
}
