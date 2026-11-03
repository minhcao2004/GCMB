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
        return Map.of("month", month, "branches", options, "results", rows, "totals", totals(rows));
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
                   count(*) FILTER (WHERE r.operating_status <> 'INACTIVE' AND EXISTS (SELECT 1 FROM gcmb.room_allocation a WHERE a.room_id=r.id AND a.state='OCCUPIED')) AS "occupiedRooms",
                   count(*) FILTER (WHERE r.operating_status='UNDER_MAINTENANCE') AS "maintenanceRooms"
            FROM gcmb.room r WHERE r.branch_id=?
            """, actor.branchId());
        operational.put("openIncidents", db.queryForObject("SELECT count(*) FROM gcmb.asset_incident WHERE branch_id=? AND status NOT IN ('VERIFIED','CLOSED')", Long.class, actor.branchId()));
        return Map.of("month", month, "result", rows.get(0), "operations", operational);
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
        // BR-103, BR-107: chỉ cộng số chi đã thanh toán, tách OPEX và CAPEX; chuyển quỹ không nằm trong expense_payment.
        // BR-104, BR-105: lợi nhuận = thu - chi vận hành; biên lợi nhuận chỉ tính khi thu > 0.
        return db.queryForList("""
            SELECT b.id, b.code, b.name, b.address, b.status, b.timezone,
                COALESCE(revenue.amount,0) AS revenue, COALESCE(revenue.bills,0) AS "billCount",
                COALESCE(cost.opex,0) AS expenses, COALESCE(cost.capex,0) AS investment, COALESCE(cost.payments,0) AS "expenseCount",
                COALESCE(revenue.amount,0)-COALESCE(cost.opex,0) AS profit,
                CASE WHEN COALESCE(revenue.amount,0)>0 THEN (revenue.amount-COALESCE(cost.opex,0))*100/revenue.amount ELSE NULL END AS margin
            FROM gcmb.branch b
            LEFT JOIN LATERAL (SELECT sum(x.net_amount) amount, count(*) bills FROM gcmb.bill x
                WHERE x.branch_id=b.id AND x.status='SETTLED' AND (x.completed_at AT TIME ZONE b.timezone)::date>=? AND (x.completed_at AT TIME ZONE b.timezone)::date<?) revenue ON true
            LEFT JOIN LATERAL (SELECT sum(p.amount) FILTER (WHERE e.class_snapshot='OPEX') opex, sum(p.amount) FILTER (WHERE e.class_snapshot='CAPEX') capex, count(*) payments
                FROM gcmb.expense_payment p JOIN gcmb.expense e ON e.id=p.expense_id
                WHERE e.branch_id=b.id AND p.status='PAID' AND (p.paid_at AT TIME ZONE b.timezone)::date>=? AND (p.paid_at AT TIME ZONE b.timezone)::date<?) cost ON true
            WHERE (?::uuid IS NULL OR b.id=?::uuid) ORDER BY b.name,b.id
            """, start, start.plusMonths(1), start, start.plusMonths(1), selected, selected);
    }
    private Map<String, Object> totals(List<Map<String, Object>> rows) {
        // BR-109: lợi nhuận chuỗi bằng tổng lợi nhuận các cơ sở trong kỳ.
        BigDecimal revenue=BigDecimal.ZERO, expenses=BigDecimal.ZERO, investment=BigDecimal.ZERO, profit=BigDecimal.ZERO;
        long bills=0, payments=0, loss=0;
        for (Map<String,Object> row : rows) {
            revenue=revenue.add((BigDecimal)row.get("revenue")); expenses=expenses.add((BigDecimal)row.get("expenses"));
            investment=investment.add((BigDecimal)row.get("investment")); profit=profit.add((BigDecimal)row.get("profit"));
            bills+=((Number)row.get("billCount")).longValue(); payments+=((Number)row.get("expenseCount")).longValue(); if (((BigDecimal)row.get("profit")).signum()<0) loss++;
        }
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("revenue",revenue); result.put("expenses",expenses); result.put("investment",investment); result.put("profit",profit);
        result.put("billCount",bills); result.put("expenseCount",payments); result.put("lossBranches",loss); result.put("branchCount",rows.size());
        return result;
    }
}
