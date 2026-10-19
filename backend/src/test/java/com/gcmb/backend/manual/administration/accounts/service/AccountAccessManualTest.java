/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 14/10/2026
 * Description: Covers account-access authorization rules for administrative operations.
 */

package com.gcmb.backend.manual.administration.accounts.service;

import com.gcmb.backend.administration.accounts.dto.AccountAccessDtos.Deactivate;
import com.gcmb.backend.administration.accounts.dto.AccountAccessDtos.PermissionView;
import com.gcmb.backend.administration.accounts.dto.AccountAccessDtos.SetRole;
import com.gcmb.backend.administration.accounts.dto.AccountDtos.AccountView;
import com.gcmb.backend.administration.accounts.repository.AccountAccessRepository;
import com.gcmb.backend.administration.accounts.repository.AccountRepository;
import com.gcmb.backend.administration.accounts.service.AccountAccessService;
import com.gcmb.backend.administration.branch.service.BranchAdministrationService;
import com.gcmb.backend.manual.ManualTestCase;
import com.gcmb.backend.manual.ManualTestSuite;
import com.gcmb.backend.manual.ManualTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class AccountAccessManualTest {
    private static final UUID ACTOR_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID TARGET_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID BRANCH_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID ROLE_ID = UUID.fromString("40000000-0000-0000-0000-000000000004");

    private AccountAccessManualTest() {
    }

    public static ManualTestSuite suite() {
        List<ManualTestCase> cases = new ArrayList<>();

        cases.add(setRoleSuccess(false));
        cases.add(setRoleSuccess(true));

        cases.add(test("set-role-unchanged", "UTC033", "UTC-ACL-003",
                "setRoleSkipsMutationWhenAssignmentIsUnchanged", 48,
                "Actor may manage target; target is ACTIVE with the same role and branch",
                "role=RECEPTIONIST, branchId unchanged",
                "Return the current account; no setRole, token revocation or audit", () -> {
                    Context context = context();
                    AccountView before = account("RECEPTIONIST", BRANCH_ID, "ACTIVE");
                    arrangeEditable(context, before);
                    when(context.accounts.roleId("RECEPTIONIST")).thenReturn(ROLE_ID);
                    when(context.accounts.activeBranch(BRANCH_ID)).thenReturn(true);
                    return ManualTestSupport.observe(
                            () -> context.service.setRole(TARGET_ID, new SetRole("RECEPTIONIST", BRANCH_ID), ACTOR_ID),
                            context.accounts, context.access, context.branches);
                }));

        cases.add(test("self-modification", "UTC034", "UTC-ACL-004",
                "editableRejectsSelfModificationBeforeDependencyCall", 49,
                "actorId equals targetId", "setRole and deactivate are attempted for the signed-in account",
                "Both calls throw AuthFailure status=400 before any dependency call", () -> {
                    Context context = context();
                    return ManualTestSupport.observe(() -> {
                        String role = ManualTestSupport.capture(
                                () -> context.service.setRole(ACTOR_ID, new SetRole("ACCOUNTANT", null), ACTOR_ID));
                        String deactivate = ManualTestSupport.capture(
                                () -> context.service.deactivate(ACTOR_ID, new Deactivate("Resigned"), ACTOR_ID));
                        return "setRole: " + role + System.lineSeparator() + "deactivate: " + deactivate;
                    }, context.accounts, context.access, context.branches);
                }));

        cases.add(test("stale-permission", "UTC035", "UTC-ACL-005",
                "editableRejectsActorWhoLostManagementPermission", 50,
                "lockManagement(actorId)=false", "Change target role",
                "AuthFailure status=403; target is not locked and AccountRepository is not called", () -> {
                    Context context = context();
                    when(context.access.lockManagement(ACTOR_ID)).thenReturn(false);
                    return ManualTestSupport.observe(
                            () -> context.service.setRole(TARGET_ID, new SetRole("ACCOUNTANT", null), ACTOR_ID),
                            context.accounts, context.access, context.branches);
                }));

        cases.add(test("missing-target", "UTC036", "UTC-ACL-006", "editableRejectsMissingTargetAccount", 51,
                "Actor may manage accounts; lockAccount(targetId)=false", "Change target role",
                "AuthFailure status=404; AccountRepository is not called", () -> {
                    Context context = context();
                    when(context.access.lockManagement(ACTOR_ID)).thenReturn(true);
                    when(context.access.lockAccount(TARGET_ID)).thenReturn(false);
                    return ManualTestSupport.observe(
                            () -> context.service.setRole(TARGET_ID, new SetRole("ACCOUNTANT", null), ACTOR_ID),
                            context.accounts, context.access, context.branches);
                }));

        cases.add(test("revoked-target", "UTC037", "UTC-ACL-007", "editableRejectsAlreadyRevokedTarget", 52,
                "Actor may manage accounts; target is REVOKED", "Change target role",
                "AuthFailure status=409; role is not changed", () -> {
                    Context context = context();
                    arrangeEditable(context, account("RECEPTIONIST", BRANCH_ID, "REVOKED"));
                    return ManualTestSupport.observe(
                            () -> context.service.setRole(TARGET_ID, new SetRole("ACCOUNTANT", null), ACTOR_ID),
                            context.accounts, context.access, context.branches);
                }));

        cases.add(test("unknown-role", "UTC038", "UTC-ACL-008", "setRoleRejectsUnknownRole", 53,
                "Actor may manage an ACTIVE target; role lookup returns null", "role=UNKNOWN",
                "AuthFailure status=400; access.setRole is not called", () -> {
                    Context context = context();
                    arrangeEditable(context, account("RECEPTIONIST", BRANCH_ID, "ACTIVE"));
                    when(context.accounts.roleId("UNKNOWN")).thenReturn(null);
                    return ManualTestSupport.observe(
                            () -> context.service.setRole(TARGET_ID, new SetRole("UNKNOWN", BRANCH_ID), ACTOR_ID),
                            context.accounts, context.access, context.branches);
                }));

        Object[][] scopes = {
                {"UTC039", "chain role with branch", "ACCOUNTANT", BRANCH_ID, true, 54},
                {"UTC040", "branch role without branch", "RECEPTIONIST", null, true, 55},
                {"UTC041", "branch role with inactive branch", "RECEPTIONIST", BRANCH_ID, false, 56}
        };
        for (Object[] data : scopes) {
            String id = (String) data[0];
            String label = (String) data[1];
            String role = (String) data[2];
            UUID scope = (UUID) data[3];
            boolean active = (Boolean) data[4];
            int row = (Integer) data[5];
            cases.add(test("invalid-scope-" + label.replace(' ', '-'), id, "UTC-ACL-009",
                    "setRoleRejectsInvalidScope[" + label + "]", row,
                    "Actor may manage an ACTIVE target; role exists",
                    "role=" + role + ", branchId=" + scope + ", activeBranch=" + active,
                    "AuthFailure status=400; no role mutation or token revocation", () -> {
                        Context context = context();
                        arrangeEditable(context, account("RECEPTIONIST", BRANCH_ID, "ACTIVE"));
                        when(context.accounts.roleId(role)).thenReturn(ROLE_ID);
                        if (scope != null && "RECEPTIONIST".equals(role)) {
                            when(context.accounts.activeBranch(scope)).thenReturn(active);
                        }
                        return ManualTestSupport.observe(
                                () -> context.service.setRole(TARGET_ID, new SetRole(role, scope), ACTOR_ID),
                                context.accounts, context.access, context.branches);
                    }));
        }

        cases.add(test("deactivate", "UTC042", "UTC-ACL-010",
                "deactivateTrimsReasonRevokesSessionsAndAudits", 58,
                "Actor may manage an ACTIVE target; before/after snapshots are available",
                "reason='  Resigned  '",
                "Trim reason, deactivate, synchronize branch manager, revoke tokens, audit snapshots and return the after account", () -> {
                    Context context = context();
                    AccountView before = account("RECEPTIONIST", BRANCH_ID, "ACTIVE");
                    AccountView after = account("RECEPTIONIST", BRANCH_ID, "REVOKED");
                    arrangeEditable(context, before, after);
                    return ManualTestSupport.observe(
                            () -> context.service.deactivate(TARGET_ID, new Deactivate("  Resigned  "), ACTOR_ID),
                            context.accounts, context.access, context.branches);
                }));

        Object[][] reasons = {{"UTC043", "null", null, 59}, {"UTC044", "blank", " ", 60},
                {"UTC045", "over maximum", "x".repeat(1001), 61}};
        for (Object[] data : reasons) {
            String id = (String) data[0];
            String label = (String) data[1];
            String reason = (String) data[2];
            int row = (Integer) data[3];
            cases.add(test("deactivate-reason-" + label.replace(' ', '-'), id, "UTC-ACL-011",
                    "deactivateRejectsInvalidReasonBeforeAuthorization[" + label + "]", row,
                    "No dependency setup required", "reason=" + label,
                    "AuthFailure status=400 before authorization; no dependency interaction", () -> {
                        Context context = context();
                        return ManualTestSupport.observe(
                                () -> context.service.deactivate(TARGET_ID, new Deactivate(reason), ACTOR_ID),
                                context.accounts, context.access, context.branches);
                    }));
        }

        cases.add(test("permissions-valid", "UTC046", "UTC-ACL-012",
                "permissionsReturnsRepositoryProjectionForValidRole", 62,
                "Role RECEPTIONIST exists", "role=RECEPTIONIST",
                "Return the same permission projection from AccountAccessRepository", () -> {
                    Context context = context();
                    List<PermissionView> permissions = List.of(
                            new PermissionView("ACCOUNT_VIEW", "View accounts", "BRANCH"));
                    when(context.accounts.roleId("RECEPTIONIST")).thenReturn(ROLE_ID);
                    when(context.access.permissions("RECEPTIONIST")).thenReturn(permissions);
                    return ManualTestSupport.observe(
                            () -> context.service.permissions("RECEPTIONIST"), context.accounts, context.access, context.branches);
                }));

        cases.add(test("permissions-invalid", "UTC047", "UTC-ACL-013", "permissionsRejectsInvalidRole", 63,
                "Role lookup returns null", "role=UNKNOWN",
                "AuthFailure status=400; access.permissions is not called", () -> {
                    Context context = context();
                    when(context.accounts.roleId("UNKNOWN")).thenReturn(null);
                    return ManualTestSupport.observe(
                            () -> context.service.permissions("UNKNOWN"), context.accounts, context.access, context.branches);
                }));

        return new ManualTestSuite("Administration", "AccountAccessService", cases);
    }

    private static ManualTestCase setRoleSuccess(boolean branchRole) {
        String id = branchRole ? "UTC032" : "UTC031";
        String source = branchRole ? "UTC-ACL-002" : "UTC-ACL-001";
        String role = branchRole ? "RECEPTIONIST" : "ACCOUNTANT";
        UUID scope = branchRole ? BRANCH_ID : null;
        int row = branchRole ? 47 : 46;
        return test("set-role-success-" + role.toLowerCase(), id, source,
                branchRole ? "setRoleAssignsBranchRoleForActiveBranch"
                        : "setRoleAssignsChainRoleRevokesSessionsAndAudits",
                row, "Actor may manage an ACTIVE target; role exists" + (branchRole ? "; branch is active" : ""),
                "role=" + role + ", branchId=" + scope,
                "Set role, synchronize branch manager, revoke tokens, audit before/after and return the after account", () -> {
                    Context context = context();
                    AccountView before = account(branchRole ? "ACCOUNTANT" : "RECEPTIONIST",
                            branchRole ? null : BRANCH_ID, "ACTIVE");
                    AccountView after = account(role, scope, "ACTIVE");
                    arrangeEditable(context, before, after);
                    when(context.accounts.roleId(role)).thenReturn(ROLE_ID);
                    if (branchRole) {
                        when(context.accounts.activeBranch(BRANCH_ID)).thenReturn(true);
                    }
                    return ManualTestSupport.observe(
                            () -> context.service.setRole(TARGET_ID, new SetRole(role, scope), ACTOR_ID),
                            context.accounts, context.access, context.branches);
                });
    }

    private static Context context() {
        AccountRepository accounts = mock(AccountRepository.class);
        AccountAccessRepository access = mock(AccountAccessRepository.class);
        BranchAdministrationService branches = mock(BranchAdministrationService.class);
        return new Context(accounts, access, branches, new AccountAccessService(accounts, access, branches));
    }

    private static void arrangeEditable(Context context, AccountView... sequence) {
        when(context.access.lockManagement(ACTOR_ID)).thenReturn(true);
        when(context.access.lockAccount(TARGET_ID)).thenReturn(true);
        when(context.accounts.find(TARGET_ID)).thenReturn(
                sequence[0], java.util.Arrays.copyOfRange(sequence, 1, sequence.length));
    }

    private static AccountView account(String role, UUID scope, String status) {
        return new AccountView(
                TARGET_ID, "Nguyen Van A", "NV-001", "staff.rec", "staff@example.com", role,
                scope, scope == null ? "Toan chuoi" : "Ho Tay", status);
    }

    private static ManualTestCase test(
            String key,
            String documentId,
            String sourceId,
            String method,
            int workbookRow,
            String preconditions,
            String input,
            String expected,
            ManualTestCase.Executor executor) {
        return new ManualTestCase("account-access/" + key,
                ManualTestCase.identifier(documentId, sourceId),
                "AccountAccessServiceTest",
                method,
                preconditions,
                input,
                expected,
                executor);
    }

    private record Context(
            AccountRepository accounts,
            AccountAccessRepository access,
            BranchAdministrationService branches,
            AccountAccessService service) {
    }
}
