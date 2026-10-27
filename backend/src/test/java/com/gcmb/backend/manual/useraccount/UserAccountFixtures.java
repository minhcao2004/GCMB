/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 27/10/2026
 * Description: Builds Mockito fixtures around User Account production services.
 */
package com.gcmb.backend.manual.useraccount;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gcmb.backend.accounts.dto.AccountAccessDtos.SetRole;
import com.gcmb.backend.accounts.dto.AccountDtos.AccountPage;
import com.gcmb.backend.accounts.dto.AccountDtos.AccountView;
import com.gcmb.backend.accounts.dto.AccountDtos.CreateAccount;
import com.gcmb.backend.accounts.repository.AccountAccessRepository;
import com.gcmb.backend.accounts.repository.AccountRepository;
import com.gcmb.backend.accounts.service.AccountAccessService;
import com.gcmb.backend.accounts.service.AccountService;
import com.gcmb.backend.auth.security.AuthCrypto;
import com.gcmb.backend.branch.service.BranchAdministrationService;
import java.util.List;
import java.util.UUID;

final class UserAccountFixtures {
    private UserAccountFixtures() {
    }

    static CreateFixture create(CreateAccount input) {
        AccountRepository repository = mock(AccountRepository.class);
        AuthCrypto crypto = mock(AuthCrypto.class);
        BranchAdministrationService branches = mock(BranchAdministrationService.class);
        when(repository.lockAvailableEmployee(any(UUID.class))).thenReturn(true);
        when(repository.roleId(anyString())).thenReturn(UserAccountDemoData.ROLE_ID);
        when(repository.activeBranch(any(UUID.class))).thenReturn(true);
        when(repository.exists(anyString(), anyString())).thenReturn(false);
        when(crypto.encode(anyString())).thenReturn("{pbkdf2-gcmb}test-hash");
        when(repository.insert(any(CreateAccount.class), anyString(), anyString(), anyString(),
                any(UUID.class), any(UUID.class))).thenReturn(UserAccountDemoData.NEW_ACCOUNT_ID);
        when(repository.find(UserAccountDemoData.NEW_ACCOUNT_ID)).thenAnswer(call -> {
            String role = input.role();
            boolean chain = List.of("HEAD_OFFICE_MANAGER", "ACCOUNTANT").contains(role);
            UUID branchId = chain ? null : input.branchId();
            return UserAccountDemoData.account(UserAccountDemoData.NEW_ACCOUNT_ID,
                    "Nhân viên mới", "GENZ-NEW", input.username(),
                    input.email() == null ? null : input.email().trim().toLowerCase(), role,
                    branchId, chain ? "Toàn chuỗi" : "GenZ Cinema Hoa Bằng", "ACTIVE",
                    "BRANCH_MANAGER".equals(role) && branchId != null ? List.of(branchId) : List.of());
        });
        return new CreateFixture(repository, crypto, branches,
                new AccountService(repository, crypto, branches), input);
    }

    static ListFixture list(AccountPage result) {
        AccountRepository repository = mock(AccountRepository.class);
        when(repository.list(anyString(), anyString(), any(), anyString(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(result);
        return new ListFixture(repository,
                new AccountService(repository, mock(AuthCrypto.class), mock(BranchAdministrationService.class)));
    }

    static AccessFixture access(AccountView before, AccountView after) {
        AccountRepository accounts = mock(AccountRepository.class);
        AccountAccessRepository access = mock(AccountAccessRepository.class);
        BranchAdministrationService branches = mock(BranchAdministrationService.class);
        when(access.lockManagement(any(UUID.class))).thenReturn(true);
        when(access.lockAccount(any(UUID.class))).thenReturn(true);
        when(accounts.find(before.id())).thenReturn(before, after);
        when(accounts.roleId(anyString())).thenReturn(UserAccountDemoData.ROLE_ID);
        when(accounts.activeBranch(any(UUID.class))).thenReturn(true);
        return new AccessFixture(accounts, access, branches,
                new AccountAccessService(accounts, access, branches), before, after);
    }

    static AccountView changed(AccountView before, SetRole input) {
        boolean chain = List.of("HEAD_OFFICE_MANAGER", "ACCOUNTANT").contains(input.role());
        List<UUID> managed = input.branchIds() == null ? List.of() : input.branchIds();
        UUID branchId = chain ? null : managed.isEmpty() ? input.branchId() : managed.get(0);
        String branchName = chain ? "Toàn chuỗi" : branchId == null ? "Chưa phân công" : "GenZ Cinema Hoa Bằng";
        return UserAccountDemoData.account(before.id(), before.employeeName(), before.employeeCode(),
                before.username(), before.email(), input.role(), branchId, branchName,
                before.status(), managed);
    }

    record CreateFixture(AccountRepository repository, AuthCrypto crypto,
            BranchAdministrationService branches, AccountService service, CreateAccount input) {
    }

    record ListFixture(AccountRepository repository, AccountService service) {
    }

    record AccessFixture(AccountRepository accounts, AccountAccessRepository access,
            BranchAdministrationService branches, AccountAccessService service,
            AccountView before, AccountView after) {
    }
}
