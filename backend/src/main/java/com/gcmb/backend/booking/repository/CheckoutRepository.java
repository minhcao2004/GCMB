package com.gcmb.backend.booking.repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
// Truy vấn bill checkout, yêu cầu thanh toán, giao dịch SePay và bút toán liên quan (UC-41, UC-42).
public class CheckoutRepository {

    private static final String ACTUAL_PAYMENT_FILTER =
        "NOT EXISTS (SELECT 1 FROM gcmb.checkout_cash_reclassification c WHERE c.payment_id=p.id)";

    private final JdbcTemplate jdbcTemplate;

    public CheckoutRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ---- Kỳ tài chính và tài khoản tiền ----

    public Map<String, Object> ensureAndLockFinancialPeriod(LocalDate month) {
        jdbcTemplate.update("INSERT INTO gcmb.financial_period(month,status) VALUES (?,'OPEN') ON CONFLICT(month) DO NOTHING", month);
        return jdbcTemplate.queryForMap("SELECT id,status FROM gcmb.financial_period WHERE month=? FOR SHARE", month);
    }

    public UUID ensureExternalDepositAccount(String accountCode, UUID branchId) {
        jdbcTemplate.update("""
            INSERT INTO gcmb.financial_account(code,name,kind,branch_id,active)
            VALUES (?,'Cọc chuyển khoản nhận ngoài hệ thống','BANK',?,true)
            ON CONFLICT(code) DO NOTHING
            """, accountCode, branchId);
        return jdbcTemplate.queryForObject("SELECT id FROM gcmb.financial_account WHERE code=? AND branch_id=? AND kind='BANK'",
            UUID.class, accountCode, branchId);
    }

    public UUID ensureSePayAccount(String accountCode, String bankName, String accountNumber, String accountHolder) {
        jdbcTemplate.update("""
            INSERT INTO gcmb.financial_account(code,name,kind,bank_name,bank_account_number,account_holder,active)
            VALUES (?,'Ngân hàng nhận thanh toán SePay','BANK',?,?,?,true) ON CONFLICT(code) DO NOTHING
            """, accountCode, bankName, accountNumber, accountHolder);
        return jdbcTemplate.queryForObject("SELECT id FROM gcmb.financial_account WHERE code=? AND active=true", UUID.class, accountCode);
    }

    // ---- Cọc chuyển khoản xác nhận thủ công ----

    public void cancelOpenDepositRequests(UUID bookingId) {
        jdbcTemplate.update("UPDATE gcmb.payment_request SET status='CANCELLED' WHERE booking_id=? AND purpose='DEPOSIT' AND status IN ('PENDING','EXPIRED')", bookingId);
    }

    public UUID findLatestDepositPaymentId(UUID bookingId) {
        return jdbcTemplate.queryForObject("SELECT p.id FROM gcmb.payment p WHERE p.booking_id=? AND p.purpose='DEPOSIT' ORDER BY p.created_at DESC LIMIT 1", UUID.class, bookingId);
    }

    public void insertFinancialTransaction(UUID transactionId, Object periodId, LocalDate bookingDate, UUID paymentId,
                                           String reason, UUID actorId, String idempotencyKey) {
        jdbcTemplate.update("INSERT INTO gcmb.financial_transaction(id,period_id,booking_date,occurred_at,kind,payment_id,reason,created_by,idempotency_key) VALUES (?,?,?,CURRENT_TIMESTAMP,'PAYMENT',?,?,?,?)",
            transactionId, periodId, bookingDate, paymentId, reason, actorId, idempotencyKey);
    }

    public void insertFinancialEntry(UUID transactionId, UUID accountId, UUID branchId, BigDecimal signedAmount) {
        jdbcTemplate.update("INSERT INTO gcmb.financial_entry(transaction_id,account_id,branch_id,amount_signed) VALUES (?,?,?,?)",
            transactionId, accountId, branchId, signedAmount);
    }

    // ---- Bill ----

    public List<Map<String, Object>> findBillIdsByBooking(UUID bookingId, UUID branchId) {
        return jdbcTemplate.queryForList("SELECT id FROM gcmb.bill WHERE booking_id=? AND branch_id=?", bookingId, branchId);
    }

    public List<Map<String, Object>> findBillIdsByBooking(UUID bookingId) {
        return jdbcTemplate.queryForList("SELECT id FROM gcmb.bill WHERE booking_id=?", bookingId);
    }

    public List<Map<String, Object>> findRecentBills(UUID branchId) {
        return jdbcTemplate.queryForList("""
            SELECT b.id,b.number,b.status,b.net_amount AS amount,k.contact_name AS customer
            FROM gcmb.bill b JOIN gcmb.booking k ON k.id=b.booking_id
            WHERE b.branch_id=? ORDER BY b.created_at DESC LIMIT 100
            """, branchId);
    }

    public List<Map<String, Object>> findBill(UUID billId, UUID branchId) {
        return jdbcTemplate.queryForList("""
            SELECT b.id,b.booking_id AS "bookingId",b.number,b.status,b.net_amount AS amount,
                br.name AS "branchName",br.phone AS "branchPhone",br.timezone AS "branchTimezone",
                b.gross_amount AS "grossAmount",b.discount_amount AS "discountAmount",
                b.checkout_rounding_adjustment AS "roundingAdjustment",
                b.checkout_at AS "checkoutAt",b.inspection_note AS "inspectionNote",
                b.planned_cash AS "plannedCash",b.planned_cash_tendered AS "plannedCashTendered",
                COALESCE((SELECT sum(e.amount) FROM gcmb.deposit_entry e WHERE e.bill_id=b.id AND e.kind='APPLY'),0) AS "depositApplied",
                k.code AS "bookingCode",k.contact_name AS customer,k.actual_checkin AS checkin,
                COALESCE((SELECT r.code||' · '||r.name FROM gcmb.room_allocation a JOIN gcmb.room r ON r.id=a.room_id
                    WHERE a.booking_id=k.id ORDER BY a.created_at DESC LIMIT 1),'—') AS room,
                COALESCE((SELECT sum(p.amount) FROM gcmb.payment p WHERE p.bill_id=b.id AND NOT EXISTS (SELECT 1 FROM gcmb.checkout_cash_reclassification c WHERE c.payment_id=p.id)),0) AS paid
            FROM gcmb.bill b JOIN gcmb.booking k ON k.id=b.booking_id
            JOIN gcmb.branch br ON br.id=b.branch_id
            WHERE b.id=? AND b.branch_id=?
            """, billId, branchId);
    }

    public List<Map<String, Object>> findBillLines(UUID billId) {
        return jdbcTemplate.queryForList("""
            SELECT l.description,l.quantity,l.unit_price AS "unitPrice",l.amount,
                CASE WHEN l.order_line_id IS NOT NULL THEN 'FNB'
                    WHEN c.kind='SURCHARGE' THEN 'SURCHARGE' ELSE 'ROOM' END AS category
            FROM gcmb.bill_line l LEFT JOIN gcmb.booking_charge c ON c.id=l.booking_charge_id
            WHERE l.bill_id=? ORDER BY l.created_at,l.id
            """, billId);
    }

    public List<Map<String, Object>> findLatestPaymentRequest(UUID billId) {
        return jdbcTemplate.queryForList("""
            SELECT q.id,q.reference_code AS reference,q.expected_amount AS amount,q.status,q.expires_at AS "expiresAt",
                f.bank_name AS bank,f.bank_account_number AS account,f.account_holder AS holder
            FROM gcmb.payment_request q JOIN gcmb.financial_account f ON f.id=q.account_id
            WHERE q.bill_id=? AND q.status<>'CANCELLED' ORDER BY q.created_at DESC,q.id DESC LIMIT 1
            """, billId);
    }

    public List<Map<String, Object>> findBillPayments(UUID billId) {
        return jdbcTemplate.queryForList(
            "SELECT method,amount,cash_tendered AS \"cashTendered\",cash_change AS \"cashChange\" FROM gcmb.payment p WHERE bill_id=? AND " + ACTUAL_PAYMENT_FILTER + " ORDER BY created_at",
            billId);
    }

    public void insertSurcharge(UUID bookingId, String reason, BigDecimal amount) {
        jdbcTemplate.update("""
            INSERT INTO gcmb.booking_charge(booking_id,kind,description,quantity,unit_price,amount,pricing_snapshot,status)
            VALUES (?,'SURCHARGE',?,1,?,?,jsonb_build_object('source','CHECKOUT','reason',CAST(? AS text)),'ACTIVE')
            """, bookingId, reason, amount, amount, reason);
    }

    public void insertBill(UUID billId, UUID bookingId, UUID branchId, String number, BigDecimal grossAmount,
                           BigDecimal discountAmount, BigDecimal netAmount, BigDecimal roundingAdjustment,
                           Timestamp checkoutAt, String inspectionNote, UUID issuedBy) {
        jdbcTemplate.update("""
            INSERT INTO gcmb.bill(id,booking_id,branch_id,number,status,gross_amount,discount_amount,net_amount,checkout_rounding_adjustment,
                issued_at,checkout_at,inspection_note,issued_by,discount_by)
            VALUES (?,?,?,?,'ISSUED',?,?,?,?,CURRENT_TIMESTAMP,?,?,?,?)
            """, billId, bookingId, branchId, number, grossAmount, discountAmount, netAmount, roundingAdjustment,
            checkoutAt, inspectionNote, issuedBy, discountAmount.signum() > 0 ? issuedBy : null);
    }

    public void insertBillLine(UUID billId, Object chargeId, Object orderLineId, Object description,
                               Object quantity, Object unitPrice, Object amount) {
        jdbcTemplate.update("""
            INSERT INTO gcmb.bill_line(bill_id,booking_charge_id,order_line_id,description,quantity,unit_price,amount)
            VALUES (?,?,?,?,?,?,?)
            """, billId, chargeId, orderLineId, description, quantity, unitPrice, amount);
    }

    public void insertDepositApplication(UUID depositId, BigDecimal amount, UUID billId, UUID actorId, String idempotencyKey) {
        jdbcTemplate.update("""
            INSERT INTO gcmb.deposit_entry(deposit_id,kind,amount,bill_id,actor_id,reason,idempotency_key)
            VALUES (?,'APPLY',?,?,?,'Trừ cọc đã thu vào bill checkout',?)
            """, depositId, amount, billId, actorId, idempotencyKey);
    }

    public boolean hasPaymentOrOpenRequest(UUID billId) {
        return !jdbcTemplate.queryForList(
            "SELECT id FROM gcmb.payment p WHERE bill_id=? AND " + ACTUAL_PAYMENT_FILTER + " UNION ALL SELECT id FROM gcmb.payment_request WHERE bill_id=? AND status<>'CANCELLED'",
            billId, billId).isEmpty();
    }

    public boolean hasActualPayment(UUID billId) {
        return !jdbcTemplate.queryForList(
            "SELECT id FROM gcmb.payment p WHERE bill_id=? AND " + ACTUAL_PAYMENT_FILTER, billId).isEmpty();
    }

    public void updatePlannedCash(UUID billId, BigDecimal plannedCash, BigDecimal plannedCashTendered) {
        jdbcTemplate.update("UPDATE gcmb.bill SET planned_cash=?,planned_cash_tendered=? WHERE id=?", plannedCash, plannedCashTendered, billId);
    }

    public void insertBillPaymentRequest(UUID branchId, UUID bookingId, UUID billId, UUID accountId, String referenceCode, BigDecimal amount) {
        jdbcTemplate.update("""
            INSERT INTO gcmb.payment_request(branch_id,booking_id,bill_id,account_id,purpose,reference_code,expected_amount,expires_at,status)
            VALUES (?,?,?,?,'BILL',?,?,CURRENT_TIMESTAMP+interval '30 minutes','PENDING')
            """, branchId, bookingId, billId, accountId, referenceCode, amount);
    }

    public void insertCashPayment(UUID branchId, UUID bookingId, UUID billId, BigDecimal amount, BigDecimal cashTendered,
                                  BigDecimal cashChange, UUID receivedBy, String idempotencyKey) {
        jdbcTemplate.update("""
            INSERT INTO gcmb.payment(branch_id,booking_id,bill_id,purpose,method,amount,cash_tendered,cash_change,received_at,received_by,idempotency_key)
            VALUES (?,?,?,'BILL','CASH',?,?,?,CURRENT_TIMESTAMP,?,?)
            """, branchId, bookingId, billId, amount, cashTendered, cashChange, receivedBy, idempotencyKey);
    }

    public void settleBill(UUID billId) {
        jdbcTemplate.update("UPDATE gcmb.bill SET status='SETTLED',completed_at=CURRENT_TIMESTAMP,planned_cash=0,planned_cash_tendered=0 WHERE id=?", billId);
    }

    public void finishAllocation(UUID allocationId, Timestamp actualEnd) {
        jdbcTemplate.update("UPDATE gcmb.room_allocation SET state='FINISHED',actual_end=? WHERE id=?", actualEnd, allocationId);
    }

    public void completeBooking(UUID bookingId, Timestamp actualCheckout) {
        jdbcTemplate.update("UPDATE gcmb.booking SET status='COMPLETED',actual_checkout=? WHERE id=?", actualCheckout, bookingId);
    }

    public void releaseRoomAfterCheckout(UUID roomId, UUID branchId, String previousStatus, UUID actorId) {
        jdbcTemplate.update("UPDATE gcmb.room SET operating_status='AVAILABLE',status_reason='Khách đã checkout',expected_ready_at=NULL WHERE id=?", roomId);
        jdbcTemplate.update("""
            INSERT INTO gcmb.room_operating_status_history(room_id,branch_id,previous_status,new_status,reason,changed_by)
            VALUES (?, ?, ?, 'AVAILABLE','Khách đã checkout',?)
            """, roomId, branchId, previousStatus, actorId);
    }

    public int renewExpiredBillRequest(UUID billId) {
        return jdbcTemplate.update("""
            UPDATE gcmb.payment_request SET expires_at=CURRENT_TIMESTAMP+interval '30 minutes'
            WHERE bill_id=? AND status='PENDING' AND expires_at<CURRENT_TIMESTAMP
            """, billId);
    }

    public int cancelOpenBillRequests(UUID billId) {
        return jdbcTemplate.update("""
            UPDATE gcmb.payment_request SET status='CANCELLED'
            WHERE bill_id=? AND status IN ('PENDING','EXPIRED')
            """, billId);
    }

    public void resetPlannedCash(UUID billId) {
        jdbcTemplate.update("UPDATE gcmb.bill SET planned_cash=0,planned_cash_tendered=0 WHERE id=?", billId);
    }

    public void lockBill(Object billId) {
        jdbcTemplate.queryForList("SELECT id FROM gcmb.bill WHERE id=? FOR UPDATE", billId);
    }

    public boolean lockBookingInBranch(UUID bookingId, UUID branchId) {
        return !jdbcTemplate.queryForList("SELECT id FROM gcmb.booking WHERE id=? AND branch_id=? FOR UPDATE",
            UUID.class, bookingId, branchId).isEmpty();
    }

    // ---- Dòng tính tiền, order và hoàn cọc ----

    public List<Map<String, Object>> findChargeAndServedOrderLines(UUID bookingId) {
        return jdbcTemplate.queryForList("""
            SELECT c.id AS "chargeId",NULL::uuid AS "orderId",c.description,c.quantity,c.unit_price AS "unitPrice",c.amount,
                CASE WHEN c.kind='SURCHARGE' THEN 'SURCHARGE' ELSE 'ROOM' END AS category,c.kind AS "chargeKind"
            FROM gcmb.booking_charge c WHERE c.booking_id=? AND c.status='ACTIVE'
            UNION ALL
            SELECT NULL::uuid,l.id,l.name_snapshot,l.quantity,l.unit_price,round(l.quantity*l.unit_price),'FNB','FNB'
            FROM gcmb.order_line l JOIN gcmb.fb_order o ON o.id=l.order_id
            WHERE l.booking_id=? AND o.status='SERVED' AND l.unit_price>0
            """, bookingId, bookingId);
    }

    public List<Map<String, Object>> findPendingOrders(UUID bookingId) {
        return jdbcTemplate.queryForList("""
            SELECT o.id,o.status,COALESCE(sum(l.quantity),0) AS "itemCount" FROM gcmb.fb_order o
            LEFT JOIN gcmb.order_line l ON l.order_id=o.id
            WHERE o.booking_id=? AND o.status IN ('PENDING','ACCEPTED') GROUP BY o.id,o.status
            """, bookingId);
    }

    public boolean hasPendingDepositRefund(UUID bookingId) {
        return !jdbcTemplate.queryForList("""
            SELECT id FROM gcmb.refund_request WHERE booking_id=? AND kind='DEPOSIT'
                AND status IN ('REQUESTED','APPROVED','PROCESSING')
            """, bookingId).isEmpty();
    }

    // ---- SePay webhook ----

    public List<Map<String, Object>> findPaymentRequestsByReference(String referenceCode) {
        return jdbcTemplate.queryForList(
            "SELECT * FROM gcmb.payment_request WHERE reference_code=? AND purpose IN ('BILL','DEPOSIT')", referenceCode);
    }

    public Map<String, Object> lockPaymentRequest(Object requestId) {
        return jdbcTemplate.queryForMap("SELECT * FROM gcmb.payment_request WHERE id=? FOR UPDATE", requestId);
    }

    public boolean bankTransactionExists(UUID accountId, String externalId) {
        return !jdbcTemplate.queryForList("SELECT id FROM gcmb.bank_transaction WHERE provider='SEPAY' AND account_id=? AND external_id=?",
            UUID.class, accountId, externalId).isEmpty();
    }

    public void insertClosedPeriodBankTransaction(UUID transactionId, UUID accountId, String externalId, String direction,
                                                  BigDecimal amount, String content, Timestamp occurredAt, String rawPayload) {
        jdbcTemplate.update("""
            INSERT INTO gcmb.bank_transaction(id,account_id,provider,external_id,direction,amount,transfer_content,
              occurred_at,match_status,raw_payload,exception_reason)
            VALUES (?,?,'SEPAY',?,?,?,?,?,'EXCEPTION',CAST(? AS jsonb),'PERIOD_CLOSED')
            ON CONFLICT(provider,account_id,external_id) DO NOTHING
            """, transactionId, accountId, externalId, direction, amount, content, occurredAt, rawPayload);
    }

    public int insertUnmatchedBankTransaction(UUID transactionId, UUID accountId, String externalId, String direction,
                                              BigDecimal amount, String content, Timestamp occurredAt, String rawPayload) {
        return jdbcTemplate.update("""
            INSERT INTO gcmb.bank_transaction(id,account_id,provider,external_id,direction,amount,transfer_content,occurred_at,match_status,raw_payload)
            VALUES (?,?,'SEPAY',?,?,?,?,?,'UNMATCHED',CAST(? AS jsonb)) ON CONFLICT(provider,account_id,external_id) DO NOTHING
            """, transactionId, accountId, externalId, direction, amount, content, occurredAt, rawPayload);
    }

    public void markBankTransactionException(UUID transactionId, String reason) {
        jdbcTemplate.update("UPDATE gcmb.bank_transaction SET match_status='EXCEPTION',exception_reason=? WHERE id=?", reason, transactionId);
    }

    public void insertBankTransferPayment(UUID paymentId, Object branchId, Object bookingId, Object billId, Object purpose,
                                          BigDecimal amount, Object requestId, UUID bankTransactionId, Timestamp receivedAt,
                                          String idempotencyKey) {
        jdbcTemplate.update("""
            INSERT INTO gcmb.payment(id,branch_id,booking_id,bill_id,purpose,method,amount,request_id,bank_transaction_id,received_at,idempotency_key)
            VALUES (?,?,?,?,?,'BANK_TRANSFER',?,?,?, ?,?)
            """, paymentId, branchId, bookingId, billId, purpose, amount, requestId, bankTransactionId, receivedAt, idempotencyKey);
    }

    public void insertSePayDepositReceipt(BigDecimal amount, UUID paymentId, String idempotencyKey, Object bookingId) {
        jdbcTemplate.update("""
            INSERT INTO gcmb.deposit_entry(deposit_id,kind,amount,payment_id,reason,idempotency_key)
            SELECT id,'RECEIVE',?,?,'SePay xác nhận tiền cọc',? FROM gcmb.deposit WHERE booking_id=?
            """, amount, paymentId, idempotencyKey, bookingId);
    }

    // ---- Audit ----

    public void insertBookingAudit(UUID actorId, UUID branchId, String action, UUID bookingId, String afterJson) {
        jdbcTemplate.update("""
            INSERT INTO gcmb.audit_event(actor_id,branch_id,action,entity_type,entity_id,after_data)
            VALUES (?,?,?,'BOOKING',?,CAST(? AS jsonb))
            """, actorId, branchId, action, bookingId, afterJson);
    }
}
