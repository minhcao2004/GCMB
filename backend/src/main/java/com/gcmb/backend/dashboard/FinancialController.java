package com.gcmb.backend.dashboard;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.dashboard.repository.FinancialReportRepository;
import com.gcmb.backend.finance.service.FinancialPeriodService;
import com.gcmb.backend.finance.service.FinanceAccess;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/accounting")
public class FinancialController {
    private final FinancialReportRepository financialReportRepository;
    private final FinancialTransactionAdjustmentService financialTransactionAdjustmentService;
    private final FinancialPeriodService financialPeriodService;
    private final MonthlyPerformanceService monthlyPerformanceService;
    private final AccountingWorkbookService accountingWorkbookService;
    private final FinanceAccess financeAccess;

    public FinancialController(FinancialReportRepository financialReportRepository, FinancialTransactionAdjustmentService financialTransactionAdjustmentService,
                               FinancialPeriodService financialPeriodService, MonthlyPerformanceService monthlyPerformanceService,
                               AccountingWorkbookService accountingWorkbookService, FinanceAccess financeAccess) {
        this.financialReportRepository = financialReportRepository;
        this.financialTransactionAdjustmentService = financialTransactionAdjustmentService;
        this.financialPeriodService = financialPeriodService;
        this.monthlyPerformanceService = monthlyPerformanceService;
        this.accountingWorkbookService = accountingWorkbookService;
        this.financeAccess = financeAccess;
    }

    @GetMapping("/reports/monthly")
    @Transactional(readOnly = true)
    public Map<String, Object> monthlyReport(@AuthenticationPrincipal Identity actor, @RequestParam String month,
                                             @RequestParam(required = false) UUID branchId) {
        requireReportRole(actor);
        return monthlyPerformanceService.report(actor,month(month),branchId);
    }

    @GetMapping("/reports/source-imports")
    @Transactional(readOnly = true)
    public Map<String, Object> sourceImportReport(@AuthenticationPrincipal Identity actor,
                                                   @RequestParam String from,
                                                   @RequestParam String to,
                                                   @RequestParam(required = false) UUID branchId,
                                                   @RequestParam(defaultValue = "ALL") String source) {
        requireOverviewRole(actor);
        LocalDate start = date(from), end = date(to);
        if (start.isAfter(end)) throw bad("Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.");
        if (end.isAfter(LocalDate.now(FinancialPeriodService.ZONE))) throw bad("Không thể xem báo cáo đến ngày trong tương lai.");
        if (java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1 > 62)
            throw bad("Khoảng ngày báo cáo nguồn tối đa là 62 ngày.");
        String selectedSource = source == null ? "ALL" : source.toUpperCase(java.util.Locale.ROOT);
        if (!List.of("ALL", "SOURCE_FILES", "SYNTHETIC_DEMO").contains(selectedSource))
            throw bad("Nguồn dữ liệu báo cáo không hợp lệ.");
        String syntheticFilter = "ALL".equals(selectedSource) ? "ALL"
            : "SYNTHETIC_DEMO".equals(selectedSource) ? "SYNTHETIC_DEMO" : "SOURCE_FILES";
        List<Map<String, Object>> rows = financialReportRepository.sourceImportRows(start, end, branchId, syntheticFilter);
        if ("ALL".equals(selectedSource)) {
            List<Map<String, Object>> operationalBills = financialReportRepository.operationalBillRows(start, end, branchId);
            rows.addAll(operationalBills);
        }
        rows.sort(java.util.Comparator.comparing((Map<String, Object> row) -> String.valueOf(row.get("businessDate"))).reversed()
            .thenComparing(row -> String.valueOf(row.get("branchName"))));
        List<Map<String, Object>> branches = financialReportRepository.findBranchOptions();
        return Map.of("from", start.toString(), "to", end.toString(), "source", selectedSource,
            "branchOptions", branches, "rows", rows);
    }

    @GetMapping("/reports/financial-overview")
    @Transactional(readOnly = true)
    public Map<String, Object> financialOverview(@AuthenticationPrincipal Identity actor,
                                                  @RequestParam String from, @RequestParam String to,
                                                  @RequestParam(required = false) List<UUID> branchIds,
                                                  @RequestParam(defaultValue = "prev") String compare,
                                                  @RequestParam(defaultValue = "day") String granularity) {
        requireOverviewRole(actor);
        return monthlyPerformanceService.overview(actor, date(from), date(to), branchIds, compare, granularity);
    }

    @GetMapping("/reports/financial-overview/export")
    @Transactional
    public ResponseEntity<byte[]> exportFinancialOverview(@AuthenticationPrincipal Identity actor,
                                                            @RequestParam String from, @RequestParam String to,
                                                            @RequestParam(required = false) List<UUID> branchIds,
                                                            @RequestParam(defaultValue = "prev") String compare,
                                                            @RequestParam(defaultValue = "day") String granularity,
                                                            @RequestParam(defaultValue = "csv") String format) {
        requireOverviewRole(actor);
        if (!List.of("csv", "xlsx").contains(format.toLowerCase(java.util.Locale.ROOT))) throw bad("Định dạng xuất chỉ nhận csv hoặc xlsx.");
        LocalDate start = date(from), end = date(to);
        Map<String, Object> data = monthlyPerformanceService.overview(actor, start, end, branchIds, compare, granularity);
        byte[] bytes = "xlsx".equalsIgnoreCase(format) ? MonthlyPerformanceExport.overviewXlsx(data) : MonthlyPerformanceExport.overviewCsv(data);
        financialReportRepository.insertOverviewExportAudit(actor.id(), String.format(java.util.Locale.ROOT,
              "{\"from\":\"%s\",\"to\":\"%s\",\"compare\":\"%s\",\"granularity\":\"%s\",\"format\":\"%s\"}",
              start, end, compare, granularity, format.toLowerCase(java.util.Locale.ROOT)));
        String extension = format.toLowerCase(java.util.Locale.ROOT);
        MediaType type = "xlsx".equals(extension)
            ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            : new MediaType("text", "csv", StandardCharsets.UTF_8);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=gcmb-financial-overview-" + start + "-" + end + "." + extension)
            .contentType(type).body(bytes);
    }

    @GetMapping("/reports/monthly-performance/export")
    @Transactional
    public ResponseEntity<byte[]> exportMonthlyPerformance(@AuthenticationPrincipal Identity actor, @RequestParam String month,
                                                            @RequestParam(required = false) UUID branchId,
                                                            @RequestParam(defaultValue = "csv") String format) {
        byte[] bytes=monthlyPerformanceService.export(actor,month(month),branchId,format);
        String extension=format.toLowerCase(java.util.Locale.ROOT);
        MediaType type="xlsx".equals(extension)
            ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            : new MediaType("text","csv",StandardCharsets.UTF_8);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=gcmb-monthly-performance-"+month+"."+extension)
            .contentType(type).body(bytes);
    }

    @GetMapping("/reports/expense-breakdown")
    @Transactional(readOnly = true)
    public Map<String, Object> expenseBreakdown(@AuthenticationPrincipal Identity actor,
                                                @RequestParam(required = false) String month,
                                                @RequestParam(required = false) String from,
                                                @RequestParam(required = false) String to,
                                                @RequestParam(required = false) List<UUID> branchIds,
                                                @RequestParam(required = false) UUID branchId,
                                                @RequestParam(defaultValue = "") String costType,
                                                @RequestParam(required = false) UUID categoryId,
                                                @RequestParam(defaultValue = "ALLOCATION") String basis,
                                                @RequestParam(defaultValue = "") String q) {
        requireReportRole(actor);
        List<LocalDate> range = reportDateRange(month, from, to);
        LocalDate start = range.get(0), endExclusive = range.get(1);
        String type = costType == null ? "" : costType.trim().toUpperCase(java.util.Locale.ROOT);
        String selectedBasis = basis == null ? "ALLOCATION" : basis.trim().toUpperCase(java.util.Locale.ROOT);
        String search = q == null ? "" : q.trim();
        if (!type.isEmpty() && !List.of("OPEX", "CAPEX").contains(type)) throw bad("Loại chi phí không hợp lệ.");
        if (!List.of("ALLOCATION", "CASH").contains(selectedBasis)) throw bad("Cách xem chi phí không hợp lệ.");
        if (search.length() > 100) throw bad("Từ khóa tìm kiếm chỉ được chứa tối đa 100 ký tự.");
        List<UUID> selectedBranches = monthlyPerformanceService.validateReportBranchIds(branchIds);
        if (selectedBranches.isEmpty() && branchId != null) monthlyPerformanceService.validateReportBranchIds(List.of(branchId));
        // BR-103/107: mặc định lấy lịch phân bổ, giữ OPEX và CAPEX riêng; CASH dùng ngày thanh toán.
        List<Map<String, Object>> rows = financialReportRepository.expenseBreakdownRows(selectedBasis, start, endExclusive, selectedBranches,
            branchId, type, categoryId, search);
        List<Map<String, Object>> branches = financialReportRepository.findBranchNames();
        List<Map<String, Object>> categories = financialReportRepository.findExpenseCategories();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("month", month); result.put("from", start.toString()); result.put("to", endExclusive.minusDays(1).toString());
        result.put("basis", selectedBasis);
        result.put("branches", branches); result.put("categories", categories); result.put("rows", rows);
        return result;
    }

    @GetMapping("/reports/expense-breakdown/details")
    @Transactional(readOnly = true)
    public Map<String, Object> expenseBreakdownDetails(@AuthenticationPrincipal Identity actor,
                                                        @RequestParam(required = false) String month,
                                                        @RequestParam(required = false) String from,
                                                        @RequestParam(required = false) String to,
                                                        @RequestParam UUID branchId,
                                                        @RequestParam UUID categoryId,
                                                        @RequestParam String costType,
                                                        @RequestParam(defaultValue = "ALLOCATION") String basis,
                                                        @RequestParam(defaultValue = "") String q) {
        requireReportRole(actor);
        List<LocalDate> range = reportDateRange(month, from, to);
        LocalDate start = range.get(0), endExclusive = range.get(1);
        String type = costType == null ? "" : costType.trim().toUpperCase(java.util.Locale.ROOT);
        String selectedBasis = basis == null ? "ALLOCATION" : basis.trim().toUpperCase(java.util.Locale.ROOT);
        String search = q == null ? "" : q.trim();
        if (!List.of("OPEX", "CAPEX").contains(type)) throw bad("Loại chi phí không hợp lệ.");
        if (!List.of("ALLOCATION", "CASH").contains(selectedBasis)) throw bad("Cách xem chi phí không hợp lệ.");
        if (search.length() > 100) throw bad("Từ khóa tìm kiếm chỉ được chứa tối đa 100 ký tự.");
        List<Map<String, Object>> rows = financialReportRepository.expenseDetailRows(selectedBasis, start, endExclusive, branchId, categoryId, type, search);
        java.util.Set<UUID> requestIds = new java.util.LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            if ("EXPENSE_REQUEST".equals(row.get("sourceType"))) requestIds.add((UUID) row.get("expenseId"));
        }
        Map<UUID, List<Map<String, Object>>> attachmentsByRequest = new java.util.HashMap<>();
        if (!requestIds.isEmpty()) {
            List<Map<String, Object>> attachments = financialReportRepository.findRequestAttachments(requestIds);
            for (Map<String, Object> attachment : attachments) {
                UUID ownerId = (UUID) attachment.get("ownerId");
                attachmentsByRequest.computeIfAbsent(ownerId, ignored -> new java.util.ArrayList<>()).add(attachment);
            }
        }
        for (Map<String, Object> row : rows) {
            row.put("attachments", attachmentsByRequest.getOrDefault((UUID) row.get("expenseId"), List.of()));
        }
        return Map.of("month", month, "basis", selectedBasis, "rows", rows);
    }

    @GetMapping("/dashboard-details")
    @Transactional(readOnly = true)
    public Map<String, Object> dashboardDetails(@AuthenticationPrincipal Identity actor,
                                                @RequestParam String month,
                                                @RequestParam(required = false) UUID branchId,
                                                @RequestParam(required = false) String from,
                                                @RequestParam(required = false) String to) {
        requireAccountant(actor);
        LocalDate start;
        LocalDate endExclusive;
        try {
            start = from == null || from.isBlank() ? month(month) : LocalDate.parse(from);
            LocalDate end = to == null || to.isBlank() ? start.plusMonths(1).minusDays(1) : LocalDate.parse(to);
            if (end.isBefore(start)) throw bad("Khoảng ngày không hợp lệ.");
            endExclusive = end.plusDays(1);
        } catch (java.time.format.DateTimeParseException invalidDate) {
            throw bad("Khoảng ngày không hợp lệ.");
        }
        List<Map<String, Object>> methods = financialReportRepository.paymentMethodTotals(start, endExclusive, branchId);
        List<Map<String, Object>> exceptions = financialReportRepository.sePayExceptions(Timestamp.from(start.atStartOfDay(FinancialPeriodService.ZONE).toInstant()),
            Timestamp.from(endExclusive.atStartOfDay(FinancialPeriodService.ZONE).toInstant()), branchId);
        return Map.of("paymentMethods", methods, "sepayExceptions", exceptions);
    }

    @PostMapping("/sepay-exceptions/{id}/resolve")
    public Map<String, Object> resolveSePayException(@AuthenticationPrincipal Identity actor, @PathVariable UUID id,
                                                      @RequestBody(required = false) ResolveSePayExceptionCommand command) {
        return financialPeriodService.resolveSePayException(actor, id, command == null ? null : command.resolutionNote());
    }

    @GetMapping("/financial-periods")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> periods(@AuthenticationPrincipal Identity actor) {
        return financialPeriodService.list(actor);
    }

    @GetMapping("/financial-periods/{month}/readiness")
    public Map<String, Object> periodReadiness(@AuthenticationPrincipal Identity actor, @PathVariable String month) {
        return financialPeriodService.readiness(actor, month(month));
    }

    @PostMapping("/financial-periods/{month}/close")
    public Map<String, Object> closePeriod(@AuthenticationPrincipal Identity actor, @PathVariable String month,
                                           @RequestBody(required = false) ClosePeriodCommand command) {
        return financialPeriodService.close(actor, month(month), command != null && command.confirmed(), command == null ? "" : command.note());
    }

    @GetMapping("/transactions")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> transactions(@AuthenticationPrincipal Identity actor, @RequestParam String month,
                                                  @RequestParam(required=false) UUID branchId,
                                                  @RequestParam(defaultValue="") String kind,
                                                  @RequestParam(defaultValue="") String q) {
        requireAccountant(actor);
        LocalDate start = month(month);
        if (!kind.isBlank() && !List.of("PAYMENT", "REFUND", "EXPENSE", "SALARY", "ADVANCE", "TRANSFER", "OPENING", "ADJUSTMENT").contains(kind))
            throw bad("Loại giao dịch không hợp lệ.");
        if (q.trim().length() > 100) throw bad("Từ khóa tìm kiếm chỉ được chứa tối đa 100 ký tự.");
        return financialReportRepository.transactionRows(start, branchId, kind.trim(), q.trim());
    }

    @PostMapping(value="/transactions/{id}/adjustments", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> adjust(@AuthenticationPrincipal Identity actor, @PathVariable UUID id,
                                      @RequestPart("command") AdjustmentCommand input,
                                      @RequestPart(value="evidence", required=false) MultipartFile evidence) {
        return financialTransactionAdjustmentService.adjust(actor, id, input == null ? null : input.amountSigned(),
            input == null ? null : input.reason(), evidence);
    }

    @GetMapping("/tax-data.csv")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal Identity actor, @RequestParam String month,
                                         @RequestParam(defaultValue = "ALL") String scope) {
        requireAccountant(actor);
        LocalDate start = month(month);
        if (!List.of("ALL", "INFLOW", "OUTFLOW").contains(scope)) throw bad("Phạm vi xuất dữ liệu không hợp lệ.");
        // UC-125: export the selected period and cash-flow scope without changing source transactions.
        List<Map<String, Object>> rows = financialReportRepository.taxExportRows(start, scope);
        StringBuilder csv = new StringBuilder("\uFEFFtransaction_id,original_transaction_id,booking_date,occurred_at,kind,reason,branch,account,amount_signed\r\n");
        for (Map<String, Object> row : rows) csv.append(csv(row.get("id"))).append(',').append(csv(row.get("adjusts_transaction_id"))).append(',')
            .append(csv(row.get("booking_date"))).append(',').append(csv(row.get("occurred_at"))).append(',').append(csv(row.get("kind"))).append(',').append(csvText(row.get("reason"))).append(',')
            .append(csvText(row.get("branch_code"))).append(',').append(csvText(row.get("account_code"))).append(',').append(csv(row.get("amount_signed"))).append("\r\n");
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=gcmb-accounting-" + month + ".csv")
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8)).body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/reports/monthly-workbook/preview")
    @Transactional(readOnly = true)
    public Map<String, Object> previewMonthlyWorkbook(@AuthenticationPrincipal Identity actor,
                                                       @RequestParam String month,
                                                       @RequestParam(required = false) String branchIds) {
        return accountingWorkbookService.preview(actor, month(month), branchIds);
    }

    @GetMapping("/reports/monthly-workbook.xlsx")
    @Transactional
    public ResponseEntity<byte[]> exportMonthlyWorkbook(@AuthenticationPrincipal Identity actor,
                                                         @RequestParam String month,
                                                         @RequestParam(required = false) String branchIds) {
        byte[] bytes = accountingWorkbookService.export(actor, month(month), branchIds);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=gcmb-bao-cao-thu-chi-" + month + ".xlsx")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(bytes);
    }

    private Map<String,Object> totals(List<Map<String,Object>> rows) {
        BigDecimal revenue=BigDecimal.ZERO,expenses=BigDecimal.ZERO,salary=BigDecimal.ZERO,investment=BigDecimal.ZERO,forfeitedDeposits=BigDecimal.ZERO,profit=BigDecimal.ZERO,cash=BigDecimal.ZERO;
        long bills=0;
        for (Map<String,Object> row:rows) { revenue=revenue.add((BigDecimal)row.get("revenue")); expenses=expenses.add((BigDecimal)row.get("expenses"));
            investment=investment.add((BigDecimal)row.get("investment")); salary=salary.add((BigDecimal)row.get("salary")); forfeitedDeposits=forfeitedDeposits.add((BigDecimal)row.get("forfeitedDeposits")); profit=profit.add((BigDecimal)row.get("profit")); cash=cash.add((BigDecimal)row.get("netCashFlow")); bills+=((Number)row.get("billCount")).longValue(); }
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("revenue",revenue); result.put("expenses",expenses); result.put("salary",salary); result.put("investment",investment); result.put("forfeitedDeposits",forfeitedDeposits); result.put("profit",profit);
        result.put("netCashFlow",cash); result.put("billCount",bills); result.put("margin",revenue.signum()>0?profit.multiply(BigDecimal.valueOf(100)).divide(revenue,2,RoundingMode.HALF_UP):null);
        return result;
    }
    private static String csv(Object value) { String text=value == null ? "" : value.toString(); return "\""+text.replace("\"","\"\"")+"\""; }
    private static String csvText(Object value) { String text=value == null ? "" : value.toString(); if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) text="'"+text; return csv(text); }
    private static void requireReportRole(Identity actor) { if (actor==null || !List.of("HEAD_OFFICE_MANAGER","ACCOUNTANT").contains(actor.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Bạn không có quyền xem đối với báo cáo tài chính."); }
    private void requireOverviewRole(Identity actor) {
        if (actor == null || !List.of("ACCOUNTANT", "HEAD_OFFICE_MANAGER").contains(actor.role()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem đối với báo cáo tài chính.");
        financeAccess.role(actor, actor.role());
    }
    private static void requireAccountant(Identity actor) { if (actor==null || !"ACCOUNTANT".equals(actor.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Bạn không có quyền thực hiện đối với nghiệp vụ kế toán."); }
    private static LocalDate month(String value) { try { if (!value.matches("\\d{4}-\\d{2}")) throw new IllegalArgumentException(); return YearMonth.parse(value).atDay(1); } catch (RuntimeException e) { throw bad("Tháng báo cáo không hợp lệ."); } }
    private static LocalDate date(String value) { try { return LocalDate.parse(value); } catch (RuntimeException e) { throw bad("Ngày báo cáo không hợp lệ."); } }
    private static List<LocalDate> reportDateRange(String month, String from, String to) {
        if ((from == null || from.isBlank()) && (to == null || to.isBlank())) {
            if (month == null || month.isBlank()) throw bad("Cần chọn tháng hoặc khoảng ngày báo cáo.");
            LocalDate start = month(month);
            if (start.isAfter(LocalDate.now(FinancialPeriodService.ZONE).withDayOfMonth(1)))
                throw bad("Không thể xem báo cáo của tháng chưa diễn ra.");
            return List.of(start, start.plusMonths(1));
        }
        if (from == null || from.isBlank() || to == null || to.isBlank()) throw bad("Cần chọn đủ ngày bắt đầu và ngày kết thúc.");
        LocalDate start = date(from), end = date(to);
        if (start.isAfter(end)) throw bad("Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.");
        if (end.isAfter(LocalDate.now(FinancialPeriodService.ZONE))) throw bad("Không thể xem báo cáo đến ngày trong tương lai.");
        if (java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1 > 366) throw bad("Khoảng ngày báo cáo tối đa là 366 ngày.");
        return List.of(start, end.plusDays(1));
    }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    public record AdjustmentCommand(BigDecimal amountSigned, String reason) {}
    public record ClosePeriodCommand(boolean confirmed, String note) {}
    public record ResolveSePayExceptionCommand(String resolutionNote) {}
}
