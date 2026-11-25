package com.gcmb.backend.dashboard.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
// Truy vấn số liệu tổng hợp cho trợ lý kinh doanh của QLCC; chỉ trả cột tổng hợp, không có hồ sơ cá nhân.
public class BusinessAssistantRepository {

  private static final String COMBO_SCOPE = """
      WITH scoped AS (
        SELECT b.id,b.status,b.room_type_id FROM gcmb.booking b JOIN gcmb.branch br ON br.id=b.branch_id
        WHERE b.status IN ('CONFIRMED','IN_HOUSE','COMPLETED')
          AND (b.planned_start AT TIME ZONE br.timezone)::date>=? AND (b.planned_start AT TIME ZONE br.timezone)::date<?
          AND (?::uuid IS NULL OR b.branch_id=?)
      )
      """;

  private static final String CHANNEL_SCOPE = """
      WITH scoped AS (
        SELECT b.status,CASE WHEN upper(trim(b.channel)) IN
          ('FACEBOOK','TIKTOK','ZALO','INSTAGRAM','WALK_IN','PHONE','OTHER')
          THEN upper(trim(b.channel)) ELSE 'UNKNOWN' END AS channel
        FROM gcmb.booking b JOIN gcmb.branch br ON br.id=b.branch_id
        WHERE b.status<>'CANCELLED'
          AND b.planned_start >= (CAST(? AS timestamp) AT TIME ZONE br.timezone)
          AND b.planned_start < (CAST(? AS timestamp) AT TIME ZONE br.timezone)
          AND (?::uuid IS NULL OR b.branch_id=?)
      )
      """;

  private static final String FNB_ITEM_SCOPE = """
      WITH scoped AS (
        SELECT l.menu_item_id,l.name_snapshot,l.booking_combo_item_id,l.unit_price,
          line.quantity,line.amount,bill.id AS bill_id,o.booking_id,m.category
        FROM gcmb.bill_line line JOIN gcmb.bill bill ON bill.id=line.bill_id AND bill.status='SETTLED'
        JOIN gcmb.branch br ON br.id=bill.branch_id JOIN gcmb.order_line l ON l.id=line.order_line_id AND l.branch_id=bill.branch_id
        JOIN gcmb.fb_order o ON o.id=l.order_id AND o.branch_id=bill.branch_id AND o.booking_id=l.booking_id AND o.status IN ('ACCEPTED','SERVED')
        JOIN gcmb.booking b ON b.id=o.booking_id AND b.id=bill.booking_id AND b.branch_id=bill.branch_id AND b.status IN ('CONFIRMED','IN_HOUSE','COMPLETED')
        JOIN gcmb.menu_item m ON m.id=l.menu_item_id AND m.branch_id=bill.branch_id AND m.kind IN ('PACKAGED','PREPARED')
        WHERE (bill.completed_at AT TIME ZONE br.timezone)::date>=? AND (bill.completed_at AT TIME ZONE br.timezone)::date<?
          AND (?::uuid IS NULL OR bill.branch_id=?)
      )
      """;

  private final JdbcTemplate jdbcTemplate;

  public BusinessAssistantRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public boolean isActiveHeadOfficeManager(UUID userId) {
    return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
      "SELECT EXISTS(SELECT 1 FROM gcmb.user_account u JOIN gcmb.role r ON r.id=u.role_id "
        + "WHERE u.id=? AND u.status='ACTIVE' AND r.code='HEAD_OFFICE_MANAGER')", Boolean.class, userId));
  }

  public boolean branchExists(UUID branchId) {
    return Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT EXISTS(SELECT 1 FROM gcmb.branch WHERE id=?)", Boolean.class, branchId));
  }

  public List<Map<String, Object>> findActiveBranches() {
    return jdbcTemplate.queryForList("SELECT id,name FROM gcmb.branch WHERE status='ACTIVE' ORDER BY name,id");
  }

  public List<Map<String, Object>> roomInventory(UUID branchId) {
    return jdbcTemplate.queryForList("""
      SELECT b.name AS branch,rt.mode,count(*) AS rooms,
        count(*) FILTER (WHERE r.operating_status='UNDER_MAINTENANCE') AS maintenance
      FROM gcmb.room r JOIN gcmb.room_type rt ON rt.id=r.room_type_id JOIN gcmb.branch b ON b.id=r.branch_id
      WHERE b.status='ACTIVE' AND r.operating_status<>'INACTIVE' AND (?::uuid IS NULL OR b.id=?)
      GROUP BY b.id,b.name,rt.mode ORDER BY b.name,rt.mode
      """, branchId, branchId);
  }

  public List<Map<String, Object>> roomUsage(LocalDate from, LocalDate to, UUID branchId) {
    return jdbcTemplate.queryForList("""
      SELECT b.name AS branch,rt.mode,count(DISTINCT a.id) AS allocations,
        round(sum(greatest(0,extract(epoch FROM (
          least(COALESCE(a.actual_end,least(a.ends_at,CURRENT_TIMESTAMP)), (?::date::timestamp AT TIME ZONE b.timezone))
          - greatest(COALESCE(a.actual_start,a.starts_at), (?::date::timestamp AT TIME ZONE b.timezone))
        ))))/3600,2) AS occupied_hours
      FROM gcmb.room_allocation a JOIN gcmb.room r ON r.id=a.room_id
      JOIN gcmb.room_type rt ON rt.id=r.room_type_id JOIN gcmb.branch b ON b.id=r.branch_id
      WHERE b.status='ACTIVE' AND a.state IN ('OCCUPIED','FINISHED') AND (?::uuid IS NULL OR b.id=?)
        AND COALESCE(a.actual_start,a.starts_at)<(?::date::timestamp AT TIME ZONE b.timezone)
        AND COALESCE(a.actual_end,least(a.ends_at,CURRENT_TIMESTAMP))>(?::date::timestamp AT TIME ZONE b.timezone)
      GROUP BY b.id,b.name,rt.mode ORDER BY b.name,rt.mode
      """, to, from, branchId, branchId, to, from);
  }

  public List<Map<String, Object>> roomBilledRevenue(LocalDate from, LocalDate to, UUID branchId) {
    return jdbcTemplate.queryForList("""
      SELECT br.name AS branch,rt.mode,sum(l.amount) AS room_line_amount
      FROM gcmb.bill_line l JOIN gcmb.bill b ON b.id=l.bill_id
      JOIN gcmb.booking_charge c ON c.id=l.booking_charge_id
      JOIN gcmb.room_allocation a ON a.id=c.allocation_id JOIN gcmb.room r ON r.id=a.room_id
      JOIN gcmb.room_type rt ON rt.id=r.room_type_id JOIN gcmb.branch br ON br.id=b.branch_id
      WHERE br.status='ACTIVE' AND b.status='SETTLED' AND l.order_line_id IS NULL
        AND (?::uuid IS NULL OR br.id=?)
        AND (b.completed_at AT TIME ZONE br.timezone)::date>=?
        AND (b.completed_at AT TIME ZONE br.timezone)::date<?
      GROUP BY br.id,br.name,rt.mode ORDER BY br.name,rt.mode
      """, branchId, branchId, from, to);
  }

  public List<Map<String, Object>> openIncidentSummary(UUID branchId) {
    return jdbcTemplate.queryForList("""
      SELECT b.name AS branch,count(*) AS incidents,count(*) FILTER (WHERE i.room_unusable) AS room_unusable
      FROM gcmb.asset_incident i JOIN gcmb.branch b ON b.id=i.branch_id
      WHERE b.status='ACTIVE' AND i.status NOT IN ('VERIFIED','CLOSED') AND (?::uuid IS NULL OR b.id=?)
      GROUP BY b.id,b.name ORDER BY b.name
      """, branchId, branchId);
  }

  // Liệt kê các tháng có hoạt động ghi nhận của từng cơ sở, tính đến ngày asOf.
  public List<Map<String, Object>> activityMonthsByBranch(LocalDate asOf) {
    return jdbcTemplate.queryForList("""
      WITH activity AS (
        SELECT x.branch_id,(x.planned_start AT TIME ZONE b.timezone)::date AS activity_day FROM gcmb.booking x JOIN gcmb.branch b ON b.id=x.branch_id
        UNION ALL SELECT x.branch_id,(x.completed_at AT TIME ZONE b.timezone)::date FROM gcmb.bill x JOIN gcmb.branch b ON b.id=x.branch_id WHERE x.status='SETTLED'
        UNION ALL SELECT e.branch_id,(x.occurred_at AT TIME ZONE b.timezone)::date FROM gcmb.financial_transaction x JOIN gcmb.financial_entry e ON e.transaction_id=x.id JOIN gcmb.branch b ON b.id=e.branch_id WHERE x.kind<>'OPENING'
        UNION ALL SELECT e.branch_id,(p.paid_at AT TIME ZONE b.timezone)::date FROM gcmb.expense_payment p JOIN gcmb.expense e ON e.id=p.expense_id JOIN gcmb.branch b ON b.id=e.branch_id WHERE p.status='PAID'
        UNION ALL SELECT branch_id,period_month::date FROM gcmb.v_expense_recognition
        UNION ALL SELECT p.branch_id,(p.received_at AT TIME ZONE b.timezone)::date FROM gcmb.payment p JOIN gcmb.branch b ON b.id=p.branch_id
        UNION ALL SELECT x.branch_id,(x.paid_at AT TIME ZONE b.timezone)::date FROM gcmb.finance_expense_request x JOIN gcmb.branch b ON b.id=x.branch_id WHERE x.status='PAID'
        UNION ALL SELECT x.branch_id,(e.created_at AT TIME ZONE b.timezone)::date FROM gcmb.deposit_entry e JOIN gcmb.deposit d ON d.id=e.deposit_id JOIN gcmb.booking x ON x.id=d.booking_id JOIN gcmb.branch b ON b.id=x.branch_id WHERE e.kind='FORFEIT'
        UNION ALL SELECT a.branch_id,d.day::date FROM gcmb.room_allocation a JOIN gcmb.branch b ON b.id=a.branch_id
          CROSS JOIN LATERAL generate_series(date_trunc('month',COALESCE(a.actual_start,a.starts_at) AT TIME ZONE b.timezone),date_trunc('month',least(COALESCE(a.actual_end,a.ends_at),CURRENT_TIMESTAMP) AT TIME ZONE b.timezone),interval '1 month') d(day)
          WHERE a.state IN ('OCCUPIED','FINISHED')
      )
      SELECT b.id,b.name,b.status,string_agg(DISTINCT to_char(a.activity_day,'YYYY-MM'),',' ORDER BY to_char(a.activity_day,'YYYY-MM')) months
      FROM gcmb.branch b LEFT JOIN activity a ON a.branch_id=b.id AND a.activity_day<=?::date
      GROUP BY b.id,b.name,b.status ORDER BY b.name,b.id
      """, asOf);
  }

  public Map<String, Object> popularityComboCoverage(LocalDate from, LocalDate until, UUID branchId) {
    return jdbcTemplate.queryForMap(COMBO_SCOPE + """
        SELECT count(*) AS "eligibleBookings",count(*) FILTER (WHERE EXISTS (
          SELECT 1 FROM gcmb.booking_charge c WHERE c.booking_id=scoped.id AND c.status='ACTIVE' AND c.kind='COMBO'
        )) AS "bookingsWithCombo" FROM scoped
        """, from, until, branchId, branchId);
  }

  public List<Map<String, Object>> popularityComboRanking(LocalDate from, LocalDate until, UUID branchId, int limit) {
    return jdbcTemplate.queryForList(COMBO_SCOPE + """
        , grouped AS (
          SELECT COALESCE(NULLIF(c.pricing_snapshot->>'name',''),c.description,p.name,'Combo chưa rõ tên') AS "comboName",
            rt.name AS "roomTypeName",rt.mode,count(DISTINCT b.id) AS "bookingCount",
            count(DISTINCT b.id) FILTER (WHERE b.status='COMPLETED') AS "completedBookingCount",
            COALESCE(sum(l.amount) FILTER (WHERE bill.status='SETTLED'),0) AS "settledLineAmount"
          FROM scoped b JOIN gcmb.booking_charge c ON c.booking_id=b.id AND c.status='ACTIVE' AND c.kind='COMBO'
          LEFT JOIN gcmb.combo p ON p.id=c.combo_id
          LEFT JOIN gcmb.room_type rt ON rt.id=COALESCE(p.room_type_id,b.room_type_id)
          LEFT JOIN gcmb.bill_line l ON l.booking_charge_id=c.id LEFT JOIN gcmb.bill bill ON bill.id=l.bill_id
          GROUP BY COALESCE(NULLIF(c.pricing_snapshot->>'code',''),p.code,c.combo_id::text,c.description),
            COALESCE(NULLIF(c.pricing_snapshot->>'name',''),c.description,p.name,'Combo chưa rõ tên'),rt.id,rt.name,rt.mode
        )
        SELECT *,round("bookingCount"*100.0/nullif(sum("bookingCount") OVER (),0),2) AS "selectionSharePercent"
        FROM grouped ORDER BY "bookingCount" DESC,"comboName","roomTypeName",mode LIMIT ?
        """, from, until, branchId, branchId, limit);
  }

  public Map<String, Object> popularityChannelCoverage(LocalDate from, LocalDate until, UUID branchId) {
    return jdbcTemplate.queryForMap(CHANNEL_SCOPE + """
        SELECT count(*) AS "eligibleBookings",
          count(*) FILTER (WHERE channel<>'UNKNOWN') AS "bookingsWithKnownChannel",
          count(*) FILTER (WHERE channel='UNKNOWN') AS "bookingsWithoutKnownChannel" FROM scoped
        """, from, until, branchId, branchId);
  }

  public List<Map<String, Object>> popularityChannelRanking(LocalDate from, LocalDate until, UUID branchId, int limit) {
    return jdbcTemplate.queryForList(CHANNEL_SCOPE + """
        , grouped AS (
          SELECT CASE channel WHEN 'FACEBOOK' THEN 'Facebook' WHEN 'TIKTOK' THEN 'TikTok'
            WHEN 'ZALO' THEN 'Zalo' WHEN 'INSTAGRAM' THEN 'Instagram' WHEN 'WALK_IN' THEN 'Khách đến trực tiếp'
            WHEN 'PHONE' THEN 'Điện thoại' WHEN 'OTHER' THEN 'Khác' ELSE 'Chưa xác định' END AS "channelName",
            count(*) AS "bookingCount",count(*) FILTER (WHERE status='COMPLETED') AS "completedBookingCount"
          FROM scoped GROUP BY channel
        )
        SELECT *,round("bookingCount"*100.0/nullif(sum("bookingCount") OVER (),0),2) AS "bookingSharePercent"
        FROM grouped ORDER BY "bookingCount" DESC,"channelName" LIMIT ?
        """, from, until, branchId, branchId, limit);
  }

  public Map<String, Object> popularityFnbItemCoverage(LocalDate from, LocalDate until, UUID branchId) {
    return jdbcTemplate.queryForMap(FNB_ITEM_SCOPE + """
        SELECT count(*) FILTER (WHERE booking_combo_item_id IS NULL AND unit_price>0) AS "paidItemLines",
          COALESCE(sum(quantity) FILTER (WHERE booking_combo_item_id IS NOT NULL OR unit_price=0),0) AS "excludedGiftQuantity"
        FROM scoped
        """, from, until, branchId, branchId);
  }

  public List<Map<String, Object>> popularityFnbItemRanking(LocalDate from, LocalDate until, UUID branchId, int limit) {
    return jdbcTemplate.queryForList(FNB_ITEM_SCOPE + """
        , grouped AS (
          SELECT min(name_snapshot) AS "productName",min(category) AS category,sum(quantity) AS "soldQuantity",
            count(DISTINCT bill_id) AS "billCount",count(DISTINCT booking_id) AS "bookingCount",sum(amount) AS "settledLineAmount"
          FROM scoped WHERE booking_combo_item_id IS NULL AND unit_price>0
          GROUP BY lower(trim(name_snapshot)),lower(trim(category))
        )
        SELECT *,round("soldQuantity"*100.0/nullif(sum("soldQuantity") OVER (),0),2) AS "selectionSharePercent"
        FROM grouped ORDER BY "soldQuantity" DESC,"productName",category LIMIT ?
        """, from, until, branchId, branchId, limit);
  }
}
