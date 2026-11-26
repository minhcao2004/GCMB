package com.gcmb.backend.dashboard;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.dashboard.repository.MonthlyPerformanceRepository;
import com.gcmb.backend.finance.service.FinancialPeriodService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MonthlyPerformanceService {
  static final int MAX_REPORT_BRANCH_IDS = 50;
  private static final List<String> MONEY_METRICS = List.of("revenue", "billRevenue", "roomRevenue", "fnbRevenue", "overtimeRevenue", "otherRevenue", "expenses", "operatingExpense", "salary", "investment", "profit", "netCashFlow", "forfeitedDeposits");
  private static final List<String> COUNT_METRICS = List.of("billCount", "bookingCount", "completedBookings", "cancelledNoShowBookings", "expenseCount");
  private final MonthlyPerformanceRepository monthlyPerformanceRepository;

  public MonthlyPerformanceService(MonthlyPerformanceRepository monthlyPerformanceRepository) {
    this.monthlyPerformanceRepository = monthlyPerformanceRepository;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> report(Identity actor, LocalDate month, UUID branchId) {
    requireScope(actor, branchId);
    if (YearMonth.from(month).isAfter(YearMonth.now(FinancialPeriodService.ZONE)))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể xem báo cáo của tháng chưa diễn ra.");
    var current = monthlyPerformanceRepository.monthlyRows(month, branchId);
    var priorRows = monthlyPerformanceRepository.monthlyRows(month.minusMonths(1), branchId);
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
    var revenueGroups = monthlyPerformanceRepository.revenueGroups(month, branchId);
    var expenseGroups = monthlyPerformanceRepository.expenseGroups(month, branchId);
    return Map.of("month", YearMonth.from(month).toString(), "periodStatus", current.stream().anyMatch(r -> "CLOSED".equals(r.get("periodStatus"))) ? "CLOSED" : "OPEN", "results", current, "totals", totals,
      "previousTotals", previousTotals, "changes", changes, "revenueGroups", revenueGroups,
      "expenseGroups", expenseGroups);
  }

  /** Read-only assistant history uses the same accounting definitions, including inactive branches. */
  @Transactional(readOnly = true)
  public Map<String, Object> assistantReport(Identity actor, LocalDate month, UUID branchId) {
    if (actor == null || !"HEAD_OFFICE_MANAGER".equals(actor.role()))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ quản lý cấp cao được tra cứu lịch sử kinh doanh.");
    if (month == null || month.getDayOfMonth() != 1 || month.getYear() < 2000 || YearMonth.from(month).isAfter(YearMonth.now(FinancialPeriodService.ZONE)))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tháng tra cứu không hợp lệ.");
    if (branchId != null && !monthlyPerformanceRepository.branchExists(branchId))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không tìm thấy cơ sở cần tra cứu.");
    var current = monthlyPerformanceRepository.monthlyRows(month, branchId);
    return Map.of("month", YearMonth.from(month).toString(), "results", current, "totals", totals(current),
        "periodStatus", current.stream().anyMatch(row -> "CLOSED".equals(row.get("periodStatus"))) ? "CLOSED" : "OPEN");
  }

  @Transactional(readOnly = true)
  public Map<String, Object> overview(Identity actor, LocalDate from, LocalDate to, List<UUID> requestedBranchIds,
                                      String compare, String granularity) {
    if (actor == null || !List.of("ACCOUNTANT", "HEAD_OFFICE_MANAGER").contains(actor.role()))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem đối với báo cáo tài chính.");
    if (from == null || to == null || from.isAfter(to)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoảng ngày báo cáo không hợp lệ.");
    if (java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1 > 366)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoảng ngày báo cáo tối đa là 366 ngày.");
    if (to.isAfter(LocalDate.now(FinancialPeriodService.ZONE)))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể xem báo cáo đến ngày trong tương lai.");
    String comparison = compare == null ? "prev" : compare.toLowerCase(Locale.ROOT);
    String bucket = granularity == null ? "day" : granularity.toLowerCase(Locale.ROOT);
    if (!List.of("day", "week", "month").contains(bucket)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cách gom nhóm không hợp lệ.");
    DateRange priorRange = comparisonRange(from, to, comparison);
    List<UUID> branchIds = validateReportBranchIds(requestedBranchIds);
    LocalDate comparisonFrom = priorRange.from();
    LocalDate comparisonTo = priorRange.to();
    Map<String, Object> current = readOverview(from, to, branchIds, bucket);
    Map<String, Object> previous = readOverview(comparisonFrom, comparisonTo, branchIds, bucket);
    @SuppressWarnings("unchecked") List<Map<String, Object>> currentBranches = (List<Map<String, Object>>) current.get("branches");
    @SuppressWarnings("unchecked") List<Map<String, Object>> previousBranches = (List<Map<String, Object>>) previous.get("branches");
    @SuppressWarnings("unchecked") List<Map<String, Object>> currentCategories = (List<Map<String, Object>>) current.get("categories");
    @SuppressWarnings("unchecked") List<Map<String, Object>> previousCategories = (List<Map<String, Object>>) previous.get("categories");
    @SuppressWarnings("unchecked") List<Map<String, Object>> currentSeries = (List<Map<String, Object>>) current.get("series");
    @SuppressWarnings("unchecked") List<Map<String, Object>> previousSeries = (List<Map<String, Object>>) previous.get("series");
    @SuppressWarnings("unchecked") List<Map<String, Object>> currentCategorySeries = (List<Map<String, Object>>) current.get("categorySeries");
    @SuppressWarnings("unchecked") List<Map<String, Object>> previousCategorySeries = (List<Map<String, Object>>) previous.get("categorySeries");

    if (comparison.startsWith("previous-") && previousSeries.size() > currentSeries.size())
      previousSeries = previousSeries.subList(0, currentSeries.size());
    Map<String, Object> currentTotals = totalsForOverview(currentBranches);
    Map<String, Object> previousTotals = totalsForOverview(previousBranches);
    List<Map<String, Object>> branchOptions = monthlyPerformanceRepository.findBranchOptions();
    return Map.ofEntries(
      Map.entry("from", from.toString()), Map.entry("to", to.toString()),
      Map.entry("comparison", comparison), Map.entry("comparisonFrom", comparisonFrom.toString()),
      Map.entry("comparisonTo", comparisonTo.toString()), Map.entry("granularity", bucket),
      Map.entry("branchOptions", branchOptions),
      Map.entry("branches", mergeOverviewBranches(currentBranches, previousBranches)),
      Map.entry("categories", mergeOverviewCategories(currentCategories, previousCategories)),
      Map.entry("series", mergeOverviewSeries(currentSeries, previousSeries, currentCategorySeries, previousCategorySeries, bucket)),
      Map.entry("totals", currentTotals), Map.entry("comparisonTotals", previousTotals),
      Map.entry("changes", overviewChanges(currentTotals, previousTotals)),
      Map.entry("hasData", hasOverviewData(currentTotals))
    );
  }

  static DateRange comparisonRange(LocalDate from, LocalDate to, String comparison) {
    return switch (comparison) {
      case "prev" -> {
        long days = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
        yield new DateRange(from.minusDays(days), from.minusDays(1));
      }
      case "previous-day" -> new DateRange(from.minusDays(1), to.minusDays(1));
      case "previous-month" -> {
        LocalDate start = from.minusMonths(1);
        LocalDate end = to.minusMonths(1);
        if (from.getDayOfMonth() == 1 && to.getDayOfMonth() == to.lengthOfMonth()) end = end.withDayOfMonth(end.lengthOfMonth());
        yield new DateRange(start, end);
      }
      case "previous-year", "yoy" -> new DateRange(from.minusYears(1), to.minusYears(1));
      default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kỳ so sánh không hợp lệ.");
    };
  }

  record DateRange(LocalDate from, LocalDate to) {}

  List<UUID> validateReportBranchIds(List<UUID> requestedBranchIds) {
    List<UUID> branchIds = requestedBranchIds == null ? List.of() : requestedBranchIds.stream().filter(Objects::nonNull).distinct().toList();
    if (branchIds.size() > MAX_REPORT_BRANCH_IDS)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Báo cáo chỉ hỗ trợ tối đa 50 cơ sở cùng lúc.");
    if (!branchIds.isEmpty()) {
      Set<UUID> existingBranchIds = new HashSet<>(monthlyPerformanceRepository.findAllBranchIds());
      if (!existingBranchIds.containsAll(branchIds))
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Một hoặc nhiều cơ sở không tồn tại hoặc bạn không có quyền xem báo cáo.");
    }
    return branchIds;
  }

  private Map<String, Object> readOverview(LocalDate from, LocalDate to, List<UUID> branchIds, String granularity) {
    List<Map<String, Object>> branchRows = monthlyPerformanceRepository.overviewBranchRows(from, to, branchIds);
    branchRows.forEach(MonthlyPerformanceService::decorateOverviewBranch);
    List<Map<String, Object>> categories = monthlyPerformanceRepository.overviewCategories(from, to, branchIds);
    List<Map<String, Object>> series = monthlyPerformanceRepository.overviewSeries(from, to, branchIds, granularity);
    List<Map<String, Object>> categorySeries = monthlyPerformanceRepository.overviewCategorySeries(from, to, branchIds, granularity);
    return Map.of("branches", branchRows, "categories", categories, "series", decorateOverviewSeries(series), "categorySeries", categorySeries);
  }

  private static void decorateOverviewBranch(Map<String, Object> row) {
    BigDecimal bill = decimal(row.get("billRevenue"));
    BigDecimal forfeited = decimal(row.get("forfeitedDeposits"));
    BigDecimal income = bill.add(forfeited);
    BigDecimal expense = decimal(row.get("operatingExpense")).add(decimal(row.get("salary")));
    // BR-104/105/107/211: profit uses operating income less paid operating costs; CAPEX stays outside profit.
    BigDecimal profit = income.subtract(expense);
    row.put("revenue", income); row.put("expenses", expense); row.put("profit", profit);
    row.put("margin", income.signum() > 0 ? profit.multiply(BigDecimal.valueOf(100)).divide(income, 2, RoundingMode.HALF_UP) : null);
  }

  private static List<Map<String, Object>> decorateOverviewSeries(List<Map<String, Object>> rows) {
    for (Map<String, Object> row : rows) {
      BigDecimal bill = decimal(row.get("billRevenue"));
      BigDecimal forfeited = decimal(row.get("forfeitedDeposits"));
      BigDecimal expense = decimal(row.get("expenses"));
      BigDecimal income = bill.add(forfeited);
      row.put("revenue", income); row.put("profit", income.subtract(expense));
    }
    return rows;
  }

  private static Map<String, Object> totalsForOverview(List<Map<String, Object>> rows) {
    Map<String, BigDecimal> sums = new LinkedHashMap<>();
    for (String key : List.of("billRevenue","forfeitedDeposits","revenue","operatingExpense","salary","expenses","investment","profit")) sums.put(key, BigDecimal.ZERO);
    for (Map<String, Object> row : rows) for (String key : sums.keySet()) sums.put(key, sums.get(key).add(decimal(row.get(key))));
    BigDecimal income = sums.get("revenue");
    Map<String, Object> totals = new LinkedHashMap<>(sums);
    totals.put("margin", income.signum() > 0 ? sums.get("profit").multiply(BigDecimal.valueOf(100)).divide(income, 2, RoundingMode.HALF_UP) : null);
    return totals;
  }

  private static Map<String, Object> overviewChanges(Map<String, Object> current, Map<String, Object> previous) {
    Map<String, Object> changes = new LinkedHashMap<>();
    for (String key : List.of("billRevenue","forfeitedDeposits","revenue","operatingExpense","salary","expenses","investment","profit","margin"))
      changes.put(key, percentChange(decimal(current.get(key)), decimal(previous.get(key))));
    return changes;
  }

  private static List<Map<String, Object>> mergeOverviewBranches(List<Map<String, Object>> current, List<Map<String, Object>> previous) {
    Map<Object, Map<String, Object>> previousById = new HashMap<>();
    previous.forEach(row -> previousById.put(row.get("id"), row));
    List<Map<String, Object>> result = new ArrayList<>();
    for (Map<String, Object> row : current) {
      Map<String, Object> item = new LinkedHashMap<>(row);
      Map<String, Object> prior = previousById.get(row.get("id"));
      if (prior != null) for (String key : List.of("revenue","expenses","profit","margin")) item.put("comparison" + cap(key), prior.get(key));
      result.add(item);
    }
    return result;
  }

  private static List<Map<String, Object>> mergeOverviewCategories(List<Map<String, Object>> current, List<Map<String, Object>> previous) {
    Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
    for (Map<String, Object> row : current) {
      Map<String, Object> item = new LinkedHashMap<>(); item.putAll(row); item.put("current", decimal(row.get("amount"))); item.put("comparison", BigDecimal.ZERO);
      merged.put(String.valueOf(row.get("categoryKey")), item);
    }
    for (Map<String, Object> row : previous) {
      String key = String.valueOf(row.get("categoryKey"));
      Map<String, Object> item = merged.computeIfAbsent(key, ignored -> {
        Map<String, Object> value = new LinkedHashMap<>(row); value.put("current", BigDecimal.ZERO); return value;
      });
      item.put("comparison", decimal(row.get("amount")));
    }
    for (Map<String, Object> item : merged.values()) {
      item.put("changePercent", percentChange(decimal(item.get("current")), decimal(item.get("comparison"))));
      item.remove("amount");
    }
    return new ArrayList<>(merged.values());
  }

  private static List<Map<String, Object>> mergeOverviewSeries(List<Map<String, Object>> current, List<Map<String, Object>> previous,
                                                               List<Map<String, Object>> currentCategories, List<Map<String, Object>> previousCategories,
                                                               String granularity) {
    Map<String, Map<String, BigDecimal>> currentByBucket = categorySeriesMap(currentCategories);
    Map<String, Map<String, BigDecimal>> previousByBucket = categorySeriesMap(previousCategories);
    int count = Math.max(current.size(), previous.size());
    List<Map<String, Object>> result = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      Map<String, Object> now = i < current.size() ? current.get(i) : Map.of();
      Map<String, Object> then = i < previous.size() ? previous.get(i) : Map.of();
      Object bucket = now.getOrDefault("bucket", then.get("bucket"));
      String key = String.valueOf(bucket);
      Map<String, Object> item = new LinkedHashMap<>(); item.put("key", i); item.put("label", overviewBucketLabel(bucket, granularity));
      for (String metric : List.of("billRevenue","forfeitedDeposits","revenue","operatingExpense","salary","expenses","investment","profit")) {
        item.put(metric, decimal(now.get(metric)));
        item.put("comparison" + cap(metric), decimal(then.get(metric)));
      }
      item.put("expensesByCategory", currentByBucket.getOrDefault(key, Map.of()));
      item.put("comparisonExpensesByCategory", previousByBucket.getOrDefault(String.valueOf(then.get("bucket")), Map.of()));
      result.add(item);
    }
    return result;
  }

  private static Map<String, Map<String, BigDecimal>> categorySeriesMap(List<Map<String, Object>> rows) {
    Map<String, Map<String, BigDecimal>> result = new HashMap<>();
    for (Map<String, Object> row : rows)
      result.computeIfAbsent(String.valueOf(row.get("bucket")), ignored -> new LinkedHashMap<>())
        .put(String.valueOf(row.get("categoryKey")), decimal(row.get("amount")));
    return result;
  }

  private static String overviewBucketLabel(Object value, String granularity) {
    if (value == null) return "";
    LocalDate date = value instanceof java.sql.Date sql ? sql.toLocalDate() : value instanceof LocalDate local ? local : LocalDate.parse(value.toString());
    String day = date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"));
    return "month".equals(granularity) ? date.format(java.time.format.DateTimeFormatter.ofPattern("MM/yyyy")) : "week".equals(granularity) ? "Tuần " + day : day;
  }

  private static BigDecimal decimal(Object value) {
    if (value == null) return BigDecimal.ZERO;
    return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
  }

  private static BigDecimal percentChange(BigDecimal current, BigDecimal previous) {
    return previous.signum() == 0 ? null : current.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous.abs(), 2, RoundingMode.HALF_UP);
  }

  private static boolean hasOverviewData(Map<String, Object> totals) {
    return List.of("billRevenue","forfeitedDeposits","operatingExpense","salary","investment").stream()
      .map(totals::get).map(MonthlyPerformanceService::decimal).anyMatch(value -> value.signum() != 0);
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
    monthlyPerformanceRepository.insertReportAudit(actor.id(),branchId,"EXPORT_MONTHLY_PERFORMANCE",String.format(Locale.ROOT,"{\"month\":\"%s\",\"format\":\"%s\"}",YearMonth.from(month),format));
    if (!"xlsx".equals(format)) return MonthlyPerformanceExport.csv(rows);
    List<Map<String, Object>> details = "ACCOUNTANT".equals(actor.role())
      ? accountingEntries(YearMonth.from(month), branchId == null ? List.of() : List.of(branchId)) : null;
    String scope = branchId == null ? "Toàn chuỗi" : source.isEmpty() ? "Cơ sở đã chọn" : String.valueOf(source.get(0).get("name"));
    return MonthlyPerformanceExport.xlsx(rows, YearMonth.from(month).toString(), scope,
      String.valueOf(data.get("periodStatus")), details);
  }

  @Transactional(readOnly = true)
  List<Map<String, Object>> accountingEntries(YearMonth month, List<UUID> branchIds) {
    return monthlyPerformanceRepository.accountingEntries(month, branchIds);
  }

  public List<Map<String,Object>> dailyRevenue(LocalDate start, LocalDate end, UUID branchId) {
    return monthlyPerformanceRepository.dailyRevenue(start, end, branchId);
  }

  public List<Map<String,Object>> dailyRevenue(LocalDate start, UUID branchId) {
    return dailyRevenue(start, start.plusMonths(1), branchId);
  }

  Map<String,Object> totals(List<Map<String,Object>> rows) {
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
      || !monthlyPerformanceRepository.isBranchManagerOf(actor.id(),actor.branchId())))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Bạn không có quyền xem đối với cơ sở này.");
    if (branchId!=null && !monthlyPerformanceRepository.activeBranchExists(branchId)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cơ sở đang chọn không hoạt động hoặc không tồn tại.");
  }

  private static String cap(String key) { return Character.toUpperCase(key.charAt(0))+key.substring(1); }
}
