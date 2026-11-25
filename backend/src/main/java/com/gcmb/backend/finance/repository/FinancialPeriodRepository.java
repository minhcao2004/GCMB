package com.gcmb.backend.finance.repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
// Kỳ tài chính, điều kiện chốt kỳ (BR-99/100) và xử lý ngoại lệ SePay (UC-105, UC-143).
public class FinancialPeriodRepository {

    private final JdbcTemplate jdbcTemplate;

    public FinancialPeriodRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void ensurePeriod(LocalDate month) {
        jdbcTemplate.update("INSERT INTO gcmb.financial_period(\"month\",status) VALUES (?,'OPEN') ON CONFLICT(\"month\") DO NOTHING", month);
    }

    public String lockPeriodStatusForShare(LocalDate month) {
        return jdbcTemplate.queryForObject("SELECT status FROM gcmb.financial_period WHERE \"month\"=? FOR SHARE", String.class, month);
    }

    public UUID lockPeriodIdForUpdate(LocalDate month) {
        return jdbcTemplate.queryForObject("SELECT id FROM gcmb.financial_period WHERE \"month\"=? FOR UPDATE", UUID.class, month);
    }

    public String lockPeriodStatusById(UUID periodId) {
        return (String) jdbcTemplate.queryForMap("SELECT status FROM gcmb.financial_period WHERE id=? FOR UPDATE", periodId).get("status");
    }

    public List<String> findPeriodStatus(LocalDate month) {
        return jdbcTemplate.queryForList("SELECT status FROM gcmb.financial_period WHERE \"month\"=?", String.class, month);
    }

    public int closePeriod(UUID periodId, UUID closedBy, String closeNote) {
        return jdbcTemplate.update("UPDATE gcmb.financial_period SET status='CLOSED',closed_by=?,closed_at=CURRENT_TIMESTAMP,close_note=? WHERE id=? AND status='OPEN'",
            closedBy, closeNote, periodId);
    }

    public Map<String, Object> getPeriodView(UUID periodId) {
        return jdbcTemplate.queryForMap("""
            SELECT p.id,p."month"::text AS "month",p.status,p.closed_at "closedAt",u.username "closedBy",p.close_note "closeNote"
            FROM gcmb.financial_period p LEFT JOIN gcmb.user_account u ON u.id=p.closed_by WHERE p.id=?
            """, periodId);
    }

    public List<Map<String, Object>> findPeriodsWithActivity() {
        return jdbcTemplate.queryForList("""
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
                FROM gcmb.bank_transaction WHERE provider='SEPAY' AND match_status IN ('EXCEPTION','UNMATCHED')
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

    // ---- Đếm công việc còn tồn để chốt kỳ (BR-99) ----

    public long countUnreconciledClosings(LocalDate start, LocalDate end) {
        return count("SELECT count(*) FROM gcmb.daily_cash_closing WHERE business_date>=? AND business_date<? AND status<>'RECONCILED'", start, end);
    }

    public long countCashActivityWithoutClosing(LocalDate start, LocalDate end) {
        return count("""
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
    }

    public long countBranchesWithOpenAttendance(LocalDate month) {
        return count("""
            SELECT count(*) FROM gcmb.branch b WHERE b.status='ACTIVE' AND NOT EXISTS (
              SELECT 1 FROM gcmb.attendance_period a WHERE a.branch_id=b.id AND a.month=? AND a.status='CLOSED'
            )
            """, month);
    }

    public long countUnconfirmedPayroll(LocalDate month) {
        return count("SELECT CASE WHEN EXISTS(SELECT 1 FROM gcmb.payroll_period WHERE month=? AND status='CONFIRMED') THEN 0 ELSE 1 END", month);
    }

    public long countPendingExpenseRequests(LocalDate start, LocalDate end) {
        return count("""
            SELECT count(*) FROM gcmb.finance_expense_request WHERE expense_on>=? AND expense_on<?
              AND status IN ('WAITING_ACCOUNTANT','WAITING_HEAD_OFFICE','APPROVED_TO_PAY')
            """, start, end);
    }

    public long countPendingRefunds(OffsetDateTime from, OffsetDateTime to) {
        return count("""
            SELECT count(*) FROM gcmb.refund_request WHERE created_at>=? AND created_at<?
              AND status IN ('REQUESTED','APPROVED','PROCESSING')
            """, from, to);
    }

    public long countUnresolvedSePayTransactionsBefore(OffsetDateTime before) {
        return count("""
            SELECT count(*) FROM gcmb.bank_transaction WHERE provider='SEPAY' AND match_status IN ('EXCEPTION','UNMATCHED')
              AND resolution_status='UNRESOLVED'
              AND occurred_at<?
            """, before);
    }

    // ---- Ngoại lệ SePay ----

    public List<Map<String, Object>> lockSePayTransaction(UUID transactionId) {
        return jdbcTemplate.queryForList("""
            SELECT match_status,resolution_status FROM gcmb.bank_transaction
            WHERE id=? AND provider='SEPAY' FOR UPDATE
            """, transactionId);
    }

    public void resolveSePayTransaction(UUID transactionId, UUID resolvedBy, String resolutionNote) {
        jdbcTemplate.update("""
            UPDATE gcmb.bank_transaction SET resolution_status='RESOLVED',resolved_at=CURRENT_TIMESTAMP,
              resolved_by=?,resolution_note=? WHERE id=? AND resolution_status='UNRESOLVED'
            """, resolvedBy, resolutionNote, transactionId);
    }

    public Map<String, Object> getSePayTransactionView(UUID transactionId) {
        return jdbcTemplate.queryForMap("""
            SELECT bt.id,bt.external_id AS "externalId",bt.match_status AS status,bt.exception_reason AS "exceptionReason",
              bt.resolution_status AS "resolutionStatus",bt.resolved_at AS "resolvedAt",u.username AS "resolvedBy",
              bt.resolution_note AS "resolutionNote"
            FROM gcmb.bank_transaction bt LEFT JOIN gcmb.user_account u ON u.id=bt.resolved_by WHERE bt.id=?
            """, transactionId);
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
}
