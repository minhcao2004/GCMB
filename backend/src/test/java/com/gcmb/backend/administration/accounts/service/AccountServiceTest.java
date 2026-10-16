/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 13/10/2026
 * Description: Unit Test Case cho xem danh sách và tạo tài khoản theo UC-04, UC-05 và BR-111 đến BR-114.
 */

package com.gcmb.backend.administration.accounts.service;

import com.gcmb.backend.administration.accounts.dto.AccountDtos.AccountPage;
import com.gcmb.backend.administration.accounts.dto.AccountDtos.AccountView;
import com.gcmb.backend.administration.accounts.dto.AccountDtos.CreateAccount;
import com.gcmb.backend.administration.accounts.dto.AccountDtos.EmployeeOption;
import com.gcmb.backend.administration.accounts.dto.AccountDtos.Option;
import com.gcmb.backend.administration.accounts.dto.AccountDtos.Options;
import com.gcmb.backend.administration.accounts.repository.AccountRepository;
import com.gcmb.backend.administration.branch.service.BranchAdministrationService;
import com.gcmb.backend.administration.auth.exception.AuthFailure;
import com.gcmb.backend.administration.auth.security.AuthCrypto;
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
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {
    @Mock
    private AccountRepository repository;

    @Mock
    private AuthCrypto crypto;

    @Mock
    private BranchAdministrationService branches;

    private AccountService service;
    private UUID employeeId;
    private UUID branchId;
    private UUID roleId;
    private UUID actorId;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        service = new AccountService(repository, crypto, branches);
        employeeId = UUID.randomUUID();
        branchId = UUID.randomUUID();
        roleId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        accountId = UUID.randomUUID();
    }

    @Test
    @DisplayName("UTC-ACC-001 list trims query and delegates all read-only filters")
    void listTrimsQueryAndDelegatesAllFilters() {
        AccountPage expected = new AccountPage(List.of(account("RECEPTIONIST", branchId)), 1, 2, 25);
        when(repository.list("staff", "RECEPTIONIST", branchId, "ACTIVE", 2, 25)).thenReturn(expected);

        AccountPage actual = service.list("  staff  ", "RECEPTIONIST", branchId, "ACTIVE", 2, 25);

        assertSame(expected, actual);
        verify(repository).list("staff", "RECEPTIONIST", branchId, "ACTIVE", 2, 25);
    }

    static Stream<Arguments> invalidListFilters() {
        return Stream.of(
                Arguments.of("negative page", "", -1, 10),
                Arguments.of("zero size", "", 0, 0),
                Arguments.of("size above maximum", "", 0, 101),
                Arguments.of("query above maximum", "q".repeat(201), 0, 10));
    }

    @ParameterizedTest(name = "UTC-ACC-002 rejects {0}")
    @MethodSource("invalidListFilters")
    void listRejectsInvalidFilterOrPagination(String caseName, String query, int page, int size) {
        AuthFailure failure = assertThrows(
                AuthFailure.class,
                () -> service.list(query, "", null, "", page, size));

        assertEquals(400, failure.status(), caseName);
        verifyNoInteractions(repository, crypto);
    }

    @Test
    @DisplayName("UTC-ACC-003 options returns repository projection unchanged")
    void optionsReturnsRepositoryProjectionUnchanged() {
        Options expected = new Options(
                List.of(new EmployeeOption(employeeId, "Nguyen Van A", "NV-001")),
                List.of(new Option("RECEPTIONIST", "Receptionist")),
                List.of(new Option(branchId.toString(), "Ho Tay")));
        when(repository.options()).thenReturn(expected);

        assertSame(expected, service.options());

        verify(repository).options();
        verifyNoInteractions(crypto);
    }

    @Test
    @DisplayName("UTC-ACC-004 create normalizes data, hashes password and persists branch account")
    void createNormalizesDataHashesPasswordAndPersistsBranchAccount() {
        CreateAccount input = new CreateAccount(
                employeeId, "  staff.rec  ", "Aa1!bcde", "  STAFF@Example.com  ",
                "RECEPTIONIST", branchId);
        AccountView expected = account("RECEPTIONIST", branchId);
        arrangeValidEmployeeAndRole("RECEPTIONIST", branchId);
        when(crypto.encode("Aa1!bcde")).thenReturn("encoded-password");
        when(repository.insert(
                input, "staff.rec", "staff@example.com", "encoded-password", roleId, actorId))
                .thenReturn(accountId);
        when(repository.find(accountId)).thenReturn(expected);

        AccountView actual = service.create(input, actorId);

        assertSame(expected, actual);
        verify(repository).insert(
                input, "staff.rec", "staff@example.com", "encoded-password", roleId, actorId);
    }

    @Test
    @DisplayName("UTC-ACC-005 create accepts chain role only without branch scope")
    void createAcceptsChainRoleOnlyWithoutBranchScope() {
        CreateAccount input = accountInput("ACCOUNTANT", null, "Aa1!bcde");
        AccountView expected = account("ACCOUNTANT", null);
        when(repository.lockAvailableEmployee(employeeId)).thenReturn(true);
        when(repository.roleId("ACCOUNTANT")).thenReturn(roleId);
        when(crypto.encode("Aa1!bcde")).thenReturn("encoded-password");
        when(repository.insert(
                input, "staff.rec", "staff@example.com", "encoded-password", roleId, actorId))
                .thenReturn(accountId);
        when(repository.find(accountId)).thenReturn(expected);

        assertSame(expected, service.create(input, actorId));

        verify(repository, never()).activeBranch(any());
    }

    static Stream<String> invalidUsernames() {
        return Stream.of(null, " ", "ab", "bad name", "bad/name", "x".repeat(101));
    }

    @ParameterizedTest(name = "UTC-ACC-006 rejects invalid username [{0}]")
    @MethodSource("invalidUsernames")
    void createRejectsInvalidUsernameBeforeUsingDependencies(String username) {
        CreateAccount input = new CreateAccount(
                employeeId, username, "Aa1!bcde", "staff@example.com", "RECEPTIONIST", branchId);

        AuthFailure failure = assertThrows(AuthFailure.class, () -> service.create(input, actorId));

        assertEquals(400, failure.status());
        verifyNoInteractions(repository, crypto);
    }

    @Test
    @DisplayName("UTC-ACC-007 create rejects malformed email according to BR-113")
    void createRejectsMalformedEmailBeforeUsingDependencies() {
        CreateAccount input = new CreateAccount(
                employeeId, "staff.rec", "Aa1!bcde", "staff @example.com", "RECEPTIONIST", branchId);

        AuthFailure failure = assertThrows(AuthFailure.class, () -> service.create(input, actorId));

        assertEquals(400, failure.status());
        verifyNoInteractions(repository, crypto);
    }

    static Stream<Arguments> passwordsViolatingBr114() {
        return Stream.of(
                Arguments.of("shorter than 8", "Aa1!bcd"),
                Arguments.of("longer than 64", "Aa1!" + "b".repeat(61)),
                Arguments.of("missing uppercase", "aa1!bcde"),
                Arguments.of("missing special character", "Aa1bcdef"),
                Arguments.of("contains whitespace", "Aa1! bcde"));
    }

    @ParameterizedTest(name = "UTC-ACC-008 rejects BR-114 violation: {0}")
    @MethodSource("passwordsViolatingBr114")
    void createRejectsPasswordThatViolatesBr114(String caseName, String password) {
        CreateAccount input = accountInput("RECEPTIONIST", branchId, password);
        arrangeValidEmployeeAndRole("RECEPTIONIST", branchId);
        when(crypto.encode(password)).thenReturn("encoded-password");
        when(repository.insert(
                input, "staff.rec", "staff@example.com", "encoded-password", roleId, actorId))
                .thenReturn(accountId);
        when(repository.find(accountId)).thenReturn(account("RECEPTIONIST", branchId));

        AuthFailure failure = assertThrows(AuthFailure.class, () -> service.create(input, actorId), caseName);

        assertEquals(400, failure.status(), caseName);
        verify(repository, never()).insert(any(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("UTC-ACC-009 create rejects missing employee before repository lock")
    void createRejectsMissingEmployee() {
        CreateAccount input = new CreateAccount(
                null, "staff.rec", "Aa1!bcde", "staff@example.com", "RECEPTIONIST", branchId);

        AuthFailure failure = assertThrows(AuthFailure.class, () -> service.create(input, actorId));

        assertEquals(400, failure.status());
        verifyNoInteractions(repository, crypto);
    }

    @Test
    @DisplayName("UTC-ACC-010 create rejects inactive or already-linked employee")
    void createRejectsUnavailableEmployee() {
        CreateAccount input = accountInput("RECEPTIONIST", branchId, "Aa1!bcde");
        when(repository.lockAvailableEmployee(employeeId)).thenReturn(false);

        AuthFailure failure = assertThrows(AuthFailure.class, () -> service.create(input, actorId));

        assertEquals(409, failure.status());
        verifyNoInteractions(crypto);
        verify(repository, never()).insert(any(), anyString(), anyString(), anyString(), any(), any());
    }

    static Stream<String> invalidRoles() {
        return Stream.of("<null>", "UNKNOWN");
    }

    @ParameterizedTest(name = "UTC-ACC-011 rejects role {0}")
    @MethodSource("invalidRoles")
    void createRejectsMissingOrUnknownRole(String roleCase) {
        String role = "<null>".equals(roleCase) ? null : roleCase;
        CreateAccount input = accountInput(role, branchId, "Aa1!bcde");
        when(repository.lockAvailableEmployee(employeeId)).thenReturn(true);
        if (role != null) {
            when(repository.roleId(role)).thenReturn(null);
        }

        AuthFailure failure = assertThrows(AuthFailure.class, () -> service.create(input, actorId));

        assertEquals(400, failure.status());
        verifyNoInteractions(crypto);
    }

    static Stream<Arguments> invalidRoleScopes() {
        return Stream.of(
                Arguments.of("chain role with branch", "ACCOUNTANT", UUID.randomUUID(), true),
                Arguments.of("branch role without branch", "RECEPTIONIST", null, true),
                Arguments.of("branch role with inactive branch", "RECEPTIONIST", UUID.randomUUID(), false));
    }

    @ParameterizedTest(name = "UTC-ACC-012 rejects {0}")
    @MethodSource("invalidRoleScopes")
    void createRejectsInvalidRoleScope(String caseName, String role, UUID scope, boolean active) {
        CreateAccount input = accountInput(role, scope, "Aa1!bcde");
        when(repository.lockAvailableEmployee(employeeId)).thenReturn(true);
        when(repository.roleId(role)).thenReturn(roleId);
        if (scope != null && "RECEPTIONIST".equals(role)) {
            when(repository.activeBranch(scope)).thenReturn(active);
        }

        AuthFailure failure = assertThrows(AuthFailure.class, () -> service.create(input, actorId));

        assertEquals(400, failure.status(), caseName);
        verifyNoInteractions(crypto);
    }

    static Stream<String> duplicateIdentityFields() {
        return Stream.of("username", "email");
    }

    @ParameterizedTest(name = "UTC-ACC-013 rejects duplicate {0} according to BR-112/BR-113")
    @MethodSource("duplicateIdentityFields")
    void createRejectsDuplicateUsernameOrEmail(String duplicateField) {
        CreateAccount input = accountInput("RECEPTIONIST", branchId, "Aa1!bcde");
        arrangeValidEmployeeAndRole("RECEPTIONIST", branchId);
        if ("username".equals(duplicateField)) {
            when(repository.exists("username", "staff.rec")).thenReturn(true);
        } else {
            when(repository.exists("username", "staff.rec")).thenReturn(false);
            when(repository.exists("email", "staff@example.com")).thenReturn(true);
        }

        AuthFailure failure = assertThrows(AuthFailure.class, () -> service.create(input, actorId));

        assertEquals(409, failure.status());
        verifyNoInteractions(crypto);
        verify(repository, never()).insert(any(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("UTC-ACC-014 create translates concurrent unique-key conflict to business conflict")
    void createTranslatesConcurrentDuplicateToConflict() {
        CreateAccount input = accountInput("RECEPTIONIST", branchId, "Aa1!bcde");
        arrangeValidEmployeeAndRole("RECEPTIONIST", branchId);
        when(crypto.encode("Aa1!bcde")).thenReturn("encoded-password");
        when(repository.insert(
                input, "staff.rec", "staff@example.com", "encoded-password", roleId, actorId))
                .thenThrow(new DuplicateKeyException("concurrent duplicate"));

        AuthFailure failure = assertThrows(AuthFailure.class, () -> service.create(input, actorId));

        assertEquals(409, failure.status());
        verify(repository, never()).find(any());
    }

    private void arrangeValidEmployeeAndRole(String role, UUID scope) {
        when(repository.lockAvailableEmployee(employeeId)).thenReturn(true);
        when(repository.roleId(role)).thenReturn(roleId);
        if (!List.of("HEAD_OFFICE_MANAGER", "ACCOUNTANT").contains(role)) {
            when(repository.activeBranch(scope)).thenReturn(true);
        }
    }

    private CreateAccount accountInput(String role, UUID scope, String password) {
        return new CreateAccount(
                employeeId, "staff.rec", password, "staff@example.com", role, scope);
    }

    private AccountView account(String role, UUID scope) {
        return new AccountView(
                accountId,
                "Nguyen Van A",
                "NV-001",
                "staff.rec",
                "staff@example.com",
                role,
                scope,
                scope == null ? "Toan chuoi" : "Ho Tay",
                "ACTIVE");
    }
}
