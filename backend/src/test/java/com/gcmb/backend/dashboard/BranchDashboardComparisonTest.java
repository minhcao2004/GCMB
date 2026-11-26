package com.gcmb.backend.dashboard;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.dashboard.repository.DashboardRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BranchDashboardComparisonTest {
    @ParameterizedTest
    @CsvSource({
        "DAY, 2024-03-01, 2024-03-01, 2024-02-29, 2024-02-29",
        "MONTH, 2024-03-01, 2024-03-31, 2024-02-01, 2024-02-29",
        "MONTH, 2024-03-01, 2024-03-10, 2024-02-01, 2024-02-10",
        "YEAR, 2024-01-01, 2024-12-31, 2023-01-01, 2023-12-31",
        "YEAR, 2024-01-01, 2024-02-29, 2023-01-01, 2023-02-28",
        "RANGE, 2024-03-01, 2024-03-03, 2024-02-27, 2024-02-29"
    })
    void returnsComparableTotalsForTheAssignedBranch(String period, String from, String to,
                                                      String previousFrom, String previousTo) {
        var dashboard = mock(DashboardRepository.class);
        var performance = mock(MonthlyPerformanceService.class);
        var controller = new DashboardController(dashboard, performance);
        var branchId = UUID.randomUUID();
        var actor = new Identity(UUID.randomUUID(), "manager", "BRANCH_MANAGER", "", branchId, "Cơ sở A");
        var previousStart = LocalDate.parse(previousFrom);
        var previousEndExclusive = LocalDate.parse(previousTo).plusDays(1);
        when(dashboard.isBranchManagerOf(actor.id(), branchId)).thenReturn(true);
        when(dashboard.findBranchTimezone(branchId)).thenReturn("Asia/Ho_Chi_Minh");
        when(performance.report(actor, LocalDate.of(2024, 6, 1), branchId))
            .thenReturn(Map.of("results", List.of(Map.of("id", branchId))));
        when(performance.dailyRevenue(previousStart, previousEndExclusive, branchId)).thenReturn(List.of(
            Map.of("revenue", 1000, "billCount", 10, "fnbRevenue", 500)));

        var result = controller.branch(actor, "2024-06", period, from, to, null, null, null, null);

        assertEquals(Map.of("from", previousFrom, "to", previousTo, "summary",
            Map.of("revenue", BigDecimal.valueOf(1000), "billCount", 10L, "fnbRevenue", BigDecimal.valueOf(500))),
            result.get("reportComparison"));
        verify(performance).dailyRevenue(previousStart, previousEndExclusive, branchId);
    }
}
