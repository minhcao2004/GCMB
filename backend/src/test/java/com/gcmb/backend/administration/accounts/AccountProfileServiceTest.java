/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: Kiểm thử xác nhận email, dữ liệu cá nhân và tài khoản chưa liên kết nhân viên.
 */
package com.gcmb.backend.administration.accounts;

import com.gcmb.backend.administration.accounts.dto.AccountDtos.AccountView;
import com.gcmb.backend.administration.accounts.dto.AccountProfileDtos.*;
import com.gcmb.backend.administration.accounts.repository.AccountProfileRepository;
import com.gcmb.backend.administration.accounts.service.AccountProfileService;
import com.gcmb.backend.administration.auth.exception.AuthFailure;
import com.gcmb.backend.administration.auth.security.AuthCrypto;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountProfileServiceTest {
    private final AccountProfileRepository repository = mock(AccountProfileRepository.class);
    private final AuthCrypto crypto = mock(AuthCrypto.class);
    private final AccountProfileService service = new AccountProfileService(repository, crypto);
    private final UUID actor = UUID.randomUUID();
    private Profile before;

    @BeforeEach
    void setup() {
        before = profile("ACTIVE", true, "old@example.com");
        when(repository.find(actor)).thenReturn(before);
        when(repository.lockActiveAccount(actor)).thenReturn("hash");
        when(crypto.matches("123", "hash")).thenReturn(true);
    }

    private Profile profile(String status, boolean linked, String email) {
        return new Profile(new AccountView(actor, "Hà", linked ? "NV-01" : null,
                "ha.rec", email, "RECEPTIONIST", null, "Chưa phân công", status),
                linked ? "Hà" : null, null, linked);
    }

    @Test
    void normalizesAndConfirmsEmailBeforeSavingAndAuditing() {
        Profile after = profile("ACTIVE", true, "new@example.com");
        when(repository.find(actor)).thenReturn(before, after);
        assertEquals(after, service.update(actor, new UpdateProfile(" Hà ", "", " NEW@example.com ", "123")));
        verify(repository).update(actor, "Hà", null, "new@example.com", true);
        verify(repository).audit(before, after);
    }

    @Test
    void rejectsWrongPasswordAndDuplicateEmail() {
        assertEquals(400, assertThrows(AuthFailure.class, () -> service.update(actor,
                new UpdateProfile("Hà", "", "new@example.com", "wrong"))).status());
        when(repository.emailTaken(actor, "new@example.com")).thenReturn(true);
        assertEquals(409, assertThrows(AuthFailure.class, () -> service.update(actor,
                new UpdateProfile("Hà", "", "new@example.com", "123"))).status());
        verify(repository, never()).update(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void rejectsInvalidNamePhoneAndInactiveAccount() {
        assertThrows(AuthFailure.class, () -> service.update(actor,
                new UpdateProfile(" ", "", "old@example.com", null)));
        assertThrows(AuthFailure.class, () -> service.update(actor,
                new UpdateProfile("Hà", "abcdefg", "old@example.com", null)));
        when(repository.lockActiveAccount(actor)).thenReturn(null);
        assertEquals(403, assertThrows(AuthFailure.class, () -> service.update(actor,
                new UpdateProfile("Hà", "", "old@example.com", null))).status());
        when(repository.find(actor)).thenReturn(profile("REVOKED", true, "old@example.com"));
        assertEquals(403, assertThrows(AuthFailure.class, () -> service.view(actor)).status());
    }

    @Test
    void unchangedProfileDoesNotAuditOrRequirePassword() {
        assertEquals(before, service.update(actor, new UpdateProfile("Hà", "", "old@example.com", null)));
        verifyNoInteractions(crypto);
        verify(repository, never()).audit(any(), any());
        verify(repository, never()).update(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void unlinkedAccountCanOnlyUpdateEmail() {
        when(repository.find(actor)).thenReturn(profile("ACTIVE", false, "old@example.com"));
        assertEquals(409, assertThrows(AuthFailure.class, () -> service.update(actor,
                new UpdateProfile("Fake employee", null, "old@example.com", null))).status());
        service.update(actor, new UpdateProfile(null, null, "new@example.com", "123"));
        verify(repository).update(actor, null, null, "new@example.com", true);
    }

    @Test
    void concurrentDuplicateReturnsConflictWithoutAudit() {
        doThrow(new DuplicateKeyException("duplicate")).when(repository)
                .update(actor, "Hà", null, "new@example.com", true);
        assertEquals(409, assertThrows(AuthFailure.class, () -> service.update(actor,
                new UpdateProfile("Hà", null, "new@example.com", "123"))).status());
        verify(repository, never()).audit(any(), any());
    }
}
