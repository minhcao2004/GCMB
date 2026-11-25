package com.gcmb.backend.dashboard.repository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
// Truy vấn số liệu hiệu quả kinh doanh theo tháng, tổng quan tài chính và bút toán chi tiết cho báo cáo.
public class MonthlyPerformanceRepository {

  private final JdbcTemplate jdbcTemplate;

  public MonthlyPerformanceRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public boolean branchExists(UUID branchId) {
    return Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT EXISTS(SELECT 1 FROM gcmb.branch WHERE id=?)", Boolean.class, branchId));
  }

  public boolean activeBranchExists(UUID branchId) {
    return Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT EXISTS(SELECT 1 FROM gcmb.branch WHERE id=? AND status='ACTIVE')", Boolean.class, branchId));
  }

  public boolean isBranchManagerOf(UUID userId, UUID branchId) {
    return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
      "SELECT EXISTS(SELECT 1 FROM gcmb.branch_manager_assignment WHERE user_id=? AND branch_id=?)", Boolean.class, userId, branchId));
  }

  public List<Map<String, Object>> findBranchOptions() {
    return jdbcTemplate.queryForList("SELECT id,code,name,status FROM gcmb.branch ORDER BY name,id");
  }

  public List<UUID> findAllBranchIds() {
    return jdbcTemplate.queryForList("SELECT id FROM gcmb.branch", UUID.class);
  }

  public void insertReportAudit(UUID actorId, UUID branchId, String action, String afterDataJson) {
    jdbcTemplate.update("INSERT INTO gcmb.audit_event(actor_id,branch_id,action,entity_type,after_data) VALUES (?,?,?,'REPORT',?::jsonb)",
      actorId, branchId, action, afterDataJson);
  }

  public List<Map<String, Object>> overviewBranchRows(LocalDate from, LocalDate to, List<UUID> branchIds) {
    List<Object> args = new ArrayList<>();
    String ctes = overviewCtes(from, to, branchIds, args);
    return jdbcTemplate.queryForList(ctes + """
      , bills AS (SELECT branch_id,sum(amount) bill_revenue FROM bill_daily GROUP BY branch_id)
      , forfeitures AS (SELECT branch_id,sum(amount) forfeited FROM forfeiture_daily GROUP BY branch_id)
      , expenses AS (SELECT branch_id,
          sum(amount) FILTER (WHERE cost_type='OPEX') opex,
          sum(amount) FILTER (WHERE cost_type='CAPEX') capex
        FROM paid_expenses GROUP BY branch_id)
      , payroll AS (SELECT branch_id,sum(amount) salary FROM salary_daily GROUP BY branch_id)
      SELECT br.id,br.code,br.name,br.status,
        COALESCE(b.bill_revenue,0) "billRevenue",COALESCE(f.forfeited,0) "forfeitedDeposits",
        COALESCE(e.opex,0) "operatingExpense",COALESCE(p.salary,0) salary,COALESCE(e.capex,0) investment
      FROM selected_branches br
      LEFT JOIN bills b ON b.branch_id=br.id LEFT JOIN forfeitures f ON f.branch_id=br.id
      LEFT JOIN expenses e ON e.branch_id=br.id LEFT JOIN payroll p ON p.branch_id=br.id
      ORDER BY br.name,br.id
      """, args.toArray());
  }

  public List<Map<String, Object>> overviewCategories(LocalDate from, LocalDate to, List<UUID> branchIds) {
    List<Object> args = new ArrayList<>();
    String ctes = overviewCtes(from, to, branchIds, args);
    return jdbcTemplate.queryForList(ctes + """
      , items AS (
        SELECT category_id::text "categoryKey",category_name "categoryName",cost_type "costType",amount
        FROM paid_expenses WHERE cost_type='OPEX'
        UNION ALL SELECT 'PAYROLL','Lương và ứng lương','OPEX',amount FROM salary_daily
      )
      SELECT "categoryKey","categoryName","costType",sum(amount) amount
      FROM items GROUP BY "categoryKey","categoryName","costType" ORDER BY "categoryName"
      """, args.toArray());
  }

  public List<Map<String, Object>> overviewSeries(LocalDate from, LocalDate to, List<UUID> branchIds, String granularity) {
    List<Object> args = new ArrayList<>();
    String ctes = overviewCtes(from, to, branchIds, args);
    args.add(from); args.add(to); args.add(granularity);
    return jdbcTemplate.queryForList(ctes + """
      , calendar AS (SELECT generate_series(?::date,?::date,interval '1 day')::date report_date)
      , expenses_daily AS (SELECT branch_id,report_date,
          sum(amount) FILTER (WHERE cost_type='OPEX') opex,
          sum(amount) FILTER (WHERE cost_type='CAPEX') capex
        FROM paid_expenses GROUP BY branch_id,report_date)
      , daily AS (
        SELECT br.id branch_id,cal.report_date,
          COALESCE(b.amount,0) bill_revenue,COALESCE(f.amount,0) forfeited,
          COALESCE(e.opex,0) opex,COALESCE(e.capex,0) capex,COALESCE(p.amount,0) salary
        FROM selected_branches br CROSS JOIN calendar cal
        LEFT JOIN bill_daily b ON b.branch_id=br.id AND b.report_date=cal.report_date
        LEFT JOIN forfeiture_daily f ON f.branch_id=br.id AND f.report_date=cal.report_date
        LEFT JOIN expenses_daily e ON e.branch_id=br.id AND e.report_date=cal.report_date
        LEFT JOIN salary_daily p ON p.branch_id=br.id AND p.report_date=cal.report_date
      )
      SELECT date_trunc(?,report_date::timestamp)::date bucket,
        sum(bill_revenue) "billRevenue",sum(forfeited) "forfeitedDeposits",
        sum(bill_revenue+forfeited) revenue,sum(opex) "operatingExpense",sum(salary) salary,
        sum(opex+salary) expenses,sum(capex) investment,
        sum(bill_revenue+forfeited-opex-salary) profit
      FROM daily GROUP BY bucket ORDER BY bucket
      """, args.toArray());
  }

  public List<Map<String, Object>> overviewCategorySeries(LocalDate from, LocalDate to, List<UUID> branchIds, String granularity) {
    List<Object> args = new ArrayList<>();
    String ctes = overviewCtes(from, to, branchIds, args);
    args.add(granularity); args.add(granularity);
    return jdbcTemplate.queryForList(ctes + """
      , items AS (
        SELECT date_trunc(?,report_date::timestamp)::date bucket,category_id::text "categoryKey",category_name "categoryName",sum(amount) amount
        FROM paid_expenses WHERE cost_type='OPEX' GROUP BY bucket,category_id,category_name
        UNION ALL SELECT date_trunc(?,report_date::timestamp)::date bucket,'PAYROLL','Lương và ứng lương',sum(amount)
        FROM salary_daily GROUP BY bucket
      )
      SELECT bucket,"categoryKey","categoryName",amount FROM items ORDER BY bucket,"categoryName"
      """, args.toArray());
  }

  public List<Map<String, Object>> accountingEntries(YearMonth month, List<UUID> branchIds) {
    String branchFilter = branchIds == null || branchIds.isEmpty() ? "" : " AND e.branch_id IN (" + String.join(",", Collections.nCopies(branchIds.size(), "?")) + ")";
    var args = new ArrayList<Object>(List.of(month.atDay(1), month.plusMonths(1).atDay(1)));
    if (branchIds != null) args.addAll(branchIds);
    return jdbcTemplate.queryForList("""
      SELECT t.id transaction_id,e.id entry_id,t.payment_id,t.refund_id,t.expense_payment_id,
        t.salary_payment_id,t.salary_advance_id,t.fund_transfer_id,t.adjusts_transaction_id,
        COALESCE(t.payment_id,t.refund_id,t.expense_payment_id,t.salary_payment_id,
          t.salary_advance_id,t.fund_transfer_id)::text source_id,
        t.booking_date,t.occurred_at,t.kind,t.reason,COALESCE(b.name,'Toàn chuỗi') branch_name,b.code branch_code,
        COALESCE(pay.booking_id,refund.booking_id) booking_id,bk.code booking_code,bill.number bill_number,
        CASE WHEN e.amount_signed>0 THEN 'THU' ELSE 'CHI' END flow_type,
        COALESCE(CASE WHEN pay.id IS NOT NULL THEN pay.purpose
          WHEN refund.id IS NOT NULL THEN refund.kind
          WHEN ep.id IS NOT NULL THEN concat_ws(' · ',expense.class_snapshot,expense_category.name)
          WHEN t.salary_payment_id IS NOT NULL THEN 'Lương'
          WHEN t.salary_advance_id IS NOT NULL THEN 'Ứng lương'
          WHEN t.fund_transfer_id IS NOT NULL THEN 'Chuyển quỹ'
          WHEN t.adjusts_transaction_id IS NOT NULL THEN 'Điều chỉnh' END,t.kind) item_category,
        a.code account_code,e.amount_signed,pay.method payment_method,
        COALESCE(payment_request.reference_code,refund.external_reference,ep.external_reference,
          salary_payment.external_reference,salary_advance.external_reference,fund_transfer.external_reference) source_reference,
        (SELECT string_agg(fa.kind || ': ' || fa.original_name,'; ' ORDER BY fa.created_at,fa.id)
          FROM gcmb.finance_attachment fa
          WHERE fa.owner_type='FINANCIAL_TRANSACTION' AND fa.owner_id=t.id) documents
      FROM gcmb.financial_transaction t
      JOIN gcmb.financial_period fp ON fp.id=t.period_id
      JOIN gcmb.financial_entry e ON e.transaction_id=t.id
      JOIN gcmb.financial_account a ON a.id=e.account_id
      LEFT JOIN gcmb.branch b ON b.id=e.branch_id
      LEFT JOIN gcmb.payment pay ON pay.id=t.payment_id
      LEFT JOIN gcmb.payment_request payment_request ON payment_request.id=pay.request_id
      LEFT JOIN gcmb.bill bill ON bill.id=pay.bill_id
      LEFT JOIN gcmb.refund_request refund ON refund.id=t.refund_id
      LEFT JOIN gcmb.expense_payment ep ON ep.id=t.expense_payment_id
      LEFT JOIN gcmb.expense expense ON expense.id=ep.expense_id
      LEFT JOIN gcmb.expense_category expense_category ON expense_category.id=expense.category_id
      LEFT JOIN gcmb.salary_payment salary_payment ON salary_payment.id=t.salary_payment_id
      LEFT JOIN gcmb.salary_advance salary_advance ON salary_advance.id=t.salary_advance_id
      LEFT JOIN gcmb.fund_transfer fund_transfer ON fund_transfer.id=t.fund_transfer_id
      LEFT JOIN gcmb.booking bk ON bk.id=COALESCE(pay.booking_id,refund.booking_id)
      WHERE t.booking_date>=? AND t.booking_date<?
      """ + branchFilter + " ORDER BY t.booking_date,t.occurred_at,t.id,e.id", args.toArray());
  }

  public List<Map<String,Object>> dailyRevenue(LocalDate start, LocalDate end, UUID branchId) {
    return jdbcTemplate.queryForList("""
      WITH bills AS (SELECT b.id,b.branch_id,(b.completed_at AT TIME ZONE br.timezone)::date AS report_date,b.net_amount,
        COALESCE(sum(l.amount) FILTER (WHERE l.order_line_id IS NULL AND c.kind='ROOM'),0) room,
        COALESCE(sum(l.amount) FILTER (WHERE l.order_line_id IS NOT NULL),0) fnb,
        COALESCE(sum(l.amount) FILTER (WHERE l.order_line_id IS NULL AND c.kind='OVERTIME'),0) overtime
        FROM gcmb.bill b JOIN gcmb.branch br ON br.id=b.branch_id
        LEFT JOIN gcmb.bill_line l ON l.bill_id=b.id LEFT JOIN gcmb.booking_charge c ON c.id=l.booking_charge_id
        WHERE b.status='SETTLED' AND (b.completed_at AT TIME ZONE br.timezone)::date>=?
          AND (b.completed_at AT TIME ZONE br.timezone)::date<? AND (?::uuid IS NULL OR b.branch_id=?)
        GROUP BY b.id,b.branch_id,report_date,b.net_amount), forfeitures AS (
        SELECT bk.branch_id,(de.created_at AT TIME ZONE br.timezone)::date AS report_date,sum(de.amount) amount
        FROM gcmb.deposit_entry de JOIN gcmb.deposit d ON d.id=de.deposit_id
        JOIN gcmb.booking bk ON bk.id=d.booking_id JOIN gcmb.branch br ON br.id=bk.branch_id
        WHERE de.kind='FORFEIT' AND (de.created_at AT TIME ZONE br.timezone)::date>=?
          AND (de.created_at AT TIME ZONE br.timezone)::date<? AND (?::uuid IS NULL OR bk.branch_id=?)
        GROUP BY bk.branch_id,report_date)
      SELECT bills.report_date AS date,sum(room) "roomRevenue",sum(fnb) "fnbRevenue",sum(overtime) "overtimeRevenue",
        sum(net_amount-room-fnb-overtime) "otherRevenue",sum(net_amount) "billRevenue",count(*) "billCount",
        COALESCE((SELECT sum(f.amount) FROM forfeitures f WHERE f.report_date=bills.report_date),0) "forfeitedDeposits",
        sum(net_amount)+COALESCE((SELECT sum(f.amount) FROM forfeitures f WHERE f.report_date=bills.report_date),0) revenue
      FROM bills GROUP BY bills.report_date
      UNION ALL
      SELECT f.report_date,0,0,0,0,0,0,sum(f.amount),sum(f.amount) FROM forfeitures f
      WHERE NOT EXISTS (SELECT 1 FROM bills WHERE bills.report_date=f.report_date) GROUP BY f.report_date
      ORDER BY date
      """,start,end,branchId,branchId,start,end,branchId,branchId);
  }

  public List<Map<String, Object>> monthlyRows(LocalDate start, UUID selected) {
    return jdbcTemplate.queryForList("""
      WITH bill_lines AS (
        SELECT b.id,b.branch_id,b.net_amount,
          COALESCE(sum(l.amount) FILTER (WHERE l.order_line_id IS NULL AND c.kind='ROOM'),0) room_revenue,
          COALESCE(sum(l.amount) FILTER (WHERE l.order_line_id IS NOT NULL),0) fnb_revenue,
          COALESCE(sum(l.amount) FILTER (WHERE l.order_line_id IS NULL AND c.kind='OVERTIME'),0) overtime_revenue
        FROM gcmb.bill b LEFT JOIN gcmb.bill_line l ON l.bill_id=b.id
        LEFT JOIN gcmb.booking_charge c ON c.id=l.booking_charge_id
        WHERE b.status='SETTLED' AND (b.completed_at AT TIME ZONE (SELECT timezone FROM gcmb.branch WHERE id=b.branch_id))::date>=?
          AND (b.completed_at AT TIME ZONE (SELECT timezone FROM gcmb.branch WHERE id=b.branch_id))::date<?
        GROUP BY b.id,b.branch_id,b.net_amount
      ), revenue AS (
        SELECT branch_id,sum(net_amount) bill_revenue,count(*) bill_count,sum(room_revenue) room_revenue,
          sum(fnb_revenue) fnb_revenue,sum(overtime_revenue) overtime_revenue,
          sum(net_amount-room_revenue-fnb_revenue-overtime_revenue) other_revenue
        FROM bill_lines GROUP BY branch_id
      ), expense_rows AS (
        SELECT e.branch_id,c.name category,e.class_snapshot class,p.amount
        FROM gcmb.expense_payment p JOIN gcmb.expense e ON e.id=p.expense_id
        JOIN gcmb.expense_category c ON c.id=e.category_id JOIN gcmb.branch b ON b.id=e.branch_id
        WHERE p.status='PAID' AND (p.paid_at AT TIME ZONE b.timezone)::date>=? AND (p.paid_at AT TIME ZONE b.timezone)::date<?
        UNION ALL
        SELECT v.branch_id,c.name,v.category_type,v.amount
        FROM gcmb.v_expense_recognition v JOIN gcmb.finance_expense_category c ON c.id=v.category_id
        WHERE v.period_month>=? AND v.period_month<?
      ), cost AS (
        SELECT branch_id,sum(amount) FILTER (WHERE class='OPEX') opex,sum(amount) FILTER (WHERE class='CAPEX') capex,count(*) payments
        FROM expense_rows GROUP BY branch_id
      ), bookings AS (
        SELECT b.branch_id,count(*) FILTER (WHERE b.status<>'CANCELLED') booking_count,
          count(*) FILTER (WHERE b.status<>'CANCELLED' AND EXISTS (SELECT 1 FROM gcmb.bill x WHERE x.booking_id=b.id AND x.status='SETTLED')) completed,
          count(*) FILTER (WHERE b.status IN ('CANCELLED','NO_SHOW')) cancelled_no_show
        FROM gcmb.booking b JOIN gcmb.branch br ON br.id=b.branch_id
        WHERE (b.planned_start AT TIME ZONE br.timezone)::date>=? AND (b.planned_start AT TIME ZONE br.timezone)::date<?
        GROUP BY b.branch_id
      ), occupied AS (
        SELECT br.id branch_id,COALESCE(sum(greatest(0,extract(epoch FROM
          (least(COALESCE(a.actual_end,a.ends_at),((d.day::date+time '20:00') AT TIME ZONE br.timezone))-
           greatest(a.starts_at,((d.day::date+time '08:00') AT TIME ZONE br.timezone))))/3600)),0) occupied_hours
        FROM gcmb.branch br CROSS JOIN generate_series(?::date,(?::date-1),interval '1 day') d(day)
        LEFT JOIN gcmb.room_allocation a ON a.branch_id=br.id AND a.state IN ('OCCUPIED','FINISHED')
          AND a.starts_at<((d.day::date+time '20:00') AT TIME ZONE br.timezone)
          AND COALESCE(a.actual_end,a.ends_at)>((d.day::date+time '08:00') AT TIME ZONE br.timezone)
          AND EXISTS (SELECT 1 FROM gcmb.room r WHERE r.id=a.room_id AND r.operating_status NOT IN ('INACTIVE','UNDER_MAINTENANCE'))
        GROUP BY br.id
      ), room_capacity AS (
        SELECT branch_id,count(*) FILTER (WHERE operating_status NOT IN ('INACTIVE','UNDER_MAINTENANCE')) rooms
        FROM gcmb.room GROUP BY branch_id
      ), salary AS (
        SELECT fe.branch_id,sum(-fe.amount_signed) amount,count(*) payments FROM gcmb.financial_transaction ft
        JOIN gcmb.financial_entry fe ON fe.transaction_id=ft.id
        JOIN gcmb.branch b ON b.id=fe.branch_id
        WHERE ft.kind IN ('SALARY','ADVANCE') AND fe.amount_signed<0
          AND (ft.occurred_at AT TIME ZONE b.timezone)::date>=? AND (ft.occurred_at AT TIME ZONE b.timezone)::date<?
        GROUP BY fe.branch_id
      ), cash_movements AS (
        -- Include actual source payments missing from the ledger; linked records stay ledger-only to avoid duplicates.
        SELECT fe.branch_id,fe.amount_signed amount FROM gcmb.financial_entry fe
        JOIN gcmb.financial_transaction ft ON ft.id=fe.transaction_id JOIN gcmb.branch b ON b.id=fe.branch_id
        WHERE ft.kind<>'OPENING' AND (ft.occurred_at AT TIME ZONE b.timezone)::date>=? AND (ft.occurred_at AT TIME ZONE b.timezone)::date<?
        UNION ALL
        SELECT p.branch_id,p.amount FROM gcmb.payment p JOIN gcmb.branch b ON b.id=p.branch_id
        WHERE (p.received_at AT TIME ZONE b.timezone)::date>=? AND (p.received_at AT TIME ZONE b.timezone)::date<?
          AND NOT EXISTS (SELECT 1 FROM gcmb.checkout_cash_reclassification cr WHERE cr.payment_id=p.id)
          AND NOT EXISTS (SELECT 1 FROM gcmb.financial_transaction ft WHERE ft.payment_id=p.id)
        UNION ALL
        SELECT e.branch_id,-p.amount FROM gcmb.expense_payment p JOIN gcmb.expense e ON e.id=p.expense_id
        JOIN gcmb.branch b ON b.id=e.branch_id
        WHERE p.status='PAID' AND (p.paid_at AT TIME ZONE b.timezone)::date>=? AND (p.paid_at AT TIME ZONE b.timezone)::date<?
          AND NOT EXISTS (SELECT 1 FROM gcmb.financial_transaction ft WHERE ft.expense_payment_id=p.id)
        UNION ALL
        SELECT r.branch_id,-r.amount FROM gcmb.finance_expense_request r JOIN gcmb.branch b ON b.id=r.branch_id
        WHERE r.status='PAID' AND (r.paid_at AT TIME ZONE b.timezone)::date>=? AND (r.paid_at AT TIME ZONE b.timezone)::date<?
      ), cash AS (
        SELECT branch_id,sum(amount) net FROM cash_movements GROUP BY branch_id
      ), forfeiture AS (
        SELECT bk.branch_id,sum(de.amount) amount FROM gcmb.deposit_entry de
        JOIN gcmb.deposit d ON d.id=de.deposit_id JOIN gcmb.booking bk ON bk.id=d.booking_id
        JOIN gcmb.branch b ON b.id=bk.branch_id
        WHERE de.kind='FORFEIT' AND (de.created_at AT TIME ZONE b.timezone)::date>=? AND (de.created_at AT TIME ZONE b.timezone)::date<?
        GROUP BY bk.branch_id
      )
      SELECT b.id,b.code,b.name,b.status,b.timezone,COALESCE(rv.bill_revenue,0)+COALESCE(f.amount,0) revenue,
        COALESCE(rv.bill_revenue,0) "billRevenue",COALESCE(rv.bill_count,0) "billCount",
        COALESCE(rv.room_revenue,0) "roomRevenue",COALESCE(rv.fnb_revenue,0) "fnbRevenue",
        COALESCE(rv.overtime_revenue,0) "overtimeRevenue",COALESCE(rv.other_revenue,0) "otherRevenue",
        COALESCE(c.opex,0)+COALESCE(s.amount,0) expenses,COALESCE(c.opex,0) "operatingExpense",COALESCE(s.amount,0) salary,
        COALESCE(c.capex,0) investment,COALESCE(c.payments,0)+COALESCE(s.payments,0) "expenseCount",
        COALESCE(f.amount,0) "forfeitedDeposits",COALESCE(cf.net,0) "netCashFlow",
        COALESCE(rv.bill_revenue,0)+COALESCE(f.amount,0)-COALESCE(c.opex,0)-COALESCE(s.amount,0) profit,
        CASE WHEN COALESCE(rv.bill_revenue,0)+COALESCE(f.amount,0)>0 THEN
          (COALESCE(rv.bill_revenue,0)+COALESCE(f.amount,0)-COALESCE(c.opex,0)-COALESCE(s.amount,0))*100/(COALESCE(rv.bill_revenue,0)+COALESCE(f.amount,0)) END margin,
        COALESCE(bk.booking_count,0) "bookingCount",COALESCE(bk.completed,0) "completedBookings",
        COALESCE(bk.cancelled_no_show,0) "cancelledNoShowBookings",
        CASE WHEN COALESCE(rc.rooms,0)>0 THEN COALESCE(o.occupied_hours,0)/(rc.rooms*((?-?)::numeric)*12)*100 END "occupancyRate",
        COALESCE(o.occupied_hours,0) "occupiedRoomHours",COALESCE(rc.rooms,0)*((?-?)::numeric)*12 "sellableRoomHours",
        COALESCE(fp.status,'OPEN') "periodStatus"
      FROM gcmb.branch b LEFT JOIN revenue rv ON rv.branch_id=b.id LEFT JOIN cost c ON c.branch_id=b.id
      LEFT JOIN salary s ON s.branch_id=b.id LEFT JOIN cash cf ON cf.branch_id=b.id LEFT JOIN forfeiture f ON f.branch_id=b.id
      LEFT JOIN bookings bk ON bk.branch_id=b.id LEFT JOIN occupied o ON o.branch_id=b.id
      LEFT JOIN room_capacity rc ON rc.branch_id=b.id LEFT JOIN gcmb.financial_period fp ON fp.month=?::date
      WHERE (?::uuid IS NULL OR b.id=?::uuid) ORDER BY b.name,b.id
      """, start,start.plusMonths(1), // bills
      start,start.plusMonths(1), // legacy expenses
      start,start.plusMonths(1), // finance requests
      start,start.plusMonths(1), // bookings
      start,start.plusMonths(1), // occupancy days
      start,start.plusMonths(1), // payroll movements
      start,start.plusMonths(1), // posted cash ledger
      start,start.plusMonths(1), // received payments not posted to the ledger
      start,start.plusMonths(1), // legacy expense payments not posted to the ledger
      start,start.plusMonths(1), // finance request payments
      start,start.plusMonths(1), // forfeited deposits
      YearMonth.from(start).lengthOfMonth(), 0, // monthly occupancy rate
      YearMonth.from(start).lengthOfMonth(), 0, // available room-hours
      start.withDayOfMonth(1), selected, selected);
  }

  public List<Map<String,Object>> revenueGroups(LocalDate start, UUID branchId) {
    return jdbcTemplate.queryForList("""
      WITH lines AS (SELECT b.id,b.branch_id,b.net_amount,
        COALESCE(sum(l.amount) FILTER (WHERE l.order_line_id IS NULL AND c.kind='ROOM'),0) room,
        COALESCE(sum(l.amount) FILTER (WHERE l.order_line_id IS NOT NULL),0) fnb,
        COALESCE(sum(l.amount) FILTER (WHERE l.order_line_id IS NULL AND c.kind='OVERTIME'),0) overtime
        FROM gcmb.bill b LEFT JOIN gcmb.bill_line l ON l.bill_id=b.id LEFT JOIN gcmb.booking_charge c ON c.id=l.booking_charge_id
        JOIN gcmb.branch br ON br.id=b.branch_id WHERE b.status='SETTLED'
          AND (b.completed_at AT TIME ZONE br.timezone)::date>=? AND (b.completed_at AT TIME ZONE br.timezone)::date<?
          AND (?::uuid IS NULL OR b.branch_id=?) GROUP BY b.id,b.branch_id,b.net_amount), groups AS (
        SELECT l.branch_id,g.label,g.amount FROM lines l
        CROSS JOIN LATERAL (VALUES ('Tiền phòng',l.room),('F&B',l.fnb),('Phụ phí quá giờ',l.overtime),
          ('Khác (phụ phí/giảm giá)',l.net_amount-l.room-l.fnb-l.overtime)) g(label,amount)
        UNION ALL
        SELECT bk.branch_id,'Thu từ cọc tịch thu',sum(de.amount) FROM gcmb.deposit_entry de
        JOIN gcmb.deposit d ON d.id=de.deposit_id JOIN gcmb.booking bk ON bk.id=d.booking_id
        JOIN gcmb.branch br ON br.id=bk.branch_id
        WHERE de.kind='FORFEIT' AND (de.created_at AT TIME ZONE br.timezone)::date>=?
          AND (de.created_at AT TIME ZONE br.timezone)::date<? AND (?::uuid IS NULL OR bk.branch_id=?)
        GROUP BY bk.branch_id)
      SELECT b.name "branchName",g.label,sum(g.amount) amount FROM groups g JOIN gcmb.branch b ON b.id=g.branch_id
      WHERE g.amount<>0 GROUP BY b.name,g.label ORDER BY b.name,g.label
      """, start,start.plusMonths(1),branchId,branchId,start,start.plusMonths(1),branchId,branchId);
  }

  public List<Map<String,Object>> expenseGroups(LocalDate start, UUID branchId) {
    return jdbcTemplate.queryForList("""
      SELECT category,sum(amount) amount,count(*) count FROM (
        SELECT e.branch_id,c.name category,p.amount FROM gcmb.expense_payment p JOIN gcmb.expense e ON e.id=p.expense_id
        JOIN gcmb.expense_category c ON c.id=e.category_id JOIN gcmb.branch b ON b.id=e.branch_id
        WHERE p.status='PAID' AND (p.paid_at AT TIME ZONE b.timezone)::date>=? AND (p.paid_at AT TIME ZONE b.timezone)::date<?
        UNION ALL SELECT v.branch_id,c.name,v.amount FROM gcmb.v_expense_recognition v
        JOIN gcmb.finance_expense_category c ON c.id=v.category_id
        WHERE v.period_month>=? AND v.period_month<?
      ) x JOIN gcmb.branch b ON b.id=x.branch_id WHERE (?::uuid IS NULL OR b.id=?)
      GROUP BY category ORDER BY category
      """,start,start.plusMonths(1),start,start.plusMonths(1),branchId,branchId);
  }

  private String overviewCtes(LocalDate from, LocalDate to, List<UUID> branchIds, List<Object> args) {
    String branchFilter = "";
    if (!branchIds.isEmpty()) {
      branchFilter = " WHERE id IN (" + String.join(",", Collections.nCopies(branchIds.size(), "?")) + ")";
      args.addAll(branchIds);
    }
    LocalDate end = to.plusDays(1);
    for (int i = 0; i < 5; i++) { args.add(from); args.add(end); }
    return """
      WITH selected_branches AS (SELECT id,code,name,status,timezone FROM gcmb.branch%s),
      -- BR-102/108/211: completed bill net amounts and forfeited deposits form operating income; held deposits are excluded.
      bill_daily AS (
        SELECT b.branch_id,(b.completed_at AT TIME ZONE br.timezone)::date report_date,sum(b.net_amount) amount
        FROM gcmb.bill b JOIN selected_branches br ON br.id=b.branch_id
        WHERE b.status='SETTLED' AND (b.completed_at AT TIME ZONE br.timezone)::date>=?
          AND (b.completed_at AT TIME ZONE br.timezone)::date<? GROUP BY b.branch_id,report_date
      ), forfeiture_daily AS (
        SELECT bk.branch_id,(de.created_at AT TIME ZONE br.timezone)::date report_date,sum(de.amount) amount
        FROM gcmb.deposit_entry de JOIN gcmb.deposit d ON d.id=de.deposit_id
        JOIN gcmb.booking bk ON bk.id=d.booking_id JOIN selected_branches br ON br.id=bk.branch_id
        WHERE de.kind='FORFEIT' AND (de.created_at AT TIME ZONE br.timezone)::date>=?
          AND (de.created_at AT TIME ZONE br.timezone)::date<? GROUP BY bk.branch_id,report_date
      ), paid_expenses AS (
        -- BR-103/107: use allocated recognition for business performance; CAPEX remains separate from OPEX.
        SELECT e.branch_id,(p.paid_at AT TIME ZONE br.timezone)::date report_date,e.class_snapshot cost_type,
          e.category_id::text category_id,c.name category_name,p.amount
        FROM gcmb.expense_payment p JOIN gcmb.expense e ON e.id=p.expense_id
        JOIN gcmb.expense_category c ON c.id=e.category_id JOIN selected_branches br ON br.id=e.branch_id
        WHERE p.status='PAID' AND (p.paid_at AT TIME ZONE br.timezone)::date>=?
          AND (p.paid_at AT TIME ZONE br.timezone)::date<?
        UNION ALL
        SELECT v.branch_id,v.period_month,v.category_type,v.category_id::text,c.name,v.amount
        FROM gcmb.v_expense_recognition v JOIN gcmb.finance_expense_category c ON c.id=v.category_id
        JOIN selected_branches br ON br.id=v.branch_id
        WHERE v.period_month>=? AND v.period_month<?
      ), salary_daily AS (
        SELECT fe.branch_id,(ft.occurred_at AT TIME ZONE br.timezone)::date report_date,sum(-fe.amount_signed) amount
        FROM gcmb.financial_transaction ft JOIN gcmb.financial_entry fe ON fe.transaction_id=ft.id
        JOIN selected_branches br ON br.id=fe.branch_id
        WHERE ft.kind IN ('SALARY','ADVANCE') AND fe.amount_signed<0
          AND (ft.occurred_at AT TIME ZONE br.timezone)::date>=?
          AND (ft.occurred_at AT TIME ZONE br.timezone)::date<? GROUP BY fe.branch_id,report_date
      )
      """.formatted(branchFilter);
  }
}
