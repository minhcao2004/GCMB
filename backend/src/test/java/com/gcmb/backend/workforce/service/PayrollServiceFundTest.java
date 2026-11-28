package com.gcmb.backend.workforce.service;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.exception.AuthFailure;
import com.gcmb.backend.finance.service.FinanceAccess;
import com.gcmb.backend.finance.service.FinancialPeriodService;
import com.gcmb.backend.finance.service.OperatingPolicyService;
import com.gcmb.backend.workforce.repository.PayrollRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayrollServiceFundTest {
    private final PayrollRepository repository = mock(PayrollRepository.class);
    private final FinanceAccess access = mock(FinanceAccess.class);
    private final PayrollService service = new PayrollService(repository, access, new ObjectMapper(),
        mock(FinancialPeriodService.class), mock(OperatingPolicyService.class));
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

    @Test
    void postsTwentyEmployeesAsOneBatchAndUsesActualPaymentDate() {
        var selection = batchSelection(20);
        var batch = paymentBatch(selection);
        when(repository.accountantFundBalance()).thenReturn(amount.multiply(BigDecimal.valueOf(20)));

        assertEquals(Map.of("batchId", batch.requestId(), "count", 20), service.payBatch(actor, "2026-11", batch));

        verify(repository).insertPaymentBatch(batch.requestId(), periodId, selection.branchId(), batch.paymentDate(),
            "MB", "BANK-BATCH-01", actor.id());
        verify(repository, times(20)).insertSalaryPayment(any(), any(), eq(amount), eq("PAID"),
            eq(Timestamp.from(batch.paymentDate().atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant())),
            eq("BANK-BATCH-01"), isNull(), eq(actor.id()), any(), eq(batch.requestId()));
        verify(repository, times(20)).insertPayrollOutflow(any(), eq(financialPeriodId), eq(batch.paymentDate()),
            eq(false), any(), eq(actor.id()), eq(fundId), eq(selection.branchId()), eq(amount));
        verify(repository).accountantFundBalance();
        verify(repository).incrementPeriodVersion(periodId);
    }

    @Test
    void rejectsEntireBatchWhenFundOnlyCoversSomeEmployees() {
        var batch = paymentBatch(batchSelection(3));
        assertThrows(AuthFailure.class, () -> service.payBatch(actor, "2026-11", batch));
        verify(repository, never()).insertPaymentBatch(any(), any(), any(), any(), any(), any(), any());
        verify(repository, never()).insertPayrollOutflow(any(), any(), any(), eq(false), any(), any(), any(), any(), any());
    }

    @Test
    void exactRetryReturnsExistingBatchWithoutPostingAgain() {
        var selection = batchSelection(2);
        var batch = paymentBatch(selection);
        when(repository.findPaymentBatch(batch.requestId())).thenReturn(List.of(Map.of(
            "payroll_period_id", periodId, "branch_id", selection.branchId(), "payment_date", batch.paymentDate(),
            "bank_name", batch.bankName(), "external_reference", batch.reference(), "created_by", actor.id())));
        when(repository.findBatchEmployees(batch.requestId())).thenReturn(selection.employeeIds());

        assertEquals(batch.requestId(), service.payBatch(actor, "2026-11", batch).get("batchId"));
        verify(repository, never()).accountantFundBalance();
        verify(repository, never()).incrementPeriodVersion(any());
    }

    @Test
    void rejectsBatchWhenASelectedEmployeeDoesNotBelongToItsBranch() {
        var selection = batchSelection(2);
        when(repository.findBatchEntries(periodId, selection.branchId(), selection.employeeIds())).thenReturn(List.of());

        assertThrows(AuthFailure.class, () -> service.payBatch(actor, "2026-11", paymentBatch(selection)));
        verify(repository, never()).insertPaymentBatch(any(), any(), any(), any(), any(), any(), any());
    }

    private PayrollService.BatchSelection batchSelection(int size) {
        var employeeIds = IntStream.range(0, size).mapToObj(index -> UUID.randomUUID()).toList();
        var selection = new PayrollService.BatchSelection(1, UUID.randomUUID(), employeeIds);
        when(repository.findBatchEntries(periodId, selection.branchId(), employeeIds)).thenReturn(employeeIds.stream()
            .map(id -> Map.<String, Object>of("id", UUID.randomUUID(), "employee_id", id, "net_amount", amount,
                "name", "Nhân viên", "bank_name_snapshot", "MB", "bank_account_snapshot", "001002003",
                "bank_holder_snapshot", "NHAN VIEN")).toList());
        return selection;
    }

    private PayrollService.PaymentBatch paymentBatch(PayrollService.BatchSelection selection) {
        return new PayrollService.PaymentBatch(UUID.randomUUID(), selection, LocalDate.of(2026, 10, 31),
            "MB", "BANK-BATCH-01");
    }
}
