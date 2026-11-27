/** Author: HuyDV. UC-98..101 / BR-95..98/208: cash-only daily closing and accountant reconciliation. */
package com.gcmb.backend.finance.service;

import static com.gcmb.backend.finance.service.FinanceAccess.*;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.booking.service.RefundEvidenceStorageService;
import com.gcmb.backend.finance.repository.CashClosingRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class CashClosingService {
  private final CashClosingRepository cashClosingRepository;
  private final FinanceAccess financeAccess;
  private final ObjectMapper objectMapper;
  private final RefundEvidenceStorageService refundEvidenceStorageService;
  private final FinancialPeriodService financialPeriodService;

  public CashClosingService(
    CashClosingRepository cashClosingRepository,
    FinanceAccess financeAccess,
    ObjectMapper objectMapper,
    RefundEvidenceStorageService refundEvidenceStorageService,
    FinancialPeriodService financialPeriodService
  ) {
    this.cashClosingRepository = cashClosingRepository;
    this.financeAccess = financeAccess;
    this.objectMapper = objectMapper;
    this.refundEvidenceStorageService = refundEvidenceStorageService;
    this.financialPeriodService = financialPeriodService;
  }

  public record Submit(LocalDate date, BigDecimal actual, String explanation,
    String revision, Integer reportVersion) {}
  public record Review(boolean resolved, String conclusion) {}
  public record Remittance(LocalDate date, BigDecimal amount, String reason, String reference, UUID requestId, boolean fullSweep) {
    public Remittance(LocalDate date, BigDecimal amount, String reason, String reference, UUID requestId) {
      this(date,amount,reason,reference,requestId,false);
    }
  }

  private static BigDecimal decimal(Object value) {
    return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
  }

  private LocalDate businessDate(LocalDate value) {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    if (value == null) return today;
    if (value.isAfter(today) || value.getYear() < 2000)
      throw bad("Ngày chốt quỹ không hợp lệ hoặc ở tương lai.");
    return value;
  }

  private UUID drawer(UUID branchId) {
    var accounts = cashClosingRepository.findActiveDrawerAccounts(branchId);
    if (accounts.size() != 1)
      throw conflict("Cần cấu hình đúng một két tiền mặt đang hoạt động cho cơ sở trước khi chốt quỹ.");
    return accounts.get(0);
  }

  private ZoneId zone(UUID branchId) {
    return ZoneId.of(cashClosingRepository.findBranchTimezone(branchId));
  }

  private void checkDate(UUID accountId, LocalDate businessDate) {
    if (cashClosingRepository.hasClosingAfter(accountId, businessDate))
      throw conflict("Không được chốt lùi trước ngày đã có báo cáo chốt quỹ mới hơn.");
  }

  private void requireOpenPeriod(LocalDate businessDate) {
    if (cashClosingRepository.isPeriodClosed(businessDate.withDayOfMonth(1)))
      throw conflict("Kỳ tài chính của ngày này đã khóa. Không được thay đổi báo cáo hoặc số dư két.");
  }

  public Map<String,Object> preview(Identity actor, LocalDate value) {
    UUID branchId = financeAccess.branch(actor, false);
    LocalDate businessDate = businessDate(value);
    var result = source(branchId, businessDate);
    java.sql.Date latestClosingDate = cashClosingRepository.findLatestClosingDate(result.get("accountId"));
    result.put("closingBlocked", latestClosingDate != null && businessDate.isBefore(latestClosingDate.toLocalDate()));
    result.put("lastClosingDate", latestClosingDate == null ? null : latestClosingDate.toString());
    var current=source(branchId,LocalDate.now(zone(branchId)));
    result.put("currentBalance",current.get("expected"));
    result.put("cashCycle",cashCycle((UUID)result.get("accountId"),zone(branchId),current));
    result.put("pendingHandovers",cashClosingRepository.countPendingShiftDiscrepancies(branchId));
    return result;
  }

  private Map<String,Object> source(UUID branchId, LocalDate businessDate) {
    return sourceAt(branchId,businessDate,businessDate.plusDays(1).atStartOfDay(zone(branchId)).toInstant());
  }

  // HuyDV: bàn giao ca dùng cùng nguồn tiền với chốt ngày, chỉ thay mốc kết thúc.
  Map<String,Object> sourceAt(UUID branchId, LocalDate businessDate, Instant cutoff) {
    UUID accountId = drawer(branchId);
    ZoneId zone = zone(branchId);
    // BR-95: cash receipts not yet posted to the ledger are included exactly once.
    // V44 receipt movements remain historical; no new report creates a handover.
    var allMovements = cashClosingRepository.findCashMovementsBefore(accountId, branchId, Timestamp.from(cutoff));
    BigDecimal opening=BigDecimal.ZERO, incoming=BigDecimal.ZERO, outgoing=BigDecimal.ZERO;
    var movements = new ArrayList<Map<String,Object>>();
    for (var movement : allMovements) {
      BigDecimal amount = decimal(movement.get("amount"));
      LocalDate movementDate = ((Timestamp)movement.get("time")).toInstant().atZone(zone).toLocalDate();
      if (movementDate.isBefore(businessDate)) opening=opening.add(amount);
      else {
        movements.add(movement);
        if (amount.signum()>0) incoming=incoming.add(amount);
        else outgoing=outgoing.subtract(amount);
      }
    }
    var result = new LinkedHashMap<String,Object>();
    result.put("accountId",accountId);
    result.put("date",businessDate.toString());
    result.put("opening",opening);
    result.put("cashIn",incoming);
    result.put("cashOut",outgoing);
    result.put("expected",opening.add(incoming).subtract(outgoing));
    result.put("movements",movements);
    result.put("firstCashAt",allMovements.stream().filter(movement->decimal(movement.get("amount")).signum()>0
      && !"COUNT_VARIANCE".equals(movement.get("kind"))).map(movement->movement.get("time")).findFirst().orElse(null));
    result.put("remitted",movements.stream().filter(movement->"REMITTANCE".equals(movement.get("kind")))
      .map(movement->decimal(movement.get("amount")).negate()).reduce(BigDecimal.ZERO,BigDecimal::add));
    result.put("cashExpense",movements.stream().filter(movement->decimal(movement.get("amount")).signum()<0
      && !Set.of("REMITTANCE","COUNT_VARIANCE").contains(String.valueOf(movement.get("kind"))))
      .map(movement->decimal(movement.get("amount")).negate()).reduce(BigDecimal.ZERO,BigDecimal::add));
    result.put("countAdjustment",movements.stream().filter(movement->"COUNT_VARIANCE".equals(movement.get("kind")))
      .map(movement->decimal(movement.get("amount"))).reduce(BigDecimal.ZERO,BigDecimal::add));
    result.put("drawerFunding",movements.stream().filter(movement->"DRAWER_FUNDING".equals(movement.get("kind")))
      .map(movement->decimal(movement.get("amount"))).reduce(BigDecimal.ZERO,BigDecimal::add));
    result.put("cashReceipts",movements.stream().filter(movement->decimal(movement.get("amount")).signum()>0
      && !Set.of("COUNT_VARIANCE","DRAWER_FUNDING").contains(String.valueOf(movement.get("kind"))))
      .map(movement->decimal(movement.get("amount"))).reduce(BigDecimal.ZERO,BigDecimal::add));
    result.put("scope","CASH_ONLY");
    result.put("revision",UUID.nameUUIDFromBytes(objectMapper.writeValueAsBytes(allMovements)).toString());
    result.put("existing",cashClosingRepository.findClosingsOn(accountId,businessDate));
    return result;
  }

  // HuyDV: five calendar days is a reminder, never an automatic withdrawal.
  private Map<String,Object> cashCycle(UUID accountId,ZoneId zone,Map<String,Object> current) {
    Timestamp lastFullSweepAt=cashClosingRepository.findLastFullSweepAt(accountId);
    Timestamp anchor=cashClosingRepository.findFirstReceiptAfter(accountId,lastFullSweepAt);
    // Empty days before cash arrives do not consume the next five-day holding cycle.
    if(anchor==null && lastFullSweepAt==null) anchor=(Timestamp)current.get("firstCashAt");
    if(anchor==null && lastFullSweepAt!=null && decimal(current.get("expected")).signum()>0) anchor=lastFullSweepAt;
    var cycle=new LinkedHashMap<String,Object>();
    LocalDate startedOn=anchor==null?null:anchor.toInstant().atZone(zone).toLocalDate();
    long elapsedDays=startedOn==null?0:Math.max(0,java.time.temporal.ChronoUnit.DAYS.between(startedOn,LocalDate.now(zone)));
    cycle.put("lastFullSweepAt",lastFullSweepAt);
    cycle.put("startedOn",startedOn==null?null:startedOn.toString());
    cycle.put("dueOn",startedOn==null?null:startedOn.plusDays(5).toString());
    cycle.put("elapsedDays",elapsedDays);
    cycle.put("due",elapsedDays>=5 && decimal(current.get("expected")).signum()>0);
    return cycle;
  }

  @SuppressWarnings("unchecked")
  public Map<String,Object> submit(Identity actor, Submit input, MultipartFile evidence) {
    financeAccess.lock();
    UUID branchId=financeAccess.branch(actor,true);
    LocalDate businessDate=businessDate(input.date());
    requireOpenPeriod(businessDate);
    var preview=source(branchId,businessDate);
    if(!Objects.equals(input.revision(),preview.get("revision")))
      throw conflict("Giao dịch nguồn đã thay đổi. Vui lòng tải lại và kiểm tra trước khi gửi báo cáo.");
    var existing=(List<Map<String,Object>>)preview.get("existing");
    UUID closingId=existing.isEmpty()?UUID.randomUUID():(UUID)existing.get(0).get("id");
    int reportVersion=existing.isEmpty()?0:((Number)existing.get(0).get("report_version")).intValue();
    if(!existing.isEmpty() && !"NEEDS_EXPLANATION".equals(existing.get(0).get("status")))
      throw conflict("Ngày này đã gửi báo cáo. Chỉ được bổ sung khi kế toán yêu cầu.");
    if(!existing.isEmpty() && !Objects.equals(input.reportVersion(),reportVersion))
      throw conflict("Phiên bản báo cáo đã thay đổi. Vui lòng tải lại.");
    if(existing.isEmpty()) checkDate((UUID)preview.get("accountId"),businessDate);
    BigDecimal actualClosing=money(input.actual(),"Tiền mặt thực đếm",false);
    BigDecimal expectedClosing=decimal(preview.get("expected"));
    BigDecimal variance=actualClosing.subtract(expectedClosing);
    String explanation=text(input.explanation(),"Giải trình chênh lệch",
      variance.signum()!=0);
    if(decimal(preview.get("opening")).signum()<0 || expectedClosing.signum()<0)
      throw conflict("Số dư tiền mặt âm. Cần kiểm tra giao dịch nguồn trước khi chốt quỹ.");
    if(existing.isEmpty()) cashClosingRepository.insertClosing(closingId,branchId,preview.get("accountId"),businessDate,
      preview.get("opening"),preview.get("cashIn"),preview.get("cashOut"),expectedClosing,actualClosing,variance,explanation,
      actor.id(),objectMapper.writeValueAsString(preview));
    int nextVersion=reportVersion+1;
    var payload=new LinkedHashMap<String,Object>();
    payload.put("opening_amount",preview.get("opening"));
    payload.put("cash_in",preview.get("cashIn"));
    payload.put("cash_out",preview.get("cashOut"));
    payload.put("expected_closing",expectedClosing);
    payload.put("actual_closing",actualClosing);
    payload.put("variance",variance);
    payload.put("scope","CASH_ONLY");
    payload.put("explanation",explanation);
    payload.put("source",preview);
    cashClosingRepository.insertReportRevision(closingId,nextVersion,objectMapper.writeValueAsString(payload),actor.id());
    if(reportVersion==0) cashClosingRepository.insertReport(closingId,nextVersion);
    else cashClosingRepository.updateReportRevision(closingId,nextVersion);
    if(evidence!=null && !evidence.isEmpty()) storeEvidence(closingId,nextVersion,evidence);
    financeAccess.audit(actor,branchId,closingId,reportVersion==0?"SUBMIT_DAILY_TILL_REPORT":"RESUBMIT_DAILY_TILL_REPORT",null,payload);
    return detail(actor,closingId);
  }

  private void storeEvidence(UUID closingId,int reportVersion,MultipartFile evidence) {
    var stored=refundEvidenceStorageService.store(evidence, "chot-quy");
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override public void afterCompletion(int status) {
        if(status!=STATUS_COMMITTED) refundEvidenceStorageService.deleteQuietly(stored.objectKey());
      }
    });
    cashClosingRepository.insertRevisionEvidence(closingId,reportVersion,stored.originalName(),stored.mimeType(),stored.objectKey());
  }

  @SuppressWarnings("unchecked")
  private void reportRow(Map<String,Object> closing, boolean history) {
    String businessDate=closing.get("business_date").toString();
    var reports=cashClosingRepository.findCurrentReport(closing.get("id"));
    closing.put("report_version",0);
    if(!reports.isEmpty()) {
      var current=reports.get(0);
      var payload=objectMapper.readValue(current.get("payload").toString(),Map.class);
      closing.putAll(payload);
      // Normalize JSON numbers so review validation also works for small/zero amounts.
      for(String key:List.of("opening_amount","cash_in","cash_out","expected_closing","actual_closing","variance","actual_bank","bank_variance"))
        if(closing.containsKey(key) && closing.get(key)!=null) closing.put(key,decimal(closing.get(key)));
      closing.put("report_version",current.get("revision"));
      closing.put("status",current.get("state"));
      closing.put("submitted_at",current.get("submitted_at"));
      closing.put("submittedBy",current.get("username"));
    }
    closing.put("business_date",businessDate);
    if(history) closing.put("versions",cashClosingRepository.findReportRevisions(closing.get("id")).stream().map(revision -> {
        revision.put("payload",objectMapper.readValue(revision.get("payload").toString(),Map.class));
        return revision;
      }).toList());
  }

  public List<Map<String,Object>> list(Identity actor,LocalDate from,LocalDate to,UUID branchId,String status,boolean alerts) {
    if("BRANCH_MANAGER".equals(actor.role())) branchId=financeAccess.branch(actor,false);
    else financeAccess.accountant(actor);
    LocalDate until=to==null?LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")):to;
    LocalDate start=from==null?until.withDayOfMonth(1):from;
    if(start.isAfter(until)||start.plusYears(2).isBefore(until)) throw bad("Khoảng ngày không hợp lệ, tối đa hai năm.");
    var closings=cashClosingRepository.findClosings(branchId,start,until,status);
    closings.forEach(closing->reportRow(closing,false));
    closings.forEach(closing->{ closing.remove("source_snapshot"); closing.remove("source"); });
    if(alerts) closings.removeIf(closing->decimal(closing.get("variance")).signum()==0);
    return closings;
  }

  @Transactional(readOnly = true)
  public List<Map<String,Object>> statuses(Identity actor,LocalDate date,UUID branchId) {
    financeAccess.accountant(actor);
    LocalDate selectedDate=date==null?LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")):date;
    if(selectedDate.isAfter(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")))) throw bad("Không thể xem trạng thái báo cáo trong tương lai.");
    return cashClosingRepository.findBranchClosingStatuses(selectedDate,branchId);
  }

  @Transactional(readOnly = true)
  public List<Map<String,Object>> notifications(Identity actor) {
    financeAccess.accountant(actor);
    return cashClosingRepository.findNotifications(actor.id());
  }

  @Transactional(readOnly = true)
  public long countUnreconciled(Identity actor, LocalDate from, LocalDate to, UUID branchId) {
    financeAccess.accountant(actor);
    if (from.isAfter(to) || from.plusYears(2).isBefore(to)) throw bad("Khoảng ngày không hợp lệ, tối đa hai năm.");
    return cashClosingRepository.countUnreconciled(from, to, branchId);
  }

  public Map<String,Object> detail(Identity actor,UUID closingId) {
    var rows=cashClosingRepository.findClosing(closingId);
    if(rows.isEmpty()) throw bad("Không tìm thấy báo cáo chốt quỹ.");
    var closing=rows.get(0);
    if("BRANCH_MANAGER".equals(actor.role())) {
      if(!Objects.equals(closing.get("branch_id"),financeAccess.branch(actor,false)))
        throw new com.gcmb.backend.auth.exception.AuthFailure(403,"form","Bạn không có quyền xem bản chốt quỹ này.");
    } else financeAccess.accountant(actor);
    closing.put("source",objectMapper.readValue(closing.remove("source_snapshot").toString(),Map.class));
    reportRow(closing,true);
    closing.put("reviews",cashClosingRepository.findReviews(closingId));
    closing.put("evidence",cashClosingRepository.findEvidenceNames(closingId));
    closing.put("legacyHandover",cashClosingRepository.findLegacyHandover(closingId));
    return closing;
  }

  // HuyDV: old report snapshots also contain bank data; only cash changes invalidate a cash closing.
  private String cashFingerprint(Map<?,?> source) {
    var movements=new ArrayList<Object>();
    if(source.get("movements") instanceof List<?> rows) for(Object value:rows) {
      var movement=(Map<?,?>)value;
      // PostgreSQL jsonb reorders object keys; compare cash fields in a fixed order.
      movements.add(Arrays.asList(movement.get("id"),movement.get("kind"),movement.get("time"),
        decimal(movement.get("amount")),movement.get("description")));
    }
    return objectMapper.writeValueAsString(List.of(decimal(source.get("opening")),decimal(source.get("cashIn")),
      decimal(source.get("cashOut")),decimal(source.get("expected")),movements));
  }

  public Map<String,Object> reconcile(Identity actor,UUID closingId,Review input) {
    financeAccess.accountant(actor);
    financeAccess.lock();
    var current=detail(actor,closingId);
    if(!"SUBMITTED".equals(current.get("status"))) throw conflict("Chỉ được đối soát báo cáo đang chờ kế toán.");
    LocalDate businessDate=((java.sql.Date)current.get("business_date")).toLocalDate();
    requireOpenPeriod(businessDate);
    if(!input.resolved()) throw bad("Chưa thể xác nhận đã đối soát vì chênh lệch chưa được giải quyết. Vui lòng kiểm tra và ghi nhận kết luận xử lý.");
    if(((Number)current.get("report_version")).intValue()>0) {
      var snapshot=(Map<?,?>)current.get("source");
      var latestSource=source((UUID)current.get("branch_id"),businessDate);
      if(!Objects.equals(cashFingerprint(snapshot),cashFingerprint(latestSource)))
        throw conflict("Có giao dịch phát sinh sau khi gửi báo cáo. Vui lòng trả lại để QLCS cập nhật trước khi đối soát.");
    }
    String conclusion=text(input.conclusion(),"Kết luận đối soát",false);
    if(decimal(current.get("variance")).signum()!=0)
      text((String)current.get("explanation"),"Giải trình chênh lệch",true);
    if(((Number)current.get("report_version")).intValue()>0)
      cashClosingRepository.updateReportState(closingId,"RECONCILED");
    cashClosingRepository.insertReconciliation(closingId,actor.id(),"RECONCILED",conclusion);
    cashClosingRepository.markClosingReconciled(closingId);
    financeAccess.audit(actor,(UUID)current.get("branch_id"),closingId,"RECONCILE_DAILY_TILL_REPORT",current,Map.of("conclusion",conclusion));
    return detail(actor,closingId);
  }

  public Map<String,Object> returnReport(Identity actor,UUID closingId,Review input) {
    financeAccess.accountant(actor);
    financeAccess.lock();
    var current=detail(actor,closingId);
    if(!"SUBMITTED".equals(current.get("status"))) throw conflict("Chỉ được trả lại báo cáo đang chờ đối soát.");
    requireOpenPeriod(((java.sql.Date)current.get("business_date")).toLocalDate());
    String note=text(input.conclusion(),"Nội dung cần bổ sung",false);
    if(((Number)current.get("report_version")).intValue()==0) {
      var payload=new LinkedHashMap<String,Object>();
      for(String key:List.of("opening_amount","cash_in","cash_out","expected_closing","actual_closing","variance","explanation","source"))
        payload.put(key,current.get(key));
      cashClosingRepository.insertLegacyFirstRevision(closingId,objectMapper.writeValueAsString(payload),current.get("submitted_by"));
      cashClosingRepository.insertReport(closingId,1);
    }
    cashClosingRepository.updateReportState(closingId,"NEEDS_EXPLANATION");
    cashClosingRepository.insertReconciliation(closingId,actor.id(),"NEEDS_EXPLANATION",note);
    financeAccess.audit(actor,(UUID)current.get("branch_id"),closingId,"RETURN_DAILY_TILL_REPORT",current,Map.of("note",note));
    return detail(actor,closingId);
  }

  public Map<String,Object> remit(Identity actor,Remittance input) {
    financeAccess.lock();
    UUID branchId=financeAccess.branch(actor,true);
    LocalDate businessDate=businessDate(input.date());
    requireOpenPeriod(businessDate);
    UUID accountId=drawer(branchId);
    if(input.requestId()==null) throw bad("Thiếu mã xác nhận khoản nộp tiền.");
    var duplicates=cashClosingRepository.findRemittanceByIdempotencyKey(input.requestId());
    if(!duplicates.isEmpty()) {
      var existing=duplicates.get(0);
      if(!Objects.equals(existing.get("branch_id"),branchId) || decimal(existing.get("amount")).compareTo(decimal(input.amount()))!=0
        || !Objects.equals(((Timestamp)existing.get("occurred_at")).toInstant().atZone(zone(branchId)).toLocalDate(),businessDate)
        || !Objects.equals(existing.get("reason"),text(input.reason(),"Nội dung nộp tiền / người nhận",true))
        || !Objects.equals(existing.get("reference"),text(input.reference(),"Mã chứng từ",false)))
        throw conflict("Mã xác nhận đã được dùng cho khoản nộp khác.");
      if(input.fullSweep() && !Boolean.TRUE.equals(existing.get("full_sweep")))
        throw conflict("Khoản nộp đã ghi nhận trước đó không phải lần nộp hết két.");
      return existing;
    }
    checkDate(accountId,businessDate);
    if(businessDate.isBefore(LocalDate.now(zone(branchId))) && cashClosingRepository.hasClosingOn(accountId,businessDate))
      throw conflict("Ngày này đã gửi chốt quỹ; không được thêm khoản nộp vào ngày đã chốt.");
    BigDecimal amount=money(input.amount(),"Số tiền nộp khỏi két",true);
    var source=source(branchId,businessDate);
    BigDecimal balance=decimal(source.get("expected"));
    if(input.fullSweep() && amount.compareTo(balance)!=0)
      throw conflict("Số dư két đã thay đổi. Vui lòng tải lại trước khi xác nhận nộp hết tiền.");
    if(amount.compareTo(decimal(source.get("expected")))>0) throw conflict("Số tiền nộp vượt số dư tiền mặt theo sổ.");
    String reason=text(input.reason(),"Nội dung nộp tiền / người nhận",true);
    String reference=text(input.reference(),"Mã chứng từ",false);
    if(reference.length()>200) throw bad("Mã chứng từ tối đa 200 ký tự.");
    UUID remittanceId=UUID.randomUUID();
    Timestamp occurredAt=Timestamp.from(businessDate.atTime(LocalTime.now(zone(branchId))).atZone(zone(branchId)).toInstant());
    cashClosingRepository.insertRemittance(remittanceId,accountId,branchId,occurredAt,amount,reason,reference,actor.id(),
      input.requestId(),balance,amount.compareTo(balance)==0);
    financeAccess.audit(actor,branchId,remittanceId,"RECORD_DRAWER_REMITTANCE",null,Map.of("amount",amount,"reason",reason));
    return cashClosingRepository.getRemittance(remittanceId);
  }

  public Map<String,Object> evidence(Identity actor,UUID closingId,int reportVersion) {
    detail(actor,closingId);
    var rows=reportVersion==0?cashClosingRepository.findOriginalEvidence(closingId)
      :cashClosingRepository.findRevisionEvidence(closingId,reportVersion);
    if(rows.isEmpty()) throw bad("Không tìm thấy minh chứng.");
    var evidence=rows.get(0);
    try { evidence.put("bytes",refundEvidenceStorageService.read((String)evidence.get("object_key"))); }
    catch(java.io.IOException exception) { throw conflict("Không thể đọc minh chứng. Vui lòng thử lại."); }
    return evidence;
  }
}
