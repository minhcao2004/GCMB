package com.gcmb.backend.dashboard;

import com.gcmb.backend.auth.dto.Identity;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountingWorkbookService {
  private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
  private static final BigDecimal ZERO = BigDecimal.ZERO;
  private static final String NOTE = "Nguồn không có: TINGBOX, TK HKD, ẢNH SAO KÊ, NỘP KÉT MINI. CHI KÉT lấy từ payment_source=BRANCH_FUND (luồng cũ); CHI CK gồm các khoản còn lại và toàn bộ luồng chi mới (không có trường phương thức chi). Hoàn cọc chỉ tham khảo, không phải chi phí/lợi nhuận. CAPEX tách riêng, không trừ vào lợi nhuận.";

  private final JdbcTemplate db;
  private final MonthlyPerformanceService performance;

  public AccountingWorkbookService(JdbcTemplate db, MonthlyPerformanceService performance) {
    this.db = db;
    this.performance = performance;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> preview(Identity actor, LocalDate month, String branchIds) {
    requireAccountant(actor);
    YearMonth selectedMonth = validateMonth(month);
    List<Branch> branches = branches(branchIds);
    Dataset data = load(selectedMonth, branches);
    BigDecimal bill = ZERO, forfeited = ZERO, expense = ZERO, profit = ZERO;
    for (Branch branch : branches) {
      for (Day day : data.days.get(branch.id).values()) {
        bill = bill.add(day.bill);
        forfeited = forfeited.add(day.forfeit);
        expense = expense.add(day.opexCash).add(day.opexBank).add(day.salary);
      }
    }
    @SuppressWarnings("unchecked") List<Map<String, Object>> ucRows = (List<Map<String, Object>>) performance.report(actor, selectedMonth.atDay(1), null).get("results");
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
    Map<String, Object> report = performance.report(actor, selectedMonth.atDay(1), null);
    @SuppressWarnings("unchecked") List<Map<String, Object>> allRows = (List<Map<String, Object>>) report.get("results");
    Set<UUID> ids = scopeIds.isEmpty() ? null : new HashSet<>(scopeIds);
    List<Map<String, Object>> rows = new ArrayList<>();
    for (Map<String, Object> row : allRows) if (ids == null || ids.contains(row.get("id"))) rows.add(row);
    Map<String, Object> total = performance.totals(rows);
    total.put("name", scopeIds.isEmpty() ? "Toàn chuỗi" : "Tổng phạm vi đã chọn");
    total.put("periodStatus", report.get("periodStatus"));
    rows.add(total);
    String scope = scopeIds.isEmpty() ? "Toàn chuỗi" : String.join(", ", branches.stream().map(Branch::name).toList());
    List<Map<String, Object>> details = performance.accountingEntries(selectedMonth, scopeIds);
    byte[] bytes = MonthlyPerformanceExport.xlsx(rows, selectedMonth.toString(), scope,
      String.valueOf(report.get("periodStatus")), details);
    String selected = branchIdsJson(branches);
    db.update("INSERT INTO gcmb.audit_event(actor_id,branch_id,action,entity_type,after_data) VALUES (?,NULL,'EXPORT_ACCOUNTING_MONTHLY_WORKBOOK','REPORT',?::jsonb)",
      actor.id(), "{\"month\":\"" + selectedMonth + "\",\"branchIds\":" + selected + "}");
    return bytes;
  }

  private Dataset load(YearMonth month, List<Branch> branches) {
    var data = new Dataset(month.lengthOfMonth());
    if (branches.isEmpty()) return data;
    List<UUID> ids = branches.stream().map(Branch::id).toList();
    List<Object> args = new ArrayList<>();
    args.add(month.atDay(1)); args.add(month.plusMonths(1).atDay(1));
    dataQuery("""
      SELECT b.branch_id,(b.completed_at AT TIME ZONE br.timezone)::date day,sum(b.net_amount) amount
      FROM gcmb.bill b JOIN gcmb.branch br ON br.id=b.branch_id
      WHERE b.status='SETTLED' AND (b.completed_at AT TIME ZONE br.timezone)::date>=? AND (b.completed_at AT TIME ZONE br.timezone)::date<?
      """, ids, args, "b.branch_id", " GROUP BY b.branch_id,day", data, "bill");

    args = rangeArgs(month);
    dataQuery("""
      SELECT d.booking_branch_id branch_id,d.day,
        sum(d.amount) FILTER (WHERE d.kind='APPLY') apply_amount,
        sum(d.amount) FILTER (WHERE d.kind='FORFEIT') forfeit_amount,
        sum(d.amount) FILTER (WHERE d.kind='REFUND') refund_amount
      FROM (SELECT bk.branch_id booking_branch_id,(e.created_at AT TIME ZONE br.timezone)::date day,e.kind,e.amount
        FROM gcmb.deposit_entry e JOIN gcmb.deposit dep ON dep.id=e.deposit_id
        JOIN gcmb.booking bk ON bk.id=dep.booking_id JOIN gcmb.branch br ON br.id=bk.branch_id
        WHERE e.kind IN ('APPLY','FORFEIT','REFUND') AND (e.created_at AT TIME ZONE br.timezone)::date>=? AND (e.created_at AT TIME ZONE br.timezone)::date<?) d
      WHERE 1=1
      """, ids, args, "d.booking_branch_id", " GROUP BY d.booking_branch_id,d.day", data, "deposits");

    args = rangeArgs(month);
    dataQuery("""
      SELECT p.branch_id,(p.received_at AT TIME ZONE br.timezone)::date day,
        sum(p.amount) FILTER (WHERE p.method='BANK_TRANSFER') bank_amount,
        sum(p.amount) FILTER (WHERE p.method='CASH') cash_amount
      FROM gcmb.payment p JOIN gcmb.branch br ON br.id=p.branch_id
      WHERE p.purpose='BILL' AND (p.received_at AT TIME ZONE br.timezone)::date>=? AND (p.received_at AT TIME ZONE br.timezone)::date<?
      """, ids, args, "p.branch_id", " GROUP BY p.branch_id,day", data, "payments");

    args = rangeArgs(month);
    expenseDaily("""
      SELECT e.branch_id,(p.paid_at AT TIME ZONE br.timezone)::date day,
        sum(p.amount) FILTER (WHERE e.payment_source='BRANCH_FUND') cash_amount,
        sum(p.amount) FILTER (WHERE e.payment_source<>'BRANCH_FUND') bank_amount,
        sum(p.amount) FILTER (WHERE e.payment_source='BRANCH_FUND' AND e.class_snapshot='OPEX') opex_cash,
        sum(p.amount) FILTER (WHERE e.payment_source<>'BRANCH_FUND' AND e.class_snapshot='OPEX') opex_bank
      FROM gcmb.expense_payment p JOIN gcmb.expense e ON e.id=p.expense_id JOIN gcmb.branch br ON br.id=e.branch_id
      WHERE p.status='PAID' AND (p.paid_at AT TIME ZONE br.timezone)::date>=? AND (p.paid_at AT TIME ZONE br.timezone)::date<?
      """, ids, args, data, "legacyOpex");
    args = rangeArgs(month);
    expenseDaily("""
      SELECT r.branch_id,(r.paid_at AT TIME ZONE br.timezone)::date day,sum(r.amount) bank_amount,
        sum(r.amount) FILTER (WHERE r.category_type='OPEX') opex_bank
      FROM gcmb.finance_expense_request r JOIN gcmb.branch br ON br.id=r.branch_id
      WHERE r.status='PAID' AND (r.paid_at AT TIME ZONE br.timezone)::date>=? AND (r.paid_at AT TIME ZONE br.timezone)::date<?
      """, ids, args, data, "newOpex");

    args = rangeArgs(month);
    dataQuery("""
      SELECT e.branch_id,(t.occurred_at AT TIME ZONE br.timezone)::date day,sum(-e.amount_signed) amount
      FROM gcmb.financial_entry e JOIN gcmb.financial_transaction t ON t.id=e.transaction_id JOIN gcmb.branch br ON br.id=e.branch_id
      WHERE t.kind IN ('SALARY','ADVANCE') AND e.amount_signed<0 AND (t.occurred_at AT TIME ZONE br.timezone)::date>=? AND (t.occurred_at AT TIME ZONE br.timezone)::date<?
      """, ids, args, "e.branch_id", " GROUP BY e.branch_id,day", data, "salary");

    args = rangeArgs(month);
    dataQuery("""
      SELECT bk.branch_id,(e.created_at AT TIME ZONE br.timezone)::date day,sum(e.amount) amount
      FROM gcmb.deposit_entry e JOIN gcmb.deposit dep ON dep.id=e.deposit_id JOIN gcmb.booking bk ON bk.id=dep.booking_id JOIN gcmb.branch br ON br.id=bk.branch_id
      WHERE e.kind='REFUND' AND (e.created_at AT TIME ZONE br.timezone)::date>=? AND (e.created_at AT TIME ZONE br.timezone)::date<?
      """, ids, args, "bk.branch_id", " GROUP BY bk.branch_id,day", data, "refunds");

    data.expenses = expenseLines(month, ids);
    data.hasTransactions = data.days.values().stream().anyMatch(m -> m.values().stream().anyMatch(Day::hasValues)) || !data.expenses.isEmpty();
    return data;
  }

  private void expenseDaily(String sql, List<UUID> ids, List<Object> args, Dataset data, String ignored) {
    String full = sql + " AND " + (sql.contains("expense_payment") ? "e.branch_id" : "r.branch_id") + " IN (" + marks(ids.size()) + ") GROUP BY branch_id,day";
    args.addAll(ids);
    for (Map<String, Object> row : db.queryForList(full, args.toArray())) {
      Day day = data.day((UUID) row.get("branch_id"), localDate(row.get("day")));
      if (sql.contains("expense_payment")) {
        day.totalExpenseCash = decimal(row.get("cash_amount"));
        day.totalExpenseBank = day.totalExpenseBank.add(decimal(row.get("bank_amount")));
        day.opexCash = decimal(row.get("opex_cash"));
        day.opexBank = decimal(row.get("opex_bank"));
      } else {
        day.totalExpenseBank = day.totalExpenseBank.add(decimal(row.get("bank_amount")));
        day.opexBank = day.opexBank.add(decimal(row.get("opex_bank")));
      }
    }
  }

  private void dataQuery(String sql, List<UUID> ids, List<Object> args, String branchColumn, String group, Dataset data, String kind) {
    String query = sql + " AND " + branchColumn + " IN (" + marks(ids.size()) + ")" + group;
    args.addAll(ids);
    for (Map<String, Object> row : db.queryForList(query, args.toArray())) {
      UUID branch = (UUID) row.get("branch_id");
      LocalDate date = localDate(row.get("day"));
      Day day = data.day(branch, date);
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

  private List<Map<String, Object>> expenseLines(YearMonth month, List<UUID> ids) {
    String placeholders = marks(ids.size());
    return db.queryForList("""
      SELECT x.branch_id,x.day,x.content,x.cost_type,x.amount,x.request_code
      FROM (
        SELECT e.branch_id,(p.paid_at AT TIME ZONE b.timezone)::date day,c.name content,e.class_snapshot cost_type,p.amount,e.code request_code
        FROM gcmb.expense_payment p JOIN gcmb.expense e ON e.id=p.expense_id JOIN gcmb.expense_category c ON c.id=e.category_id JOIN gcmb.branch b ON b.id=e.branch_id
        WHERE p.status='PAID' AND (p.paid_at AT TIME ZONE b.timezone)::date>=? AND (p.paid_at AT TIME ZONE b.timezone)::date<?
        UNION ALL
        SELECT r.branch_id,(r.paid_at AT TIME ZONE b.timezone)::date day,c.name content,r.category_type cost_type,r.amount,r.code request_code
        FROM gcmb.finance_expense_request r JOIN gcmb.finance_expense_category c ON c.id=r.category_id JOIN gcmb.branch b ON b.id=r.branch_id
        WHERE r.status='PAID' AND (r.paid_at AT TIME ZONE b.timezone)::date>=? AND (r.paid_at AT TIME ZONE b.timezone)::date<?
      ) x WHERE x.branch_id IN (""" + placeholders + ") ORDER BY x.branch_id,x.day,x.request_code",
      month.atDay(1), month.plusMonths(1).atDay(1), month.atDay(1), month.plusMonths(1).atDay(1), ids.toArray());
  }

  private List<Branch> branches(String branchIds) {
    List<UUID> ids = parseIds(branchIds);
    if (ids.isEmpty()) return db.query("SELECT id,code,name,timezone FROM gcmb.branch ORDER BY name,id", (rs,n) -> new Branch(rs.getObject("id",UUID.class),rs.getString("code"),rs.getString("name"),rs.getString("timezone")));
    String marks = marks(ids.size());
    List<Branch> branches = db.query("SELECT id,code,name,timezone FROM gcmb.branch WHERE id IN (" + marks + ") ORDER BY name,id",
      (rs,n) -> new Branch(rs.getObject("id",UUID.class),rs.getString("code"),rs.getString("name"),rs.getString("timezone")), ids.toArray());
    if (branches.size() != ids.size()) throw bad("Cơ sở được chọn không tồn tại.");
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
    List<String> rows = db.queryForList("SELECT status FROM gcmb.financial_period WHERE \"month\"=?", String.class, month);
    return rows.isEmpty() ? "OPEN" : rows.get(0);
  }

  private long unresolvedExceptions(YearMonth month, List<Branch> branches) {
    if (branches.isEmpty()) return 0;
    List<UUID> ids = branches.stream().map(Branch::id).toList();
    String marks = marks(ids.size());
    Long count = db.queryForObject("""
      SELECT count(*) FROM gcmb.bank_transaction t JOIN gcmb.financial_account a ON a.id=t.account_id
      WHERE t.provider='SEPAY' AND t.match_status='EXCEPTION' AND t.resolution_status='UNRESOLVED'
        AND (t.occurred_at AT TIME ZONE COALESCE((SELECT timezone FROM gcmb.branch WHERE id=a.branch_id),'Asia/Ho_Chi_Minh'))::date>=?
        AND (t.occurred_at AT TIME ZONE COALESCE((SELECT timezone FROM gcmb.branch WHERE id=a.branch_id),'Asia/Ho_Chi_Minh'))::date<?
        AND (a.branch_id IS NULL OR a.branch_id IN (""" + marks + "))",
      Long.class, month.atDay(1), month.plusMonths(1).atDay(1), ids.toArray());
    return count == null ? 0 : count;
  }

  private static String marks(int count){return String.join(",",Collections.nCopies(count,"?"));}
  private static List<Object> rangeArgs(YearMonth m){return new ArrayList<>(List.of(m.atDay(1),m.plusMonths(1).atDay(1)));}
  private static BigDecimal decimal(Object v){return v==null?ZERO:v instanceof BigDecimal b?b:new BigDecimal(v.toString());}
  private static LocalDate localDate(Object v){return v instanceof java.sql.Date d?d.toLocalDate():v instanceof LocalDate d?d:LocalDate.parse(v.toString());}
  private static void requireAccountant(Identity actor){if(actor==null||!"ACCOUNTANT".equals(actor.role()))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Bạn không có quyền thực hiện đối với nghiệp vụ kế toán.");}
  private static YearMonth validateMonth(LocalDate date){YearMonth m=YearMonth.from(date);if(m.isAfter(YearMonth.now(ZONE)))throw bad("Không thể xuất báo cáo cho tháng trong tương lai.");return m;}
  private static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
  private static List<String> warnings(String status,long exceptions){List<String> values=new ArrayList<>();if(!"CLOSED".equals(status))values.add("Kỳ chưa khóa; số liệu có thể thay đổi.");if(exceptions>0)values.add("Còn "+exceptions+" giao dịch SePay EXCEPTION chưa xử lý.");return values;}
  private static String branchIdsJson(List<Branch> branches){return "["+String.join(",",branches.stream().map(b->"\""+b.id+"\"").toList())+"]";}

  private record Branch(UUID id,String code,String name,String timezone){}
  private static final class Day { BigDecimal bill=ZERO,apply=ZERO,bank=ZERO,cash=ZERO,totalExpenseCash=ZERO,totalExpenseBank=ZERO,opexCash=ZERO,opexBank=ZERO,forfeit=ZERO,refund=ZERO,salary=ZERO; boolean hasValues(){return bill.signum()!=0||apply.signum()!=0||bank.signum()!=0||cash.signum()!=0||totalExpenseCash.signum()!=0||totalExpenseBank.signum()!=0||opexCash.signum()!=0||opexBank.signum()!=0||forfeit.signum()!=0||refund.signum()!=0||salary.signum()!=0;} }
  private static final class Dataset { final int length; final Map<UUID,Map<LocalDate,Day>> days=new LinkedHashMap<>(); List<Map<String,Object>> expenses=List.of(); boolean hasTransactions; Dataset(int length){this.length=length;} Day day(UUID branch,LocalDate date){return days.computeIfAbsent(branch,k->new HashMap<>()).computeIfAbsent(date,k->new Day());} }
}
