package com.gcmb.backend.dashboard;

import com.gcmb.backend.auth.dto.Identity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MonthlyPerformanceService {
  private static final List<String> MONEY_METRICS = List.of("revenue", "billRevenue", "roomRevenue", "fnbRevenue", "overtimeRevenue", "otherRevenue", "expenses", "operatingExpense", "salary", "investment", "profit", "netCashFlow", "forfeitedDeposits");
  private static final List<String> COUNT_METRICS = List.of("billCount", "bookingCount", "completedBookings", "cancelledNoShowBookings", "expenseCount");
  private final JdbcTemplate db;

  public MonthlyPerformanceService(JdbcTemplate db) { this.db = db; }

  @Transactional(readOnly = true)
  public Map<String, Object> report(Identity actor, LocalDate month, UUID branchId) {
    requireScope(actor, branchId);
    var current = rows(month, branchId);
    var priorRows = rows(month.minusMonths(1), branchId);
    Map<UUID, Map<String, Object>> priorByBranch = new HashMap<>();
    for (var row : priorRows) priorByBranch.put((UUID) row.get("id"), row);
    for (var row : current) {
      var prior = priorByBranch.get((UUID) row.get("id"));
      for (String key : MONEY_METRICS) row.put("previous" + cap(key), prior == null ? BigDecimal.ZERO : prior.get(key));
      for (String key : COUNT_METRICS) row.put("previous" + cap(key), prior == null ? 0L : prior.get(key));
    }
    var totals = totals(current);
    var previousTotals = totals(priorRows);
    var changes = changes(totals, previousTotals);
    var revenueGroups = revenueGroups(month, branchId);
    var expenseGroups = expenseGroups(month, branchId);
    return Map.of("month", YearMonth.from(month).toString(), "periodStatus", current.stream().anyMatch(r -> "CLOSED".equals(r.get("periodStatus"))) ? "CLOSED" : "OPEN", "results", current, "totals", totals,
      "previousTotals", previousTotals, "changes", changes, "revenueGroups", revenueGroups,
      "expenseGroups", expenseGroups);
  }

  @Transactional
  public byte[] export(Identity actor, LocalDate month, UUID branchId, String format) {
    if (actor==null || !List.of("ACCOUNTANT","HEAD_OFFICE_MANAGER").contains(actor.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Bạn không có quyền xuất báo cáo hiệu quả kinh doanh.");
    if (!List.of("csv","xlsx").contains(format)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Định dạng xuất chỉ nhận csv hoặc xlsx.");
    var data=report(actor,month,branchId);
    @SuppressWarnings("unchecked") var source=(List<Map<String,Object>>)data.get("results");
    var rows=new ArrayList<Map<String,Object>>(source);
    if(branchId==null) {
      @SuppressWarnings("unchecked") var total=(Map<String,Object>)data.get("totals");
      var summary=new LinkedHashMap<String,Object>(total); summary.put("name","Toàn chuỗi"); summary.put("periodStatus",data.get("periodStatus")); rows.add(summary);
    }
    db.update("INSERT INTO gcmb.audit_event(actor_id,branch_id,action,entity_type,after_data) VALUES (?,?,?,'REPORT',?::jsonb)",
      actor.id(),branchId,"EXPORT_MONTHLY_PERFORMANCE",String.format(Locale.ROOT,"{\"month\":\"%s\",\"format\":\"%s\"}",YearMonth.from(month),format));
    return "xlsx".equals(format) ? MonthlyPerformanceExport.xlsx(rows) : MonthlyPerformanceExport.csv(rows);
  }

  public List<Map<String,Object>> dailyRevenue(LocalDate start, UUID branchId) {
    return db.queryForList("""
      WITH bills AS (SELECT b.id,b.branch_id,(b.completed_at AT TIME ZONE br.timezone)::date day,b.net_amount,
        COALESCE(sum(l.amount) FILTER (WHERE l.order_line_id IS NULL AND c.kind='ROOM'),0) room,
        COALESCE(sum(l.amount) FILTER (WHERE l.order_line_id IS NOT NULL),0) fnb,
        COALESCE(sum(l.amount) FILTER (WHERE l.order_line_id IS NULL AND c.kind='OVERTIME'),0) overtime
        FROM gcmb.bill b JOIN gcmb.branch br ON br.id=b.branch_id
        LEFT JOIN gcmb.bill_line l ON l.bill_id=b.id LEFT JOIN gcmb.booking_charge c ON c.id=l.booking_charge_id
        WHERE b.status='SETTLED' AND (b.completed_at AT TIME ZONE br.timezone)::date>=?
          AND (b.completed_at AT TIME ZONE br.timezone)::date<? AND (?::uuid IS NULL OR b.branch_id=?)
        GROUP BY b.id,b.branch_id,day,b.net_amount), forfeitures AS (
        SELECT bk.branch_id,(de.created_at AT TIME ZONE br.timezone)::date day,sum(de.amount) amount
        FROM gcmb.deposit_entry de JOIN gcmb.deposit d ON d.id=de.deposit_id
        JOIN gcmb.booking bk ON bk.id=d.booking_id JOIN gcmb.branch br ON br.id=bk.branch_id
        WHERE de.kind='FORFEIT' AND (de.created_at AT TIME ZONE br.timezone)::date>=?
          AND (de.created_at AT TIME ZONE br.timezone)::date<? AND (?::uuid IS NULL OR bk.branch_id=?)
        GROUP BY bk.branch_id,day)
      SELECT day date,sum(room) "roomRevenue",sum(fnb) "fnbRevenue",sum(overtime) "overtimeRevenue",
        sum(net_amount-room-fnb-overtime) "otherRevenue",sum(net_amount) "billRevenue",
        COALESCE((SELECT sum(f.amount) FROM forfeitures f WHERE f.day=bills.day),0) "forfeitedDeposits",
        sum(net_amount)+COALESCE((SELECT sum(f.amount) FROM forfeitures f WHERE f.day=bills.day),0) revenue
      FROM bills GROUP BY day
      UNION ALL
      SELECT f.day,0,0,0,0,0,sum(f.amount),sum(f.amount) FROM forfeitures f
      WHERE NOT EXISTS (SELECT 1 FROM bills WHERE bills.day=f.day) GROUP BY f.day
      ORDER BY date
      """,start,start.plusMonths(1),branchId,branchId,start,start.plusMonths(1),branchId,branchId);
  }

  private List<Map<String, Object>> rows(LocalDate start, UUID selected) {
    return db.queryForList("""
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
        SELECT r.branch_id,c.name,r.category_type,r.amount
        FROM gcmb.finance_expense_request r JOIN gcmb.finance_expense_category c ON c.id=r.category_id
        JOIN gcmb.branch b ON b.id=r.branch_id
        WHERE r.status='PAID' AND (r.paid_at AT TIME ZONE b.timezone)::date>=? AND (r.paid_at AT TIME ZONE b.timezone)::date<?
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

  private List<Map<String,Object>> revenueGroups(LocalDate start, UUID branchId) {
    return db.queryForList("""
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

  private List<Map<String,Object>> expenseGroups(LocalDate start, UUID branchId) {
    return db.queryForList("""
      SELECT category,sum(amount) amount,count(*) count FROM (
        SELECT e.branch_id,c.name category,p.amount FROM gcmb.expense_payment p JOIN gcmb.expense e ON e.id=p.expense_id
        JOIN gcmb.expense_category c ON c.id=e.category_id JOIN gcmb.branch b ON b.id=e.branch_id
        WHERE p.status='PAID' AND (p.paid_at AT TIME ZONE b.timezone)::date>=? AND (p.paid_at AT TIME ZONE b.timezone)::date<?
        UNION ALL SELECT r.branch_id,c.name,r.amount FROM gcmb.finance_expense_request r
        JOIN gcmb.finance_expense_category c ON c.id=r.category_id JOIN gcmb.branch b ON b.id=r.branch_id
        WHERE r.status='PAID' AND (r.paid_at AT TIME ZONE b.timezone)::date>=? AND (r.paid_at AT TIME ZONE b.timezone)::date<?
      ) x JOIN gcmb.branch b ON b.id=x.branch_id WHERE (?::uuid IS NULL OR b.id=?)
      GROUP BY category ORDER BY category
      """,start,start.plusMonths(1),start,start.plusMonths(1),branchId,branchId);
  }

  private Map<String,Object> totals(List<Map<String,Object>> rows) {
    var result = new LinkedHashMap<String,Object>();
    for (String key : MONEY_METRICS) result.put(key, BigDecimal.ZERO);
    for (String key : COUNT_METRICS) result.put(key, 0L);
    BigDecimal occupiedHours = BigDecimal.ZERO, sellableHours = BigDecimal.ZERO;
    for (var row : rows) {
      for (String key : MONEY_METRICS) result.put(key, ((BigDecimal)result.get(key)).add((BigDecimal)row.get(key)));
      for (String key : COUNT_METRICS) result.put(key, ((Number)result.get(key)).longValue()+((Number)row.get(key)).longValue());
      occupiedHours=occupiedHours.add((BigDecimal)row.get("occupiedRoomHours"));
      sellableHours=sellableHours.add((BigDecimal)row.get("sellableRoomHours"));
    }
    BigDecimal revenue=(BigDecimal)result.get("revenue"), profit=(BigDecimal)result.get("profit");
    result.put("margin", revenue.signum()>0 ? profit.multiply(BigDecimal.valueOf(100)).divide(revenue,2,RoundingMode.HALF_UP) : null);
    result.put("occupancyRate", sellableHours.signum()>0 ? occupiedHours.multiply(BigDecimal.valueOf(100)).divide(sellableHours,2,RoundingMode.HALF_UP) : null);
    result.put("branchCount",rows.size());
    result.put("lossBranches",rows.stream().filter(r -> ((BigDecimal)r.get("profit")).signum()<0).count());
    return result;
  }

  private Map<String,Object> changes(Map<String,Object> current, Map<String,Object> previous) {
    var result = new LinkedHashMap<String,Object>();
    var keys = new ArrayList<String>(); keys.addAll(MONEY_METRICS); keys.addAll(COUNT_METRICS); keys.addAll(List.of("margin","occupancyRate"));
    for (String key : keys) {
      BigDecimal before = previous.get(key) == null ? BigDecimal.ZERO : new BigDecimal(previous.get(key).toString());
      BigDecimal now = current.get(key) == null ? BigDecimal.ZERO : new BigDecimal(current.get(key).toString());
      result.put(key, before.signum()==0 ? null : now.subtract(before).multiply(BigDecimal.valueOf(100)).divide(before.abs(),2,RoundingMode.HALF_UP));
    }
    return result;
  }

  private void requireScope(Identity actor, UUID branchId) {
    if (actor == null || !List.of("ACCOUNTANT","HEAD_OFFICE_MANAGER","BRANCH_MANAGER").contains(actor.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Bạn không có quyền xem báo cáo hiệu quả kinh doanh.");
    if ("BRANCH_MANAGER".equals(actor.role()) && (actor.branchId()==null || !actor.branchId().equals(branchId)
      || !Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM gcmb.branch_manager_assignment WHERE user_id=? AND branch_id=?)",Boolean.class,actor.id(),actor.branchId()))))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Bạn không có quyền xem đối với cơ sở này.");
    if (branchId!=null && !Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM gcmb.branch WHERE id=? AND status='ACTIVE')",Boolean.class,branchId))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cơ sở đang chọn không hoạt động hoặc không tồn tại.");
  }

  private static String cap(String key) { return Character.toUpperCase(key.charAt(0))+key.substring(1); }
}
