package com.gcmb.backend.manual.auth;

import com.gcmb.backend.administration.auth.repository.AuthRepository;
import com.gcmb.backend.administration.auth.dto.response.Identity;
import com.gcmb.backend.administration.auth.entity.Account;
import com.gcmb.backend.administration.auth.security.AuthCrypto;
import com.gcmb.backend.administration.auth.service.AuthService;
import com.gcmb.backend.administration.auth.service.RecoveryMailer;
import com.gcmb.backend.manual.ManualTestCase;
import com.gcmb.backend.manual.ManualTestSuite;
import com.gcmb.backend.manual.ManualTestSupport;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class AuthChangePasswordManualTest {
    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    private AuthChangePasswordManualTest() {
    }

    public static ManualTestSuite suite() {
        ManualTestCase contract = new ManualTestCase(
                "auth-change-password/contract", "UTC045", "UTC-PAM-CHANGE-PASSWORD-003",
                "AuthChangePasswordContractTest", "rejectsNewPasswordEqualToCurrentPassword", "Contract",
                "Personal Account Management!A59",
                "Active account linked to Identity; current password matches stored hash",
                "currentPassword=newPassword=confirmation='Current1!'",
                "AuthFailure status=400 on password; no password/session/reset write",
                () -> {
                    AuthRepository repository = mock(AuthRepository.class);
                    AuthCrypto crypto = mock(AuthCrypto.class);
                    RecoveryMailer mailer = mock(RecoveryMailer.class);
                    when(crypto.token()).thenReturn("D".repeat(43));
                    when(crypto.encode("D".repeat(43))).thenReturn("{pbkdf2-gcmb}dummy");
                    AuthService service = new AuthService(repository, crypto, mailer);
                    Identity identity = new Identity(USER_ID, "staff", "RECEPTIONIST", "/le-tan", null, null);
                    when(repository.findByUsername("staff")).thenReturn(new Account(USER_ID, "staff",
                            "staff@example.com", "stored-hash", "ACTIVE", "RECEPTIONIST", null, null));
                    when(crypto.matches("Current1!", "stored-hash")).thenReturn(true);
                    return ManualTestSupport.observeVoid(
                            () -> service.changePassword(identity, "Current1!", "Current1!", "Current1!"),
                            repository, crypto, mailer);
                });
        return new ManualTestSuite("Authentication", "AuthChangePasswordContract", List.of(contract));
    }
}
