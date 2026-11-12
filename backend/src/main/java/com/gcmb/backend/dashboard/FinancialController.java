package com.gcmb.backend.dashboard;

import com.gcmb.backend.auth.dto.Identity;
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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/accounting")
public class FinancialController {
    private final JdbcTemplate db;

    public FinancialController(JdbcTemplate db) { this.db = db; }

    @GetMapping("/reports/monthly")
    @Transactional(readOnly = true)
    public Map<String, Object> monthlyReport(@AuthenticationPrincipal Identity actor, @RequestParam String month,
                                             @RequestParam(required = false) UUID branchId) {
        requireReportRole(actor);
        LocalDate start = month(month);
        // BR-102/103/104/105/106/107/108/109/211: doanh thu bill, chi đã trả, cọc tịch thu riêng, dòng tiền từ sổ tiền.
        List<Map<String, Object>> rows = db.queryForList("""
            SELECT b.id,b.code,b.name,
              COALESCE(revenue.amount,0) revenue,COALESCE(revenue.bills,0) "billCount",
              COALESCE(cost.opex,0)+COALESCE(salary.amount,0) expenses,COALESCE(salary.amount,0) salary,COALESCE(cost.capex,0) investment,
              COALESCE(forfeiture.amount,0) "forfeitedDeposits",
              COALESCE(cash.net,0) "netCashFlow",
              COALESCE(revenue.amount,0)-COALESCE(cost.opex,0)-COALESCE(salary.amount,0) profit,
              CASE WHEN COALESCE(revenue.amount,0)>0 THEN
                (revenue.amount-COALESCE(cost.opex,0)-COALESCE(salary.amount,0))*100/revenue.amount ELSE NULL END margin
            FROM gcmb.branch b
            LEFT JOIN LATERAL (SELECT sum(x.net_amount) amount,count(*) bills FROM gcmb.bill x
              WHERE x.branch_id=b.id AND x.status='SETTLED'
                AND (x.completed_at AT TIME ZONE b.timezone)::date>=? AND (x.completed_at AT TIME ZONE b.timezone)::date<?) revenue ON true
            LEFT JOIN LATERAL (SELECT sum(p.amount) FILTER (WHERE e.class_snapshot='OPEX') opex,
                sum(p.amount) FILTER (WHERE e.class_snapshot='CAPEX') capex
              FROM gcmb.expense_payment p JOIN gcmb.expense e ON e.id=p.expense_id
              WHERE e.branch_id=b.id AND p.status='PAID'
                AND (p.paid_at AT TIME ZONE b.timezone)::date>=? AND (p.paid_at AT TIME ZONE b.timezone)::date<?) cost ON true
            LEFT JOIN LATERAL (SELECT sum(-fe.amount_signed) amount FROM gcmb.financial_transaction ft
              JOIN gcmb.financial_entry fe ON fe.transaction_id=ft.id
              WHERE ft.kind IN ('SALARY','ADVANCE') AND fe.branch_id=b.id AND fe.amount_signed<0
                AND (ft.occurred_at AT TIME ZONE b.timezone)::date>=? AND (ft.occurred_at AT TIME ZONE b.timezone)::date<?) salary ON true
            -- BR-108: báo cáo cọc tịch thu riêng; không cộng vào doanh thu bill.
            LEFT JOIN LATERAL (SELECT sum(de.amount) amount FROM gcmb.deposit_entry de
              JOIN gcmb.deposit d ON d.id=de.deposit_id JOIN gcmb.booking bk ON bk.id=d.booking_id
              WHERE bk.branch_id=b.id AND de.kind='FORFEIT'
                AND (de.created_at AT TIME ZONE b.timezone)::date>=? AND (de.created_at AT TIME ZONE b.timezone)::date<?) forfeiture ON true
            LEFT JOIN LATERAL (SELECT sum(fe.amount_signed) net FROM gcmb.financial_entry fe
              JOIN gcmb.financial_transaction ft ON ft.id=fe.transaction_id
              WHERE fe.branch_id=b.id AND (ft.occurred_at AT TIME ZONE b.timezone)::date>=?
                AND (ft.occurred_at AT TIME ZONE b.timezone)::date<?) cash ON true
            WHERE (?::uuid IS NULL OR b.id=?::uuid) ORDER BY b.name,b.id
            """, start, start.plusMonths(1), start, start.plusMonths(1), start, start.plusMonths(1), start, start.plusMonths(1), start, start.plusMonths(1), branchId, branchId);
        return Map.of("month", month, "results", rows, "totals", totals(rows));
    }

    @GetMapping("/reports/expense-breakdown")
    @Transactional(readOnly = true)
    public Map<String, Object> expenseBreakdown(@AuthenticationPrincipal Identity actor, @RequestParam String month,
                                                @RequestParam(required = false) UUID branchId) {
        requireReportRole(actor);
        LocalDate start = month(month);
        // BR-103/107: chỉ nhóm khoản chi đã trả và trình bày CAPEX tách riêng.
        List<Map<String, Object>> rows = db.queryForList("""
            SELECT e.class_snapshot class,c.name "categoryName",b.name "branchName",
              sum(p.amount) amount,count(*) "paymentCount"
            FROM gcmb.expense_payment p JOIN gcmb.expense e ON e.id=p.expense_id
            JOIN gcmb.expense_category c ON c.id=e.category_id JOIN gcmb.branch b ON b.id=e.branch_id
            WHERE p.status='PAID' AND (p.paid_at AT TIME ZONE b.timezone)::date>=?
              AND (p.paid_at AT TIME ZONE b.timezone)::date<? AND (?::uuid IS NULL OR b.id=?::uuid)
            GROUP BY e.class_snapshot,c.name,b.name ORDER BY e.class_snapshot,c.name,b.name
            """, start, start.plusMonths(1), branchId, branchId);
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
            SELECT bt.external_id AS "externalId",bt.occurred_at AS "occurredAt",bt.amount,
              bt.match_status AS status,bt.transfer_content AS reason,
              COALESCE(b.name,'Tài khoản chung') AS "branchName"
            FROM gcmb.bank_transaction bt JOIN gcmb.financial_account a ON a.id=bt.account_id
            LEFT JOIN gcmb.payment p ON p.bank_transaction_id=bt.id
            LEFT JOIN gcmb.branch b ON b.id=COALESCE(p.branch_id,a.branch_id)
            WHERE bt.provider='SEPAY' AND bt.match_status='EXCEPTION' AND bt.occurred_at>=? AND bt.occurred_at<?
              AND (CAST(? AS uuid) IS NULL OR COALESCE(p.branch_id,a.branch_id)=?)
            ORDER BY bt.occurred_at DESC LIMIT 100
            """, Timestamp.from(start.atStartOfDay(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant()),
                Timestamp.from(start.plusMonths(1).atStartOfDay(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant()), branchId, branchId);
        return Map.of("paymentMethods", methods, "sepayExceptions", exceptions);
    }

    @GetMapping("/financial-periods")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> periods(@AuthenticationPrincipal Identity actor) {
        requireAccountant(actor);
        return db.queryForList("""
            WITH months AS (
              SELECT "month" FROM gcmb.financial_period
              UNION SELECT date_trunc('month',business_date)::date FROM gcmb.daily_cash_closing
              UNION SELECT date_trunc('month',booking_date)::date FROM gcmb.financial_transaction
              UNION SELECT date_trunc('month',CURRENT_DATE)::date
            )
            SELECT p.id,m."month"::text AS "month",COALESCE(p.status,'OPEN') AS status,p.closed_at "closedAt",u.username "closedBy",
              count(c.id) FILTER (WHERE c.status<>'RECONCILED') "unreconciledClosings"
            FROM months m LEFT JOIN gcmb.financial_period p ON p."month"=m."month"
            LEFT JOIN gcmb.user_account u ON u.id=p.closed_by
            LEFT JOIN gcmb.daily_cash_closing c ON date_trunc('month',c.business_date)::date=m."month"
            GROUP BY p.id,m."month",p.status,p.closed_at,u.username ORDER BY m."month" DESC
            """);
    }

    @PostMapping("/financial-periods/{month}/close")
    @Transactional
    public Map<String, Object> closePeriod(@AuthenticationPrincipal Identity actor, @PathVariable String month,
                                           @RequestBody(required = false) Map<String, Object> confirmation) {
        requireAccountant(actor);
        LocalDate start = month(month);
        Boolean available = db.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM gcmb.financial_period WHERE "month"=?
              UNION SELECT 1 FROM gcmb.daily_cash_closing WHERE business_date>=? AND business_date<?
              UNION SELECT 1 FROM gcmb.financial_transaction WHERE booking_date>=? AND booking_date<?
              UNION SELECT 1 WHERE date_trunc('month',CURRENT_DATE)::date=?)
            """, Boolean.class, start, start, start.plusMonths(1), start, start.plusMonths(1), start);
        if (!Boolean.TRUE.equals(available)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu kỳ tài chính cần chốt.");
        db.update("INSERT INTO gcmb.financial_period(\"month\",status) VALUES (?,'OPEN') ON CONFLICT(\"month\") DO NOTHING", start);
        Map<String, Object> period = db.queryForMap("SELECT id,status FROM gcmb.financial_period WHERE \"month\"=? FOR UPDATE", start);
        if ("CLOSED".equals(period.get("status"))) throw conflict("Kỳ tài chính này đã được chốt.");
        Long unresolved = db.queryForObject("SELECT count(*) FROM gcmb.daily_cash_closing WHERE business_date>=? AND business_date<? AND status<>'RECONCILED'", Long.class, start, start.plusMonths(1));
        if (unresolved != null && unresolved > 0) throw conflict("Chưa thể chốt kỳ tài chính vì còn " + unresolved + " phiếu chốt quỹ chưa đối soát. Vui lòng hoàn tất đối soát tất cả phiếu trong kỳ."); // BR-99, MSG102.
        if (confirmation == null || !Boolean.TRUE.equals(confirmation.get("confirmed"))) throw bad("Vui lòng xác nhận khóa kỳ tài chính.");
        db.update("UPDATE gcmb.financial_period SET status='CLOSED',closed_by=?,closed_at=CURRENT_TIMESTAMP WHERE id=? AND status='OPEN'", actor.id(), period.get("id"));
        return db.queryForMap("SELECT p.id,p.\"month\"::text AS \"month\",p.status,p.closed_at \"closedAt\",u.username \"closedBy\" FROM gcmb.financial_period p LEFT JOIN gcmb.user_account u ON u.id=p.closed_by WHERE p.id=?", period.get("id"));
    }

    @GetMapping("/transactions")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> transactions(@AuthenticationPrincipal Identity actor, @RequestParam String month) {
        requireAccountant(actor);
        LocalDate start = month(month);
        return db.queryForList("""
            SELECT t.id,t.kind,t.booking_date "bookingDate",t.occurred_at "occurredAt",t.reason,
              COALESCE(e.amount,0) amount,COALESCE(e."entryCount",0) "entryCount",
              COALESCE(a."adjustmentCount",0) "adjustmentCount",COALESCE(a.adjustments::text,'[]') adjustments,
              p.status "periodStatus"
            FROM gcmb.financial_transaction t JOIN gcmb.financial_period p ON p.id=t.period_id
            LEFT JOIN LATERAL (SELECT sum(amount_signed) amount,count(*) "entryCount" FROM gcmb.financial_entry WHERE transaction_id=t.id) e ON true
            LEFT JOIN LATERAL (
              SELECT count(*) "adjustmentCount",
                json_agg(json_build_object('reason',x.reason,'createdBy',u.username,'createdAt',x.occurred_at,'amount',v.amount) ORDER BY x.occurred_at DESC) adjustments
              FROM gcmb.financial_transaction x LEFT JOIN gcmb.user_account u ON u.id=x.created_by
              LEFT JOIN LATERAL (SELECT sum(amount_signed) amount FROM gcmb.financial_entry WHERE transaction_id=x.id) v ON true
              WHERE x.adjusts_transaction_id=t.id
            ) a ON true
            WHERE t.booking_date>=? AND t.booking_date<? ORDER BY t.occurred_at DESC,t.id
            """, start, start.plusMonths(1));
    }

    @PostMapping("/transactions/{id}/adjustments")
    @Transactional
    public Map<String, Object> adjust(@AuthenticationPrincipal Identity actor, @PathVariable UUID id,
                                      @RequestBody(required = false) Map<String, Object> input) {
        requireAccountant(actor);
        String reason = input == null ? "" : String.valueOf(input.getOrDefault("reason", "")).trim();
        if (reason.isEmpty() || reason.length() > 500) throw bad("Vui lòng nhập lý do điều chỉnh (tối đa 500 ký tự).");
        BigDecimal amount;
        try { amount = new BigDecimal(String.valueOf(input.get("amountSigned"))); }
        catch (RuntimeException ex) { throw bad("Số tiền điều chỉnh không hợp lệ."); }
        if (amount.signum() == 0 || amount.stripTrailingZeros().scale() > 0 || amount.abs().compareTo(new BigDecimal("999999999999999999")) > 0) throw bad("Số tiền điều chỉnh phải là số nguyên VND khác 0.");
        List<Map<String, Object>> originals = db.queryForList("""
            SELECT t.kind,t.reason,t.booking_date,t.period_id,p.status period_status,
              count(e.id) entry_count,min(e.account_id::text) account_id,min(e.branch_id::text) branch_id
            FROM gcmb.financial_transaction t JOIN gcmb.financial_period p ON p.id=t.period_id
            LEFT JOIN gcmb.financial_entry e ON e.transaction_id=t.id WHERE t.id=?
            GROUP BY t.id,p.status
            """, id);
        if (originals.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch cần điều chỉnh.");
        Map<String, Object> original = originals.get(0);
        // shortcut: one-entry adjustments only; add multi-account corrections after their accounting rule is defined.
        if ("TRANSFER".equals(original.get("kind")) || "OPENING".equals(original.get("kind")) || "ADJUSTMENT".equals(original.get("kind")) || ((Number) original.get("entry_count")).intValue() != 1)
            throw bad("Chỉ có thể điều chỉnh giao dịch gốc có một dòng sổ tiền.");
        LocalDate today = LocalDate.now();
        LocalDate periodMonth = today.withDayOfMonth(1);
        db.update("INSERT INTO gcmb.financial_period(month,status) VALUES (?,'OPEN') ON CONFLICT(month) DO NOTHING", periodMonth);
        UUID adjustment = UUID.randomUUID();
        List<UUID> openPeriods = db.query("SELECT id FROM gcmb.financial_period WHERE month=? AND status='OPEN' FOR UPDATE", (rs, rowNum) -> rs.getObject(1, UUID.class), periodMonth);
        if (openPeriods.isEmpty()) throw conflict("Kỳ hiện tại đã khóa. Không thể ghi điều chỉnh vào kỳ đã chốt.");
        UUID periodId = openPeriods.get(0);
        String branchValue = (String) original.get("branch_id");
        UUID branchId = branchValue == null ? null : UUID.fromString(branchValue);
        db.update("""
            INSERT INTO gcmb.financial_transaction(id,period_id,booking_date,occurred_at,kind,adjusts_transaction_id,reason,created_by,idempotency_key)
            VALUES (?,?,?,CURRENT_TIMESTAMP,'ADJUSTMENT',?,?,?,?)
            """, adjustment, periodId, today, id, reason, actor.id(), adjustment.toString()); // BR-100/101.
        db.update("""
            INSERT INTO gcmb.financial_entry(transaction_id,account_id,branch_id,amount_signed)
            VALUES (?,?,?,?)
            """, adjustment, UUID.fromString((String) original.get("account_id")), branchId, amount);
        return db.queryForMap("SELECT id,kind,adjusts_transaction_id \"adjustsTransactionId\",reason,occurred_at \"occurredAt\" FROM gcmb.financial_transaction WHERE id=?", adjustment);
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
    private static void requireAccountant(Identity actor) { if (actor==null || !"ACCOUNTANT".equals(actor.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Bạn không có quyền thực hiện đối với nghiệp vụ kế toán."); }
    private static LocalDate month(String value) { try { if (!value.matches("\\d{4}-\\d{2}")) throw new IllegalArgumentException(); return YearMonth.parse(value).atDay(1); } catch (RuntimeException e) { throw bad("Tháng báo cáo không hợp lệ."); } }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
}
