/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: Kiểm thử bảo vệ tài khoản quản lý và giao dịch đổi quyền, thu hồi truy cập.
 */
package com.gcmb.backend.administration.accounts;

import com.gcmb.backend.administration.accounts.dto.AccountAccessDtos.*;
import com.gcmb.backend.administration.accounts.dto.AccountDtos.AccountView;
import com.gcmb.backend.administration.accounts.repository.AccountAccessRepository;
import com.gcmb.backend.administration.accounts.repository.AccountRepository;
import com.gcmb.backend.administration.accounts.service.AccountAccessService;
import com.gcmb.backend.administration.auth.exception.AuthFailure;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountAccessServiceTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final AccountAccessRepository access = mock(AccountAccessRepository.class);
    private final AccountAccessService service = new AccountAccessService(accounts, access);
    private final UUID actor = UUID.randomUUID();
    private final UUID target = UUID.randomUUID();
    private final UUID branch = UUID.randomUUID();
    private final UUID roleId = UUID.randomUUID();
    private AccountView before;

    @BeforeEach
    void setup() {
        before = view("RECEPTIONIST", "ACTIVE", branch);
        when(access.lockManagement(actor)).thenReturn(true);
        when(access.lockAccount(target)).thenReturn(true);
        when(accounts.find(target)).thenReturn(before);
        when(accounts.roleId("ACCOUNTANT")).thenReturn(roleId);
        when(accounts.roleId("RECEPTIONIST")).thenReturn(roleId);
    }

    private AccountView view(String role, String status, UUID scope) {
        return new AccountView(target, "Nhân viên", "NV-01", "staff", "staff@example.com",
                role, scope, scope == null ? "Toàn chuỗi" : "Hồ Tây", status);
    }

    @Test
    void changingRoleRevokesSessionsAndRecordsBeforeAfter() {
        AccountView after = view("ACCOUNTANT", "ACTIVE", null);
        when(accounts.find(target)).thenReturn(before, after);
        assertEquals(after, service.setRole(target, new SetRole("ACCOUNTANT", null), actor));
        verify(access).setRole(target, roleId, null);
        verify(access).revokeTokens(target);
        verify(access).audit(actor, "SET_ACCOUNT_ROLE", before, after, null);
    }

    @Test
    void deactivationRequiresReasonAndRecordsIt() {
        assertThrows(AuthFailure.class, () -> service.deactivate(target, new Deactivate("  "), actor));
        verify(access, never()).deactivate(any());
        AccountView after = view("RECEPTIONIST", "REVOKED", branch);
        when(accounts.find(target)).thenReturn(before, after);
        service.deactivate(target, new Deactivate("  Nghỉ việc  "), actor);
        verify(access).deactivate(target);
        verify(access).revokeTokens(target);
        verify(access).audit(actor, "DEACTIVATE_USER_ACCOUNT", before, after, "Nghỉ việc");
    }

    @Test
    void cannotChangeOrRevokeSelf() {
        assertThrows(AuthFailure.class, () -> service.setRole(actor, new SetRole("ACCOUNTANT", null), actor));
        assertThrows(AuthFailure.class, () -> service.deactivate(actor, new Deactivate("test"), actor));
        verifyNoInteractions(access);
    }

    @Test
    void rejectsStaleManagerAndAlreadyRevokedAccount() {
        when(access.lockManagement(actor)).thenReturn(false);
        assertEquals(403, assertThrows(AuthFailure.class,
                () -> service.deactivate(target, new Deactivate("test"), actor)).status());
        when(access.lockManagement(actor)).thenReturn(true);
        when(accounts.find(target)).thenReturn(view("RECEPTIONIST", "REVOKED", branch));
        assertEquals(409, assertThrows(AuthFailure.class,
                () -> service.deactivate(target, new Deactivate("test"), actor)).status());
        verify(access, never()).deactivate(any());
    }

    @Test
    void validatesBranchAndSkipsUnchangedPermissions() {
        assertThrows(AuthFailure.class,
                () -> service.setRole(target, new SetRole("ACCOUNTANT", branch), actor));
        assertThrows(AuthFailure.class,
                () -> service.setRole(target, new SetRole("RECEPTIONIST", branch), actor));
        when(accounts.activeBranch(branch)).thenReturn(true);
        assertEquals(before, service.setRole(target, new SetRole("RECEPTIONIST", branch), actor));
        verify(access, never()).setRole(any(), any(), any());
        verify(access, never()).revokeTokens(any());
    }
}
