package com.gcmb.backend.dashboard;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.dashboard.repository.FinancialReportRepository;
import com.gcmb.backend.finance.service.FinanceAccess;
import com.gcmb.backend.finance.service.FinancialPeriodService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountantCashFlowServiceTest {
    private final UUID branch = UUID.randomUUID();
    private final UUID fallbackBranch = UUID.randomUUID();
    private final Identity accountant = new Identity(UUID.randomUUID(), "accountant", "ACCOUNTANT", "/ke-toan", null, null);
    private final FinancialReportRepository repository = mock(FinancialReportRepository.class);
    private final FinanceAccess access = mock(FinanceAccess.class);
    private final AccountantCashFlowService service = new AccountantCashFlowService(repository, access);

    @Test
    void comparesFullMonthsUsingCanonicalIncomeAndInvoiceFallbackInBothPeriods() {
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 10, 31);
        LocalDate priorStart = LocalDate.of(2026, 9, 1);
        LocalDate priorEnd = LocalDate.of(2026, 9, 30);
        when(repository.cashFlowRows(timestamp(from), timestamp(to.plusDays(1)), null))
            .thenReturn(List.of(entry(branch, "PAYMENT", 1200), entry(branch, "SALARY", -400)));
        when(repository.cashFlowRows(timestamp(priorStart), timestamp(from), null))
            .thenReturn(List.of(entry(branch, "PAYMENT", 900), entry(branch, "EXPENSE", -500)));
        when(repository.sourceImportRows(from, to, null, "ALL")).thenReturn(List.of(
            source(branch, from, "KIOT_INVOICE", 500), source(fallbackBranch, from, "KIOT_INVOICE", 300),
            source(fallbackBranch, from, "BOOKING", 800)));
        when(repository.sourceImportRows(priorStart, priorEnd, null, "ALL")).thenReturn(List.of(
            source(fallbackBranch, priorStart, "BOOKING", 200)));

        var result = service.overview(accountant, from, to, null, "previous-month");

        assertEquals("2026-09-01", result.get("comparisonFrom"));
        assertEquals("2026-09-30", result.get("comparisonTo"));
        assertEquals(totals(1500, 400), result.get("current"));
        assertEquals(totals(1100, 500), result.get("previous"));
        verify(access).accountant(accountant);
    }

    @Test
    void preservesBranchScopeAndUsesVietnamMidnightForCalendarComparisons() {
        LocalDate day = LocalDate.of(2026, 11, 1);
        service.overview(accountant, day, day, branch, "previous-day");
        verify(repository).cashFlowRows(timestamp(day.minusDays(1)), timestamp(day), branch);
        verify(repository).cashFlowRows(timestamp(day), timestamp(day.plusDays(1)), branch);
        verify(repository).sourceImportRows(day.minusDays(1), day.minusDays(1), branch, "ALL");

        LocalDate yearStart = LocalDate.of(2025, 1, 1);
        var year = service.overview(accountant, yearStart, LocalDate.of(2025, 12, 31), branch, "previous-year");
        assertEquals("2024-01-01", year.get("comparisonFrom"));
        assertEquals("2024-12-31", year.get("comparisonTo"));
        verify(repository).cashFlowRows(timestamp(yearStart.minusYears(1)), timestamp(yearStart), branch);
    }

    private static Timestamp timestamp(LocalDate date) {
        return Timestamp.from(date.atStartOfDay(FinancialPeriodService.ZONE).toInstant());
    }

    private static Map<String, Object> entry(UUID branchId, String kind, long amount) {
        return Map.of("branchId", branchId, "kind", kind, "amount", BigDecimal.valueOf(amount));
    }

    private static Map<String, Object> source(UUID branchId, LocalDate date, String type, long amount) {
        return Map.of("id", UUID.randomUUID(), "branchId", branchId, "recordType", type,
            "businessDate", date, "paidAmount", BigDecimal.valueOf(amount));
    }

    private static AccountantCashFlowService.CashTotals totals(long income, long expense) {
        return new AccountantCashFlowService.CashTotals(BigDecimal.valueOf(income), BigDecimal.valueOf(expense),
            BigDecimal.valueOf(income - expense));
    }
}
