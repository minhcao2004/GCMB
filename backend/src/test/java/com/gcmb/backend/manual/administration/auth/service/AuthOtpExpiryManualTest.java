/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 20/10/2026
 * Description: Supports AuthOtpExpiryManualTest test execution and verification.
 */

package com.gcmb.backend.manual.administration.auth.service;

import com.gcmb.backend.administration.auth.repository.AuthRepository;
import com.gcmb.backend.administration.auth.security.AuthCrypto;
import com.gcmb.backend.administration.auth.service.AuthService;
import com.gcmb.backend.administration.auth.service.RecoveryMailer;
import com.gcmb.backend.manual.ManualTestCase;
import com.gcmb.backend.manual.ManualTestResult;
import com.gcmb.backend.manual.ManualTestSuite;
import com.gcmb.backend.manual.ManualTestSupport;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** User-approved ten-minute OTP contract. JDBC rows are stubs, not SQL execution. */
public final class AuthOtpExpiryManualTest {
    private AuthOtpExpiryManualTest() {}

    public static ManualTestSuite suite() {
        return new ManualTestSuite("Authentication", "AuthOtpExpiry", List.of(new ManualTestCase("auth-otp/expired-ten-minutes",
                ManualTestCase.identifier("UTC133", "UC-02/user-confirmed OTP TTL 10 minutes"),
                "AuthOtpExpiryManualTest",
                "resetRejectsExpiredPersistedOtp",
                "ACTIVE account; persisted OTP row created 11 minutes ago, expires_at=created_at+10 minutes; unused and unrevoked; real AuthRepository/AuthMapper/AuthService, stubbed JDBC and crypto. SQL filtering is inspected, not executed.",
                "email=staff@example.com; challenge='A' repeated 43; code=038291; password=confirmation=Replacement1!; expires_at is already in the past",
                "Reject expired OTP with AuthFailure status=400; no password update, consumeReset, token revocation or CHANGE_PASSWORD audit. No SQL statement may allow an expired OTP to reach the password write.",
                AuthOtpExpiryManualTest::observe)));
    }

    @SuppressWarnings("unchecked")
    private static ManualTestResult observe() throws Exception {
        UUID user = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID resetId = UUID.fromString("30000000-0000-0000-0000-000000000003");
        Instant observedAt = Instant.now();
        Instant createdAt = observedAt.minusSeconds(660);
        Instant expiresAt = createdAt.plusSeconds(600);
        JdbcTemplate db = mock(JdbcTemplate.class);
        ResultSet account = mock(ResultSet.class);
        when(account.getObject("id", UUID.class)).thenReturn(user);
        when(account.getString("username")).thenReturn("staff");
        when(account.getString("email")).thenReturn("staff@example.com");
        when(account.getString("password_hash")).thenReturn("old-hash");
        when(account.getString("status")).thenReturn("ACTIVE");
        when(account.getString("role")).thenReturn("RECEPTIONIST");
        ResultSet reset = mock(ResultSet.class);
        when(reset.getObject("id", UUID.class)).thenReturn(resetId);
        when(reset.getString("code_hash")).thenReturn("otp-hash");
        when(reset.getTimestamp("created_at")).thenReturn(Timestamp.from(createdAt));
        when(reset.getTimestamp("expires_at")).thenReturn(Timestamp.from(expiresAt));
        when(db.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(call -> {
            String sql = call.getArgument(0);
            RowMapper<?> mapper = call.getArgument(1);
            return List.of(mapper.mapRow(sql.contains("gcmb.auth_token") ? reset : account, 0));
        });
        AuthCrypto crypto = mock(AuthCrypto.class);
        when(crypto.token()).thenReturn("D".repeat(43));
        when(crypto.encode(anyString())).thenReturn("encoded-test-value");
        when(crypto.matches("staff@example.com:038291", "otp-hash")).thenReturn(true);
        RecoveryMailer mailer = mock(RecoveryMailer.class);
        AuthService service = new AuthService(new AuthRepository(db), crypto, mailer);
        // Constructor dummy-hash setup is not part of the observed reset operation.
        clearInvocations(db, crypto, mailer, reset, account);
        ManualTestResult result = ManualTestSupport.observeVoid(
            () -> service.reset("staff@example.com", "A".repeat(43), "038291", "Replacement1!", "Replacement1!"),
            db, crypto, mailer, reset);
        return new ManualTestResult(result.actualResult(), result.exception(),
            "Fixture UTC: created_at=" + createdAt + "; expires_at=" + expiresAt + "; observed_at=" + observedAt
                + "; age=660 seconds; TTL=600 seconds. JDBC stub supplies an expired persisted row; SQL predicates are not executed.\n"
                + result.sideEffects());
    }
}
