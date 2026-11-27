package com.gcmb.backend.dashboard.repository;

import com.gcmb.backend.inventory.service.EquipmentAssetFileStorageService.StoredFile;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.gcmb.backend.dashboard.ReportCache;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
// Truy vấn báo cáo kế toán: nguồn nhập, chi phí, sổ thu chi, điều chỉnh bút toán, SePay và dữ liệu thuế.
public class FinancialReportRepository {
    private static final String LEGACY_PAID_EXPENSE_ROWS = """
        SELECT 'EXPENSE'::text source_type,e.id record_id,e.class_snapshot cost_type,e.category_id,
          c.name category_name,e.branch_id,b.name branch_name,e.code expense_code,e.kind expense_kind,
          e.reason description,p.amount,p.paid_at,p.external_reference payment_reference,b.timezone,
          (p.paid_at AT TIME ZONE b.timezone)::date recognition_date,'LEGACY'::text origin
        FROM gcmb.expense_payment p JOIN gcmb.expense e ON e.id=p.expense_id
        JOIN gcmb.expense_category c ON c.id=e.category_id JOIN gcmb.branch b ON b.id=e.branch_id
        WHERE p.status='PAID'
        """;
    private static final String CASH_FINANCE_EXPENSE_ROWS = """
        SELECT 'EXPENSE_REQUEST'::text source_type,r.id record_id,r.category_type cost_type,r.category_id,
          c.name category_name,r.branch_id,b.name branch_name,r.code expense_code,r.kind expense_kind,
          r.description,r.amount,r.paid_at,r.paid_reference payment_reference,b.timezone,
          (r.paid_at AT TIME ZONE b.timezone)::date recognition_date,r.origin
        FROM gcmb.finance_expense_request r JOIN gcmb.finance_expense_category c ON c.id=r.category_id
        JOIN gcmb.branch b ON b.id=r.branch_id WHERE r.status='PAID'
        """;
    private static final String ALLOCATED_FINANCE_EXPENSE_ROWS = """
        SELECT 'EXPENSE_REQUEST'::text source_type,r.id record_id,v.category_type cost_type,r.category_id,
          c.name category_name,v.branch_id,b.name branch_name,r.code expense_code,r.kind expense_kind,
          r.description,v.amount,r.paid_at,r.paid_reference payment_reference,b.timezone,v.period_month recognition_date,r.origin
        FROM gcmb.v_expense_recognition v JOIN gcmb.finance_expense_request r ON r.id=v.request_id
        JOIN gcmb.finance_expense_category c ON c.id=v.category_id JOIN gcmb.branch b ON b.id=v.branch_id
        """;

    private static String paidExpensesCte(String basis) {
        String financeRows="CASH".equals(basis)?CASH_FINANCE_EXPENSE_ROWS:ALLOCATED_FINANCE_EXPENSE_ROWS;
        return "WITH paid_expenses AS (\n"+LEGACY_PAID_EXPENSE_ROWS+"\nUNION ALL\n"+financeRows+"\n)\n";
    }

    private final JdbcTemplate jdbcTemplate;
    private final ReportCache reportCache;

    public FinancialReportRepository(JdbcTemplate jdbcTemplate, ReportCache reportCache) {
        this.jdbcTemplate = jdbcTemplate;
        this.reportCache = reportCache;
    }

    // Dòng nhập từ file nguồn (KiotViet/booking) đã chuẩn hóa số tiền theo từng cột của file gốc.
    public List<Map<String, Object>> sourceImportRows(LocalDate start, LocalDate end, UUID branchId, String syntheticFilter) {
        return reportCache.get("FinancialReportRepository.sourceImportRows", () -> sourceImportRowsQuery(start, end, branchId, syntheticFilter), start, end, branchId, syntheticFilter);
    }

    private List<Map<String, Object>> sourceImportRowsQuery(LocalDate start, LocalDate end, UUID branchId, String syntheticFilter) {
        return jdbcTemplate.queryForList("""
        WITH imported AS (
          SELECT r.id,r.record_type "recordType",i.source_system "sourceSystem",r.business_date "businessDate",
            r.source_sheet "sourceSheet",r.source_row "sourceRow",COALESCE(r.source_key,'') "sourceKey",
            b.id "branchId",b.name "branchName",r.payload,
            CASE WHEN COALESCE(r.payload->>'Tổng sau giảm giá','') ~ '^-?[0-9]+([.][0-9]+)?$'
                 THEN (r.payload->>'Tổng sau giảm giá')::numeric ELSE 0::numeric END invoice_revenue,
            CASE WHEN COALESCE(r.payload->>'Khách đã trả','') ~ '^-?[0-9]+([.][0-9]+)?$'
                 THEN (r.payload->>'Khách đã trả')::numeric ELSE 0::numeric END invoice_paid,
            CASE WHEN COALESCE(r.payload->>'BILL KIOTVIET','') ~ '^-?[0-9]+([.][0-9]+)?$'
                 THEN (r.payload->>'BILL KIOTVIET')::numeric ELSE 0::numeric END booking_bill,
            CASE WHEN COALESCE(r.payload->>'TIỀN KHÁCH TT','') ~ '^-?[0-9]+([.][0-9]+)?$'
                 THEN (r.payload->>'TIỀN KHÁCH TT')::numeric ELSE 0::numeric END booking_paid,
            CASE WHEN COALESCE(r.payload->>'TIỀN MẶT','') ~ '^-?[0-9]+([.][0-9]+)?$'
                 THEN (r.payload->>'TIỀN MẶT')::numeric ELSE 0::numeric END cash_amount,
            CASE WHEN COALESCE(r.payload->>'CK(TINGBOX)','') ~ '^-?[0-9]+([.][0-9]+)?$'
                 THEN (r.payload->>'CK(TINGBOX)')::numeric ELSE 0::numeric END bank_amount
          FROM gcmb.source_import_record r
          JOIN gcmb.source_import_batch i ON i.id=r.batch_id
          JOIN gcmb.branch b ON b.id=r.branch_id
          WHERE r.business_date>=? AND r.business_date<=?
            AND (CAST(? AS uuid) IS NULL OR r.branch_id=?)
            AND (?='ALL' OR (?='SYNTHETIC_DEMO' AND i.source_system='SYNTHETIC_DEMO')
              OR (?='SOURCE_FILES' AND i.source_system<>'SYNTHETIC_DEMO'))
        )
        SELECT id,"recordType","sourceSystem","businessDate","sourceSheet","sourceRow","sourceKey",
          "branchId","branchName",
          COALESCE(NULLIF(payload->>'Mã hóa đơn',''),"sourceKey") "invoiceNumber",
          COALESCE(NULLIF(payload->>'PHÒNG ĐÃ ĐẶT',''), '') room,
          COALESCE(NULLIF(payload->>'PHƯƠNG THỨC THANH TOÁN',''), '') "paymentMethod",
          CASE WHEN "recordType"='KIOT_INVOICE' THEN invoice_revenue ELSE booking_bill END amount,
          CASE WHEN "recordType"='KIOT_INVOICE' THEN invoice_paid ELSE booking_paid END "paidAmount",
          cash_amount "cashAmount",bank_amount "bankAmount"
        FROM imported
        ORDER BY "businessDate" DESC,"branchName", "recordType", "sourceRow"
        """, start, end, branchId, branchId, syntheticFilter, syntheticFilter, syntheticFilter);
    }

    // Bill vận hành đã tất toán của các cơ sở chưa có dữ liệu KiotViet nhập trong khoảng ngày.
    public List<Map<String, Object>> operationalBillRows(LocalDate start, LocalDate end, UUID branchId) {
        return reportCache.get("FinancialReportRepository.operationalBillRows", () -> operationalBillRowsQuery(start, end, branchId), start, end, branchId);
    }

    private List<Map<String, Object>> operationalBillRowsQuery(LocalDate start, LocalDate end, UUID branchId) {
        return jdbcTemplate.queryForList("""
        WITH bill_payments AS (
          SELECT bill_id,SUM(amount) paid_amount,
            COUNT(DISTINCT method) method_count,BOOL_OR(method='CASH') has_cash,BOOL_OR(method='BANK_TRANSFER') has_bank,
            COALESCE(SUM(amount) FILTER (WHERE method='CASH'),0) cash_amount,
            COALESCE(SUM(amount) FILTER (WHERE method='BANK_TRANSFER'),0) bank_amount
          FROM gcmb.payment WHERE purpose='BILL' GROUP BY bill_id
        )
        SELECT b.id,'KIOT_INVOICE'::varchar AS "recordType",'OPERATIONAL'::varchar AS "sourceSystem",
          (b.completed_at AT TIME ZONE br.timezone)::date AS "businessDate",'BILL'::text AS "sourceSheet",
          row_number() OVER (ORDER BY b.completed_at DESC,b.number)::integer AS "sourceRow",
          b.number AS "sourceKey",br.id AS "branchId",br.name AS "branchName",b.number AS "invoiceNumber",
          ''::text AS room,
          CASE WHEN p.method_count>1 THEN 'Kết hợp' WHEN p.has_bank THEN 'Chuyển khoản'
               WHEN p.has_cash THEN 'Tiền mặt' ELSE '' END AS "paymentMethod",
          b.net_amount AS amount,COALESCE(p.paid_amount,0) AS "paidAmount",
          COALESCE(p.cash_amount,0) AS "cashAmount",COALESCE(p.bank_amount,0) AS "bankAmount"
        FROM gcmb.bill b JOIN gcmb.branch br ON br.id=b.branch_id
        LEFT JOIN bill_payments p ON p.bill_id=b.id
        WHERE b.status='SETTLED'
          AND (b.completed_at AT TIME ZONE br.timezone)::date>=?
          AND (b.completed_at AT TIME ZONE br.timezone)::date<=?
          AND (CAST(? AS uuid) IS NULL OR b.branch_id=?)
          AND NOT EXISTS (
            SELECT 1 FROM gcmb.source_import_record r
            WHERE r.branch_id=b.branch_id AND r.record_type='KIOT_INVOICE'
              AND r.business_date>=? AND r.business_date<=?
          )
        ORDER BY "businessDate" DESC,"branchName","sourceRow"
        """, start, end, branchId, branchId, start, end);
    }

    public List<Map<String, Object>> findBranchOptions() {
        return jdbcTemplate.queryForList("SELECT id,code,name,status FROM gcmb.branch ORDER BY name,id");
    }

    public void insertOverviewExportAudit(UUID actorId, String afterDataJson) {
        jdbcTemplate.update("INSERT INTO gcmb.audit_event(actor_id,branch_id,action,entity_type,after_data) VALUES (?,NULL,'EXPORT_FINANCIAL_OVERVIEW','REPORT',?::jsonb)",
            actorId, afterDataJson);
    }

    // Tổng chi phí theo loại, hạng mục và cơ sở; basis=CASH theo ngày thanh toán, ALLOCATION theo lịch phân bổ.
    public List<Map<String, Object>> expenseBreakdownRows(String basis, LocalDate start, LocalDate endExclusive, List<UUID> selectedBranches, UUID branchId, String type, UUID categoryId, String search) {
        return reportCache.get("FinancialReportRepository.expenseBreakdownRows", () -> expenseBreakdownRowsQuery(basis, start, endExclusive, selectedBranches, branchId, type, categoryId, search), basis, start, endExclusive, selectedBranches, branchId, type, categoryId, search);
    }

    private List<Map<String, Object>> expenseBreakdownRowsQuery(String basis, LocalDate start, LocalDate endExclusive,
            List<UUID> selectedBranches, UUID branchId, String type, UUID categoryId, String search) {
        String branchFilter;
        List<Object> args = new ArrayList<>();
        args.add(start); args.add(endExclusive);
        if (!selectedBranches.isEmpty()) {
            branchFilter = " AND branch_id IN (" + String.join(",", Collections.nCopies(selectedBranches.size(), "?")) + ")";
            args.addAll(selectedBranches);
        } else {
            branchFilter = " AND (?::uuid IS NULL OR branch_id=?)";
            args.add(branchId); args.add(branchId);
        }
        branchFilter += " AND (?='' OR cost_type=?) AND (?::uuid IS NULL OR category_id=?)"
            + " AND (?='' OR concat_ws(' ',cost_type,category_name,branch_name,expense_code,description,payment_reference) ILIKE '%'||?||'%')";
        args.add(type); args.add(type); args.add(categoryId); args.add(categoryId); args.add(search); args.add(search);
        return jdbcTemplate.queryForList(paidExpensesCte(basis) + """
        SELECT cost_type "class",cost_type "costType",category_id "categoryId",category_name "categoryName",
          branch_id "branchId",branch_name "branchName",sum(amount) amount,count(*) "paymentCount"
        FROM paid_expenses
        WHERE recognition_date>=? AND recognition_date<?
        """ + branchFilter + """
        GROUP BY cost_type,category_id,category_name,branch_id,branch_name
        ORDER BY cost_type,category_name,branch_name
        """, args.toArray());
    }

    public List<Map<String, Object>> findBranchNames() {
        return jdbcTemplate.queryForList("SELECT id,name FROM gcmb.branch ORDER BY name");
    }

    // Hạng mục chi của luồng cũ (expense_category) và luồng mới (finance_expense_category).
    public List<Map<String, Object>> findExpenseCategories() {
        return jdbcTemplate.queryForList("""
        SELECT id,name FROM gcmb.expense_category
        UNION SELECT id,name FROM gcmb.finance_expense_category
        ORDER BY name
        """);
    }

    public List<Map<String, Object>> expenseDetailRows(String basis, LocalDate start, LocalDate endExclusive, UUID branchId, UUID categoryId, String type, String search) {
        return reportCache.get("FinancialReportRepository.expenseDetailRows", () -> expenseDetailRowsQuery(basis, start, endExclusive, branchId, categoryId, type, search), basis, start, endExclusive, branchId, categoryId, type, search);
    }

    private List<Map<String, Object>> expenseDetailRowsQuery(String basis, LocalDate start, LocalDate endExclusive,
            UUID branchId, UUID categoryId, String type, String search) {
        return jdbcTemplate.queryForList(paidExpensesCte(basis) + """
        SELECT source_type "sourceType",record_id "expenseId",expense_code "expenseCode",expense_kind kind,
          cost_type "costType",category_name "categoryName",branch_name "branchName",
          recognition_date "recognitionDate",(paid_at AT TIME ZONE timezone) "paidAt",amount,description,
          payment_reference "paymentReference",origin
        FROM paid_expenses
        WHERE recognition_date>=? AND recognition_date<?
          AND branch_id=? AND category_id=? AND cost_type=?
          AND (?='' OR concat_ws(' ',cost_type,category_name,branch_name,expense_code,description,payment_reference) ILIKE '%'||?||'%')
        ORDER BY recognition_date DESC,paid_at DESC,expense_code
        """, start, endExclusive, branchId, categoryId, type, search, search);
    }

    // Hóa đơn và chứng từ thanh toán của các đề nghị chi trong chi tiết báo cáo chi phí.
    public List<Map<String, Object>> findRequestAttachments(Collection<UUID> requestIds) {
        String placeholders = String.join(",", Collections.nCopies(requestIds.size(), "?"));
        return jdbcTemplate.queryForList("""
        SELECT id,owner_id "ownerId",kind,original_name name FROM gcmb.finance_attachment
        WHERE owner_type='EXPENSE_REQUEST' AND kind IN ('INVOICE','PAYMENT_PROOF') AND owner_id IN (
        """ + placeholders + ") ORDER BY created_at", requestIds.toArray());
    }

    public List<Map<String, Object>> paymentMethodTotals(LocalDate start, LocalDate endExclusive, UUID branchId) {
        return reportCache.get("FinancialReportRepository.paymentMethodTotals", () -> paymentMethodTotalsQuery(start, endExclusive, branchId), start, endExclusive, branchId);
    }

    private List<Map<String, Object>> paymentMethodTotalsQuery(LocalDate start, LocalDate endExclusive, UUID branchId) {
        return jdbcTemplate.queryForList("""
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
        """, start, endExclusive, branchId, branchId);
    }

    public List<Map<String, Object>> sePayExceptions(Timestamp from, Timestamp toExclusive, UUID branchId) {
        return jdbcTemplate.queryForList("""
        SELECT bt.id,bt.external_id AS "externalId",bt.occurred_at AS "occurredAt",bt.amount,
          bt.match_status AS status,bt.exception_reason AS "exceptionReason",bt.transfer_content AS reason,
          bt.resolution_status AS "resolutionStatus",bt.resolved_at AS "resolvedAt",u.username AS "resolvedBy",
          bt.resolution_note AS "resolutionNote",COALESCE(b.name,'Tài khoản chung') AS "branchName"
        FROM gcmb.bank_transaction bt JOIN gcmb.financial_account a ON a.id=bt.account_id
        LEFT JOIN gcmb.payment p ON p.bank_transaction_id=bt.id
        LEFT JOIN gcmb.branch b ON b.id=COALESCE(p.branch_id,a.branch_id)
        LEFT JOIN gcmb.user_account u ON u.id=bt.resolved_by
        WHERE bt.provider='SEPAY' AND bt.match_status IN ('EXCEPTION','UNMATCHED') AND bt.occurred_at>=? AND bt.occurred_at<?
          AND (CAST(? AS uuid) IS NULL OR COALESCE(p.branch_id,a.branch_id)=?)
        ORDER BY bt.occurred_at DESC LIMIT 100
        """, from, toExclusive, branchId, branchId);
    }

    // Sổ thu chi tháng: giao dịch sổ cái kèm điều chỉnh, cộng các đề nghị chi luồng mới đã thanh toán (UC-120).
    public List<Map<String, Object>> transactionRows(LocalDate monthStart, UUID branchId, String kind, String search) {
        return reportCache.get("FinancialReportRepository.transactionRows", () -> transactionRowsQuery(monthStart, branchId, kind, search), monthStart, branchId, kind, search);
    }

    private List<Map<String, Object>> transactionRowsQuery(LocalDate monthStart, UUID branchId, String kind, String search) {
        LocalDate monthEnd = monthStart.plusMonths(1);
        return jdbcTemplate.queryForList("""
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
        """, monthStart, monthEnd, monthStart, monthEnd, branchId, branchId, kind, kind, search, search);
    }

    public List<Map<String, Object>> lockTransactionForAdjustment(UUID transactionId) {
        return jdbcTemplate.queryForList("""
        SELECT t.kind,t.adjusts_transaction_id,t.booking_date,p.status period_status
        FROM gcmb.financial_transaction t JOIN gcmb.financial_period p ON p.id=t.period_id
        WHERE t.id=? FOR UPDATE OF t
        """, transactionId);
    }

    public List<Map<String, Object>> findEntries(UUID transactionId) {
        return jdbcTemplate.queryForList("SELECT account_id,branch_id,amount_signed FROM gcmb.financial_entry WHERE transaction_id=? ORDER BY id", transactionId);
    }

    public BigDecimal sumAdjustments(UUID transactionId) {
        return jdbcTemplate.queryForObject("""
        SELECT COALESCE(sum(e.amount_signed),0) FROM gcmb.financial_transaction t
        JOIN gcmb.financial_entry e ON e.transaction_id=t.id WHERE t.adjusts_transaction_id=?
        """, BigDecimal.class, transactionId);
    }

    // Tạo kỳ của tháng nếu chưa có rồi khóa kỳ đó; trả rỗng khi kỳ đã chốt.
    public List<UUID> lockOpenPeriod(LocalDate periodMonth) {
        jdbcTemplate.update("INSERT INTO gcmb.financial_period(\"month\",status) VALUES (?,'OPEN') ON CONFLICT(\"month\") DO NOTHING", periodMonth);
        return jdbcTemplate.query("SELECT id FROM gcmb.financial_period WHERE \"month\"=? AND status='OPEN' FOR UPDATE",
            (resultSet, rowNumber) -> resultSet.getObject(1, UUID.class), periodMonth);
    }

    public void insertAdjustment(UUID adjustmentId, UUID periodId, LocalDate bookingDate, UUID originalId, String reason, UUID actorId) {
        jdbcTemplate.update("""
        INSERT INTO gcmb.financial_transaction(id,period_id,booking_date,occurred_at,kind,adjusts_transaction_id,reason,created_by,idempotency_key)
        VALUES (?,?,?,CURRENT_TIMESTAMP,'ADJUSTMENT',?,?,?,?)
        """, adjustmentId, periodId, bookingDate, originalId, reason, actorId, adjustmentId.toString());
    }

    public void insertEntry(UUID transactionId, Object accountId, Object branchId, BigDecimal amountSigned) {
        jdbcTemplate.update("INSERT INTO gcmb.financial_entry(transaction_id,account_id,branch_id,amount_signed) VALUES (?,?,?,?)",
            transactionId, accountId, branchId, amountSigned);
    }

    public void insertAdjustmentEvidence(StoredFile file, UUID transactionId, UUID actorId) {
        jdbcTemplate.update("""
        INSERT INTO gcmb.finance_attachment(id,owner_type,owner_id,kind,object_key,original_name,mime_type,byte_size,sha256,created_by)
        VALUES (?,'FINANCIAL_TRANSACTION',?,'ADJUSTMENT_EVIDENCE',?,?,?,?,?,?)
        """, file.id(), transactionId, file.objectKey(), file.originalName(), file.mimeType(), file.byteSize(), file.sha256(), actorId);
    }

    public Map<String, Object> findAdjustment(UUID adjustmentId) {
        return jdbcTemplate.queryForMap("""
        SELECT t.id,t.kind,t.adjusts_transaction_id \"adjustsTransactionId\",t.reason,t.occurred_at \"occurredAt\",
          p.\"month\"::text \"periodMonth\",p.status \"periodStatus\"
        FROM gcmb.financial_transaction t JOIN gcmb.financial_period p ON p.id=t.period_id WHERE t.id=?
        """, adjustmentId);
    }

    public List<Map<String, Object>> taxExportRows(LocalDate monthStart, String scope) {
        return jdbcTemplate.queryForList("""
        SELECT t.id,t.adjusts_transaction_id,t.booking_date,t.occurred_at,t.kind,t.reason,b.code branch_code,a.code account_code,e.amount_signed
        FROM gcmb.financial_transaction t JOIN gcmb.financial_period p ON p.id=t.period_id
        JOIN gcmb.financial_entry e ON e.transaction_id=t.id JOIN gcmb.financial_account a ON a.id=e.account_id
        LEFT JOIN gcmb.branch b ON b.id=e.branch_id
        WHERE t.booking_date>=? AND t.booking_date<? AND (?='ALL' OR (?='INFLOW' AND e.amount_signed>0) OR (?='OUTFLOW' AND e.amount_signed<0))
        ORDER BY t.booking_date,t.occurred_at,t.id,e.account_id
        """, monthStart, monthStart.plusMonths(1), scope, scope, scope);
    }
}
