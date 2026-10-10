package com.gcmb.backend.administration.auth.service;

import com.gcmb.backend.administration.auth.service.AuthService;
import com.gcmb.backend.administration.auth.service.RecoveryMailer;
import com.gcmb.backend.administration.auth.security.AuthCrypto;
import com.gcmb.backend.administration.auth.exception.AuthFailure;

import java.util.UUID;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest
@ActiveProfiles("auth-test")
@EnabledIfEnvironmentVariable(named = "AUTH_TEST_DB_URL", matches = ".+")
class AuthIntegrationTest {
    @Autowired AuthService service;
    @Autowired AuthCrypto crypto;
    @Autowired JdbcTemplate db;
    @Autowired WebApplicationContext context;
    @MockitoBean RecoveryMailer mailer;
    private String username;
    private String email;
    private UUID userId;
    private String code;
    private static final String PASSWORD = "  Original password 123  ";
    private static final String NEW_PASSWORD = "  Replacement password 123  ";
    @BeforeEach void seedIsolatedAccount() {
        username = "auth_test_" + UUID.randomUUID().toString().replace("-", "");
        email = username + "@example.invalid";
        db.update("INSERT INTO gcmb.role(code,name) VALUES ('RECEPTIONIST','Lễ tân') ON CONFLICT(code) DO NOTHING");
        UUID roleId = db.queryForObject("SELECT id FROM gcmb.role WHERE code='RECEPTIONIST'", UUID.class);
        userId = UUID.randomUUID();
        db.update("INSERT INTO gcmb.user_account(id,username,email,password_hash,role_id,status,failed_login_count) VALUES (?,?,?,?,?,'ACTIVE',0)", userId, username, email, crypto.encode(PASSWORD), roleId);
        doAnswer(invocation -> { code = invocation.getArgument(1); return null; }).when(mailer).send(eq(email), anyString());
    }
    private AuthFailure fails(Runnable action, String expected) {
        AuthFailure failure = assertThrows(AuthFailure.class, action::run);
        assertTrue(failure.getMessage().contains(expected), failure.getMessage()); return failure;
    }
    @Test void fullResetPreservesSpacesRevokesSessionsAndConsumesCode() {
        var login = service.login(username.toUpperCase(), PASSWORD, true);
        assertEquals("/le-tan", login.user().destination());
        assertNotNull(service.authenticate(login.token()));
        var recovery = service.sendCode("  " + email.toUpperCase() + "  ");
        assertTrue(code.matches("[0-9]{6}"));
        service.reset(email, recovery.challenge(), code, NEW_PASSWORD, NEW_PASSWORD);
        assertNull(service.authenticate(login.token()));
        fails(() -> service.reset(email, recovery.challenge(), code, NEW_PASSWORD, NEW_PASSWORD), "đã được sử dụng");
        fails(() -> service.login(username, PASSWORD, false), "không đúng");
        fails(() -> service.login(username, NEW_PASSWORD.trim(), false), "không đúng");
        assertNotNull(service.login(username, NEW_PASSWORD, false));
    }
    @Test void leadingZeroAndTamperedEmailCannotBypassVerification() {
        var recovery = service.sendCode(email);
        db.update("UPDATE gcmb.auth_token SET code_hash=? WHERE token_hash=?", crypto.encode(email + ":038291"), AuthCrypto.hash(recovery.challenge()));
        fails(() -> service.reset("other@example.invalid", recovery.challenge(), "038291", NEW_PASSWORD, NEW_PASSWORD), "không đúng");
        fails(() -> service.reset(email, crypto.token(), "038291", NEW_PASSWORD, NEW_PASSWORD), "không đúng");
        service.reset(email, recovery.challenge(), "038291", NEW_PASSWORD, NEW_PASSWORD);
        assertNotNull(service.login(username, NEW_PASSWORD, false));
    }
    @Test void failedSmtpPreservesOldCodeAndNewSendInvalidatesIt() {
        var first = service.sendCode(email);
        String firstCode = code;
        doThrow(new RecoveryMailer.MailUnavailable()).when(mailer).send(eq(email), anyString());
        assertThrows(RecoveryMailer.MailUnavailable.class, () -> service.sendCode(email));
        assertEquals(1, db.queryForObject("SELECT count(*) FROM gcmb.auth_token WHERE user_id=? AND kind='PASSWORD_RESET' AND revoked_at IS NULL", Integer.class, userId));
        assertNull(db.queryForObject("SELECT revoked_at FROM gcmb.auth_token WHERE token_hash=?", java.sql.Timestamp.class, AuthCrypto.hash(first.challenge())));
        doAnswer(invocation -> { code = invocation.getArgument(1); return null; }).when(mailer).send(eq(email), anyString());
        var second = service.sendCode(email);
        fails(() -> service.reset(email, first.challenge(), firstCode, NEW_PASSWORD, NEW_PASSWORD), "hết hiệu lực");
        service.reset(email, second.challenge(), code, NEW_PASSWORD, NEW_PASSWORD);
    }
    @Test void wrongCodesDoNotChangePasswordAndDoNotAddAnAttemptLimit() {
        var recovery = service.sendCode(email);
        String correct = code;
        String wrong = "000000".equals(correct) ? "000001" : "000000";
        for (int i = 0; i < 5; i++) assertThrows(AuthFailure.class, () -> service.reset(email, recovery.challenge(), wrong, NEW_PASSWORD, NEW_PASSWORD));
        assertNotNull(service.login(username, PASSWORD, false));
        service.reset(email, recovery.challenge(), correct, NEW_PASSWORD, NEW_PASSWORD);
        assertNotNull(service.login(username, NEW_PASSWORD, false));
    }
    @Test void malformedCodeBlankPasswordAndMismatchDoNotUpdateAccount() {
        var recovery = service.sendCode(email);
        fails(() -> service.reset(email, recovery.challenge(), "１２３４５６", NEW_PASSWORD, NEW_PASSWORD), "6 chữ số");
        fails(() -> service.reset(email, recovery.challenge(), code, "   ", "   "), "Vui lòng nhập");
        fails(() -> service.reset(email, recovery.challenge(), code, NEW_PASSWORD, NEW_PASSWORD.trim()), "không khớp");
        assertNotNull(service.login(username, PASSWORD, false));
        service.reset(email, recovery.challenge(), code, "short", "short");
        assertNotNull(service.login(username, "short", false));
    }
    @Test void lockedAndRevokedAccountsCannotLoginOrRecover() {
        for (String status : List.of("LOCKED", "REVOKED")) {
            db.update("UPDATE gcmb.user_account SET status=? WHERE id=?", status, userId);
            assertEquals(403, assertThrows(AuthFailure.class, () -> service.login(username, PASSWORD, false)).status());
            assertEquals(403, assertThrows(AuthFailure.class, () -> service.sendCode(email)).status());
        }
        verifyNoInteractions(mailer);
    }
    @Test void wrongLoginsDoNotTemporarilyLockAnActiveAccount() {
        for (int i = 0; i < 5; i++) assertThrows(AuthFailure.class, () -> service.login(username, "wrong", false));
        assertNotNull(service.login(username, PASSWORD, false));
        assertEquals(0, db.queryForObject("SELECT failed_login_count FROM gcmb.user_account WHERE id=?", Integer.class, userId));
    }
    @Test void resendHasNoCooldownButReplacesThePreviousCode() {
        var first = service.sendCode(email);
        String oldCode = code;
        var second = service.sendCode(email);
        fails(() -> service.reset(email, first.challenge(), oldCode, NEW_PASSWORD, NEW_PASSWORD), "hết hiệu lực");
        service.reset(email, second.challenge(), code, NEW_PASSWORD, NEW_PASSWORD);
        assertEquals(2, db.queryForObject("SELECT count(*) FROM gcmb.auth_token WHERE user_id=? AND kind='PASSWORD_RESET' AND expires_at='infinity'", Integer.class, userId));
    }
    @Test void concurrentResetCanSucceedOnlyOnce() throws Exception {
        var recovery = service.sendCode(email);
        String currentCode = code;
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> reset = () -> {
                try { service.reset(email, recovery.challenge(), currentCode, NEW_PASSWORD, NEW_PASSWORD); return true; }
                catch (AuthFailure failure) { return false; }
            };
            var results = executor.invokeAll(List.of(reset, reset), 20, TimeUnit.SECONDS);
            int successes = 0;
            for (var result : results) if (result.get()) successes++;
            assertEquals(1, successes);
        } finally { executor.shutdownNow(); }
    }
    @Test void httpContractProtectsCsrfAndSessionCookieAndBackendRole() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content("{}")) .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        var csrfResponse = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty()).andExpect(cookie().httpOnly("XSRF-TOKEN", true)).andReturn().getResponse();
        var matcher = java.util.regex.Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"").matcher(csrfResponse.getContentAsString());
        assertTrue(matcher.find());
        String csrfToken = matcher.group(1);
        Cookie csrfCookie = csrfResponse.getCookie("XSRF-TOKEN");
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\",\"remember\":true}";
        var response = mvc.perform(post("/api/v1/auth/login").cookie(csrfCookie).header("X-XSRF-TOKEN", csrfToken).contentType("application/json").content(body))
            .andExpect(status().isOk()).andExpect(cookie().httpOnly("GCMB_SESSION", true)).andExpect(cookie().maxAge("GCMB_SESSION", Integer.MAX_VALUE))
            .andExpect(jsonPath("$.destination").value("/le-tan")).andExpect(jsonPath("$.token").doesNotExist()).andReturn().getResponse();
        Cookie cookie = response.getCookie("GCMB_SESSION");
        mvc.perform(get("/api/v1/auth/me").cookie(cookie)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/head-office/report").cookie(cookie)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/logout").cookie(cookie, csrfCookie).header("X-XSRF-TOKEN", csrfToken)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").cookie(cookie)).andExpect(status().isUnauthorized());
    }
    @Test void browserCsrfCookieAndHeaderRoundTripWorks() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var response = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
        var matcher = java.util.regex.Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"").matcher(response.getContentAsString());
        assertTrue(matcher.find());
        String token = matcher.group(1);
        Cookie cookie = response.getCookie("XSRF-TOKEN");
        mvc.perform(post("/api/v1/auth/forgot-password").cookie(cookie).header("X-XSRF-TOKEN", token)
            .contentType("application/json").content("{\"email\":\"" + email + "\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.challenge").isNotEmpty()).andExpect(jsonPath("$.code").doesNotExist());
        mvc.perform(post("/api/v1/auth/forgot-password").cookie(cookie).header("X-XSRF-TOKEN", "invalid")
            .contentType("application/json").content("{\"email\":\"" + email + "\"}"))
            .andExpect(status().isForbidden());
    }
    @Test void databaseFailureRollsBackPasswordAndTokenChangesTogether() {
        var session = service.login(username, PASSWORD, false);
        var recovery = service.sendCode(email);
        db.execute("CREATE FUNCTION gcmb.auth_test_fail_reset() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.used_at IS NOT NULL THEN RAISE EXCEPTION 'Injected test database failure'; END IF; RETURN NEW; END $$");
        db.execute("CREATE TRIGGER auth_test_fail_reset BEFORE UPDATE ON gcmb.auth_token FOR EACH ROW EXECUTE FUNCTION gcmb.auth_test_fail_reset()");
        try {
            assertThrows(org.springframework.dao.DataAccessException.class, () -> service.reset(email, recovery.challenge(), code, NEW_PASSWORD, NEW_PASSWORD));
            assertNotNull(service.authenticate(session.token()));
            assertNotNull(service.login(username, PASSWORD, false));
            assertNull(db.queryForObject("SELECT used_at FROM gcmb.auth_token WHERE token_hash=?", java.sql.Timestamp.class, AuthCrypto.hash(recovery.challenge())));
        } finally {
            db.execute("DROP TRIGGER auth_test_fail_reset ON gcmb.auth_token");
            db.execute("DROP FUNCTION gcmb.auth_test_fail_reset()");
        }
        service.reset(email, recovery.challenge(), code, NEW_PASSWORD, NEW_PASSWORD);
    }
}
