/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 13/10/2026
 * Description: Kiểm thử đơn vị lọc, phân trang và truy xuất nhật ký hệ thống chỉ đọc theo UC-08.
 */

package com.gcmb.backend.administration.audit.service;

import com.gcmb.backend.administration.audit.dto.AuditDtos.AuditDetail;
import com.gcmb.backend.administration.audit.dto.AuditDtos.AuditEntry;
import com.gcmb.backend.administration.audit.dto.AuditDtos.AuditPage;
import com.gcmb.backend.administration.audit.dto.AuditDtos.Snapshot;
import com.gcmb.backend.administration.audit.repository.AuditRepository;
import com.gcmb.backend.administration.auth.exception.AuthFailure;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {
    @Mock
    private AuditRepository repository;

    private AuditService service;

    @BeforeEach
    void setUp() {
        service = new AuditService(repository);
    }

    @Test
    void listTrimsSearchTextAndDelegatesEveryFilterWithoutChangingResult() {
        UUID branchId = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 10, 13);
        AuditPage expected = new AuditPage(List.of(entry()), 1, 2, 25);
        when(repository.list("manager", "SET_ACCOUNT_ROLE", branchId, from, to, 2, 25))
                .thenReturn(expected);

        AuditPage actual = service.list(
                "  manager  ", "SET_ACCOUNT_ROLE", branchId, from, to, 2, 25);

        assertSame(expected, actual);
        verify(repository).list("manager", "SET_ACCOUNT_ROLE", branchId, from, to, 2, 25);
    }

    @Test
    void listAcceptsDocumentedTechnicalBoundaries() {
        String q = "q".repeat(200);
        String action = "A".repeat(60);
        LocalDate to = LocalDate.of(9999, 1, 1);
        AuditPage expected = new AuditPage(List.of(), 0, 0, 100);
        when(repository.list(q, action, null, null, to, 0, 100)).thenReturn(expected);

        assertSame(expected, service.list(q, action, null, null, to, 0, 100));

        verify(repository).list(q, action, null, null, to, 0, 100);
    }

    static Stream<Arguments> invalidFilters() {
        return Stream.of(
                Arguments.of("negative page", "", "", -1, 10),
                Arguments.of("zero size", "", "", 0, 0),
                Arguments.of("size above maximum", "", "", 0, 101),
                Arguments.of("search text above maximum", "q".repeat(201), "", 0, 10),
                Arguments.of("action above maximum", "", "A".repeat(61), 0, 10)
        );
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("invalidFilters")
    void listRejectsInvalidPaginationOrTextLengthBeforeRepositoryCall(
            String caseName,
            String q,
            String action,
            int page,
            int size) {
        AuthFailure failure = assertThrows(
                AuthFailure.class,
                () -> service.list(q, action, null, null, null, page, size));

        assertEquals(400, failure.status(), caseName);
        assertEquals("Bộ lọc hoặc phân trang không hợp lệ", failure.getMessage());
        verifyNoInteractions(repository);
    }

    @Test
    void listRejectsDateRangeWhenStartIsAfterEnd() {
        AuthFailure failure = assertThrows(
                AuthFailure.class,
                () -> service.list(
                        "", "", null,
                        LocalDate.of(2026, 10, 14),
                        LocalDate.of(2026, 10, 13),
                        0, 10));

        assertEquals(400, failure.status());
        assertEquals("Ngày bắt đầu không được sau ngày kết thúc", failure.getMessage());
        verifyNoInteractions(repository);
    }

    @Test
    void listRejectsEndDateOutsideSupportedDatabaseYear() {
        AuthFailure failure = assertThrows(
                AuthFailure.class,
                () -> service.list(
                        "", "", null, null,
                        LocalDate.of(10_000, 1, 1),
                        0, 10));

        assertEquals(400, failure.status());
        assertEquals("Ngày kết thúc không hợp lệ", failure.getMessage());
        verifyNoInteractions(repository);
    }

    @Test
    void actionsReturnsRepositoryValuesWithoutModification() {
        List<String> expected = List.of("DEACTIVATE_USER_ACCOUNT", "SET_ACCOUNT_ROLE");
        when(repository.actions()).thenReturn(expected);

        List<String> actual = service.actions();

        assertSame(expected, actual);
        verify(repository).actions();
    }

    @Test
    void detailReturnsExistingReadOnlyProjection() {
        UUID id = UUID.randomUUID();
        AuditDetail expected = detail();
        when(repository.detail(id)).thenReturn(expected);

        AuditDetail actual = service.detail(id);

        assertSame(expected, actual);
        verify(repository).detail(id);
    }

    @Test
    void detailReturnsNotFoundWhenAuditEntryDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(repository.detail(id)).thenReturn(null);

        AuthFailure failure = assertThrows(AuthFailure.class, () -> service.detail(id));

        assertEquals(404, failure.status());
        assertEquals("Không tìm thấy nhật ký", failure.getMessage());
        verify(repository).detail(id);
        verify(repository, never()).actions();
    }

    private static AuditEntry entry() {
        return new AuditEntry(
                UUID.randomUUID(),
                Instant.parse("2026-10-13T00:00:00Z"),
                "Head Office Manager",
                "manager",
                "SET_ACCOUNT_ROLE",
                "USER_ACCOUNT",
                UUID.randomUUID(),
                "staff",
                "Toàn chuỗi",
                null);
    }

    private static AuditDetail detail() {
        Snapshot before = new Snapshot(
                "staff", "RECEPTIONIST", "Hồ Tây", "ACTIVE",
                "Nhân viên", "staff@example.com", "0900000000");
        Snapshot after = new Snapshot(
                "staff", "ACCOUNTANT", "Toàn chuỗi", "ACTIVE",
                "Nhân viên", "staff@example.com", "0900000000");
        return new AuditDetail(entry(), before, after);
    }
}
