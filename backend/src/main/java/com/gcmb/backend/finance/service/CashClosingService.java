/** Author: HuyDV. UC-98..101 / BR-95..98/208: chốt quỹ và đối soát giữ lịch sử. */
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

  public CashClosingService(
    JdbcTemplate db,
    FinanceAccess access,
    ObjectMapper json,
    RefundEvidenceStorageService storage
  ) {
    this.db = db;
    this.access = access;
    this.json = json;
    this.storage = storage;
  }

  public record Submit(LocalDate date, BigDecimal actual, String explanation, String revision) {}

  public record Review(boolean resolved, String conclusion) {}

  public Map<String, Object> preview(Identity actor, LocalDate date) {
    UUID branch = access.branch(actor, false);
    return source(branch, day(date));
  }

  private LocalDate day(LocalDate date) {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    if (date == null) return today;
    if (date.isAfter(today) || date.getYear() < 2000) throw bad(
      "Ngày chốt quỹ không hợp lệ hoặc ở tương lai."
    );
    return date;
  }

  private UUID drawer(UUID branch) {
    var accounts = db.queryForList(
      "SELECT id FROM gcmb.financial_account WHERE branch_id=? AND kind='CASH_DRAWER' AND active ORDER BY code",
      UUID.class,
      branch
    );
    if (accounts.size() != 1) throw conflict(
      "Cần cấu hình đúng một két tiền mặt đang hoạt động cho cơ sở trước khi chốt quỹ."
    );
    return accounts.get(0);
  }

  private Map<String, Object> source(UUID branch, LocalDate date) {
    UUID account = drawer(branch);
    String zone = db.queryForObject(
      "SELECT timezone FROM gcmb.branch WHERE id=?",
      String.class,
      branch
    );
    Instant end = date.plusDays(1).atStartOfDay(ZoneId.of(zone)).toInstant();
    // BR-95: union receipts not yet posted to the ledger; never count the same payment twice.
    // Deposit APPLY is not a cash movement and is deliberately absent.
    var all = db.queryForList(
      """
      SELECT e.id::text AS id,t.occurred_at AS time,e.amount_signed AS amount,
        CASE WHEN p.purpose='DEPOSIT' THEN 'DEPOSIT' WHEN p.purpose='BILL' THEN 'CHECKOUT' ELSE t.kind END AS kind,
        t.reason AS description
      FROM gcmb.financial_entry e JOIN gcmb.financial_transaction t ON t.id=e.transaction_id
      LEFT JOIN gcmb.payment p ON p.id=t.payment_id
      WHERE e.account_id=? AND t.occurred_at<?
      UNION ALL
      SELECT p.id::text,p.received_at,p.amount,CASE WHEN p.purpose='DEPOSIT' THEN 'DEPOSIT' ELSE 'CHECKOUT' END,
        'Thu tiền mặt '||b.code
      FROM gcmb.payment p JOIN gcmb.booking b ON b.id=p.booking_id
      WHERE p.branch_id=? AND p.method='CASH' AND p.received_at<?
        AND NOT EXISTS(SELECT 1 FROM gcmb.financial_transaction t JOIN gcmb.financial_entry e ON e.transaction_id=t.id WHERE t.payment_id=p.id)
      ORDER BY time,id
      """,
      account,
      Timestamp.from(end),
      branch,
      Timestamp.from(end)
    );
    BigDecimal opening = BigDecimal.ZERO,
      incoming = BigDecimal.ZERO,
      outgoing = BigDecimal.ZERO;
    var movements = new ArrayList<Map<String, Object>>();
    for (var row : all) {
      BigDecimal amount = (BigDecimal) row.get("amount");
      LocalDate workDay = ((Timestamp) row.get("time"))
        .toInstant()
        .atZone(ZoneId.of(zone))
        .toLocalDate();
      if (workDay.isBefore(date)) opening = opening.add(amount);
      else {
        movements.add(row);
        if (amount.signum() > 0) incoming = incoming.add(amount);
        else outgoing = outgoing.subtract(amount);
      }
    }
    var result = new LinkedHashMap<String, Object>();
    result.put("accountId", account);
    result.put("date", date.toString());
    result.put("opening", opening);
    result.put("cashIn", incoming);
    result.put("cashOut", outgoing);
    result.put("expected", opening.add(incoming).subtract(outgoing));
    result.put("movements", movements);
    result.put("revision", UUID.nameUUIDFromBytes(json.writeValueAsBytes(all)).toString());
    result.put(
      "existing",
      db.queryForList(
        "SELECT id,status FROM gcmb.daily_cash_closing WHERE account_id=? AND business_date=?",
        account,
        date
      )
    );
    return result;
  }

  public Map<String, Object> submit(Identity actor, Submit input, MultipartFile evidence) {
    access.lock();
    UUID branch = access.branch(actor, true);
    LocalDate date = day(input.date());
    BigDecimal actual = money(input.actual(), "Tiền mặt đếm thực tế", false);
    var preview = source(branch, date);
    if (!Objects.equals(input.revision(), preview.get("revision"))) throw conflict(
      "Giao dịch nguồn đã thay đổi. Vui lòng tải lại và kiểm tra trước khi chốt quỹ."
    );
    if (!((List<?>) preview.get("existing")).isEmpty()) throw conflict(
      "Ngày này đã được chốt quỹ. Vui lòng xem lịch sử chốt quỹ."
    );
    BigDecimal expected = (BigDecimal) preview.get("expected"),
      variance = actual.subtract(expected);
    String explanation = text(input.explanation(), "Giải trình chênh lệch", variance.signum() != 0);
    if (((BigDecimal) preview.get("opening")).signum() < 0) throw conflict(
      "Số dư đầu ngày âm. Cần kiểm tra giao dịch nguồn trước khi chốt quỹ."
    );
    UUID id = UUID.randomUUID();
    db.update(
      """
      INSERT INTO gcmb.daily_cash_closing(id,branch_id,account_id,business_date,opening_amount,cash_in,cash_out,expected_closing,actual_closing,variance,explanation,status,submitted_by,submitted_at,source_snapshot)
      VALUES (?,?,?,?,?,?,?,?,?,?,?,'SUBMITTED',?,CURRENT_TIMESTAMP,CAST(? AS jsonb))
      """,
      id,
      branch,
      preview.get("accountId"),
      date,
      preview.get("opening"),
      preview.get("cashIn"),
      preview.get("cashOut"),
      expected,
      actual,
      variance,
      explanation,
      actor.id(),
      json.writeValueAsString(preview)
    );
    if (evidence != null) {
      var stored = storage.store(evidence); // BR-115/116: reuse existing signature/size checks.
      TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED) storage.deleteQuietly(stored.objectKey());
          }
        }
      );
      db.update(
        "INSERT INTO gcmb.cash_closing_evidence VALUES (?,?,?,?)",
        id,
        stored.originalName(),
        stored.mimeType(),
        stored.objectKey()
      );
    }
    access.audit(actor, branch, id, "SUBMIT_CASH_CLOSING", null, preview);
    return detail(actor, id);
  }

  public List<Map<String, Object>> list(
    Identity actor,
    LocalDate from,
    LocalDate to,
    UUID branch,
    String status,
    boolean alerts
  ) {
    if ("BRANCH_MANAGER".equals(actor.role())) branch = access.branch(actor, false);
    else access.accountant(actor);
    LocalDate until = to == null ? LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")) : to;
    LocalDate start = from == null ? until.withDayOfMonth(1) : from;
    if (start.isAfter(until) || start.plusYears(2).isBefore(until)) throw bad(
      "Khoảng ngày không hợp lệ, tối đa hai năm."
    );
    return db.queryForList(
      """
      SELECT c.*,b.name AS "branchName",u.username AS "submittedBy"
      FROM gcmb.daily_cash_closing c JOIN gcmb.branch b ON b.id=c.branch_id
      JOIN gcmb.user_account u ON u.id=c.submitted_by
      WHERE (CAST(? AS uuid) IS NULL OR c.branch_id=?) AND business_date BETWEEN ? AND ?
        AND (?='' OR c.status=?) AND (NOT ? OR c.variance<>0)
      ORDER BY c.business_date DESC,b.name
      """,
      branch,
      branch,
      start,
      until,
      status == null ? "" : status,
      status,
      alerts
    );
  }

  public Map<String, Object> detail(Identity actor, UUID id) {
    var rows = db.queryForList(
      "SELECT c.*,b.name AS \"branchName\",u.username AS \"submittedBy\" FROM gcmb.daily_cash_closing c JOIN gcmb.branch b ON b.id=c.branch_id JOIN gcmb.user_account u ON u.id=c.submitted_by WHERE c.id=?",
      id
    );
    if (rows.isEmpty()) throw bad("Không tìm thấy bản chốt quỹ.");
    var row = rows.get(0);
    if ("BRANCH_MANAGER".equals(actor.role())) {
      if (
        !Objects.equals(row.get("branch_id"), access.branch(actor, false))
      ) throw new com.gcmb.backend.auth.exception.AuthFailure(
        403,
        "form",
        "Bạn không có quyền xem bản chốt quỹ này."
      );
    } else access.accountant(actor);
    row.put("source", json.readValue(row.remove("source_snapshot").toString(), Map.class));
    row.put(
      "reviews",
      db.queryForList(
        "SELECT r.*,u.username FROM gcmb.cash_reconciliation r JOIN gcmb.user_account u ON u.id=r.reviewed_by WHERE closing_id=? ORDER BY created_at",
        id
      )
    );
    row.put(
      "evidence",
      db.queryForList(
        "SELECT original_name AS name FROM gcmb.cash_closing_evidence WHERE closing_id=?",
        id
      )
    );
    return row;
  }

  public Map<String, Object> reconcile(Identity actor, UUID id, Review input) {
    access.accountant(actor);
    access.lock();
    var old = detail(actor, id);
    if ("RECONCILED".equals(old.get("status"))) throw conflict("Bản chốt quỹ đã được đối soát.");
    if (!input.resolved()) throw bad(
      "Cần xác nhận đã kiểm tra và xử lý xong chênh lệch trước khi đối soát."
    ); // BR-98.
    if (((BigDecimal) old.get("variance")).signum() != 0) text(
      (String) old.get("explanation"),
      "Giải trình chênh lệch",
      true
    );
    String conclusion = text(input.conclusion(), "Kết luận đối soát", true);
    db.update(
      "INSERT INTO gcmb.cash_reconciliation(closing_id,reviewed_by,decision,note) VALUES (?,?,'RECONCILED',?)",
      id,
      actor.id(),
      conclusion
    );
    db.update("UPDATE gcmb.daily_cash_closing SET status='RECONCILED' WHERE id=?", id);
    access.audit(
      actor,
      (UUID) old.get("branch_id"),
      id,
      "RECONCILE_CASH_CLOSING",
      old,
      Map.of("conclusion", conclusion)
    );
    return detail(actor, id);
  }

  public Map<String, Object> evidence(Identity actor, UUID id) {
    detail(actor, id);
    var rows = db.queryForList("SELECT * FROM gcmb.cash_closing_evidence WHERE closing_id=?", id);
    if (rows.isEmpty()) throw bad("Không tìm thấy minh chứng.");
    var row = rows.get(0);
    try {
      row.put("bytes", storage.read((String) row.get("object_key")));
    } catch (java.io.IOException e) {
      throw conflict("Không thể đọc minh chứng. Vui lòng thử lại.");
    }
    return row;
  }
}
