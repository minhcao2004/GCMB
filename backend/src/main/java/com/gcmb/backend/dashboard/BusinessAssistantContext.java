package com.gcmb.backend.dashboard;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.exception.AuthFailure;
import com.gcmb.backend.finance.service.FinancialPeriodService;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

/** HuyDV: dữ liệu tổng hợp cho trợ lý QLCC; không đưa hồ sơ cá nhân hoặc tài khoản ngân hàng vào AI. */
@Service
public class BusinessAssistantContext {
  private final JdbcTemplate db;
  private final MonthlyPerformanceService performance;
  public BusinessAssistantContext(JdbcTemplate db, MonthlyPerformanceService performance) {
    this.db = db; this.performance = performance;
  }

  public void authorize(Identity actor) {
    if (actor == null || !"HEAD_OFFICE_MANAGER".equals(actor.role()) || !Boolean.TRUE.equals(db.queryForObject(
        "SELECT EXISTS(SELECT 1 FROM gcmb.user_account u JOIN gcmb.role r ON r.id=u.role_id "
          + "WHERE u.id=? AND u.status='ACTIVE' AND r.code='HEAD_OFFICE_MANAGER')", Boolean.class, actor.id())))
      throw new AuthFailure(403, "form", "Chỉ quản lý cấp cao đang hoạt động được sử dụng trợ lý kinh doanh.");
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  @SuppressWarnings("unchecked")
  public Map<String, Object> snapshot(Identity actor, String value, UUID branchId) {
    authorize(actor);
    YearMonth month;
    try { month = YearMonth.parse(value); }
    catch (RuntimeException failure) { throw new AuthFailure(400, "month", "Tháng phải có định dạng YYYY-MM."); }
    var today = LocalDate.now(FinancialPeriodService.ZONE);
    if (month.getYear() < 2000 || month.isAfter(YearMonth.from(today)))
      throw new AuthFailure(400, "month", "Chọn tháng từ năm 2000 đến tháng hiện tại.");
    var from = month.atDay(1);
    var to = month.plusMonths(1).atDay(1);
    var report = performance.report(actor, from, branchId);
    var branchRows = (List<Map<String, Object>>) report.get("results");
    var result = new LinkedHashMap<String, Object>();
    result.put("month", month.toString());
    result.put("branchId", branchId);
    result.put("generatedAt", Instant.now().toString());
    result.put("incompleteMonth", month.equals(YearMonth.from(today)));
    result.put("periodStatus", report.get("periodStatus"));
    result.put("branches", db.queryForList("SELECT id,name FROM gcmb.branch WHERE status='ACTIVE' ORDER BY name,id"));
    result.put("totals", report.get("totals"));
    result.put("previousTotals", report.get("previousTotals"));
    // Chỉ các cột số liệu được phép, không gửi nguyên hàng hoặc snapshot từ nghiệp vụ khác.
    var fields = Set.of("name", "code", "revenue", "expenses", "profit", "margin", "occupancyRate", "bookingCount", "roomCount");
    result.put("branchPerformance", branchRows.stream().map(row -> {
      var safe = new LinkedHashMap<String, Object>();
      row.forEach((key, val) -> { if (fields.contains(key)) safe.put(key, val); });
      return safe;
    }).toList());
    result.put("expenseGroups", report.get("expenseGroups"));
    result.put("roomInventory", db.queryForList("""
      SELECT b.name AS branch,rt.mode,count(*) AS rooms,
        count(*) FILTER (WHERE r.operating_status='UNDER_MAINTENANCE') AS maintenance
      FROM gcmb.room r JOIN gcmb.room_type rt ON rt.id=r.room_type_id JOIN gcmb.branch b ON b.id=r.branch_id
      WHERE b.status='ACTIVE' AND r.operating_status<>'INACTIVE' AND (?::uuid IS NULL OR b.id=?)
      GROUP BY b.id,b.name,rt.mode ORDER BY b.name,rt.mode
      """, branchId, branchId));
    result.put("roomUsage", db.queryForList("""
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
      """, to, from, branchId, branchId, to, from));
    result.put("roomBilledRevenue", db.queryForList("""
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
      """, branchId, branchId, from, to));
    result.put("openIncidents", db.queryForList("""
      SELECT b.name AS branch,count(*) AS incidents,count(*) FILTER (WHERE i.room_unusable) AS room_unusable
      FROM gcmb.asset_incident i JOIN gcmb.branch b ON b.id=i.branch_id
      WHERE b.status='ACTIVE' AND i.status NOT IN ('VERIFIED','CLOSED') AND (?::uuid IS NULL OR b.id=?)
      GROUP BY b.id,b.name ORDER BY b.name
      """, branchId, branchId));
    result.put("sources", List.of(
      Map.of("id", "S1", "title", "Báo cáo kinh doanh tháng đang chọn: totals, branchPerformance, expenseGroups"),
      Map.of("id", "S2", "title", "Báo cáo tháng trước: previousTotals"),
      Map.of("id", "S3", "title", "Phòng Cinema/Music Box: roomUsage và roomBilledRevenue trong tháng; roomInventory là hiện tại"),
      Map.of("id", "S4", "title", "Sự cố thiết bị chưa đóng tại thời điểm truy vấn: openIncidents")));
    result.put("definitions", List.of(
      "Đơn vị tiền là đồng Việt Nam. Dùng định nghĩa doanh thu/chi phí/lợi nhuận từ báo cáo kinh doanh, không coi cấp quỹ hoặc cọc đang giữ là doanh thu.",
      "Tháng hiện tại chưa đủ tháng: không so tỷ lệ tăng/giảm với trọn tháng trước như hai kỳ tương đương.",
      "Có thể tra cứu báo cáo các tháng khác bằng công cụ đọc báo cáo; dữ liệu đính kèm ban đầu không phải toàn bộ lịch sử trong database.",
      "room_line_amount là tổng dòng tiền phòng trên hóa đơn đã tất toán, chưa phân bổ giảm giá cấp hóa đơn; không đồng nhất với doanh thu thuần toàn cơ sở.",
      "occupied_hours là giờ phân bổ đã diễn ra, cắt trong tháng; không phải số khách hoặc tỷ lệ lấp đầy. Không suy ra nhu cầu thị trường từ số phòng.",
      "Danh mục phòng/sự cố là trạng thái hiện tại, không phải bản ghi lịch sử của tháng đang chọn.",
      "Phân nhóm Cinema/Music Box dùng loại phòng hiện tại; nếu phòng đã đổi loại, không xem đây là phân loại lịch sử được xác minh.",
      "Không có chi phí phân bổ riêng theo loại hình phòng; không suy ra lợi nhuận riêng Cinema/Music Box chỉ từ dòng tiền phòng.",
      "Không có khảo sát nhu cầu, chi phí chuyển đổi Cinema/Music Box, diện tích/cách âm và dự toán ROI được xác minh trong bộ dữ liệu này."));
    return result;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 30)
  public List<Map<String, Object>> catalog(Identity actor) {
    authorize(actor);
    var rows = db.queryForList("""
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
      """, LocalDate.now(FinancialPeriodService.ZONE));
    return rows.stream().map(row -> Map.<String, Object>of(
        "branchId", row.get("id").toString(), "name", row.get("name"), "status", row.get("status"),
        "availableMonths", row.get("months") == null ? List.of() : List.of(row.get("months").toString().split(",")))).toList();
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 30)
  @SuppressWarnings("unchecked")
  public Map<String, Object> reports(Identity actor, String start, String end, UUID branchId, boolean details) {
    var catalog = catalog(actor);
    if (branchId != null && catalog.stream().noneMatch(b -> b.get("branchId").equals(branchId.toString())))
      throw new AuthFailure(400, "branchId", "Không tìm thấy cơ sở cần tra cứu.");
    var available = catalog.stream().filter(b -> branchId == null || b.get("branchId").equals(branchId.toString()))
        .flatMap(b -> ((List<String>)b.get("availableMonths")).stream()).map(YearMonth::parse).distinct().sorted().toList();
    var today = LocalDate.now(FinancialPeriodService.ZONE);
    YearMonth from, to;
    try {
      from = start == null || start.isBlank() ? (available.isEmpty() ? YearMonth.from(today) : available.get(0)) : YearMonth.parse(start);
      to = end == null || end.isBlank() ? YearMonth.from(today) : YearMonth.parse(end);
    } catch (RuntimeException failure) { throw new AuthFailure(400, "month", "Tháng tra cứu phải có định dạng YYYY-MM."); }
    if (from.getYear() < 2000 || from.isAfter(to) || to.isAfter(YearMonth.from(today)))
      throw new AuthFailure(400, "month", "Khoảng tháng tra cứu không hợp lệ hoặc thuộc tương lai.");
    var periods = new ArrayList<Map<String, Object>>();
    var summaries = new LinkedHashMap<String, Map<String, Object>>();
    var metrics = List.of("revenue", "expenses", "profit", "billCount", "bookingCount");
    for (var month : available) {
      if (month.isBefore(from) || month.isAfter(to)) continue;
      var report = performance.assistantReport(actor, month.atDay(1), branchId);
      var period = new LinkedHashMap<String, Object>();
      period.put("month", month.toString()); period.put("incompleteMonth", month.equals(YearMonth.from(today)));
      period.put("periodStatus", report.get("periodStatus")); period.put("totals", report.get("totals"));
      var rows = (List<Map<String, Object>>)report.get("results");
      if (details || branchId != null) period.put("branchPerformance", rows.stream().map(row -> {
        var safe = new LinkedHashMap<String, Object>(); safe.put("name", row.get("name"));
        for (var metric : metrics) safe.put(metric, row.get(metric));
        safe.put("occupancyRate", row.get("occupancyRate")); safe.put("periodStatus", row.get("periodStatus"));
        return safe;
      }).toList());
      for (var row : rows) {
        var summary = summaries.computeIfAbsent(row.get("id").toString(), key -> {
          var item = new LinkedHashMap<String, Object>(); item.put("name", row.get("name")); return item;
        });
        for (var metric : metrics) {
          var before = (java.math.BigDecimal)summary.getOrDefault(metric, java.math.BigDecimal.ZERO);
          summary.put(metric, before.add(new java.math.BigDecimal(String.valueOf(row.get(metric)))));
        }
      }
      periods.add(period);
    }
    return new LinkedHashMap<>(Map.of("startMonth", from.toString(), "endMonth", to.toString(),
        "monthlyHistory", periods, "branchSummary", new ArrayList<>(summaries.values()), "hasData", !periods.isEmpty(),
        "notes", "Chỉ liệt kê tháng có hoạt động ghi nhận trong phạm vi; tháng không xuất hiện không có nghĩa database chưa được lưu. Không coi tháng dở dang tương đương tháng đầy đủ. Danh mục phòng và tỷ lệ lấp đầy dùng năng lực phòng hiện tại, không phải ảnh chụp lịch sử cấu hình phòng."));
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 30)
  public Map<String, Object> popularity(Identity actor, String kind, String start, String end, UUID branchId, int limit) {
    authorize(actor);
    if (!Set.of("ROOM_COMBO", "FNB_ITEM").contains(kind))
      throw new AuthFailure(400, "kind", "Chọn thống kê combo phòng hoặc món ăn, đồ uống.");
    if (limit < 1 || limit > 20) throw new AuthFailure(400, "limit", "Chỉ lấy từ 1 đến 20 mục đứng đầu.");
    var today = LocalDate.now(FinancialPeriodService.ZONE);
    YearMonth from, to;
    try {
      from = start == null || start.isBlank() ? YearMonth.from(today) : YearMonth.parse(start);
      to = end == null || end.isBlank() ? YearMonth.from(today) : YearMonth.parse(end);
    } catch (RuntimeException failure) { throw new AuthFailure(400, "month", "Tháng tra cứu phải có định dạng YYYY-MM."); }
    if (from.getYear() < 2000 || from.isAfter(to) || to.isAfter(YearMonth.from(today)))
      throw new AuthFailure(400, "month", "Khoảng tháng tra cứu không hợp lệ hoặc thuộc tương lai.");
    if (branchId != null && !Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM gcmb.branch WHERE id=?)", Boolean.class, branchId)))
      throw new AuthFailure(400, "branchId", "Không tìm thấy cơ sở cần tra cứu.");
    LocalDate until = to.plusMonths(1).atDay(1).isAfter(today.plusDays(1)) ? today.plusDays(1) : to.plusMonths(1).atDay(1);
    Object[] args = { from.atDay(1), until, branchId, branchId };
    Object[] rankedArgs = { from.atDay(1), until, branchId, branchId, limit };
    Map<String, Object> coverage;
    List<Map<String, Object>> ranking;
    String notes;
    if ("ROOM_COMBO".equals(kind)) {
      String scoped = """
        WITH scoped AS (
          SELECT b.id,b.status,b.room_type_id FROM gcmb.booking b JOIN gcmb.branch br ON br.id=b.branch_id
          WHERE b.status IN ('CONFIRMED','IN_HOUSE','COMPLETED')
            AND (b.planned_start AT TIME ZONE br.timezone)::date>=? AND (b.planned_start AT TIME ZONE br.timezone)::date<?
            AND (?::uuid IS NULL OR b.branch_id=?)
        )
        """;
      coverage = db.queryForMap(scoped + """
        SELECT count(*) AS "eligibleBookings",count(*) FILTER (WHERE EXISTS (
          SELECT 1 FROM gcmb.booking_charge c WHERE c.booking_id=scoped.id AND c.status='ACTIVE' AND c.kind='COMBO'
        )) AS "bookingsWithCombo" FROM scoped
        """, args);
      ranking = db.queryForList(scoped + """
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
        """, rankedArgs);
      notes = "Xếp theo số booking khác nhau có dòng combo còn hiệu lực, theo ngày dự kiến sử dụng đã đến; loại bản nháp, hủy và không đến. Một booking có nhiều combo khác nhau có thể được tính ở mỗi combo. Tỷ trọng là tỷ trọng lượt chọn combo, không phải toàn bộ booking. Gộp phiên bản cùng mã, tên và hạng phòng; đổi tên tách riêng. Tiền dòng combo đã tất toán chưa phân bổ giảm giá hoặc hoàn tiền, không phải doanh thu thuần. Không có dữ liệu đánh giá sở thích khách; chỉ đo mức độ được đặt. Hạng phòng/loại hình tham chiếu danh mục hiện tại.";
    } else {
      String scoped = """
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
      coverage = db.queryForMap(scoped + """
        SELECT count(*) FILTER (WHERE booking_combo_item_id IS NULL AND unit_price>0) AS "paidItemLines",
          COALESCE(sum(quantity) FILTER (WHERE booking_combo_item_id IS NOT NULL OR unit_price=0),0) AS "excludedGiftQuantity"
        FROM scoped
        """, args);
      ranking = db.queryForList(scoped + """
        , grouped AS (
          SELECT min(name_snapshot) AS "productName",min(category) AS category,sum(quantity) AS "soldQuantity",
            count(DISTINCT bill_id) AS "billCount",count(DISTINCT booking_id) AS "bookingCount",sum(amount) AS "settledLineAmount"
          FROM scoped WHERE booking_combo_item_id IS NULL AND unit_price>0
          GROUP BY lower(trim(name_snapshot)),lower(trim(category))
        )
        SELECT *,round("soldQuantity"*100.0/nullif(sum("soldQuantity") OVER (),0),2) AS "selectionSharePercent"
        FROM grouped ORDER BY "soldQuantity" DESC,"productName",category LIMIT ?
        """, rankedArgs);
      notes = "Xếp món ăn/đồ uống theo số lượng trên hóa đơn đã tất toán, theo ngày tất toán; chỉ đơn đã xác nhận hoặc phục vụ. Loại món tặng/miễn phí hoặc đã gồm trong combo và đơn bị hủy/từ chối/chờ xác nhận. Gộp theo tên món đã lưu và danh mục hiện tại; không gộp thành combo F&B nếu hệ thống chỉ lưu từng món. Giá trị dòng hóa đơn chưa phân bổ giảm giá hoặc hoàn tiền. Bán nhiều không đồng nghĩa khách đánh giá yêu thích nhất.";
    }
    return Map.ofEntries(Map.entry("kind", kind), Map.entry("startMonth", from.toString()), Map.entry("endMonth", to.toString()),
        Map.entry("asOfDate", until.minusDays(1).toString()), Map.entry("coverage", coverage), Map.entry("ranking", ranking),
        Map.entry("hasData", !ranking.isEmpty()), Map.entry("notes", notes + " Chỉ đọc booking, đơn hàng và hóa đơn vận hành; chưa hợp nhất các dòng nhập file chưa liên kết vào nghiệp vụ để tránh đếm trùng. Kết quả rỗng chỉ là chưa ghi nhận giao dịch phù hợp trong phạm vi tra cứu, không có nghĩa hệ thống không lưu chi tiết."));
  }
}
