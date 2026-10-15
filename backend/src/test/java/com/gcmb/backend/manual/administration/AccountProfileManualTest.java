/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 14/10/2026
 * Description: Covers profile retrieval, updates and account-profile validation rules.
 */

package com.gcmb.backend.manual.administration;

import com.gcmb.backend.administration.accounts.dto.AccountDtos.AccountView;
import com.gcmb.backend.administration.accounts.dto.AccountProfileDtos.Profile;
import com.gcmb.backend.administration.accounts.dto.AccountProfileDtos.UpdateProfile;
import com.gcmb.backend.administration.accounts.repository.AccountProfileRepository;
import com.gcmb.backend.administration.accounts.service.AccountProfileService;
import com.gcmb.backend.administration.auth.security.AuthCrypto;
import com.gcmb.backend.manual.ManualTestCase;
import com.gcmb.backend.manual.ManualTestSuite;
import com.gcmb.backend.manual.ManualTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class AccountProfileManualTest {
    private static final UUID ACTOR_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID BRANCH_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");

    private AccountProfileManualTest() {
    }

    public static ManualTestSuite suite() {
        List<ManualTestCase> cases = new ArrayList<>();

        cases.add(test("view-active", "UTC104", "UTC-PRO-001", "viewReturnsActiveProfile", 121,
                "The signed-in account exists and is ACTIVE", "actorId",
                "Return the same Profile; call repository.find; do not call crypto", () -> {
                    Context context = context();
                    Profile expected = profile("ACTIVE", true, "staff@example.com", "Nguyen Van A", null);
                    when(context.repository.find(ACTOR_ID)).thenReturn(expected);
                    return ManualTestSupport.observe(() -> context.service.view(ACTOR_ID), context.repository, context.crypto);
                }));

        Object[][] statuses = {{"UTC105", "REVOKED", 122}, {"UTC106", "LOCKED", 123}, {"UTC107", "INACTIVE", 124}};
        for (Object[] data : statuses) {
            String id = (String) data[0];
            String status = (String) data[1];
            int row = (Integer) data[2];
            cases.add(test("view-status-" + status.toLowerCase(), id, "UTC-PRO-002",
                    "viewRejectsNonActiveAccount[" + status + "]", row,
                    "Account exists with status=" + status, "actorId",
                    "AuthFailure status=403; crypto is not called", () -> {
                        Context context = context();
                        when(context.repository.find(ACTOR_ID))
                                .thenReturn(profile(status, true, "staff@example.com", "Nguyen Van A", null));
                        return ManualTestSupport.observe(() -> context.service.view(ACTOR_ID), context.repository, context.crypto);
                    }));
        }

        cases.add(test("view-missing", "UTC108", "UTC-PRO-003", "viewTranslatesMissingAccountToNotFound", 125,
                "repository.find throws EmptyResultDataAccessException", "actorId does not exist",
                "Translate to AuthFailure status=404; crypto is not called", () -> {
                    Context context = context();
                    when(context.repository.find(ACTOR_ID)).thenThrow(new EmptyResultDataAccessException(1));
                    return ManualTestSupport.observe(() -> context.service.view(ACTOR_ID), context.repository, context.crypto);
                }));

        cases.add(test("update-normalize", "UTC109", "UTC-PRO-004",
                "updateLinkedProfileNormalizesFieldsAndWritesAudit", 127,
                "ACTIVE linked account; before and after profiles are available",
                "fullName='  Nguyen Van B  ', phone='  0901234567  ', email='STAFF@example.com'",
                "Normalize fields, update linked profile, write audit and return after profile", () -> {
                    Context context = context();
                    Profile before = profile("ACTIVE", true, "staff@example.com", "Nguyen Van A", null);
                    Profile after = profile("ACTIVE", true, "staff@example.com", "Nguyen Van B", "0901234567");
                    arrange(context, before, after);
                    return ManualTestSupport.observe(
                            () -> context.service.update(ACTOR_ID,
                                    new UpdateProfile("  Nguyen Van B  ", "  0901234567  ", "STAFF@example.com", null)),
                            context.repository, context.crypto);
                }));

        cases.add(test("update-email", "UTC110", "UTC-PRO-005",
                "updateChangedEmailVerifiesPasswordAndAudits", 128,
                "ACTIVE linked account; current password matches; new email is unused",
                "email=' NEW@example.com ', currentPassword='Current1!'",
                "Normalize email, verify password, update, audit and return after profile", () -> {
                    Context context = context();
                    Profile before = profile("ACTIVE", true, "old@example.com", "Nguyen Van A", null);
                    Profile after = profile("ACTIVE", true, "new@example.com", "Nguyen Van A", null);
                    arrange(context, before, after);
                    when(context.crypto.matches("Current1!", "stored-hash")).thenReturn(true);
                    when(context.repository.emailTaken(ACTOR_ID, "new@example.com")).thenReturn(false);
                    return ManualTestSupport.observe(
                            () -> context.service.update(ACTOR_ID,
                                    new UpdateProfile("Nguyen Van A", null, " NEW@example.com ", "Current1!")),
                            context.repository, context.crypto);
                }));

        cases.add(test("update-no-change", "UTC111", "UTC-PRO-006",
                "updateReturnsCurrentProfileWhenNothingChanged", 129,
                "ACTIVE linked account; requested values equal current values",
                "fullName and email unchanged; phone=''", "Return current Profile; no update, audit or crypto call", () -> {
                    Context context = context();
                    Profile before = profile("ACTIVE", true, "staff@example.com", "Nguyen Van A", null);
                    arrange(context, before);
                    return ManualTestSupport.observe(
                            () -> context.service.update(ACTOR_ID,
                                    new UpdateProfile("Nguyen Van A", "", "staff@example.com", null)),
                            context.repository, context.crypto);
                }));

        cases.add(test("update-malformed-email", "UTC112", "UTC-PRO-007",
                "updateRejectsMalformedEmailBeforeLockingAccount", 130,
                "No dependency setup required", "email='staff @example.com'",
                "AuthFailure status=400 before repository or crypto is called", () -> {
                    Context context = context();
                    return ManualTestSupport.observe(
                            () -> context.service.update(ACTOR_ID,
                                    new UpdateProfile("Nguyen Van A", null, "staff @example.com", null)),
                            context.repository, context.crypto);
                }));

        cases.add(test("update-inactive", "UTC113", "UTC-PRO-008", "updateRejectsAccountThatBecameInactive", 131,
                "lockActiveAccount returns null", "Valid update request",
                "AuthFailure status=403; repository.find and crypto are not called", () -> {
                    Context context = context();
                    when(context.repository.lockActiveAccount(ACTOR_ID)).thenReturn(null);
                    return ManualTestSupport.observe(
                            () -> context.service.update(ACTOR_ID,
                                    new UpdateProfile("Nguyen Van A", null, "staff@example.com", null)),
                            context.repository, context.crypto);
                }));

        cases.add(test("update-blank-name", "UTC114", "UTC-PRO-009",
                "updateRejectsBlankLinkedEmployeeFullName", 132,
                "ACTIVE linked account", "fullName=' '",
                "AuthFailure status=400; no update and no crypto call", () -> {
                    Context context = context();
                    arrange(context, profile("ACTIVE", true, "staff@example.com", "Nguyen Van A", null));
                    return ManualTestSupport.observe(
                            () -> context.service.update(ACTOR_ID,
                                    new UpdateProfile(" ", null, "staff@example.com", null)),
                            context.repository, context.crypto);
                }));

        Object[][] unlinked = {
                {"UTC115", "full name", "Fake Employee", null, 133},
                {"UTC116", "phone", null, "0901234567", 134}
        };
        for (Object[] data : unlinked) {
            String id = (String) data[0];
            String field = (String) data[1];
            String name = (String) data[2];
            String phone = (String) data[3];
            int row = (Integer) data[4];
            cases.add(test("update-unlinked-" + field.replace(' ', '-'), id, "UTC-PRO-010",
                    "updateRejectsEmployeeFieldsWhenAccountIsUnlinked[" + field + "]", row,
                    "ACTIVE account is not linked to an employee", field + " is supplied",
                    "AuthFailure status=409; no update and no crypto call", () -> {
                        Context context = context();
                        arrange(context, profile("ACTIVE", false, "staff@example.com", null, null));
                        return ManualTestSupport.observe(
                                () -> context.service.update(ACTOR_ID,
                                        new UpdateProfile(name, phone, "staff@example.com", null)),
                                context.repository, context.crypto);
                    }));
        }

        cases.add(test("update-unlinked-email", "UTC117", "UTC-PRO-011",
                "updateUnlinkedAccountMayChangeOnlyEmail", 135,
                "ACTIVE unlinked account; current password matches; email is unused",
                "Only email changes to new@example.com",
                "Update email, audit and return after profile", () -> {
                    Context context = context();
                    Profile before = profile("ACTIVE", false, "old@example.com", null, null);
                    Profile after = profile("ACTIVE", false, "new@example.com", null, null);
                    arrange(context, before, after);
                    when(context.crypto.matches("Current1!", "stored-hash")).thenReturn(true);
                    return ManualTestSupport.observe(
                            () -> context.service.update(ACTOR_ID,
                                    new UpdateProfile(null, null, "new@example.com", "Current1!")),
                            context.repository, context.crypto);
                }));

        cases.add(test("update-email-password-required", "UTC118", "UTC-PRO-012",
                "updateEmailRequiresCurrentPassword", 136,
                "ACTIVE account; requested email differs", "currentPassword=' '",
                "AuthFailure status=400; no update and no crypto call", () -> {
                    Context context = context();
                    arrange(context, profile("ACTIVE", true, "old@example.com", "Nguyen Van A", null));
                    return ManualTestSupport.observe(
                            () -> context.service.update(ACTOR_ID,
                                    new UpdateProfile("Nguyen Van A", null, "new@example.com", " ")),
                            context.repository, context.crypto);
                }));

        cases.add(test("update-email-wrong-password", "UTC119", "UTC-PRO-013",
                "updateEmailRejectsIncorrectCurrentPassword", 137,
                "ACTIVE account; crypto.matches returns false", "new email; currentPassword='Wrong1!'",
                "AuthFailure status=400; no emailTaken lookup or update", () -> {
                    Context context = context();
                    arrange(context, profile("ACTIVE", true, "old@example.com", "Nguyen Van A", null));
                    when(context.crypto.matches("Wrong1!", "stored-hash")).thenReturn(false);
                    return ManualTestSupport.observe(
                            () -> context.service.update(ACTOR_ID,
                                    new UpdateProfile("Nguyen Van A", null, "new@example.com", "Wrong1!")),
                            context.repository, context.crypto);
                }));

        cases.add(test("update-email-duplicate", "UTC120", "UTC-PRO-014", "updateEmailRejectsDuplicateAddress", 138,
                "ACTIVE account; password matches; emailTaken=true", "new email",
                "AuthFailure status=409; no update", () -> {
                    Context context = context();
                    arrange(context, profile("ACTIVE", true, "old@example.com", "Nguyen Van A", null));
                    when(context.crypto.matches("Current1!", "stored-hash")).thenReturn(true);
                    when(context.repository.emailTaken(ACTOR_ID, "new@example.com")).thenReturn(true);
                    return ManualTestSupport.observe(
                            () -> context.service.update(ACTOR_ID,
                                    new UpdateProfile("Nguyen Van A", null, "new@example.com", "Current1!")),
                            context.repository, context.crypto);
                }));

        cases.add(test("update-email-race", "UTC121", "UTC-PRO-015",
                "updateTranslatesConcurrentDuplicateEmailWithoutAudit", 139,
                "Pre-checks pass; repository.update throws DuplicateKeyException", "new email",
                "AuthFailure status=409; no audit", () -> {
                    Context context = context();
                    Profile before = profile("ACTIVE", true, "old@example.com", "Nguyen Van A", null);
                    arrange(context, before);
                    when(context.crypto.matches("Current1!", "stored-hash")).thenReturn(true);
                    when(context.repository.emailTaken(ACTOR_ID, "new@example.com")).thenReturn(false);
                    doThrow(new DuplicateKeyException("concurrent duplicate"))
                            .when(context.repository).update(ACTOR_ID, "Nguyen Van A", null, "new@example.com", true);
                    return ManualTestSupport.observe(
                            () -> context.service.update(ACTOR_ID,
                                    new UpdateProfile("Nguyen Van A", null, "new@example.com", "Current1!")),
                            context.repository, context.crypto);
                }));

        Object[][] validPhones = {
                {"UTC122", "lower boundary", "0123456", "0123456", 140},
                {"UTC123", "ordinary local number", "0901234567", "0901234567", 141},
                {"UTC124", "upper boundary", "0" + "1".repeat(14), "0" + "1".repeat(14), 142},
                {"UTC125", "trim outer spaces", "  0901234567  ", "0901234567", 143}
        };
        for (Object[] data : validPhones) {
            String id = (String) data[0];
            String label = (String) data[1];
            String inputPhone = (String) data[2];
            String normalized = (String) data[3];
            int row = (Integer) data[4];
            cases.add(test("phone-valid-" + label.replace(' ', '-'), id, "UTC-PRO-016",
                    "updateAcceptsPhoneComplyingWithBr119[" + label + "]", row,
                    "ACTIVE linked account", "phone='" + inputPhone + "'",
                    "Normalize to '" + normalized + "', update, audit and return after profile", () -> {
                        Context context = context();
                        Profile before = profile("ACTIVE", true, "staff@example.com", "Nguyen Van A", null);
                        Profile after = profile("ACTIVE", true, "staff@example.com", "Nguyen Van A", normalized);
                        arrange(context, before, after);
                        return ManualTestSupport.observe(
                                () -> context.service.update(ACTOR_ID,
                                        new UpdateProfile("Nguyen Van A", inputPhone, "staff@example.com", null)),
                                context.repository, context.crypto);
                    }));
        }

        Object[][] invalidPhones = {
                {"UTC126", "fewer than 7 digits", "012345", 144},
                {"UTC127", "more than 15 digits", "0" + "1".repeat(15), 145},
                {"UTC128", "first digit is not zero", "1234567", 146},
                {"UTC129", "leading plus sign", "+84901234567", 147},
                {"UTC130", "formatting spaces", "090 123 4567", 148},
                {"UTC131", "alphabetic characters", "09012ABC", 149},
                {"UTC132", "hyphen formatting", "090-123-4567", 150}
        };
        for (Object[] data : invalidPhones) {
            String id = (String) data[0];
            String label = (String) data[1];
            String phone = (String) data[2];
            int row = (Integer) data[3];
            cases.add(test("phone-invalid-" + label.replace(' ', '-'), id, "UTC-PRO-017",
                    "updateRejectsPhoneViolatingBr119[" + label + "]", row,
                    "ACTIVE linked account", "phone='" + phone + "'",
                    "AuthFailure status=400; no update, audit or crypto call", () -> {
                        Context context = context();
                        arrange(context, profile("ACTIVE", true, "staff@example.com", "Nguyen Van A", null));
                        return ManualTestSupport.observe(
                                () -> context.service.update(ACTOR_ID,
                                        new UpdateProfile("Nguyen Van A", phone, "staff@example.com", null)),
                                context.repository, context.crypto);
                    }));
        }

        return new ManualTestSuite("Administration", "AccountProfileService", cases);
    }

    private static Context context() {
        AccountProfileRepository repository = mock(AccountProfileRepository.class);
        AuthCrypto crypto = mock(AuthCrypto.class);
        return new Context(repository, crypto, new AccountProfileService(repository, crypto));
    }

    private static void arrange(Context context, Profile... profiles) {
        when(context.repository.lockActiveAccount(ACTOR_ID)).thenReturn("stored-hash");
        when(context.repository.find(ACTOR_ID)).thenReturn(
                profiles[0], java.util.Arrays.copyOfRange(profiles, 1, profiles.length));
    }

    private static Profile profile(String status, boolean linked, String email, String fullName, String phone) {
        AccountView account = new AccountView(
                ACTOR_ID, fullName, linked ? "NV-001" : null, "staff.rec", email, "RECEPTIONIST",
                BRANCH_ID, "Ho Tay", status);
        return new Profile(account, fullName, phone, linked);
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
        return new ManualTestCase(
                "account-profile/" + key, documentId, sourceId, "AccountProfileServiceTest", method, "Unit",
                "Personal Account Management!A" + workbookRow,
                preconditions, input, expected, executor);
    }

    private record Context(
            AccountProfileRepository repository,
            AuthCrypto crypto,
            AccountProfileService service) {
    }
}
