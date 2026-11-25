package com.gcmb.backend.workforce.repository;

import com.gcmb.backend.finance.repository.AccountantFund;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
// Bảng lương: đơn giá giờ theo cơ sở, phần lương QLCS gửi, bảng lương tháng, ứng lương và chi trả.
public class PayrollRepository {

  private static final String SUBMISSION_COLUMNS = """
    SELECT period_id,employee_id,branch_id,minutes,wage,bonus,penalty,reason,
      submitted_by,submitted_at,source_snapshot::text AS source_snapshot
    FROM gcmb.branch_payroll_submission
    """;

  private final JdbcTemplate jdbcTemplate;

  public PayrollRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  // ---- Nhân viên và đơn giá giờ tại cơ sở ----

  public List<Map<String, Object>> findPayableBranchEmployees(UUID branchId) {
    return jdbcTemplate.queryForList("""
      SELECT e.id,e.code,e.full_name AS name,e.bank_name,e.bank_account,e.bank_holder FROM gcmb.employee e
      LEFT JOIN gcmb.user_account u ON u.id=e.user_id
      LEFT JOIN gcmb.role r ON r.id=u.role_id
      WHERE e.status='ACTIVE'
        AND e.position NOT IN ('BRANCH_MANAGER','ACCOUNTANT')
        AND (e.user_id IS NULL OR (u.status='ACTIVE' AND r.code NOT IN ('BRANCH_MANAGER','ACCOUNTANT')))
        AND EXISTS (SELECT 1 FROM gcmb.employee_assignment a WHERE a.employee_id=e.id
        AND a.branch_id=? AND a.valid_from<=CURRENT_TIMESTAMP
        AND (a.valid_to IS NULL OR a.valid_to>CURRENT_TIMESTAMP))
      ORDER BY e.full_name
      """, branchId);
  }

  // BR-67: đơn giá có hiệu lực trước khi tháng lương đã gửi kết thúc thì không được sửa ngược.
  public boolean rateAffectsSubmittedPayroll(UUID branchId, UUID employeeId, LocalDate effectiveFrom) {
    return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
      SELECT EXISTS(SELECT 1 FROM gcmb.branch_payroll_submission s
        JOIN gcmb.payroll_period p ON p.id=s.period_id
        WHERE s.branch_id=? AND s.employee_id=? AND p.month+interval '1 month'>?)
      OR EXISTS(SELECT 1 FROM gcmb.payroll_entry e JOIN gcmb.payroll_period p ON p.id=e.period_id
        WHERE e.submitted_branch_id=? AND e.employee_id=? AND p.month+interval '1 month'>?)
      """, Boolean.class, branchId, employeeId, effectiveFrom, branchId, employeeId, effectiveFrom));
  }

  public void upsertBranchHourlyRate(UUID branchId, UUID employeeId, LocalDate effectiveFrom, BigDecimal hourlyRate) {
    jdbcTemplate.update("""
      INSERT INTO gcmb.branch_employee_pay_rate VALUES (?,?,?,?)
      ON CONFLICT (branch_id,employee_id,effective_from) DO UPDATE SET hourly_rate=excluded.hourly_rate
      """, branchId, employeeId, effectiveFrom, hourlyRate);
  }

  public boolean hasWorkedShiftWithoutRate(UUID branchId, UUID employeeId, LocalDate from, LocalDate toExclusive) {
    return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
      SELECT EXISTS(SELECT 1 FROM gcmb.attendance a
        JOIN gcmb.shift_assignment x ON x.id=a.assignment_id
        JOIN gcmb.scheduled_shift s ON s.id=x.shift_id
        WHERE x.employee_id=? AND s.branch_id=? AND x.status='CONFIRMED'
          AND s.status<>'CANCELLED' AND a.status='WORKED'
          AND s.work_date>=? AND s.work_date<? AND NOT EXISTS(
            SELECT 1 FROM gcmb.branch_employee_pay_rate r WHERE r.branch_id=s.branch_id
              AND r.employee_id=x.employee_id AND r.effective_from<=s.work_date))
      """, Boolean.class, employeeId, branchId, from, toExclusive));
  }

  public List<BigDecimal> findBranchHourlyRate(Object branchId, UUID employeeId, LocalDate workDate) {
    return jdbcTemplate.queryForList("""
      SELECT hourly_rate FROM gcmb.branch_employee_pay_rate
      WHERE branch_id=? AND employee_id=? AND effective_from<=?
      ORDER BY effective_from DESC LIMIT 1
      """, BigDecimal.class, branchId, employeeId, workDate);
  }

  // ---- Công và phần lương QLCS gửi ----

  public List<Map<String, Object>> findSettlementEmployees(UUID branchId, LocalDate monthStart) {
    return jdbcTemplate.queryForList("""
      SELECT e.id,e.code,e.full_name AS name,e.status,e.bank_name,e.bank_account,e.bank_holder
      FROM gcmb.employee e WHERE EXISTS (
        SELECT 1 FROM gcmb.shift_assignment x JOIN gcmb.scheduled_shift s ON s.id=x.shift_id
        WHERE x.employee_id=e.id AND s.branch_id=? AND s.work_date>=? AND s.work_date<?
          AND x.status='CONFIRMED' AND s.status<>'CANCELLED')
      ORDER BY e.code
      """, branchId, monthStart, monthStart.plusMonths(1));
  }

  // Lịch sử theo cơ sở, gồm cả bảng lương đã gửi trước V42 (chưa có branch_payroll_submission).
  public List<Map<String, Object>> findBranchHistory(UUID branchId, LocalDate monthStart) {
    return jdbcTemplate.queryForList("""
      SELECT e.id AS employee_id,e.code,e.full_name AS name,s.wage,s.bonus,s.penalty,s.reason,
        s.minutes,s.submitted_at,(s.wage+s.bonus-s.penalty) AS branch_amount,p.net_amount AS final_net,
        p.id IS NOT NULL AS ready,
        EXISTS(SELECT 1 FROM gcmb.salary_payment z WHERE z.payroll_entry_id=p.id AND z.status='PAID') AS paid
      FROM gcmb.branch_payroll_submission s JOIN gcmb.employee e ON e.id=s.employee_id
      JOIN gcmb.payroll_period q ON q.id=s.period_id
      LEFT JOIN gcmb.payroll_entry p ON p.period_id=s.period_id AND p.employee_id=s.employee_id
        AND p.submitted_branch_id IS NOT NULL
      WHERE s.branch_id=? AND q.month=?
      UNION ALL
      SELECT e.id,e.code,e.full_name,p.gross_amount,0,p.deduction_amount,'Bảng lương đã chốt',
        0,p.created_at,p.net_amount,p.net_amount,true,
        EXISTS(SELECT 1 FROM gcmb.salary_payment z WHERE z.payroll_entry_id=p.id AND z.status='PAID')
      FROM gcmb.payroll_entry p JOIN gcmb.employee e ON e.id=p.employee_id
      JOIN gcmb.payroll_period q ON q.id=p.period_id
      WHERE p.submitted_branch_id=? AND q.month=? AND NOT EXISTS(
        SELECT 1 FROM gcmb.branch_payroll_submission s WHERE s.period_id=p.period_id AND s.employee_id=p.employee_id)
      ORDER BY code
      """, branchId, monthStart, branchId, monthStart);
  }

  public List<Map<String, Object>> findSubmittedCalculation(UUID branchId, UUID employeeId, LocalDate monthStart) {
    return jdbcTemplate.queryForList("""
      SELECT (s.source_snapshot->'calculation'->>'reward')::numeric AS reward,
        (s.source_snapshot->'calculation'->>'advance')::numeric AS advance,
        (s.source_snapshot->'calculation'->>'net')::numeric AS net,
        (s.source_snapshot->'calculation'->>'multipleBranches')::boolean AS multiple
      FROM gcmb.branch_payroll_submission s JOIN gcmb.payroll_period p ON p.id=s.period_id
      WHERE s.branch_id=? AND s.employee_id=? AND p.month=?
      """, branchId, employeeId, monthStart);
  }

  public Boolean workedAtMultipleBranches(UUID employeeId, LocalDate monthStart) {
    return jdbcTemplate.queryForObject("""
      SELECT count(DISTINCT s.branch_id)>1 FROM gcmb.shift_assignment x
      JOIN gcmb.scheduled_shift s ON s.id=x.shift_id WHERE x.employee_id=?
        AND s.work_date>=? AND s.work_date<? AND x.status='CONFIRMED' AND s.status<>'CANCELLED'
      """, Boolean.class, employeeId, monthStart, monthStart.plusMonths(1));
  }

  public List<Map<String, Object>> findMonthAttendance(UUID employeeId, LocalDate monthStart) {
    return jdbcTemplate.queryForList("""
      SELECT a.*,s.starts_at,s.ends_at,s.branch_id,b.timezone,
        CASE WHEN gcmb.employee_month_closed(s.branch_id,x.employee_id,s.work_date) THEN 'CLOSED' ELSE p.status END AS period_status,s.work_date
      FROM gcmb.attendance a JOIN gcmb.shift_assignment x ON x.id=a.assignment_id
      JOIN gcmb.scheduled_shift s ON s.id=x.shift_id JOIN gcmb.branch b ON b.id=s.branch_id
      JOIN gcmb.attendance_period p ON p.id=a.period_id
      WHERE x.employee_id=? AND s.work_date>=? AND s.work_date<?
        AND x.status='CONFIRMED' AND s.status<>'CANCELLED' ORDER BY s.starts_at
      """, employeeId, monthStart, monthStart.plusMonths(1));
  }

  // BR-64: còn ca đã xác nhận chưa có công hoặc chưa khóa công tháng của nhân viên.
  public boolean hasUnclosedAttendance(UUID employeeId, LocalDate monthStart) {
    return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
      SELECT EXISTS(SELECT 1 FROM gcmb.shift_assignment x JOIN gcmb.scheduled_shift s ON s.id=x.shift_id
        LEFT JOIN gcmb.attendance a ON a.assignment_id=x.id LEFT JOIN gcmb.attendance_period p ON p.id=a.period_id
        WHERE x.employee_id=? AND s.work_date>=? AND s.work_date<? AND x.status='CONFIRMED' AND s.status<>'CANCELLED'
          AND (a.id IS NULL OR NOT gcmb.employee_month_closed(s.branch_id,x.employee_id,s.work_date)))
      """, Boolean.class, employeeId, monthStart, monthStart.plusMonths(1)));
  }

  public boolean hasSubmittedPayrollEntry(Object periodId, UUID employeeId) {
    return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
      SELECT EXISTS(SELECT 1 FROM gcmb.payroll_entry WHERE period_id=? AND employee_id=?
        AND submitted_branch_id IS NOT NULL)
      """, Boolean.class, periodId, employeeId));
  }

  public List<Map<String, Object>> findBranchSubmission(Object periodId, UUID employeeId, UUID branchId) {
    return jdbcTemplate.queryForList(SUBMISSION_COLUMNS + " WHERE period_id=? AND employee_id=? AND branch_id=?",
      periodId, employeeId, branchId);
  }

  public List<Map<String, Object>> findSubmissions(Object periodId, UUID employeeId) {
    return jdbcTemplate.queryForList(SUBMISSION_COLUMNS + " WHERE period_id=? AND employee_id=? ORDER BY branch_id",
      periodId, employeeId);
  }

  public void upsertBranchSubmission(Object periodId, UUID employeeId, UUID branchId, long minutes, BigDecimal wage,
      BigDecimal bonus, BigDecimal penalty, String reason, String sourceSnapshotJson, UUID submittedBy) {
    jdbcTemplate.update("""
      INSERT INTO gcmb.branch_payroll_submission
        (period_id,employee_id,branch_id,minutes,wage,bonus,penalty,reason,source_snapshot,submitted_by)
      VALUES (?,?,?,?,?,?,?,?,CAST(? AS jsonb),?)
      ON CONFLICT (period_id,employee_id,branch_id) DO UPDATE SET minutes=excluded.minutes,
        wage=excluded.wage,bonus=excluded.bonus,penalty=excluded.penalty,reason=excluded.reason,
        source_snapshot=excluded.source_snapshot,submitted_by=excluded.submitted_by,submitted_at=CURRENT_TIMESTAMP
      """, periodId, employeeId, branchId, minutes, wage, bonus, penalty, reason, sourceSnapshotJson, submittedBy);
  }

  // ---- Bảng lương tháng ----

  public void ensurePayrollPeriod(LocalDate monthStart) {
    jdbcTemplate.update("INSERT INTO gcmb.payroll_period(month,status) VALUES (?,'DRAFT') ON CONFLICT(month) DO NOTHING",
      monthStart);
  }

  public List<Map<String, Object>> findPayrollPeriod(LocalDate monthStart) {
    return jdbcTemplate.queryForList("SELECT * FROM gcmb.payroll_period WHERE month=?", monthStart);
  }

  public void incrementPeriodVersion(Object periodId) {
    jdbcTemplate.update("UPDATE gcmb.payroll_period SET version=version+1 WHERE id=?", periodId);
  }

  // Tạo hoặc cập nhật bảng lương tháng từ phần QLCS gửi, chụp lại tài khoản nhận lương hiện tại.
  public UUID upsertSubmittedEntry(Object periodId, UUID employeeId, BigDecimal gross, BigDecimal deduction,
      BigDecimal net, UUID submittedBranchId) {
    return jdbcTemplate.queryForObject("""
      INSERT INTO gcmb.payroll_entry(period_id,employee_id,gross_amount,deduction_amount,net_amount,
        submitted_branch_id,bank_name_snapshot,bank_account_snapshot,bank_holder_snapshot)
      SELECT ?,id,?,?,?,?,bank_name,bank_account,bank_holder FROM gcmb.employee WHERE id=?
      ON CONFLICT (period_id,employee_id) DO UPDATE SET gross_amount=excluded.gross_amount,
        deduction_amount=excluded.deduction_amount,net_amount=excluded.net_amount,
        submitted_branch_id=excluded.submitted_branch_id,bank_name_snapshot=excluded.bank_name_snapshot,
        bank_account_snapshot=excluded.bank_account_snapshot,bank_holder_snapshot=excluded.bank_holder_snapshot
      RETURNING id
      """, UUID.class, periodId, gross, deduction, net, submittedBranchId, employeeId);
  }

  public void deleteComponents(UUID payrollEntryId) {
    jdbcTemplate.update("DELETE FROM gcmb.payroll_component WHERE payroll_entry_id=?", payrollEntryId);
  }

  public void deleteAdvanceApplications(UUID payrollEntryId) {
    jdbcTemplate.update("DELETE FROM gcmb.advance_application WHERE payroll_entry_id=?", payrollEntryId);
  }

  // BR-75: mỗi khoản ứng đã chi chỉ được trừ vào một bảng lương (advance_id duy nhất).
  public void applyPaidAdvances(UUID payrollEntryId, UUID employeeId, Object periodId) {
    jdbcTemplate.update("""
      INSERT INTO gcmb.advance_application(advance_id,payroll_entry_id,amount)
      SELECT id,?,amount FROM gcmb.salary_advance WHERE employee_id=? AND period_id=? AND status='PAID'
      """, payrollEntryId, employeeId, periodId);
  }

  public void insertComponent(UUID payrollEntryId, UUID branchId, String kind, BigDecimal amount,
      String calculationSnapshotJson, String reason) {
    jdbcTemplate.update(
      "INSERT INTO gcmb.payroll_component(payroll_entry_id,branch_id,kind,minutes,hourly_rate_snapshot,multiplier,amount,calculation_snapshot,reason) VALUES (?,?,?,0,0,1,?,CAST(? AS jsonb),?)",
      payrollEntryId, branchId, kind, amount, calculationSnapshotJson, reason);
  }

  public BigDecimal sumPaidAdvances(UUID employeeId, UUID periodId) {
    return jdbcTemplate.queryForObject(
      "SELECT COALESCE(sum(amount),0) FROM gcmb.salary_advance WHERE employee_id=? AND period_id=? AND status='PAID'",
      BigDecimal.class, employeeId, periodId);
  }

  // ---- Màn hình kế toán ----

  public List<Map<String, Object>> findEmployeeOptions() {
    return jdbcTemplate.queryForList("SELECT id,code,full_name AS name,status FROM gcmb.employee ORDER BY code");
  }

  public List<Map<String, Object>> findBranchOptions() {
    return jdbcTemplate.queryForList("SELECT id,name FROM gcmb.branch ORDER BY name");
  }

  public List<Map<String, Object>> findCentralFunds() {
    return jdbcTemplate.queryForList(
      "SELECT id,name,CAST(? AS numeric) AS balance FROM gcmb.financial_account WHERE kind='CENTRAL_FUND' AND active ORDER BY code",
      accountantFundBalance());
  }

  public List<Map<String, Object>> findPayrollSettings() {
    return jdbcTemplate.queryForList("SELECT * FROM gcmb.payroll_setting ORDER BY effective_from DESC");
  }

  public List<Map<String, Object>> findEmployeePayRates() {
    return jdbcTemplate.queryForList(
      "SELECT r.*,e.code,e.full_name FROM gcmb.employee_pay_rate r JOIN gcmb.employee e ON e.id=r.employee_id ORDER BY r.valid_from DESC");
  }

  public List<Map<String, Object>> findPayrollCalendar() {
    return jdbcTemplate.queryForList("SELECT * FROM gcmb.payroll_calendar ORDER BY work_date");
  }

  public List<Integer> findVerifiedCalendarYears() {
    return jdbcTemplate.queryForList("SELECT year FROM gcmb.payroll_calendar_year ORDER BY year", Integer.class);
  }

  public List<Map<String, Object>> findPeriodEntries(Object periodId) {
    return jdbcTemplate.queryForList("""
      SELECT e.*,n.code,n.full_name AS name,
        COALESCE((SELECT string_agg(b2.name, ', ' ORDER BY b2.name)
          FROM gcmb.branch_payroll_submission s JOIN gcmb.branch b2 ON b2.id=s.branch_id
          WHERE s.period_id=e.period_id AND s.employee_id=e.employee_id),b.name) AS submitted_branch_name,
        EXISTS(SELECT 1 FROM gcmb.salary_payment s WHERE s.payroll_entry_id=e.id AND s.status='PAID') AS paid,
        COALESCE((SELECT sum(a.amount) FROM gcmb.salary_advance a WHERE a.employee_id=e.employee_id AND a.period_id=e.period_id AND a.status='PAID'),0) AS advances
      FROM gcmb.payroll_entry e JOIN gcmb.employee n ON n.id=e.employee_id
      LEFT JOIN gcmb.branch b ON b.id=e.submitted_branch_id
      WHERE e.period_id=? ORDER BY n.code
      """, periodId);
  }

  public List<Map<String, Object>> findEntryComponents(Object payrollEntryId) {
    return jdbcTemplate.queryForList(
      "SELECT c.kind,c.amount,c.reason,c.branch_id,b.name AS branch_name,c.calculation_snapshot::text AS detail FROM gcmb.payroll_component c JOIN gcmb.branch b ON b.id=c.branch_id WHERE c.payroll_entry_id=? ORDER BY c.created_at,c.id",
      payrollEntryId);
  }

  public List<Map<String, Object>> findEntryPayments(Object payrollEntryId) {
    return jdbcTemplate.queryForList(
      "SELECT p.id,p.status,p.amount,p.paid_at,p.external_reference,p.failure_reason,e.original_name AS evidence_name "
        + "FROM gcmb.salary_payment p LEFT JOIN gcmb.salary_payment_evidence e ON e.payment_id=p.id "
        + "WHERE p.payroll_entry_id=? ORDER BY p.created_at",
      payrollEntryId);
  }

  // Nhân viên đã có phần lương từ một số cơ sở nhưng chưa đủ để tổng hợp bảng lương tháng.
  public List<Map<String, Object>> findPendingBranchSubmissions(Object periodId) {
    return jdbcTemplate.queryForList("""
      SELECT e.code,e.full_name AS name,s.branch_id,b.name AS branch_name,s.submitted_at
      FROM gcmb.branch_payroll_submission s JOIN gcmb.employee e ON e.id=s.employee_id
      JOIN gcmb.branch b ON b.id=s.branch_id WHERE s.period_id=? AND NOT EXISTS(
        SELECT 1 FROM gcmb.payroll_entry x WHERE x.period_id=s.period_id AND x.employee_id=s.employee_id
          AND x.submitted_branch_id IS NOT NULL)
      ORDER BY e.code,b.name
      """, periodId);
  }

  public List<Map<String, Object>> findPeriodAdvances(Object periodId) {
    return jdbcTemplate.queryForList(
      "SELECT a.*,e.code,e.full_name AS name FROM gcmb.salary_advance a JOIN gcmb.employee e ON e.id=a.employee_id WHERE a.period_id=? ORDER BY a.created_at DESC",
      periodId);
  }

  // ---- Ứng lương ----

  public BigDecimal sumApprovedAdjustments(Object periodId, UUID employeeId) {
    return jdbcTemplate.queryForObject(
      "SELECT COALESCE(sum(CASE WHEN c.kind='DEDUCTION' THEN -c.amount ELSE c.amount END),0) FROM gcmb.payroll_component c JOIN gcmb.payroll_entry e ON e.id=c.payroll_entry_id WHERE e.period_id=? AND e.employee_id=? AND c.kind IN ('ADDITION','DEDUCTION') AND c.reason NOT LIKE 'Thưởng trên 100 giờ%'",
      BigDecimal.class, periodId, employeeId);
  }

  public boolean advanceRequestExists(String idempotencyKey) {
    return !jdbcTemplate.queryForList("SELECT id FROM gcmb.salary_advance WHERE idempotency_key=?", idempotencyKey).isEmpty();
  }

  public boolean payrollAlreadySubmitted(Object periodId, UUID employeeId) {
    return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
      SELECT EXISTS(SELECT 1 FROM gcmb.payroll_entry
      WHERE period_id=? AND employee_id=? AND submitted_branch_id IS NOT NULL)
      OR EXISTS(SELECT 1 FROM gcmb.branch_payroll_submission WHERE period_id=? AND employee_id=?)
      """, Boolean.class, periodId, employeeId, periodId, employeeId));
  }

  public boolean finalSalaryPaid(Object periodId, UUID employeeId) {
    return !jdbcTemplate.queryForList(
      "SELECT s.id FROM gcmb.salary_payment s JOIN gcmb.payroll_entry e ON e.id=s.payroll_entry_id WHERE e.period_id=? AND e.employee_id=? AND s.status='PAID'",
      periodId, employeeId).isEmpty();
  }

  public List<UUID> findCurrentBranch(UUID employeeId) {
    return jdbcTemplate.queryForList(
      "SELECT branch_id FROM gcmb.employee_assignment WHERE employee_id=? AND valid_from<=CURRENT_TIMESTAMP AND (valid_to IS NULL OR valid_to>CURRENT_TIMESTAMP) ORDER BY valid_from DESC LIMIT 1",
      UUID.class, employeeId);
  }

  public void insertPaidAdvance(UUID advanceId, UUID employeeId, Object periodId, UUID branchId, BigDecimal amount,
      UUID processedBy, String externalReference, String idempotencyKey) {
    jdbcTemplate.update(
      "INSERT INTO gcmb.salary_advance(id,employee_id,period_id,branch_id,amount,status,paid_at,processed_by,external_reference,idempotency_key) VALUES (?,?,?,?,?,'PAID',CURRENT_TIMESTAMP,?,?,?)",
      advanceId, employeeId, periodId, branchId, amount, processedBy, externalReference, idempotencyKey);
  }

  public List<Map<String, Object>> findEntryNetAmount(Object periodId, UUID employeeId) {
    return jdbcTemplate.queryForList("SELECT id,net_amount FROM gcmb.payroll_entry WHERE period_id=? AND employee_id=?",
      periodId, employeeId);
  }

  public void deductAdvanceFromEntry(Object payrollEntryId, UUID advanceId, BigDecimal amount) {
    jdbcTemplate.update(
      "UPDATE gcmb.payroll_entry SET deduction_amount=deduction_amount+?,net_amount=net_amount-? WHERE id=?",
      amount, amount, payrollEntryId);
    jdbcTemplate.update("INSERT INTO gcmb.advance_application(advance_id,payroll_entry_id,amount) VALUES (?,?,?)",
      advanceId, payrollEntryId, amount);
  }

  // ---- Chi trả lương ----

  public void insertPaymentEvidence(UUID paymentId, String objectKey, String originalName, String mimeType,
      long byteSize, String sha256) {
    jdbcTemplate.update("""
      INSERT INTO gcmb.salary_payment_evidence(payment_id,object_key,original_name,mime_type,byte_size,sha256)
      VALUES (?,?,?,?,?,?)
      """, paymentId, objectKey, originalName, mimeType, byteSize, sha256);
  }

  public List<Map<String, Object>> findPaymentEvidence(UUID paymentId) {
    return jdbcTemplate.queryForList("SELECT * FROM gcmb.salary_payment_evidence WHERE payment_id=?", paymentId);
  }

  public List<Map<String, Object>> findEntry(Object periodId, UUID employeeId) {
    return jdbcTemplate.queryForList("SELECT * FROM gcmb.payroll_entry WHERE period_id=? AND employee_id=?",
      periodId, employeeId);
  }

  public boolean salaryPaymentRequestExists(String idempotencyKey) {
    return !jdbcTemplate.queryForList("SELECT id FROM gcmb.salary_payment WHERE idempotency_key=?", idempotencyKey).isEmpty();
  }

  public boolean entryAlreadyPaid(Object payrollEntryId) {
    return !jdbcTemplate.queryForList("SELECT id FROM gcmb.salary_payment WHERE payroll_entry_id=? AND status='PAID'",
      payrollEntryId).isEmpty();
  }

  public String findEmployeeName(UUID employeeId) {
    return jdbcTemplate.queryForObject("SELECT full_name FROM gcmb.employee WHERE id=?", String.class, employeeId);
  }

  public void insertSalaryPayment(UUID paymentId, Object payrollEntryId, BigDecimal amount, String status,
      Timestamp paidAt, String externalReference, String failureReason, UUID processedBy, String idempotencyKey) {
    jdbcTemplate.update(
      "INSERT INTO gcmb.salary_payment(id,payroll_entry_id,amount,status,paid_at,external_reference,failure_reason,processed_by,idempotency_key) VALUES (?,?,?,?,?,?,?,?,?)",
      paymentId, payrollEntryId, amount, status, paidAt, externalReference, failureReason, processedBy, idempotencyKey);
  }

  // ---- Ghi sổ quỹ kế toán ----

  public List<Map<String, Object>> lockActiveCentralFund(UUID accountId) {
    return jdbcTemplate.queryForList(
      "SELECT id,name FROM gcmb.financial_account WHERE id=? AND active AND kind='CENTRAL_FUND' FOR UPDATE", accountId);
  }

  public BigDecimal accountantFundBalance() {
    return AccountantFund.balance(jdbcTemplate);
  }

  public Map<String, Object> shareFinancialPeriod(LocalDate monthStart) {
    jdbcTemplate.update("INSERT INTO gcmb.financial_period(month,status) VALUES (?,'OPEN') ON CONFLICT(month) DO NOTHING",
      monthStart);
    return jdbcTemplate.queryForMap("SELECT id,status FROM gcmb.financial_period WHERE month=? FOR SHARE", monthStart);
  }

  // BR-83: chỉ ghi sổ khoản đã chi thực tế; advance=true liên kết salary_advance, ngược lại salary_payment.
  public void insertPayrollOutflow(UUID transactionId, Object periodId, LocalDate bookingDate, boolean advance,
      UUID sourceId, UUID actorId, UUID accountId, UUID branchId, BigDecimal amount) {
    String column = advance ? "salary_advance_id" : "salary_payment_id";
    jdbcTemplate.update(
      "INSERT INTO gcmb.financial_transaction(id,period_id,booking_date,occurred_at,kind," + column
        + ",reason,created_by,idempotency_key) VALUES (?,?,?,CURRENT_TIMESTAMP,?,?,?,?,?)",
      transactionId, periodId, bookingDate, advance ? "ADVANCE" : "SALARY", sourceId,
      advance ? "Ứng lương" : "Trả lương cuối kỳ", actorId, "PAYROLL-" + sourceId);
    jdbcTemplate.update(
      "INSERT INTO gcmb.financial_entry(transaction_id,account_id,branch_id,amount_signed) VALUES (?,?,?,?)",
      transactionId, accountId, branchId, amount.negate());
  }
}
