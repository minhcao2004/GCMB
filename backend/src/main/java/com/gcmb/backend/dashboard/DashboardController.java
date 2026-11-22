package com.gcmb.backend.dashboard;

import com.gcmb.backend.auth.dto.Identity;
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
    private final MonthlyPerformanceService performance;
    public DashboardController(JdbcTemplate db, MonthlyPerformanceService performance) { this.db = db; this.performance = performance; }

    @GetMapping("/api/v1/head-office/dashboard")
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Map<String, Object> chain(@AuthenticationPrincipal Identity actor, @RequestParam String month, @RequestParam(required = false) UUID branchId) {
        requireRole(actor, "HEAD_OFFICE_MANAGER");
        LocalDate start = month(month);
        List<Map<String, Object>> options = db.queryForList("SELECT id, name, code FROM gcmb.branch WHERE status='ACTIVE' ORDER BY name, id");
        Map<String, Object> report = performance.report(actor,start,branchId);
        List<Map<String,Object>> rows = (List<Map<String,Object>>) report.get("results");
        return Map.ofEntries(Map.entry("month",month),Map.entry("branches",options),Map.entry("results",rows),
            Map.entry("totals",report.get("totals")),Map.entry("previousTotals",report.get("previousTotals")),
            Map.entry("changes",report.get("changes")),Map.entry("revenueGroups",report.get("revenueGroups")),
            Map.entry("expenseGroups",report.get("expenseGroups")),Map.entry("dailyRevenue",dailyRevenue(start,branchId)),
            Map.entry("revenueMix",revenueMix(start,branchId)),Map.entry("occupancyByBranch",rows),
            Map.entry("warnings",operationalWarnings(branchId)));
    }

    private List<Map<String,Object>> operationalWarnings(UUID branchId) {
        return db.queryForList("""
            SELECT id,branch_id AS "branchId",branch,kind,detail,level FROM (
              SELECT 'branch-'||b.id::text AS id,b.id AS branch_id,b.name AS branch,'Cơ sở tạm ngừng' AS kind,
                COALESCE(NULLIF(b.status_reason,''),'Cơ sở đang ở trạng thái '||b.status||'.') AS detail,
                'critical' AS level,1 AS priority,b.updated_at AS created_at
              FROM gcmb.branch b WHERE b.status<>'ACTIVE' AND (CAST(? AS uuid) IS NULL OR b.id=?)
              UNION ALL
              SELECT i.id::text,i.branch_id,b.name,'Sự cố thiết bị',a.name||' · '||i.description,
                CASE WHEN i.severity IN ('HIGH','CRITICAL') OR i.priority IN ('HIGH','URGENT') THEN 'critical' ELSE 'warning' END,
                CASE WHEN i.severity IN ('HIGH','CRITICAL') OR i.priority IN ('HIGH','URGENT') THEN 2 ELSE 3 END,
                i.created_at
              FROM gcmb.asset_incident i JOIN gcmb.branch b ON b.id=i.branch_id
              JOIN gcmb.asset a ON a.id=i.asset_id
              WHERE i.status IN ('REPORTED','WAITING_APPROVAL','ASSESSED','IN_PROGRESS')
                AND (CAST(? AS uuid) IS NULL OR i.branch_id=?)
              UNION ALL
              SELECT 'stock-'||i.id::text,i.branch_id,b.name,'Tồn kho thấp',
                i.name||' còn '||s.quantity||' '||u.name||' (định mức '||s.minimum_quantity||' '||u.name||').',
                'warning',4,s.created_at
              FROM gcmb.inventory_item i JOIN gcmb.stock_balance s ON s.inventory_item_id=i.id
              JOIN gcmb.branch b ON b.id=i.branch_id JOIN gcmb.unit u ON u.id=i.unit_id
              WHERE i.active AND s.minimum_quantity>0 AND s.quantity<s.minimum_quantity
                AND (CAST(? AS uuid) IS NULL OR i.branch_id=?)
              UNION ALL
              SELECT 'room-'||r.id::text,r.branch_id,b.name,'Phòng đang bảo trì',
                COALESCE(NULLIF(r.code,''),r.name)||' đang tạm ngưng phục vụ.',
                'warning',5,r.created_at
              FROM gcmb.room r JOIN gcmb.branch b ON b.id=r.branch_id
              WHERE r.operating_status='UNDER_MAINTENANCE' AND (CAST(? AS uuid) IS NULL OR r.branch_id=?)
            ) alerts ORDER BY priority,created_at DESC LIMIT 24
            """,branchId,branchId,branchId,branchId,branchId,branchId,branchId,branchId);
    }

    @GetMapping("/api/v1/branch-management/dashboard")
    @Transactional(readOnly = true)
    public Map<String, Object> branch(@AuthenticationPrincipal Identity actor, @RequestParam String month,
                                      @RequestParam(required = false) String scopePeriod,
                                      @RequestParam(required = false) String scopeFrom,
                                      @RequestParam(required = false) String scopeTo,
                                      @RequestParam(required = false) String revenuePeriod,
                                      @RequestParam(required = false) String revenueDate,
                                      @RequestParam(required = false) String revenueFrom,
                                      @RequestParam(required = false) String revenueTo) {
        requireRole(actor, "BRANCH_MANAGER");
        // Phạm vi được kiểm tra lại tại máy chủ, kể cả khi không có header chọn cơ sở.
        if (actor.branchId() == null || !Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM gcmb.branch_manager_assignment WHERE user_id=? AND branch_id=?)", Boolean.class, actor.id(), actor.branchId())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem đối với cơ sở này.");
        LocalDate start = month(month);
        String timezone = db.queryForObject("SELECT timezone FROM gcmb.branch WHERE id=?", String.class, actor.branchId());
        LocalDate today = LocalDate.now(java.time.ZoneId.of(timezone));
        ReportRange selectedReport = reportRange(scopePeriod, scopeFrom, scopeTo, start, today);
        RevenueRange selectedRevenue = revenueRange(revenuePeriod, revenueDate, revenueFrom, revenueTo, start, today);
        Map<String,Object> report = performance.report(actor,start,actor.branchId());
        List<Map<String,Object>> rows = (List<Map<String,Object>>) report.get("results");
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
        List<Map<String,Object>> dailyRevenue = performance.dailyRevenue(selectedRevenue.from(), selectedRevenue.to(), actor.branchId());
        Map<String,Object> revenueSummary = revenueSummary(dailyRevenue);
        List<Map<String,Object>> reportDailyRevenue = performance.dailyRevenue(selectedReport.from(), selectedReport.toExclusive(), actor.branchId());
        Map<String,Object> reportSummary = revenueSummary(reportDailyRevenue);
        return Map.ofEntries(
            Map.entry("month", month),
            Map.entry("scopePeriod", selectedReport.period()),
            Map.entry("scopeFrom", selectedReport.from().toString()),
            Map.entry("scopeTo", selectedReport.to().toString()),
            Map.entry("result", rows.get(0)),
            Map.entry("operations", operational),
            Map.entry("revenuePeriod", selectedRevenue.period()),
            Map.entry("revenueDate", selectedRevenue.date()),
            Map.entry("revenueSummary", revenueSummary),
            Map.entry("reportSummary", reportSummary),
            Map.entry("dailyRevenue", dailyRevenue),
            Map.entry("revenueMix", revenueMix(selectedReport.from(), selectedReport.toExclusive(), actor.branchId())),
            Map.entry("hourlyOccupancy", hourlyOccupancy(selectedReport.from(), selectedReport.toExclusive(), actor.branchId())),
            Map.entry("roomTypePerformance", roomTypePerformance(selectedReport.from(), selectedReport.toExclusive(), actor.branchId())),
            Map.entry("topItems", topItems(selectedReport.from(), selectedReport.toExclusive(), actor.branchId())),
            Map.entry("lowStockItems", lowStock(actor.branchId())),
            Map.entry("openIncidents", openIncidents(actor.branchId())),
            Map.entry("todayShifts", todayShifts(actor.branchId(), today)));
    }

    private List<Map<String, Object>> dailyRevenue(LocalDate start, UUID branchId) {
        return performance.dailyRevenue(start,branchId);
    }

    private RevenueRange revenueRange(String period, String date, String fromValue, String toValue, LocalDate defaultMonth, LocalDate today) {
        String selectedPeriod = period == null ? "MONTH" : period.trim().toUpperCase(Locale.ROOT);
        String selectedDate = date == null || date.isBlank()
            ? ("DAY".equals(selectedPeriod) ? defaultMonth.toString() : "YEAR".equals(selectedPeriod) ? String.valueOf(defaultMonth.getYear()) : YearMonth.from(defaultMonth).toString())
            : date.trim();
        try {
            LocalDate from;
            LocalDate to;
            switch (selectedPeriod) {
                case "DAY" -> { from = LocalDate.parse(selectedDate); to = from.plusDays(1); }
                case "MONTH" -> { YearMonth month = YearMonth.parse(selectedDate); from = month.atDay(1); to = month.plusMonths(1).atDay(1); }
                case "YEAR" -> { int year = java.time.Year.parse(selectedDate).getValue(); from = LocalDate.of(year, 1, 1); to = from.plusYears(1); }
                case "RANGE" -> {
                    if (fromValue == null || fromValue.isBlank() || toValue == null || toValue.isBlank())
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn đầy đủ ngày bắt đầu và ngày kết thúc.");
                    from = LocalDate.parse(fromValue.trim());
                    LocalDate inclusiveEnd = LocalDate.parse(toValue.trim());
                    if (from.isAfter(inclusiveEnd) || inclusiveEnd.isAfter(today)
                            || java.time.temporal.ChronoUnit.DAYS.between(from, inclusiveEnd) >= 366)
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoảng ngày của biểu đồ không hợp lệ hoặc vượt quá 366 ngày.");
                    to = inclusiveEnd.plusDays(1);
                    selectedDate = from + "/" + inclusiveEnd;
                }
                default -> throw new DateTimeParseException("invalid period", selectedPeriod, 0);
            }
            if (from.isAfter(today) && !"MONTH".equals(selectedPeriod))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể xem doanh thu ở thời điểm trong tương lai.");
            return new RevenueRange(selectedPeriod, selectedDate, from, to);
        } catch (java.time.DateTimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày hoặc phạm vi doanh thu không hợp lệ.");
        }
    }

    private ReportRange reportRange(String period, String fromValue, String toValue, LocalDate defaultMonth, LocalDate today) {
        String selectedPeriod = period == null ? "MONTH" : period.trim().toUpperCase(Locale.ROOT);
        boolean hasFrom = fromValue != null && !fromValue.isBlank();
        boolean hasTo = toValue != null && !toValue.isBlank();
        if (hasFrom != hasTo)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn đầy đủ ngày bắt đầu và ngày kết thúc.");
        try {
            LocalDate from = hasFrom ? LocalDate.parse(fromValue.trim()) : defaultMonth;
            LocalDate to;
            if (hasTo) {
                to = LocalDate.parse(toValue.trim());
            } else {
                LocalDate monthEnd = defaultMonth.plusMonths(1).minusDays(1);
                to = !defaultMonth.isAfter(today) && monthEnd.isAfter(today) ? today : monthEnd;
            }
            if (!List.of("MONTH", "YEAR", "RANGE").contains(selectedPeriod)
                    || from.isAfter(to)
                    || java.time.temporal.ChronoUnit.DAYS.between(from, to) >= 366
                    || ("RANGE".equals(selectedPeriod) && to.isAfter(today)))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phạm vi báo cáo không hợp lệ hoặc vượt quá 366 ngày.");
            return new ReportRange(selectedPeriod, from, to);
        } catch (java.time.DateTimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày hoặc phạm vi báo cáo không hợp lệ.");
        }
    }

    private Map<String,Object> revenueSummary(List<Map<String,Object>> rows) {
        java.math.BigDecimal revenue = java.math.BigDecimal.ZERO;
        java.math.BigDecimal fnbRevenue = java.math.BigDecimal.ZERO;
        long billCount = 0;
        for (Map<String,Object> row : rows) {
            revenue = revenue.add(decimal(row.get("revenue")));
            fnbRevenue = fnbRevenue.add(decimal(row.get("fnbRevenue")));
            billCount += ((Number) row.getOrDefault("billCount", 0L)).longValue();
        }
        return Map.of("revenue", revenue, "fnbRevenue", fnbRevenue, "billCount", billCount);
    }

    private static java.math.BigDecimal decimal(Object value) {
        if (value == null) return java.math.BigDecimal.ZERO;
        return value instanceof java.math.BigDecimal amount ? amount : new java.math.BigDecimal(value.toString());
    }

    private record RevenueRange(String period, String date, LocalDate from, LocalDate to) {}
    private record ReportRange(String period, LocalDate from, LocalDate to) {
        LocalDate toExclusive() { return to.plusDays(1); }
    }

    private List<Map<String, Object>> revenueMix(LocalDate start, UUID branchId) {
        return revenueMix(start, start.plusMonths(1), branchId);
    }

    @GetMapping("/api/v1/head-office/dashboard/revenue-mix")
    @Transactional(readOnly = true)
    public Map<String,Object> chainRevenueMix(@AuthenticationPrincipal Identity actor,
            @RequestParam String from, @RequestParam String to, @RequestParam(required = false) UUID branchId) {
        requireRole(actor, "HEAD_OFFICE_MANAGER");
        try {
            LocalDate start = LocalDate.parse(from), end = LocalDate.parse(to);
            if (start.isAfter(end) || end.isAfter(LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")))
                    || java.time.temporal.ChronoUnit.DAYS.between(start, end) >= 366)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoảng ngày báo cáo không hợp lệ.");
            return Map.of("from", start.toString(), "to", end.toString(), "revenueMix", revenueMix(start, end.plusDays(1), branchId));
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày báo cáo không hợp lệ.");
        }
    }

    private List<Map<String, Object>> revenueMix(LocalDate start, LocalDate endExclusive, UUID branchId) {
        return db.queryForList("""
            WITH revenue_mix AS (
              SELECT CASE WHEN p.method='BANK_TRANSFER' THEN 'Chuyển khoản'
                          WHEN p.method='CASH' THEN 'Tiền mặt' ELSE p.method END AS label,
                SUM(p.amount) AS value
              FROM gcmb.bill b JOIN gcmb.branch br ON br.id=b.branch_id
              JOIN gcmb.payment p ON p.bill_id=b.id AND p.purpose='BILL'
              WHERE b.status='SETTLED' AND (b.completed_at AT TIME ZONE br.timezone)::date >= ?
                AND (b.completed_at AT TIME ZONE br.timezone)::date < ?
                AND (CAST(? AS uuid) IS NULL OR b.branch_id=?)
              GROUP BY label
              UNION ALL
              SELECT 'Cọc tịch thu', COALESCE(SUM(de.amount),0)
              FROM gcmb.deposit_entry de JOIN gcmb.deposit d ON d.id=de.deposit_id
              JOIN gcmb.booking bk ON bk.id=d.booking_id JOIN gcmb.branch br ON br.id=bk.branch_id
              WHERE de.kind='FORFEIT' AND (de.created_at AT TIME ZONE br.timezone)::date>=?
                AND (de.created_at AT TIME ZONE br.timezone)::date<? AND (CAST(? AS uuid) IS NULL OR bk.branch_id=?)
            ) SELECT label,value FROM revenue_mix ORDER BY label
            """, start, endExclusive, branchId, branchId,
                start, endExclusive, branchId, branchId);
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

    private List<Map<String, Object>> roomTypePerformance(LocalDate start, LocalDate endExclusive, UUID branchId) {
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
            """, start, endExclusive, branchId, start, endExclusive);
    }

    private List<Map<String, Object>> hourlyOccupancy(LocalDate start, LocalDate endExclusive, UUID branchId) {
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
            """, start, endExclusive, branchId);
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

    private List<Map<String, Object>> topItems(LocalDate start, LocalDate endExclusive, UUID branchId) {
        return db.queryForList("""
            SELECT ol.name_snapshot AS name,COALESCE(SUM(ol.quantity),0) AS quantity,
              COALESCE(SUM(bl.amount),0) AS revenue
            FROM gcmb.bill b JOIN gcmb.branch br ON br.id=b.branch_id
            JOIN gcmb.bill_line bl ON bl.bill_id=b.id
            JOIN gcmb.order_line ol ON ol.id=bl.order_line_id
            WHERE b.status='SETTLED' AND (b.completed_at AT TIME ZONE br.timezone)::date >= ?
              AND (b.completed_at AT TIME ZONE br.timezone)::date < ? AND b.branch_id=?
            GROUP BY ol.name_snapshot ORDER BY quantity DESC,revenue DESC LIMIT 10
            """, start, endExclusive, branchId);
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

}
