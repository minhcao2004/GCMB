package com.gcmb.backend.finance.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
// Yêu cầu cấp tiền két của QLCS và thanh toán từ quỹ kế toán (UC-139, UC-140).
public class DrawerFundingRepository {

    private final JdbcTemplate jdbcTemplate;

    public DrawerFundingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public BigDecimal accountantFundBalance() {
        return AccountantFund.balance(jdbcTemplate);
    }

    public List<Map<String, Object>> findBranchOptions() {
        return jdbcTemplate.queryForList("SELECT id,name FROM gcmb.branch ORDER BY name");
    }

    public List<Map<String, Object>> findRequests(UUID branchId) {
        return jdbcTemplate.queryForList("""
            SELECT f.*,b.name AS "branchName",u.username AS "requestedBy",p.username AS "processedBy"
            FROM gcmb.cash_drawer_funding f JOIN gcmb.branch b ON b.id=f.branch_id
            JOIN gcmb.user_account u ON u.id=f.requested_by LEFT JOIN gcmb.user_account p ON p.id=f.processed_by
            WHERE (CAST(? AS uuid) IS NULL OR f.branch_id=?) ORDER BY f.requested_at DESC,f.id
            """, branchId, branchId);
    }

    public Map<String, Object> findRecipientOfUser(UUID userId) {
        return jdbcTemplate.queryForMap("""
            SELECT COALESCE(e.full_name,u.username) AS recipient_name,e.bank_name AS recipient_bank_name,
              e.bank_account AS recipient_bank_account,e.bank_holder AS recipient_bank_holder
            FROM gcmb.user_account u LEFT JOIN gcmb.employee e ON e.user_id=u.id WHERE u.id=?
            """, userId);
    }

    public void updateRecipient(UUID fundingId, Object name, Object bankName, Object bankAccount, Object bankHolder) {
        jdbcTemplate.update("""
            UPDATE gcmb.cash_drawer_funding SET recipient_name=?,recipient_bank_name=?,recipient_bank_account=?,recipient_bank_holder=?
            WHERE id=?
            """, name, bankName, bankAccount, bankHolder, fundingId);
    }

    public Map<String, Object> getFunding(UUID fundingId) {
        return jdbcTemplate.queryForMap("SELECT * FROM gcmb.cash_drawer_funding WHERE id=?", fundingId);
    }

    public List<Map<String, Object>> lockFunding(UUID fundingId) {
        return jdbcTemplate.queryForList("SELECT * FROM gcmb.cash_drawer_funding WHERE id=? FOR UPDATE", fundingId);
    }

    public List<Map<String, Object>> findByIdempotencyKey(UUID requestId) {
        return jdbcTemplate.queryForList("SELECT * FROM gcmb.cash_drawer_funding WHERE idempotency_key=?", requestId);
    }

    public boolean hasPendingRequest(UUID accountId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM gcmb.cash_drawer_funding WHERE account_id=? AND status='REQUESTED')",
            Boolean.class, accountId));
    }

    public void insertRequest(UUID fundingId, String code, UUID branchId, UUID accountId, BigDecimal amount, String reason,
                              UUID requestedBy, UUID idempotencyKey, Object recipientName, Object recipientBankName,
                              Object recipientBankAccount, Object recipientBankHolder) {
        jdbcTemplate.update("""
            INSERT INTO gcmb.cash_drawer_funding(id,code,branch_id,account_id,amount,reason,requested_by,idempotency_key,
              recipient_name,recipient_bank_name,recipient_bank_account,recipient_bank_holder)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
            """, fundingId, code, branchId, accountId, amount, reason, requestedBy, idempotencyKey, recipientName,
            recipientBankName, recipientBankAccount, recipientBankHolder);
    }

    public Map<String, Object> lockDrawerAccount(Object accountId) {
        return jdbcTemplate.queryForMap("""
            SELECT a.active,a.kind,a.branch_id,b.status,b.timezone FROM gcmb.financial_account a
            JOIN gcmb.branch b ON b.id=a.branch_id WHERE a.id=? FOR SHARE OF a,b
            """, accountId);
    }

    public boolean isPeriodClosed(LocalDate month) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM gcmb.financial_period WHERE month=? AND status='CLOSED')", Boolean.class, month));
    }

    public void markPaid(UUID fundingId, UUID processedBy, String reference, String note, String evidenceName,
                         String evidenceMimeType, String evidenceObjectKey) {
        jdbcTemplate.update("""
            UPDATE gcmb.cash_drawer_funding SET status='PAID',processed_by=?,processed_at=clock_timestamp(),
              payment_reference=?,resolution_note=?,evidence_original_name=?,evidence_mime_type=?,evidence_object_key=?
              WHERE id=? AND status='REQUESTED'
            """, processedBy, reference, note, evidenceName, evidenceMimeType, evidenceObjectKey, fundingId);
    }

    public void markRejected(UUID fundingId, UUID processedBy, String note) {
        jdbcTemplate.update("UPDATE gcmb.cash_drawer_funding SET status='REJECTED',processed_by=?,processed_at=clock_timestamp(),resolution_note=? WHERE id=?",
            processedBy, note, fundingId);
    }

    public List<Map<String, Object>> findEvidence(UUID fundingId, UUID branchId) {
        return jdbcTemplate.queryForList("""
            SELECT evidence_original_name AS original_name,evidence_mime_type AS mime_type,evidence_object_key AS object_key
            FROM gcmb.cash_drawer_funding WHERE id=? AND (CAST(? AS uuid) IS NULL OR branch_id=?)
              AND evidence_object_key IS NOT NULL
            """, fundingId, branchId, branchId);
    }
}
