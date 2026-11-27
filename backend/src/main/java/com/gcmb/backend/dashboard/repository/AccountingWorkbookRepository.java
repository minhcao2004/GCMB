package com.gcmb.backend.dashboard.repository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.gcmb.backend.dashboard.ReportCache;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
// Số liệu theo ngày cho sổ kế toán tháng (Excel): bill, cọc, thanh toán, chi phí, lương và hoàn cọc.
// Mọi truy vấn nhận danh sách cơ sở đã được kiểm tra tồn tại; danh sách rỗng được xử lý ở service.
public class AccountingWorkbookRepository {

  private final JdbcTemplate jdbcTemplate;
  private final ReportCache reportCache;

  public AccountingWorkbookRepository(JdbcTemplate jdbcTemplate, ReportCache reportCache) {
    this.jdbcTemplate = jdbcTemplate;
    this.reportCache = reportCache;
  }

  public List<Map<String, Object>> billDaily(YearMonth month, List<UUID> branchIds) {
    return reportCache.get("AccountingWorkbookRepository.billDaily", () -> billDailyQuery(month, branchIds), month, branchIds);
  }

  private List<Map<String, Object>> billDailyQuery(YearMonth month, List<UUID> branchIds) {
    return dailyRows("""
      SELECT b.branch_id,(b.completed_at AT TIME ZONE br.timezone)::date AS day,sum(b.net_amount) amount
      FROM gcmb.bill b JOIN gcmb.branch br ON br.id=b.branch_id
      WHERE b.status='SETTLED' AND (b.completed_at AT TIME ZONE br.timezone)::date>=? AND (b.completed_at AT TIME ZONE br.timezone)::date<?
      """, month, branchIds, "b.branch_id", " GROUP BY b.branch_id,day");
  }

  public List<Map<String, Object>> depositDaily(YearMonth month, List<UUID> branchIds) {
    return reportCache.get("AccountingWorkbookRepository.depositDaily", () -> depositDailyQuery(month, branchIds), month, branchIds);
  }

  private List<Map<String, Object>> depositDailyQuery(YearMonth month, List<UUID> branchIds) {
    return dailyRows("""
      SELECT d.booking_branch_id branch_id,d.day,
        sum(d.amount) FILTER (WHERE d.kind='APPLY') apply_amount,
        sum(d.amount) FILTER (WHERE d.kind='FORFEIT') forfeit_amount,
        sum(d.amount) FILTER (WHERE d.kind='REFUND') refund_amount
      FROM (SELECT bk.branch_id booking_branch_id,(e.created_at AT TIME ZONE br.timezone)::date AS day,e.kind,e.amount
        FROM gcmb.deposit_entry e JOIN gcmb.deposit dep ON dep.id=e.deposit_id
        JOIN gcmb.booking bk ON bk.id=dep.booking_id JOIN gcmb.branch br ON br.id=bk.branch_id
        WHERE e.kind IN ('APPLY','FORFEIT','REFUND') AND (e.created_at AT TIME ZONE br.timezone)::date>=? AND (e.created_at AT TIME ZONE br.timezone)::date<?) d
      WHERE 1=1
      """, month, branchIds, "d.booking_branch_id", " GROUP BY d.booking_branch_id,d.day");
  }

  public List<Map<String, Object>> billPaymentDaily(YearMonth month, List<UUID> branchIds) {
    return reportCache.get("AccountingWorkbookRepository.billPaymentDaily", () -> billPaymentDailyQuery(month, branchIds), month, branchIds);
  }

  private List<Map<String, Object>> billPaymentDailyQuery(YearMonth month, List<UUID> branchIds) {
    return dailyRows("""
      SELECT p.branch_id,(p.received_at AT TIME ZONE br.timezone)::date AS day,
        sum(p.amount) FILTER (WHERE p.method='BANK_TRANSFER') bank_amount,
        sum(p.amount) FILTER (WHERE p.method='CASH') cash_amount
      FROM gcmb.payment p JOIN gcmb.branch br ON br.id=p.branch_id
      WHERE p.purpose='BILL' AND (p.received_at AT TIME ZONE br.timezone)::date>=? AND (p.received_at AT TIME ZONE br.timezone)::date<?
      """, month, branchIds, "p.branch_id", " GROUP BY p.branch_id,day");
  }

  // Luồng chi cũ: payment_source=BRANCH_FUND là chi két, còn lại là chi chuyển khoản.
  public List<Map<String, Object>> legacyExpenseDaily(YearMonth month, List<UUID> branchIds) {
    return reportCache.get("AccountingWorkbookRepository.legacyExpenseDaily", () -> legacyExpenseDailyQuery(month, branchIds), month, branchIds);
  }

  private List<Map<String, Object>> legacyExpenseDailyQuery(YearMonth month, List<UUID> branchIds) {
    return dailyRows("""
      SELECT e.branch_id,(p.paid_at AT TIME ZONE br.timezone)::date AS day,
        sum(p.amount) FILTER (WHERE e.payment_source='BRANCH_FUND') cash_amount,
        sum(p.amount) FILTER (WHERE e.payment_source<>'BRANCH_FUND') bank_amount,
        sum(p.amount) FILTER (WHERE e.payment_source='BRANCH_FUND' AND e.class_snapshot='OPEX') opex_cash,
        sum(p.amount) FILTER (WHERE e.payment_source<>'BRANCH_FUND' AND e.class_snapshot='OPEX') opex_bank
      FROM gcmb.expense_payment p JOIN gcmb.expense e ON e.id=p.expense_id JOIN gcmb.branch br ON br.id=e.branch_id
      WHERE p.status='PAID' AND (p.paid_at AT TIME ZONE br.timezone)::date>=? AND (p.paid_at AT TIME ZONE br.timezone)::date<?
      """, month, branchIds, "e.branch_id", " GROUP BY branch_id,day");
  }

  // Luồng chi mới không có trường phương thức chi nên được tính toàn bộ là chi chuyển khoản.
  public List<Map<String, Object>> financeExpenseDaily(YearMonth month, List<UUID> branchIds) {
    return reportCache.get("AccountingWorkbookRepository.financeExpenseDaily", () -> financeExpenseDailyQuery(month, branchIds), month, branchIds);
  }

  private List<Map<String, Object>> financeExpenseDailyQuery(YearMonth month, List<UUID> branchIds) {
    return dailyRows("""
      SELECT r.branch_id,(r.paid_at AT TIME ZONE br.timezone)::date AS day,sum(r.amount) bank_amount,
        sum(r.amount) FILTER (WHERE r.category_type='OPEX') opex_bank
      FROM gcmb.finance_expense_request r JOIN gcmb.branch br ON br.id=r.branch_id
      WHERE r.status='PAID' AND (r.paid_at AT TIME ZONE br.timezone)::date>=? AND (r.paid_at AT TIME ZONE br.timezone)::date<?
      """, month, branchIds, "r.branch_id", " GROUP BY branch_id,day");
  }

  public List<Map<String, Object>> salaryDaily(YearMonth month, List<UUID> branchIds) {
    return reportCache.get("AccountingWorkbookRepository.salaryDaily", () -> salaryDailyQuery(month, branchIds), month, branchIds);
  }

  private List<Map<String, Object>> salaryDailyQuery(YearMonth month, List<UUID> branchIds) {
    return dailyRows("""
      SELECT e.branch_id,(t.occurred_at AT TIME ZONE br.timezone)::date AS day,sum(-e.amount_signed) amount
      FROM gcmb.financial_entry e JOIN gcmb.financial_transaction t ON t.id=e.transaction_id JOIN gcmb.branch br ON br.id=e.branch_id
      WHERE t.kind IN ('SALARY','ADVANCE') AND e.amount_signed<0 AND (t.occurred_at AT TIME ZONE br.timezone)::date>=? AND (t.occurred_at AT TIME ZONE br.timezone)::date<?
      """, month, branchIds, "e.branch_id", " GROUP BY e.branch_id,day");
  }

  public List<Map<String, Object>> depositRefundDaily(YearMonth month, List<UUID> branchIds) {
    return reportCache.get("AccountingWorkbookRepository.depositRefundDaily", () -> depositRefundDailyQuery(month, branchIds), month, branchIds);
  }

  private List<Map<String, Object>> depositRefundDailyQuery(YearMonth month, List<UUID> branchIds) {
    return dailyRows("""
      SELECT bk.branch_id,(e.created_at AT TIME ZONE br.timezone)::date AS day,sum(e.amount) amount
      FROM gcmb.deposit_entry e JOIN gcmb.deposit dep ON dep.id=e.deposit_id JOIN gcmb.booking bk ON bk.id=dep.booking_id JOIN gcmb.branch br ON br.id=bk.branch_id
      WHERE e.kind='REFUND' AND (e.created_at AT TIME ZONE br.timezone)::date>=? AND (e.created_at AT TIME ZONE br.timezone)::date<?
      """, month, branchIds, "bk.branch_id", " GROUP BY bk.branch_id,day");
  }

  public List<Map<String, Object>> expenseLines(YearMonth month, List<UUID> branchIds) {
    return reportCache.get("AccountingWorkbookRepository.expenseLines", () -> expenseLinesQuery(month, branchIds), month, branchIds);
  }

  private List<Map<String, Object>> expenseLinesQuery(YearMonth month, List<UUID> branchIds) {
    List<Object> args = rangeArgs(month);
    args.addAll(rangeArgs(month));
    args.addAll(branchIds);
    return jdbcTemplate.queryForList("""
      SELECT x.branch_id,x.day,x.content,x.cost_type,x.amount,x.request_code
      FROM (
        SELECT e.branch_id,(p.paid_at AT TIME ZONE b.timezone)::date AS day,c.name content,e.class_snapshot cost_type,p.amount,e.code request_code
        FROM gcmb.expense_payment p JOIN gcmb.expense e ON e.id=p.expense_id JOIN gcmb.expense_category c ON c.id=e.category_id JOIN gcmb.branch b ON b.id=e.branch_id
        WHERE p.status='PAID' AND (p.paid_at AT TIME ZONE b.timezone)::date>=? AND (p.paid_at AT TIME ZONE b.timezone)::date<?
        UNION ALL
        SELECT r.branch_id,(r.paid_at AT TIME ZONE b.timezone)::date AS day,c.name content,r.category_type cost_type,r.amount,r.code request_code
        FROM gcmb.finance_expense_request r JOIN gcmb.finance_expense_category c ON c.id=r.category_id JOIN gcmb.branch b ON b.id=r.branch_id
        WHERE r.status='PAID' AND (r.paid_at AT TIME ZONE b.timezone)::date>=? AND (r.paid_at AT TIME ZONE b.timezone)::date<?
      ) x WHERE x.branch_id IN (""" + placeholders(branchIds.size()) + ") ORDER BY x.branch_id,x.day,x.request_code",
      args.toArray());
  }

  public List<Map<String, Object>> findBranches(List<UUID> branchIds) {
    if (branchIds.isEmpty()) {
      return jdbcTemplate.queryForList("SELECT id,code,name,timezone FROM gcmb.branch ORDER BY name,id");
    }
    return jdbcTemplate.queryForList("SELECT id,code,name,timezone FROM gcmb.branch WHERE id IN ("
      + placeholders(branchIds.size()) + ") ORDER BY name,id", branchIds.toArray());
  }

  public List<String> findPeriodStatus(LocalDate month) {
    return jdbcTemplate.queryForList("SELECT status FROM gcmb.financial_period WHERE \"month\"=?", String.class, month);
  }

  public long countUnresolvedSePayExceptions(YearMonth month, List<UUID> branchIds) {
    List<Object> args = rangeArgs(month);
    args.addAll(branchIds);
    Long count = jdbcTemplate.queryForObject("""
      SELECT count(*) FROM gcmb.bank_transaction t JOIN gcmb.financial_account a ON a.id=t.account_id
      WHERE t.provider='SEPAY' AND t.match_status IN ('EXCEPTION','UNMATCHED') AND t.resolution_status='UNRESOLVED'
        AND (t.occurred_at AT TIME ZONE COALESCE((SELECT timezone FROM gcmb.branch WHERE id=a.branch_id),'Asia/Ho_Chi_Minh'))::date>=?
        AND (t.occurred_at AT TIME ZONE COALESCE((SELECT timezone FROM gcmb.branch WHERE id=a.branch_id),'Asia/Ho_Chi_Minh'))::date<?
        AND (a.branch_id IS NULL OR a.branch_id IN (""" + placeholders(branchIds.size()) + "))",
      Long.class, args.toArray());
    return count == null ? 0 : count;
  }

  public void insertExportAudit(UUID actorId, String afterDataJson) {
    jdbcTemplate.update("INSERT INTO gcmb.audit_event(actor_id,branch_id,action,entity_type,after_data) VALUES (?,NULL,'EXPORT_ACCOUNTING_MONTHLY_WORKBOOK','REPORT',?::jsonb)",
      actorId, afterDataJson);
  }

  private List<Map<String, Object>> dailyRows(String sql, YearMonth month, List<UUID> branchIds, String branchColumn, String groupBy) {
    List<Object> args = rangeArgs(month);
    args.addAll(branchIds);
    String query = sql + " AND " + branchColumn + " IN (" + placeholders(branchIds.size()) + ")" + groupBy;
    return jdbcTemplate.queryForList(query, args.toArray());
  }

  private static String placeholders(int count) {
    return String.join(",", Collections.nCopies(count, "?"));
  }

  private static List<Object> rangeArgs(YearMonth month) {
    return new ArrayList<>(List.of(month.atDay(1), month.plusMonths(1).atDay(1)));
  }
}
