package com.gcmb.backend.dashboard;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.dashboard.repository.MonthlyPerformanceRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonthlyPerformanceServiceTest {
  @ParameterizedTest
  @CsvSource({
    "previous-day, 2024-03-01, 2024-03-01, 2024-02-29, 2024-02-29",
    "previous-month, 2024-01-01, 2024-01-15, 2023-12-01, 2023-12-15",
    "previous-month, 2024-03-01, 2024-03-31, 2024-02-01, 2024-02-29",
    "previous-month, 2024-02-01, 2024-02-29, 2024-01-01, 2024-01-31",
    "previous-month, 2024-03-30, 2024-03-31, 2024-02-29, 2024-02-29",
    "previous-year, 2024-01-01, 2024-02-29, 2023-01-01, 2023-02-28",
    "previous-year, 2024-01-01, 2024-12-31, 2023-01-01, 2023-12-31",
    "prev, 2024-03-01, 2024-03-31, 2024-01-30, 2024-02-29",
    "yoy, 2024-01-01, 2024-02-29, 2023-01-01, 2023-02-28"
  })
  void comparesWithTheRequestedPeriodForTheSameBranch(String mode, String from, String to,
                                                      String previousFrom, String previousTo) {
    var repository = mock(MonthlyPerformanceRepository.class);
    var service = new MonthlyPerformanceService(repository);
    var manager = new Identity(UUID.randomUUID(), "manager", "HEAD_OFFICE_MANAGER", "", null, null);
    var branches = List.of(UUID.randomUUID());
    when(repository.findAllBranchIds()).thenReturn(branches);

    var result = service.overview(manager, LocalDate.parse(from), LocalDate.parse(to), branches, mode, "day");

    assertEquals(previousFrom, result.get("comparisonFrom"));
    assertEquals(previousTo, result.get("comparisonTo"));
    verify(repository).overviewBranchRows(LocalDate.parse(from), LocalDate.parse(to), branches);
    verify(repository).overviewBranchRows(LocalDate.parse(previousFrom), LocalDate.parse(previousTo), branches);
  }
}
