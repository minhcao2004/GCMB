/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: Kiểm thử tạo tài khoản, băm mật khẩu, liên kết nhân viên và từ chối dữ liệu hoặc phạm vi truy cập không hợp lệ.
 */

package com.gcmb.backend.administration.accounts;

import com.gcmb.backend.administration.accounts.dto.AccountDtos.*;
import com.gcmb.backend.administration.accounts.repository.AccountRepository;
import com.gcmb.backend.administration.accounts.service.AccountService;
import com.gcmb.backend.administration.auth.exception.AuthFailure;
import com.gcmb.backend.administration.auth.security.AuthCrypto;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountServiceTest {
    final AccountRepository repository = mock(AccountRepository.class);
    final AuthCrypto crypto = mock(AuthCrypto.class);
    final AccountService service = new AccountService(repository, crypto);
    final UUID employee = UUID.randomUUID();
    final UUID branch = UUID.randomUUID();
    final UUID role = UUID.randomUUID();
    final UUID actor = UUID.randomUUID();
    final UUID id = UUID.randomUUID();

    CreateAccount input(String roleCode, UUID scope) {
        return new CreateAccount(employee, "staff.rec", "123", "Staff@example.com", roleCode, scope);
    }

    @BeforeEach
    void setup() {
        when(repository.lockAvailableEmployee(employee)).thenReturn(true);
        when(repository.roleId(anyString())).thenReturn(role);
        when(repository.activeBranch(branch)).thenReturn(true);
    }

    @Test
    void hashesPasswordNormalizesEmailAndLinksActor() {
        var request = input("RECEPTIONIST", branch);
        when(crypto.encode("123")).thenReturn("encoded-password");
        when(
            repository.insert(request, "staff.rec", "staff@example.com", "encoded-password", role, actor)
        ).thenReturn(id);
        service.create(request, actor);
        verify(repository).insert(request, "staff.rec", "staff@example.com", "encoded-password", role, actor);
        verify(repository).find(id);
    }

    @Test
    void rejectsForeignBranchAndChainScopeMismatch() {
        when(repository.activeBranch(branch)).thenReturn(false);
        assertThrows(AuthFailure.class, () -> service.create(input("RECEPTIONIST", branch), actor));
        assertThrows(AuthFailure.class, () -> service.create(input("HEAD_OFFICE_MANAGER", branch), actor));
        verifyNoInteractions(crypto);
    }

    @Test
    void rejectsDuplicateUsernameAndInvalidPassword() {
        when(repository.exists("username", "staff.rec")).thenReturn(true);
        assertEquals(
            409,
            assertThrows(
                AuthFailure.class,
                () -> service.create(
                    input("RECEPTIONIST", branch),
                    actor
                )
            ).status()
        );
        assertEquals(
            400,
            assertThrows(
                AuthFailure.class,
                () -> service.create(
                    new CreateAccount(employee, "staff.rec", "", "staff@example.com", "RECEPTIONIST", branch),
                    actor
                )
            ).status()
        );
        verifyNoInteractions(crypto);
    }

    @Test
    void rejectsAlreadyLinkedEmployee() {
        when(repository.lockAvailableEmployee(employee)).thenReturn(false);
        assertEquals(
            409,
            assertThrows(
                AuthFailure.class,
                () -> service.create(
                    input("RECEPTIONIST", branch),
                    actor
                )
            ).status()
        );
        verifyNoInteractions(crypto);
    }

    @Test
    void rejectsMissingEmployee() {
        assertEquals(
            400,
            assertThrows(
                AuthFailure.class,
                () -> service.create(
                    new CreateAccount(null, "staff.rec", "123", "staff@example.com", "RECEPTIONIST", branch),
                    actor
                )
            ).status()
        );
        verifyNoInteractions(repository, crypto);
    }

    @Test
    void rejectsUnboundedPagination() {
        assertThrows(
            AuthFailure.class,
            () -> service.list("", "", null, "", -1, 10)
        );
        assertThrows(
            AuthFailure.class,
            () -> service.list("", "", null, "", 0, 101)
        );
        verifyNoInteractions(repository);
    }
}
