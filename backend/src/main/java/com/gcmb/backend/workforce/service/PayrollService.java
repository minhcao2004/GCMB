/** Author: HuyDV. UC-91..97: tính, duyệt, xuất lương, ứng và ghi nhận trả lương thực tế. */
package com.gcmb.backend.workforce.service;

import static com.gcmb.backend.finance.service.FinanceAccess.*;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.finance.service.FinanceAccess;
import com.gcmb.backend.finance.service.FinancialPeriodService;
import java.math.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class PayrollService {

  private final JdbcTemplate db;
  private final FinanceAccess access;
  private final ObjectMapper json;
  private final FinancialPeriodService periods;
  private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  public record BranchSettlement(UUID employeeId, BigDecimal bonus, BigDecimal penalty,
                                 String reason, BigDecimal reviewedTotal, String reviewedRevision) {}

  public List<Map<String, Object>> branchEmployees(Identity actor) {
    UUID branch = access.branch(actor, false);
    return db.queryForList("""
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
      """, branch);
  }

  public void branchHourly(Identity actor, Hourly input) {
    access.lock();
    UUID branch = access.branch(actor, true);
    if (input == null || input.employeeId() == null || input.effectiveFrom() == null) {
      throw bad("Vui lòng chọn nhân viên của cơ sở và ngày hiệu lực.");
    }
    boolean currentEmployee = branchEmployees(actor).stream()
        .anyMatch(e -> e.get("id").equals(input.employeeId()));
    if (!currentEmployee && !missingHistoricalRate(branch, input.employeeId(), input.effectiveFrom())) {
      throw bad("Chỉ bổ sung đơn giá công cũ cho nhân viên có công đi làm còn thiếu đơn giá tại cơ sở trong tháng đã kết thúc.");
    }
    BigDecimal rate = money(input.amount(), "Đơn giá giờ", true);
    // BR-67: không sửa ngược đơn giá đã dùng trong phần lương QLCS đã gửi.
    if (Boolean.TRUE.equals(db.queryForObject("""
      SELECT EXISTS(SELECT 1 FROM gcmb.branch_payroll_submission s
        JOIN gcmb.payroll_period p ON p.id=s.period_id
        WHERE s.branch_id=? AND s.employee_id=? AND p.month+interval '1 month'>?)
      OR EXISTS(SELECT 1 FROM gcmb.payroll_entry e JOIN gcmb.payroll_period p ON p.id=e.period_id
        WHERE e.submitted_branch_id=? AND e.employee_id=? AND p.month+interval '1 month'>?)
      """, Boolean.class, branch, input.employeeId(), input.effectiveFrom(),
      branch, input.employeeId(), input.effectiveFrom()))) {
      throw conflict("Đơn giá này ảnh hưởng tháng lương đã gửi. Vui lòng chọn ngày hiệu lực sau tháng đã chốt.");
    }
    db.update("""
      INSERT INTO gcmb.branch_employee_pay_rate VALUES (?,?,?,?)
      ON CONFLICT (branch_id,employee_id,effective_from) DO UPDATE SET hourly_rate=excluded.hourly_rate
      """, branch, input.employeeId(), input.effectiveFrom(), rate);
    access.audit(actor, branch, input.employeeId(), "SET_BRANCH_HOURLY_PAY", null, input);
  }

  // HuyDV | UC-91/95, BR-67: nghỉ/chuyển cơ sở không làm mất quyền quyết toán công cũ.
  private boolean missingHistoricalRate(UUID branch, UUID employee, LocalDate effective) {
    LocalDate month = effective.withDayOfMonth(1);
    if (!month.isBefore(LocalDate.now(ZONE).withDayOfMonth(1))) return false;
    return Boolean.TRUE.equals(db.queryForObject("""
      SELECT EXISTS(SELECT 1 FROM gcmb.attendance a
        JOIN gcmb.shift_assignment x ON x.id=a.assignment_id
        JOIN gcmb.scheduled_shift s ON s.id=x.shift_id
        WHERE x.employee_id=? AND s.branch_id=? AND x.status='CONFIRMED'
          AND s.status<>'CANCELLED' AND a.status='WORKED'
          AND s.work_date>=? AND s.work_date<? AND NOT EXISTS(
            SELECT 1 FROM gcmb.branch_employee_pay_rate r WHERE r.branch_id=s.branch_id
              AND r.employee_id=x.employee_id AND r.effective_from<=s.work_date))
      """, Boolean.class, employee, branch, effective, month.plusMonths(1)));
  }

  // HuyDV | UC-92, BR-67: công cũ vẫn được quyết toán khi nhân viên nghỉ/chuyển cơ sở.
  public List<Map<String, Object>> settlementEmployees(Identity actor, String value) {
    UUID branch = access.branch(actor, false);
    LocalDate m = month(value);
    return db.queryForList("""
      SELECT e.id,e.code,e.full_name AS name,e.status,e.bank_name,e.bank_account,e.bank_holder
      FROM gcmb.employee e WHERE EXISTS (
        SELECT 1 FROM gcmb.shift_assignment x JOIN gcmb.scheduled_shift s ON s.id=x.shift_id
        WHERE x.employee_id=e.id AND s.branch_id=? AND s.work_date>=? AND s.work_date<?
          AND x.status='CONFIRMED' AND s.status<>'CANCELLED')
      ORDER BY e.code
      """, branch, m, m.plusMonths(1));
  }

  // HuyDV | UC-92: lịch sử theo cơ sở, bao gồm bảng lương đã gửi trước V42.
  public List<Map<String, Object>> branchHistory(Identity actor, String value) {
    UUID branch = access.branch(actor, false);
    LocalDate m = month(value);
    return db.queryForList("""
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
      """, branch, m, branch, m);
  }

  public record BankDetails(UUID employeeId, String month, String bankName, String bankAccount, String bankHolder) {}

  // HuyDV | UC-97: nhận lương ngoài GCMB; lưu thông tin nhận, không thực hiện chuyển tiền.
  public void branchBank(Identity actor, BankDetails input) {
    access.lock();
    UUID branch = access.branch(actor, true);
    if (branchEmployees(actor).stream().noneMatch(e -> e.get("id").equals(input.employeeId()))) {
      requireSettlementEmployee(actor, input.month(), input.employeeId());
    }
    String name = text(input.bankName(), "Ngân hàng", true);
    String account = text(input.bankAccount(), "Số tài khoản", true).replace(" ", "");
    String holder = text(input.bankHolder(), "Chủ tài khoản", true);
    if (name.length() > 200 || holder.length() > 200 || !account.matches("[A-Za-z0-9]{3,60}")) {
      throw bad("Ngân hàng và chủ tài khoản tối đa 200 ký tự; số tài khoản gồm 3–60 chữ hoặc số.");
    }
    var affectedMonths = db.queryForList("""
      SELECT DISTINCT q.month FROM gcmb.payroll_entry e JOIN gcmb.payroll_period q ON q.id=e.period_id
      WHERE e.employee_id=? AND NOT EXISTS(
        SELECT 1 FROM gcmb.salary_payment p WHERE p.payroll_entry_id=e.id AND p.status='PAID')
      """, LocalDate.class, input.employeeId());
    for (LocalDate affectedMonth : affectedMonths) periods.requireOpen(affectedMonth, "cập nhật thông tin nhận lương");
    var before = db.queryForMap("SELECT bank_name,bank_account,bank_holder FROM gcmb.employee WHERE id=?", input.employeeId());
    db.update("UPDATE gcmb.employee SET bank_name=?,bank_account=?,bank_holder=?,version=version+1 WHERE id=?", name, account, holder, input.employeeId());
    // BR-77: không đổi thông tin lịch sử của khoản đã thanh toán.
    var periods = db.queryForList("""
      UPDATE gcmb.payroll_entry e SET bank_name_snapshot=?,bank_account_snapshot=?,bank_holder_snapshot=?
      WHERE employee_id=? AND NOT EXISTS(SELECT 1 FROM gcmb.salary_payment p WHERE p.payroll_entry_id=e.id AND p.status='PAID')
      RETURNING period_id
      """, UUID.class, name, account, holder, input.employeeId());
    for (UUID period : periods) db.update("UPDATE gcmb.payroll_period SET version=version+1 WHERE id=?", period);
    access.audit(actor, branch, input.employeeId(), "UPDATE_PAYROLL_BANK", before, input);
  }

  private void requireSettlementEmployee(Identity actor, String month, UUID employee) {
    if (employee == null || settlementEmployees(actor, month).stream()
        .noneMatch(e -> employee.equals(e.get("id")))) {
      throw bad("Vui lòng chọn nhân viên có công tại cơ sở trong tháng này.");
    }
  }

  // HuyDV | UC-91/96, BR-67/226/227: dùng chung đơn giá QLCS theo ngày làm việc.
  private Map<String, Object> branchEarnings(UUID employee, List<Map<String, Object>> rows,
                                            boolean accrued) {
    BigDecimal wageMinutes = BigDecimal.ZERO;
    long minutes = 0;
    for (var row : rows) {
      if ("ABSENT".equals(row.get("status"))) continue;
      Instant start = ((Timestamp) row.get("starts_at")).toInstant();
      Instant end = ((Timestamp) row.get("ends_at")).toInstant();
      // Chỉ ca đã hoàn thành mới đủ điều kiện ứng, dù QLCS đã tích đi làm từ đầu ca.
      if (accrued && end.isAfter(Instant.now())) continue;
      if (!"WORKED".equals(row.get("status")) || !end.isAfter(start)) {
        throw conflict("Bản ghi công chưa hợp lệ. Vui lòng kiểm tra trước khi tính lương.");
      }
      LocalDate day = ((java.sql.Date) row.get("work_date")).toLocalDate();
      var rate = db.queryForList("""
        SELECT hourly_rate FROM gcmb.branch_employee_pay_rate
        WHERE branch_id=? AND employee_id=? AND effective_from<=?
        ORDER BY effective_from DESC LIMIT 1
        """, BigDecimal.class, row.get("branch_id"), employee, day);
      if (rate.isEmpty()) throw conflict(
          "Chưa thể tính lương vì thiếu đơn giá giờ áp dụng cho ngày làm việc " + day
          + ". Vui lòng bổ sung cấu hình phù hợp."); // MSG107, áp dụng luồng đơn giá QLCS.
      ZoneId zone = ZoneId.of((String) row.get("timezone"));
      boolean night = start.atZone(zone).toLocalTime().equals(LocalTime.MIDNIGHT)
          && end.atZone(zone).toLocalTime().equals(LocalTime.of(7, 0));
      long duration = Duration.between(start, end).toMinutes();
      BigDecimal hourly = rate.get(0).add(night ? BigDecimal.valueOf(3000) : BigDecimal.ZERO);
      // BR-67: lưu cả đơn giá/ngày và phụ cấp đã áp dụng trong snapshot chốt lương.
      row.put("applied_hourly_rate", rate.get(0));
      row.put("night_hourly_premium", night ? 3000 : 0);
      row.put("paid_minutes", duration);
      wageMinutes = wageMinutes.add(hourly.multiply(BigDecimal.valueOf(duration)));
      minutes += duration;
    }
    return Map.of("wage", PayrollCalculator.rounded(wageMinutes), "minutes", minutes);
  }

  public Map<String, Object> branchSettlement(Identity actor, String value, BranchSettlement input, boolean send) {
    if (send) access.lock();
    UUID branch = access.branch(actor, send);
    LocalDate m = month(value);
    if (send) periods.requireOpen(m, "chốt dữ liệu payroll tại cơ sở");
    if (!m.isBefore(LocalDate.now(ZONE).withDayOfMonth(1))) throw bad("Chỉ chốt lương cho tháng đã kết thúc.");
    requireSettlementEmployee(actor, value, input.employeeId());
    // BR-64 / MSG87: tất cả công của tháng phải được chốt trước khi tổng hợp.
    var allRows = work(input.employeeId(), m, true);
    var rows = allRows.stream().filter(r -> branch.equals(r.get("branch_id"))).toList();
    var earnings = branchEarnings(input.employeeId(), rows, false);
    BigDecimal wage = (BigDecimal) earnings.get("wage");
    long minutes = (long) earnings.get("minutes");
    var branches = allRows.stream().map(r -> (UUID) r.get("branch_id")).distinct().toList();
    BigDecimal bonus = money(input.bonus(), "Thưởng thêm", false);
    BigDecimal penalty = money(input.penalty(), "Phạt", false);
    String reason = text(input.reason(), "Lý do điều chỉnh", bonus.signum() > 0 || penalty.signum() > 0);
    var period = period(m, send);
    boolean singleBranch = branches.size() == 1;
    BigDecimal reward = singleBranch && minutes > 6000 ? BigDecimal.valueOf(100000) : BigDecimal.ZERO;
    BigDecimal advance = !singleBranch || period == null ? BigDecimal.ZERO
        : advances(input.employeeId(), (UUID) period.get("id"));
    BigDecimal net = wage.add(reward).add(bonus).subtract(penalty).subtract(advance);
    if (net.signum() < 0) throw bad("Tiền phạt và tạm ứng vượt tổng lương.");
    var result = new LinkedHashMap<String, Object>();
    result.putAll(Map.of("minutes", minutes, "wage", wage, "reward", reward,
        "bonus", bonus, "penalty", penalty, "advance", advance, "net", net));
    result.put("multipleBranches", !singleBranch);
    result.put("attendanceNotes", rows.stream()
        .filter(row -> row.get("exception_note") instanceof String note && !note.isBlank())
        .map(row -> Map.of("date", row.get("work_date").toString(),
            "status", row.get("status"), "note", row.get("exception_note"))).toList());
    String revision = UUID.nameUUIDFromBytes(json.writeValueAsBytes(List.of(rows, result))).toString();
    result.put("revision", revision);
    if (!send) return result;
    if (input.reviewedTotal() == null || net.compareTo(input.reviewedTotal()) != 0
        || !revision.equals(input.reviewedRevision())) {
      throw conflict("Số liệu đã thay đổi. Vui lòng xem trước lại trước khi chốt lương.");
    }
    if (!"DRAFT".equals(period.get("status"))) throw conflict("Bảng lương tháng đã khóa.");
    if (Boolean.TRUE.equals(db.queryForObject("""
      SELECT EXISTS(SELECT 1 FROM gcmb.payroll_entry WHERE period_id=? AND employee_id=?
        AND submitted_branch_id IS NOT NULL)
      """, Boolean.class, period.get("id"), input.employeeId()))) {
      throw conflict("Bảng lương tháng đã được tổng hợp và gửi kế toán, không thể chốt lại.");
    }
    // UC-95: chỉ được sửa phần đã gửi khi còn chờ cơ sở khác; luôn giữ audit trước/sau.
    var before = db.queryForList("""
      SELECT period_id,employee_id,branch_id,minutes,wage,bonus,penalty,reason,
        submitted_by,submitted_at,source_snapshot::text AS source_snapshot
      FROM gcmb.branch_payroll_submission WHERE period_id=? AND employee_id=? AND branch_id=?
      """, period.get("id"), input.employeeId(), branch);
    db.update("""
      INSERT INTO gcmb.branch_payroll_submission
        (period_id,employee_id,branch_id,minutes,wage,bonus,penalty,reason,source_snapshot,submitted_by)
      VALUES (?,?,?,?,?,?,?,?,CAST(? AS jsonb),?)
      ON CONFLICT (period_id,employee_id,branch_id) DO UPDATE SET minutes=excluded.minutes,
        wage=excluded.wage,bonus=excluded.bonus,penalty=excluded.penalty,reason=excluded.reason,
        source_snapshot=excluded.source_snapshot,submitted_by=excluded.submitted_by,submitted_at=CURRENT_TIMESTAMP
      """, period.get("id"), input.employeeId(), branch, minutes, wage, bonus, penalty, reason,
      json.writeValueAsString(Map.of("attendance", rows, "calculation", result)), actor.id());
    var parts = db.queryForList("""
      SELECT period_id,employee_id,branch_id,minutes,wage,bonus,penalty,reason,
        submitted_by,submitted_at,source_snapshot::text AS source_snapshot
      FROM gcmb.branch_payroll_submission WHERE period_id=? AND employee_id=? ORDER BY branch_id
      """, period.get("id"), input.employeeId());
    boolean complete = branches.stream().allMatch(b -> parts.stream().anyMatch(p -> b.equals(p.get("branch_id"))));
    if (complete) finalizeBranchPayroll(period, input.employeeId(), branch, parts);
    increment(period);
    access.audit(actor, branch, input.employeeId(), "SUBMIT_BRANCH_PAYROLL", before, result);
    result.put("message", complete ? "Đã chốt lương và gửi kế toán."
        : "Đã lưu phần lương cơ sở. Chờ các cơ sở còn lại chốt để tổng hợp lương tháng.");
    return result;
  }

  // HuyDV | BR-75/76/77: một bảng lương tháng, thưởng mốc giờ và tạm ứng chỉ tính một lần.
  private void finalizeBranchPayroll(Map<String, Object> period, UUID employee, UUID branch,
                                    List<Map<String, Object>> parts) {
    BigDecimal gross = BigDecimal.ZERO;
    BigDecimal penalty = BigDecimal.ZERO;
    long minutes = 0;
    for (var part : parts) {
      gross = gross.add((BigDecimal) part.get("wage")).add((BigDecimal) part.get("bonus"));
      penalty = penalty.add((BigDecimal) part.get("penalty"));
      minutes += ((Number) part.get("minutes")).longValue();
    }
    BigDecimal reward = minutes > 6000 ? BigDecimal.valueOf(100000) : BigDecimal.ZERO;
    gross = gross.add(reward);
    BigDecimal deduction = penalty.add(advances(employee, (UUID) period.get("id")));
    BigDecimal net = gross.subtract(deduction);
    if (net.signum() < 0) throw conflict("Tổng tiền phạt và tạm ứng vượt lương tháng. Cần điều chỉnh phần phạt trước khi chốt.");
    UUID entry = db.queryForObject("""
      INSERT INTO gcmb.payroll_entry(period_id,employee_id,gross_amount,deduction_amount,net_amount,
        submitted_branch_id,bank_name_snapshot,bank_account_snapshot,bank_holder_snapshot)
      SELECT ?,id,?,?,?,?,bank_name,bank_account,bank_holder FROM gcmb.employee WHERE id=?
      ON CONFLICT (period_id,employee_id) DO UPDATE SET gross_amount=excluded.gross_amount,
        deduction_amount=excluded.deduction_amount,net_amount=excluded.net_amount,
        submitted_branch_id=excluded.submitted_branch_id,bank_name_snapshot=excluded.bank_name_snapshot,
        bank_account_snapshot=excluded.bank_account_snapshot,bank_holder_snapshot=excluded.bank_holder_snapshot
      RETURNING id
      """, UUID.class, period.get("id"), gross, deduction, net, branch, employee);
    db.update("DELETE FROM gcmb.payroll_component WHERE payroll_entry_id=?", entry);
    db.update("DELETE FROM gcmb.advance_application WHERE payroll_entry_id=?", entry);
    for (var part : parts) {
      UUID source = (UUID) part.get("branch_id");
      var snapshot = json.readTree((String) part.get("source_snapshot"));
      component(entry, source, "BASE", (BigDecimal) part.get("wage"), "Lương theo ca", snapshot);
      if (((BigDecimal) part.get("bonus")).signum() > 0)
        component(entry, source, "ADDITION", (BigDecimal) part.get("bonus"), (String) part.get("reason"), snapshot);
      if (((BigDecimal) part.get("penalty")).signum() > 0)
        component(entry, source, "DEDUCTION", (BigDecimal) part.get("penalty"), (String) part.get("reason"), snapshot);
    }
    if (reward.signum() > 0) component(entry, branch, "ADDITION", reward, "Thưởng trên 100 giờ (toàn tháng)", Map.of("minutes", minutes));
    db.update("""
      INSERT INTO gcmb.advance_application(advance_id,payroll_entry_id,amount)
      SELECT id,?,amount FROM gcmb.salary_advance WHERE employee_id=? AND period_id=? AND status='PAID'
      """, entry, employee, period.get("id"));
  }

  public PayrollService(JdbcTemplate db, FinanceAccess access, ObjectMapper json, FinancialPeriodService periods) {
    this.db = db;
    this.access = access;
    this.json = json;
    this.periods = periods;
  }

  public record Setting(
    LocalDate effectiveFrom,
    BigDecimal normal,
    BigDecimal rest,
    BigDecimal holiday,
    BigDecimal night
  ) {}

  public record Hourly(
    UUID employeeId,
    LocalDate effectiveFrom,
    BigDecimal amount,
    String reason
  ) {}

  public record CalendarDay(LocalDate date, String kind, String description) {}

  public record Calendar(int year, List<CalendarDay> days, boolean verified) {}

  public record Calculate(
    List<UUID> employees,
    long version,
    boolean confirmed,
    String previewRevision
  ) {}

  // UC-91 steps 6/7: preview does not write payroll; confirmation compares the reviewed amounts.
  public Map<String, Object> preview(Identity actor, String value, Calculate input) {
    throw legacyPayrollDenied(actor);
  }

  public record Adjustment(
    UUID employeeId,
    BigDecimal bonus,
    BigDecimal deduction,
    String reason,
    long version
  ) {}

  public record Confirm(long version, boolean reviewed) {}

  public record Pay(
    UUID employeeId,
    UUID accountId,
    BigDecimal amount,
    String result,
    String reference,
    String requestId
  ) {}

  public record Payments(long version, List<Pay> payments) {}

  private LocalDate month(String value) {
    try {
      var m = YearMonth.parse(value);
      if (m.getYear() < 2000 || m.getYear() > 9998) throw new DateTimeException("year");
      return m.atDay(1);
    } catch (DateTimeException e) {
      throw bad("Tháng phải có định dạng YYYY-MM.");
    }
  }

  private Map<String, Object> period(LocalDate month, boolean create) {
    if (create) {
      periods.requireOpen(month, "tạo hoặc cập nhật bảng lương");
      db.update(
        "INSERT INTO gcmb.payroll_period(month,status) VALUES (?,'DRAFT') ON CONFLICT(month) DO NOTHING",
        month
      );
    }
    var rows = db.queryForList("SELECT * FROM gcmb.payroll_period WHERE month=?", month);
    return rows.isEmpty() ? null : rows.get(0);
  }

  private Map<String, Object> draft(LocalDate month, long version) {
    var p = period(month, true);
    if (!"DRAFT".equals(p.get("status"))) throw conflict(
      "Bảng lương đã xác nhận nên không thể sửa trực tiếp."
    );
    if (((Number) p.get("version")).longValue() != version) throw conflict(
      "Bảng lương đã thay đổi. Vui lòng tải lại và kiểm tra."
    );
    return p;
  }

  public Map<String, Object> options(Identity actor) {
    access.accountant(actor);
    return Map.of(
      "employees",
      db.queryForList("SELECT id,code,full_name AS name,status FROM gcmb.employee ORDER BY code"),
      "funds",
      db.queryForList(
        "SELECT a.id,a.name,b.balance FROM gcmb.financial_account a JOIN gcmb.v_account_balance b ON b.id=a.id WHERE a.kind='CENTRAL_FUND' AND a.active ORDER BY a.code"
      ),
      "settings",
      db.queryForList("SELECT * FROM gcmb.payroll_setting ORDER BY effective_from DESC"),
      "rates",
      db.queryForList(
        "SELECT r.*,e.code,e.full_name FROM gcmb.employee_pay_rate r JOIN gcmb.employee e ON e.id=r.employee_id ORDER BY r.valid_from DESC"
      ),
      "calendar",
      db.queryForList("SELECT * FROM gcmb.payroll_calendar ORDER BY work_date"),
      "calendarYears",
      db.queryForList("SELECT year FROM gcmb.payroll_calendar_year ORDER BY year", Integer.class)
    );
  }

  public void setting(Identity actor, Setting input) {
    throw legacyPayrollDenied(actor);
  }

  public void hourly(Identity actor, Hourly input) {
    throw legacyPayrollDenied(actor);
  }

  public void calendar(Identity actor, Calendar input) {
    throw legacyPayrollDenied(actor);
  }

  public Map<String, Object> view(Identity actor, String value) {
    access.accountant(actor);
    LocalDate m = month(value);
    var p = period(m, false);
    if (p == null) return Map.of(
      "month",
      value,
      "status",
      "DRAFT",
      "version",
      0,
      "rows",
      List.of(),
      "totalDue",
      0,
      "advances",
      List.of()
    );
    var rows = db.queryForList(
      """
      SELECT e.*,n.code,n.full_name AS name,
        COALESCE((SELECT string_agg(b2.name, ', ' ORDER BY b2.name)
          FROM gcmb.branch_payroll_submission s JOIN gcmb.branch b2 ON b2.id=s.branch_id
          WHERE s.period_id=e.period_id AND s.employee_id=e.employee_id),b.name) AS submitted_branch_name,
        EXISTS(SELECT 1 FROM gcmb.salary_payment s WHERE s.payroll_entry_id=e.id AND s.status='PAID') AS paid,
        COALESCE((SELECT sum(a.amount) FROM gcmb.salary_advance a WHERE a.employee_id=e.employee_id AND a.period_id=e.period_id AND a.status='PAID'),0) AS advances
      FROM gcmb.payroll_entry e JOIN gcmb.employee n ON n.id=e.employee_id
      LEFT JOIN gcmb.branch b ON b.id=e.submitted_branch_id
      WHERE e.period_id=? ORDER BY n.code
      """,
      p.get("id")
    );
    for (var row : rows) {
      row.put(
        "components",
        db.queryForList(
          "SELECT c.kind,c.amount,c.reason,b.name AS branch_name,c.calculation_snapshot::text AS detail FROM gcmb.payroll_component c JOIN gcmb.branch b ON b.id=c.branch_id WHERE c.payroll_entry_id=? ORDER BY c.created_at,c.id",
          row.get("id")
        )
      );
      row.put(
        "payments",
        db.queryForList(
          "SELECT status,amount,paid_at,external_reference,failure_reason FROM gcmb.salary_payment WHERE payroll_entry_id=? ORDER BY created_at",
          row.get("id")
        )
      );
    }
    p.put("pendingBranches", db.queryForList("""
      SELECT e.code,e.full_name AS name,b.name AS branch_name,s.submitted_at
      FROM gcmb.branch_payroll_submission s JOIN gcmb.employee e ON e.id=s.employee_id
      JOIN gcmb.branch b ON b.id=s.branch_id WHERE s.period_id=? AND NOT EXISTS(
        SELECT 1 FROM gcmb.payroll_entry x WHERE x.period_id=s.period_id AND x.employee_id=s.employee_id
          AND x.submitted_branch_id IS NOT NULL)
      ORDER BY e.code,b.name
      """, p.get("id")));
    p.put("rows", rows);
    p.put(
      "totalDue",
      rows
        .stream()
        .filter(r -> !Boolean.TRUE.equals(r.get("paid")))
        .map(r -> (BigDecimal) r.get("net_amount"))
        .reduce(BigDecimal.ZERO, BigDecimal::add)
    );
    p.put(
      "advances",
      db.queryForList(
        "SELECT a.*,e.code,e.full_name AS name FROM gcmb.salary_advance a JOIN gcmb.employee e ON e.id=a.employee_id WHERE a.period_id=? ORDER BY a.created_at DESC",
        p.get("id")
      )
    );
    return p;
  }

  private List<Map<String, Object>> work(UUID employee, LocalDate m, boolean closed) {
    var rows = db.queryForList(
      """
      SELECT a.*,s.starts_at,s.ends_at,s.branch_id,b.timezone,p.status AS period_status,s.work_date
      FROM gcmb.attendance a JOIN gcmb.shift_assignment x ON x.id=a.assignment_id
      JOIN gcmb.scheduled_shift s ON s.id=x.shift_id JOIN gcmb.branch b ON b.id=s.branch_id
      JOIN gcmb.attendance_period p ON p.id=a.period_id
      WHERE x.employee_id=? AND s.work_date>=? AND s.work_date<?
        AND x.status='CONFIRMED' AND s.status<>'CANCELLED' ORDER BY s.starts_at
      """,
      employee,
      m,
      m.plusMonths(1)
    );
    if (closed) {
      Boolean missing = db.queryForObject(
        """
        SELECT EXISTS(SELECT 1 FROM gcmb.shift_assignment x JOIN gcmb.scheduled_shift s ON s.id=x.shift_id
          LEFT JOIN gcmb.attendance a ON a.assignment_id=x.id LEFT JOIN gcmb.attendance_period p ON p.id=a.period_id
          WHERE x.employee_id=? AND s.work_date>=? AND s.work_date<? AND x.status='CONFIRMED' AND s.status<>'CANCELLED'
            AND (a.id IS NULL OR p.status<>'CLOSED'))
        """,
        Boolean.class,
        employee,
        m,
        m.plusMonths(1)
      );
      if (
        rows.isEmpty() ||
        Boolean.TRUE.equals(missing) ||
        rows.stream().anyMatch(r -> !"CLOSED".equals(r.get("period_status")))
      ) throw conflict(
        "Chưa thể tính lương tháng " + YearMonth.from(m) + " vì bảng công chưa được chốt."
      ); // MSG87 / BR-64.
    }
    return rows;
  }

  private Map<String, BigDecimal> earned(UUID employee, LocalDate m, boolean closed) {
    var totals = new LinkedHashMap<String, BigDecimal>();
    var cache = new HashMap<LocalDate, PayrollCalculator.Policy>();
    var intervals = new ArrayList<Instant[]>();
    for (var row : work(employee, m, closed)) {
      if ("ABSENT".equals(row.get("status"))) continue;
      if (row.get("actual_in") == null || row.get("actual_out") == null) {
        if (closed) throw conflict("Bản ghi công thiếu giờ thực tế.");
        continue;
      }
      Instant in = ((Timestamp) row.get("actual_in")).toInstant(),
        out = ((Timestamp) row.get("actual_out")).toInstant();
      if (!closed && out.isAfter(Instant.now())) continue;
      if (
        !out.isAfter(in) ||
        Duration.between(in, out).toMinutes() > 2880 ||
        in.getEpochSecond() % 60 != 0 ||
        out.getEpochSecond() % 60 != 0
      ) throw conflict("Giờ công không hợp lệ.");
      if (intervals.stream().anyMatch(r -> in.isBefore(r[1]) && out.isAfter(r[0]))) throw conflict(
        "Các khoảng công thực tế bị trùng nhau. Cần kiểm tra công trước khi tính lương."
      );
      intervals.add(new Instant[] { in, out });
      var work = new PayrollCalculator.Work(
        in,
        out,
        ((Timestamp) row.get("starts_at")).toInstant(),
        ((Timestamp) row.get("ends_at")).toInstant(),
        ZoneId.of((String) row.get("timezone"))
      );
      var amount = PayrollCalculator.calculate(work, date ->
        cache.computeIfAbsent(date, d -> policy(employee, d))
      );
      amount.forEach((k, v) -> totals.merge(k, v, BigDecimal::add));
    }
    totals.replaceAll((k, v) -> PayrollCalculator.rounded(v));
    return totals;
  }

  private PayrollCalculator.Policy policy(UUID employee, LocalDate date) {
    var hourly = db.queryForList(
      "SELECT hourly_rate FROM gcmb.employee_pay_rate WHERE employee_id=? AND valid_from<=? AND (valid_to IS NULL OR valid_to>?)",
      BigDecimal.class,
      employee,
      date,
      date
    );
    var settings = db.queryForList(
      "SELECT * FROM gcmb.payroll_setting WHERE effective_from<=? ORDER BY effective_from DESC LIMIT 1",
      date
    );
    if (hourly.size() != 1 || settings.isEmpty()) throw conflict(
      "Chưa thể tính lương vì thiếu đơn giá giờ hoặc hệ số áp dụng cho ngày làm việc " +
        date +
        ". Vui lòng bổ sung cấu hình phù hợp."
    ); // MSG107.
    if (
      !Boolean.TRUE.equals(
        db.queryForObject(
          "SELECT EXISTS(SELECT 1 FROM gcmb.payroll_calendar_year WHERE year=?)",
          Boolean.class,
          date.getYear()
        )
      )
    ) throw conflict(
      "Chưa xác nhận lịch ngày lễ Nhà nước năm " +
        date.getYear() +
        ". Vui lòng kiểm tra lịch trước khi tính lương."
    ); // BR-225.
    var days = db.queryForList(
      "SELECT day_kind FROM gcmb.payroll_calendar WHERE work_date=?",
      String.class,
      date
    );
    var s = settings.get(0);
    return new PayrollCalculator.Policy(
      new PayrollCalculator.Rates(
        hourly.get(0),
        (BigDecimal) s.get("normal_multiplier"),
        (BigDecimal) s.get("rest_multiplier"),
        (BigDecimal) s.get("holiday_multiplier"),
        (BigDecimal) s.get("night_rate")
      ),
      days.isEmpty() ? "NORMAL" : days.get(0)
    );
  }

  public Map<String, Object> calculate(Identity actor, String value, Calculate input) {
    throw legacyPayrollDenied(actor);
  }

  private void rebuild(
    Identity actor,
    Map<String, Object> p,
    UUID employee,
    LocalDate m,
    Adjustment adjustment
  ) {
    if (Boolean.TRUE.equals(db.queryForObject("""
      SELECT EXISTS(SELECT 1 FROM gcmb.branch_payroll_submission WHERE period_id=? AND employee_id=?)
      """, Boolean.class, p.get("id"), employee))) {
      throw conflict("QLCS đã gửi phần lương của nhân viên. Không thể tính lại bằng luồng kế toán cũ.");
    }
    var amounts = earned(employee, m, true);
    var old = db.queryForList(
      "SELECT * FROM gcmb.payroll_entry WHERE period_id=? AND employee_id=?",
      p.get("id"),
      employee
    );
    UUID id = old.isEmpty() ? UUID.randomUUID() : (UUID) old.get(0).get("id");
    if (!old.isEmpty() && old.get(0).get("submitted_branch_id") != null) {
      throw conflict("Bảng lương nhân viên đã được QLCS chốt và gửi.");
    }
    BigDecimal bonus = BigDecimal.ZERO,
      deduction = BigDecimal.ZERO;
    String reason = "";
    if (adjustment != null) {
      bonus = money(adjustment.bonus(), "Thưởng", false);
      deduction = money(adjustment.deduction(), "Khấu trừ đã duyệt", false);
      reason = text(adjustment.reason(), "Lý do điều chỉnh đã duyệt", true);
    } else if (!old.isEmpty()) {
      for (var c : db.queryForList(
        "SELECT kind,amount,reason FROM gcmb.payroll_component WHERE payroll_entry_id=? AND kind IN ('ADDITION','DEDUCTION')",
        id
      )) {
        if ("ADDITION".equals(c.get("kind"))) bonus = bonus.add((BigDecimal) c.get("amount"));
        else deduction = deduction.add((BigDecimal) c.get("amount"));
        reason = (String) c.get("reason");
      }
    }
    BigDecimal gross = amounts.values().stream().reduce(bonus, BigDecimal::add),
      advance = advances(employee, (UUID) p.get("id"));
    BigDecimal deductions = deduction.add(advance),
      net = gross.subtract(deductions);
    if (net.signum() < 0) throw conflict(
      "Tổng khấu trừ và ứng lương vượt lương đã kiếm được. Cần kiểm tra trước khi ghi nhận."
    );
    var e = db.queryForMap(
      "SELECT bank_name,bank_account,bank_holder FROM gcmb.employee WHERE id=?",
      employee
    );
    db.update(
      """
      INSERT INTO gcmb.payroll_entry(id,period_id,employee_id,gross_amount,deduction_amount,net_amount,bank_name_snapshot,bank_account_snapshot,bank_holder_snapshot)
      VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT(period_id,employee_id) DO UPDATE SET gross_amount=excluded.gross_amount,deduction_amount=excluded.deduction_amount,net_amount=excluded.net_amount,
        bank_name_snapshot=excluded.bank_name_snapshot,bank_account_snapshot=excluded.bank_account_snapshot,bank_holder_snapshot=excluded.bank_holder_snapshot
      """,
      id,
      p.get("id"),
      employee,
      gross,
      deductions,
      net,
      e.get("bank_name"),
      e.get("bank_account"),
      e.get("bank_holder")
    );
    db.update("DELETE FROM gcmb.payroll_component WHERE payroll_entry_id=?", id);
    var work = work(employee, m, true);
    UUID branch = (UUID) work.get(0).get("branch_id");
    // BR-67/210: keep each attendance/branch/date/rate; distribute rounding so the component total is rounded once.
    var running = new HashMap<String, BigDecimal>();
    var policies = new HashMap<LocalDate, PayrollCalculator.Policy>();
    for (var row : work) {
      if ("ABSENT".equals(row.get("status"))) continue;
      var w = new PayrollCalculator.Work(
        ((Timestamp) row.get("actual_in")).toInstant(),
        ((Timestamp) row.get("actual_out")).toInstant(),
        ((Timestamp) row.get("starts_at")).toInstant(),
        ((Timestamp) row.get("ends_at")).toInstant(),
        ZoneId.of((String) row.get("timezone"))
      );
      for (var segment : PayrollCalculator.segments(w, d ->
        policies.computeIfAbsent(d, date -> policy(employee, date))
      )) {
        BigDecimal before = running.getOrDefault(segment.kind(), BigDecimal.ZERO),
          after = before.add(segment.minuteAmount());
        BigDecimal amount = PayrollCalculator.rounded(after).subtract(
          PayrollCalculator.rounded(before)
        );
        running.put(segment.kind(), after);
        String kind = Set.of("REST", "HOLIDAY").contains(segment.kind())
          ? "OVERTIME"
          : segment.kind();
        UUID payRate = db.queryForObject(
          "SELECT id FROM gcmb.employee_pay_rate WHERE employee_id=? AND valid_from<=? AND (valid_to IS NULL OR valid_to>?)",
          UUID.class,
          employee,
          segment.date(),
          segment.date()
        );
        var snapshot = Map.of(
          "work",
          row,
          "segment",
          segment,
          "policy",
          policies.get(segment.date()),
          "rounding",
          "HALF_UP_COMPONENT_TOTAL"
        );
        db.update(
          "INSERT INTO gcmb.payroll_component(payroll_entry_id,branch_id,attendance_id,pay_rate_id,kind,minutes,hourly_rate_snapshot,multiplier,amount,calculation_snapshot,reason) VALUES (?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),?)",
          id,
          row.get("branch_id"),
          row.get("id"),
          payRate,
          kind,
          segment.minutes(),
          segment.hourly(),
          segment.multiplier(),
          amount,
          json.writeValueAsString(snapshot),
          segment.kind()
        );
      }
    }
    if (bonus.signum() > 0) component(
      id,
      branch,
      "ADDITION",
      bonus,
      reason,
      Map.of("approvedBy", actor.id())
    );
    if (deduction.signum() > 0) component(
      id,
      branch,
      "DEDUCTION",
      deduction,
      reason,
      Map.of("approvedBy", actor.id())
    );
    db.update("DELETE FROM gcmb.advance_application WHERE payroll_entry_id=?", id);
    db.update(
      "INSERT INTO gcmb.advance_application(advance_id,payroll_entry_id,amount) SELECT id,?,amount FROM gcmb.salary_advance WHERE employee_id=? AND period_id=? AND status='PAID'",
      id,
      employee,
      p.get("id")
    ); // BR-75 unique advance_id.
  }

  private void component(
    UUID id,
    UUID branch,
    String kind,
    BigDecimal amount,
    String reason,
    Object snapshot
  ) {
    db.update(
      "INSERT INTO gcmb.payroll_component(payroll_entry_id,branch_id,kind,minutes,hourly_rate_snapshot,multiplier,amount,calculation_snapshot,reason) VALUES (?,?,?,0,0,1,?,CAST(? AS jsonb),?)",
      id,
      branch,
      kind,
      amount,
      json.writeValueAsString(snapshot),
      reason
    );
  }

  private BigDecimal advances(UUID employee, UUID period) {
    return db.queryForObject(
      "SELECT COALESCE(sum(amount),0) FROM gcmb.salary_advance WHERE employee_id=? AND period_id=? AND status='PAID'",
      BigDecimal.class,
      employee,
      period
    );
  }

  private void increment(Map<String, Object> p) {
    db.update("UPDATE gcmb.payroll_period SET version=version+1 WHERE id=?", p.get("id"));
  }

  public Map<String, Object> adjust(Identity actor, String value, Adjustment input) {
    throw legacyPayrollDenied(actor);
  }

  public Map<String, Object> confirm(Identity actor, String value, Confirm input) {
    throw legacyPayrollDenied(actor);
  }

  // HuyDV | UC-91/94/95 theo phân công đã chốt: kế toán chỉ tiếp nhận và ghi nhận chi trả.
  private com.gcmb.backend.auth.exception.AuthFailure legacyPayrollDenied(Identity actor) {
    access.accountant(actor);
    return new com.gcmb.backend.auth.exception.AuthFailure(403, "form",
        "QLCS đặt đơn giá và chốt lương tại cơ sở. Kế toán chỉ tiếp nhận bảng lương đã gửi, xuất và ghi nhận thanh toán.");
  }

  public Map<String, Object> available(Identity actor, String value, UUID employee) {
    access.accountant(actor);
    var p = period(month(value), false);
    var accrued = branchEarnings(employee, work(employee, month(value), false), true);
    BigDecimal earned = (BigDecimal) accrued.get("wage");
    if ((long) accrued.get("minutes") > 6000) earned = earned.add(BigDecimal.valueOf(100000));
    if (p != null) {
      BigDecimal adjustment = db.queryForObject(
        "SELECT COALESCE(sum(CASE WHEN c.kind='DEDUCTION' THEN -c.amount ELSE c.amount END),0) FROM gcmb.payroll_component c JOIN gcmb.payroll_entry e ON e.id=c.payroll_entry_id WHERE e.period_id=? AND e.employee_id=? AND c.kind IN ('ADDITION','DEDUCTION') AND c.reason NOT LIKE 'Thưởng trên 100 giờ%'",
        BigDecimal.class,
        p.get("id"),
        employee
      );
      earned = earned.add(adjustment);
    }
    BigDecimal paid = p == null ? BigDecimal.ZERO : advances(employee, (UUID) p.get("id"));
    return Map.of(
      "earned",
      earned,
      "paidAdvances",
      paid,
      "available",
      earned.subtract(paid).max(BigDecimal.ZERO)
    );
  }

  public Map<String, Object> advance(Identity actor, String value, Pay input) {
    access.accountant(actor);
    access.lock();
    LocalDate m = month(value);
    periods.requireOpen(m, "ghi nhận ứng lương cho kỳ này");
    var p = period(m, true);
    validatePay(input);
    var repeated = db.queryForList(
      "SELECT id FROM gcmb.salary_advance WHERE idempotency_key=?",
      input.requestId()
    );
    if (!repeated.isEmpty()) throw conflict(
      "Yêu cầu ứng lương này đã được ghi nhận. Vui lòng tải lại lịch sử."
    );
    if (Boolean.TRUE.equals(db.queryForObject("""
      SELECT EXISTS(SELECT 1 FROM gcmb.payroll_entry
      WHERE period_id=? AND employee_id=? AND submitted_branch_id IS NOT NULL)
      OR EXISTS(SELECT 1 FROM gcmb.branch_payroll_submission WHERE period_id=? AND employee_id=?)
      """, Boolean.class, p.get("id"), input.employeeId(), p.get("id"), input.employeeId()))) {
      throw conflict("QLCS đã chốt lương, không thể ứng thêm cho kỳ này.");
    }
    if (
      !db
        .queryForList(
          "SELECT s.id FROM gcmb.salary_payment s JOIN gcmb.payroll_entry e ON e.id=s.payroll_entry_id WHERE e.period_id=? AND e.employee_id=? AND s.status='PAID'",
          p.get("id"),
          input.employeeId()
        )
        .isEmpty()
    ) throw conflict("Nhân viên đã nhận lương cuối kỳ, không thể ứng thêm."); // BR-74.
    BigDecimal amount = money(input.amount(), "Số tiền ứng", true);
    BigDecimal available = (BigDecimal) available(actor, value, input.employeeId()).get(
      "available"
    );
    if (amount.compareTo(available) > 0) throw bad(
      "Số tiền tạm ứng vượt mức lương đã tích lũy còn có thể ứng. Nhân viên hiện có thể ứng tối đa " +
        available +
        " đ."
    ); // MSG90.
    if (!"PAID".equals(input.result())) return Map.of(
      "message",
      "Khoản ứng chưa thành công, chưa ghi giảm quỹ."
    );
    var assignments = db.queryForList(
      "SELECT branch_id FROM gcmb.employee_assignment WHERE employee_id=? AND valid_from<=CURRENT_TIMESTAMP AND (valid_to IS NULL OR valid_to>CURRENT_TIMESTAMP) ORDER BY valid_from DESC LIMIT 1",
      UUID.class,
      input.employeeId()
    );
    if (assignments.isEmpty()) throw bad("Nhân viên chưa có cơ sở phân công.");
    UUID id = UUID.randomUUID();
    db.update(
      "INSERT INTO gcmb.salary_advance(id,employee_id,period_id,branch_id,amount,status,paid_at,processed_by,external_reference,idempotency_key) VALUES (?,?,?,?,?,'PAID',CURRENT_TIMESTAMP,?,?,?)",
      id,
      input.employeeId(),
      p.get("id"),
      assignments.get(0),
      amount,
      actor.id(),
      input.reference(),
      input.requestId()
    );
    post(actor, input.accountId(), id, true, amount, assignments.get(0));
    // BR-74/75/76: an advance after approval changes settlement only, not earned salary.
    var entries = db.queryForList(
      "SELECT id,net_amount FROM gcmb.payroll_entry WHERE period_id=? AND employee_id=?",
      p.get("id"),
      input.employeeId()
    );
    if (!entries.isEmpty()) {
      var entry = entries.get(0);
      if (((BigDecimal) entry.get("net_amount")).compareTo(amount) < 0) throw conflict(
        "Khoản ứng vượt số tiền còn phải trả của bảng lương."
      );
      db.update(
        "UPDATE gcmb.payroll_entry SET deduction_amount=deduction_amount+?,net_amount=net_amount-? WHERE id=?",
        amount,
        amount,
        entry.get("id")
      );
      db.update(
        "INSERT INTO gcmb.advance_application(advance_id,payroll_entry_id,amount) VALUES (?,?,?)",
        id,
        entry.get("id"),
        amount
      );
    }
    increment(p);
    access.audit(actor, assignments.get(0), id, "PAY_SALARY_ADVANCE", null, input);
    return view(actor, value);
  }

  private void validatePay(Pay p) {
    if (
      p == null ||
      p.employeeId() == null ||
      p.accountId() == null ||
      p.requestId() == null ||
      !p.requestId().matches("[A-Za-z0-9-]{10,100}") ||
      p.result() == null ||
      !Set.of("PAID", "FAILED", "PENDING").contains(p.result())
    ) throw bad("Thông tin kết quả thanh toán không hợp lệ.");
    if (text(p.reference(), "Mã tham chiếu", "PAID".equals(p.result())).length() > 200) throw bad(
      "Mã tham chiếu chỉ được chứa tối đa 200 ký tự."
    );
  }

  public Map<String, Object> pay(Identity actor, String value, Payments input) {
    access.accountant(actor);
    access.lock();
    var p = period(month(value), false);
    if (p == null) throw conflict("Chưa có bảng lương.");
    if (input.version() != ((Number) p.get("version")).longValue()) throw conflict(
      "Bảng lương đã thay đổi. Vui lòng tải lại."
    );
    if (
      input.payments() == null || input.payments().isEmpty() || input.payments().size() > 500
    ) throw bad("Vui lòng chọn nhân viên để ghi nhận thanh toán.");
    var outcomes = new ArrayList<Map<String, Object>>();
    for (var payment : input.payments()) {
      validatePay(payment);
      var entries = db.queryForList(
        "SELECT * FROM gcmb.payroll_entry WHERE period_id=? AND employee_id=?",
        p.get("id"),
        payment.employeeId()
      );
      if (entries.isEmpty()) throw bad("Nhân viên không thuộc bảng lương đã xác nhận.");
      var entry = entries.get(0);
      if (!"CONFIRMED".equals(p.get("status")) && entry.get("submitted_branch_id") == null) {
        throw conflict("Bảng lương nhân viên chưa được chốt.");
      }
      if (
        !db
          .queryForList(
            "SELECT id FROM gcmb.salary_payment WHERE idempotency_key=?",
            payment.requestId()
          )
          .isEmpty()
      ) {
        outcomes.add(
          Map.of(
            "employeeId",
            payment.employeeId(),
            "message",
            "Yêu cầu thanh toán này đã được ghi nhận. Vui lòng xem lịch sử kết quả."
          )
        );
        continue;
      }
      if (
        !db
          .queryForList(
            "SELECT id FROM gcmb.salary_payment WHERE payroll_entry_id=? AND status='PAID'",
            entry.get("id")
          )
          .isEmpty()
      ) {
        String name = db.queryForObject(
          "SELECT full_name FROM gcmb.employee WHERE id=?",
          String.class,
          payment.employeeId()
        );
        outcomes.add(
          Map.of(
            "employeeId",
            payment.employeeId(),
            "message",
            "Lương tháng " +
              value +
              " của " +
              name +
              " đã được thanh toán. Không thể ghi nhận thanh toán cuối kỳ lần thứ hai."
          )
        );
        continue;
      }
      BigDecimal amount = (BigDecimal) entry.get("net_amount");
      if (amount.signum() == 0) {
        outcomes.add(
          Map.of("employeeId", payment.employeeId(), "message", "Không còn tiền lương phải trả.")
        );
        continue;
      }
      // Unconfirmed attempts stay FAILED in storage so another confirmed attempt can be recorded.
      String status = "PAID".equals(payment.result()) ? "PAID" : "FAILED";
      UUID id = UUID.randomUUID();
      db.update(
        "INSERT INTO gcmb.salary_payment(id,payroll_entry_id,amount,status,paid_at,external_reference,failure_reason,processed_by,idempotency_key) VALUES (?,?,?,?,?,?,?,?,?)",
        id,
        entry.get("id"),
        amount,
        status,
        "PAID".equals(status) ? Timestamp.from(Instant.now()) : null,
        payment.reference(),
        "PAID".equals(status) ? null : "Kết quả: " + payment.result(),
        actor.id(),
        payment.requestId()
      );
      if ("PAID".equals(status)) post(actor, payment.accountId(), id, false, amount, null);
      access.audit(actor, null, id, "RECORD_SALARY_PAYMENT", null, payment);
      String name = db.queryForObject(
        "SELECT full_name FROM gcmb.employee WHERE id=?",
        String.class,
        payment.employeeId()
      );
      outcomes.add(
        Map.of(
          "employeeId",
          payment.employeeId(),
          "status",
          status,
          "message",
          "PAID".equals(status)
            ? "Đã ghi nhận thanh toán thành công."
            : "Khoản trả lương cho " +
                name +
                " chưa được xác nhận thành công. Nhân viên chưa được ghi nhận là đã nhận lương; vui lòng kiểm tra kết quả thanh toán."
        )
      ); // MSG92.
    }
    increment(p);
    var result = view(actor, value);
    result.put("outcomes", outcomes);
    return result;
  }

  private void post(
    Identity actor,
    UUID account,
    UUID id,
    boolean advance,
    BigDecimal amount,
    UUID branch
  ) {
    var accounts = db.queryForList(
      "SELECT id,name FROM gcmb.financial_account WHERE id=? AND active AND kind='CENTRAL_FUND' FOR UPDATE",
      account
    );
    if (accounts.isEmpty()) throw bad("Vui lòng chọn quỹ kế toán đang hoạt động.");
    BigDecimal balance = db.queryForObject(
      "SELECT balance FROM gcmb.v_account_balance WHERE id=?",
      BigDecimal.class,
      account
    );
    if (balance.compareTo(amount) < 0) throw conflict(
      "Quỹ " +
        accounts.get(0).get("name") +
        " hiện còn " +
        balance +
        " đ, không đủ cho khoản " +
        amount +
        " đ. Vui lòng kiểm tra lại nguồn tiền hoặc số tiền."
    ); // MSG93.
    LocalDate today = LocalDate.now(ZONE);
    periods.requireOpen(today, "ghi nhận chi trả payroll");
    db.update(
      "INSERT INTO gcmb.financial_period(month,status) VALUES (?,'OPEN') ON CONFLICT(month) DO NOTHING",
      today.withDayOfMonth(1)
    );
    var period = db.queryForMap(
      "SELECT id,status FROM gcmb.financial_period WHERE month=? FOR SHARE",
      today.withDayOfMonth(1)
    );
    if (!"OPEN".equals(period.get("status"))) throw conflict("Kỳ tài chính hiện tại đã đóng.");
    UUID transaction = UUID.randomUUID();
    String column = advance ? "salary_advance_id" : "salary_payment_id";
    db.update(
      "INSERT INTO gcmb.financial_transaction(id,period_id,booking_date,occurred_at,kind," +
        column +
        ",reason,created_by,idempotency_key) VALUES (?,?,?,CURRENT_TIMESTAMP,?,?,?,?,?)",
      transaction,
      period.get("id"),
      today,
      advance ? "ADVANCE" : "SALARY",
      id,
      advance ? "Ứng lương" : "Trả lương cuối kỳ",
      actor.id(),
      "PAYROLL-" + id
    );
    db.update(
      "INSERT INTO gcmb.financial_entry(transaction_id,account_id,branch_id,amount_signed) VALUES (?,?,?,?)",
      transaction,
      account,
      branch,
      amount.negate()
    ); // BR-83: paid movements only.
  }

  public List<Map<String, Object>> export(Identity actor, String value) {
    var result = view(actor, value);
    @SuppressWarnings("unchecked")
    var rows = (List<Map<String, Object>>) result.get("rows");
    if ("CONFIRMED".equals(result.get("status"))) return rows;
    var submitted = rows.stream().filter(row -> row.get("submitted_branch_id") != null).toList();
    if (submitted.isEmpty()) throw conflict(
        "Bảng lương chưa được QLCS chốt và gửi. Vui lòng kiểm tra trước khi xuất để thanh toán."); // MSG89 theo actor đã thống nhất.
    return submitted;
  }
}
