package com.gcmb.backend.workforce.service;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.exception.AuthFailure;
import com.gcmb.backend.finance.service.FinanceAccess;
import com.gcmb.backend.finance.service.FinancialPeriodService;
import com.gcmb.backend.workforce.repository.PayrollRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayrollServiceFundTest {
    private final PayrollRepository repository = mock(PayrollRepository.class);
    private final FinanceAccess access = mock(FinanceAccess.class);
    private final PayrollService service = new PayrollService(repository, access, new ObjectMapper(),
        mock(FinancialPeriodService.class));
    private final Identity actor = new Identity(UUID.randomUUID(), "accountant", "ACCOUNTANT", "/ke-toan", null, null);
    private final UUID employeeId = UUID.randomUUID();
    private final UUID fundId = UUID.randomUUID();
    private final UUID periodId = UUID.randomUUID();
    private final UUID financialPeriodId = UUID.randomUUID();
    private final BigDecimal amount = BigDecimal.valueOf(490000);

    @BeforeEach
    void preparePayroll() {
        var period = new HashMap<String, Object>();
        period.put("id", periodId);
        period.put("version", 1L);
        period.put("status", "CONFIRMED");
        when(repository.findPayrollPeriod(LocalDate.of(2026, 11, 1))).thenReturn(List.of(period));
        when(repository.findEntry(periodId, employeeId)).thenReturn(List.of(Map.of(
            "id", UUID.randomUUID(), "net_amount", amount)));
        when(repository.findEmployeeName(employeeId)).thenReturn("Nhân viên");
        when(repository.lockActiveCentralFund(null)).thenReturn(List.of(Map.of("id", fundId, "name", "Quỹ kế toán")));
        when(repository.accountantFundBalance()).thenReturn(BigDecimal.valueOf(1000000));
        when(repository.shareFinancialPeriod(any())).thenReturn(Map.of("id", financialPeriodId, "status", "OPEN"));
    }

    private PayrollService.Payments command(UUID accountId, String result) {
        return new PayrollService.Payments(1, List.of(new PayrollService.Pay(
            employeeId, accountId, null, result, "PAYROLL-REFERENCE", "payroll-request-001")));
    }

    @Test
    void paysFromResolvedFundWhenRequestOmitsAccount() {
        service.pay(actor, "2026-11", command(null, "PAID"));

        verify(access).lock();
        verify(repository).lockActiveCentralFund(null);
        verify(repository).insertPayrollOutflow(any(), eq(financialPeriodId), any(), eq(false), any(),
            eq(actor.id()), eq(fundId), isNull(), eq(amount));
    }

    @Test
    void preservesExplicitFundForExistingCallers() {
        when(repository.lockActiveCentralFund(fundId)).thenReturn(List.of(Map.of("id", fundId, "name", "Quỹ kế toán")));

        service.pay(actor, "2026-11", command(fundId, "PAID"));

        verify(repository).lockActiveCentralFund(fundId);
        verify(repository).insertPayrollOutflow(any(), eq(financialPeriodId), any(), eq(false), any(),
            eq(actor.id()), eq(fundId), isNull(), eq(amount));
    }

    @Test
    void rejectsSuccessfulPaymentWhenSharedFundIsInsufficient() {
        when(repository.accountantFundBalance()).thenReturn(BigDecimal.ZERO);

        AuthFailure failure = assertThrows(AuthFailure.class,
            () -> service.pay(actor, "2026-11", command(null, "PAID")));

        assertTrue(failure.getMessage().contains("không đủ"));
        verify(repository, never()).insertPayrollOutflow(any(), any(), any(), eq(false), any(), any(), any(), any(), any());
    }

    @Test
    void recordsFailedAttemptWithoutTakingMoneyFromFund() {
        service.pay(actor, "2026-11", command(null, "FAILED"));

        verify(repository, never()).lockActiveCentralFund(any());
        verify(repository, never()).insertPayrollOutflow(any(), any(), any(), eq(false), any(), any(), any(), any(), any());
    }

    @Test
    void reportsMissingFundConfiguration() {
        when(repository.lockActiveCentralFund(null)).thenReturn(List.of());

        AuthFailure failure = assertThrows(AuthFailure.class,
            () -> service.pay(actor, "2026-11", command(null, "PAID")));

        assertTrue(failure.getMessage().contains("Chưa có quỹ kế toán"));
    }
}
