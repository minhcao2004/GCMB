/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 13/10/2026
 * Description: Unit Test Case cho đổi vai trò, thu hồi truy cập và xem quyền theo UC-06, UC-07.
 */

package com.gcmb.backend.administration.accounts.service;

import com.gcmb.backend.administration.accounts.dto.AccountAccessDtos.Deactivate;
import com.gcmb.backend.administration.accounts.dto.AccountAccessDtos.PermissionView;
import com.gcmb.backend.administration.accounts.dto.AccountAccessDtos.SetRole;
import com.gcmb.backend.administration.accounts.dto.AccountDtos.AccountView;
import com.gcmb.backend.administration.accounts.repository.AccountAccessRepository;
import com.gcmb.backend.administration.accounts.repository.AccountRepository;
import com.gcmb.backend.administration.auth.exception.AuthFailure;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountAccessServiceTest {
    @Mock
    private AccountRepository accounts;

    @Mock
    private AccountAccessRepository access;

    private AccountAccessService service;
    private UUID actorId;
    private UUID targetId;
    private UUID branchId;
    private UUID roleId;

    @BeforeEach
    void setUp() {
        service = new AccountAccessService(accounts, access);
        actorId = UUID.randomUUID();
        targetId = UUID.randomUUID();
        branchId = UUID.randomUUID();
        roleId = UUID.randomUUID();
    }

    @Test
    @DisplayName("UTC-ACL-001 setRole assigns chain role, revokes sessions and audits snapshots")
    void setRoleAssignsChainRoleRevokesSessionsAndAudits() {
        AccountView before = account("RECEPTIONIST", branchId, "ACTIVE");
        AccountView after = account("ACCOUNTANT", null, "ACTIVE");
        arrangeEditable(before, after);
        when(accounts.roleId("ACCOUNTANT")).thenReturn(roleId);

        AccountView actual = service.setRole(targetId, new SetRole("ACCOUNTANT", null), actorId);

        assertSame(after, actual);
        verify(access).setRole(targetId, roleId, null);
        verify(access).revokeTokens(targetId);
        verify(access).audit(actorId, "SET_ACCOUNT_ROLE", before, after, null);
        verify(accounts, never()).activeBranch(any());
    }

    @Test
    @DisplayName("UTC-ACL-002 setRole assigns branch role only for active branch")
    void setRoleAssignsBranchRoleForActiveBranch() {
        AccountView before = account("ACCOUNTANT", null, "ACTIVE");
        AccountView after = account("RECEPTIONIST", branchId, "ACTIVE");
        arrangeEditable(before, after);
        when(accounts.roleId("RECEPTIONIST")).thenReturn(roleId);
        when(accounts.activeBranch(branchId)).thenReturn(true);

        assertSame(
                after,
                service.setRole(targetId, new SetRole("RECEPTIONIST", branchId), actorId));

        verify(access).setRole(targetId, roleId, branchId);
        verify(access).revokeTokens(targetId);
        verify(access).audit(actorId, "SET_ACCOUNT_ROLE", before, after, null);
    }

    @Test
    @DisplayName("UTC-ACL-003 setRole returns current account when role and scope are unchanged")
    void setRoleSkipsMutationWhenAssignmentIsUnchanged() {
        AccountView before = account("RECEPTIONIST", branchId, "ACTIVE");
        arrangeEditable(before);
        when(accounts.roleId("RECEPTIONIST")).thenReturn(roleId);
        when(accounts.activeBranch(branchId)).thenReturn(true);

        assertSame(
                before,
                service.setRole(targetId, new SetRole("RECEPTIONIST", branchId), actorId));

        verify(access, never()).setRole(any(), any(), any());
        verify(access, never()).revokeTokens(any());
        verify(access, never()).audit(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("UTC-ACL-004 manager cannot change or deactivate own account")
    void editableRejectsSelfModificationBeforeDependencyCall() {
        AuthFailure roleFailure = assertThrows(
                AuthFailure.class,
                () -> service.setRole(actorId, new SetRole("ACCOUNTANT", null), actorId));
        AuthFailure deactivateFailure = assertThrows(
                AuthFailure.class,
                () -> service.deactivate(actorId, new Deactivate("Resigned"), actorId));

        assertEquals(400, roleFailure.status());
        assertEquals(400, deactivateFailure.status());
        verifyNoInteractions(accounts, access);
    }

    @Test
    @DisplayName("UTC-ACL-005 stale manager permission rejects account mutation")
    void editableRejectsActorWhoLostManagementPermission() {
        when(access.lockManagement(actorId)).thenReturn(false);

        AuthFailure failure = assertThrows(
                AuthFailure.class,
                () -> service.setRole(targetId, new SetRole("ACCOUNTANT", null), actorId));

        assertEquals(403, failure.status());
        verify(access, never()).lockAccount(any());
        verifyNoInteractions(accounts);
    }

    @Test
    @DisplayName("UTC-ACL-006 missing target account returns 404")
    void editableRejectsMissingTargetAccount() {
        when(access.lockManagement(actorId)).thenReturn(true);
        when(access.lockAccount(targetId)).thenReturn(false);

        AuthFailure failure = assertThrows(
                AuthFailure.class,
                () -> service.setRole(targetId, new SetRole("ACCOUNTANT", null), actorId));

        assertEquals(404, failure.status());
        verifyNoInteractions(accounts);
    }

    @Test
    @DisplayName("UTC-ACL-007 revoked target account cannot be changed again")
    void editableRejectsAlreadyRevokedTarget() {
        arrangeEditable(account("RECEPTIONIST", branchId, "REVOKED"));

        AuthFailure failure = assertThrows(
                AuthFailure.class,
                () -> service.setRole(targetId, new SetRole("ACCOUNTANT", null), actorId));

        assertEquals(409, failure.status());
        verify(access, never()).setRole(any(), any(), any());
    }

    @Test
    @DisplayName("UTC-ACL-008 setRole rejects missing or unknown role")
    void setRoleRejectsUnknownRole() {
        arrangeEditable(account("RECEPTIONIST", branchId, "ACTIVE"));
        when(accounts.roleId("UNKNOWN")).thenReturn(null);

        AuthFailure failure = assertThrows(
                AuthFailure.class,
                () -> service.setRole(targetId, new SetRole("UNKNOWN", branchId), actorId));

        assertEquals(400, failure.status());
        verify(access, never()).setRole(any(), any(), any());
    }

    static Stream<Arguments> invalidRoleScopes() {
        return Stream.of(
                Arguments.of("chain role with branch", "ACCOUNTANT", UUID.randomUUID(), true),
                Arguments.of("branch role without branch", "RECEPTIONIST", null, true),
                Arguments.of("branch role with inactive branch", "RECEPTIONIST", UUID.randomUUID(), false));
    }

    @ParameterizedTest(name = "UTC-ACL-009 rejects {0}")
    @MethodSource("invalidRoleScopes")
    void setRoleRejectsInvalidScope(String caseName, String role, UUID scope, boolean active) {
        arrangeEditable(account("RECEPTIONIST", branchId, "ACTIVE"));
        when(accounts.roleId(role)).thenReturn(roleId);
        if (scope != null && "RECEPTIONIST".equals(role)) {
            when(accounts.activeBranch(scope)).thenReturn(active);
        }

        AuthFailure failure = assertThrows(
                AuthFailure.class,
                () -> service.setRole(targetId, new SetRole(role, scope), actorId));

        assertEquals(400, failure.status(), caseName);
        verify(access, never()).setRole(any(), any(), any());
        verify(access, never()).revokeTokens(any());
    }

    @Test
    @DisplayName("UTC-ACL-010 deactivate trims reason, revokes sessions and audits snapshots")
    void deactivateTrimsReasonRevokesSessionsAndAudits() {
        AccountView before = account("RECEPTIONIST", branchId, "ACTIVE");
        AccountView after = account("RECEPTIONIST", branchId, "REVOKED");
        arrangeEditable(before, after);

        AccountView actual = service.deactivate(targetId, new Deactivate("  Resigned  "), actorId);

        assertSame(after, actual);
        verify(access).deactivate(targetId);
        verify(access).revokeTokens(targetId);
        verify(access).audit(actorId, "DEACTIVATE_USER_ACCOUNT", before, after, "Resigned");
    }

    static Stream<String> invalidReasons() {
        return Stream.of("<null>", " ", "x".repeat(1001));
    }

    @ParameterizedTest(name = "UTC-ACL-011 rejects invalid reason [{0}]")
    @MethodSource("invalidReasons")
    void deactivateRejectsInvalidReasonBeforeAuthorization(String reasonCase) {
        String reason = "<null>".equals(reasonCase) ? null : reasonCase;

        AuthFailure failure = assertThrows(
                AuthFailure.class,
                () -> service.deactivate(targetId, new Deactivate(reason), actorId));

        assertEquals(400, failure.status());
        verifyNoInteractions(accounts, access);
    }

    @Test
    @DisplayName("UTC-ACL-012 permissions delegates a valid role")
    void permissionsReturnsRepositoryProjectionForValidRole() {
        List<PermissionView> expected = List.of(
                new PermissionView("ACCOUNT_VIEW", "View accounts", "BRANCH"));
        when(accounts.roleId("RECEPTIONIST")).thenReturn(roleId);
        when(access.permissions("RECEPTIONIST")).thenReturn(expected);

        assertSame(expected, service.permissions("RECEPTIONIST"));

        verify(access).permissions("RECEPTIONIST");
    }

    @Test
    @DisplayName("UTC-ACL-013 permissions rejects an invalid role")
    void permissionsRejectsInvalidRole() {
        when(accounts.roleId("UNKNOWN")).thenReturn(null);

        AuthFailure failure = assertThrows(AuthFailure.class, () -> service.permissions("UNKNOWN"));

        assertEquals(400, failure.status());
        verifyNoInteractions(access);
    }

    private void arrangeEditable(AccountView... accountSequence) {
        when(access.lockManagement(actorId)).thenReturn(true);
        when(access.lockAccount(targetId)).thenReturn(true);
        when(accounts.find(targetId)).thenReturn(accountSequence[0], java.util.Arrays.copyOfRange(
                accountSequence, 1, accountSequence.length));
    }

    private AccountView account(String role, UUID scope, String status) {
        return new AccountView(
                targetId,
                "Nguyen Van A",
                "NV-001",
                "staff.rec",
                "staff@example.com",
                role,
                scope,
                scope == null ? "Toan chuoi" : "Ho Tay",
                status);
    }
}
