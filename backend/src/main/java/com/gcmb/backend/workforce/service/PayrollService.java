/** Author: HuyDV. UC-91..97: tính, duyệt, xuất lương, ứng và ghi nhận trả lương thực tế. */
package com.gcmb.backend.workforce.service;

import static com.gcmb.backend.finance.service.FinanceAccess.*;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.finance.service.FinanceAccess;
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
  private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  public record BranchSettlement(UUID employeeId, BigDecimal bonus, BigDecimal penalty,
                                 String reason, BigDecimal reviewedTotal) {}

  public List<Map<String, Object>> branchEmployees(Identity actor) {
    UUID branch = access.branch(actor, false);
    return db.queryForList("""
      SELECT e.id,e.code,e.full_name AS name FROM gcmb.employee e
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
    if (input.effectiveFrom() == null || branchEmployees(actor).stream()
        .noneMatch(e -> e.get("id").equals(input.employeeId()))) {
      throw bad("Vui lòng chọn nhân viên của cơ sở và ngày hiệu lực.");
    }
    BigDecimal rate = money(input.amount(), "Đơn giá giờ", true);
    db.update("""
      INSERT INTO gcmb.branch_employee_pay_rate VALUES (?,?,?,?)
      ON CONFLICT (branch_id,employee_id,effective_from) DO UPDATE SET hourly_rate=excluded.hourly_rate
      """, branch, input.employeeId(), input.effectiveFrom(), rate);
    access.audit(actor, branch, input.employeeId(), "SET_BRANCH_HOURLY_PAY", null, input);
  }

  // HuyDV: QLCS chốt số tiền theo công đã khóa và bàn giao từng nhân viên cho kế toán.
  public Map<String, Object> branchSettlement(Identity actor, String value, BranchSettlement input, boolean send) {
    if (send) access.lock();
    UUID branch = access.branch(actor, send);
    LocalDate m = month(value);
    if (!m.isBefore(LocalDate.now(ZONE).withDayOfMonth(1))) throw bad("Chỉ chốt lương cho tháng đã kết thúc.");
    if (input.employeeId() == null || branchEmployees(actor).stream()
        .noneMatch(e -> e.get("id").equals(input.employeeId()))) throw bad("Vui lòng chọn nhân viên của cơ sở.");
    var rows = work(input.employeeId(), m, true);
    if (rows.stream().anyMatch(r -> !branch.equals(r.get("branch_id")))) {
      throw conflict("Nhân viên có công ở cơ sở khác. Vui lòng chuyển kế toán đối chiếu.");
    }
    BigDecimal wageMinutes = BigDecimal.ZERO;
    long minutes = 0;
    for (var row : rows) {
      if ("ABSENT".equals(row.get("status"))) continue;
      var day = ((java.sql.Date) row.get("work_date")).toLocalDate();
      var rate = db.queryForList("""
        SELECT hourly_rate FROM gcmb.branch_employee_pay_rate
        WHERE branch_id=? AND employee_id=? AND effective_from<=?
        ORDER BY effective_from DESC LIMIT 1
        """, BigDecimal.class, branch, input.employeeId(), day);
      if (rate.isEmpty()) throw conflict("Thiếu đơn giá của cơ sở cho ngày " + day + ".");
      ZoneId zone = ZoneId.of((String) row.get("timezone"));
      var start = ((Timestamp) row.get("starts_at")).toInstant().atZone(zone);
      var end = ((Timestamp) row.get("ends_at")).toInstant().atZone(zone);
      long duration = Duration.between(start, end).toMinutes();
      boolean night = start.toLocalTime().equals(LocalTime.MIDNIGHT)
          && end.toLocalTime().equals(LocalTime.of(7, 0));
      BigDecimal hourly = rate.get(0).add(night ? BigDecimal.valueOf(3000) : BigDecimal.ZERO);
      wageMinutes = wageMinutes.add(hourly.multiply(BigDecimal.valueOf(duration)));
      minutes += duration;
    }
    BigDecimal wage = PayrollCalculator.rounded(wageMinutes);
    BigDecimal reward = minutes > 6000 ? BigDecimal.valueOf(100000) : BigDecimal.ZERO;
    BigDecimal bonus = money(input.bonus(), "Thưởng thêm", false);
    BigDecimal penalty = money(input.penalty(), "Phạt", false);
    String reason = text(input.reason(), "Lý do điều chỉnh", bonus.signum() > 0 || penalty.signum() > 0);
    var period = period(m, send);
    BigDecimal advance = period == null ? BigDecimal.ZERO : advances(input.employeeId(), (UUID) period.get("id"));
    BigDecimal gross = wage.add(reward).add(bonus);
    BigDecimal deduction = advance.add(penalty);
    BigDecimal net = gross.subtract(deduction);
    if (net.signum() < 0) throw bad("Tiền phạt và tạm ứng vượt tổng lương.");
    var result = Map.<String, Object>of("minutes", minutes, "wage", wage, "reward", reward,
        "bonus", bonus, "penalty", penalty, "advance", advance, "net", net);
    if (!send) {
      var preview = new LinkedHashMap<>(result);
      preview.put("attendanceNotes", rows.stream()
          .filter(row -> row.get("exception_note") instanceof String note && !note.isBlank())
          .map(row -> Map.of("date", row.get("work_date").toString(),
              "status", row.get("status"), "note", row.get("exception_note")))
          .toList());
      return preview;
    }
    if (input.reviewedTotal() == null || net.compareTo(input.reviewedTotal()) != 0) {
      throw conflict("Số tiền đã thay đổi. Vui lòng xem trước lại.");
    }
    if (!"DRAFT".equals(period.get("status"))) throw conflict("Bảng lương tháng đã khóa.");
    var inserted = db.queryForList("""
      INSERT INTO gcmb.payroll_entry(period_id,employee_id,gross_amount,deduction_amount,net_amount,
        submitted_branch_id,bank_name_snapshot,bank_account_snapshot,bank_holder_snapshot)
      SELECT ?,id,?,?,?,?,bank_name,bank_account,bank_holder FROM gcmb.employee WHERE id=?
      ON CONFLICT (period_id,employee_id) DO UPDATE
        SET gross_amount=excluded.gross_amount,deduction_amount=excluded.deduction_amount,
            net_amount=excluded.net_amount,submitted_branch_id=excluded.submitted_branch_id,
            bank_name_snapshot=excluded.bank_name_snapshot,bank_account_snapshot=excluded.bank_account_snapshot,
            bank_holder_snapshot=excluded.bank_holder_snapshot
        WHERE payroll_entry.submitted_branch_id IS NULL
      RETURNING id
      """, UUID.class, period.get("id"), gross, deduction, net, branch, input.employeeId());
    if (inserted.isEmpty()) throw conflict("Nhân viên đã có bảng lương tháng này. Vui lòng kiểm tra với kế toán.");
    UUID entry = inserted.get(0);
    db.update("DELETE FROM gcmb.payroll_component WHERE payroll_entry_id=?", entry);
    db.update("DELETE FROM gcmb.advance_application WHERE payroll_entry_id=?", entry);
    component(entry, branch, "BASE", wage, "Lương theo ca", result);
    if (reward.signum() > 0) component(entry, branch, "ADDITION", reward, "Thưởng trên 100 giờ", result);
    if (bonus.signum() > 0) component(entry, branch, "ADDITION", bonus, reason, result);
    if (penalty.signum() > 0) component(entry, branch, "DEDUCTION", penalty, reason, result);
    db.update("""
      INSERT INTO gcmb.advance_application(advance_id,payroll_entry_id,amount)
      SELECT id,?,amount FROM gcmb.salary_advance WHERE employee_id=? AND period_id=? AND status='PAID'
      """, entry, input.employeeId(), period.get("id"));
    increment(period);
    access.audit(actor, branch, entry, "SUBMIT_BRANCH_PAYROLL", null, result);
    return result;
  }

  public PayrollService(JdbcTemplate db, FinanceAccess access, ObjectMapper json) {
    this.db = db;
    this.access = access;
    this.json = json;
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
    access.accountant(actor);
    if (
      input.employees() == null ||
      input.employees().isEmpty() ||
      input.employees().size() > 500 ||
      input.employees().stream().anyMatch(Objects::isNull)
    ) throw bad("Vui lòng chọn nhân viên để tính lương.");
    LocalDate m = month(value);
    var p = period(m, false);
    var rows = new ArrayList<Map<String, Object>>();
    for (UUID employee : new TreeSet<>(input.employees())) {
      var amounts = earned(employee, m, true);
      BigDecimal bonus = BigDecimal.ZERO,
        deduction = BigDecimal.ZERO;
      if (p != null) for (var c : db.queryForList(
        "SELECT c.kind,c.amount FROM gcmb.payroll_component c JOIN gcmb.payroll_entry e ON e.id=c.payroll_entry_id WHERE e.employee_id=? AND e.period_id=? AND c.kind IN ('ADDITION','DEDUCTION')",
        employee,
        p.get("id")
      )) {
        if ("ADDITION".equals(c.get("kind"))) bonus = bonus.add((BigDecimal) c.get("amount"));
        else deduction = deduction.add((BigDecimal) c.get("amount"));
      }
      BigDecimal advance = p == null ? BigDecimal.ZERO : advances(employee, (UUID) p.get("id"));
      BigDecimal gross = amounts.values().stream().reduce(bonus, BigDecimal::add);
      rows.add(
        Map.of(
          "employeeId",
          employee,
          "components",
          amounts,
          "bonus",
          bonus,
          "deduction",
          deduction,
          "advances",
          advance,
          "net",
          gross.subtract(deduction).subtract(advance)
        )
      );
    }
    return Map.of(
      "rows",
      rows,
      "revision",
      UUID.nameUUIDFromBytes(json.writeValueAsBytes(rows)).toString()
    );
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
    if (create) db.update(
      "INSERT INTO gcmb.payroll_period(month,status) VALUES (?,'DRAFT') ON CONFLICT(month) DO NOTHING",
      month
    );
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
    access.accountant(actor);
    access.lock();
    if (input.effectiveFrom() == null) throw bad("Vui lòng bổ sung ngày hiệu lực.");
    for (var value : Arrays.asList(input.normal(), input.rest(), input.holiday(), input.night()))
      if (
        value == null ||
        value.signum() < 0 ||
        value.compareTo(BigDecimal.valueOf(100)) > 0 ||
        value.scale() > 4
      ) throw bad("Hệ số phải từ 0 đến 100 và tối đa 4 chữ số thập phân.");
    // BR-213: versioned by effective date; confirmed payroll retains its calculation snapshot.
    db.update(
      "INSERT INTO gcmb.payroll_setting VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP) ON CONFLICT(effective_from) DO UPDATE SET normal_multiplier=excluded.normal_multiplier,rest_multiplier=excluded.rest_multiplier,holiday_multiplier=excluded.holiday_multiplier,night_rate=excluded.night_rate,changed_by=excluded.changed_by",
      input.effectiveFrom(),
      input.normal(),
      input.rest(),
      input.holiday(),
      input.night(),
      actor.id()
    );
    access.audit(actor, null, null, "CONFIGURE_PAYROLL_RATES", null, input);
  }

  public void hourly(Identity actor, Hourly input) {
    access.accountant(actor);
    access.lock();
    BigDecimal amount = money(input.amount(), "Đơn giá giờ", false);
    if (input.employeeId() == null || input.effectiveFrom() == null) throw bad(
      "Vui lòng chọn nhân viên và ngày hiệu lực."
    );
    var exists = db.queryForList("SELECT id FROM gcmb.employee WHERE id=?", input.employeeId());
    if (exists.isEmpty()) throw bad("Không tìm thấy nhân viên.");
    String reason = text(input.reason(), "Lý do thay đổi đơn giá", true);
    var next = db.queryForList(
      "SELECT valid_from FROM gcmb.employee_pay_rate WHERE employee_id=? AND valid_from>? ORDER BY valid_from LIMIT 1",
      LocalDate.class,
      input.employeeId(),
      input.effectiveFrom()
    );
    db.update(
      "UPDATE gcmb.employee_pay_rate SET valid_to=? WHERE employee_id=? AND valid_from<? AND (valid_to IS NULL OR valid_to>?)",
      input.effectiveFrom(),
      input.employeeId(),
      input.effectiveFrom(),
      input.effectiveFrom()
    );
    var same = db.queryForList(
      "SELECT id FROM gcmb.employee_pay_rate WHERE employee_id=? AND valid_from=?",
      UUID.class,
      input.employeeId(),
      input.effectiveFrom()
    );
    if (same.isEmpty()) db.update(
      "INSERT INTO gcmb.employee_pay_rate(employee_id,valid_from,valid_to,hourly_rate,changed_by,reason) VALUES (?,?,?,?,?,?)",
      input.employeeId(),
      input.effectiveFrom(),
      next.isEmpty() ? null : next.get(0),
      amount,
      actor.id(),
      reason
    );
    else db.update(
      "UPDATE gcmb.employee_pay_rate SET hourly_rate=?,changed_by=?,reason=? WHERE id=?",
      amount,
      actor.id(),
      reason,
      same.get(0)
    );
    access.audit(actor, null, input.employeeId(), "CONFIGURE_HOURLY_PAY", null, input);
  }

  public void calendar(Identity actor, Calendar input) {
    access.accountant(actor);
    access.lock();
    if (
      !input.verified() ||
      input.year() < 2000 ||
      input.year() > 9998 ||
      input.days() == null ||
      input.days().size() > 366
    ) throw bad("Cần xác nhận lịch ngày lễ của năm đã được đối chiếu theo lịch Nhà nước.");
    var seen = new HashSet<LocalDate>();
    for (var d : input.days())
      if (
        d == null ||
        d.date() == null ||
        d.date().getYear() != input.year() ||
        !seen.add(d.date()) ||
        d.kind() == null ||
        !Set.of("HOLIDAY", "REST", "NORMAL").contains(d.kind()) ||
        text(d.description(), "Tên ngày", true).length() > 500
      ) throw bad("Lịch ngày lễ/ngày nghỉ không hợp lệ hoặc trùng ngày.");
    db.update(
      "DELETE FROM gcmb.payroll_calendar WHERE extract(year from work_date)=?",
      input.year()
    );
    for (var d : input.days())
      db.update(
        "INSERT INTO gcmb.payroll_calendar VALUES (?,?,?,?)",
        d.date(),
        d.kind(),
        d.description().trim(),
        actor.id()
      );
    db.update(
      "INSERT INTO gcmb.payroll_calendar_year(year,confirmed_by) VALUES (?,?) ON CONFLICT(year) DO UPDATE SET confirmed_by=excluded.confirmed_by,confirmed_at=CURRENT_TIMESTAMP",
      input.year(),
      actor.id()
    );
    access.audit(actor, null, null, "CONFIGURE_PAYROLL_CALENDAR", null, input);
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
      SELECT e.*,n.code,n.full_name AS name,b.name AS submitted_branch_name,
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
          "SELECT kind,amount,reason,calculation_snapshot::text AS detail FROM gcmb.payroll_component WHERE payroll_entry_id=? ORDER BY created_at,id",
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
      WHERE x.employee_id=? AND s.work_date>=? AND s.work_date<? ORDER BY s.starts_at
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
    access.accountant(actor);
    access.lock();
    if (
      !input.confirmed() ||
      input.employees() == null ||
      input.employees().isEmpty() ||
      input.employees().size() > 500
    ) throw bad("Vui lòng chọn nhân viên và xác nhận tính lương.");
    LocalDate m = month(value);
    var p = draft(m, input.version());
    if (
      !Objects.equals(input.previewRevision(), preview(actor, value, input).get("revision"))
    ) throw conflict(
      "Kết quả tính lương đã thay đổi hoặc chưa được xem trước. Vui lòng xem trước lại trước khi xác nhận."
    );
    for (UUID employee : new LinkedHashSet<>(input.employees()))
      rebuild(actor, p, employee, m, null);
    increment(p);
    access.audit(actor, null, (UUID) p.get("id"), "CALCULATE_PAYROLL", null, input);
    return view(actor, value);
  }

  private void rebuild(
    Identity actor,
    Map<String, Object> p,
    UUID employee,
    LocalDate m,
    Adjustment adjustment
  ) {
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
    access.accountant(actor);
    access.lock();
    LocalDate m = month(value);
    var p = draft(m, input.version());
    if (
      db
        .queryForList(
          "SELECT id FROM gcmb.payroll_entry WHERE period_id=? AND employee_id=?",
          p.get("id"),
          input.employeeId()
        )
        .isEmpty()
    ) throw bad("Cần tính lương nhân viên trước khi điều chỉnh.");
    var before = view(actor, value);
    rebuild(actor, p, input.employeeId(), m, input);
    increment(p);
    access.audit(actor, null, (UUID) p.get("id"), "ADJUST_PAYROLL", before, input);
    return view(actor, value);
  }

  public Map<String, Object> confirm(Identity actor, String value, Confirm input) {
    access.accountant(actor);
    access.lock();
    var p = draft(month(value), input.version());
    if (!input.reviewed()) throw bad("Vui lòng xác nhận đã kiểm tra bảng lương.");
    var rows = db.queryForList("SELECT * FROM gcmb.payroll_entry WHERE period_id=?", p.get("id"));
    if (rows.isEmpty()) throw bad("Bảng lương chưa có nhân viên.");
    if (rows.stream().anyMatch(row -> row.get("submitted_branch_id") != null)) {
      throw conflict("Tháng này nhận bảng lương theo cơ sở. Hãy thanh toán các dòng QLCS đã gửi.");
    }
    // Never silently confirm stale rates or advances: force recalculation for review first.
    for (var row : rows) {
      UUID employee = (UUID) row.get("employee_id");
      var fresh = earned(employee, month(value), true);
      for (var amount : fresh.entrySet()) {
        BigDecimal saved = db.queryForObject(
          "SELECT COALESCE(sum(amount),0) FROM gcmb.payroll_component WHERE payroll_entry_id=? AND reason=? AND kind NOT IN ('ADDITION','DEDUCTION')",
          BigDecimal.class,
          row.get("id"),
          amount.getKey()
        );
        if (saved.compareTo(amount.getValue()) != 0) throw conflict(
          "Cấu hình lương đã thay đổi. Vui lòng tính lại và kiểm tra bảng lương."
        );
      }
      BigDecimal applied = db.queryForObject(
        "SELECT COALESCE(sum(amount),0) FROM gcmb.advance_application WHERE payroll_entry_id=?",
        BigDecimal.class,
        row.get("id")
      );
      if (applied.compareTo(advances(employee, (UUID) p.get("id"))) != 0) throw conflict(
        "Tạm ứng đã thay đổi. Vui lòng tính lại bảng lương."
      );
    }
    db.update(
      "UPDATE gcmb.payroll_period SET status='CONFIRMED',confirmed_by=?,confirmed_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?",
      actor.id(),
      p.get("id")
    );
    access.audit(actor, null, (UUID) p.get("id"), "CONFIRM_PAYROLL", p, input);
    return view(actor, value);
  }

  public Map<String, Object> available(Identity actor, String value, UUID employee) {
    access.accountant(actor);
    var p = period(month(value), false);
    BigDecimal earned = earned(employee, month(value), false)
      .values()
      .stream()
      .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (p != null) {
      BigDecimal adjustment = db.queryForObject(
        "SELECT COALESCE(sum(CASE WHEN c.kind='DEDUCTION' THEN -c.amount ELSE c.amount END),0) FROM gcmb.payroll_component c JOIN gcmb.payroll_entry e ON e.id=c.payroll_entry_id WHERE e.period_id=? AND e.employee_id=? AND c.kind IN ('ADDITION','DEDUCTION')",
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
      """, Boolean.class, p.get("id"), input.employeeId()))) {
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
    if (submitted.isEmpty()) throw conflict("Chưa có bảng lương đã chốt để xuất.");
    return submitted;
  }
}
