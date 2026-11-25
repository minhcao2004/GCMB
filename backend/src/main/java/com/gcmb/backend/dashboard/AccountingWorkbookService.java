package com.gcmb.backend.dashboard;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.dashboard.repository.AccountingWorkbookRepository;
import com.gcmb.backend.finance.service.FinancialPeriodService;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountingWorkbookService {
  private static final BigDecimal ZERO = BigDecimal.ZERO;
  private static final String NOTE = "Nguồn không có: TINGBOX, TK HKD, ẢNH SAO KÊ, NỘP KÉT MINI. CHI KÉT lấy từ payment_source=BRANCH_FUND (luồng cũ); CHI CK gồm các khoản còn lại và toàn bộ luồng chi mới (không có trường phương thức chi). Hoàn cọc chỉ tham khảo, không phải chi phí/lợi nhuận. CAPEX tách riêng, không trừ vào lợi nhuận.";

  private final AccountingWorkbookRepository accountingWorkbookRepository;
  private final MonthlyPerformanceService monthlyPerformanceService;

  public AccountingWorkbookService(AccountingWorkbookRepository accountingWorkbookRepository, MonthlyPerformanceService monthlyPerformanceService) {
    this.accountingWorkbookRepository = accountingWorkbookRepository;
    this.monthlyPerformanceService = monthlyPerformanceService;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> preview(Identity actor, LocalDate month, String branchIds) {
    requireAccountant(actor);
    YearMonth selectedMonth = validateMonth(month);
    List<Branch> branches = branches(branchIds);
    Dataset data = load(selectedMonth, branches);
    BigDecimal bill = ZERO, forfeited = ZERO, expense = ZERO, profit = ZERO;
    for (Branch branch : branches) {
      for (Day day : data.days.getOrDefault(branch.id, Map.of()).values()) {
        bill = bill.add(day.bill);
        forfeited = forfeited.add(day.forfeit);
        expense = expense.add(day.opexCash).add(day.opexBank).add(day.salary);
      }
    }
    @SuppressWarnings("unchecked") List<Map<String, Object>> ucRows = (List<Map<String, Object>>) monthlyPerformanceService.report(actor, selectedMonth.atDay(1), null).get("results");
    Set<UUID> ids = branches.stream().map(Branch::id).collect(java.util.stream.Collectors.toSet());
    for (Map<String, Object> row : ucRows) if (ids.contains(row.get("id"))) profit = profit.add((BigDecimal) row.get("profit"));
    String status = periodStatus(selectedMonth.atDay(1));
    long exceptions = unresolvedExceptions(selectedMonth, branches);
    return Map.of("month", selectedMonth.toString(), "branchCount", branches.size(), "billRevenue", bill,
      "forfeitedDeposits", forfeited, "totalExpense", expense, "profit", profit,
      "periodStatus", status, "unresolvedSePayExceptions", exceptions,
      "hasData", data.hasTransactions, "warnings", warnings(status, exceptions));
  }

  @Transactional
  public byte[] export(Identity actor, LocalDate month, String branchIds) {
    requireAccountant(actor);
    YearMonth selectedMonth = validateMonth(month);
    List<UUID> scopeIds = parseIds(branchIds);
    List<Branch> branches = branches(branchIds);
    Map<String, Object> report = monthlyPerformanceService.report(actor, selectedMonth.atDay(1), null);
    @SuppressWarnings("unchecked") List<Map<String, Object>> allRows = (List<Map<String, Object>>) report.get("results");
    Set<UUID> ids = scopeIds.isEmpty() ? null : new HashSet<>(scopeIds);
    List<Map<String, Object>> rows = new ArrayList<>();
    for (Map<String, Object> row : allRows) if (ids == null || ids.contains(row.get("id"))) rows.add(row);
    Map<String, Object> total = monthlyPerformanceService.totals(rows);
    total.put("name", scopeIds.isEmpty() ? "Toàn chuỗi" : "Tổng phạm vi đã chọn");
    total.put("periodStatus", report.get("periodStatus"));
    rows.add(total);
    String scope = scopeIds.isEmpty() ? "Toàn chuỗi" : String.join(", ", branches.stream().map(Branch::name).toList());
    List<Map<String, Object>> details = monthlyPerformanceService.accountingEntries(selectedMonth, scopeIds);
    byte[] bytes = MonthlyPerformanceExport.xlsx(rows, selectedMonth.toString(), scope,
      String.valueOf(report.get("periodStatus")), details);
    String selected = branchIdsJson(branches);
    accountingWorkbookRepository.insertExportAudit(actor.id(), "{\"month\":\"" + selectedMonth + "\",\"branchIds\":" + selected + "}");
    return bytes;
  }

  private Dataset load(YearMonth month, List<Branch> branches) {
    var data = new Dataset(month.lengthOfMonth());
    if (branches.isEmpty()) return data;
    List<UUID> ids = branches.stream().map(Branch::id).toList();
    applyDaily(accountingWorkbookRepository.billDaily(month, ids), data, "bill");
    applyDaily(accountingWorkbookRepository.depositDaily(month, ids), data, "deposits");
    applyDaily(accountingWorkbookRepository.billPaymentDaily(month, ids), data, "payments");
    applyLegacyExpenses(accountingWorkbookRepository.legacyExpenseDaily(month, ids), data);
    applyFinanceExpenses(accountingWorkbookRepository.financeExpenseDaily(month, ids), data);
    applyDaily(accountingWorkbookRepository.salaryDaily(month, ids), data, "salary");
    applyDaily(accountingWorkbookRepository.depositRefundDaily(month, ids), data, "refunds");
    data.expenses = accountingWorkbookRepository.expenseLines(month, ids);
    data.hasTransactions = data.days.values().stream().anyMatch(m -> m.values().stream().anyMatch(Day::hasValues)) || !data.expenses.isEmpty();
    return data;
  }

  private static void applyLegacyExpenses(List<Map<String, Object>> rows, Dataset data) {
    for (Map<String, Object> row : rows) {
      Day day = data.day((UUID) row.get("branch_id"), localDate(row.get("day")));
      day.totalExpenseCash = decimal(row.get("cash_amount"));
      day.totalExpenseBank = day.totalExpenseBank.add(decimal(row.get("bank_amount")));
      day.opexCash = decimal(row.get("opex_cash"));
      day.opexBank = decimal(row.get("opex_bank"));
    }
  }

  private static void applyFinanceExpenses(List<Map<String, Object>> rows, Dataset data) {
    for (Map<String, Object> row : rows) {
      Day day = data.day((UUID) row.get("branch_id"), localDate(row.get("day")));
      day.totalExpenseBank = day.totalExpenseBank.add(decimal(row.get("bank_amount")));
      day.opexBank = day.opexBank.add(decimal(row.get("opex_bank")));
    }
  }

  private static void applyDaily(List<Map<String, Object>> rows, Dataset data, String kind) {
    for (Map<String, Object> row : rows) {
      Day day = data.day((UUID) row.get("branch_id"), localDate(row.get("day")));
      switch (kind) {
        case "bill" -> day.bill = decimal(row.get("amount"));
        case "deposits" -> { day.apply = decimal(row.get("apply_amount")); day.forfeit = decimal(row.get("forfeit_amount")); day.refund = decimal(row.get("refund_amount")); }
        case "payments" -> { day.bank = decimal(row.get("bank_amount")); day.cash = decimal(row.get("cash_amount")); }
        case "salary" -> day.salary = decimal(row.get("amount"));
        case "refunds" -> day.refund = decimal(row.get("amount"));
        default -> { }
      }
    }
  }

  private List<Branch> branches(String branchIds) {
    List<UUID> ids = parseIds(branchIds);
    List<Branch> branches = accountingWorkbookRepository.findBranches(ids).stream()
      .map(row -> new Branch((UUID) row.get("id"), (String) row.get("code"), (String) row.get("name"), (String) row.get("timezone")))
      .toList();
    if (!ids.isEmpty() && branches.size() != ids.size()) throw bad("Cơ sở được chọn không tồn tại.");
    return branches;
  }

  private List<UUID> parseIds(String value) {
    if (value == null || value.isBlank()) return List.of();
    try {
      LinkedHashSet<UUID> result = new LinkedHashSet<>();
      for (String part : value.split(",")) result.add(UUID.fromString(part.trim()));
      return List.copyOf(result);
    } catch (RuntimeException ex) { throw bad("Danh sách cơ sở không hợp lệ."); }
  }

  private String periodStatus(LocalDate month) {
    List<String> rows = accountingWorkbookRepository.findPeriodStatus(month);
    return rows.isEmpty() ? "OPEN" : rows.get(0);
  }

  private long unresolvedExceptions(YearMonth month, List<Branch> branches) {
    if (branches.isEmpty()) return 0;
    return accountingWorkbookRepository.countUnresolvedSePayExceptions(month, branches.stream().map(Branch::id).toList());
  }

  private static BigDecimal decimal(Object v){return v==null?ZERO:v instanceof BigDecimal b?b:new BigDecimal(v.toString());}
  private static LocalDate localDate(Object v){return v instanceof java.sql.Date d?d.toLocalDate():v instanceof LocalDate d?d:LocalDate.parse(v.toString());}
  private static void requireAccountant(Identity actor){if(actor==null||!"ACCOUNTANT".equals(actor.role()))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Bạn không có quyền thực hiện đối với nghiệp vụ kế toán.");}
  private static YearMonth validateMonth(LocalDate date){YearMonth m=YearMonth.from(date);if(m.isAfter(YearMonth.now(FinancialPeriodService.ZONE)))throw bad("Không thể xuất báo cáo cho tháng trong tương lai.");return m;}
  private static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
  private static List<String> warnings(String status,long exceptions){List<String> values=new ArrayList<>();if(!"CLOSED".equals(status))values.add("Kỳ chưa khóa; số liệu có thể thay đổi.");if(exceptions>0)values.add("Còn "+exceptions+" giao dịch SePay EXCEPTION chưa xử lý.");return values;}
  private static String branchIdsJson(List<Branch> branches){return "["+String.join(",",branches.stream().map(b->"\""+b.id+"\"").toList())+"]";}

  private record Branch(UUID id,String code,String name,String timezone){}
  private static final class Day { BigDecimal bill=ZERO,apply=ZERO,bank=ZERO,cash=ZERO,totalExpenseCash=ZERO,totalExpenseBank=ZERO,opexCash=ZERO,opexBank=ZERO,forfeit=ZERO,refund=ZERO,salary=ZERO; boolean hasValues(){return bill.signum()!=0||apply.signum()!=0||bank.signum()!=0||cash.signum()!=0||totalExpenseCash.signum()!=0||totalExpenseBank.signum()!=0||opexCash.signum()!=0||opexBank.signum()!=0||forfeit.signum()!=0||refund.signum()!=0||salary.signum()!=0;} }
  private static final class Dataset { final int length; final Map<UUID,Map<LocalDate,Day>> days=new LinkedHashMap<>(); List<Map<String,Object>> expenses=List.of(); boolean hasTransactions; Dataset(int length){this.length=length;} Day day(UUID branch,LocalDate date){return days.computeIfAbsent(branch,k->new HashMap<>()).computeIfAbsent(date,k->new Day());} }
}
