/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 13/10/2026
 * Description: Kiểm thử đơn vị nghiệp vụ đăng nhập, khôi phục mật khẩu, xác thực phiên và đăng xuất.
 */

package com.gcmb.backend.auth.service;

import com.gcmb.backend.auth.dto.LoginResult;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.dto.Recovery;
import com.gcmb.backend.auth.repository.model.Account;
import com.gcmb.backend.auth.repository.model.Reset;
import com.gcmb.backend.auth.exception.AuthFailure;
import com.gcmb.backend.auth.repository.AuthRepository;
import com.gcmb.backend.auth.security.AuthCrypto;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID RESET_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final String USERNAME = "staff";
    private static final String EMAIL = "staff@example.com";
    private static final String PASSWORD = "  Original password 123  ";
    private static final String NEW_PASSWORD = "ReplacementPassword123!";
    private static final String PASSWORD_HASH = "{pbkdf2-gcmb}stored-password";
    private static final String NEW_PASSWORD_HASH = "{pbkdf2-gcmb}new-password";
    private static final String CODE_HASH = "{pbkdf2-gcmb}stored-code";
    private static final String CODE = "038291";
    private static final String DUMMY_TOKEN = "D".repeat(43);
    private static final String DUMMY_HASH = "{pbkdf2-gcmb}dummy";
    private static final String SESSION_TOKEN = "S".repeat(43);
    private static final String CHALLENGE = "C".repeat(43);

    @Mock AuthRepository repository;
    @Mock AuthCrypto crypto;
    @Mock RecoveryMailer mailer;
    private AuthService service;

    @BeforeEach
    void setUp() {
        when(crypto.token()).thenReturn(DUMMY_TOKEN);
        when(crypto.encode(DUMMY_TOKEN)).thenReturn(DUMMY_HASH);
        service = new AuthService(repository, crypto, mailer);
        clearInvocations(repository, crypto, mailer);
    }

    private static Account account(String status, String role) {
        return new Account(USER_ID, USERNAME, EMAIL, PASSWORD_HASH, status, role, null, null);
    }

    private static Reset reset(Instant usedAt, Instant revokedAt) {
        return new Reset(RESET_ID, CODE_HASH, usedAt, revokedAt);
    }

    private void arrangeSuccessfulLogin() {
        when(repository.findByUsername(USERNAME)).thenReturn(account("ACTIVE", "RECEPTIONIST"));
        when(crypto.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);
        when(crypto.token()).thenReturn(SESSION_TOKEN);
    }

    private void arrangeSuccessfulRecovery() {
        when(repository.findByEmail(EMAIL)).thenReturn(account("ACTIVE", "RECEPTIONIST"));
        when(crypto.token()).thenReturn(CHALLENGE);
        when(crypto.code()).thenReturn(CODE);
        when(crypto.encode(EMAIL + ":" + CODE)).thenReturn(CODE_HASH);
    }

    private void arrangeReset(Reset reset) {
        when(repository.findByEmail(EMAIL)).thenReturn(account("ACTIVE", "RECEPTIONIST"));
        when(repository.findReset(USER_ID, AuthCrypto.hash(CHALLENGE))).thenReturn(reset);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void loginReturnsIdentityAndCreatesHashedSession(boolean remember) {
        arrangeSuccessfulLogin();

        LoginResult result = service.login("  " + USERNAME + "  ", PASSWORD, remember);

        assertEquals(USER_ID, result.user().id());
        assertEquals(USERNAME, result.user().username());
        assertEquals("RECEPTIONIST", result.user().role());
        assertEquals("/le-tan", result.user().destination());
        assertEquals(SESSION_TOKEN, result.token());
        assertEquals(remember ? Integer.MAX_VALUE : -1, result.seconds());
        verify(repository).findByUsername(USERNAME);
        verify(repository).recordLogin(eq(USER_ID), any(Instant.class));
        verify(repository).createSession(USER_ID, AuthCrypto.hash(SESSION_TOKEN));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n", "\u00a0", "\uFEFF"})
    void loginRejectsMissingUsername(String username) {
        AuthFailure failure = assertThrows(AuthFailure.class, () -> service.login(username, PASSWORD, false));

        assertEquals(400, failure.status());
        verifyNoInteractions(repository, mailer);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n", "\u00a0", "\uFEFF"})
    void loginRejectsMissingPassword(String password) {
        AuthFailure failure = assertThrows(AuthFailure.class, () -> service.login(USERNAME, password, false));

        assertEquals(400, failure.status());
        verifyNoInteractions(repository, mailer);
    }

    @Test
    void loginUsesDummyHashAndGenericErrorForUnknownUsername() {
        when(repository.findByUsername(USERNAME)).thenReturn(null);

        AuthFailure failure = assertThrows(AuthFailure.class, () -> service.login(USERNAME, PASSWORD, false));

        assertEquals(401, failure.status());
        assertEquals("Tên đăng nhập hoặc mật khẩu không đúng", failure.getMessage());
        verify(crypto).matches(PASSWORD, DUMMY_HASH);
        verify(repository, never()).recordLogin(any(), any());
        verify(repository, never()).createSession(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"LOCKED", "REVOKED", "INACTIVE"})
    void loginRejectsAccountThatIsNotActive(String status) {
        when(repository.findByUsername(USERNAME)).thenReturn(account(status, "RECEPTIONIST"));

        AuthFailure failure = assertThrows(AuthFailure.class, () -> service.login(USERNAME, PASSWORD, false));

        assertEquals(403, failure.status());
        verify(crypto, never()).matches(PASSWORD, PASSWORD_HASH);
        verify(repository, never()).recordLogin(any(), any());
        verify(repository, never()).createSession(any(), any());
    }

    @Test
    void loginRejectsWrongPasswordWithoutWritingSession() {
        when(repository.findByUsername(USERNAME)).thenReturn(account("ACTIVE", "RECEPTIONIST"));
        when(crypto.matches(PASSWORD, PASSWORD_HASH)).thenReturn(false);

        AuthFailure failure = assertThrows(AuthFailure.class, () -> service.login(USERNAME, PASSWORD, false));

        assertEquals(401, failure.status());
        verify(repository, never()).recordLogin(any(), any());
        verify(repository, never()).createSession(any(), any());
    }

    @Test
    void loginRejectsUnsupportedRoleBeforeWritingSession() {
        when(repository.findByUsername(USERNAME)).thenReturn(account("ACTIVE", "UNKNOWN_ROLE"));
        when(crypto.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);

        AuthFailure failure = assertThrows(AuthFailure.class, () -> service.login(USERNAME, PASSWORD, false));

        assertEquals(403, failure.status());
        verify(repository, never()).recordLogin(any(), any());
        verify(repository, never()).createSession(any(), any());
    }

    @Test
    void sendCodeNormalizesEmailRevokesOldResetAndSendsRawCode() {
        arrangeSuccessfulRecovery();

        Recovery result = service.sendCode("  STAFF@EXAMPLE.COM  ");

        assertEquals(CHALLENGE, result.challenge());
        assertEquals(EMAIL, result.email());
        verify(repository).findByEmail(EMAIL);
        verify(repository).revokePendingResets(eq(USER_ID), any(Instant.class));
        verify(repository).createReset(eq(USER_ID), eq(AuthCrypto.hash(CHALLENGE)), eq(CODE_HASH), any(Instant.class));
        verify(mailer).send(EMAIL, CODE);
        InOrder order = inOrder(repository, mailer);
        order.verify(repository).revokePendingResets(eq(USER_ID), any(Instant.class));
        order.verify(repository).createReset(eq(USER_ID), eq(AuthCrypto.hash(CHALLENGE)), eq(CODE_HASH), any(Instant.class));
        order.verify(mailer).send(EMAIL, CODE);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "not-an-email", ".user@example.com", "a..b@example.com"})
    void sendCodeRejectsInvalidEmail(String email) {
        AuthFailure failure = assertThrows(AuthFailure.class, () -> service.sendCode(email));

        assertEquals(400, failure.status());
        verifyNoInteractions(repository, mailer);
    }

    @Test
    void sendCodeRejectsUnknownEmailWithoutSendingMail() {
        when(repository.findByEmail(EMAIL)).thenReturn(null);

        AuthFailure failure = assertThrows(AuthFailure.class, () -> service.sendCode(EMAIL));

        assertEquals(404, failure.status());
        verify(repository, never()).createReset(any(), any(), any(), any());
        verifyNoInteractions(mailer);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LOCKED", "REVOKED", "INACTIVE"})
    void sendCodeRejectsAccountThatIsNotActive(String status) {
        when(repository.findByEmail(EMAIL)).thenReturn(account(status, "RECEPTIONIST"));

        AuthFailure failure = assertThrows(AuthFailure.class, () -> service.sendCode(EMAIL));

        assertEquals(403, failure.status());
        verify(repository, never()).createReset(any(), any(), any(), any());
        verifyNoInteractions(mailer);
    }

    @Test
    void sendCodePropagatesMailFailure() {
        arrangeSuccessfulRecovery();
        doThrow(new RecoveryMailer.MailUnavailable()).when(mailer).send(EMAIL, CODE);

        assertThrows(RecoveryMailer.MailUnavailable.class, () -> service.sendCode(EMAIL));

        verify(repository).revokePendingResets(eq(USER_ID), any(Instant.class));
        verify(repository).createReset(eq(USER_ID), eq(AuthCrypto.hash(CHALLENGE)), eq(CODE_HASH), any(Instant.class));
    }

    @Test
    void resetChangesPasswordConsumesResetAndRevokesTokens() {
        arrangeReset(reset(null, null));
        when(crypto.matches(EMAIL + ":" + CODE, CODE_HASH)).thenReturn(true);
        when(crypto.encode(NEW_PASSWORD)).thenReturn(NEW_PASSWORD_HASH);

        service.reset("  STAFF@EXAMPLE.COM  ", CHALLENGE, CODE, NEW_PASSWORD, NEW_PASSWORD);

        ArgumentCaptor<Instant> passwordTime = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> consumedTime = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> revokedTime = ArgumentCaptor.forClass(Instant.class);
        verify(repository).changePassword(eq(USER_ID), eq(NEW_PASSWORD_HASH), passwordTime.capture());
        verify(repository).consumeReset(eq(RESET_ID), consumedTime.capture());
        verify(repository).revokeTokens(eq(USER_ID), revokedTime.capture());
        assertEquals(passwordTime.getValue(), consumedTime.getValue());
        assertEquals(passwordTime.getValue(), revokedTime.getValue());
        verify(crypto).encode(NEW_PASSWORD);
        InOrder order = inOrder(repository);
        order.verify(repository).changePassword(eq(USER_ID), eq(NEW_PASSWORD_HASH), any(Instant.class));
        order.verify(repository).consumeReset(eq(RESET_ID), any(Instant.class));
        order.verify(repository).revokeTokens(eq(USER_ID), any(Instant.class));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "not-an-email", "a..b@example.com"})
    void resetRejectsInvalidEmail(String email) {
        AuthFailure failure = assertThrows(AuthFailure.class,
            () -> service.reset(email, CHALLENGE, CODE, NEW_PASSWORD, NEW_PASSWORD));

        assertEquals(400, failure.status());
        verifyNoInteractions(repository, mailer);
    }

    static Stream<Arguments> invalidPasswordPairs() {
        return Stream.of(
            Arguments.of(null, NEW_PASSWORD),
            Arguments.of("", NEW_PASSWORD),
            Arguments.of("   ", "   "),
            Arguments.of(NEW_PASSWORD, null),
            Arguments.of(NEW_PASSWORD, ""),
            Arguments.of(NEW_PASSWORD, "DifferentPassword123!")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidPasswordPairs")
    void resetRejectsMissingOrMismatchedPassword(String password, String confirmation) {
        AuthFailure failure = assertThrows(AuthFailure.class,
            () -> service.reset(EMAIL, CHALLENGE, CODE, password, confirmation));

        assertEquals(400, failure.status());
        verifyNoInteractions(repository, mailer);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "12345", "1234567", "１２３４５６", "12345a", " 12345"})
    void resetRejectsInvalidCode(String code) {
        AuthFailure failure = assertThrows(AuthFailure.class,
            () -> service.reset(EMAIL, CHALLENGE, code, NEW_PASSWORD, NEW_PASSWORD));

        assertEquals(400, failure.status());
        verifyNoInteractions(repository, mailer);
    }

    static Stream<String> invalidTokens() {
        return Stream.of(null, "", "short", "A" + "!".repeat(42), "A".repeat(42), "A".repeat(44));
    }

    @ParameterizedTest
    @MethodSource("invalidTokens")
    void resetRejectsInvalidChallenge(String challenge) {
        AuthFailure failure = assertThrows(AuthFailure.class,
            () -> service.reset(EMAIL, challenge, CODE, NEW_PASSWORD, NEW_PASSWORD));

        assertEquals(400, failure.status());
        verifyNoInteractions(repository, mailer);
    }

    @Test
    void resetHidesWhetherEmailExists() {
        when(repository.findByEmail(EMAIL)).thenReturn(null);

        AuthFailure failure = assertThrows(AuthFailure.class,
            () -> service.reset(EMAIL, CHALLENGE, CODE, NEW_PASSWORD, NEW_PASSWORD));

        assertEquals(400, failure.status());
        assertEquals("Mã xác nhận không đúng", failure.getMessage());
        verify(repository, never()).changePassword(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"LOCKED", "REVOKED", "INACTIVE"})
    void resetRejectsAccountThatIsNotActive(String status) {
        when(repository.findByEmail(EMAIL)).thenReturn(account(status, "RECEPTIONIST"));

        AuthFailure failure = assertThrows(AuthFailure.class,
            () -> service.reset(EMAIL, CHALLENGE, CODE, NEW_PASSWORD, NEW_PASSWORD));

        assertEquals(403, failure.status());
        verify(repository, never()).findReset(any(), any());
        verify(repository, never()).changePassword(any(), any(), any());
    }

    @Test
    void resetRejectsUnknownChallenge() {
        arrangeReset(null);

        AuthFailure failure = assertThrows(AuthFailure.class,
            () -> service.reset(EMAIL, CHALLENGE, CODE, NEW_PASSWORD, NEW_PASSWORD));

        assertEquals(400, failure.status());
        assertEquals("Mã xác nhận không đúng", failure.getMessage());
        verify(repository, never()).changePassword(any(), any(), any());
    }

    @Test
    void resetRejectsAlreadyUsedCode() {
        arrangeReset(reset(Instant.parse("2026-10-13T00:00:00Z"), null));

        AuthFailure failure = assertThrows(AuthFailure.class,
            () -> service.reset(EMAIL, CHALLENGE, CODE, NEW_PASSWORD, NEW_PASSWORD));

        assertEquals(400, failure.status());
        assertTrue(failure.getMessage().contains("đã được sử dụng"));
        verify(crypto, never()).matches(any(), any());
        verify(repository, never()).changePassword(any(), any(), any());
    }

    @Test
    void resetRejectsRevokedCode() {
        arrangeReset(reset(null, Instant.parse("2026-10-13T00:00:00Z")));

        AuthFailure failure = assertThrows(AuthFailure.class,
            () -> service.reset(EMAIL, CHALLENGE, CODE, NEW_PASSWORD, NEW_PASSWORD));

        assertEquals(400, failure.status());
        assertTrue(failure.getMessage().contains("hết hiệu lực"));
        verify(crypto, never()).matches(any(), any());
        verify(repository, never()).changePassword(any(), any(), any());
    }

    @Test
    void resetRejectsWrongCodeWithoutChangingState() {
        arrangeReset(reset(null, null));
        when(crypto.matches(EMAIL + ":" + CODE, CODE_HASH)).thenReturn(false);

        AuthFailure failure = assertThrows(AuthFailure.class,
            () -> service.reset(EMAIL, CHALLENGE, CODE, NEW_PASSWORD, NEW_PASSWORD));

        assertEquals(400, failure.status());
        assertEquals("Mã xác nhận không đúng", failure.getMessage());
        verify(repository, never()).changePassword(any(), any(), any());
        verify(repository, never()).consumeReset(any(), any());
        verify(repository, never()).revokeTokens(any(), any());
    }

    @ParameterizedTest
    @MethodSource("invalidTokens")
    void authenticateRejectsMalformedTokenWithoutRepositoryCall(String token) {
        assertNull(service.authenticate(token));

        verifyNoInteractions(repository, mailer);
    }

    @Test
    void authenticateReturnsIdentityFromHashedToken() {
        Identity identity = new Identity(USER_ID, USERNAME, "RECEPTIONIST", "/le-tan", null, null);
        when(repository.findSession(AuthCrypto.hash(SESSION_TOKEN))).thenReturn(identity);

        assertSame(identity, service.authenticate(SESSION_TOKEN));
        verify(repository).findSession(AuthCrypto.hash(SESSION_TOKEN));
    }

    @Test
    void authenticateReturnsNullWhenSessionDoesNotExist() {
        when(repository.findSession(AuthCrypto.hash(SESSION_TOKEN))).thenReturn(null);

        assertNull(service.authenticate(SESSION_TOKEN));
    }

    @Test
    void logoutIgnoresNullToken() {
        assertDoesNotThrow(() -> service.logout(null));

        verifyNoInteractions(repository, mailer);
    }

    static Stream<String> nonNullLogoutTokens() {
        return Stream.of("S" + "x".repeat(42), "malformed");
    }

    @ParameterizedTest
    @MethodSource("nonNullLogoutTokens")
    void logoutRevokesHashOfEveryNonNullToken(String token) {
        service.logout(token);

        verify(repository).revokeSession(AuthCrypto.hash(token));
    }
}
