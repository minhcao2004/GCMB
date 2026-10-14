package com.gcmb.backend.manual.auth;

import com.gcmb.backend.administration.auth.repository.AuthRepository;
import com.gcmb.backend.administration.auth.security.AuthCrypto;
import com.gcmb.backend.administration.auth.service.AuthService;
import com.gcmb.backend.administration.auth.service.RecoveryMailer;
import com.gcmb.backend.manual.ManualTestCase;
import com.gcmb.backend.manual.ManualTestResult;
import com.gcmb.backend.manual.ManualTestSuite;
import com.gcmb.backend.manual.ManualTestSupport;
import java.lang.reflect.Method;
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
                "AuthService should expose changePassword(UUID, currentPassword, newPassword, confirmation)",
                "currentPassword=newPassword=confirmation='Current1!'",
                "Method exists and throws AuthFailure status=400 without changing password or revoking tokens",
                () -> {
                    AuthRepository repository = mock(AuthRepository.class);
                    AuthCrypto crypto = mock(AuthCrypto.class);
                    RecoveryMailer mailer = mock(RecoveryMailer.class);
                    when(crypto.token()).thenReturn("D".repeat(43));
                    when(crypto.encode("D".repeat(43))).thenReturn("{pbkdf2-gcmb}dummy");
                    AuthService service = new AuthService(repository, crypto, mailer);
                    try {
                        Method method = AuthService.class.getDeclaredMethod(
                                "changePassword", UUID.class, String.class, String.class, String.class);
                        ManualTestResult observation = ManualTestSupport.observeVoid(
                                () -> method.invoke(service, USER_ID, "Current1!", "Current1!", "Current1!"),
                                repository, crypto, mailer);
                        return observation;
                    } catch (NoSuchMethodException missing) {
                        return new ManualTestResult(
                                "Required method was not found by reflection",
                                missing.getClass().getName() + ": " + missing.getMessage(),
                                ManualTestSupport.renderInvocations(repository, crypto, mailer));
                    }
                });
        return new ManualTestSuite("Authentication", "AuthChangePasswordContract", List.of(contract));
    }
}
