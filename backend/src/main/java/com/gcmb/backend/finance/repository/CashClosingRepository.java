package com.gcmb.backend.finance.repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
// Chốt quỹ tiền mặt hằng ngày, đối soát kế toán và nộp tiền khỏi két (UC-98..101, BR-95..98/208).
public class CashClosingRepository {

    private final JdbcTemplate jdbcTemplate;

    public CashClosingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ---- Két, cơ sở và kỳ ----

    public List<UUID> findActiveDrawerAccounts(UUID branchId) {
        return jdbcTemplate.queryForList(
            "SELECT id FROM gcmb.financial_account WHERE branch_id=? AND kind='CASH_DRAWER' AND active", UUID.class, branchId);
    }

    public String findBranchTimezone(UUID branchId) {
        return jdbcTemplate.queryForObject("SELECT timezone FROM gcmb.branch WHERE id=?", String.class, branchId);
    }

    public boolean hasClosingAfter(UUID accountId, LocalDate businessDate) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM gcmb.daily_cash_closing WHERE account_id=? AND business_date>?)",
            Boolean.class, accountId, businessDate));
    }

    public boolean hasClosingOn(UUID accountId, LocalDate businessDate) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM gcmb.daily_cash_closing WHERE account_id=? AND business_date=?)",
            Boolean.class, accountId, businessDate));
    }

    public boolean isPeriodClosed(LocalDate month) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM gcmb.financial_period WHERE month=? AND status='CLOSED')", Boolean.class, month));
    }

    public Date findLatestClosingDate(Object accountId) {
        return jdbcTemplate.queryForObject(
            "SELECT max(business_date) FROM gcmb.daily_cash_closing WHERE account_id=?", Date.class, accountId);
    }

    public Integer countPendingShiftDiscrepancies(UUID branchId) {
        return jdbcTemplate.queryForObject(
            "SELECT (SELECT count(*) FROM gcmb.shift_cash_handover WHERE branch_id=? AND status='DISCREPANCY') "
                + "+ (SELECT count(*) FROM gcmb.shift_cash_dispatch WHERE branch_id=? AND status='DISCREPANCY')",
            Integer.class, branchId, branchId);
    }

    // ---- Dòng tiền nguồn của két (BR-95) ----

    /** Toàn bộ dòng tiền mặt của két trước mốc {@code cutoff}; tiền thu chưa ghi sổ cái chỉ được tính một lần. */
    public List<Map<String, Object>> findCashMovementsBefore(UUID accountId, UUID branchId, Timestamp cutoff) {
        return jdbcTemplate.queryForList("""
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
            """, accountId, cutoff, branchId, cutoff, accountId, cutoff, accountId, cutoff, accountId, cutoff);
    }

    public List<Map<String, Object>> findClosingsOn(UUID accountId, LocalDate businessDate) {
        return jdbcTemplate.queryForList("""
            SELECT c.id,coalesce(r.state,c.status) AS status,coalesce(r.revision,0) AS report_version
            FROM gcmb.daily_cash_closing c LEFT JOIN gcmb.cash_closing_report r ON r.closing_id=c.id
            WHERE c.account_id=? AND c.business_date=?
            """, accountId, businessDate);
    }

    public Timestamp findLastFullSweepAt(UUID accountId) {
        return jdbcTemplate.queryForObject("""
            SELECT max(time) FROM (
              SELECT occurred_at AS time FROM gcmb.cash_drawer_remittance WHERE account_id=? AND full_sweep
              UNION ALL SELECT received_at FROM gcmb.cash_closing_handover WHERE account_id=? AND received_at IS NOT NULL
            ) swept
            """, Timestamp.class, accountId, accountId);
    }

    public Timestamp findFirstReceiptAfter(UUID accountId, Timestamp after) {
        return jdbcTemplate.queryForObject("""
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
            """, Timestamp.class, accountId, after, accountId, after, accountId, after);
    }

    // ---- Báo cáo chốt quỹ ----

    public void insertClosing(UUID closingId, UUID branchId, Object accountId, LocalDate businessDate, Object openingAmount,
                              Object cashIn, Object cashOut, BigDecimal expectedClosing, BigDecimal actualClosing,
                              BigDecimal variance, String explanation, UUID submittedBy, String sourceSnapshot) {
        jdbcTemplate.update("""
            INSERT INTO gcmb.daily_cash_closing(id,branch_id,account_id,business_date,opening_amount,cash_in,cash_out,
              expected_closing,actual_closing,variance,explanation,status,submitted_by,submitted_at,source_snapshot)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,'SUBMITTED',?,CURRENT_TIMESTAMP,?::jsonb)
            """, closingId, branchId, accountId, businessDate, openingAmount, cashIn, cashOut, expectedClosing, actualClosing,
            variance, explanation, submittedBy, sourceSnapshot);
    }

    public void insertReportRevision(UUID closingId, int revision, String payload, UUID submittedBy) {
        jdbcTemplate.update("INSERT INTO gcmb.cash_closing_report_revision(closing_id,revision,payload,submitted_by) VALUES (?,?,?::jsonb,?)",
            closingId, revision, payload, submittedBy);
    }

    public void insertLegacyFirstRevision(UUID closingId, String payload, Object submittedBy) {
        jdbcTemplate.update("INSERT INTO gcmb.cash_closing_report_revision VALUES (?,1,?::jsonb,?,CURRENT_TIMESTAMP)",
            closingId, payload, submittedBy);
    }

    public void insertReport(UUID closingId, int revision) {
        jdbcTemplate.update("INSERT INTO gcmb.cash_closing_report VALUES (?,?,'SUBMITTED')", closingId, revision);
    }

    public void updateReportRevision(UUID closingId, int revision) {
        jdbcTemplate.update("UPDATE gcmb.cash_closing_report SET revision=?,state='SUBMITTED' WHERE closing_id=?", revision, closingId);
    }

    public void updateReportState(UUID closingId, String state) {
        jdbcTemplate.update("UPDATE gcmb.cash_closing_report SET state=? WHERE closing_id=?", state, closingId);
    }

    public void insertRevisionEvidence(UUID closingId, int revision, String originalName, String mimeType, String objectKey) {
        jdbcTemplate.update("INSERT INTO gcmb.cash_closing_revision_evidence VALUES (?,?,?,?,?)",
            closingId, revision, originalName, mimeType, objectKey);
    }

    public List<Map<String, Object>> findCurrentReport(Object closingId) {
        return jdbcTemplate.queryForList("""
            SELECT r.state,r.revision,v.payload,v.submitted_at,u.username
            FROM gcmb.cash_closing_report r JOIN gcmb.cash_closing_report_revision v
              ON v.closing_id=r.closing_id AND v.revision=r.revision
            JOIN gcmb.user_account u ON u.id=v.submitted_by WHERE r.closing_id=?
            """, closingId);
    }

    public List<Map<String, Object>> findReportRevisions(Object closingId) {
        return jdbcTemplate.queryForList("""
            SELECT v.revision,v.submitted_at,u.username,v.payload FROM gcmb.cash_closing_report_revision v
            JOIN gcmb.user_account u ON u.id=v.submitted_by WHERE v.closing_id=? ORDER BY v.revision
            """, closingId);
    }

    public List<Map<String, Object>> findClosings(UUID branchId, LocalDate from, LocalDate to, String status) {
        String statusFilter = status == null ? "" : status;
        return jdbcTemplate.queryForList("""
            SELECT c.*,b.name AS "branchName",u.username AS "submittedBy"
            FROM gcmb.daily_cash_closing c JOIN gcmb.branch b ON b.id=c.branch_id
            JOIN gcmb.user_account u ON u.id=c.submitted_by
            LEFT JOIN gcmb.cash_closing_report r ON r.closing_id=c.id
            WHERE (CAST(? AS uuid) IS NULL OR c.branch_id=?) AND c.business_date BETWEEN ? AND ?
              AND (?='' OR coalesce(r.state,c.status)=?)
            ORDER BY c.business_date DESC,b.name
            """, branchId, branchId, from, to, statusFilter, statusFilter);
    }

    public List<Map<String, Object>> findBranchClosingStatuses(LocalDate businessDate, UUID branchId) {
        return jdbcTemplate.queryForList("""
            SELECT b.id AS "branchId",b.name AS "branchName",?::date::text AS "businessDate",c.id AS "closingId",
              coalesce(c.state,'NOT_SUBMITTED') AS status,c.submitted_at AS "submittedAt",
              c.expected_closing AS "expectedClosing",c.actual_closing AS "actualClosing",c.variance,c.explanation
            FROM gcmb.branch b LEFT JOIN LATERAL (
              SELECT d.id,coalesce(r.state,d.status) state,coalesce(v.submitted_at,d.submitted_at) submitted_at,
                coalesce((v.payload->>'expected_closing')::numeric,d.expected_closing) expected_closing,
                coalesce((v.payload->>'actual_closing')::numeric,d.actual_closing) actual_closing,
                coalesce((v.payload->>'variance')::numeric,d.variance) variance,
                coalesce(v.payload->>'explanation',d.explanation) explanation
              FROM gcmb.daily_cash_closing d
              LEFT JOIN gcmb.cash_closing_report r ON r.closing_id=d.id
              LEFT JOIN gcmb.cash_closing_report_revision v ON v.closing_id=r.closing_id AND v.revision=r.revision
              WHERE d.branch_id=b.id AND d.business_date=? AND coalesce(r.state,d.status)<>'DRAFT'
              ORDER BY d.submitted_at DESC,d.id DESC LIMIT 1
            ) c ON true
            WHERE (CAST(? AS uuid) IS NULL OR b.id=?) ORDER BY b.name,b.id
            """, businessDate, businessDate, branchId, branchId);
    }

    public long countUnreconciled(LocalDate from, LocalDate to, UUID branchId) {
        return jdbcTemplate.queryForObject("""
            SELECT count(*) FROM gcmb.daily_cash_closing c
            LEFT JOIN gcmb.cash_closing_report r ON r.closing_id=c.id
            WHERE c.business_date BETWEEN ? AND ?
              AND (CAST(? AS uuid) IS NULL OR c.branch_id=?)
              AND coalesce(r.state,c.status) IN ('SUBMITTED','NEEDS_EXPLANATION')
            """, Long.class, from, to, branchId, branchId);
    }

    public List<Map<String, Object>> findNotifications(UUID recipientId) {
        return jdbcTemplate.queryForList("""
            SELECT n.id,n.closing_id AS "closingId",n.report_version AS "reportVersion",
              n.created_at AS "sentAt",b.name AS "branchName",c.business_date::text AS "businessDate",
              'Báo cáo chốt quỹ ngày' AS "reportType"
            FROM gcmb.cash_closing_notification n
            JOIN gcmb.daily_cash_closing c ON c.id=n.closing_id
            JOIN gcmb.branch b ON b.id=c.branch_id
            WHERE n.recipient_id=? ORDER BY n.created_at DESC LIMIT 100
            """, recipientId);
    }

    public List<Map<String, Object>> findClosing(UUID closingId) {
        return jdbcTemplate.queryForList("""
            SELECT c.*,b.name AS "branchName",u.username AS "submittedBy"
            FROM gcmb.daily_cash_closing c JOIN gcmb.branch b ON b.id=c.branch_id
            JOIN gcmb.user_account u ON u.id=c.submitted_by WHERE c.id=?
            """, closingId);
    }

    public List<Map<String, Object>> findReviews(UUID closingId) {
        return jdbcTemplate.queryForList("""
            SELECT r.*,u.username FROM gcmb.cash_reconciliation r JOIN gcmb.user_account u ON u.id=r.reviewed_by
            WHERE closing_id=? ORDER BY created_at,id
            """, closingId);
    }

    public List<Map<String, Object>> findEvidenceNames(UUID closingId) {
        return jdbcTemplate.queryForList("""
            SELECT original_name AS name,0 AS revision FROM gcmb.cash_closing_evidence WHERE closing_id=?
            UNION ALL SELECT original_name,revision FROM gcmb.cash_closing_revision_evidence WHERE closing_id=? ORDER BY revision
            """, closingId, closingId);
    }

    public List<Map<String, Object>> findLegacyHandover(UUID closingId) {
        return jdbcTemplate.queryForList("""
            SELECT h.received_at,c.actual_closing AS amount,u.username FROM gcmb.cash_closing_handover h
            JOIN gcmb.daily_cash_closing c ON c.id=h.closing_id
            LEFT JOIN gcmb.user_account u ON u.id=h.received_by WHERE h.closing_id=? AND h.received_at IS NOT NULL
            """, closingId);
    }

    public void insertReconciliation(UUID closingId, UUID reviewedBy, String decision, String note) {
        jdbcTemplate.update("INSERT INTO gcmb.cash_reconciliation(closing_id,reviewed_by,decision,note) VALUES (?,?,?,?)",
            closingId, reviewedBy, decision, note);
    }

    public void markClosingReconciled(UUID closingId) {
        jdbcTemplate.update("UPDATE gcmb.daily_cash_closing SET status='RECONCILED' WHERE id=?", closingId);
    }

    public List<Map<String, Object>> findOriginalEvidence(UUID closingId) {
        return jdbcTemplate.queryForList("SELECT * FROM gcmb.cash_closing_evidence WHERE closing_id=?", closingId);
    }

    public List<Map<String, Object>> findRevisionEvidence(UUID closingId, int revision) {
        return jdbcTemplate.queryForList("SELECT * FROM gcmb.cash_closing_revision_evidence WHERE closing_id=? AND revision=?", closingId, revision);
    }

    // ---- Nộp tiền khỏi két ----

    public List<Map<String, Object>> findRemittanceByIdempotencyKey(UUID requestId) {
        return jdbcTemplate.queryForList("SELECT * FROM gcmb.cash_drawer_remittance WHERE idempotency_key=?", requestId);
    }

    public void insertRemittance(UUID remittanceId, UUID accountId, UUID branchId, Timestamp occurredAt, BigDecimal amount,
                                 String reason, String reference, UUID recordedBy, UUID idempotencyKey,
                                 BigDecimal balanceBefore, boolean fullSweep) {
        jdbcTemplate.update("INSERT INTO gcmb.cash_drawer_remittance(id,account_id,branch_id,occurred_at,amount,reason,reference,recorded_by,idempotency_key,balance_before,full_sweep) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
            remittanceId, accountId, branchId, occurredAt, amount, reason, reference, recordedBy, idempotencyKey, balanceBefore, fullSweep);
    }

    public Map<String, Object> getRemittance(UUID remittanceId) {
        return jdbcTemplate.queryForMap("SELECT * FROM gcmb.cash_drawer_remittance WHERE id=?", remittanceId);
    }
}
