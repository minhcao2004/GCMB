package com.gcmb.backend.booking.repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
// Truy vấn yêu cầu hóa đơn thuế, bill liên quan và lịch sử xử lý (UC-127/BR-125).
public class TaxInvoiceRepository {

    private static final String REQUEST_VIEW = """
        SELECT t.booking_id AS "bookingId",b.code AS "bookingCode",b.contact_name AS customer,
          b.branch_id AS "branchId",br.name AS "branchName",t.buyer_name AS "buyerName",
          t.tax_code AS "taxCode",t.buyer_address AS "buyerAddress",t.contact_email AS "contactEmail",
          t.status,t.note,t.delivery_result AS "deliveryResult",t.version,
          t.created_at AS "createdAt",t.updated_at AS "updatedAt"
        FROM gcmb.tax_invoice_request t JOIN gcmb.booking b ON b.id=t.booking_id
        JOIN gcmb.branch br ON br.id=b.branch_id
        """;

    private final JdbcTemplate jdbcTemplate;

    public TaxInvoiceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isActiveAccountInScope(UUID userId, String roleCode, UUID branchId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM gcmb.user_account u JOIN gcmb.role r ON r.id=u.role_id WHERE u.id=? AND u.status='ACTIVE' AND r.code=? AND (r.code='ACCOUNTANT' OR u.branch_id=?))",
            Boolean.class, userId, roleCode, branchId));
    }

    public List<Map<String, Object>> findBookingWithBranch(UUID bookingId, boolean lockForWrite) {
        return jdbcTemplate.queryForList(
            "SELECT b.id,b.code,b.branch_id,br.name,br.status FROM gcmb.booking b JOIN gcmb.branch br ON br.id=b.branch_id WHERE b.id=?" +
                (lockForWrite ? " FOR UPDATE OF b FOR SHARE OF br" : ""),
            bookingId);
    }

    public List<Map<String, Object>> findRequestsInScope(String roleCode, UUID branchId) {
        return jdbcTemplate.queryForList(
            REQUEST_VIEW + " WHERE (?='ACCOUNTANT' OR b.branch_id=?) ORDER BY t.updated_at DESC,t.booking_id",
            roleCode, branchId);
    }

    public List<Map<String, Object>> findRequest(UUID bookingId) {
        return jdbcTemplate.queryForList(REQUEST_VIEW + " WHERE t.booking_id=?", bookingId);
    }

    public Map<String, Object> getRequest(UUID bookingId) {
        return jdbcTemplate.queryForMap(REQUEST_VIEW + " WHERE t.booking_id=?", bookingId);
    }

    public List<Map<String, Object>> findBillsForBooking(UUID bookingId) {
        return jdbcTemplate.queryForList("""
            SELECT b.id,b.number,b.status,b.net_amount AS amount,b.checkout_at AS "checkoutAt",
                k.contact_name AS customer,k.actual_checkin AS checkin,
                COALESCE((SELECT r.code||' · '||r.name FROM gcmb.room_allocation a JOIN gcmb.room r ON r.id=a.room_id
                    WHERE a.booking_id=k.id ORDER BY a.created_at DESC LIMIT 1),'—') AS room,
                COALESCE((SELECT sum(e.amount) FROM gcmb.deposit_entry e WHERE e.bill_id=b.id AND e.kind='APPLY'),0) AS "depositApplied",
                COALESCE((SELECT sum(p.amount) FROM gcmb.payment p WHERE p.bill_id=b.id
                    AND NOT EXISTS (SELECT 1 FROM gcmb.checkout_cash_reclassification c WHERE c.payment_id=p.id)),0) AS paid
            FROM gcmb.bill b JOIN gcmb.booking k ON k.id=b.booking_id WHERE b.booking_id=?
            """, bookingId);
    }

    public List<Map<String, Object>> findBillLines(Object billId) {
        return jdbcTemplate.queryForList("""
            SELECT l.description,l.quantity,l.amount,
                CASE WHEN l.order_line_id IS NOT NULL THEN 'FNB'
                    WHEN c.kind='SURCHARGE' THEN 'SURCHARGE' ELSE 'ROOM' END AS category
            FROM gcmb.bill_line l LEFT JOIN gcmb.booking_charge c ON c.id=l.booking_charge_id
            WHERE l.bill_id=? ORDER BY l.created_at,l.id
            """, billId);
    }

    public List<Map<String, Object>> findHistory(UUID bookingId) {
        return jdbcTemplate.queryForList(
            "SELECT action,after_data::text AS data,created_at AS \"createdAt\" FROM gcmb.audit_event WHERE entity_type='TAX_INVOICE_REQUEST' AND entity_id=? ORDER BY created_at,id",
            bookingId);
    }

    public boolean requestExists(UUID bookingId) {
        return !jdbcTemplate.queryForList(
            "SELECT booking_id FROM gcmb.tax_invoice_request WHERE booking_id=?", bookingId).isEmpty();
    }

    public void insertRequest(UUID bookingId, String buyerName, String taxCode, String buyerAddress,
                              String contactEmail, String note, UUID actorId) {
        jdbcTemplate.update(
            "INSERT INTO gcmb.tax_invoice_request(booking_id,buyer_name,tax_code,buyer_address,contact_email,note,created_by,updated_by) VALUES (?,?,?,?,?,?,?,?)",
            bookingId, buyerName, taxCode, buyerAddress, contactEmail, note, actorId, actorId);
    }

    public List<Map<String, Object>> lockRequestProgress(UUID bookingId) {
        return jdbcTemplate.queryForList(
            "SELECT version,status,delivery_result FROM gcmb.tax_invoice_request WHERE booking_id=? FOR UPDATE",
            bookingId);
    }

    public void updateProgress(UUID bookingId, String status, String deliveryResult, UUID actorId) {
        jdbcTemplate.update(
            "UPDATE gcmb.tax_invoice_request SET status=?,delivery_result=?,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE booking_id=?",
            status, deliveryResult, actorId, bookingId);
    }

    public Long countUnread(UUID userId) {
        return jdbcTemplate.queryForObject(
            "SELECT count(*) FROM gcmb.tax_invoice_request t WHERE NOT EXISTS(SELECT 1 FROM gcmb.tax_invoice_read r WHERE r.booking_id=t.booking_id AND r.user_id=?)",
            Long.class, userId);
    }

    public void markRead(UUID bookingId, UUID userId) {
        jdbcTemplate.update(
            "INSERT INTO gcmb.tax_invoice_read(booking_id,user_id) VALUES (?,?) ON CONFLICT DO NOTHING",
            bookingId, userId);
    }

    public void insertAudit(UUID actorId, Object branchId, String action, UUID bookingId, String afterJson) {
        jdbcTemplate.update(
            "INSERT INTO gcmb.audit_event(actor_id,branch_id,action,entity_type,entity_id,after_data) VALUES (?,?,?,'TAX_INVOICE_REQUEST',?,CAST(? AS jsonb))",
            actorId, branchId, action, bookingId, afterJson);
    }
}
