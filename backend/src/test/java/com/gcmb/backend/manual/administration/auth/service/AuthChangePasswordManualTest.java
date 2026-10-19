/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 14/10/2026
 * Description: Covers the authenticated password-change workflow and its side effects.
 */

package com.gcmb.backend.manual.administration.auth.service;

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
import static org.mockito.Mockito.clearInvocations;

public final class AuthChangePasswordManualTest {
    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    private AuthChangePasswordManualTest() {
    }

    public static ManualTestSuite suite() {
        ManualTestCase contract = new ManualTestCase("auth-change-password/contract",
                ManualTestCase.identifier("UTC045", "UTC-PAM-CHANGE-PASSWORD-003"),
                "AuthChangePasswordManualTest",
                "rejectsNewPasswordEqualToCurrentPassword",
                "ACTIVE account matches signed-in Identity.id()/username(); current password matches stored hash; AuthService.changePassword(Identity, String, String, String)",
                "currentPassword=newPassword=confirmation='Current1!'",
                "AuthFailure status=400, message='Mật khẩu mới phải khác mật khẩu hiện tại'; findByUsername('staff'), verify current and new password against stored hash; no changePassword, revokePendingResets, revokeTokens or mailer call",
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
                    // Observe only the business call; constructor dummy-hash setup is excluded.
                    clearInvocations(repository, crypto, mailer);
                    return ManualTestSupport.observeVoid(
                            () -> service.changePassword(identity, "Current1!", "Current1!", "Current1!"),
                            repository, crypto, mailer);
                });
        return new ManualTestSuite("Authentication", "AuthChangePasswordContract", List.of(contract));
    }
}
