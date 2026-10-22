/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 14/10/2026
 * Description: Covers account listing, options, creation and branch-manager synchronization.
 */

package com.gcmb.backend.manual.administration.accounts.service;

import com.gcmb.backend.administration.accounts.dto.AccountDtos.AccountPage;
import com.gcmb.backend.administration.accounts.dto.AccountDtos.AccountView;
import com.gcmb.backend.administration.accounts.dto.AccountDtos.CreateAccount;
import com.gcmb.backend.administration.accounts.dto.AccountDtos.EmployeeOption;
import com.gcmb.backend.administration.accounts.dto.AccountDtos.Option;
import com.gcmb.backend.administration.accounts.dto.AccountDtos.Options;
import com.gcmb.backend.administration.accounts.repository.AccountRepository;
import com.gcmb.backend.administration.accounts.service.AccountService;
import com.gcmb.backend.administration.auth.security.AuthCrypto;
import com.gcmb.backend.administration.branch.service.BranchAdministrationService;
import com.gcmb.backend.manual.ManualTestCase;
import com.gcmb.backend.manual.ManualTestSuite;
import com.gcmb.backend.manual.ManualTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class AccountManualTest {
    private static final UUID EMPLOYEE_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID BRANCH_ID = UUID.fromString("40000000-0000-0000-0000-000000000004");
    private static final UUID ROLE_ID = UUID.fromString("50000000-0000-0000-0000-000000000005");
    private static final UUID ACTOR_ID = UUID.fromString("60000000-0000-0000-0000-000000000006");
    private static final UUID ACCOUNT_ID = UUID.fromString("70000000-0000-0000-0000-000000000007");

    private AccountManualTest() {
    }

    public static ManualTestSuite suite() {
        List<ManualTestCase> cases = new ArrayList<>();

        cases.add(test("list-valid",
                "UTC001",
                "UTC-ACC-001",
                "listTrimsQueryAndDelegatesAllFilters",
                "Repository returns a matching AccountPage",
                "q='  staff  ', role=RECEPTIONIST, branchId, status=ACTIVE, page=2, size=25",
                "Trim q to staff, delegate every filter and return the repository projection",
                () -> {
                    AccountRepository repository = mock(AccountRepository.class);
                    AuthCrypto crypto = mock(AuthCrypto.class);
                    BranchAdministrationService branches = mock(BranchAdministrationService.class);
                    AccountPage page = new AccountPage(List.of(account("RECEPTIONIST", BRANCH_ID)), 1, 2, 25);
                    when(repository.list("staff", "RECEPTIONIST", BRANCH_ID, "ACTIVE", 2, 25)).thenReturn(page);
                    return ManualTestSupport.observe(
                            () -> new AccountService(repository, crypto, branches)
                                    .list("  staff  ", "RECEPTIONIST", BRANCH_ID, "ACTIVE", 2, 25),
                            repository, crypto, branches);
                }));

        Object[][] invalidFilters = {
                {"UTC002", "negative page", "", -1, 10, 14},
                {"UTC003", "zero size", "", 0, 0, 15},
                {"UTC004", "size above maximum", "", 0, 101, 16},
                {"UTC005", "query above maximum", "q".repeat(201), 0, 10, 17}
        };
        for (Object[] data : invalidFilters) {
            String id = (String) data[0];
            String label = (String) data[1];
            String query = (String) data[2];
            int page = (Integer) data[3];
            int size = (Integer) data[4];

            cases.add(test("list-invalid-" + label.replace(' ', '-'),
                id,
                "UTC-ACC-002",
                "listRejectsInvalidFilterOrPagination[" + label + "]",
                "No dependency setup required",
                "q length=" + query.length() + ", page=" + page + ", size=" + size,
                "AuthFailure status=400; no repository or crypto interaction",
                () -> {
                        AccountRepository repository = mock(AccountRepository.class);
                        AuthCrypto crypto = mock(AuthCrypto.class);
                        BranchAdministrationService branches = mock(BranchAdministrationService.class);
                        return ManualTestSupport.observe(
                                () -> new AccountService(repository, crypto, branches).list(query, "", null, "", page, size),
                                repository, crypto, branches);
                    }));
        }

        cases.add(test("options",
                "UTC006",
                "UTC-ACC-003",
                "optionsReturnsRepositoryProjectionUnchanged",
                "Repository returns employee, role and branch options",
                "No parameters",
                "Return the same Options projection; do not call crypto",
                () -> {
                    AccountRepository repository = mock(AccountRepository.class);
                    AuthCrypto crypto = mock(AuthCrypto.class);
                    BranchAdministrationService branches = mock(BranchAdministrationService.class);
                    Options options = new Options(
                            List.of(new EmployeeOption(EMPLOYEE_ID, "Nguyen Van A", "NV-001")),
                            List.of(new Option("RECEPTIONIST", "Receptionist")),
                            List.of(new Option(BRANCH_ID.toString(), "Ho Tay")));
                    when(repository.options()).thenReturn(options);
                    return ManualTestSupport.observe(() -> new AccountService(repository, crypto, branches).options(), repository, crypto, branches);
                }));

        cases.add(createSuccessCase(false));
        cases.add(createSuccessCase(true));

        Object[][] invalidUsernames = {
                {"UTC009", "null", null, 23},
                {"UTC010", "blank", " ", 24},
                {"UTC011", "too short", "ab", 25},
                {"UTC012", "internal space", "bad name", 26},
                {"UTC013", "slash", "bad/name", 27},
                {"UTC014", "over 100", "x".repeat(101), 28}
        };
        for (Object[] data : invalidUsernames) {
            String id = (String) data[0];
            String label = (String) data[1];
            String username = (String) data[2];

            cases.add(test("create-username-" + label.replace(' ', '-'),
                id,
                "UTC-ACC-006",
                "createRejectsInvalidUsernameBeforeUsingDependencies[" + label + "]",
                "No dependency setup required",
                "username=" + printable(username),
                "AuthFailure status=400 before repository or crypto is used",
                () -> {
                        AccountRepository repository = mock(AccountRepository.class);
                        AuthCrypto crypto = mock(AuthCrypto.class);
                        BranchAdministrationService branches = mock(BranchAdministrationService.class);
                        CreateAccount input = new CreateAccount(
                                EMPLOYEE_ID, username, "Aa1!bcde", "staff@example.com", "RECEPTIONIST", BRANCH_ID);
                        return ManualTestSupport.observe(
                                () -> new AccountService(repository, crypto, branches).create(input, ACTOR_ID), repository, crypto, branches);
                    }));
        }

        cases.add(test("create-invalid-email",
                "UTC015",
                "UTC-ACC-007",
                "createRejectsMalformedEmailBeforeUsingDependencies",
                "No dependency setup required",
                "email='staff @example.com'",
                "AuthFailure status=400 before repository or crypto is used",
                () -> {
                    AccountRepository repository = mock(AccountRepository.class);
                    AuthCrypto crypto = mock(AuthCrypto.class);
                    BranchAdministrationService branches = mock(BranchAdministrationService.class);
                    CreateAccount input = new CreateAccount(
                            EMPLOYEE_ID, "staff.rec", "Aa1!bcde", "staff @example.com", "RECEPTIONIST", BRANCH_ID);
                    return ManualTestSupport.observe(
                            () -> new AccountService(repository, crypto, branches).create(input, ACTOR_ID), repository, crypto, branches);
                }));

        Object[][] invalidPasswords = {
                {"UTC016", "shorter than 8", "Aa1!bcd", 30},
                {"UTC017", "longer than 64", "Aa1!" + "b".repeat(61), 31},
                {"UTC018", "missing uppercase", "aa1!bcde", 32},
                {"UTC019", "missing special character", "Aa1bcdef", 33},
                {"UTC020", "contains whitespace", "Aa1! bcde", 34}
        };
        for (Object[] data : invalidPasswords) {
            String id = (String) data[0];
            String label = (String) data[1];
            String password = (String) data[2];

            cases.add(test("create-password-" + label.replace(' ', '-'),
                id,
                "UTC-ACC-008",
                "createRejectsPasswordThatViolatesBr114[" + label + "]",
                "Employee, role and branch are valid; downstream insert is stubbed",
                "password=" + printable(password),
                "AuthFailure status=400; repository.insert must not be called",
                () -> {
                        AccountRepository repository = mock(AccountRepository.class);
                        AuthCrypto crypto = mock(AuthCrypto.class);
                        BranchAdministrationService branches = mock(BranchAdministrationService.class);
                        CreateAccount input = accountInput("RECEPTIONIST", BRANCH_ID, password);
                        arrangeValid(repository, "RECEPTIONIST", BRANCH_ID);
                        when(crypto.encode(password)).thenReturn("encoded-password");
                        when(repository.insert(input, "staff.rec", "staff@example.com", "encoded-password", ROLE_ID, ACTOR_ID))
                                .thenReturn(ACCOUNT_ID);
                        when(repository.find(ACCOUNT_ID)).thenReturn(account("RECEPTIONIST", BRANCH_ID));
                        return ManualTestSupport.observe(
                                () -> new AccountService(repository, crypto, branches).create(input, ACTOR_ID), repository, crypto, branches);
                    }));
        }

        cases.add(test("create-missing-employee",
                "UTC021",
                "UTC-ACC-009",
                "createRejectsMissingEmployee",
                "No dependency setup required",
                "employeeId=null",
                "AuthFailure status=400 before repository or crypto is used",
                () -> {
                    AccountRepository repository = mock(AccountRepository.class);
                    AuthCrypto crypto = mock(AuthCrypto.class);
                    BranchAdministrationService branches = mock(BranchAdministrationService.class);
                    CreateAccount input = new CreateAccount(
                            null, "staff.rec", "Aa1!bcde", "staff@example.com", "RECEPTIONIST", BRANCH_ID);
                    return ManualTestSupport.observe(
                            () -> new AccountService(repository, crypto, branches).create(input, ACTOR_ID), repository, crypto, branches);
                }));

        cases.add(test("create-unavailable-employee",
                "UTC022",
                "UTC-ACC-010",
                "createRejectsUnavailableEmployee",
                "lockAvailableEmployee=false",
                "Valid account input",
                "AuthFailure status=409; no password hash or insert",
                () -> {
                    AccountRepository repository = mock(AccountRepository.class);
                    AuthCrypto crypto = mock(AuthCrypto.class);
                    BranchAdministrationService branches = mock(BranchAdministrationService.class);
                    when(repository.lockAvailableEmployee(EMPLOYEE_ID)).thenReturn(false);
                    CreateAccount input = accountInput("RECEPTIONIST", BRANCH_ID, "Aa1!bcde");
                    return ManualTestSupport.observe(
                            () -> new AccountService(repository, crypto, branches).create(input, ACTOR_ID), repository, crypto, branches);
                }));

        Object[][] roles = {{"UTC023", "null", null, 37}, {"UTC024", "UNKNOWN", "UNKNOWN", 38}};
        for (Object[] data : roles) {
            String id = (String) data[0];
            String label = (String) data[1];
            String role = (String) data[2];

            cases.add(test("create-role-" + label,
                id,
                "UTC-ACC-011",
                "createRejectsMissingOrUnknownRole[" + label + "]",
                "Employee is available",
                "role=" + label,
                "AuthFailure status=400; crypto is not used",
                () -> {
                        AccountRepository repository = mock(AccountRepository.class);
                        AuthCrypto crypto = mock(AuthCrypto.class);
                        BranchAdministrationService branches = mock(BranchAdministrationService.class);
                        when(repository.lockAvailableEmployee(EMPLOYEE_ID)).thenReturn(true);
                        if (role != null) {
                            when(repository.roleId(role)).thenReturn(null);
                        }
                        return ManualTestSupport.observe(
                                () -> new AccountService(repository, crypto, branches)
                                        .create(accountInput(role, BRANCH_ID, "Aa1!bcde"), ACTOR_ID),
                                repository, crypto, branches);
                    }));
        }

        Object[][] scopes = {
                {"UTC025", "chain role with branch", "ACCOUNTANT", BRANCH_ID, true, 39},
                {"UTC026", "branch role without branch", "RECEPTIONIST", null, true, 40},
                {"UTC027", "branch role with inactive branch", "RECEPTIONIST", BRANCH_ID, false, 41}
        };
        for (Object[] data : scopes) {
            String id = (String) data[0];
            String label = (String) data[1];
            String role = (String) data[2];
            UUID scope = (UUID) data[3];
            boolean active = (Boolean) data[4];

            cases.add(test("create-scope-" + label.replace(' ', '-'),
                id,
                "UTC-ACC-012",
                "createRejectsInvalidRoleScope[" + label + "]",
                "Employee is available and role exists",
                "role=" + role + ", branchId=" + scope + ", activeBranch=" + active,
                "AuthFailure status=400; no password hash or insert",
                () -> {
                        AccountRepository repository = mock(AccountRepository.class);
                        AuthCrypto crypto = mock(AuthCrypto.class);
                        BranchAdministrationService branches = mock(BranchAdministrationService.class);
                        when(repository.lockAvailableEmployee(EMPLOYEE_ID)).thenReturn(true);
                        when(repository.roleId(role)).thenReturn(ROLE_ID);
                        if (scope != null && "RECEPTIONIST".equals(role)) {
                            when(repository.activeBranch(scope)).thenReturn(active);
                        }
                        return ManualTestSupport.observe(
                                () -> new AccountService(repository, crypto, branches)
                                        .create(accountInput(role, scope, "Aa1!bcde"), ACTOR_ID),
                                repository, crypto, branches);
                    }));
        }

        Object[][] duplicates = {{"UTC028", "username", 42}, {"UTC029", "email", 43}};
        for (Object[] data : duplicates) {
            String id = (String) data[0];
            String field = (String) data[1];

            cases.add(test("create-duplicate-" + field,
                id,
                "UTC-ACC-013",
                "createRejectsDuplicateUsernameOrEmail[" + field + "]",
                "Employee, role and branch are valid; " + field + " already exists",
                "Valid CreateAccount",
                "AuthFailure status=409; no hash or insert",
                () -> {
                        AccountRepository repository = mock(AccountRepository.class);
                        AuthCrypto crypto = mock(AuthCrypto.class);
                        BranchAdministrationService branches = mock(BranchAdministrationService.class);
                        arrangeValid(repository, "RECEPTIONIST", BRANCH_ID);
                        when(repository.exists("username", "staff.rec")).thenReturn("username".equals(field));
                        if ("email".equals(field)) {
                            when(repository.exists("email", "staff@example.com")).thenReturn(true);
                        }
                        return ManualTestSupport.observe(
                                () -> new AccountService(repository, crypto, branches)
                                        .create(accountInput("RECEPTIONIST", BRANCH_ID, "Aa1!bcde"), ACTOR_ID),
                                repository, crypto, branches);
                    }));
        }

        cases.add(test("create-concurrent-duplicate",
                "UTC030",
                "UTC-ACC-014",
                "createTranslatesConcurrentDuplicateToConflict",
                "Pre-checks pass but repository.insert throws DuplicateKeyException",
                "Valid CreateAccount",
                "AuthFailure status=409; repository.find is not called",
                () -> {
                    AccountRepository repository = mock(AccountRepository.class);
                    AuthCrypto crypto = mock(AuthCrypto.class);
                    BranchAdministrationService branches = mock(BranchAdministrationService.class);
                    CreateAccount input = accountInput("RECEPTIONIST", BRANCH_ID, "Aa1!bcde");
                    arrangeValid(repository, "RECEPTIONIST", BRANCH_ID);
                    when(crypto.encode("Aa1!bcde")).thenReturn("encoded-password");
                    when(repository.insert(input, "staff.rec", "staff@example.com", "encoded-password", ROLE_ID, ACTOR_ID))
                            .thenThrow(new DuplicateKeyException("concurrent duplicate"));
                    return ManualTestSupport.observe(
                            () -> new AccountService(repository, crypto, branches).create(input, ACTOR_ID), repository, crypto, branches);
                }));

        cases.add(test("create-branch-manager",
                "UTC048",
                "UTC-ACC-015",
                "createSynchronizesBranchManagerAfterInsertBeforeFind",
                "Employee, role and branch are valid; identity is unique",
                "role=BRANCH_MANAGER, branchId=" + BRANCH_ID,
                "Insert account, call branches.syncAccountManager(ACCOUNT_ID, ACTOR_ID), then find and return AccountView",
                () -> {
                    AccountRepository repository = mock(AccountRepository.class);
                    AuthCrypto crypto = mock(AuthCrypto.class);
                    BranchAdministrationService branches = mock(BranchAdministrationService.class);
                    CreateAccount input = accountInput("BRANCH_MANAGER", BRANCH_ID, "Aa1!bcde");
                    arrangeValid(repository, "BRANCH_MANAGER", BRANCH_ID);
                    when(crypto.encode("Aa1!bcde")).thenReturn("encoded-password");
                    when(repository.insert(input, "staff.rec", "staff@example.com", "encoded-password", ROLE_ID, ACTOR_ID))
                            .thenReturn(ACCOUNT_ID);
                    when(repository.find(ACCOUNT_ID)).thenReturn(account("BRANCH_MANAGER", BRANCH_ID));
                    return ManualTestSupport.observe(
                            () -> new AccountService(repository, crypto, branches).create(input, ACTOR_ID),
                            repository, crypto, branches);
                }));

        return new ManualTestSuite("Administration", "AccountService", cases);
    }

    private static ManualTestCase createSuccessCase(boolean chainRole) {
        String id = chainRole ? "UTC008" : "UTC007";
        String sourceId = chainRole ? "UTC-ACC-005" : "UTC-ACC-004";
        String role = chainRole ? "ACCOUNTANT" : "RECEPTIONIST";
        UUID scope = chainRole ? null : BRANCH_ID;

        return test("create-success-" + role.toLowerCase(),
                id,
                sourceId,
                chainRole ? "createAcceptsChainRoleOnlyWithoutBranchScope"
                        : "createNormalizesDataHashesPasswordAndPersistsBranchAccount",
                "Employee and role are available; identity is unique" + (chainRole ? "" : "; branch is active"),
                "username='  staff.rec  ', email='  STAFF@Example.com  ', password='Aa1!bcde', role=" + role,
                "Normalize identity, hash password, persist and return AccountView; no branch-manager synchronization",
                () -> {
                    AccountRepository repository = mock(AccountRepository.class);
                    AuthCrypto crypto = mock(AuthCrypto.class);
                    BranchAdministrationService branches = mock(BranchAdministrationService.class);
                    CreateAccount input = new CreateAccount(
                            EMPLOYEE_ID, "  staff.rec  ", "Aa1!bcde", "  STAFF@Example.com  ", role, scope);
                    arrangeValid(repository, role, scope);
                    when(crypto.encode("Aa1!bcde")).thenReturn("encoded-password");
                    when(repository.insert(input, "staff.rec", "staff@example.com", "encoded-password", ROLE_ID, ACTOR_ID))
                            .thenReturn(ACCOUNT_ID);
                    when(repository.find(ACCOUNT_ID)).thenReturn(account(role, scope));
                    return ManualTestSupport.observe(
                            () -> new AccountService(repository, crypto, branches).create(input, ACTOR_ID), repository, crypto, branches);
                });
    }

    private static void arrangeValid(AccountRepository repository, String role, UUID scope) {
        when(repository.lockAvailableEmployee(EMPLOYEE_ID)).thenReturn(true);
        when(repository.roleId(role)).thenReturn(ROLE_ID);
        if (!List.of("HEAD_OFFICE_MANAGER", "ACCOUNTANT").contains(role)) {
            when(repository.activeBranch(scope)).thenReturn(true);
        }
    }

    private static CreateAccount accountInput(String role, UUID scope, String password) {
        return new CreateAccount(EMPLOYEE_ID, "staff.rec", password, "staff@example.com", role, scope);
    }

    private static AccountView account(String role, UUID scope) {
        return new AccountView(
                ACCOUNT_ID, "Nguyen Van A", "NV-001", "staff.rec", "staff@example.com", role,
                scope, scope == null ? "Toan chuoi" : "Ho Tay", "ACTIVE");
    }

    private static String printable(String value) {
        return value == null ? "null" : "'" + value.replace("\n", "\\n").replace("\t", "\\t") + "'";
    }

    private static ManualTestCase test(String key,
                String documentId,
                String sourceId,
                String method,
                String preconditions,
                String input,
                String expected,
                ManualTestCase.Executor executor) {
        return new ManualTestCase("account/" + key,
                ManualTestCase.identifier(documentId, sourceId),
                "AccountServiceTest",
                method,
                preconditions,
                input,
                expected,
                executor);
    }
}
