/** Author: HuyDV. UC-91..97: tính, duyệt, xuất lương, ứng và ghi nhận trả lương thực tế. */
package com.gcmb.backend.workforce.service;

import static com.gcmb.backend.finance.service.FinanceAccess.*;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.exception.AuthFailure;
import com.gcmb.backend.finance.service.FinanceAccess;
import com.gcmb.backend.finance.service.FinancialPeriodService;
import com.gcmb.backend.inventory.service.EquipmentAssetFileStorageService;
import com.gcmb.backend.workforce.repository.PayrollRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class PayrollService {

  @Autowired
  private EquipmentAssetFileStorageService evidenceStorage;

  private final PayrollRepository payrollRepository;
  private final FinanceAccess financeAccess;
  private final ObjectMapper objectMapper;
  private final FinancialPeriodService financialPeriodService;
  private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  public record BranchSettlement(UUID employeeId, BigDecimal bonus, BigDecimal penalty,
                                 String reason, BigDecimal reviewedTotal, String reviewedRevision) {}

  public List<Map<String, Object>> branchEmployees(Identity actor) {
    UUID branch = financeAccess.branch(actor, false);
    return payrollRepository.findPayableBranchEmployees(branch);
  }

  public void branchHourly(Identity actor, Hourly input) {
    financeAccess.lock();
    UUID branch = financeAccess.branch(actor, true);
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
    if (payrollRepository.rateAffectsSubmittedPayroll(branch, input.employeeId(), input.effectiveFrom())) {
      throw conflict("Đơn giá này ảnh hưởng tháng lương đã gửi. Vui lòng chọn ngày hiệu lực sau tháng đã chốt.");
    }
    payrollRepository.upsertBranchHourlyRate(branch, input.employeeId(), input.effectiveFrom(), rate);
    financeAccess.audit(actor, branch, input.employeeId(), "SET_BRANCH_HOURLY_PAY", null, input);
  }

  // HuyDV | UC-91/95, BR-67: nghỉ/chuyển cơ sở không làm mất quyền quyết toán công cũ.
  private boolean missingHistoricalRate(UUID branch, UUID employee, LocalDate effective) {
    LocalDate month = effective.withDayOfMonth(1);
    if (!month.isBefore(LocalDate.now(ZONE).withDayOfMonth(1))) return false;
    return payrollRepository.hasWorkedShiftWithoutRate(branch, employee, effective, month.plusMonths(1));
  }

  // HuyDV | UC-92, BR-67: công cũ vẫn được quyết toán khi nhân viên nghỉ/chuyển cơ sở.
  public List<Map<String, Object>> settlementEmployees(Identity actor, String value) {
    UUID branch = financeAccess.branch(actor, false);
    LocalDate monthStart = month(value);
    return payrollRepository.findSettlementEmployees(branch, monthStart);
  }

  // HuyDV | UC-92: lịch sử theo cơ sở, bao gồm bảng lương đã gửi trước V42.
  public List<Map<String, Object>> branchHistory(Identity actor, String value) {
    UUID branch = financeAccess.branch(actor, false);
    LocalDate monthStart = month(value);
    return payrollRepository.findBranchHistory(branch, monthStart);
  }

  public record BankDetails(UUID employeeId, String month, String bankName, String bankAccount, String bankHolder) {}

  // HuyDV | UC-89/91/95, BR-63/64/67/77: xem công và tiền cùng màn hình; chỉ lương đã gửi dùng snapshot.
  @Transactional(readOnly = true)
  public List<Map<String, Object>> branchSummary(Identity actor, String value) {
    UUID branch = financeAccess.branch(actor, false);
    LocalDate monthStart = month(value);
    var history = branchHistory(actor, value);
    var result = new ArrayList<Map<String, Object>>();
    for (var employee : settlementEmployees(actor, value)) {
      UUID id = (UUID) employee.get("id");
      var item = new LinkedHashMap<String, Object>();
      item.put("employeeId", id);
      var saved = history.stream().filter(h -> id.equals(h.get("employee_id"))).findFirst().orElse(null);
      item.put("submitted", saved != null);
      item.put("ready", saved != null && Boolean.TRUE.equals(saved.get("ready")));
      item.put("paid", saved != null && Boolean.TRUE.equals(saved.get("paid")));
      item.put("bonus", saved == null ? BigDecimal.ZERO : saved.get("bonus"));
      item.put("penalty", saved == null ? BigDecimal.ZERO : saved.get("penalty"));
      item.put("reason", saved == null ? "" : Objects.toString(saved.get("reason"), ""));
      try {
        if (saved != null) {
          var calculations = payrollRepository.findSubmittedCalculation(branch, id, monthStart);
          var calculation = calculations.isEmpty() ? Map.<String, Object>of() : calculations.get(0);
          item.put("wage", saved.get("wage"));
          item.put("reward", Objects.requireNonNullElse(calculation.get("reward"), BigDecimal.ZERO));
          item.put("advance", Objects.requireNonNullElse(calculation.get("advance"), BigDecimal.ZERO));
          boolean multiple = Boolean.TRUE.equals(calculation.get("multiple"));
          item.put("multipleBranches", multiple);
          item.put("finalNet", saved.get("final_net"));
          item.put("net", Boolean.TRUE.equals(saved.get("ready")) && !multiple ? saved.get("final_net")
              : Objects.requireNonNullElse(calculation.get("net"), saved.get("branch_amount")));
        } else {
          var rows = work(id, monthStart, false).stream().filter(r -> branch.equals(r.get("branch_id"))).toList();
          var earnings = branchEarnings(id, rows, false);
          boolean multiple = payrollRepository.workedAtMultipleBranches(id, monthStart);
          BigDecimal wage = (BigDecimal) earnings.get("wage");
          BigDecimal reward = !multiple && (long) earnings.get("minutes") > 6000
              ? BigDecimal.valueOf(100000) : BigDecimal.ZERO;
          var period = period(monthStart, false);
          BigDecimal advance = multiple || period == null ? BigDecimal.ZERO : advances(id, (UUID) period.get("id"));
          item.put("wage", wage);
          item.put("reward", reward);
          item.put("advance", advance);
          item.put("multipleBranches", multiple);
          item.put("net", wage.add(reward).subtract(advance));
        }
      } catch (AuthFailure failure) {
        // Một nhân viên thiếu đơn giá không được làm mất bảng công của cả cơ sở.
        item.put("calculationError", failure.getMessage());
      }
      result.add(item);
    }
    return result;
  }

  // HuyDV | UC-97: nguồn tài khoản nhận lương là thông tin cá nhân; chặn API QLCS cũ.
  public void branchBank(Identity actor, BankDetails input) {
    financeAccess.branch(actor, true);
    throw new AuthFailure(403, "form", "Nhân viên tự cập nhật tài khoản nhận lương trong Thông tin tài khoản.");
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
      var rate = payrollRepository.findBranchHourlyRate(row.get("branch_id"), employee, day);
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
    if (send) financeAccess.lock();
    UUID branch = financeAccess.branch(actor, send);
    LocalDate monthStart = month(value);
    if (send) financialPeriodService.requireOpen(monthStart, "chốt dữ liệu payroll tại cơ sở");
    if (monthStart.isAfter(LocalDate.now(ZONE).withDayOfMonth(1))) throw bad("Không thể chốt lương cho tháng tương lai.");
    requireSettlementEmployee(actor, value, input.employeeId());
    // HuyDV / BR-64 / MSG87: cho chốt lương sớm khi công nhân viên đã khóa cả tháng, kể cả ở cơ sở khác.
    var allRows = work(input.employeeId(), monthStart, true);
    var rows = allRows.stream().filter(r -> branch.equals(r.get("branch_id"))).toList();
    var earnings = branchEarnings(input.employeeId(), rows, false);
    BigDecimal wage = (BigDecimal) earnings.get("wage");
    long minutes = (long) earnings.get("minutes");
    var branches = allRows.stream().map(r -> (UUID) r.get("branch_id")).distinct().toList();
    BigDecimal bonus = money(input.bonus(), "Thưởng thêm", false);
    BigDecimal penalty = money(input.penalty(), "Phạt", false);
    String reason = text(input.reason(), "Lý do điều chỉnh", bonus.signum() > 0 || penalty.signum() > 0);
    var period = period(monthStart, send);
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
    String revision = UUID.nameUUIDFromBytes(objectMapper.writeValueAsBytes(List.of(rows, result))).toString();
    result.put("revision", revision);
    if (!send) return result;
    if (input.reviewedTotal() == null || net.compareTo(input.reviewedTotal()) != 0
        || !revision.equals(input.reviewedRevision())) {
      throw conflict("Số liệu đã thay đổi. Vui lòng xem trước lại trước khi chốt lương.");
    }
    if (!"DRAFT".equals(period.get("status"))) throw conflict("Bảng lương tháng đã khóa.");
    if (payrollRepository.hasSubmittedPayrollEntry(period.get("id"), input.employeeId())) {
      throw conflict("Bảng lương tháng đã được tổng hợp và gửi kế toán, không thể chốt lại.");
    }
    // UC-95: chỉ được sửa phần đã gửi khi còn chờ cơ sở khác; luôn giữ audit trước/sau.
    var before = payrollRepository.findBranchSubmission(period.get("id"), input.employeeId(), branch);
    payrollRepository.upsertBranchSubmission(period.get("id"), input.employeeId(), branch, minutes, wage, bonus, penalty, reason,
        objectMapper.writeValueAsString(Map.of("attendance", rows, "calculation", result)), actor.id());
    var parts = payrollRepository.findSubmissions(period.get("id"), input.employeeId());
    boolean complete = branches.stream().allMatch(branchId -> parts.stream().anyMatch(part -> branchId.equals(part.get("branch_id"))));
    if (complete) finalizeBranchPayroll(period, input.employeeId(), branch, parts);
    increment(period);
    financeAccess.audit(actor, branch, input.employeeId(), "SUBMIT_BRANCH_PAYROLL", before, result);
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
    UUID entry = payrollRepository.upsertSubmittedEntry(period.get("id"), employee, gross, deduction, net, branch);
    payrollRepository.deleteComponents(entry);
    payrollRepository.deleteAdvanceApplications(entry);
    for (var part : parts) {
      UUID source = (UUID) part.get("branch_id");
      var snapshot = objectMapper.readTree((String) part.get("source_snapshot"));
      component(entry, source, "BASE", (BigDecimal) part.get("wage"), "Lương theo ca", snapshot);
      if (((BigDecimal) part.get("bonus")).signum() > 0)
        component(entry, source, "ADDITION", (BigDecimal) part.get("bonus"), (String) part.get("reason"), snapshot);
      if (((BigDecimal) part.get("penalty")).signum() > 0)
        component(entry, source, "DEDUCTION", (BigDecimal) part.get("penalty"), (String) part.get("reason"), snapshot);
    }
    if (reward.signum() > 0) component(entry, branch, "ADDITION", reward, "Thưởng trên 100 giờ (toàn tháng)", Map.of("minutes", minutes));
    payrollRepository.applyPaidAdvances(entry, employee, period.get("id"));
  }

  public PayrollService(PayrollRepository payrollRepository, FinanceAccess financeAccess, ObjectMapper objectMapper,
                        FinancialPeriodService financialPeriodService) {
    this.payrollRepository = payrollRepository;
    this.financeAccess = financeAccess;
    this.objectMapper = objectMapper;
    this.financialPeriodService = financialPeriodService;
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

  public record BatchSelection(long version, UUID branchId, List<UUID> employeeIds) {}

  public record PaymentBatch(UUID requestId, BatchSelection selection, LocalDate paymentDate,
                             String bankName, String reference) {}

  private record PayrollOutflow(UUID accountId, Object periodId) {}

  private LocalDate month(String value) {
    try {
      var yearMonth = YearMonth.parse(value);
      if (yearMonth.getYear() < 2000 || yearMonth.getYear() > 9998) throw new DateTimeException("year");
      return yearMonth.atDay(1);
    } catch (DateTimeException e) {
      throw bad("Tháng phải có định dạng YYYY-MM.");
    }
  }

  private Map<String, Object> period(LocalDate month, boolean create) {
    if (create) {
      financialPeriodService.requireOpen(month, "tạo hoặc cập nhật bảng lương");
      payrollRepository.ensurePayrollPeriod(month);
    }
    var rows = payrollRepository.findPayrollPeriod(month);
    return rows.isEmpty() ? null : rows.get(0);
  }

  private Map<String, Object> draft(LocalDate month, long version) {
    var payrollPeriod = period(month, true);
    if (!"DRAFT".equals(payrollPeriod.get("status"))) throw conflict(
      "Bảng lương đã xác nhận nên không thể sửa trực tiếp."
    );
    if (((Number) payrollPeriod.get("version")).longValue() != version) throw conflict(
      "Bảng lương đã thay đổi. Vui lòng tải lại và kiểm tra."
    );
    return payrollPeriod;
  }

  public Map<String, Object> options(Identity actor) {
    financeAccess.accountant(actor);
    return Map.of(
      "employees", payrollRepository.findEmployeeOptions(),
      "branches", payrollRepository.findBranchOptions(),
      "funds", payrollRepository.findCentralFunds(),
      "settings", payrollRepository.findPayrollSettings(),
      "rates", payrollRepository.findEmployeePayRates(),
      "calendar", payrollRepository.findPayrollCalendar(),
      "calendarYears", payrollRepository.findVerifiedCalendarYears()
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
    financeAccess.accountant(actor);
    LocalDate monthStart = month(value);
    var payrollPeriod = period(monthStart, false);
    if (payrollPeriod == null) return Map.of(
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
    var rows = payrollRepository.findPeriodEntries(payrollPeriod.get("id"));
    for (var row : rows) {
      row.put("components", payrollRepository.findEntryComponents(row.get("id")));
      row.put("payments", payrollRepository.findEntryPayments(row.get("id")));
    }
    payrollPeriod.put("pendingBranches", payrollRepository.findPendingBranchSubmissions(payrollPeriod.get("id")));
    payrollPeriod.put("rows", rows);
    payrollPeriod.put(
      "totalDue",
      rows
        .stream()
        .filter(r -> !Boolean.TRUE.equals(r.get("paid")))
        .map(r -> (BigDecimal) r.get("net_amount"))
        .reduce(BigDecimal.ZERO, BigDecimal::add)
    );
    payrollPeriod.put("advances", payrollRepository.findPeriodAdvances(payrollPeriod.get("id")));
    return payrollPeriod;
  }

  private List<Map<String, Object>> work(UUID employee, LocalDate monthStart, boolean closed) {
    var rows = payrollRepository.findMonthAttendance(employee, monthStart);
    if (closed) {
      boolean missing = payrollRepository.hasUnclosedAttendance(employee, monthStart);
      if (
        rows.isEmpty() ||
        missing ||
        rows.stream().anyMatch(r -> !"CLOSED".equals(r.get("period_status")))
      ) throw conflict(
        "Chưa thể tính lương tháng " + YearMonth.from(monthStart) + " vì bảng công chưa được chốt."
      ); // MSG87 / BR-64.
    }
    return rows;
  }

  public Map<String, Object> calculate(Identity actor, String value, Calculate input) {
    throw legacyPayrollDenied(actor);
  }

  private void component(
    UUID id,
    UUID branch,
    String kind,
    BigDecimal amount,
    String reason,
    Object snapshot
  ) {
    payrollRepository.insertComponent(id, branch, kind, amount, objectMapper.writeValueAsString(snapshot), reason);
  }

  private BigDecimal advances(UUID employee, UUID period) {
    return payrollRepository.sumPaidAdvances(employee, period);
  }

  private void increment(Map<String, Object> payrollPeriod) {
    payrollRepository.incrementPeriodVersion(payrollPeriod.get("id"));
  }

  public Map<String, Object> adjust(Identity actor, String value, Adjustment input) {
    throw legacyPayrollDenied(actor);
  }

  public Map<String, Object> confirm(Identity actor, String value, Confirm input) {
    throw legacyPayrollDenied(actor);
  }

  // HuyDV | UC-91/94/95 theo phân công đã chốt: kế toán chỉ tiếp nhận và ghi nhận chi trả.
  private com.gcmb.backend.auth.exception.AuthFailure legacyPayrollDenied(Identity actor) {
    financeAccess.accountant(actor);
    return new com.gcmb.backend.auth.exception.AuthFailure(403, "form",
        "QLCS đặt đơn giá và chốt lương tại cơ sở. Kế toán chỉ tiếp nhận bảng lương đã gửi, xuất và ghi nhận thanh toán.");
  }

  public Map<String, Object> available(Identity actor, String value, UUID employee) {
    financeAccess.accountant(actor);
    var payrollPeriod = period(month(value), false);
    var accrued = branchEarnings(employee, work(employee, month(value), false), true);
    BigDecimal earned = (BigDecimal) accrued.get("wage");
    if ((long) accrued.get("minutes") > 6000) earned = earned.add(BigDecimal.valueOf(100000));
    if (payrollPeriod != null) {
      BigDecimal adjustment = payrollRepository.sumApprovedAdjustments(payrollPeriod.get("id"), employee);
      earned = earned.add(adjustment);
    }
    BigDecimal paid = payrollPeriod == null ? BigDecimal.ZERO : advances(employee, (UUID) payrollPeriod.get("id"));
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
    financeAccess.accountant(actor);
    financeAccess.lock();
    LocalDate monthStart = month(value);
    financialPeriodService.requireOpen(monthStart, "ghi nhận ứng lương cho kỳ này");
    var payrollPeriod = period(monthStart, true);
    validatePay(input);
    if (payrollRepository.advanceRequestExists(input.requestId())) throw conflict(
      "Yêu cầu ứng lương này đã được ghi nhận. Vui lòng tải lại lịch sử."
    );
    if (payrollRepository.payrollAlreadySubmitted(payrollPeriod.get("id"), input.employeeId())) {
      throw conflict("QLCS đã chốt lương, không thể ứng thêm cho kỳ này.");
    }
    if (payrollRepository.finalSalaryPaid(payrollPeriod.get("id"), input.employeeId())) throw conflict("Nhân viên đã nhận lương cuối kỳ, không thể ứng thêm."); // BR-74.
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
    var assignments = payrollRepository.findCurrentBranch(input.employeeId());
    if (assignments.isEmpty()) throw bad("Nhân viên chưa có cơ sở phân công.");
    UUID id = UUID.randomUUID();
    payrollRepository.insertPaidAdvance(id, input.employeeId(), payrollPeriod.get("id"), assignments.get(0), amount, actor.id(),
        input.reference(), input.requestId());
    post(actor, input.accountId(), id, true, amount, assignments.get(0));
    // BR-74/75/76: an advance after approval changes settlement only, not earned salary.
    var entries = payrollRepository.findEntryNetAmount(payrollPeriod.get("id"), input.employeeId());
    if (!entries.isEmpty()) {
      var entry = entries.get(0);
      if (((BigDecimal) entry.get("net_amount")).compareTo(amount) < 0) throw conflict(
        "Khoản ứng vượt số tiền còn phải trả của bảng lương."
      );
      payrollRepository.deductAdvanceFromEntry(entry.get("id"), id, amount);
    }
    increment(payrollPeriod);
    financeAccess.audit(actor, assignments.get(0), id, "PAY_SALARY_ADVANCE", null, input);
    return view(actor, value);
  }

  private void validatePay(Pay payment) {
    validatePay(payment, false);
  }

  private void validatePay(Pay payment, boolean hasEvidence) {
    if (
      payment == null ||
      payment.employeeId() == null ||
      payment.requestId() == null ||
      !payment.requestId().matches("[A-Za-z0-9-]{10,100}") ||
      payment.result() == null ||
      !Set.of("PAID", "FAILED", "PENDING").contains(payment.result())
    ) throw bad("Thông tin kết quả thanh toán không hợp lệ.");
    if (text(payment.reference(), "Mã tham chiếu", "PAID".equals(payment.result()) && !hasEvidence).length() > 200) throw bad(
      "Mã tham chiếu chỉ được chứa tối đa 200 ký tự."
    );
  }

  // HuyDV: file mới chỉ tồn tại khi toàn bộ lượt ghi nhận lương commit thành công.
  private void savePaymentEvidence(UUID paymentId, MultipartFile proof) {
    if (proof == null) return;
    var stored = evidenceStorage.store(proof, "luong");
    try {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCompletion(int status) {
          if (status != STATUS_COMMITTED) evidenceStorage.deleteQuietly(stored.objectKey());
        }
      });
      payrollRepository.insertPaymentEvidence(paymentId, stored.objectKey(), stored.originalName(), stored.mimeType(),
          stored.byteSize(), stored.sha256());
    } catch (RuntimeException failure) {
      evidenceStorage.deleteQuietly(stored.objectKey());
      throw failure;
    }
  }

  public Map<String, Object> paymentEvidence(Identity actor, UUID paymentId) {
    financeAccess.accountant(actor);
    var rows = payrollRepository.findPaymentEvidence(paymentId);
    if (rows.isEmpty()) throw new AuthFailure(404, "form", "Không tìm thấy bằng chứng trả lương.");
    var file = rows.get(0);
    try { file.put("bytes", evidenceStorage.read((String) file.get("object_key"))); }
    catch (java.io.IOException failure) { throw new AuthFailure(404, "form", "Không đọc được bằng chứng trả lương."); }
    return file;
  }

  public Map<String, Object> pay(Identity actor, String value, Payments input) {
    return pay(actor, value, input, Map.of());
  }

  public Map<String, Object> pay(Identity actor, String value, Payments input, Map<String, MultipartFile> proofs) {
    financeAccess.accountant(actor);
    financeAccess.lock();
    var payrollPeriod = period(month(value), false);
    if (payrollPeriod == null) throw conflict("Chưa có bảng lương.");
    if (input.version() != ((Number) payrollPeriod.get("version")).longValue()) throw conflict(
      "Bảng lương đã thay đổi. Vui lòng tải lại."
    );
    if (
      input.payments() == null || input.payments().isEmpty() || input.payments().size() > 500
    ) throw bad("Vui lòng chọn nhân viên để ghi nhận thanh toán.");
    var outcomes = new ArrayList<Map<String, Object>>();
    for (var payment : input.payments()) {
      validatePay(payment, payment != null && proofs.containsKey("proof_" + payment.employeeId()));
      var entries = payrollRepository.findEntry(payrollPeriod.get("id"), payment.employeeId());
      if (entries.isEmpty()) throw bad("Nhân viên không thuộc bảng lương đã xác nhận.");
      var entry = entries.get(0);
      if (!"CONFIRMED".equals(payrollPeriod.get("status")) && entry.get("submitted_branch_id") == null) {
        throw conflict("Bảng lương nhân viên chưa được chốt.");
      }
      if (payrollRepository.salaryPaymentRequestExists(payment.requestId())) {
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
      if (payrollRepository.entryAlreadyPaid(entry.get("id"))) {
        String name = payrollRepository.findEmployeeName(payment.employeeId());
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
      payrollRepository.insertSalaryPayment(id, entry.get("id"), amount, status,
          "PAID".equals(status) ? Timestamp.from(Instant.now()) : null, payment.reference(),
          "PAID".equals(status) ? null : "Kết quả: " + payment.result(), actor.id(), payment.requestId());
      if ("PAID".equals(status)) post(actor, payment.accountId(), id, false, amount, null);
      savePaymentEvidence(id, proofs.get("proof_" + payment.employeeId()));
      financeAccess.audit(actor, null, id, "RECORD_SALARY_PAYMENT", null, payment);
      String name = payrollRepository.findEmployeeName(payment.employeeId());
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
    increment(payrollPeriod);
    var result = view(actor, value);
    result.put("outcomes", outcomes);
    return result;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> paymentHistory(Identity actor, String value, UUID branchId) {
    financeAccess.accountant(actor);
    LocalDate monthStart = month(value);
    return payrollRepository.findSalaryPaymentHistory(
      Timestamp.from(monthStart.atStartOfDay(ZONE).toInstant()),
      Timestamp.from(monthStart.plusMonths(1).atStartOfDay(ZONE).toInstant()), branchId);
  }

  public List<Map<String, Object>> exportBatchRecipients(Identity actor, String value, BatchSelection selection) {
    financeAccess.accountant(actor);
    financeAccess.lock();
    return batchEntries(period(month(value), false), selection, batchEmployeeIds(selection));
  }

  public Map<String, Object> payBatch(Identity actor, String value, PaymentBatch command) {
    financeAccess.accountant(actor);
    if (command == null || command.requestId() == null || command.paymentDate() == null
        || command.paymentDate().isBefore(LocalDate.of(2000, 1, 1))
        || command.paymentDate().isAfter(LocalDate.now(ZONE))) {
      throw bad("Vui lòng nhập ngày chuyển khoản hợp lệ, không sau ngày hôm nay.");
    }
    String bankName = batchText(command.bankName(), "Ngân hàng chuyển");
    String reference = batchText(command.reference(), "Mã giao dịch lô");
    BatchSelection selection = command.selection();
    Set<UUID> employeeIds = batchEmployeeIds(selection);

    financeAccess.lock();
    var payrollPeriod = period(month(value), false);
    if (payrollPeriod == null) throw conflict("Chưa có bảng lương.");
    Object periodId = payrollPeriod.get("id");
    UUID batchId = command.requestId();
    var recorded = payrollRepository.findPaymentBatch(batchId);
    if (!recorded.isEmpty()) {
      var existing = recorded.get(0);
      if (!periodId.equals(existing.get("payroll_period_id")) || !selection.branchId().equals(existing.get("branch_id"))
          || !command.paymentDate().toString().equals(existing.get("payment_date").toString())
          || !bankName.equals(existing.get("bank_name")) || !reference.equals(existing.get("external_reference"))
          || !actor.id().equals(existing.get("created_by"))
          || !employeeIds.equals(new HashSet<>(payrollRepository.findBatchEmployees(batchId)))) {
        throw conflict("Mã yêu cầu đã được dùng cho lô khác. Vui lòng kiểm tra lịch sử thanh toán.");
      }
      return Map.of("batchId", batchId, "count", employeeIds.size());
    }

    var entries = batchEntries(payrollPeriod, selection, employeeIds);
    BigDecimal total = entries.stream().map(entry -> (BigDecimal) entry.get("net_amount"))
      .reduce(BigDecimal.ZERO, BigDecimal::add);
    LocalDate paymentDate = command.paymentDate();
    var outflow = prepareOutflow(null, total, paymentDate);
    payrollRepository.insertPaymentBatch(batchId, periodId, selection.branchId(), paymentDate, bankName, reference, actor.id());
    Timestamp paidAt = Timestamp.from(paymentDate.atStartOfDay(ZONE).toInstant());
    for (var entry : entries) {
      UUID paymentId = UUID.randomUUID();
      BigDecimal amount = (BigDecimal) entry.get("net_amount");
      payrollRepository.insertSalaryPayment(paymentId, entry.get("id"), amount, "PAID", paidAt,
        reference, null, actor.id(), batchId + "-" + entry.get("employee_id"), batchId);
      payrollRepository.insertPayrollOutflow(UUID.randomUUID(), outflow.periodId(), paymentDate,
        false, paymentId, actor.id(), outflow.accountId(), selection.branchId(), amount);
    }
    financeAccess.audit(actor, selection.branchId(), batchId, "RECORD_SALARY_PAYMENT_BATCH", null,
      Map.of("payrollMonth", value, "paymentDate", paymentDate, "bankName", bankName, "reference", reference,
        "employeeIds", selection.employeeIds(), "total", total));
    increment(payrollPeriod);
    return Map.of("batchId", batchId, "count", entries.size());
  }

  private static String batchText(String value, String label) {
    String result = text(value, label, true);
    if (result.length() > 200) throw bad(label + " chỉ được chứa tối đa 200 ký tự.");
    return result;
  }

  private static Set<UUID> batchEmployeeIds(BatchSelection selection) {
    if (selection == null || selection.branchId() == null || selection.employeeIds() == null
        || selection.employeeIds().isEmpty() || selection.employeeIds().size() > 500) {
      throw bad("Vui lòng chọn cơ sở và từ 1 đến 500 nhân viên để lập lô.");
    }
    var employeeIds = new HashSet<>(selection.employeeIds());
    if (employeeIds.contains(null) || employeeIds.size() != selection.employeeIds().size()) {
      throw bad("Danh sách lô có nhân viên không hợp lệ hoặc bị trùng.");
    }
    return employeeIds;
  }

  private List<Map<String, Object>> batchEntries(Map<String, Object> payrollPeriod, BatchSelection selection,
      Set<UUID> employeeIds) {
    if (payrollPeriod == null) throw conflict("Chưa có bảng lương.");
    if (selection.version() != ((Number) payrollPeriod.get("version")).longValue()) {
      throw conflict("Bảng lương đã thay đổi. Vui lòng tải lại và đối chiếu danh sách.");
    }
    var entries = payrollRepository.findBatchEntries(payrollPeriod.get("id"), selection.branchId(), selection.employeeIds());
    if (entries.size() != employeeIds.size()) throw bad("Có nhân viên không thuộc bảng lương của cơ sở đã chọn.");
    for (var entry : entries) {
      if (!"CONFIRMED".equals(payrollPeriod.get("status")) && entry.get("submitted_branch_id") == null) {
        throw conflict("Bảng lương chưa được chốt cho " + entry.get("name") + ".");
      }
      if (Boolean.TRUE.equals(entry.get("paid")) || ((BigDecimal) entry.get("net_amount")).signum() <= 0) {
        throw conflict("Không còn khoản lương cần trả cho " + entry.get("name") + ". Vui lòng tải lại.");
      }
      if (List.of("bank_name_snapshot", "bank_account_snapshot", "bank_holder_snapshot").stream()
          .anyMatch(key -> !(entry.get(key) instanceof String field) || field.isBlank())) {
        throw bad("Thiếu thông tin tài khoản nhận lương của " + entry.get("name") + ".");
      }
    }
    return entries;
  }

  private void post(
    Identity actor,
    UUID account,
    UUID id,
    boolean advance,
    BigDecimal amount,
    UUID branch
  ) {
    LocalDate today = LocalDate.now(ZONE);
    var outflow = prepareOutflow(account, amount, today);
    payrollRepository.insertPayrollOutflow(UUID.randomUUID(), outflow.periodId(), today, advance,
      id, actor.id(), outflow.accountId(), branch, amount); // BR-83: paid movements only.
  }

  private PayrollOutflow prepareOutflow(UUID account, BigDecimal amount, LocalDate bookingDate) {
    var accounts = payrollRepository.lockActiveCentralFund(account);
    if (accounts.isEmpty()) throw bad("Chưa có quỹ kế toán đang hoạt động để ghi nhận chi tiền.");
    UUID accountId = (UUID) accounts.get(0).get("id");
    BigDecimal balance = payrollRepository.accountantFundBalance();
    if (balance.compareTo(amount) < 0) throw conflict(
      "Quỹ " +
        accounts.get(0).get("name") +
        " hiện còn " +
        balance +
        " đ, không đủ cho khoản " +
        amount +
        " đ. Vui lòng kiểm tra lại nguồn tiền hoặc số tiền."
    ); // MSG93.
    financialPeriodService.requireOpen(bookingDate, "ghi nhận chi trả lương");
    var period = payrollRepository.shareFinancialPeriod(bookingDate.withDayOfMonth(1));
    if (!"OPEN".equals(period.get("status"))) throw conflict("Kỳ tài chính của ngày thanh toán đã đóng.");
    return new PayrollOutflow(accountId, period.get("id"));
  }

  public List<Map<String, Object>> export(Identity actor, String value) {
    return export(actor, value, null);
  }

  public List<Map<String, Object>> export(Identity actor, String value, UUID branch) {
    var result = view(actor, value);
    @SuppressWarnings("unchecked")
    var rows = (List<Map<String, Object>>) result.get("rows");
    if (branch != null) rows = rows.stream().filter(row -> {
      if (branch.equals(row.get("submitted_branch_id"))) return true;
      var components = (List<?>) row.get("components");
      return components.stream().anyMatch(c -> branch.equals(((Map<?, ?>) c).get("branch_id")));
    }).toList();
    if ("CONFIRMED".equals(result.get("status"))) return rows;
    var submitted = rows.stream().filter(row -> row.get("submitted_branch_id") != null).toList();
    if (submitted.isEmpty()) throw conflict(
        "Bảng lương chưa được QLCS chốt và gửi. Vui lòng kiểm tra trước khi xuất để thanh toán."); // MSG89 theo actor đã thống nhất.
    return submitted;
  }
}
