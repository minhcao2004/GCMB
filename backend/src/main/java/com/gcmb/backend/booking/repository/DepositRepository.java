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
// Đọc danh sách tiền cọc, các bút toán và lịch sử xử lý theo chi nhánh.
public class DepositRepository {
    public record RefundRequestRow(UUID id, UUID bookingId, UUID depositId, BigDecimal amount,
                                   String reason, String status) {}

    private static final String DEPOSIT_VIEW = """
        WITH ledger AS (
            SELECT d.id AS deposit_id, d.booking_id,
                   COALESCE(SUM(e.amount) FILTER (WHERE e.kind='RECEIVE'),0) AS received_amount,
                   COALESCE(SUM(e.amount) FILTER (WHERE e.kind='APPLY'),0) AS applied_amount,
                   COALESCE(SUM(e.amount) FILTER (WHERE e.kind='REFUND'),0) AS refunded_amount,
                   COALESCE(SUM(e.amount) FILTER (WHERE e.kind='FORFEIT'),0) AS forfeited_amount,
                   COALESCE(SUM(CASE WHEN e.kind='RECEIVE' THEN e.amount ELSE -e.amount END),0) AS balance,
                   COALESCE(COUNT(DISTINCT e.refund_id) FILTER (WHERE e.kind='REFUND'),0) AS refund_transactions,
                   MIN(e.created_at) FILTER (WHERE e.kind='RECEIVE') AS received_at,
                   MAX(e.created_at) AS latest_entry_at,
                   STRING_AGG(DISTINCT p.method, ',' ORDER BY p.method) AS payment_methods
            FROM gcmb.deposit d
            LEFT JOIN gcmb.deposit_entry e ON e.deposit_id=d.id
            LEFT JOIN gcmb.payment p ON p.id=e.payment_id
            GROUP BY d.id,d.booking_id
        ), refund_requests AS (
            SELECT r.deposit_id,
                   COALESCE(SUM(r.amount) FILTER (WHERE r.status IN ('REQUESTED','APPROVED','PROCESSING')),0) AS pending_amount,
                   COUNT(*) FILTER (WHERE r.status IN ('REQUESTED','APPROVED','PROCESSING')) AS pending_count,
                   MAX(r.created_at) AS latest_request_at,
                   (ARRAY_AGG(r.status ORDER BY r.created_at DESC,r.id DESC))[1] AS latest_request_status
            FROM gcmb.refund_request r
            WHERE r.kind='DEPOSIT'
            GROUP BY r.deposit_id
        ), latest_room AS (
            SELECT DISTINCT ON (a.booking_id) a.booking_id,r.code AS room_code,r.name AS room_name
            FROM gcmb.room_allocation a
            JOIN gcmb.room r ON r.id=a.room_id
            ORDER BY a.booking_id,a.created_at DESC,a.id DESC
        ), eligible_cancel AS (
            SELECT e.booking_id,MAX(e.created_at) AS cancelled_at
            FROM gcmb.booking_event e
            WHERE e.event_type='BOOKING_CANCELLED'
              AND e.details ->> 'depositOutcome'='REFUND_ELIGIBLE'
            GROUP BY e.booking_id
        ), deposit_view AS (
            SELECT b.branch_id,b.id AS booking_id,b.code AS booking_code,b.contact_name AS customer_name,
                   b.contact_phone AS phone,lr.room_code,lr.room_name,
                   COALESCE(b.actual_checkin,b.planned_start) AS check_in_at,
                   l.received_amount,l.balance,l.refunded_amount,l.forfeited_amount,l.refund_transactions,
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
                        WHEN l.refunded_amount>0 THEN 'REFUNDED'
                        WHEN l.forfeited_amount>0 THEN 'FORFEITED'
                        ELSE 'SETTLED' END AS status,
                   CASE WHEN COALESCE(rr.pending_amount,0)>0 THEN rr.pending_amount
                        WHEN l.balance>0 THEN GREATEST(l.balance-COALESCE(rr.pending_amount,0),0)
                        WHEN l.refunded_amount>0 THEN l.refunded_amount
                        ELSE l.forfeited_amount END AS amount,
                   (b.status='CANCELLED' AND l.balance>0 AND ec.booking_id IS NOT NULL
                    AND NOT EXISTS (
                        SELECT 1 FROM gcmb.refund_request open_request
                        WHERE open_request.deposit_id=l.deposit_id AND open_request.kind='DEPOSIT'
                          AND open_request.status IN ('REQUESTED','APPROVED','PROCESSING','PAID')
                    )) AS can_request_refund,
                   ec.cancelled_at
            FROM gcmb.booking b
            JOIN ledger l ON l.booking_id=b.id
            LEFT JOIN refund_requests rr ON rr.deposit_id=l.deposit_id
            LEFT JOIN latest_room lr ON lr.booking_id=b.id
            LEFT JOIN eligible_cancel ec ON ec.booking_id=b.id
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

    private final JdbcTemplate db;

    public DepositRepository(JdbcTemplate db) { this.db = db; }

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
        return db.queryForObject(sql, (rs, rowNum) -> new DepositDtos.DepositSummary(
            rs.getBigDecimal("held_amount"), rs.getLong("held_bookings"), rs.getBigDecimal("pending_amount"),
            rs.getLong("pending_requests"), rs.getBigDecimal("refunded_amount"), rs.getLong("refund_transactions"),
            rs.getBigDecimal("forfeited_amount"), rs.getLong("forfeited_bookings")),
            branchId, dateFrom, timezone.getId(), dateFrom, dateTo, timezone.getId(), dateTo);
    }

    public long count(UUID branchId, LocalDate dateFrom, LocalDate dateTo, ZoneId timezone,
                      String status, String method, String search) {
        String sql = DEPOSIT_VIEW + " SELECT COUNT(*) FROM deposit_view v " + FILTERS;
        Long count = db.queryForObject(sql, Long.class, filterParameters(branchId, dateFrom, dateTo, timezone, status, method, search));
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
        return db.query(sql, DepositRepository::mapItem, params);
    }

    public DepositDtos.DepositItem item(UUID branchId, UUID bookingId) {
        String sql = DEPOSIT_VIEW + """
            SELECT v.booking_id,v.booking_code,v.customer_name,v.phone,v.room_code,v.room_name,v.check_in_at,
                   v.amount,v.received_amount,v.balance,v.pending_amount,v.refunded_amount,v.forfeited_amount,
                   v.payment_method,v.status,v.latest_request_status,v.activity_at,v.can_request_refund
            FROM deposit_view v
            WHERE v.branch_id=? AND v.booking_id=? AND v.status<>'SETTLED'
            """;
        List<DepositDtos.DepositItem> rows = db.query(sql, DepositRepository::mapItem, branchId, bookingId);
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
        return db.query(sql, (rs, rowNum) -> new DepositDtos.RefundableBooking(
            rs.getObject("booking_id", UUID.class), rs.getString("booking_code"), rs.getString("customer_name"),
            rs.getString("phone"), instant(rs, "cancelled_at"), rs.getBigDecimal("amount")), branchId);
    }

    public List<DepositDtos.RefundRequestItem> refundRequests(UUID branchId) {
        return db.query("""
            SELECT r.id,r.booking_id,b.code,b.contact_name,b.contact_phone,r.amount,r.reason,
                   r.beneficiary_name,r.bank_name,r.bank_account,r.status,r.created_at,r.paid_at,r.external_reference
            FROM gcmb.refund_request r
            JOIN gcmb.booking b ON b.id=r.booking_id
            WHERE r.branch_id=? AND r.kind='DEPOSIT'
            ORDER BY CASE WHEN r.status IN ('REQUESTED','APPROVED','PROCESSING') THEN 0 ELSE 1 END,
                     r.created_at DESC,r.id DESC
            """, (rs, rowNum) -> new DepositDtos.RefundRequestItem(
                rs.getObject("id", UUID.class), rs.getObject("booking_id", UUID.class), rs.getString("code"),
                rs.getString("contact_name"), rs.getString("contact_phone"), rs.getBigDecimal("amount"),
                rs.getString("reason"), rs.getString("beneficiary_name"), rs.getString("bank_name"),
                rs.getString("bank_account"), rs.getString("status"), instant(rs, "created_at"),
                instant(rs, "paid_at"), rs.getString("external_reference")), branchId);
    }

    public RefundRequestRow refundRequest(UUID branchId, UUID requestId, boolean lock) {
        String lockClause = lock ? " FOR UPDATE" : "";
        List<RefundRequestRow> rows = db.query("""
            SELECT id,booking_id,deposit_id,amount,reason,status
            FROM gcmb.refund_request
            WHERE branch_id=? AND id=? AND kind='DEPOSIT'
            """ + lockClause, (rs, rowNum) -> new RefundRequestRow(
                rs.getObject("id", UUID.class), rs.getObject("booking_id", UUID.class),
                rs.getObject("deposit_id", UUID.class), rs.getBigDecimal("amount"),
                rs.getString("reason"), rs.getString("status")), branchId, requestId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public boolean completeRefundRequest(UUID branchId, RefundRequestRow request, UUID actorId, String externalReference) {
        int updated = db.update("""
            UPDATE gcmb.refund_request
            SET status='PAID',processed_by=?,paid_at=CURRENT_TIMESTAMP,external_reference=?
            WHERE id=? AND branch_id=? AND kind='DEPOSIT'
              AND status IN ('REQUESTED','APPROVED','PROCESSING')
            """, actorId, externalReference, request.id(), branchId);
        if (updated != 1) return false;

        int inserted = db.update("""
            INSERT INTO gcmb.deposit_entry(deposit_id,kind,amount,refund_id,actor_id,reason,idempotency_key)
            VALUES(?,'REFUND',?,?,?,?,?)
            """, request.depositId(), request.amount(), request.id(), actorId, request.reason(),
            "DEPOSIT-REFUND-" + request.id());
        if (inserted != 1) throw new IllegalStateException("Deposit refund entry was not recorded");
        return true;
    }

    public boolean canRequestRefund(UUID branchId, UUID bookingId) {
        String sql = DEPOSIT_VIEW + """
            SELECT COALESCE(bool_or(v.can_request_refund),false)
            FROM deposit_view v
            WHERE v.branch_id=? AND v.booking_id=?
            """;
        Boolean allowed = db.queryForObject(sql, Boolean.class, branchId, bookingId);
        return Boolean.TRUE.equals(allowed);
    }

    public List<DepositDtos.DepositEvent> history(UUID branchId, UUID bookingId) {
        return db.query("""
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
            """, (rs, rowNum) -> new DepositDtos.DepositEvent(
                instant(rs, "happened_at"), rs.getString("type"), rs.getString("label"), rs.getString("details")),
            branchId, bookingId, branchId, bookingId, branchId, bookingId, branchId, bookingId);
    }

    private static Object[] filterParameters(UUID branchId, LocalDate dateFrom, LocalDate dateTo, ZoneId timezone,
                                              String status, String method, String search) {
        return new Object[] { branchId, dateFrom, timezone.getId(), dateFrom,
            dateTo, timezone.getId(), dateTo, status, status, method, method,
            search, search };
    }

    private static DepositDtos.DepositItem mapItem(ResultSet rs, int rowNum) throws SQLException {
        return new DepositDtos.DepositItem(rs.getObject("booking_id", UUID.class), rs.getString("booking_code"),
            rs.getString("customer_name"), rs.getString("phone"), rs.getString("room_code"), rs.getString("room_name"),
            instant(rs, "check_in_at"), rs.getBigDecimal("amount"), rs.getBigDecimal("received_amount"),
            rs.getBigDecimal("balance"), rs.getBigDecimal("pending_amount"), rs.getBigDecimal("refunded_amount"),
            rs.getBigDecimal("forfeited_amount"), rs.getString("payment_method"), rs.getString("status"),
            rs.getString("latest_request_status"), instant(rs, "activity_at"), rs.getBoolean("can_request_refund"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
