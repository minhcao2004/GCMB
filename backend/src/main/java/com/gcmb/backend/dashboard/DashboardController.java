package com.gcmb.backend.dashboard;

import com.gcmb.backend.auth.dto.Identity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class DashboardController {
    private final JdbcTemplate db;
    public DashboardController(JdbcTemplate db) { this.db = db; }

    @GetMapping("/api/v1/head-office/dashboard")
    @Transactional(readOnly = true)
    public Map<String, Object> chain(@AuthenticationPrincipal Identity actor, @RequestParam String month, @RequestParam(required = false) UUID branchId) {
        requireRole(actor, "HEAD_OFFICE_MANAGER");
        LocalDate start = month(month);
        if (branchId != null && !Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM gcmb.branch WHERE id=? AND status='ACTIVE')", Boolean.class, branchId)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cơ sở đang chọn không hoạt động hoặc không tồn tại.");
        List<Map<String, Object>> options = db.queryForList("SELECT id, name, code FROM gcmb.branch WHERE status='ACTIVE' ORDER BY name, id");
        List<Map<String, Object>> rows = results(start, branchId);
        Map<String, Object> previousTotals = totals(results(start.minusMonths(1), branchId));
        return Map.of("month", month, "branches", options, "results", rows, "totals", totals(rows),
            "previousTotals", previousTotals,
            "dailyRevenue", dailyRevenue(start, branchId), "revenueMix", revenueMix(start, branchId),
            "occupancyByBranch", occupancy(branchId));
    }

    @GetMapping("/api/v1/branch-management/dashboard")
    @Transactional(readOnly = true)
    public Map<String, Object> branch(@AuthenticationPrincipal Identity actor, @RequestParam String month) {
        requireRole(actor, "BRANCH_MANAGER");
        // Phạm vi được kiểm tra lại tại máy chủ, kể cả khi không có header chọn cơ sở.
        if (actor.branchId() == null || !Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM gcmb.branch_manager_assignment WHERE user_id=? AND branch_id=?)", Boolean.class, actor.id(), actor.branchId())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem đối với cơ sở này.");
        LocalDate start = month(month);
        List<Map<String, Object>> rows = results(start, actor.branchId());
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy cơ sở.");
        Map<String, Object> operational = db.queryForMap("""
            SELECT count(*) FILTER (WHERE r.operating_status <> 'INACTIVE') AS "roomCount",
                   count(*) FILTER (WHERE r.operating_status NOT IN ('INACTIVE','UNDER_MAINTENANCE') AND EXISTS (SELECT 1 FROM gcmb.room_allocation a WHERE a.room_id=r.id AND a.state='OCCUPIED')) AS "occupiedRooms",
                   count(*) FILTER (WHERE r.operating_status NOT IN ('INACTIVE','UNDER_MAINTENANCE')
                     AND NOT EXISTS (SELECT 1 FROM gcmb.room_allocation a WHERE a.room_id=r.id AND a.state='OCCUPIED')
                     AND EXISTS (SELECT 1 FROM gcmb.room_allocation a WHERE a.room_id=r.id AND a.state='RESERVED')) AS "bookedRooms",
                   count(*) FILTER (WHERE r.operating_status='UNDER_MAINTENANCE') AS "maintenanceRooms"
            FROM gcmb.room r WHERE r.branch_id=?
            """, actor.branchId());
        operational.put("openIncidents", db.queryForObject("SELECT count(*) FROM gcmb.asset_incident WHERE branch_id=? AND status NOT IN ('VERIFIED','CLOSED')", Long.class, actor.branchId()));
        operational.put("lowStockItems", db.queryForObject("""
            SELECT count(*) FROM gcmb.inventory_item i JOIN gcmb.stock_balance s ON s.inventory_item_id=i.id
            WHERE i.branch_id=? AND i.active AND s.minimum_quantity>0 AND s.quantity<s.minimum_quantity
            """, Long.class, actor.branchId()));
        operational.put("staffPresent", db.queryForObject("""
            SELECT count(DISTINCT a.employee_id) FROM gcmb.shift_assignment a JOIN gcmb.scheduled_shift s ON s.id=a.shift_id
            JOIN gcmb.attendance t ON t.assignment_id=a.id
            WHERE s.branch_id=? AND s.work_date=(CURRENT_TIMESTAMP AT TIME ZONE (SELECT timezone FROM gcmb.branch WHERE id=?))::date
              AND a.status='CONFIRMED' AND s.status<>'CANCELLED' AND t.actual_in IS NOT NULL
            """, Long.class, actor.branchId(), actor.branchId()));
        operational.put("staffTotal", db.queryForObject("""
            SELECT count(DISTINCT a.employee_id) FROM gcmb.shift_assignment a JOIN gcmb.scheduled_shift s ON s.id=a.shift_id
            WHERE s.branch_id=? AND s.work_date=(CURRENT_TIMESTAMP AT TIME ZONE (SELECT timezone FROM gcmb.branch WHERE id=?))::date
              AND a.status='CONFIRMED' AND s.status<>'CANCELLED'
            """, Long.class, actor.branchId(), actor.branchId()));
        String timezone = db.queryForObject("SELECT timezone FROM gcmb.branch WHERE id=?", String.class, actor.branchId());
        LocalDate today = LocalDate.now(java.time.ZoneId.of(timezone));
        return Map.ofEntries(
            Map.entry("month", month),
            Map.entry("result", rows.get(0)),
            Map.entry("operations", operational),
            Map.entry("dailyRevenue", dailyRevenue(start, actor.branchId())),
            Map.entry("revenueMix", revenueMix(start, actor.branchId())),
            Map.entry("hourlyOccupancy", hourlyOccupancy(start, actor.branchId())),
            Map.entry("roomTypePerformance", roomTypePerformance(start, actor.branchId())),
            Map.entry("topItems", topItems(start, actor.branchId())),
            Map.entry("lowStockItems", lowStock(actor.branchId())),
            Map.entry("openIncidents", openIncidents(actor.branchId())),
            Map.entry("todayShifts", todayShifts(actor.branchId(), today)));
    }

    private List<Map<String, Object>> dailyRevenue(LocalDate start, UUID branchId) {
        return db.queryForList("""
            SELECT (b.completed_at AT TIME ZONE br.timezone)::date AS date,
                COALESCE(SUM(bl.amount) FILTER (WHERE bl.order_line_id IS NULL),0) AS "roomRevenue",
                COALESCE(SUM(bl.amount) FILTER (WHERE bl.order_line_id IS NOT NULL),0) AS "fnbRevenue",
                COALESCE(SUM(bl.amount),0) AS revenue
            FROM gcmb.bill b JOIN gcmb.branch br ON br.id=b.branch_id
            JOIN gcmb.bill_line bl ON bl.bill_id=b.id
            WHERE b.status='SETTLED' AND (b.completed_at AT TIME ZONE br.timezone)::date >= ?
              AND (b.completed_at AT TIME ZONE br.timezone)::date < ?
              AND (CAST(? AS uuid) IS NULL OR b.branch_id=?)
            GROUP BY (b.completed_at AT TIME ZONE br.timezone)::date ORDER BY date
            """, start, start.plusMonths(1), branchId, branchId);
    }

    private List<Map<String, Object>> revenueMix(LocalDate start, UUID branchId) {
        return db.queryForList("""
            WITH bill_methods AS (
              SELECT b.id AS bill_id,
                CASE WHEN COUNT(DISTINCT p.method)>1 THEN 'Kết hợp'
                     WHEN BOOL_OR(p.method='BANK_TRANSFER') THEN 'SePay' ELSE 'Tiền mặt' END AS method,
                b.net_amount
              FROM gcmb.bill b JOIN gcmb.branch br ON br.id=b.branch_id
              JOIN gcmb.payment p ON p.bill_id=b.id AND p.purpose='BILL'
              WHERE b.status='SETTLED' AND (b.completed_at AT TIME ZONE br.timezone)::date >= ?
                AND (b.completed_at AT TIME ZONE br.timezone)::date < ?
                AND (CAST(? AS uuid) IS NULL OR b.branch_id=?)
              GROUP BY b.id,b.net_amount
            ) SELECT method AS label, SUM(net_amount) AS value FROM bill_methods GROUP BY method ORDER BY method
            """, start, start.plusMonths(1), branchId, branchId);
    }

    private List<Map<String, Object>> occupancy(UUID branchId) {
        return db.queryForList("""
            SELECT b.id,b.name,COUNT(r.id) FILTER (WHERE r.operating_status NOT IN ('INACTIVE','UNDER_MAINTENANCE')) AS total,
              COUNT(r.id) FILTER (WHERE r.operating_status NOT IN ('INACTIVE','UNDER_MAINTENANCE') AND EXISTS (
                SELECT 1 FROM gcmb.room_allocation a WHERE a.room_id=r.id AND a.state='OCCUPIED'
              )) AS occupied
            FROM gcmb.branch b LEFT JOIN gcmb.room r ON r.branch_id=b.id
            WHERE b.status='ACTIVE' AND (CAST(? AS uuid) IS NULL OR b.id=?)
            GROUP BY b.id,b.name ORDER BY b.name
            """, branchId, branchId);
    }

    private List<Map<String, Object>> roomTypePerformance(LocalDate start, UUID branchId) {
        return db.queryForList("""
            SELECT rt.name,COUNT(DISTINCT a.id) AS count,COALESCE(SUM(bl.amount),0) AS revenue,
              COALESCE(AVG(EXTRACT(EPOCH FROM (COALESCE(a.actual_end,a.ends_at)-COALESCE(a.actual_start,a.starts_at)))/60),0) AS minutes
            FROM gcmb.room_type rt JOIN gcmb.room r ON r.room_type_id=rt.id
            JOIN gcmb.room_allocation a ON a.room_id=r.id
            JOIN gcmb.branch br ON br.id=r.branch_id
            LEFT JOIN gcmb.bill b ON b.booking_id=a.booking_id AND b.status='SETTLED'
              AND (b.completed_at AT TIME ZONE br.timezone)::date >= ?
              AND (b.completed_at AT TIME ZONE br.timezone)::date < ?
            LEFT JOIN gcmb.bill_line bl ON bl.bill_id=b.id AND bl.order_line_id IS NULL
              AND EXISTS (SELECT 1 FROM gcmb.booking_charge bc WHERE bc.id=bl.booking_charge_id AND bc.allocation_id=a.id)
            WHERE r.branch_id=? AND a.state='FINISHED'
              AND (COALESCE(a.actual_end,a.ends_at) AT TIME ZONE br.timezone)::date >= ?
              AND (COALESCE(a.actual_end,a.ends_at) AT TIME ZONE br.timezone)::date < ?
            GROUP BY rt.id,rt.name ORDER BY revenue DESC,rt.name
            """, start, start.plusMonths(1), branchId, start, start.plusMonths(1));
    }

    private List<Map<String, Object>> hourlyOccupancy(LocalDate start, UUID branchId) {
        return db.queryForList("""
            SELECT EXTRACT(ISODOW FROM local_day)::int AS weekday, hour_slot AS hour,
              COUNT(*) AS occupied_room_hours
            FROM (
              SELECT d.local_day, h.hour_slot
              FROM generate_series(?::date, (?::date - 1), interval '1 day') d(local_day)
              CROSS JOIN generate_series(8,19) h(hour_slot)
            ) slots
            JOIN gcmb.branch br ON br.id=?
            JOIN gcmb.room_allocation a ON a.branch_id=br.id
              AND a.state IN ('OCCUPIED','FINISHED')
              AND a.starts_at < ((slots.local_day + slots.hour_slot * interval '1 hour' + interval '1 hour') AT TIME ZONE br.timezone)
              AND COALESCE(a.actual_end,a.ends_at) > ((slots.local_day + slots.hour_slot * interval '1 hour') AT TIME ZONE br.timezone)
            GROUP BY weekday,hour_slot ORDER BY weekday,hour_slot
            """, start, start.plusMonths(1), branchId);
    }

    private List<Map<String, Object>> openIncidents(UUID branchId) {
        return db.queryForList("""
            SELECT i.id,i.asset_id AS "assetId",a.code AS "assetCode",a.name AS "assetName",
              COALESCE(r.code,r.name,a.location_note,'—') AS location,i.status,i.created_at AS "createdAt"
            FROM gcmb.asset_incident i JOIN gcmb.asset a ON a.id=i.asset_id
            LEFT JOIN gcmb.room r ON r.id=a.room_id
            WHERE i.branch_id=? AND i.status NOT IN ('VERIFIED','CLOSED')
            ORDER BY i.created_at DESC LIMIT 30
            """, branchId);
    }

    private List<Map<String, Object>> todayShifts(UUID branchId, LocalDate today) {
        return db.queryForList("""
            SELECT s.work_date AS date,st.code AS shift,e.full_name AS employee,
              CASE WHEN att.id IS NULL THEN 'CHƯA CHẤM CÔNG'
                   WHEN att.status='ABSENT' THEN 'VẮNG'
                   WHEN att.actual_in IS NULL THEN 'CHƯA VÀO CA'
                   WHEN att.actual_out IS NULL THEN 'ĐANG LÀM'
                   ELSE 'ĐÃ CHẤM CÔNG' END AS attendance,
              s.starts_at AS "startsAt",s.ends_at AS "endsAt"
            FROM gcmb.scheduled_shift s JOIN gcmb.shift_template st ON st.id=s.template_id
            JOIN gcmb.shift_assignment sa ON sa.shift_id=s.id AND sa.status='CONFIRMED'
            JOIN gcmb.employee e ON e.id=sa.employee_id
            LEFT JOIN gcmb.attendance att ON att.assignment_id=sa.id
            WHERE s.branch_id=? AND s.work_date>=? AND s.work_date<? AND s.status<>'CANCELLED'
            ORDER BY s.work_date,s.starts_at,e.full_name LIMIT 100
            """, branchId, today, today.plusDays(2));
    }

    private List<Map<String, Object>> topItems(LocalDate start, UUID branchId) {
        return db.queryForList("""
            SELECT ol.name_snapshot AS name,COALESCE(SUM(ol.quantity),0) AS quantity,
              COALESCE(SUM(bl.amount),0) AS revenue
            FROM gcmb.bill b JOIN gcmb.branch br ON br.id=b.branch_id
            JOIN gcmb.bill_line bl ON bl.bill_id=b.id
            JOIN gcmb.order_line ol ON ol.id=bl.order_line_id
            WHERE b.status='SETTLED' AND (b.completed_at AT TIME ZONE br.timezone)::date >= ?
              AND (b.completed_at AT TIME ZONE br.timezone)::date < ? AND b.branch_id=?
            GROUP BY ol.name_snapshot ORDER BY quantity DESC,revenue DESC LIMIT 10
            """, start, start.plusMonths(1), branchId);
    }

    private List<Map<String, Object>> lowStock(UUID branchId) {
        return db.queryForList("""
            SELECT i.name,b.quantity,b.minimum_quantity AS minimum,u.name AS unit
            FROM gcmb.inventory_item i JOIN gcmb.stock_balance b ON b.inventory_item_id=i.id
            JOIN gcmb.unit u ON u.id=i.unit_id
            WHERE i.branch_id=? AND i.active AND b.minimum_quantity>0 AND b.quantity<b.minimum_quantity
            ORDER BY b.quantity/b.minimum_quantity,i.name LIMIT 20
            """, branchId);
    }

    private static void requireRole(Identity actor, String role) {
        if (actor == null || !role.equals(actor.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem đối với dashboard.");
    }
    private static LocalDate month(String value) {
        try { if (!value.matches("\\d{4}-\\d{2}")) throw new DateTimeParseException("invalid", value, 0); return YearMonth.parse(value).atDay(1); }
        catch (DateTimeParseException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tháng báo cáo không hợp lệ."); }
    }

    private List<Map<String, Object>> results(LocalDate start, UUID selected) {
        // BR-102, BR-211: bill hoàn tất, theo thời điểm hoàn tất tại múi giờ của cơ sở; tiền cọc chưa dùng không phải doanh thu.
        // BR-103, BR-107: cộng OPEX, lương và ứng lương đã trả; tách CAPEX, loại chuyển quỹ khỏi chi phí vận hành.
        // BR-104, BR-105: lợi nhuận trừ chi vận hành và lương; biên chỉ tính khi thu > 0.
        // BR-106: dòng tiền thuần lấy tổng bút toán thu vào trừ bút toán thu ra trong sổ tiền.
        // BR-108: cọc tịch thu được báo cáo riêng, không nhập vào doanh thu dịch vụ.
        return db.queryForList("""
            SELECT b.id, b.code, b.name, b.address, b.status, b.timezone,
                COALESCE(revenue.amount,0) AS revenue, COALESCE(revenue.bills,0) AS "billCount",
                COALESCE(cost.opex,0)+COALESCE(salary.amount,0) AS expenses, COALESCE(salary.amount,0) AS salary,
                COALESCE(cost.capex,0) AS investment, COALESCE(cost.payments,0)+COALESCE(salary.payments,0) AS "expenseCount",
                COALESCE(forfeiture.amount,0) AS "forfeitedDeposits",
                COALESCE(cash.net,0) AS "netCashFlow",
                COALESCE(revenue.amount,0)-COALESCE(cost.opex,0)-COALESCE(salary.amount,0) AS profit,
                CASE WHEN COALESCE(revenue.amount,0)>0 THEN (revenue.amount-COALESCE(cost.opex,0)-COALESCE(salary.amount,0))*100/revenue.amount ELSE NULL END AS margin
            FROM gcmb.branch b
            LEFT JOIN LATERAL (SELECT sum(x.net_amount) amount, count(*) bills FROM gcmb.bill x
                WHERE x.branch_id=b.id AND x.status='SETTLED' AND (x.completed_at AT TIME ZONE b.timezone)::date>=? AND (x.completed_at AT TIME ZONE b.timezone)::date<?) revenue ON true
            LEFT JOIN LATERAL (SELECT sum(p.amount) FILTER (WHERE e.class_snapshot='OPEX') opex, sum(p.amount) FILTER (WHERE e.class_snapshot='CAPEX') capex, count(*) payments
                FROM gcmb.expense_payment p JOIN gcmb.expense e ON e.id=p.expense_id
                WHERE e.branch_id=b.id AND p.status='PAID' AND (p.paid_at AT TIME ZONE b.timezone)::date>=? AND (p.paid_at AT TIME ZONE b.timezone)::date<?) cost ON true
            LEFT JOIN LATERAL (SELECT sum(-fe.amount_signed) amount,count(*) payments
                FROM gcmb.financial_transaction ft JOIN gcmb.financial_entry fe ON fe.transaction_id=ft.id
                WHERE ft.kind IN ('SALARY','ADVANCE') AND fe.branch_id=b.id AND fe.amount_signed<0
                  AND (ft.occurred_at AT TIME ZONE b.timezone)::date>=? AND (ft.occurred_at AT TIME ZONE b.timezone)::date<?) salary ON true
            -- BR-108: tách tiền cọc tịch thu khỏi doanh thu bill và dòng tiền nhận cọc.
            LEFT JOIN LATERAL (SELECT sum(de.amount) amount FROM gcmb.deposit_entry de
                JOIN gcmb.deposit d ON d.id=de.deposit_id JOIN gcmb.booking bk ON bk.id=d.booking_id
                WHERE bk.branch_id=b.id AND de.kind='FORFEIT'
                  AND (de.created_at AT TIME ZONE b.timezone)::date>=? AND (de.created_at AT TIME ZONE b.timezone)::date<?) forfeiture ON true
            LEFT JOIN LATERAL (SELECT sum(fe.amount_signed) net FROM gcmb.financial_entry fe
                JOIN gcmb.financial_transaction ft ON ft.id=fe.transaction_id
                WHERE fe.branch_id=b.id AND (ft.occurred_at AT TIME ZONE b.timezone)::date>=? AND (ft.occurred_at AT TIME ZONE b.timezone)::date<?) cash ON true
            WHERE (?::uuid IS NULL OR b.id=?::uuid) ORDER BY b.name,b.id
            """, start, start.plusMonths(1), start, start.plusMonths(1), start, start.plusMonths(1), start, start.plusMonths(1), start, start.plusMonths(1), selected, selected);
    }
    private Map<String, Object> totals(List<Map<String, Object>> rows) {
        // BR-109: lợi nhuận chuỗi bằng tổng lợi nhuận các cơ sở trong kỳ.
        BigDecimal revenue=BigDecimal.ZERO, expenses=BigDecimal.ZERO, salary=BigDecimal.ZERO, investment=BigDecimal.ZERO, forfeitedDeposits=BigDecimal.ZERO, profit=BigDecimal.ZERO, cash=BigDecimal.ZERO;
        long bills=0, payments=0, loss=0;
        for (Map<String,Object> row : rows) {
            revenue=revenue.add((BigDecimal)row.get("revenue")); expenses=expenses.add((BigDecimal)row.get("expenses"));
            investment=investment.add((BigDecimal)row.get("investment")); profit=profit.add((BigDecimal)row.get("profit"));
            cash=cash.add((BigDecimal)row.get("netCashFlow"));
            salary=salary.add((BigDecimal)row.get("salary"));
            forfeitedDeposits=forfeitedDeposits.add((BigDecimal)row.get("forfeitedDeposits"));
            bills+=((Number)row.get("billCount")).longValue(); payments+=((Number)row.get("expenseCount")).longValue(); if (((BigDecimal)row.get("profit")).signum()<0) loss++;
        }
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("revenue",revenue); result.put("expenses",expenses); result.put("salary",salary); result.put("investment",investment); result.put("forfeitedDeposits",forfeitedDeposits); result.put("profit",profit); result.put("netCashFlow",cash);
        result.put("billCount",bills); result.put("expenseCount",payments); result.put("lossBranches",loss); result.put("branchCount",rows.size());
        return result;
    }
}
