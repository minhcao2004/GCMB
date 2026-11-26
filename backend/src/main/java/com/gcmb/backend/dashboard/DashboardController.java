package com.gcmb.backend.dashboard;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.dashboard.repository.DashboardRepository;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class DashboardController {
    private final DashboardRepository dashboardRepository;
    private final MonthlyPerformanceService monthlyPerformanceService;

    public DashboardController(DashboardRepository dashboardRepository, MonthlyPerformanceService monthlyPerformanceService) {
        this.dashboardRepository = dashboardRepository;
        this.monthlyPerformanceService = monthlyPerformanceService;
    }

    @GetMapping("/api/v1/head-office/dashboard")
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Map<String, Object> chain(@AuthenticationPrincipal Identity actor, @RequestParam String month, @RequestParam(required = false) UUID branchId) {
        requireRole(actor, "HEAD_OFFICE_MANAGER");
        LocalDate start = month(month);
        List<Map<String, Object>> options = dashboardRepository.findActiveBranchOptions();
        Map<String, Object> report = monthlyPerformanceService.report(actor,start,branchId);
        List<Map<String,Object>> rows = (List<Map<String,Object>>) report.get("results");
        return Map.ofEntries(Map.entry("month",month),Map.entry("branches",options),Map.entry("results",rows),
            Map.entry("totals",report.get("totals")),Map.entry("previousTotals",report.get("previousTotals")),
            Map.entry("changes",report.get("changes")),Map.entry("revenueGroups",report.get("revenueGroups")),
            Map.entry("expenseGroups",report.get("expenseGroups")),Map.entry("dailyRevenue",dailyRevenue(start,branchId)),
            Map.entry("revenueMix",revenueMix(start,branchId)),Map.entry("occupancyByBranch",rows),
            Map.entry("warnings",dashboardRepository.operationalWarnings(branchId)));
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
        requireAssignedBranch(actor);
        LocalDate start = month(month);
        String timezone = dashboardRepository.findBranchTimezone(actor.branchId());
        LocalDate today = LocalDate.now(java.time.ZoneId.of(timezone));
        ReportRange selectedReport = reportRange(scopePeriod, scopeFrom, scopeTo, start, today);
        RevenueRange selectedRevenue = revenueRange(revenuePeriod, revenueDate, revenueFrom, revenueTo, start, today);
        Map<String,Object> report = monthlyPerformanceService.report(actor,start,actor.branchId());
        List<Map<String,Object>> rows = (List<Map<String,Object>>) report.get("results");
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy cơ sở.");
        Map<String, Object> operational = dashboardRepository.branchOperations(actor.branchId());
        List<Map<String,Object>> dailyRevenue = monthlyPerformanceService.dailyRevenue(selectedRevenue.from(), selectedRevenue.to(), actor.branchId());
        Map<String,Object> revenueSummary = revenueSummary(dailyRevenue);
        List<Map<String,Object>> reportDailyRevenue = monthlyPerformanceService.dailyRevenue(selectedReport.from(), selectedReport.toExclusive(), actor.branchId());
        Map<String,Object> reportSummary = revenueSummary(reportDailyRevenue);
        var previousReport = selectedReport.previous();
        Map<String,Object> reportComparison = Map.of(
            "from", previousReport.from().toString(),
            "to", previousReport.to().toString(),
            "summary", revenueSummary(monthlyPerformanceService.dailyRevenue(
                previousReport.from(), previousReport.toExclusive(), actor.branchId())));
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
            Map.entry("reportComparison", reportComparison),
            Map.entry("dailyRevenue", dailyRevenue),
            Map.entry("revenueMix", dashboardRepository.revenueMix(selectedReport.from(), selectedReport.toExclusive(), actor.branchId())),
            Map.entry("hourlyOccupancy", dashboardRepository.hourlyOccupancy(selectedReport.from(), selectedReport.toExclusive(), actor.branchId())),
            Map.entry("roomTypePerformance", dashboardRepository.roomTypePerformance(selectedReport.from(), selectedReport.toExclusive(), actor.branchId())),
            Map.entry("topItems", dashboardRepository.topItems(selectedReport.from(), selectedReport.toExclusive(), actor.branchId())),
            Map.entry("lowStockItems", dashboardRepository.lowStock(actor.branchId())),
            Map.entry("openIncidents", dashboardRepository.openIncidents(actor.branchId())),
            Map.entry("todayShifts", dashboardRepository.todayShifts(actor.branchId(), today)));
    }

    @GetMapping("/api/v1/head-office/dashboard/customer-sources")
    @Transactional(readOnly = true)
    public Map<String, Object> chainCustomerSources(@AuthenticationPrincipal Identity actor,
            @RequestParam String period, @RequestParam String date, @RequestParam(required = false) UUID branchId) {
        requireRole(actor, "HEAD_OFFICE_MANAGER");
        return customerSources(period, date, branchId);
    }

    @GetMapping("/api/v1/branch-management/dashboard/customer-sources")
    @Transactional(readOnly = true)
    public Map<String, Object> branchCustomerSources(@AuthenticationPrincipal Identity actor,
            @RequestParam String period, @RequestParam String date) {
        requireRole(actor, "BRANCH_MANAGER");
        requireAssignedBranch(actor);
        return customerSources(period, date, actor.branchId());
    }

    private Map<String, Object> customerSources(String period, String date, UUID branchId) {
        CustomerSourceRange range = customerSourceRange(period, date);
        List<Map<String, Object>> sources = dashboardRepository.customerSourceCounts(range.from(), range.toExclusive(), branchId);
        return Map.of("sources", sources);
    }

    private static CustomerSourceRange customerSourceRange(String period, String date) {
        try {
            CustomerSourceRange range = switch (period.trim().toUpperCase(Locale.ROOT)) {
                case "DAY" -> {
                    LocalDate day = LocalDate.parse(date);
                    yield new CustomerSourceRange(day, day.plusDays(1));
                }
                case "MONTH" -> {
                    YearMonth month = YearMonth.parse(date);
                    yield new CustomerSourceRange(month.atDay(1), month.plusMonths(1).atDay(1));
                }
                case "YEAR" -> {
                    Year year = Year.parse(date);
                    yield new CustomerSourceRange(year.atDay(1), year.plusYears(1).atDay(1));
                }
                default -> throw new DateTimeException("Unsupported customer source period");
            };
            if (range.from().getYear() < 1 || range.from().getYear() > 9999)
                throw new DateTimeException("Unsupported customer source year");
            return range;
        } catch (DateTimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày hoặc kỳ thống kê nguồn khách không hợp lệ.");
        }
    }

    private record CustomerSourceRange(LocalDate from, LocalDate toExclusive) {}

    private List<Map<String, Object>> dailyRevenue(LocalDate start, UUID branchId) {
        return monthlyPerformanceService.dailyRevenue(start,branchId);
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
            } else if ("DAY".equals(selectedPeriod)) {
                to = from;
            } else {
                LocalDate monthEnd = defaultMonth.plusMonths(1).minusDays(1);
                to = !defaultMonth.isAfter(today) && monthEnd.isAfter(today) ? today : monthEnd;
            }
            if (!List.of("DAY", "MONTH", "YEAR", "RANGE").contains(selectedPeriod)
                    || from.isAfter(to)
                    || java.time.temporal.ChronoUnit.DAYS.between(from, to) >= 366
                    || (List.of("DAY", "RANGE").contains(selectedPeriod) && to.isAfter(today))
                    || ("DAY".equals(selectedPeriod) && !from.equals(to)))
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

        ReportRange previous() {
            String comparison = switch (period) {
                case "DAY" -> "previous-day";
                case "MONTH" -> "previous-month";
                case "YEAR" -> "previous-year";
                default -> "prev";
            };
            var range = MonthlyPerformanceService.comparisonRange(from, to, comparison);
            return new ReportRange(period, range.from(), range.to());
        }
    }

    private List<Map<String, Object>> revenueMix(LocalDate start, UUID branchId) {
        return dashboardRepository.revenueMix(start, start.plusMonths(1), branchId);
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
            return Map.of("from", start.toString(), "to", end.toString(), "revenueMix", dashboardRepository.revenueMix(start, end.plusDays(1), branchId));
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày báo cáo không hợp lệ.");
        }
    }

    private static void requireRole(Identity actor, String role) {
        if (actor == null || !role.equals(actor.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem đối với dashboard.");
    }

    private void requireAssignedBranch(Identity actor) {
        if (actor.branchId() == null || !dashboardRepository.isBranchManagerOf(actor.id(), actor.branchId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem đối với cơ sở này.");
        }
    }

    private static LocalDate month(String value) {
        try { if (!value.matches("\\d{4}-\\d{2}")) throw new DateTimeParseException("invalid", value, 0); return YearMonth.parse(value).atDay(1); }
        catch (DateTimeParseException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tháng báo cáo không hợp lệ."); }
    }

}
