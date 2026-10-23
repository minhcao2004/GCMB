/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 13/10/2026
 * Description: Unit Test Case cho xem và cập nhật thông tin tài khoản cá nhân theo UC-03, BR-113 và BR-119.
 */

package com.gcmb.backend.accounts.service;

import com.gcmb.backend.accounts.dto.AccountDtos.AccountView;
import com.gcmb.backend.accounts.dto.AccountProfileDtos.Profile;
import com.gcmb.backend.accounts.dto.AccountProfileDtos.UpdateProfile;
import com.gcmb.backend.accounts.repository.AccountProfileRepository;
import com.gcmb.backend.auth.exception.AuthFailure;
import com.gcmb.backend.auth.security.AuthCrypto;
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
import org.springframework.dao.EmptyResultDataAccessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountProfileServiceTest {
    @Mock
    private AccountProfileRepository repository;

    @Mock
    private AuthCrypto crypto;

    private AccountProfileService service;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        service = new AccountProfileService(repository, crypto);
        actorId = UUID.randomUUID();
    }

    @Test
    @DisplayName("UTC-PRO-001 view returns the active session owner's profile")
    void viewReturnsActiveProfile() {
        Profile expected = profile("ACTIVE", true, "staff@example.com", "Nguyen Van A", null);
        when(repository.find(actorId)).thenReturn(expected);

        assertSame(expected, service.view(actorId));

        verify(repository).find(actorId);
        verifyNoInteractions(crypto);
    }

    static Stream<String> inactiveStatuses() {
        return Stream.of("REVOKED", "LOCKED", "INACTIVE");
    }

    @ParameterizedTest(name = "UTC-PRO-002 rejects status {0}")
    @MethodSource("inactiveStatuses")
    void viewRejectsNonActiveAccount(String status) {
        when(repository.find(actorId)).thenReturn(profile(status, true, "staff@example.com", "Nguyen Van A", null));

        AuthFailure failure = assertThrows(AuthFailure.class, () -> service.view(actorId));

        assertEquals(403, failure.status());
        verifyNoInteractions(crypto);
    }

    @Test
    @DisplayName("UTC-PRO-003 view translates missing account to 404")
    void viewTranslatesMissingAccountToNotFound() {
        when(repository.find(actorId)).thenThrow(new EmptyResultDataAccessException(1));

        AuthFailure failure = assertThrows(AuthFailure.class, () -> service.view(actorId));

        assertEquals(404, failure.status());
        verifyNoInteractions(crypto);
    }

    @Test
    @DisplayName("UTC-PRO-004 update linked profile normalizes fields and writes audit")
    void updateLinkedProfileNormalizesFieldsAndWritesAudit() {
        Profile before = profile("ACTIVE", true, "staff@example.com", "Nguyen Van A", null);
        Profile after = profile("ACTIVE", true, "staff@example.com", "Nguyen Van B", "0901234567");
        when(repository.lockActiveAccount(actorId)).thenReturn("stored-hash");
        when(repository.find(actorId)).thenReturn(before, after);

        Profile actual = service.update(
                actorId,
                new UpdateProfile("  Nguyen Van B  ", "  0901234567  ", "STAFF@example.com", null));

        assertSame(after, actual);
        verify(repository).update(
                actorId, "Nguyen Van B", "0901234567", "staff@example.com", false);
        verify(repository).audit(before, after);
        verifyNoInteractions(crypto);
    }

    @Test
    @DisplayName("UTC-PRO-005 update changed email verifies current password according to BR-113")
    void updateChangedEmailVerifiesPasswordAndAudits() {
        Profile before = profile("ACTIVE", true, "old@example.com", "Nguyen Van A", null);
        Profile after = profile("ACTIVE", true, "new@example.com", "Nguyen Van A", null);
        when(repository.lockActiveAccount(actorId)).thenReturn("stored-hash");
        when(repository.find(actorId)).thenReturn(before, after);
        when(crypto.matches("Current1!", "stored-hash")).thenReturn(true);
        when(repository.emailTaken(actorId, "new@example.com")).thenReturn(false);

        Profile actual = service.update(
                actorId,
                new UpdateProfile("Nguyen Van A", null, " NEW@example.com ", "Current1!"));

        assertSame(after, actual);
        verify(repository).update(actorId, "Nguyen Van A", null, "new@example.com", true);
        verify(repository).audit(before, after);
    }

    @Test
    @DisplayName("UTC-PRO-006 update returns current profile when no value changed")
    void updateReturnsCurrentProfileWhenNothingChanged() {
        Profile before = profile("ACTIVE", true, "staff@example.com", "Nguyen Van A", null);
        when(repository.lockActiveAccount(actorId)).thenReturn("stored-hash");
        when(repository.find(actorId)).thenReturn(before);

        assertSame(
                before,
                service.update(actorId, new UpdateProfile("Nguyen Van A", "", "staff@example.com", null)));

        verify(repository, never()).update(any(), any(), any(), any(), anyBoolean());
        verify(repository, never()).audit(any(), any());
        verifyNoInteractions(crypto);
    }

    @Test
    @DisplayName("UTC-PRO-007 update rejects malformed email before locking account")
    void updateRejectsMalformedEmailBeforeLockingAccount() {
        AuthFailure failure = assertThrows(
                AuthFailure.class,
                () -> service.update(
                        actorId,
                        new UpdateProfile("Nguyen Van A", null, "staff @example.com", null)));

        assertEquals(400, failure.status());
        verifyNoInteractions(repository, crypto);
    }

    @Test
    @DisplayName("UTC-PRO-008 update rejects account that became inactive")
    void updateRejectsAccountThatBecameInactive() {
        when(repository.lockActiveAccount(actorId)).thenReturn(null);

        AuthFailure failure = assertThrows(
                AuthFailure.class,
                () -> service.update(
                        actorId,
                        new UpdateProfile("Nguyen Van A", null, "staff@example.com", null)));

        assertEquals(403, failure.status());
        verify(repository, never()).find(any());
        verifyNoInteractions(crypto);
    }

    @Test
    @DisplayName("UTC-PRO-009 update rejects blank linked employee full name")
    void updateRejectsBlankLinkedEmployeeFullName() {
        Profile before = profile("ACTIVE", true, "staff@example.com", "Nguyen Van A", null);
        when(repository.lockActiveAccount(actorId)).thenReturn("stored-hash");
        when(repository.find(actorId)).thenReturn(before);

        AuthFailure failure = assertThrows(
                AuthFailure.class,
                () -> service.update(
                        actorId,
                        new UpdateProfile(" ", null, "staff@example.com", null)));

        assertEquals(400, failure.status());
        verify(repository, never()).update(any(), any(), any(), any(), anyBoolean());
        verifyNoInteractions(crypto);
    }

    static Stream<Arguments> forbiddenUnlinkedEmployeeFields() {
        return Stream.of(
                Arguments.of("full name", "Fake Employee", null),
                Arguments.of("phone", null, "0901234567"));
    }

    @ParameterizedTest(name = "UTC-PRO-010 rejects unlinked {0}")
    @MethodSource("forbiddenUnlinkedEmployeeFields")
    void updateRejectsEmployeeFieldsWhenAccountIsUnlinked(String field, String fullName, String phone) {
        Profile before = profile("ACTIVE", false, "staff@example.com", null, null);
        when(repository.lockActiveAccount(actorId)).thenReturn("stored-hash");
        when(repository.find(actorId)).thenReturn(before);

        AuthFailure failure = assertThrows(
                AuthFailure.class,
                () -> service.update(
                        actorId,
                        new UpdateProfile(fullName, phone, "staff@example.com", null)));

        assertEquals(409, failure.status(), field);
        verify(repository, never()).update(any(), any(), any(), any(), anyBoolean());
        verifyNoInteractions(crypto);
    }

    @Test
    @DisplayName("UTC-PRO-011 unlinked account may change only its email")
    void updateUnlinkedAccountMayChangeOnlyEmail() {
        Profile before = profile("ACTIVE", false, "old@example.com", null, null);
        Profile after = profile("ACTIVE", false, "new@example.com", null, null);
        when(repository.lockActiveAccount(actorId)).thenReturn("stored-hash");
        when(repository.find(actorId)).thenReturn(before, after);
        when(crypto.matches("Current1!", "stored-hash")).thenReturn(true);

        assertSame(
                after,
                service.update(actorId, new UpdateProfile(null, null, "new@example.com", "Current1!")));

        verify(repository).update(actorId, null, null, "new@example.com", true);
        verify(repository).audit(before, after);
    }

    @Test
    @DisplayName("UTC-PRO-012 email change requires current password")
    void updateEmailRequiresCurrentPassword() {
        Profile before = profile("ACTIVE", true, "old@example.com", "Nguyen Van A", null);
        when(repository.lockActiveAccount(actorId)).thenReturn("stored-hash");
        when(repository.find(actorId)).thenReturn(before);

        AuthFailure failure = assertThrows(
                AuthFailure.class,
                () -> service.update(
                        actorId,
                        new UpdateProfile("Nguyen Van A", null, "new@example.com", " ")));

        assertEquals(400, failure.status());
        verifyNoInteractions(crypto);
        verify(repository, never()).update(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("UTC-PRO-013 email change rejects incorrect current password")
    void updateEmailRejectsIncorrectCurrentPassword() {
        Profile before = profile("ACTIVE", true, "old@example.com", "Nguyen Van A", null);
        when(repository.lockActiveAccount(actorId)).thenReturn("stored-hash");
        when(repository.find(actorId)).thenReturn(before);
        when(crypto.matches("Wrong1!", "stored-hash")).thenReturn(false);

        AuthFailure failure = assertThrows(
                AuthFailure.class,
                () -> service.update(
                        actorId,
                        new UpdateProfile("Nguyen Van A", null, "new@example.com", "Wrong1!")));

        assertEquals(400, failure.status());
        verify(repository, never()).emailTaken(any(), any());
        verify(repository, never()).update(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("UTC-PRO-014 email change rejects address owned by another account")
    void updateEmailRejectsDuplicateAddress() {
        Profile before = profile("ACTIVE", true, "old@example.com", "Nguyen Van A", null);
        when(repository.lockActiveAccount(actorId)).thenReturn("stored-hash");
        when(repository.find(actorId)).thenReturn(before);
        when(crypto.matches("Current1!", "stored-hash")).thenReturn(true);
        when(repository.emailTaken(actorId, "new@example.com")).thenReturn(true);

        AuthFailure failure = assertThrows(
                AuthFailure.class,
                () -> service.update(
                        actorId,
                        new UpdateProfile("Nguyen Van A", null, "new@example.com", "Current1!")));

        assertEquals(409, failure.status());
        verify(repository, never()).update(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("UTC-PRO-015 concurrent duplicate email becomes conflict without audit")
    void updateTranslatesConcurrentDuplicateEmailWithoutAudit() {
        Profile before = profile("ACTIVE", true, "old@example.com", "Nguyen Van A", null);
        when(repository.lockActiveAccount(actorId)).thenReturn("stored-hash");
        when(repository.find(actorId)).thenReturn(before);
        when(crypto.matches("Current1!", "stored-hash")).thenReturn(true);
        when(repository.emailTaken(actorId, "new@example.com")).thenReturn(false);
        org.mockito.Mockito.doThrow(new DuplicateKeyException("concurrent duplicate"))
                .when(repository)
                .update(actorId, "Nguyen Van A", null, "new@example.com", true);

        AuthFailure failure = assertThrows(
                AuthFailure.class,
                () -> service.update(
                        actorId,
                        new UpdateProfile("Nguyen Van A", null, "new@example.com", "Current1!")));

        assertEquals(409, failure.status());
        verify(repository, never()).audit(any(), any());
    }

    static Stream<Arguments> validPhoneNumbersUnderBr119() {
        return Stream.of(
                Arguments.of("lower boundary of seven digits", "0123456", "0123456"),
                Arguments.of("ordinary local number", "0901234567", "0901234567"),
                Arguments.of("upper boundary of 15 digits", "0" + "1".repeat(14), "0" + "1".repeat(14)),
                Arguments.of("outer spaces are trimmed", "  0901234567  ", "0901234567"));
    }

    @ParameterizedTest(name = "UTC-PRO-016 accepts BR-119 {0}")
    @MethodSource("validPhoneNumbersUnderBr119")
    void updateAcceptsPhoneComplyingWithBr119(
            String caseName,
            String inputPhone,
            String normalizedPhone) {
        Profile before = profile("ACTIVE", true, "staff@example.com", "Nguyen Van A", null);
        Profile after = profile("ACTIVE", true, "staff@example.com", "Nguyen Van A", normalizedPhone);
        when(repository.lockActiveAccount(actorId)).thenReturn("stored-hash");
        when(repository.find(actorId)).thenReturn(before, after);

        Profile actual = service.update(
                actorId,
                new UpdateProfile("Nguyen Van A", inputPhone, "staff@example.com", null));

        assertSame(after, actual, caseName);
        verify(repository).update(
                actorId, "Nguyen Van A", normalizedPhone, "staff@example.com", false);
        verify(repository).audit(before, after);
        verifyNoInteractions(crypto);
    }

    static Stream<Arguments> invalidPhoneNumbersUnderBr119() {
        return Stream.of(
                Arguments.of("fewer than seven digits", "012345"),
                Arguments.of("more than 15 digits", "0" + "1".repeat(15)),
                Arguments.of("first digit is not zero", "1234567"),
                Arguments.of("leading plus sign", "+84901234567"),
                Arguments.of("formatting spaces", "090 123 4567"),
                Arguments.of("alphabetic characters", "09012ABC"),
                Arguments.of("hyphen formatting", "090-123-4567"));
    }

    @ParameterizedTest(name = "UTC-PRO-017 rejects BR-119 {0}")
    @MethodSource("invalidPhoneNumbersUnderBr119")
    void updateRejectsPhoneViolatingBr119(String caseName, String phone) {
        Profile before = profile("ACTIVE", true, "staff@example.com", "Nguyen Van A", null);
        when(repository.lockActiveAccount(actorId)).thenReturn("stored-hash");
        when(repository.find(actorId)).thenReturn(before);

        AuthFailure failure = assertThrows(
                AuthFailure.class,
                () -> service.update(
                        actorId,
                        new UpdateProfile("Nguyen Van A", phone, "staff@example.com", null)));

        assertEquals(400, failure.status(), caseName);
        verify(repository, never()).update(any(), any(), any(), any(), anyBoolean());
        verify(repository, never()).audit(any(), any());
        verifyNoInteractions(crypto);
    }

    private Profile profile(
            String status,
            boolean linkedEmployee,
            String email,
            String fullName,
            String phone) {
        AccountView account = new AccountView(
                actorId,
                fullName,
                linkedEmployee ? "NV-001" : null,
                "staff.rec",
                email,
                "RECEPTIONIST",
                UUID.randomUUID(),
                "Ho Tay",
                status);
        return new Profile(account, fullName, phone, linkedEmployee);
    }
}
