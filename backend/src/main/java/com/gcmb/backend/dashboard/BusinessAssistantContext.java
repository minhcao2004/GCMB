package com.gcmb.backend.dashboard;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.exception.AuthFailure;
import com.gcmb.backend.dashboard.repository.BusinessAssistantRepository;
import com.gcmb.backend.finance.service.FinancialPeriodService;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

/** HuyDV: dữ liệu tổng hợp cho trợ lý QLCC; không đưa hồ sơ cá nhân hoặc tài khoản ngân hàng vào AI. */
@Service
public class BusinessAssistantContext {
  private final BusinessAssistantRepository businessAssistantRepository;
  private final MonthlyPerformanceService performance;

  public BusinessAssistantContext(BusinessAssistantRepository businessAssistantRepository, MonthlyPerformanceService performance) {
    this.businessAssistantRepository = businessAssistantRepository;
    this.performance = performance;
  }

  public void authorize(Identity actor) {
    if (actor == null || !"HEAD_OFFICE_MANAGER".equals(actor.role()) || !businessAssistantRepository.isActiveHeadOfficeManager(actor.id()))
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
    result.put("branches", businessAssistantRepository.findActiveBranches());
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
    result.put("roomInventory", businessAssistantRepository.roomInventory(branchId));
    result.put("roomUsage", businessAssistantRepository.roomUsage(from, to, branchId));
    result.put("roomBilledRevenue", businessAssistantRepository.roomBilledRevenue(from, to, branchId));
    result.put("openIncidents", businessAssistantRepository.openIncidentSummary(branchId));
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
    var rows = businessAssistantRepository.activityMonthsByBranch(LocalDate.now(FinancialPeriodService.ZONE));
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
    if (!Set.of("ROOM_COMBO", "FNB_ITEM", "BOOKING_CHANNEL").contains(kind))
      throw new AuthFailure(400, "kind", "Chọn thống kê combo phòng, món ăn/đồ uống hoặc nguồn khách đặt phòng.");
    if (limit < 1 || limit > 20) throw new AuthFailure(400, "limit", "Chỉ lấy từ 1 đến 20 mục đứng đầu.");
    var today = LocalDate.now(FinancialPeriodService.ZONE);
    YearMonth from, to;
    try {
      from = start == null || start.isBlank() ? YearMonth.from(today) : YearMonth.parse(start);
      to = end == null || end.isBlank() ? YearMonth.from(today) : YearMonth.parse(end);
    } catch (RuntimeException failure) { throw new AuthFailure(400, "month", "Tháng tra cứu phải có định dạng YYYY-MM."); }
    if (from.getYear() < 2000 || from.isAfter(to) || to.isAfter(YearMonth.from(today)))
      throw new AuthFailure(400, "month", "Khoảng tháng tra cứu không hợp lệ hoặc thuộc tương lai.");
    if (branchId != null && !businessAssistantRepository.branchExists(branchId))
      throw new AuthFailure(400, "branchId", "Không tìm thấy cơ sở cần tra cứu.");
    LocalDate until = to.plusMonths(1).atDay(1);
    if (!"BOOKING_CHANNEL".equals(kind) && until.isAfter(today.plusDays(1))) until = today.plusDays(1);
    Map<String, Object> coverage;
    List<Map<String, Object>> ranking;
    String notes;
    if ("ROOM_COMBO".equals(kind)) {
      coverage = businessAssistantRepository.popularityComboCoverage(from.atDay(1), until, branchId);
      ranking = businessAssistantRepository.popularityComboRanking(from.atDay(1), until, branchId, limit);
      notes = "Xếp theo số booking khác nhau có dòng combo còn hiệu lực, theo ngày dự kiến sử dụng đã đến; loại bản nháp, hủy và không đến. Một booking có nhiều combo khác nhau có thể được tính ở mỗi combo. Tỷ trọng là tỷ trọng lượt chọn combo, không phải toàn bộ booking. Gộp phiên bản cùng mã, tên và hạng phòng; đổi tên tách riêng. Tiền dòng combo đã tất toán chưa phân bổ giảm giá hoặc hoàn tiền, không phải doanh thu thuần. Không có dữ liệu đánh giá sở thích khách; chỉ đo mức độ được đặt. Hạng phòng/loại hình tham chiếu danh mục hiện tại.";
    } else if ("BOOKING_CHANNEL".equals(kind)) {
      coverage = businessAssistantRepository.popularityChannelCoverage(from.atDay(1), until, branchId);
      ranking = businessAssistantRepository.popularityChannelRanking(from.atDay(1), until, branchId, limit);
      notes = "Xếp nguồn khách theo số booking chưa hủy, tính theo ngày nhận phòng dự kiến trong múi giờ cơ sở, cùng cách đếm của biểu đồ Nguồn khách. Bao gồm cả booking đã đặt cho ngày sắp tới trong kỳ. Ngoài Facebook, TikTok, Zalo và Instagram, thống kê này còn có khách đến trực tiếp, điện thoại/khác của dữ liệu cũ và nhóm chưa xác định; vì vậy mẫu số tỷ trọng có thể khác biểu đồ chỉ bốn kênh mạng xã hội. Một booking tính đúng một lần, không phải số khách duy nhất hay mức hài lòng. Tỷ trọng chia cho toàn bộ booking trong phạm vi trước khi giới hạn số kênh. Nhóm chưa xác định không được coi là một kênh thu hút khách; nếu nhóm này đứng đầu phải nêu giới hạn và so sánh riêng các kênh đã biết. Không suy ra hiệu quả quảng cáo hoặc doanh thu từ số lượt đặt.";
    } else {
      coverage = businessAssistantRepository.popularityFnbItemCoverage(from.atDay(1), until, branchId);
      ranking = businessAssistantRepository.popularityFnbItemRanking(from.atDay(1), until, branchId, limit);
      notes = "Xếp món ăn/đồ uống theo số lượng trên hóa đơn đã tất toán, theo ngày tất toán; chỉ đơn đã xác nhận hoặc phục vụ. Loại món tặng/miễn phí hoặc đã gồm trong combo và đơn bị hủy/từ chối/chờ xác nhận. Gộp theo tên món đã lưu và danh mục hiện tại; không gộp thành combo F&B nếu hệ thống chỉ lưu từng món. Giá trị dòng hóa đơn chưa phân bổ giảm giá hoặc hoàn tiền. Bán nhiều không đồng nghĩa khách đánh giá yêu thích nhất.";
    }
    return Map.ofEntries(Map.entry("kind", kind), Map.entry("startMonth", from.toString()), Map.entry("endMonth", to.toString()),
        Map.entry("asOfDate", (until.minusDays(1).isAfter(today) ? today : until.minusDays(1)).toString()), Map.entry("coverage", coverage), Map.entry("ranking", ranking),
        Map.entry("hasData", !ranking.isEmpty()), Map.entry("notes", notes + " Chỉ đọc booking, đơn hàng và hóa đơn vận hành; chưa hợp nhất các dòng nhập file chưa liên kết vào nghiệp vụ để tránh đếm trùng. Kết quả rỗng chỉ là chưa ghi nhận giao dịch phù hợp trong phạm vi tra cứu, không có nghĩa hệ thống không lưu chi tiết."));
  }
}
