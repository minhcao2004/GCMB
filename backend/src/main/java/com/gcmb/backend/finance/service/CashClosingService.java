/** Author: HuyDV. UC-98..101 / BR-95..98/208: cash-only daily closing and accountant reconciliation. */
package com.gcmb.backend.finance.service;

import static com.gcmb.backend.finance.service.FinanceAccess.*;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.booking.service.RefundEvidenceStorageService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
public class CashClosingService {
  private final JdbcTemplate db;
  private final FinanceAccess access;
  private final ObjectMapper json;
  private final RefundEvidenceStorageService storage;

  public CashClosingService(JdbcTemplate db, FinanceAccess access, ObjectMapper json, RefundEvidenceStorageService storage) {
    this.db = db;
    this.access = access;
    this.json = json;
    this.storage = storage;
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

  private LocalDate day(LocalDate value) {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    if (value == null) return today;
    if (value.isAfter(today) || value.getYear() < 2000)
      throw bad("Ngày chốt quỹ không hợp lệ hoặc ở tương lai.");
    return value;
  }

  private UUID drawer(UUID branch) {
    var accounts = db.queryForList(
      "SELECT id FROM gcmb.financial_account WHERE branch_id=? AND kind='CASH_DRAWER' AND active",
      UUID.class, branch);
    if (accounts.size() != 1)
      throw conflict("Cần cấu hình đúng một két tiền mặt đang hoạt động cho cơ sở trước khi chốt quỹ.");
    return accounts.get(0);
  }

  private ZoneId zone(UUID branch) {
    return ZoneId.of(db.queryForObject("SELECT timezone FROM gcmb.branch WHERE id=?", String.class, branch));
  }

  private void checkDate(UUID account, LocalDate date) {
    if (Boolean.TRUE.equals(db.queryForObject(
      "SELECT EXISTS(SELECT 1 FROM gcmb.daily_cash_closing WHERE account_id=? AND business_date>?)",
      Boolean.class, account, date)))
      throw conflict("Không được chốt lùi trước ngày đã có báo cáo chốt quỹ mới hơn.");
  }

  private void requireOpenPeriod(LocalDate date) {
    if(Boolean.TRUE.equals(db.queryForObject(
      "SELECT EXISTS(SELECT 1 FROM gcmb.financial_period WHERE month=? AND status='CLOSED')",
      Boolean.class,date.withDayOfMonth(1))))
      throw conflict("Kỳ tài chính của ngày này đã khóa. Không được thay đổi báo cáo hoặc số dư két.");
  }

  public Map<String,Object> preview(Identity actor, LocalDate value) {
    UUID branch = access.branch(actor, false);
    LocalDate date = day(value);
    var result = source(branch, date);
    java.sql.Date latest = db.queryForObject(
      "SELECT max(business_date) FROM gcmb.daily_cash_closing WHERE account_id=?",
      java.sql.Date.class, result.get("accountId"));
    result.put("closingBlocked", latest != null && date.isBefore(latest.toLocalDate()));
    result.put("lastClosingDate", latest == null ? null : latest.toString());
    var current=source(branch,LocalDate.now(zone(branch)));
    result.put("currentBalance",current.get("expected"));
    result.put("cashCycle",cashCycle((UUID)result.get("accountId"),zone(branch),current));
    return result;
  }

  private Map<String,Object> source(UUID branch, LocalDate date) {
    UUID account = drawer(branch);
    ZoneId zone = zone(branch);
    Timestamp end = Timestamp.from(date.plusDays(1).atStartOfDay(zone).toInstant());
    // BR-95: cash receipts not yet posted to the ledger are included exactly once.
    // V44 receipt movements remain historical; no new report creates a handover.
    var all = db.queryForList("""
      SELECT e.id::text AS id,t.occurred_at AS time,e.amount_signed AS amount,
        CASE WHEN p.purpose='DEPOSIT' THEN 'DEPOSIT' WHEN p.purpose='BILL' THEN 'CHECKOUT' ELSE t.kind END AS kind,
        t.reason AS description
      FROM gcmb.financial_entry e JOIN gcmb.financial_transaction t ON t.id=e.transaction_id
      LEFT JOIN gcmb.payment p ON p.id=t.payment_id WHERE e.account_id=? AND t.occurred_at<?
      UNION ALL
      SELECT p.id::text,p.received_at,p.amount,
        CASE WHEN p.purpose='DEPOSIT' THEN 'DEPOSIT' ELSE 'CHECKOUT' END,'Thu tiền mặt '||b.code
      FROM gcmb.payment p JOIN gcmb.booking b ON b.id=p.booking_id
      WHERE p.branch_id=? AND p.method='CASH' AND p.received_at<?
        AND NOT EXISTS(SELECT 1 FROM gcmb.financial_transaction t
          JOIN gcmb.financial_entry e ON e.transaction_id=t.id WHERE t.payment_id=p.id)
      UNION ALL SELECT id,time,amount,kind,description FROM gcmb.cash_handover_movement WHERE account_id=? AND time<?
      UNION ALL SELECT id,time,amount,kind,description FROM gcmb.daily_till_movement WHERE account_id=? AND time<?
      UNION ALL SELECT id,time,amount,kind,description FROM gcmb.drawer_funding_movement WHERE account_id=? AND time<?
      ORDER BY time,id
      """, account,end,branch,end,account,end,account,end,account,end);
    BigDecimal opening=BigDecimal.ZERO, incoming=BigDecimal.ZERO, outgoing=BigDecimal.ZERO;
    var movements = new ArrayList<Map<String,Object>>();
    for (var row : all) {
      BigDecimal amount = decimal(row.get("amount"));
      LocalDate workDay = ((Timestamp)row.get("time")).toInstant().atZone(zone).toLocalDate();
      if (workDay.isBefore(date)) opening=opening.add(amount);
      else {
        movements.add(row);
        if (amount.signum()>0) incoming=incoming.add(amount);
        else outgoing=outgoing.subtract(amount);
      }
    }
    var result = new LinkedHashMap<String,Object>();
    result.put("accountId",account);
    result.put("date",date.toString());
    result.put("opening",opening);
    result.put("cashIn",incoming);
    result.put("cashOut",outgoing);
    result.put("expected",opening.add(incoming).subtract(outgoing));
    result.put("movements",movements);
    result.put("firstCashAt",all.stream().filter(r->decimal(r.get("amount")).signum()>0
      && !"COUNT_VARIANCE".equals(r.get("kind"))).map(r->r.get("time")).findFirst().orElse(null));
    result.put("remitted",movements.stream().filter(r->"REMITTANCE".equals(r.get("kind")))
      .map(r->decimal(r.get("amount")).negate()).reduce(BigDecimal.ZERO,BigDecimal::add));
    result.put("cashExpense",movements.stream().filter(r->decimal(r.get("amount")).signum()<0
      && !Set.of("REMITTANCE","COUNT_VARIANCE").contains(String.valueOf(r.get("kind"))))
      .map(r->decimal(r.get("amount")).negate()).reduce(BigDecimal.ZERO,BigDecimal::add));
    result.put("countAdjustment",movements.stream().filter(r->"COUNT_VARIANCE".equals(r.get("kind")))
      .map(r->decimal(r.get("amount"))).reduce(BigDecimal.ZERO,BigDecimal::add));
    result.put("drawerFunding",movements.stream().filter(r->"DRAWER_FUNDING".equals(r.get("kind")))
      .map(r->decimal(r.get("amount"))).reduce(BigDecimal.ZERO,BigDecimal::add));
    result.put("cashReceipts",movements.stream().filter(r->decimal(r.get("amount")).signum()>0
      && !Set.of("COUNT_VARIANCE","DRAWER_FUNDING").contains(String.valueOf(r.get("kind"))))
      .map(r->decimal(r.get("amount"))).reduce(BigDecimal.ZERO,BigDecimal::add));
    result.put("scope","CASH_ONLY");
    result.put("revision",UUID.nameUUIDFromBytes(json.writeValueAsBytes(all)).toString());
    result.put("existing",db.queryForList("""
      SELECT c.id,coalesce(r.state,c.status) AS status,coalesce(r.revision,0) AS report_version
      FROM gcmb.daily_cash_closing c LEFT JOIN gcmb.cash_closing_report r ON r.closing_id=c.id
      WHERE c.account_id=? AND c.business_date=?
      """,account,date));
    return result;
  }

  // HuyDV: five calendar days is a reminder, never an automatic withdrawal.
  private Map<String,Object> cashCycle(UUID account,ZoneId zone,Map<String,Object> current) {
    Timestamp last=db.queryForObject("""
      SELECT max(time) FROM (
        SELECT occurred_at AS time FROM gcmb.cash_drawer_remittance WHERE account_id=? AND full_sweep
        UNION ALL SELECT received_at FROM gcmb.cash_closing_handover WHERE account_id=? AND received_at IS NOT NULL
      ) swept
      """,Timestamp.class,account,account);
    Timestamp anchor=db.queryForObject("""
      SELECT min(time) FROM (
        SELECT t.occurred_at AS time FROM gcmb.financial_entry e JOIN gcmb.financial_transaction t ON t.id=e.transaction_id
        WHERE e.account_id=? AND e.amount_signed>0 AND t.occurred_at>coalesce(CAST(? AS timestamptz),'-infinity'::timestamptz)
        UNION ALL SELECT p.received_at FROM gcmb.payment p
        WHERE p.branch_id=(SELECT branch_id FROM gcmb.financial_account WHERE id=?) AND p.method='CASH'
          AND p.received_at>coalesce(CAST(? AS timestamptz),'-infinity'::timestamptz)
          AND NOT EXISTS(SELECT 1 FROM gcmb.financial_transaction t JOIN gcmb.financial_entry e ON e.transaction_id=t.id WHERE t.payment_id=p.id)
        UNION ALL SELECT time FROM gcmb.drawer_funding_movement
        WHERE account_id=? AND time>coalesce(CAST(? AS timestamptz),'-infinity'::timestamptz)
      ) receipts WHERE time<=clock_timestamp()
      """,Timestamp.class,account,last,account,last,account,last);
    // Empty days before cash arrives do not consume the next five-day holding cycle.
    if(anchor==null && last==null) anchor=(Timestamp)current.get("firstCashAt");
    if(anchor==null && last!=null && decimal(current.get("expected")).signum()>0) anchor=last;
    var cycle=new LinkedHashMap<String,Object>();
    LocalDate started=anchor==null?null:anchor.toInstant().atZone(zone).toLocalDate();
    long elapsed=started==null?0:Math.max(0,java.time.temporal.ChronoUnit.DAYS.between(started,LocalDate.now(zone)));
    cycle.put("lastFullSweepAt",last);
    cycle.put("startedOn",started==null?null:started.toString());
    cycle.put("dueOn",started==null?null:started.plusDays(5).toString());
    cycle.put("elapsedDays",elapsed);
    cycle.put("due",elapsed>=5 && decimal(current.get("expected")).signum()>0);
    return cycle;
  }

  @SuppressWarnings("unchecked")
  public Map<String,Object> submit(Identity actor, Submit input, MultipartFile evidence) {
    access.lock();
    UUID branch=access.branch(actor,true);
    LocalDate date=day(input.date());
    requireOpenPeriod(date);
    var preview=source(branch,date);
    if(!Objects.equals(input.revision(),preview.get("revision")))
      throw conflict("Giao dịch nguồn đã thay đổi. Vui lòng tải lại và kiểm tra trước khi gửi báo cáo.");
    var existing=(List<Map<String,Object>>)preview.get("existing");
    UUID id=existing.isEmpty()?UUID.randomUUID():(UUID)existing.get(0).get("id");
    int version=existing.isEmpty()?0:((Number)existing.get(0).get("report_version")).intValue();
    if(!existing.isEmpty() && !"NEEDS_EXPLANATION".equals(existing.get(0).get("status")))
      throw conflict("Ngày này đã gửi báo cáo. Chỉ được bổ sung khi kế toán yêu cầu.");
    if(!existing.isEmpty() && !Objects.equals(input.reportVersion(),version))
      throw conflict("Phiên bản báo cáo đã thay đổi. Vui lòng tải lại.");
    if(existing.isEmpty()) checkDate((UUID)preview.get("accountId"),date);
    BigDecimal actual=money(input.actual(),"Tiền mặt thực đếm",false);
    BigDecimal expected=decimal(preview.get("expected"));
    BigDecimal variance=actual.subtract(expected);
    String explanation=text(input.explanation(),"Giải trình chênh lệch",
      variance.signum()!=0);
    if(decimal(preview.get("opening")).signum()<0 || expected.signum()<0)
      throw conflict("Số dư tiền mặt âm. Cần kiểm tra giao dịch nguồn trước khi chốt quỹ.");
    if(existing.isEmpty()) db.update("""
      INSERT INTO gcmb.daily_cash_closing(id,branch_id,account_id,business_date,opening_amount,cash_in,cash_out,
        expected_closing,actual_closing,variance,explanation,status,submitted_by,submitted_at,source_snapshot)
      VALUES (?,?,?,?,?,?,?,?,?,?,?,'SUBMITTED',?,CURRENT_TIMESTAMP,?::jsonb)
      """,id,branch,preview.get("accountId"),date,preview.get("opening"),preview.get("cashIn"),
      preview.get("cashOut"),expected,actual,variance,explanation,actor.id(),json.writeValueAsString(preview));
    int next=version+1;
    var payload=new LinkedHashMap<String,Object>();
    payload.put("opening_amount",preview.get("opening"));
    payload.put("cash_in",preview.get("cashIn"));
    payload.put("cash_out",preview.get("cashOut"));
    payload.put("expected_closing",expected);
    payload.put("actual_closing",actual);
    payload.put("variance",variance);
    payload.put("scope","CASH_ONLY");
    payload.put("explanation",explanation);
    payload.put("source",preview);
    db.update("INSERT INTO gcmb.cash_closing_report_revision(closing_id,revision,payload,submitted_by) VALUES (?,?,?::jsonb,?)",
      id,next,json.writeValueAsString(payload),actor.id());
    if(version==0) db.update("INSERT INTO gcmb.cash_closing_report VALUES (?,?,'SUBMITTED')",id,next);
    else db.update("UPDATE gcmb.cash_closing_report SET revision=?,state='SUBMITTED' WHERE closing_id=?",next,id);
    if(evidence!=null && !evidence.isEmpty()) storeEvidence(id,next,evidence);
    access.audit(actor,branch,id,version==0?"SUBMIT_DAILY_TILL_REPORT":"RESUBMIT_DAILY_TILL_REPORT",null,payload);
    return detail(actor,id);
  }

  private void storeEvidence(UUID id,int version,MultipartFile evidence) {
    var stored=storage.store(evidence);
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override public void afterCompletion(int status) {
        if(status!=STATUS_COMMITTED) storage.deleteQuietly(stored.objectKey());
      }
    });
    db.update("INSERT INTO gcmb.cash_closing_revision_evidence VALUES (?,?,?,?,?)",
      id,version,stored.originalName(),stored.mimeType(),stored.objectKey());
  }

  @SuppressWarnings("unchecked")
  private void reportRow(Map<String,Object> row, boolean history) {
    var report=db.queryForList("""
      SELECT r.state,r.revision,v.payload,v.submitted_at,u.username
      FROM gcmb.cash_closing_report r JOIN gcmb.cash_closing_report_revision v
        ON v.closing_id=r.closing_id AND v.revision=r.revision
      JOIN gcmb.user_account u ON u.id=v.submitted_by WHERE r.closing_id=?
      """,row.get("id"));
    row.put("report_version",0);
    if(!report.isEmpty()) {
      var current=report.get(0);
      var payload=json.readValue(current.get("payload").toString(),Map.class);
      row.putAll(payload);
      // Normalize JSON numbers so review validation also works for small/zero amounts.
      for(String key:List.of("opening_amount","cash_in","cash_out","expected_closing","actual_closing","variance","actual_bank","bank_variance"))
        if(row.containsKey(key) && row.get(key)!=null) row.put(key,decimal(row.get(key)));
      row.put("report_version",current.get("revision"));
      row.put("status",current.get("state"));
      row.put("submitted_at",current.get("submitted_at"));
      row.put("submittedBy",current.get("username"));
    }
    if(history) row.put("versions",db.queryForList("""
      SELECT v.revision,v.submitted_at,u.username,v.payload FROM gcmb.cash_closing_report_revision v
      JOIN gcmb.user_account u ON u.id=v.submitted_by WHERE v.closing_id=? ORDER BY v.revision
      """,row.get("id")).stream().map(v -> {
        v.put("payload",json.readValue(v.get("payload").toString(),Map.class));
        return v;
      }).toList());
  }

  public List<Map<String,Object>> list(Identity actor,LocalDate from,LocalDate to,UUID branch,String status,boolean alerts) {
    if("BRANCH_MANAGER".equals(actor.role())) branch=access.branch(actor,false);
    else access.accountant(actor);
    LocalDate until=to==null?LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")):to;
    LocalDate start=from==null?until.withDayOfMonth(1):from;
    if(start.isAfter(until)||start.plusYears(2).isBefore(until)) throw bad("Khoảng ngày không hợp lệ, tối đa hai năm.");
    var rows=db.queryForList("""
      SELECT c.*,b.name AS "branchName",u.username AS "submittedBy"
      FROM gcmb.daily_cash_closing c JOIN gcmb.branch b ON b.id=c.branch_id
      JOIN gcmb.user_account u ON u.id=c.submitted_by
      LEFT JOIN gcmb.cash_closing_report r ON r.closing_id=c.id
      WHERE (CAST(? AS uuid) IS NULL OR c.branch_id=?) AND c.business_date BETWEEN ? AND ?
        AND (?='' OR coalesce(r.state,c.status)=?)
      ORDER BY c.business_date DESC,b.name
      """,branch,branch,start,until,status==null?"":status,status==null?"":status);
    rows.forEach(row->reportRow(row,false));
    rows.forEach(row->{ row.remove("source_snapshot"); row.remove("source"); });
    if(alerts) rows.removeIf(row->decimal(row.get("variance")).signum()==0);
    return rows;
  }

  public Map<String,Object> detail(Identity actor,UUID id) {
    var rows=db.queryForList("""
      SELECT c.*,b.name AS "branchName",u.username AS "submittedBy"
      FROM gcmb.daily_cash_closing c JOIN gcmb.branch b ON b.id=c.branch_id
      JOIN gcmb.user_account u ON u.id=c.submitted_by WHERE c.id=?
      """,id);
    if(rows.isEmpty()) throw bad("Không tìm thấy báo cáo chốt quỹ.");
    var row=rows.get(0);
    if("BRANCH_MANAGER".equals(actor.role())) {
      if(!Objects.equals(row.get("branch_id"),access.branch(actor,false)))
        throw new com.gcmb.backend.auth.exception.AuthFailure(403,"form","Bạn không có quyền xem bản chốt quỹ này.");
    } else access.accountant(actor);
    row.put("source",json.readValue(row.remove("source_snapshot").toString(),Map.class));
    reportRow(row,true);
    row.put("reviews",db.queryForList("""
      SELECT r.*,u.username FROM gcmb.cash_reconciliation r JOIN gcmb.user_account u ON u.id=r.reviewed_by
      WHERE closing_id=? ORDER BY created_at,id
      """,id));
    row.put("evidence",db.queryForList("""
      SELECT original_name AS name,0 AS revision FROM gcmb.cash_closing_evidence WHERE closing_id=?
      UNION ALL SELECT original_name,revision FROM gcmb.cash_closing_revision_evidence WHERE closing_id=? ORDER BY revision
      """,id,id));
    row.put("legacyHandover",db.queryForList("""
      SELECT h.received_at,c.actual_closing AS amount,u.username FROM gcmb.cash_closing_handover h
      JOIN gcmb.daily_cash_closing c ON c.id=h.closing_id
      LEFT JOIN gcmb.user_account u ON u.id=h.received_by WHERE h.closing_id=? AND h.received_at IS NOT NULL
      """,id));
    return row;
  }

  // HuyDV: old report snapshots also contain bank data; only cash changes invalidate a cash closing.
  private String cashFingerprint(Map<?,?> source) {
    var movements=new ArrayList<Object>();
    if(source.get("movements") instanceof List<?> rows) for(Object value:rows) {
      var row=(Map<?,?>)value;
      // PostgreSQL jsonb reorders object keys; compare cash fields in a fixed order.
      movements.add(Arrays.asList(row.get("id"),row.get("kind"),row.get("time"),
        decimal(row.get("amount")),row.get("description")));
    }
    return json.writeValueAsString(List.of(decimal(source.get("opening")),decimal(source.get("cashIn")),
      decimal(source.get("cashOut")),decimal(source.get("expected")),movements));
  }

  public Map<String,Object> reconcile(Identity actor,UUID id,Review input) {
    access.accountant(actor);
    access.lock();
    var old=detail(actor,id);
    if(!"SUBMITTED".equals(old.get("status"))) throw conflict("Chỉ được đối soát báo cáo đang chờ kế toán.");
    requireOpenPeriod(((java.sql.Date)old.get("business_date")).toLocalDate());
    if(!input.resolved()) throw bad("Chưa thể xác nhận đã đối soát vì chênh lệch chưa được giải quyết. Vui lòng kiểm tra và ghi nhận kết luận xử lý.");
    if(((Number)old.get("report_version")).intValue()>0) {
      var snapshot=(Map<?,?>)old.get("source");
      var current=source((UUID)old.get("branch_id"),((java.sql.Date)old.get("business_date")).toLocalDate());
      if(!Objects.equals(cashFingerprint(snapshot),cashFingerprint(current)))
        throw conflict("Có giao dịch phát sinh sau khi gửi báo cáo. Vui lòng trả lại để QLCS cập nhật trước khi đối soát.");
    }
    String conclusion=text(input.conclusion(),"Kết luận đối soát",false);
    if(decimal(old.get("variance")).signum()!=0)
      text((String)old.get("explanation"),"Giải trình chênh lệch",true);
    if(((Number)old.get("report_version")).intValue()>0)
      db.update("UPDATE gcmb.cash_closing_report SET state='RECONCILED' WHERE closing_id=?",id);
    db.update("INSERT INTO gcmb.cash_reconciliation(closing_id,reviewed_by,decision,note) VALUES (?,?,'RECONCILED',?)",
      id,actor.id(),conclusion);
    db.update("UPDATE gcmb.daily_cash_closing SET status='RECONCILED' WHERE id=?",id);
    access.audit(actor,(UUID)old.get("branch_id"),id,"RECONCILE_DAILY_TILL_REPORT",old,Map.of("conclusion",conclusion));
    return detail(actor,id);
  }

  public Map<String,Object> returnReport(Identity actor,UUID id,Review input) {
    access.accountant(actor);
    access.lock();
    var old=detail(actor,id);
    if(!"SUBMITTED".equals(old.get("status"))) throw conflict("Chỉ được trả lại báo cáo đang chờ đối soát.");
    requireOpenPeriod(((java.sql.Date)old.get("business_date")).toLocalDate());
    String note=text(input.conclusion(),"Nội dung cần bổ sung",false);
    if(((Number)old.get("report_version")).intValue()==0) {
      var payload=new LinkedHashMap<String,Object>();
      for(String key:List.of("opening_amount","cash_in","cash_out","expected_closing","actual_closing","variance","explanation","source"))
        payload.put(key,old.get(key));
      db.update("INSERT INTO gcmb.cash_closing_report_revision VALUES (?,1,?::jsonb,?,CURRENT_TIMESTAMP)",
        id,json.writeValueAsString(payload),old.get("submitted_by"));
      db.update("INSERT INTO gcmb.cash_closing_report VALUES (?,1,'SUBMITTED')",id);
    }
    db.update("UPDATE gcmb.cash_closing_report SET state='NEEDS_EXPLANATION' WHERE closing_id=?",id);
    db.update("INSERT INTO gcmb.cash_reconciliation(closing_id,reviewed_by,decision,note) VALUES (?,?,'NEEDS_EXPLANATION',?)",
      id,actor.id(),note);
    access.audit(actor,(UUID)old.get("branch_id"),id,"RETURN_DAILY_TILL_REPORT",old,Map.of("note",note));
    return detail(actor,id);
  }

  public Map<String,Object> remit(Identity actor,Remittance input) {
    access.lock();
    UUID branch=access.branch(actor,true);
    LocalDate date=day(input.date());
    requireOpenPeriod(date);
    UUID account=drawer(branch);
    if(input.requestId()==null) throw bad("Thiếu mã xác nhận khoản nộp tiền.");
    var duplicate=db.queryForList("SELECT * FROM gcmb.cash_drawer_remittance WHERE idempotency_key=?",input.requestId());
    if(!duplicate.isEmpty()) {
      var old=duplicate.get(0);
      if(!Objects.equals(old.get("branch_id"),branch) || decimal(old.get("amount")).compareTo(decimal(input.amount()))!=0
        || !Objects.equals(((Timestamp)old.get("occurred_at")).toInstant().atZone(zone(branch)).toLocalDate(),date)
        || !Objects.equals(old.get("reason"),text(input.reason(),"Nội dung nộp tiền / người nhận",true))
        || !Objects.equals(old.get("reference"),text(input.reference(),"Mã chứng từ",false)))
        throw conflict("Mã xác nhận đã được dùng cho khoản nộp khác.");
      if(input.fullSweep() && !Boolean.TRUE.equals(old.get("full_sweep")))
        throw conflict("Khoản nộp đã ghi nhận trước đó không phải lần nộp hết két.");
      return old;
    }
    checkDate(account,date);
    if(date.isBefore(LocalDate.now(zone(branch))) && Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM gcmb.daily_cash_closing WHERE account_id=? AND business_date=?)",
      Boolean.class,account,date))) throw conflict("Ngày này đã gửi chốt quỹ; không được thêm khoản nộp vào ngày đã chốt.");
    BigDecimal amount=money(input.amount(),"Số tiền nộp khỏi két",true);
    var source=source(branch,date);
    BigDecimal balance=decimal(source.get("expected"));
    if(input.fullSweep() && amount.compareTo(balance)!=0)
      throw conflict("Số dư két đã thay đổi. Vui lòng tải lại trước khi xác nhận nộp hết tiền.");
    if(amount.compareTo(decimal(source.get("expected")))>0) throw conflict("Số tiền nộp vượt số dư tiền mặt theo sổ.");
    String reason=text(input.reason(),"Nội dung nộp tiền / người nhận",true);
    String reference=text(input.reference(),"Mã chứng từ",false);
    if(reference.length()>200) throw bad("Mã chứng từ tối đa 200 ký tự.");
    UUID id=UUID.randomUUID();
    Timestamp time=Timestamp.from(date.atTime(LocalTime.now(zone(branch))).atZone(zone(branch)).toInstant());
    db.update("INSERT INTO gcmb.cash_drawer_remittance(id,account_id,branch_id,occurred_at,amount,reason,reference,recorded_by,idempotency_key,balance_before,full_sweep) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
      id,account,branch,time,amount,reason,reference,actor.id(),input.requestId(),balance,amount.compareTo(balance)==0);
    access.audit(actor,branch,id,"RECORD_DRAWER_REMITTANCE",null,Map.of("amount",amount,"reason",reason));
    return db.queryForMap("SELECT * FROM gcmb.cash_drawer_remittance WHERE id=?",id);
  }

  public Map<String,Object> evidence(Identity actor,UUID id,int version) {
    detail(actor,id);
    var rows=version==0?db.queryForList("SELECT * FROM gcmb.cash_closing_evidence WHERE closing_id=?",id)
      :db.queryForList("SELECT * FROM gcmb.cash_closing_revision_evidence WHERE closing_id=? AND revision=?",id,version);
    if(rows.isEmpty()) throw bad("Không tìm thấy minh chứng.");
    var row=rows.get(0);
    try { row.put("bytes",storage.read((String)row.get("object_key"))); }
    catch(java.io.IOException e) { throw conflict("Không thể đọc minh chứng. Vui lòng thử lại."); }
    return row;
  }
}
