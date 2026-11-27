package com.gcmb.backend.booking.repository;

import com.gcmb.backend.booking.dto.DepositDtos;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
// Đọc tiền cọc theo chi nhánh và hàng đợi hoàn cọc kế toán toàn hệ thống.
public class DepositRepository {
    public record RefundRequestRow(UUID id, UUID branchId, UUID bookingId, UUID depositId, BigDecimal amount,
                                   String reason, String status) {}
    public record RefundEvidenceRow(String objectKey, String originalName, String mimeType) {}

    private static final String DEPOSIT_VIEW = """
        WITH deposit_view AS (
            SELECT b.branch_id,b.id AS booking_id,b.code AS booking_code,b.contact_name AS customer_name,
                   b.contact_phone AS phone,lr.room_code,lr.room_name,
                   COALESCE(b.actual_checkin,b.planned_start) AS check_in_at,
                   l.received_amount,l.applied_amount,l.balance,l.refunded_amount,l.forfeited_amount,l.refund_transactions,
                   COALESCE(rr.pending_amount,0) AS pending_amount,
                   COALESCE(rr.pending_count,0) AS pending_count,
                   rr.latest_request_status,
                   CASE WHEN CARDINALITY(string_to_array(l.payment_methods,','))>1 THEN 'MULTIPLE'
                        ELSE l.payment_methods END AS payment_method,
                   GREATEST(l.latest_entry_at,rr.latest_request_at,ec.cancelled_at) AS activity_at,
                   CASE WHEN COALESCE(rr.pending_amount,0)>0 THEN 'PENDING_REFUND'
                        WHEN l.balance<=0 AND l.refunded_amount>0 THEN 'REFUNDED'
                        WHEN l.balance<=0 AND l.forfeited_amount>0 THEN 'FORFEITED'
                        WHEN l.balance>0 THEN 'HELD'
                        WHEN l.applied_amount>0 THEN 'APPLIED'
                        WHEN l.refunded_amount>0 THEN 'REFUNDED'
                        WHEN l.forfeited_amount>0 THEN 'FORFEITED'
                        ELSE 'SETTLED' END AS status,
                   CASE WHEN COALESCE(rr.pending_amount,0)>0 THEN rr.pending_amount
                        WHEN l.balance>0 THEN GREATEST(l.balance-COALESCE(rr.pending_amount,0),0)
                        WHEN l.refunded_amount>0 THEN l.refunded_amount
                        WHEN l.applied_amount>0 THEN l.applied_amount
                        ELSE l.forfeited_amount END AS amount,
                   (b.status='CANCELLED' AND l.balance>0 AND ec.booking_id IS NOT NULL
                    AND NOT EXISTS (
                        SELECT 1 FROM gcmb.refund_request open_request
                        WHERE open_request.deposit_id=l.deposit_id AND open_request.kind='DEPOSIT'
                          AND open_request.status IN ('REQUESTED','APPROVED','PROCESSING','PAID')
                    )) AS can_request_refund,
                   ec.cancelled_at
            -- Tổng hợp bằng LATERAL theo từng booking để bộ lọc cơ sở (v.branch_id) được áp dụng trước, không gom toàn chuỗi.
            FROM gcmb.booking b
            JOIN gcmb.deposit d ON d.booking_id=b.id
            CROSS JOIN LATERAL (
                SELECT d.id AS deposit_id,
                       COALESCE(SUM(e.amount) FILTER (WHERE e.kind='RECEIVE'),0) AS received_amount,
                       COALESCE(SUM(e.amount) FILTER (WHERE e.kind='APPLY'),0) AS applied_amount,
                       COALESCE(SUM(e.amount) FILTER (WHERE e.kind='REFUND'),0) AS refunded_amount,
                       COALESCE(SUM(e.amount) FILTER (WHERE e.kind='FORFEIT'),0) AS forfeited_amount,
                       COALESCE(SUM(CASE WHEN e.kind='RECEIVE' THEN e.amount ELSE -e.amount END),0) AS balance,
                       COALESCE(COUNT(DISTINCT e.refund_id) FILTER (WHERE e.kind='REFUND'),0) AS refund_transactions,
                       MIN(e.created_at) FILTER (WHERE e.kind='RECEIVE') AS received_at,
                       MAX(e.created_at) AS latest_entry_at,
                       STRING_AGG(DISTINCT p.method, ',' ORDER BY p.method) AS payment_methods
                FROM gcmb.deposit_entry e
                LEFT JOIN gcmb.payment p ON p.id=e.payment_id
                WHERE e.deposit_id=d.id
            ) l
            LEFT JOIN LATERAL (
                SELECT COALESCE(SUM(r.amount) FILTER (WHERE r.status IN ('REQUESTED','APPROVED','PROCESSING')),0) AS pending_amount,
                       COUNT(*) FILTER (WHERE r.status IN ('REQUESTED','APPROVED','PROCESSING')) AS pending_count,
                       MAX(r.created_at) AS latest_request_at,
                       (ARRAY_AGG(r.status ORDER BY r.created_at DESC,r.id DESC))[1] AS latest_request_status
                FROM gcmb.refund_request r
                WHERE r.kind='DEPOSIT' AND r.deposit_id=d.id
                GROUP BY r.deposit_id
            ) rr ON true
            LEFT JOIN LATERAL (
                SELECT r.code AS room_code,r.name AS room_name
                FROM gcmb.room_allocation a
                JOIN gcmb.room r ON r.id=a.room_id
                WHERE a.booking_id=b.id
                ORDER BY a.created_at DESC,a.id DESC
                LIMIT 1
            ) lr ON true
            LEFT JOIN LATERAL (
                SELECT e.booking_id,MAX(e.created_at) AS cancelled_at
                FROM gcmb.booking_event e
                WHERE e.booking_id=b.id AND e.event_type='BOOKING_CANCELLED'
                  AND e.details ->> 'depositOutcome'='REFUND_ELIGIBLE'
                GROUP BY e.booking_id
            ) ec ON true
            WHERE l.received_amount>0
        )
        """;

    private static final String FILTERS = """
        WHERE v.branch_id=?
          AND v.status<>'SETTLED'
          AND (CAST(? AS date) IS NULL OR (v.activity_at AT TIME ZONE ?)::date>=CAST(? AS date))
          AND (CAST(? AS date) IS NULL OR (v.activity_at AT TIME ZONE ?)::date<=CAST(? AS date))
          AND (CAST(? AS text) IS NULL OR v.status=CAST(? AS text))
          AND (CAST(? AS text) IS NULL OR v.payment_method=CAST(? AS text))
          AND (CAST(? AS text) IS NULL OR concat_ws(' ',v.booking_code,v.customer_name,v.phone,v.room_code,v.room_name) ILIKE '%' || CAST(? AS text) || '%')
        """;

    private final JdbcTemplate jdbcTemplate;

    public DepositRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public DepositDtos.DepositSummary summary(UUID branchId, LocalDate dateFrom, LocalDate dateTo, ZoneId timezone) {
        String sql = DEPOSIT_VIEW + """
            SELECT COALESCE(SUM(GREATEST(v.balance-v.pending_amount,0)) FILTER (WHERE v.status='HELD'),0) AS held_amount,
                   COUNT(*) FILTER (WHERE v.status='HELD' AND v.balance>v.pending_amount) AS held_bookings,
                   COALESCE(SUM(v.pending_amount),0) AS pending_amount,
                   COALESCE(SUM(v.pending_count),0) AS pending_requests,
                   COALESCE(SUM(v.refunded_amount),0) AS refunded_amount,
                   COALESCE(SUM(v.refund_transactions),0) AS refund_transactions,
                   COALESCE(SUM(v.forfeited_amount),0) AS forfeited_amount,
                   COUNT(*) FILTER (WHERE v.forfeited_amount>0) AS forfeited_bookings
            FROM deposit_view v
            WHERE v.branch_id=? AND v.status<>'SETTLED'
              AND (CAST(? AS date) IS NULL OR (v.activity_at AT TIME ZONE ?)::date>=CAST(? AS date))
              AND (CAST(? AS date) IS NULL OR (v.activity_at AT TIME ZONE ?)::date<=CAST(? AS date))
            """;
        return jdbcTemplate.queryForObject(sql, (resultSet, rowNumber) -> new DepositDtos.DepositSummary(
            resultSet.getBigDecimal("held_amount"), resultSet.getLong("held_bookings"), resultSet.getBigDecimal("pending_amount"),
            resultSet.getLong("pending_requests"), resultSet.getBigDecimal("refunded_amount"), resultSet.getLong("refund_transactions"),
            resultSet.getBigDecimal("forfeited_amount"), resultSet.getLong("forfeited_bookings")),
            branchId, dateFrom, timezone.getId(), dateFrom, dateTo, timezone.getId(), dateTo);
    }

    public long count(UUID branchId, LocalDate dateFrom, LocalDate dateTo, ZoneId timezone,
                      String status, String method, String search) {
        String sql = DEPOSIT_VIEW + " SELECT COUNT(*) FROM deposit_view v " + FILTERS;
        Long count = jdbcTemplate.queryForObject(sql, Long.class, filterParameters(branchId, dateFrom, dateTo, timezone, status, method, search));
        return count == null ? 0 : count;
    }

    public List<DepositDtos.DepositItem> list(UUID branchId, LocalDate dateFrom, LocalDate dateTo, ZoneId timezone,
                                               String status, String method, String search, int page, int size) {
        String sql = DEPOSIT_VIEW + """
            SELECT v.booking_id,v.booking_code,v.customer_name,v.phone,v.room_code,v.room_name,v.check_in_at,
                   v.amount,v.received_amount,v.balance,v.pending_amount,v.refunded_amount,v.forfeited_amount,
                   v.payment_method,v.status,v.latest_request_status,v.activity_at,v.can_request_refund
            FROM deposit_view v
            """ + FILTERS + " ORDER BY CASE v.status WHEN 'PENDING_REFUND' THEN 0 WHEN 'REFUNDED' THEN 1 WHEN 'HELD' THEN 2 WHEN 'FORFEITED' THEN 3 ELSE 4 END, v.activity_at DESC NULLS LAST,v.booking_code LIMIT ? OFFSET ?";
        Object[] filters = filterParameters(branchId, dateFrom, dateTo, timezone, status, method, search);
        Object[] params = java.util.Arrays.copyOf(filters, filters.length + 2);
        params[filters.length] = size;
        params[filters.length + 1] = (long) page * size;
        return jdbcTemplate.query(sql, DepositRepository::mapItem, params);
    }

    public DepositDtos.DepositItem item(UUID branchId, UUID bookingId) {
        String sql = DEPOSIT_VIEW + """
            SELECT v.booking_id,v.booking_code,v.customer_name,v.phone,v.room_code,v.room_name,v.check_in_at,
                   v.amount,v.received_amount,v.balance,v.pending_amount,v.refunded_amount,v.forfeited_amount,
                   v.payment_method,v.status,v.latest_request_status,v.activity_at,v.can_request_refund
            FROM deposit_view v
            WHERE v.branch_id=? AND v.booking_id=? AND v.status<>'SETTLED'
            """;
        List<DepositDtos.DepositItem> rows = jdbcTemplate.query(sql, DepositRepository::mapItem, branchId, bookingId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<DepositDtos.RefundableBooking> eligibleRefundBookings(UUID branchId) {
        String sql = DEPOSIT_VIEW + """
            SELECT v.booking_id,v.booking_code,v.customer_name,v.phone,v.cancelled_at,
                   GREATEST(v.balance-v.pending_amount,0) AS amount
            FROM deposit_view v
            WHERE v.branch_id=? AND v.can_request_refund=true
            ORDER BY v.cancelled_at DESC,v.booking_code
            """;
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> new DepositDtos.RefundableBooking(
            resultSet.getObject("booking_id", UUID.class), resultSet.getString("booking_code"), resultSet.getString("customer_name"),
            resultSet.getString("phone"), instant(resultSet, "cancelled_at"), resultSet.getBigDecimal("amount")), branchId);
    }

    public List<DepositDtos.RefundRequestItem> refundRequestsForAccounting() {
        return jdbcTemplate.query("""
            SELECT r.id,r.booking_id,b.code,br.name AS branch_name,b.contact_name,b.contact_phone,r.amount,r.reason,
                   r.beneficiary_name,r.bank_name,r.bank_account,r.status,r.created_at,r.paid_at,r.external_reference,
                   r.evidence_id IS NOT NULL AS has_evidence
            FROM gcmb.refund_request r
            JOIN gcmb.booking b ON b.id=r.booking_id
            JOIN gcmb.branch br ON br.id=r.branch_id
            WHERE r.kind='DEPOSIT'
            ORDER BY CASE WHEN r.status IN ('REQUESTED','APPROVED','PROCESSING') THEN 0 ELSE 1 END,
                     r.created_at DESC,r.id DESC
            """, (resultSet, rowNumber) -> new DepositDtos.RefundRequestItem(
                resultSet.getObject("id", UUID.class), resultSet.getObject("booking_id", UUID.class), resultSet.getString("code"),
                resultSet.getString("branch_name"), resultSet.getString("contact_name"), resultSet.getString("contact_phone"), resultSet.getBigDecimal("amount"),
                resultSet.getString("reason"), resultSet.getString("beneficiary_name"), resultSet.getString("bank_name"),
                resultSet.getString("bank_account"), resultSet.getString("status"), instant(resultSet, "created_at"),
                instant(resultSet, "paid_at"), resultSet.getString("external_reference"), resultSet.getBoolean("has_evidence")));
    }

    public RefundEvidenceRow refundEvidenceForAccounting(UUID requestId) {
        List<RefundEvidenceRow> rows = jdbcTemplate.query("""
            SELECT a.object_key,a.original_name,a.mime_type
            FROM gcmb.refund_request r
            JOIN gcmb.attachment a ON a.id=r.evidence_id
            WHERE r.id=? AND r.kind='DEPOSIT' AND r.status='PAID'
            """, (resultSet, rowNumber) -> new RefundEvidenceRow(
                resultSet.getString("object_key"), resultSet.getString("original_name"), resultSet.getString("mime_type")),
            requestId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public RefundEvidenceRow refundEvidence(UUID branchId, UUID requestId) {
        List<RefundEvidenceRow> rows = jdbcTemplate.query("""
            SELECT a.object_key,a.original_name,a.mime_type
            FROM gcmb.refund_request r
            JOIN gcmb.attachment a ON a.id=r.evidence_id
            WHERE r.branch_id=? AND r.id=? AND r.kind='DEPOSIT' AND r.status='PAID'
            """, (resultSet, rowNumber) -> new RefundEvidenceRow(
                resultSet.getString("object_key"), resultSet.getString("original_name"), resultSet.getString("mime_type")),
            branchId, requestId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<DepositDtos.RefundEvidenceItem> refundEvidenceForBooking(UUID branchId, UUID bookingId) {
        return jdbcTemplate.query("""
            SELECT r.id,r.amount,r.paid_at,r.external_reference,a.original_name
            FROM gcmb.refund_request r
            JOIN gcmb.booking b ON b.id=r.booking_id
            JOIN gcmb.attachment a ON a.id=r.evidence_id
            WHERE b.branch_id=? AND b.id=? AND r.branch_id=b.branch_id
              AND r.kind='DEPOSIT' AND r.status='PAID' AND r.evidence_id IS NOT NULL
            ORDER BY r.paid_at DESC,r.id DESC
            """, (resultSet, rowNumber) -> new DepositDtos.RefundEvidenceItem(
                resultSet.getObject("id", UUID.class), resultSet.getBigDecimal("amount"), instant(resultSet, "paid_at"),
                resultSet.getString("external_reference"), resultSet.getString("original_name")), branchId, bookingId);
    }

    public void insertAttachment(UUID id, String objectKey, String originalName, String mimeType,
                                 long byteSize, String sha256, UUID uploadedBy) {
        jdbcTemplate.update("""
            INSERT INTO gcmb.attachment(id,object_key,original_name,mime_type,byte_size,sha256,uploaded_by)
            VALUES(?,?,?,?,?,?,?)
            """, id, objectKey, originalName, mimeType, byteSize, sha256, uploadedBy);
    }

    public RefundRequestRow refundRequest(UUID branchId, UUID requestId, boolean lock) {
        String lockClause = lock ? " FOR UPDATE" : "";
        List<RefundRequestRow> rows = jdbcTemplate.query("""
            SELECT id,branch_id,booking_id,deposit_id,amount,reason,status
            FROM gcmb.refund_request
            WHERE branch_id=? AND id=? AND kind='DEPOSIT'
            """ + lockClause, (resultSet, rowNumber) -> new RefundRequestRow(
                resultSet.getObject("id", UUID.class), resultSet.getObject("branch_id", UUID.class),
                resultSet.getObject("booking_id", UUID.class),
                resultSet.getObject("deposit_id", UUID.class), resultSet.getBigDecimal("amount"),
                resultSet.getString("reason"), resultSet.getString("status")), branchId, requestId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public RefundRequestRow refundRequestForAccounting(UUID requestId, boolean lock) {
        String lockClause = lock ? " FOR UPDATE" : "";
        List<RefundRequestRow> rows = jdbcTemplate.query("""
            SELECT id,branch_id,booking_id,deposit_id,amount,reason,status
            FROM gcmb.refund_request
            WHERE id=? AND kind='DEPOSIT'
            """ + lockClause, (resultSet, rowNumber) -> new RefundRequestRow(
                resultSet.getObject("id", UUID.class), resultSet.getObject("branch_id", UUID.class),
                resultSet.getObject("booking_id", UUID.class), resultSet.getObject("deposit_id", UUID.class),
                resultSet.getBigDecimal("amount"), resultSet.getString("reason"), resultSet.getString("status")), requestId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public boolean completeRefundRequest(UUID branchId, RefundRequestRow request, UUID actorId,
                                         String externalReference, UUID evidenceId) {
        int updated = jdbcTemplate.update("""
            UPDATE gcmb.refund_request
            SET status='PAID',processed_by=?,paid_at=CURRENT_TIMESTAMP,external_reference=?,evidence_id=?
            WHERE id=? AND branch_id=? AND kind='DEPOSIT'
              AND status IN ('REQUESTED','APPROVED','PROCESSING')
            """, actorId, externalReference, evidenceId, request.id(), branchId);
        if (updated != 1) return false;

        int inserted = jdbcTemplate.update("""
            INSERT INTO gcmb.deposit_entry(deposit_id,kind,amount,refund_id,actor_id,reason,idempotency_key)
            VALUES(?,'REFUND',?,?,?,?,?)
            """, request.depositId(), request.amount(), request.id(), actorId, request.reason(),
            "DEPOSIT-REFUND-" + request.id());
        if (inserted != 1) throw new IllegalStateException("Deposit refund entry was not recorded");

        // BR-108, BR-157: hoàn cọc phải giảm quỹ nguồn trong cùng giao dịch với sổ cọc.
        UUID accountId = refundAccount(request);
        if (accountId == null)
            throw new com.gcmb.backend.booking.exception.BookingFailure(409, "requestId",
                "Không tìm thấy quỹ nhận khoản cọc để ghi nhận bút toán hoàn.");
        UUID periodId = openFinancialPeriod();
        if (periodId == null)
            throw new com.gcmb.backend.booking.exception.BookingFailure(409, "requestId",
                "Không có kỳ tài chính đang mở để ghi nhận bút toán hoàn cọc.");
        UUID transactionId = UUID.randomUUID();
        int transactionInserted = jdbcTemplate.update("""
            INSERT INTO gcmb.financial_transaction(id,period_id,booking_date,occurred_at,kind,refund_id,reason,created_by,idempotency_key)
            VALUES(?,?,CURRENT_DATE,CURRENT_TIMESTAMP,'REFUND',?,?,?,?)
            """, transactionId, periodId, request.id(), "Hoàn cọc: " + request.reason(), actorId,
            "DEPOSIT-REFUND-" + request.id());
        if (transactionInserted != 1) throw new IllegalStateException("Refund financial transaction was not recorded");
        int entryInserted = jdbcTemplate.update("""
            INSERT INTO gcmb.financial_entry(transaction_id,account_id,branch_id,amount_signed)
            VALUES(?,?,?,?)
            """, transactionId, accountId, branchId, request.amount().negate());
        if (entryInserted != 1) throw new IllegalStateException("Refund financial entry was not recorded");
        return true;
    }

    // Use the same fund as the deposit receipt; cash receipts without a linked fund use the branch drawer.
    private UUID refundAccount(RefundRequestRow request) {
        List<UUID> rows = jdbcTemplate.query("""
            SELECT COALESCE(
                (SELECT fe.account_id
                 FROM gcmb.deposit_entry de
                 JOIN gcmb.financial_transaction ft ON ft.payment_id=de.payment_id AND ft.kind='PAYMENT'
                 JOIN gcmb.financial_entry fe ON fe.transaction_id=ft.id
                 WHERE de.deposit_id=? AND de.kind='RECEIVE' AND de.payment_id IS NOT NULL
                 ORDER BY de.created_at,de.id LIMIT 1),
                (SELECT pr.account_id
                 FROM gcmb.deposit_entry de
                 JOIN gcmb.payment p ON p.id=de.payment_id
                 JOIN gcmb.payment_request pr ON pr.id=p.request_id
                 WHERE de.deposit_id=? AND de.kind='RECEIVE' AND de.payment_id IS NOT NULL
                 ORDER BY de.created_at,de.id LIMIT 1),
                (SELECT fa.id FROM gcmb.financial_account fa
                 WHERE fa.branch_id=? AND fa.kind='CASH_DRAWER' AND fa.active
                 ORDER BY fa.code LIMIT 1)
            ) AS account_id
            """, (resultSet, rowNumber) -> resultSet.getObject("account_id", UUID.class),
            request.depositId(), request.depositId(), request.branchId());
        return rows.isEmpty() ? null : rows.get(0);
    }

    private UUID openFinancialPeriod() {
        // A missing current-month row is treated as OPEN by the accounting period screen.
        // Materialize that implicit period before linking the refund transaction to it.
        jdbcTemplate.update("""
            INSERT INTO gcmb.financial_period(month,status)
            VALUES(date_trunc('month',CURRENT_DATE)::date,'OPEN')
            ON CONFLICT(month) DO NOTHING
            """);
        List<UUID> rows = jdbcTemplate.query("""
            SELECT id FROM gcmb.financial_period
            WHERE month=date_trunc('month',CURRENT_DATE)::date AND status='OPEN'
            FOR SHARE
            """, (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public boolean canRequestRefund(UUID branchId, UUID bookingId) {
        String sql = DEPOSIT_VIEW + """
            SELECT COALESCE(bool_or(v.can_request_refund),false)
            FROM deposit_view v
            WHERE v.branch_id=? AND v.booking_id=?
            """;
        Boolean allowed = jdbcTemplate.queryForObject(sql, Boolean.class, branchId, bookingId);
        return Boolean.TRUE.equals(allowed);
    }

    public List<DepositDtos.DepositEvent> history(UUID branchId, UUID bookingId) {
        return jdbcTemplate.query("""
            SELECT timeline.happened_at,timeline.type,timeline.label,timeline.details
            FROM (
                SELECT e.created_at AS happened_at,e.kind AS type,
                       CASE e.kind WHEN 'RECEIVE' THEN 'Đã nhận tiền cọc'
                                   WHEN 'APPLY' THEN 'Đã áp dụng cọc vào hóa đơn'
                                   WHEN 'REFUND' THEN 'Đã hoàn cọc'
                                   WHEN 'FORFEIT' THEN 'Đã ghi nhận hủy cọc'
                                   ELSE 'Cập nhật tiền cọc' END AS label,
                       COALESCE(NULLIF(e.reason,''),p.method,'') AS details
                FROM gcmb.deposit d
                JOIN gcmb.deposit_entry e ON e.deposit_id=d.id
                LEFT JOIN gcmb.payment p ON p.id=e.payment_id
                JOIN gcmb.booking b ON b.id=d.booking_id
                WHERE b.branch_id=? AND b.id=?
                UNION ALL
                SELECT be.created_at,'BOOKING',
                       CASE WHEN be.details ->> 'depositOutcome'='REFUND_ELIGIBLE'
                            THEN 'Lễ tân hủy booking, đủ điều kiện hoàn'
                            WHEN be.event_type='BOOKING_CANCELLED' THEN 'Lễ tân hủy booking'
                            ELSE 'Booking cập nhật trạng thái' END,
                       COALESCE(be.reason,'')
                FROM gcmb.booking_event be
                JOIN gcmb.booking b ON b.id=be.booking_id
                WHERE b.branch_id=? AND b.id=?
                  AND be.event_type IN ('BOOKING_CANCELLED','BOOKING_NO_SHOW')
                UNION ALL
                SELECT r.created_at,'REFUND_REQUEST','Đã gửi yêu cầu hoàn cọc cho Kế toán',
                       concat('Số tiền yêu cầu: ',r.amount::text,'đ')
                FROM gcmb.refund_request r
                JOIN gcmb.booking b ON b.id=r.booking_id
                WHERE b.branch_id=? AND b.id=? AND r.kind='DEPOSIT'
                UNION ALL
                SELECT r.paid_at,'REFUNDED','Kế toán xác nhận đã hoàn',COALESCE(r.external_reference,'')
                FROM gcmb.refund_request r
                JOIN gcmb.booking b ON b.id=r.booking_id
                WHERE b.branch_id=? AND b.id=? AND r.kind='DEPOSIT' AND r.paid_at IS NOT NULL
            ) timeline
            ORDER BY timeline.happened_at,timeline.type
            """, (resultSet, rowNumber) -> new DepositDtos.DepositEvent(
                instant(resultSet, "happened_at"), resultSet.getString("type"), resultSet.getString("label"), resultSet.getString("details")),
            branchId, bookingId, branchId, bookingId, branchId, bookingId, branchId, bookingId);
    }

    private static Object[] filterParameters(UUID branchId, LocalDate dateFrom, LocalDate dateTo, ZoneId timezone,
                                              String status, String method, String search) {
        return new Object[] { branchId, dateFrom, timezone.getId(), dateFrom,
            dateTo, timezone.getId(), dateTo, status, status, method, method,
            search, search };
    }

    private static DepositDtos.DepositItem mapItem(ResultSet resultSet, int rowNumber) throws SQLException {
        return new DepositDtos.DepositItem(resultSet.getObject("booking_id", UUID.class), resultSet.getString("booking_code"),
            resultSet.getString("customer_name"), resultSet.getString("phone"), resultSet.getString("room_code"), resultSet.getString("room_name"),
            instant(resultSet, "check_in_at"), resultSet.getBigDecimal("amount"), resultSet.getBigDecimal("received_amount"),
            resultSet.getBigDecimal("balance"), resultSet.getBigDecimal("pending_amount"), resultSet.getBigDecimal("refunded_amount"),
            resultSet.getBigDecimal("forfeited_amount"), resultSet.getString("payment_method"), resultSet.getString("status"),
            resultSet.getString("latest_request_status"), instant(resultSet, "activity_at"), resultSet.getBoolean("can_request_refund"));
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        var value = resultSet.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
