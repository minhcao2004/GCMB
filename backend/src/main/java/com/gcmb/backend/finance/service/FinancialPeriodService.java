package com.gcmb.backend.finance.service;

import com.gcmb.backend.audit.service.MutationAudit;
import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.exception.AuthFailure;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FinancialPeriodService {
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final List<String> ROLES = List.of("ACCOUNTANT");

    private final JdbcTemplate db;
    private final MutationAudit audit;
    @Value("${gcmb.finance.period-close.allow-early-close:${GCMB_ALLOW_EARLY_PERIOD_CLOSE:false}}")
    private boolean allowEarlyClose;

    public FinancialPeriodService(JdbcTemplate db, MutationAudit audit) {
        this.db = db;
        this.audit = audit;
    }

    public void requireOpen(LocalDate date, String operation) {
        if (date == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày giao dịch không hợp lệ.");
        LocalDate month = date.withDayOfMonth(1);
        db.update("INSERT INTO gcmb.financial_period(\"month\",status) VALUES (?,'OPEN') ON CONFLICT(\"month\") DO NOTHING", month);
        String status = db.queryForObject("SELECT status FROM gcmb.financial_period WHERE \"month\"=? FOR SHARE", String.class, month);
        if (!"OPEN".equals(status)) throw new AuthFailure(409, "form",
            "Kỳ tài chính " + LABEL.format(YearMonth.from(month)) + " đã khóa. Không thể " + operation + ".");
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Identity actor) {
        accountant(actor);
        return db.queryForList("""
            WITH months AS (
              SELECT \"month\" FROM gcmb.financial_period
              UNION SELECT date_trunc('month',business_date)::date FROM gcmb.daily_cash_closing
              UNION SELECT date_trunc('month',booking_date)::date FROM gcmb.financial_transaction
              UNION SELECT month FROM gcmb.attendance_period
              UNION SELECT month FROM gcmb.payroll_period
              UNION SELECT date_trunc('month',expense_on)::date FROM gcmb.finance_expense_request
              UNION SELECT date_trunc('month',sent_on)::date FROM gcmb.finance_fund_transfer
              UNION SELECT date_trunc('month',created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date FROM gcmb.refund_request
              UNION SELECT date_trunc('month',occurred_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date
                FROM gcmb.bank_transaction WHERE provider='SEPAY' AND match_status='EXCEPTION'
              UNION SELECT date_trunc('month',CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh')::date
            )
            SELECT p.id,m.month::text AS month,COALESCE(p.status,'OPEN') status,p.closed_at "closedAt",
              u.username "closedBy",p.close_note "closeNote",
              (SELECT count(*) FROM gcmb.daily_cash_closing c WHERE date_trunc('month',c.business_date)::date=m.month
                AND c.status<>'RECONCILED') "unreconciledClosings"
            FROM months m LEFT JOIN gcmb.financial_period p ON p.\"month\"=m.month
            LEFT JOIN gcmb.user_account u ON u.id=p.closed_by
            ORDER BY m.month DESC
            """);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> readiness(Identity actor, LocalDate month) {
        accountant(actor);
        LocalDate start = firstOfMonth(month);
        return readiness(start, currentStatus(start));
    }

    @Transactional
    public Map<String, Object> close(Identity actor, LocalDate month, boolean confirmed, String note) {
        accountant(actor);
        if (!confirmed) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng xác nhận khóa kỳ tài chính.");
        String closeNote = note == null ? "" : note.trim();
        if (closeNote.length() > 1000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ghi chú chỉ được chứa tối đa 1000 ký tự.");
        LocalDate start = firstOfMonth(month);
        boolean monthEnded = periodEnded(start);
        if (!monthEnded && !allowEarlyClose) throw new AuthFailure(409, "form", "Chỉ có thể khóa kỳ sau khi tháng đã kết thúc.");
        boolean earlyClose = allowEarlyClose && !monthEnded;
        db.update("INSERT INTO gcmb.financial_period(\"month\",status) VALUES (?,'OPEN') ON CONFLICT(\"month\") DO NOTHING", start);
        UUID id = db.queryForObject("SELECT id FROM gcmb.financial_period WHERE \"month\"=? FOR UPDATE", UUID.class, start);
        UUID closedId = audit.recordWithMetadata("CLOSE_FINANCIAL_PERIOD", "FINANCIAL_PERIOD", id, () -> {
            Map<String, Object> period = db.queryForMap("SELECT status FROM gcmb.financial_period WHERE id=? FOR UPDATE", id);
            if ("CLOSED".equals(period.get("status"))) throw new AuthFailure(409, "form", "Kỳ tài chính này đã được khóa.");
            Map<String, Object> checks = readiness(start, "OPEN");
            @SuppressWarnings("unchecked") List<Map<String, Object>> items = (List<Map<String, Object>>) checks.get("checks");
            List<String> blockers = items.stream().filter(item -> !Boolean.TRUE.equals(item.get("passed")))
                .map(item -> item.get("label") + ": còn " + item.get("pendingCount"))
                .toList();
            if (!blockers.isEmpty()) throw new AuthFailure(409, "form", "Chưa thể khóa kỳ " + LABEL.format(YearMonth.from(start)) + ". " + String.join("; ", blockers) + ".");
            if (db.update("UPDATE gcmb.financial_period SET status='CLOSED',closed_by=?,closed_at=CURRENT_TIMESTAMP,close_note=? WHERE id=? AND status='OPEN'",
                    actor.id(), closeNote, id) != 1)
                throw new AuthFailure(409, "form", "Kỳ tài chính vừa được khóa ở phiên khác.");
            return id;
        }, value -> value, earlyClose ? "{\"earlyClose\":true}" : null);
        return db.queryForMap("""
            SELECT p.id,p."month"::text AS "month",p.status,p.closed_at "closedAt",u.username "closedBy",p.close_note "closeNote"
            FROM gcmb.financial_period p LEFT JOIN gcmb.user_account u ON u.id=p.closed_by WHERE p.id=?
            """, closedId);
    }

    private Map<String, Object> readiness(LocalDate start, String periodStatus) {
        LocalDate end = start.plusMonths(1);
        boolean monthEnded = periodEnded(start);
        long periodEndPending = monthEnded || allowEarlyClose ? 0 : 1;
        long unreconciled = count("SELECT count(*) FROM gcmb.daily_cash_closing WHERE business_date>=? AND business_date<? AND status<>'RECONCILED'", start, end);
        long missingActivityClosings = count("""
            SELECT count(*) FROM (
              SELECT DISTINCT e.account_id,e.branch_id,t.booking_date
              FROM gcmb.financial_entry e JOIN gcmb.financial_transaction t ON t.id=e.transaction_id
              JOIN gcmb.financial_account a ON a.id=e.account_id AND a.kind='CASH_DRAWER'
              WHERE t.booking_date>=? AND t.booking_date<?
            ) expected WHERE NOT EXISTS (
              SELECT 1 FROM gcmb.daily_cash_closing c WHERE c.account_id=expected.account_id
                AND c.business_date=expected.booking_date
            )
            """, start, end);
        long cashPending = unreconciled + missingActivityClosings;
        long attendancePending = count("""
            SELECT count(*) FROM gcmb.branch b WHERE b.status='ACTIVE' AND NOT EXISTS (
              SELECT 1 FROM gcmb.attendance_period a WHERE a.branch_id=b.id AND a.month=? AND a.status='CLOSED'
            )
            """, start);
        long payrollPending = count("SELECT CASE WHEN EXISTS(SELECT 1 FROM gcmb.payroll_period WHERE month=? AND status='CONFIRMED') THEN 0 ELSE 1 END", start);
        long expensesPending = count("""
            SELECT count(*) FROM gcmb.finance_expense_request WHERE expense_on>=? AND expense_on<?
              AND status IN ('WAITING_ACCOUNTANT','WAITING_HEAD_OFFICE','APPROVED_TO_PAY')
            """, start, end);
        long refundsPending = count("""
            SELECT count(*) FROM gcmb.refund_request WHERE created_at>=? AND created_at<?
              AND status IN ('REQUESTED','APPROVED','PROCESSING')
            """, start.atStartOfDay(ZONE).toOffsetDateTime(), end.atStartOfDay(ZONE).toOffsetDateTime());
        long sepayPending = count("""
            SELECT count(*) FROM gcmb.bank_transaction WHERE provider='SEPAY' AND match_status='EXCEPTION'
              AND resolution_status='UNRESOLVED'
              AND occurred_at<?
            """, end.atStartOfDay(ZONE).toOffsetDateTime());

        List<Map<String, Object>> checks = new ArrayList<>();
        checks.add(check("periodEnded",
            allowEarlyClose ? "Chế độ demo: cho phép chốt khi tháng chưa kết thúc" : "Kỳ đã kết thúc",
            periodEndPending,
            allowEarlyClose ? "Chế độ demo đang bật; các điều kiện khác vẫn bắt buộc." : "Chỉ khóa sau khi đã sang tháng tiếp theo."));
        // BR-99: demo chỉ bỏ qua điều kiện kết thúc tháng; đối soát tiền mặt vẫn bắt buộc.
        checks.add(check("cashReconciliation", "Đối soát tiền mặt", cashPending,
            "Tính các phiếu chưa đối soát và ngày, quỹ có phát sinh dòng tiền nhưng chưa có phiếu đối soát."));
        checks.add(check("attendance", "Chốt công tháng", attendancePending,
            "Mỗi cơ sở đang hoạt động cần có kỳ công đã chốt."));
        checks.add(check("payroll", "Chốt bảng lương", payrollPending,
            "Bảng lương của tháng phải được xác nhận."));
        checks.add(check("expenses", "Xử lý đề nghị chi và hoàn ứng", expensesPending,
            "Đếm các đề nghị thanh toán hoặc hoàn ứng còn chờ duyệt hoặc chờ chi theo ngày chi."));
        checks.add(check("refunds", "Xử lý hoàn cọc", refundsPending,
            "Đếm yêu cầu hoàn cọc tạo trong tháng còn mới gửi, đã duyệt hoặc đang xử lý."));
        checks.add(check("sepayExceptions", "Xử lý giao dịch ngân hàng chưa khớp", sepayPending,
            "Đếm các giao dịch ngân hàng qua SePay chưa xử lý, có ngày giao dịch trước khi kỳ kết thúc, kể cả giao dịch từ kỳ trước."));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("month", start.toString().substring(0, 7));
        result.put("status", periodStatus);
        result.put("earlyCloseEnabled", allowEarlyClose);
        result.put("checks", checks);
        result.put("canClose", "OPEN".equals(periodStatus) && checks.stream().allMatch(item -> Boolean.TRUE.equals(item.get("passed"))));
        result.put("cashCalendarLimitation", "Không có lịch ngày bắt buộc chốt quỹ; hệ thống kiểm tra các ngày/quỹ có phát sinh dòng tiền và các phiếu đã tạo.");
        return result;
    }

    private Map<String, Object> check(String key, String label, long pending, String detail) {
        return Map.of("key", key, "label", label, "pendingCount", pending, "passed", pending == 0, "detail", detail);
    }

    private String currentStatus(LocalDate start) {
        List<String> rows = db.queryForList("SELECT status FROM gcmb.financial_period WHERE \"month\"=?", String.class, start);
        return rows.isEmpty() ? "OPEN" : rows.get(0);
    }

    private long count(String sql, Object... args) {
        Long value = db.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    @Transactional
    public Map<String, Object> resolveSePayException(Identity actor, UUID id, String note) {
        accountant(actor);
        if (id == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giao dịch ngoại lệ không hợp lệ.");
        String resolutionNote = note == null ? "" : note.trim();
        if (resolutionNote.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập ghi chú xử lý.");
        if (resolutionNote.length() > 1000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ghi chú xử lý chỉ được chứa tối đa 1000 ký tự.");
        UUID resolvedId = audit.record("RESOLVE_SEPAY_EXCEPTION", "BANK_TRANSACTION", id, () -> {
            List<Map<String, Object>> rows = db.queryForList("""
                SELECT match_status,resolution_status FROM gcmb.bank_transaction
                WHERE id=? AND provider='SEPAY' FOR UPDATE
                """, id);
            if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch SePay ngoại lệ.");
            Map<String, Object> row = rows.get(0);
            if (!"EXCEPTION".equals(row.get("match_status")))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ xử lý giao dịch đang ở trạng thái EXCEPTION.");
            if ("UNRESOLVED".equals(row.get("resolution_status"))) {
                db.update("""
                    UPDATE gcmb.bank_transaction SET resolution_status='RESOLVED',resolved_at=CURRENT_TIMESTAMP,
                      resolved_by=?,resolution_note=? WHERE id=? AND resolution_status='UNRESOLVED'
                    """, actor.id(), resolutionNote, id);
            }
            return id;
        }, value -> value);
        return db.queryForMap("""
            SELECT bt.id,bt.external_id AS "externalId",bt.match_status AS status,bt.exception_reason AS "exceptionReason",
              bt.resolution_status AS "resolutionStatus",bt.resolved_at AS "resolvedAt",u.username AS "resolvedBy",
              bt.resolution_note AS "resolutionNote"
            FROM gcmb.bank_transaction bt LEFT JOIN gcmb.user_account u ON u.id=bt.resolved_by WHERE bt.id=?
            """, resolvedId);
    }

    private static LocalDate firstOfMonth(LocalDate month) {
        if (month == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tháng tài chính không hợp lệ.");
        return month.withDayOfMonth(1);
    }

    private static boolean periodEnded(LocalDate start) {
        return start.isBefore(LocalDate.now(ZONE).withDayOfMonth(1));
    }

    private static void accountant(Identity actor) {
        if (actor == null || !ROLES.contains(actor.role())) throw new AuthFailure(403, "form", "Bạn không có quyền thực hiện đối với nghiệp vụ kế toán.");
    }
}
