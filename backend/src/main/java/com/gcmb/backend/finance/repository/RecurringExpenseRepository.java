package com.gcmb.backend.finance.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
// Khoản chi định kỳ, các kỳ chi phát sinh và thông báo nhắc hạn cho kế toán.
public class RecurringExpenseRepository {

    private static final String TEMPLATE_COLUMNS = """
        id,branch_id,category_id,beneficiary_name,bank_account,bank_name,due_day,monthly_amount,expected_amount,
          expense_belongs_to,start_month,end_month,reminder_days
        """;

    private final JdbcTemplate jdbcTemplate;

    public RecurringExpenseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> findTemplates(UUID branchId) {
        return jdbcTemplate.queryForList("""
            SELECT r.id,r.branch_id,b.name AS branch_name,r.category_id,c.code AS category_code,c.name AS category_name,
              c.invoice_required,r.beneficiary_name,r.bank_account,r.bank_name,r.due_day,r.monthly_amount,r.expected_amount,
              r.expense_belongs_to,r.start_month,r.end_month,r.reminder_days,r.status,r.created_at,r.updated_at,r.terminated_at,
              (SELECT min(p.due_date) FROM gcmb.finance_recurring_expense_period p
                WHERE p.recurring_id=r.id AND p.status='PENDING') AS next_pending_due_date
            FROM gcmb.finance_recurring_expense r
            JOIN gcmb.branch b ON b.id=r.branch_id
            JOIN gcmb.finance_expense_category c ON c.id=r.category_id
            WHERE (?::uuid IS NULL OR r.branch_id=?)
            ORDER BY CASE r.status WHEN 'ACTIVE' THEN 0 ELSE 1 END,r.due_day,c.name,b.name,r.beneficiary_name
            """, branchId, branchId);
    }

    public List<Map<String, Object>> findPeriods(UUID branchId, LocalDate dueStart, LocalDate dueEnd, String status) {
        return jdbcTemplate.queryForList("""
            SELECT p.id,p.recurring_id,p.period_month,p.due_date,p.branch_id,b.name AS branch_name,
              p.category_id,c.code AS category_code,c.name AS category_name,c.invoice_required,
              p.beneficiary_name,p.bank_account,p.bank_name,p.amount,p.default_amount,p.expected_amount,
              COALESCE(p.amount,p.expected_amount,(
                SELECT previous.amount FROM gcmb.finance_recurring_expense_period previous
                WHERE previous.recurring_id=p.recurring_id AND previous.status='PAID'
                  AND previous.period_month<p.period_month
                ORDER BY previous.period_month DESC LIMIT 1
              )) AS suggested_amount,
              p.reminder_days,p.status,p.request_id,rpay.code AS request_code,p.skip_reason,p.created_at,p.updated_at,
              r.monthly_amount AS current_default_amount,r.status AS recurring_status
            FROM gcmb.finance_recurring_expense_period p
            JOIN gcmb.finance_recurring_expense r ON r.id=p.recurring_id
            JOIN gcmb.branch b ON b.id=p.branch_id
            JOIN gcmb.finance_expense_category c ON c.id=p.category_id
            LEFT JOIN gcmb.finance_expense_request rpay ON rpay.id=p.request_id
            WHERE (?::uuid IS NULL OR p.branch_id=?) AND p.due_date>=? AND p.due_date<?
              AND (?='ALL' OR p.status=?)
            ORDER BY CASE p.status WHEN 'PENDING' THEN 0 WHEN 'SKIPPED' THEN 1 ELSE 2 END,p.due_date,p.period_month,p.id
            """, branchId, branchId, dueStart, dueEnd, status, status);
    }

    public boolean lockActiveBranch(UUID branchId) {
        return !jdbcTemplate.queryForList("SELECT id FROM gcmb.branch WHERE id=? AND status='ACTIVE' FOR SHARE",
            UUID.class, branchId).isEmpty();
    }

    public boolean lockActiveCategory(UUID categoryId) {
        return !jdbcTemplate.queryForList("SELECT id FROM gcmb.finance_expense_category WHERE id=? AND active=true FOR SHARE",
            UUID.class, categoryId).isEmpty();
    }

    public long countActiveDuplicates(UUID branchId, UUID categoryId, String beneficiary, UUID excludedId) {
        Long duplicates = jdbcTemplate.queryForObject("""
            SELECT count(*) FROM gcmb.finance_recurring_expense
            WHERE status='ACTIVE' AND branch_id=? AND category_id=? AND lower(btrim(beneficiary_name))=lower(btrim(?))
              AND (?::uuid IS NULL OR id<>?)
            """, Long.class, branchId, categoryId, beneficiary, excludedId, excludedId);
        return duplicates == null ? 0 : duplicates;
    }

    public void insertTemplate(UUID id, UUID branchId, UUID categoryId, String beneficiary, String account, String bank,
            int dueDay, BigDecimal monthlyAmount, BigDecimal expectedAmount, String belongsTo, LocalDate startMonth,
            LocalDate endMonth, int reminderDays, UUID actorId) {
        jdbcTemplate.update("""
            INSERT INTO gcmb.finance_recurring_expense(id,branch_id,category_id,beneficiary_name,bank_account,bank_name,
              due_day,monthly_amount,expected_amount,expense_belongs_to,start_month,end_month,reminder_days,status,created_by,updated_by)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,'ACTIVE',?,?)
            """, id, branchId, categoryId, beneficiary, account, bank, dueDay, monthlyAmount,
            expectedAmount, belongsTo, startMonth, endMonth, reminderDays, actorId, actorId);
    }

    public Optional<String> lockTemplateStatus(UUID id) {
        return jdbcTemplate.query("SELECT status FROM gcmb.finance_recurring_expense WHERE id=? FOR UPDATE",
            (resultSet, rowNumber) -> resultSet.getString(1), id).stream().findFirst();
    }

    public void updateActiveTemplate(UUID id, UUID branchId, UUID categoryId, String beneficiary, String account,
            String bank, int dueDay, BigDecimal monthlyAmount, BigDecimal expectedAmount, String belongsTo,
            LocalDate startMonth, LocalDate endMonth, int reminderDays, UUID actorId) {
        jdbcTemplate.update("""
            UPDATE gcmb.finance_recurring_expense SET branch_id=?,category_id=?,beneficiary_name=?,bank_account=?,bank_name=?,
              due_day=?,monthly_amount=?,expected_amount=?,expense_belongs_to=?,start_month=?,end_month=?,reminder_days=?,
              updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='ACTIVE'
            """, branchId, categoryId, beneficiary, account, bank, dueDay, monthlyAmount, expectedAmount,
            belongsTo, startMonth, endMonth, reminderDays, actorId, id);
    }

    public int terminateTemplate(UUID id, UUID actorId) {
        return jdbcTemplate.update("""
            UPDATE gcmb.finance_recurring_expense SET status='TERMINATED',terminated_at=CURRENT_TIMESTAMP,
              updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='ACTIVE'
            """, actorId, id);
    }

    public boolean templateExists(UUID id) {
        return !jdbcTemplate.queryForList("SELECT id FROM gcmb.finance_recurring_expense WHERE id=?", UUID.class, id).isEmpty();
    }

    public Optional<String> lockPeriodStatus(UUID id) {
        return jdbcTemplate.query("SELECT status FROM gcmb.finance_recurring_expense_period WHERE id=? FOR UPDATE",
            (resultSet, rowNumber) -> resultSet.getString(1), id).stream().findFirst();
    }

    public void skipPendingPeriod(UUID id, String reason) {
        jdbcTemplate.update("UPDATE gcmb.finance_recurring_expense_period SET status='SKIPPED',skip_reason=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='PENDING'",
            reason, id);
    }

    public Optional<Map<String, Object>> lockPeriodForPayment(UUID id) {
        return jdbcTemplate.queryForList("""
            SELECT p.*,r.status AS recurring_status,c.name AS category_name
            FROM gcmb.finance_recurring_expense_period p
            JOIN gcmb.finance_recurring_expense r ON r.id=p.recurring_id
            JOIN gcmb.finance_expense_category c ON c.id=p.category_id
            WHERE p.id=? FOR UPDATE OF p
            """, id).stream().findFirst();
    }

    public int markPeriodPaid(UUID id, BigDecimal amount, Object requestId) {
        return jdbcTemplate.update("""
            UPDATE gcmb.finance_recurring_expense_period
            SET amount=?,status='PAID',request_id=?,updated_at=CURRENT_TIMESTAMP
            WHERE id=? AND status='PENDING'
            """, amount, requestId, id);
    }

    public List<Map<String, Object>> findActiveTemplates() {
        return jdbcTemplate.queryForList("SELECT " + TEMPLATE_COLUMNS
            + " FROM gcmb.finance_recurring_expense WHERE status='ACTIVE' ORDER BY id");
    }

    public Optional<Map<String, Object>> lockActiveTemplate(UUID id) {
        return jdbcTemplate.queryForList("SELECT " + TEMPLATE_COLUMNS
            + " FROM gcmb.finance_recurring_expense WHERE id=? AND status='ACTIVE' FOR UPDATE", id).stream().findFirst();
    }

    public List<UUID> findActiveAccountantIds() {
        return jdbcTemplate.query("""
            SELECT u.id FROM gcmb.user_account u JOIN gcmb.role ro ON ro.id=u.role_id
            WHERE u.status='ACTIVE' AND ro.code='ACCOUNTANT' ORDER BY u.id
            """, (resultSet, rowNumber) -> resultSet.getObject(1, UUID.class));
    }

    public List<Map<String, Object>> findPendingPeriodsToRemind(LocalDate today) {
        return jdbcTemplate.queryForList("""
            SELECT p.id,p.due_date,p.period_month,p.amount,p.reminder_days,b.name AS branch_name,c.name AS category_name
            FROM gcmb.finance_recurring_expense_period p
            JOIN gcmb.branch b ON b.id=p.branch_id
            JOIN gcmb.finance_expense_category c ON c.id=p.category_id
            WHERE p.status='PENDING' AND p.due_date<=?::date+p.reminder_days
            ORDER BY p.due_date,p.id
            """, today);
    }

    public void insertPeriodIfAbsent(Map<String, Object> template, LocalDate periodMonth, LocalDate dueDate) {
        jdbcTemplate.update("""
            INSERT INTO gcmb.finance_recurring_expense_period(id,recurring_id,period_month,due_date,branch_id,category_id,
              beneficiary_name,bank_account,bank_name,amount,default_amount,expected_amount,reminder_days)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(recurring_id,period_month) DO NOTHING
            """, UUID.randomUUID(), template.get("id"), periodMonth, dueDate, template.get("branch_id"),
            template.get("category_id"), template.get("beneficiary_name"), template.get("bank_account"),
            template.get("bank_name"), template.get("monthly_amount"), template.get("monthly_amount"),
            template.get("expected_amount"), template.get("reminder_days"));
    }

    public List<Map<String, Object>> lockPendingPeriods(UUID recurringId) {
        return jdbcTemplate.queryForList("SELECT id,period_month FROM gcmb.finance_recurring_expense_period WHERE recurring_id=? AND status='PENDING' FOR UPDATE",
            recurringId);
    }

    public void updatePendingPeriodSnapshot(Object periodId, LocalDate dueDate, UUID branchId, UUID categoryId,
            String beneficiary, String account, String bank, BigDecimal monthlyAmount, BigDecimal expectedAmount,
            int reminderDays) {
        jdbcTemplate.update("""
            UPDATE gcmb.finance_recurring_expense_period SET due_date=?,branch_id=?,category_id=?,beneficiary_name=?,
              bank_account=?,bank_name=?,amount=?,default_amount=?,expected_amount=?,reminder_days=?,updated_at=CURRENT_TIMESTAMP
            WHERE id=? AND status='PENDING'
            """, dueDate, branchId, categoryId, beneficiary, account, bank, monthlyAmount, monthlyAmount,
            expectedAmount, reminderDays, periodId);
    }

    public void insertNoticeIfAbsent(Object periodId, UUID recipientId, String type, LocalDate date, String message) {
        jdbcTemplate.update("""
            INSERT INTO gcmb.finance_recurring_expense_notice(period_id,recipient_id,reminder_type,reminder_date,message)
            VALUES(?,?,?,?,?) ON CONFLICT(period_id,recipient_id,reminder_type,reminder_date) DO NOTHING
            """, periodId, recipientId, type, date, message);
    }

    public List<Map<String, Object>> findNotices(UUID recipientId) {
        return jdbcTemplate.queryForList("""
            SELECT n.id,n.period_id,n.reminder_type,n.reminder_date,n.message,n.created_at,
              p.period_month,p.due_date,p.status AS period_status,b.name AS branch_name,c.name AS category_name
            FROM gcmb.finance_recurring_expense_notice n
            JOIN gcmb.finance_recurring_expense_period p ON p.id=n.period_id
            JOIN gcmb.branch b ON b.id=p.branch_id JOIN gcmb.finance_expense_category c ON c.id=p.category_id
            WHERE n.recipient_id=? ORDER BY n.created_at DESC,n.id DESC LIMIT 30
            """, recipientId);
    }

    public int countPeriodsToRemind(LocalDate today) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM gcmb.finance_recurring_expense_period WHERE status='PENDING' AND due_date<=?::date+reminder_days",
            Integer.class, today);
    }

    public Map<String, Object> findTemplate(UUID id) {
        return jdbcTemplate.queryForMap("""
            SELECT r.*,b.name AS branch_name,c.code AS category_code,c.name AS category_name,c.invoice_required,
              (SELECT min(p.due_date) FROM gcmb.finance_recurring_expense_period p
                WHERE p.recurring_id=r.id AND p.status='PENDING') AS next_pending_due_date
            FROM gcmb.finance_recurring_expense r JOIN gcmb.branch b ON b.id=r.branch_id
            JOIN gcmb.finance_expense_category c ON c.id=r.category_id WHERE r.id=?
            """, id);
    }

    public Map<String, Object> findPeriod(UUID id) {
        return jdbcTemplate.queryForMap("""
            SELECT p.*,b.name AS branch_name,c.name AS category_name,c.invoice_required
            FROM gcmb.finance_recurring_expense_period p JOIN gcmb.branch b ON b.id=p.branch_id
            JOIN gcmb.finance_expense_category c ON c.id=p.category_id WHERE p.id=?
            """, id);
    }

    public void lockIdentity(String identityKey) {
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(1300261105,hashtext(?))",
            (ResultSet resultSet) -> { resultSet.next(); resultSet.getObject(1); return null; }, identityKey);
    }
}
