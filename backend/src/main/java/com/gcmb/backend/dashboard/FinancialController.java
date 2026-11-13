package com.gcmb.backend.dashboard;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.audit.service.MutationAudit;
import com.gcmb.backend.inventory.service.EquipmentAssetFileStorageService;
import com.gcmb.backend.inventory.service.EquipmentAssetFileStorageService.StoredFile;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/accounting")
public class FinancialController {
    private static final String PAID_EXPENSES_CTE = """
        WITH paid_expenses AS (
          SELECT 'EXPENSE'::text source_type,e.id record_id,e.class_snapshot cost_type,e.category_id,
            c.name category_name,e.branch_id,b.name branch_name,e.code expense_code,e.kind expense_kind,
            e.reason description,p.amount,p.paid_at,p.external_reference payment_reference,b.timezone
          FROM gcmb.expense_payment p JOIN gcmb.expense e ON e.id=p.expense_id
          JOIN gcmb.expense_category c ON c.id=e.category_id JOIN gcmb.branch b ON b.id=e.branch_id
          WHERE p.status='PAID'
          UNION ALL
          SELECT 'EXPENSE_REQUEST',r.id,r.category_type,r.category_id,c.name,r.branch_id,b.name,r.code,
            r.kind,r.description,r.amount,r.paid_at,r.paid_reference,b.timezone
          FROM gcmb.finance_expense_request r JOIN gcmb.finance_expense_category c ON c.id=r.category_id
          JOIN gcmb.branch b ON b.id=r.branch_id WHERE r.status='PAID'
        )
        """;
    private final JdbcTemplate db;
    private final MutationAudit mutationAudit;
    private final EquipmentAssetFileStorageService files;
    private final FinancialPeriodService periods;
    private final MonthlyPerformanceService performance;
    private final AccountingWorkbookService accountingWorkbook;
    private final FinanceAccess financeAccess;

    public FinancialController(JdbcTemplate db, MutationAudit mutationAudit, EquipmentAssetFileStorageService files,
                               FinancialPeriodService periods, MonthlyPerformanceService performance,
                               AccountingWorkbookService accountingWorkbook, FinanceAccess financeAccess) {
        this.db = db;
        this.mutationAudit = mutationAudit;
        this.files = files;
        this.periods = periods;
        this.performance = performance;
        this.accountingWorkbook = accountingWorkbook;
        this.financeAccess = financeAccess;
    }

    @GetMapping("/reports/monthly")
    @Transactional(readOnly = true)
    public Map<String, Object> monthlyReport(@AuthenticationPrincipal Identity actor, @RequestParam String month,
                                             @RequestParam(required = false) UUID branchId) {
        requireReportRole(actor);
        return performance.report(actor,month(month),branchId);
    }

    @GetMapping("/reports/financial-overview")
    @Transactional(readOnly = true)
    public Map<String, Object> financialOverview(@AuthenticationPrincipal Identity actor,
                                                  @RequestParam String from, @RequestParam String to,
                                                  @RequestParam(required = false) List<UUID> branchIds,
                                                  @RequestParam(defaultValue = "prev") String compare,
                                                  @RequestParam(defaultValue = "day") String granularity) {
        requireOverviewRole(actor);
        return performance.overview(actor, date(from), date(to), branchIds, compare, granularity);
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
        Map<String, Object> data = performance.overview(actor, start, end, branchIds, compare, granularity);
        byte[] bytes = "xlsx".equalsIgnoreCase(format) ? MonthlyPerformanceExport.overviewXlsx(data) : MonthlyPerformanceExport.overviewCsv(data);
        db.update("INSERT INTO gcmb.audit_event(actor_id,branch_id,action,entity_type,after_data) VALUES (?,NULL,'EXPORT_FINANCIAL_OVERVIEW','REPORT',?::jsonb)",
            actor.id(), String.format(java.util.Locale.ROOT,
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
        byte[] bytes=performance.export(actor,month(month),branchId,format);
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
                                                @RequestParam(defaultValue = "") String q) {
        requireReportRole(actor);
        List<LocalDate> range = reportDateRange(month, from, to);
        LocalDate start = range.get(0), endExclusive = range.get(1);
        String type = costType == null ? "" : costType.trim().toUpperCase(java.util.Locale.ROOT);
        String search = q == null ? "" : q.trim();
        if (!type.isEmpty() && !List.of("OPEX", "CAPEX").contains(type)) throw bad("Loại chi phí không hợp lệ.");
        if (search.length() > 100) throw bad("Từ khóa tìm kiếm chỉ được chứa tối đa 100 ký tự.");
        List<UUID> selectedBranches = performance.validateReportBranchIds(branchIds);
        if (selectedBranches.isEmpty() && branchId != null) performance.validateReportBranchIds(List.of(branchId));
        String branchFilter;
        List<Object> args = new java.util.ArrayList<>();
        args.add(start); args.add(endExclusive);
        if (!selectedBranches.isEmpty()) {
            branchFilter = " AND branch_id IN (" + String.join(",", java.util.Collections.nCopies(selectedBranches.size(), "?")) + ")";
            args.addAll(selectedBranches);
        } else {
            branchFilter = " AND (?::uuid IS NULL OR branch_id=?)";
            args.add(branchId); args.add(branchId);
        }
        branchFilter += " AND (?='' OR cost_type=?) AND (?::uuid IS NULL OR category_id=?)"
            + " AND (?='' OR concat_ws(' ',cost_type,category_name,branch_name,expense_code,description,payment_reference) ILIKE '%'||?||'%')";
        args.add(type); args.add(type); args.add(categoryId); args.add(categoryId); args.add(search); args.add(search);
        // BR-103: chỉ tính thanh toán PAID trong kỳ; loại chuyển quỹ nội bộ khỏi tập chi.
        // BR-107: dùng loại chi phí đã chốt và giữ OPEX/CAPEX ở các nhóm riêng.
        List<Map<String, Object>> rows = db.queryForList(PAID_EXPENSES_CTE + """
            SELECT cost_type "class",cost_type "costType",category_id "categoryId",category_name "categoryName",
              branch_id "branchId",branch_name "branchName",sum(amount) amount,count(*) "paymentCount"
            FROM paid_expenses
            WHERE (paid_at AT TIME ZONE timezone)::date>=? AND (paid_at AT TIME ZONE timezone)::date<?
            """ + branchFilter + """
            GROUP BY cost_type,category_id,category_name,branch_id,branch_name
            ORDER BY cost_type,category_name,branch_name
            """, args.toArray());
        List<Map<String, Object>> branches = db.queryForList("SELECT id,name FROM gcmb.branch ORDER BY name");
        List<Map<String, Object>> categories = db.queryForList("""
            SELECT id,name FROM gcmb.expense_category
            UNION SELECT id,name FROM gcmb.finance_expense_category
            ORDER BY name
            """);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("month", month); result.put("from", start.toString()); result.put("to", endExclusive.minusDays(1).toString());
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
                                                        @RequestParam(defaultValue = "") String q) {
        requireReportRole(actor);
        List<LocalDate> range = reportDateRange(month, from, to);
        LocalDate start = range.get(0), endExclusive = range.get(1);
        String type = costType == null ? "" : costType.trim().toUpperCase(java.util.Locale.ROOT);
        String search = q == null ? "" : q.trim();
        if (!List.of("OPEX", "CAPEX").contains(type)) throw bad("Loại chi phí không hợp lệ.");
        if (search.length() > 100) throw bad("Từ khóa tìm kiếm chỉ được chứa tối đa 100 ký tự.");
        List<Map<String, Object>> rows = db.queryForList(PAID_EXPENSES_CTE + """
            SELECT source_type "sourceType",record_id "expenseId",expense_code "expenseCode",expense_kind kind,
              cost_type "costType",category_name "categoryName",branch_name "branchName",
              (paid_at AT TIME ZONE timezone) "paidAt",amount,description,payment_reference "paymentReference"
            FROM paid_expenses
            WHERE (paid_at AT TIME ZONE timezone)::date>=? AND (paid_at AT TIME ZONE timezone)::date<?
              AND branch_id=? AND category_id=? AND cost_type=?
              AND (?='' OR concat_ws(' ',cost_type,category_name,branch_name,expense_code,description,payment_reference) ILIKE '%'||?||'%')
            ORDER BY paid_at DESC,expense_code
            """, start, endExclusive, branchId, categoryId, type, search, search);
        List<UUID> requestIds = new java.util.ArrayList<>();
        for (Map<String, Object> row : rows) {
            if ("EXPENSE_REQUEST".equals(row.get("sourceType"))) requestIds.add((UUID) row.get("expenseId"));
        }
        Map<UUID, List<Map<String, Object>>> attachmentsByRequest = new java.util.HashMap<>();
        if (!requestIds.isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(requestIds.size(), "?"));
            List<Map<String, Object>> attachments = db.queryForList("""
                SELECT id,owner_id "ownerId",kind,original_name name FROM gcmb.finance_attachment
                WHERE owner_type='EXPENSE_REQUEST' AND kind IN ('INVOICE','PAYMENT_PROOF') AND owner_id IN (
                """ + placeholders + ") ORDER BY created_at", requestIds.toArray());
            for (Map<String, Object> attachment : attachments) {
                UUID ownerId = (UUID) attachment.get("ownerId");
                attachmentsByRequest.computeIfAbsent(ownerId, ignored -> new java.util.ArrayList<>()).add(attachment);
            }
        }
        for (Map<String, Object> row : rows) {
            row.put("attachments", attachmentsByRequest.getOrDefault((UUID) row.get("expenseId"), List.of()));
        }
        return Map.of("month", month, "rows", rows);
    }

    @GetMapping("/dashboard-details")
    @Transactional(readOnly = true)
    public Map<String, Object> dashboardDetails(@AuthenticationPrincipal Identity actor,
                                                @RequestParam String month,
                                                @RequestParam(required = false) UUID branchId) {
        requireAccountant(actor);
        LocalDate start = month(month);
        List<Map<String, Object>> methods = db.queryForList("""
            WITH bills AS (
              SELECT b.id,b.net_amount,
                CASE WHEN COUNT(DISTINCT p.method)>1 THEN 'Kết hợp'
                     WHEN BOOL_OR(p.method='BANK_TRANSFER') THEN 'SePay' ELSE 'Tiền mặt' END AS method
              FROM gcmb.bill b JOIN gcmb.branch br ON br.id=b.branch_id
              JOIN gcmb.payment p ON p.bill_id=b.id AND p.purpose='BILL'
              WHERE b.status='SETTLED' AND (b.completed_at AT TIME ZONE br.timezone)::date>=?
                AND (b.completed_at AT TIME ZONE br.timezone)::date<?
                AND (CAST(? AS uuid) IS NULL OR b.branch_id=?)
              GROUP BY b.id,b.net_amount
            ) SELECT method AS label,SUM(net_amount) AS value FROM bills GROUP BY method ORDER BY method
            """, start, start.plusMonths(1), branchId, branchId);
        List<Map<String, Object>> exceptions = db.queryForList("""
            SELECT bt.id,bt.external_id AS "externalId",bt.occurred_at AS "occurredAt",bt.amount,
              bt.match_status AS status,bt.exception_reason AS "exceptionReason",bt.transfer_content AS reason,
              bt.resolution_status AS "resolutionStatus",bt.resolved_at AS "resolvedAt",u.username AS "resolvedBy",
              bt.resolution_note AS "resolutionNote",COALESCE(b.name,'Tài khoản chung') AS "branchName"
            FROM gcmb.bank_transaction bt JOIN gcmb.financial_account a ON a.id=bt.account_id
            LEFT JOIN gcmb.payment p ON p.bank_transaction_id=bt.id
            LEFT JOIN gcmb.branch b ON b.id=COALESCE(p.branch_id,a.branch_id)
            LEFT JOIN gcmb.user_account u ON u.id=bt.resolved_by
            WHERE bt.provider='SEPAY' AND bt.match_status='EXCEPTION' AND bt.occurred_at>=? AND bt.occurred_at<?
              AND (CAST(? AS uuid) IS NULL OR COALESCE(p.branch_id,a.branch_id)=?)
            ORDER BY bt.occurred_at DESC LIMIT 100
            """, Timestamp.from(start.atStartOfDay(FinancialPeriodService.ZONE).toInstant()),
                Timestamp.from(start.plusMonths(1).atStartOfDay(FinancialPeriodService.ZONE).toInstant()), branchId, branchId);
        return Map.of("paymentMethods", methods, "sepayExceptions", exceptions);
    }

    @PostMapping("/sepay-exceptions/{id}/resolve")
    public Map<String, Object> resolveSePayException(@AuthenticationPrincipal Identity actor, @PathVariable UUID id,
                                                      @RequestBody(required = false) ResolveSePayExceptionCommand command) {
        return periods.resolveSePayException(actor, id, command == null ? null : command.resolutionNote());
    }

    @GetMapping("/financial-periods")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> periods(@AuthenticationPrincipal Identity actor) {
        return periods.list(actor);
    }

    @GetMapping("/financial-periods/{month}/readiness")
    public Map<String, Object> periodReadiness(@AuthenticationPrincipal Identity actor, @PathVariable String month) {
        return periods.readiness(actor, month(month));
    }

    @PostMapping("/financial-periods/{month}/close")
    public Map<String, Object> closePeriod(@AuthenticationPrincipal Identity actor, @PathVariable String month,
                                           @RequestBody(required = false) ClosePeriodCommand command) {
        return periods.close(actor, month(month), command != null && command.confirmed(), command == null ? "" : command.note());
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
        return db.queryForList("""
            WITH transaction_rows AS (
              SELECT t.id,t.kind,t.booking_date "bookingDate",t.occurred_at "occurredAt",t.reason,
                p."month"::text "periodMonth",p.status "periodStatus",scope.branch_id "branchId",scope.branch_name "branchName",
                COALESCE(e.amount,0) "originalAmount",COALESCE(a.adjustmentAmount,0) "adjustmentAmount",
                COALESCE(e.amount,0)+COALESCE(a.adjustmentAmount,0) amount,
                COALESCE(e."entryCount",0) "entryCount",COALESCE(a."adjustmentCount",0) "adjustmentCount",
                COALESCE(a.adjustments::text,'[]') adjustments
              FROM gcmb.financial_transaction t JOIN gcmb.financial_period p ON p.id=t.period_id
              LEFT JOIN LATERAL (SELECT e.branch_id,b.name branch_name FROM gcmb.financial_entry e
                LEFT JOIN gcmb.branch b ON b.id=e.branch_id WHERE e.transaction_id=t.id ORDER BY e.id LIMIT 1) scope ON true
              LEFT JOIN LATERAL (SELECT sum(amount_signed) amount,count(*) "entryCount" FROM gcmb.financial_entry WHERE transaction_id=t.id) e ON true
              LEFT JOIN LATERAL (
                SELECT count(*) "adjustmentCount",COALESCE(sum(v.amount),0) adjustmentAmount,
                  json_agg(json_build_object('reason',x.reason,'createdBy',u.username,'createdAt',x.occurred_at,
                    'amount',v.amount,'periodMonth',xp."month"::text,'periodStatus',xp.status,'attachments',COALESCE(f.files,'[]'::json)) ORDER BY x.occurred_at DESC,x.id) adjustments
                FROM gcmb.financial_transaction x JOIN gcmb.financial_period xp ON xp.id=x.period_id
                LEFT JOIN gcmb.user_account u ON u.id=x.created_by
                LEFT JOIN LATERAL (SELECT sum(amount_signed) amount FROM gcmb.financial_entry WHERE transaction_id=x.id) v ON true
                LEFT JOIN LATERAL (SELECT json_agg(json_build_object('id',id,'name',original_name,'mimeType',mime_type)) files
                  FROM gcmb.finance_attachment WHERE owner_type='FINANCIAL_TRANSACTION' AND owner_id=x.id) f ON true
                WHERE x.adjusts_transaction_id=t.id
              ) a ON true
              WHERE t.booking_date>=? AND t.booking_date<?
              UNION ALL
              -- UC-120: paid requests are cash outflows even though they are not linked to the legacy expense ledger.
              SELECT r.id,'EXPENSE'::varchar(25),(r.paid_at AT TIME ZONE b.timezone)::date,r.paid_at,
                concat_ws(' · ',r.code,c.name,r.description),date_trunc('month',r.paid_at AT TIME ZONE b.timezone)::date::text,
                COALESCE(p.status,'OPEN'),r.branch_id,b.name,-r.amount,0::numeric,-r.amount,0::bigint,0::bigint,'[]'::text
              FROM gcmb.finance_expense_request r JOIN gcmb.branch b ON b.id=r.branch_id
              JOIN gcmb.finance_expense_category c ON c.id=r.category_id
              LEFT JOIN gcmb.financial_period p ON p."month"=date_trunc('month',r.paid_at AT TIME ZONE b.timezone)::date
              WHERE r.status='PAID' AND (r.paid_at AT TIME ZONE b.timezone)::date>=?
                AND (r.paid_at AT TIME ZONE b.timezone)::date<?
            )
            SELECT * FROM transaction_rows
            WHERE (?::uuid IS NULL OR "branchId"=?::uuid)
              AND (?='' OR kind=?)
              AND (?='' OR lower(concat_ws(' ',reason,kind,id::text,"branchName")) LIKE '%'||lower(?)||'%')
            ORDER BY "occurredAt" DESC,id
            """, start, start.plusMonths(1), start, start.plusMonths(1), branchId, branchId, kind.trim(), kind.trim(), q.trim(), q.trim());
    }

    @PostMapping(value="/transactions/{id}/adjustments", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> adjust(@AuthenticationPrincipal Identity actor, @PathVariable UUID id,
                                      @RequestPart("command") AdjustmentCommand input,
                                      @RequestPart(value="evidence", required=false) MultipartFile evidence) {
        requireAccountant(actor);
        String reason = input == null || input.reason() == null ? "" : input.reason().trim();
        if (reason.isEmpty() || reason.length() > 500) throw bad("Vui lòng nhập lý do điều chỉnh (tối đa 500 ký tự).");
        BigDecimal amount;
        try { amount = new BigDecimal(String.valueOf(input.amountSigned())); }
        catch (RuntimeException ex) { throw bad("Số tiền điều chỉnh không hợp lệ."); }
        if (amount.signum() == 0 || amount.stripTrailingZeros().scale() > 0 || amount.abs().compareTo(new BigDecimal("999999999999999999")) > 0) throw bad("Số tiền điều chỉnh phải là số nguyên VND khác 0.");
        StoredFile[] stored = new StoredFile[1];
        try {
            UUID adjustment = mutationAudit.record("ADJUST_FINANCIAL_TRANSACTION", "FINANCIAL_TRANSACTION", null, () -> {
                List<Map<String, Object>> locked = db.queryForList("""
                    SELECT t.kind,t.adjusts_transaction_id,t.booking_date,p.status period_status
                    FROM gcmb.financial_transaction t JOIN gcmb.financial_period p ON p.id=t.period_id
                    WHERE t.id=? FOR UPDATE OF t
                    """, id);
                if (locked.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch cần điều chỉnh.");
                Map<String, Object> original = locked.get(0);
                if ("ADJUSTMENT".equals(original.get("kind")) || original.get("adjusts_transaction_id") != null)
                    throw bad("Chỉ được điều chỉnh giao dịch gốc, không thể điều chỉnh một bút toán điều chỉnh.");
                List<Map<String, Object>> entries = db.queryForList("SELECT account_id,branch_id,amount_signed FROM gcmb.financial_entry WHERE transaction_id=? ORDER BY id", id);
                if (List.of("TRANSFER", "OPENING").contains(original.get("kind")) || entries.size() != 1)
                    throw bad("Chỉ có thể điều chỉnh giao dịch gốc có một dòng sổ tiền.");
                Map<String, Object> entry = entries.get(0);
                BigDecimal originalAmount = (BigDecimal) entry.get("amount_signed");
                BigDecimal previousAdjustments = db.queryForObject("""
                    SELECT COALESCE(sum(e.amount_signed),0) FROM gcmb.financial_transaction t
                    JOIN gcmb.financial_entry e ON e.transaction_id=t.id WHERE t.adjusts_transaction_id=?
                    """, BigDecimal.class, id);
                BigDecimal adjustedAmount = originalAmount.add(previousAdjustments).add(amount);
                if ((originalAmount.signum() > 0 && adjustedAmount.signum() < 0)
                    || (originalAmount.signum() < 0 && adjustedAmount.signum() > 0))
                    throw conflict("Tổng điều chỉnh vượt giá trị giao dịch gốc; số tiền sau điều chỉnh không được đổi chiều thu/chi.");

                LocalDate today = LocalDate.now(FinancialPeriodService.ZONE);
                LocalDate periodMonth = today.withDayOfMonth(1);
                db.update("INSERT INTO gcmb.financial_period(\"month\",status) VALUES (?,'OPEN') ON CONFLICT(\"month\") DO NOTHING", periodMonth);
                List<UUID> openPeriods = db.query("SELECT id FROM gcmb.financial_period WHERE \"month\"=? AND status='OPEN' FOR UPDATE", (rs, rowNum) -> rs.getObject(1, UUID.class), periodMonth);
                if (openPeriods.isEmpty()) throw conflict("Kỳ hiện tại đã khóa. Không thể ghi điều chỉnh vào kỳ đã chốt.");
                UUID newId = UUID.randomUUID();
                db.update("""
                    INSERT INTO gcmb.financial_transaction(id,period_id,booking_date,occurred_at,kind,adjusts_transaction_id,reason,created_by,idempotency_key)
                    VALUES (?,?,?,CURRENT_TIMESTAMP,'ADJUSTMENT',?,?,?,?)
                    """, newId, openPeriods.get(0), today, id, reason, actor.id(), newId.toString());
                db.update("INSERT INTO gcmb.financial_entry(transaction_id,account_id,branch_id,amount_signed) VALUES (?,?,?,?)",
                    newId, entry.get("account_id"), entry.get("branch_id"), amount);
                if (evidence != null && !evidence.isEmpty()) {
                    stored[0] = files.store(evidence, "thu-chi");
                    StoredFile file = stored[0];
                    db.update("""
                        INSERT INTO gcmb.finance_attachment(id,owner_type,owner_id,kind,object_key,original_name,mime_type,byte_size,sha256,created_by)
                        VALUES (?,'FINANCIAL_TRANSACTION',?,'ADJUSTMENT_EVIDENCE',?,?,?,?,?,?)
                        """, file.id(), newId, file.objectKey(), file.originalName(), file.mimeType(), file.byteSize(), file.sha256(), actor.id());
                }
                return newId;
            }, value -> value);
            return db.queryForMap("""
                SELECT t.id,t.kind,t.adjusts_transaction_id \"adjustsTransactionId\",t.reason,t.occurred_at \"occurredAt\",
                  p.\"month\"::text \"periodMonth\",p.status \"periodStatus\"
                FROM gcmb.financial_transaction t JOIN gcmb.financial_period p ON p.id=t.period_id WHERE t.id=?
                """, adjustment);
        } catch (RuntimeException failure) {
            if (stored[0] != null) files.deleteQuietly(stored[0].objectKey());
            throw failure;
        }
    }

    @GetMapping("/tax-data.csv")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal Identity actor, @RequestParam String month,
                                         @RequestParam(defaultValue = "ALL") String scope) {
        requireAccountant(actor);
        LocalDate start = month(month);
        if (!List.of("ALL", "INFLOW", "OUTFLOW").contains(scope)) throw bad("Phạm vi xuất dữ liệu không hợp lệ.");
        // UC-125: export the selected period and cash-flow scope without changing source transactions.
        List<Map<String, Object>> rows = db.queryForList("""
            SELECT t.id,t.adjusts_transaction_id,t.booking_date,t.occurred_at,t.kind,t.reason,b.code branch_code,a.code account_code,e.amount_signed
            FROM gcmb.financial_transaction t JOIN gcmb.financial_period p ON p.id=t.period_id
            JOIN gcmb.financial_entry e ON e.transaction_id=t.id JOIN gcmb.financial_account a ON a.id=e.account_id
            LEFT JOIN gcmb.branch b ON b.id=e.branch_id
            WHERE t.booking_date>=? AND t.booking_date<? AND (?='ALL' OR (?='INFLOW' AND e.amount_signed>0) OR (?='OUTFLOW' AND e.amount_signed<0))
            ORDER BY t.booking_date,t.occurred_at,t.id,e.account_id
            """, start, start.plusMonths(1), scope, scope, scope);
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
        return accountingWorkbook.preview(actor, month(month), branchIds);
    }

    @GetMapping("/reports/monthly-workbook.xlsx")
    @Transactional
    public ResponseEntity<byte[]> exportMonthlyWorkbook(@AuthenticationPrincipal Identity actor,
                                                         @RequestParam String month,
                                                         @RequestParam(required = false) String branchIds) {
        byte[] bytes = accountingWorkbook.export(actor, month(month), branchIds);
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
    private static String csv(Object value) { String s=value == null ? "" : value.toString(); return "\""+s.replace("\"","\"\"")+"\""; }
    private static String csvText(Object value) { String s=value == null ? "" : value.toString(); if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s="'"+s; return csv(s); }
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
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
    public record AdjustmentCommand(BigDecimal amountSigned, String reason) {}
    public record ClosePeriodCommand(boolean confirmed, String note) {}
    public record ResolveSePayExceptionCommand(String resolutionNote) {}
}
