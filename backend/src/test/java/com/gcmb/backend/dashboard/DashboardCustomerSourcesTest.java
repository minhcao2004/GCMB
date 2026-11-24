package com.gcmb.backend.dashboard;

import com.gcmb.backend.auth.dto.Identity;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class DashboardCustomerSourcesTest {
    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final Identity HEAD_OFFICE = new Identity(UUID.randomUUID(), "manager", "HEAD_OFFICE_MANAGER", "/", null, null);
    private static final Identity BRANCH_MANAGER = new Identity(UUID.randomUUID(), "branch", "BRANCH_MANAGER", "/", BRANCH_ID, "Hoa Bằng");
    private static final String ASSIGNED_BRANCH_QUERY =
        "SELECT EXISTS(SELECT 1 FROM gcmb.branch_manager_assignment WHERE user_id=? AND branch_id=?)";

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final DashboardController dashboard = new DashboardController(jdbcTemplate, null);

    @ParameterizedTest
    @CsvSource({
        "DAY, 2024-02-29, 2024-02-29, 2024-03-01",
        "MONTH, 2026-11, 2026-11-01, 2026-12-01",
        "YEAR, 2026, 2026-01-01, 2027-01-01"
    })
    void chainUsesTheSelectedPeriodWithoutABranchFilter(String period, String date, LocalDate from, LocalDate to) {
        List<Map<String, Object>> rows = List.of(Map.of("channel", "FACEBOOK", "count", 3L));
        when(jdbcTemplate.queryForList(anyString(), eq(from), eq(to), isNull(), isNull())).thenReturn(rows);

        assertEquals(Map.of("sources", rows), dashboard.chainCustomerSources(HEAD_OFFICE, period, date, null));
    }

    @Test
    void chainCanLimitTheChartToOneBranch() {
        dashboard.chainCustomerSources(HEAD_OFFICE, "DAY", "2026-11-24", BRANCH_ID);

        verify(jdbcTemplate).queryForList(anyString(), eq(LocalDate.of(2026, 11, 24)),
            eq(LocalDate.of(2026, 11, 25)), eq(BRANCH_ID), eq(BRANCH_ID));
    }

    @Test
    void branchUsesTheBranchAssignedToTheSignedInManager() {
        when(jdbcTemplate.queryForObject(ASSIGNED_BRANCH_QUERY, Boolean.class, BRANCH_MANAGER.id(), BRANCH_ID))
            .thenReturn(true);

        dashboard.branchCustomerSources(BRANCH_MANAGER, "MONTH", "2026-11");

        verify(jdbcTemplate).queryForList(anyString(), eq(LocalDate.of(2026, 11, 1)),
            eq(LocalDate.of(2026, 12, 1)), eq(BRANCH_ID), eq(BRANCH_ID));
    }

    @Test
    void revokedBranchAssignmentDoesNotReadBookingCounts() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> dashboard.branchCustomerSources(BRANCH_MANAGER, "MONTH", "2026-11"));

        assertEquals(403, error.getStatusCode().value());
        verify(jdbcTemplate).queryForObject(ASSIGNED_BRANCH_QUERY, Boolean.class, BRANCH_MANAGER.id(), BRANCH_ID);
        verifyNoMoreInteractions(jdbcTemplate);
    }

    @Test
    void branchManagerCannotUseTheChainEndpoint() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> dashboard.chainCustomerSources(BRANCH_MANAGER, "MONTH", "2026-11", null));

        assertEquals(403, error.getStatusCode().value());
        verifyNoInteractions(jdbcTemplate);
    }

    @ParameterizedTest
    @CsvSource({"DAY, 2026-02-29", "MONTH, 2026-13", "YEAR, 0000", "RANGE, 2026"})
    void invalidPeriodIsRejectedBeforeQueryingBookings(String period, String date) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> dashboard.chainCustomerSources(HEAD_OFFICE, period, date, null));

        assertEquals(400, error.getStatusCode().value());
        verifyNoInteractions(jdbcTemplate);
    }
}
