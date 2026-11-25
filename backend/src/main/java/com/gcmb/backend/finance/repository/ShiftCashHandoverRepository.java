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
// Bàn giao tiền mặt giữa các ca lễ tân: phiếu giao (dispatch), phiếu nhận (handover) và phân ca.
public class ShiftCashHandoverRepository {

    public static final String DISPATCH_TABLE = "shift_cash_dispatch";
    public static final String HANDOVER_TABLE = "shift_cash_handover";

    private final JdbcTemplate jdbcTemplate;

    public ShiftCashHandoverRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isActiveUserOfBranch(UUID userId, UUID branchId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM gcmb.user_account WHERE id=? AND branch_id=? AND status='ACTIVE')
            """, Boolean.class, userId, branchId));
    }

    public String findBranchStatus(UUID branchId, boolean lockForShare) {
        return jdbcTemplate.queryForObject("SELECT status FROM gcmb.branch WHERE id=?" + (lockForShare ? " FOR SHARE" : ""),
            String.class, branchId);
    }

    public String findBranchTimezone(UUID branchId) {
        return jdbcTemplate.queryForObject("SELECT timezone FROM gcmb.branch WHERE id=?", String.class, branchId);
    }

    public boolean isConfirmedReceptionistOfShift(UUID userId, UUID branchId, LocalDate workDate, Timestamp startsAt, Timestamp endsAt) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
            SELECT EXISTS(
              SELECT 1 FROM gcmb.shift_assignment a
              JOIN gcmb.scheduled_shift s ON s.id=a.shift_id
              JOIN gcmb.employee e ON e.id=a.employee_id
              WHERE e.user_id=? AND e.status='ACTIVE' AND e.position='RECEPTIONIST'
                AND a.status='CONFIRMED' AND s.status<>'CANCELLED'
                AND s.branch_id=? AND s.work_date=? AND s.starts_at=? AND s.ends_at=?
            )
            """, Boolean.class, userId, branchId, Date.valueOf(workDate), startsAt, endsAt));
    }

    public boolean wasAssignedAt(Object userId, UUID branchId, LocalDate workDate, Timestamp startsAt, Timestamp endsAt, Timestamp at) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM gcmb.shift_assignment a
              JOIN gcmb.scheduled_shift s ON s.id=a.shift_id JOIN gcmb.employee e ON e.id=a.employee_id
              WHERE e.user_id=? AND s.branch_id=? AND s.work_date=? AND s.starts_at=? AND s.ends_at=?
                AND a.created_at<=? AND (a.status='CONFIRMED' OR (a.status='CANCELLED' AND a.cancelled_at>?)))
            """, Boolean.class, userId, branchId, Date.valueOf(workDate), startsAt, endsAt, at, at));
    }

    /** Đọc phiếu giao hoặc phiếu nhận; {@code pendingOnly} lấy mọi phiếu còn chênh lệch thay vì theo ngày. */
    public List<Map<String, Object>> findRecords(UUID branchId, LocalDate businessDate, boolean pendingOnly, boolean dispatch) {
        String table = dispatch ? DISPATCH_TABLE : HANDOVER_TABLE;
        String userColumn = dispatch ? "handed_by" : "received_by";
        return jdbcTemplate.queryForList("SELECT h.*,coalesce(e.full_name,u.username) AS actor_name, "
            + "coalesce(me.full_name,m.username) AS resolver FROM gcmb." + table + " h "
            + "JOIN gcmb.user_account u ON u.id=h." + userColumn + " "
            + "LEFT JOIN gcmb.user_account m ON m.id=h.resolved_by "
            + "LEFT JOIN gcmb.employee e ON e.user_id=u.id "
            + "LEFT JOIN gcmb.employee me ON me.user_id=m.id WHERE h.branch_id=? AND "
            + (pendingOnly ? " h.status='DISCREPANCY' " : " h.business_date=? ") + " ORDER BY h.business_date,h.shift_number",
            pendingOnly ? new Object[]{branchId} : new Object[]{branchId, Date.valueOf(businessDate)});
    }

    public void lockShiftHandover() {
        jdbcTemplate.execute("SELECT pg_advisory_xact_lock(1300261013)");
    }

    public boolean handoverExists(UUID branchId, LocalDate businessDate, int shiftNumber) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM gcmb.shift_cash_handover WHERE branch_id=? AND business_date=? AND shift_number=?)
            """, Boolean.class, branchId, Date.valueOf(businessDate), shiftNumber));
    }

    public boolean isPeriodClosed(LocalDate month) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM gcmb.financial_period WHERE month=? AND status='CLOSED')
            """, Boolean.class, Date.valueOf(month)));
    }

    public List<Map<String, Object>> findDispatches(UUID branchId, LocalDate businessDate, int shiftNumber) {
        return jdbcTemplate.queryForList("SELECT * FROM gcmb.shift_cash_dispatch WHERE branch_id=? AND business_date=? AND shift_number=?",
            branchId, Date.valueOf(businessDate), shiftNumber);
    }

    public void insertRecord(boolean dispatch, UUID recordId, UUID branchId, LocalDate businessDate, int shiftNumber,
                             BigDecimal expectedAmount, BigDecimal actualAmount, String sourceRevision, String sourceSnapshot,
                             String note, String status, UUID actorId) {
        String table = dispatch ? DISPATCH_TABLE : HANDOVER_TABLE;
        jdbcTemplate.update("INSERT INTO gcmb." + table + "(id,branch_id,business_date,shift_number,expected_amount,"
            + "actual_amount,source_revision,source_snapshot,note,status," + (dispatch ? "handed_by" : "received_by") + ") "
            + "VALUES (?,?,?,?,?,?,?::uuid,?::jsonb,?,?,?)",
            recordId, branchId, Date.valueOf(businessDate), shiftNumber, expectedAmount, actualAmount, sourceRevision,
            sourceSnapshot, note, status, actorId);
    }

    public Map<String, Object> getRecord(String table, UUID recordId) {
        return jdbcTemplate.queryForMap("SELECT * FROM gcmb." + table + " WHERE id=?", recordId);
    }

    public int resolveDiscrepancy(String table, UUID recordId, UUID branchId, String resolution, UUID resolvedBy) {
        return jdbcTemplate.update("UPDATE gcmb." + table + " SET status='RESOLVED',resolution=?,resolved_by=?,resolved_at=clock_timestamp() "
            + "WHERE id=? AND branch_id=? AND status='DISCREPANCY'", resolution, resolvedBy, recordId, branchId);
    }
}
