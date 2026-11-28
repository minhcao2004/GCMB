package com.gcmb.backend.dashboard;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.dashboard.repository.FinancialReportRepository;
import com.gcmb.backend.finance.service.FinanceAccess;
import com.gcmb.backend.finance.service.FinancialPeriodService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountantCashFlowService {
    private final FinancialReportRepository repository;
    private final FinanceAccess financeAccess;

    public AccountantCashFlowService(FinancialReportRepository repository, FinanceAccess financeAccess) {
        this.repository = repository;
        this.financeAccess = financeAccess;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> overview(Identity actor, LocalDate from, LocalDate to, UUID branchId, String compare) {
        financeAccess.accountant(actor);
        if (from.isAfter(to) || to.isAfter(LocalDate.now(FinancialPeriodService.ZONE))
                || ChronoUnit.DAYS.between(from, to) >= 366) {
            throw FinanceAccess.bad("Khoảng ngày không hợp lệ. Chọn tối đa 366 ngày đến hôm nay.");
        }
        if (!List.of("previous-day", "previous-month", "previous-year").contains(compare)) {
            throw FinanceAccess.bad("Kỳ so sánh không hợp lệ.");
        }
        var prior = MonthlyPerformanceService.comparisonRange(from, to, compare);
        var currentRows = rows(from, to, branchId);
        return Map.of("rows", currentRows, "current", totals(currentRows),
            "previous", totals(rows(prior.from(), prior.to(), branchId)),
            "comparisonFrom", prior.from().toString(), "comparisonTo", prior.to().toString(),
            "branches", repository.findBranchOptions());
    }

    private List<Map<String, Object>> rows(LocalDate from, LocalDate to, UUID branchId) {
        var zone = FinancialPeriodService.ZONE;
        var rows = new ArrayList<>(repository.cashFlowRows(Timestamp.from(from.atStartOfDay(zone).toInstant()),
            Timestamp.from(to.plusDays(1).atStartOfDay(zone).toInstant()), branchId));
        Set<Object> coveredBranches = new HashSet<>();
        for (var row : rows) {
            if ("PAYMENT".equals(row.get("kind")) && amount(row).signum() > 0) coveredBranches.add(row.get("branchId"));
        }
        var source = new ArrayList<>(repository.sourceImportRows(from, to, branchId, "ALL"));
        source.addAll(repository.operationalBillRows(from, to, branchId));
        Set<Object> invoiceBranches = new HashSet<>();
        for (var row : source) {
            if ("KIOT_INVOICE".equals(row.get("recordType"))) invoiceBranches.add(row.get("branchId"));
        }
        for (var row : source) {
            Object branch = row.get("branchId");
            if (coveredBranches.contains(branch) || (invoiceBranches.contains(branch) && !"KIOT_INVOICE".equals(row.get("recordType")))) continue;
            var cashRow = new LinkedHashMap<String, Object>();
            cashRow.put("id", "source-" + row.get("id"));
            cashRow.put("cashDate", row.get("businessDate").toString());
            cashRow.put("kind", "PAYMENT");
            for (String key : List.of("branchId", "branchName", "sourceSystem")) cashRow.put(key, row.get(key));
            cashRow.put("method", row.get("paymentMethod"));
            cashRow.put("amount", row.get("paidAmount"));
            rows.add(cashRow);
        }
        return rows;
    }

    private static BigDecimal amount(Map<String, Object> row) {
        return (BigDecimal) row.get("amount");
    }

    // BR-106: dòng tiền ròng bằng thực thu trừ thực chi trong kỳ báo cáo.
    private static CashTotals totals(List<Map<String, Object>> rows) {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        for (var row : rows) {
            BigDecimal value = amount(row);
            if (value.signum() > 0) income = income.add(value);
            else expense = expense.subtract(value);
        }
        return new CashTotals(income, expense, income.subtract(expense));
    }

    public record CashTotals(BigDecimal income, BigDecimal expense, BigDecimal net) {}
}
