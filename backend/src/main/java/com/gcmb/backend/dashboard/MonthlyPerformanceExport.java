package com.gcmb.backend.dashboard;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

final class MonthlyPerformanceExport {
  private MonthlyPerformanceExport() {}

  static final List<String> KEYS = List.of("name","billRevenue","forfeitedDeposits","revenue","roomRevenue","fnbRevenue","overtimeRevenue","otherRevenue","expenses","salary","investment","profit","margin","netCashFlow","bookingCount","completedBookings","cancelledNoShowBookings","occupancyRate","periodStatus");
  static final List<String> HEADERS = List.of("Cơ sở","Doanh thu hóa đơn hoàn tất","Cọc bị tịch thu","Thu nhập vận hành","Tiền phòng","Đồ ăn và thức uống","Phụ phí quá giờ","Khác (phụ phí/giảm giá)","Chi phí vận hành","Lương và ứng lương","Chi đầu tư","Lợi nhuận vận hành (Thu nhập vận hành − chi phí vận hành)","Biên lợi nhuận vận hành (%)","Dòng tiền thuần","Số lượt đặt","Lượt đặt hoàn tất","Lượt hủy hoặc không đến","Lấp đầy (%)","Tháng");
  private static final String[] SUMMARY_HEADERS = {
    "Cơ sở","Mã cơ sở","Doanh thu hóa đơn hoàn tất","Cọc bị tịch thu","Thu nhập vận hành","Tiền phòng","Đồ ăn và thức uống","Phụ phí quá giờ","Khác",
    "Chi phí vận hành đã thanh toán","Lương và ứng lương","Tổng chi phí vận hành","Chi đầu tư","Lợi nhuận vận hành","Biên lợi nhuận vận hành (%)",
    "Dòng tiền thuần","Số hóa đơn","Số lượt đặt","Lượt đặt hoàn tất","Lượt hủy hoặc không đến","Lấp đầy (%)","Tháng","Trạng thái kỳ"
  };
  private static final String[] SUMMARY_KEYS = {
    "name","code","billRevenue","forfeitedDeposits","revenue","roomRevenue","fnbRevenue","overtimeRevenue","otherRevenue",
    "operatingExpense","salary","expenses","investment","profit","margin","netCashFlow","billCount","bookingCount",
    "completedBookings","cancelledNoShowBookings","occupancyRate","month","periodStatus"
  };
  private static final String[] DETAIL_HEADERS = {
    "Ngày ghi sổ","Thời điểm phát sinh","Cơ sở","Mã giao dịch","Mã dòng sổ","Mã giao dịch nguồn",
    "Mã giao dịch gốc","Mã lượt đặt","Số hóa đơn","Loại thu/chi","Loại giao dịch","Hạng mục","Diễn giải",
    "Mã tài khoản","Số tiền (+ thu / − chi)","Phương thức thanh toán","Tham chiếu / chứng từ"
  };

  static byte[] csv(List<Map<String,Object>> rows) {
    var out = new StringBuilder("\uFEFF").append(String.join(",", HEADERS.stream().map(MonthlyPerformanceExport::cell).toList())).append("\r\n");
    for (var row : rows) out.append(String.join(",", KEYS.stream().map(key -> cell(safe(row.get(key)))).toList())).append("\r\n");
    return out.toString().getBytes(StandardCharsets.UTF_8);
  }

  static byte[] overviewCsv(Map<String,Object> report) {
    String[] headers = overviewHeaders();
    var out = new StringBuilder("\uFEFF").append(String.join(",", Arrays.stream(headers).map(MonthlyPerformanceExport::cell).toList())).append("\r\n");
    for (var row : overviewRows(report)) out.append(String.join(",", overviewValues(row, report).stream().map(MonthlyPerformanceExport::cell).toList())).append("\r\n");
    return out.toString().getBytes(StandardCharsets.UTF_8);
  }

  static byte[] overviewXlsx(Map<String,Object> report) {
    try (var wb = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
      Styles styles = new Styles(wb);
      Sheet sheet = wb.createSheet("Tổng hợp");
      String[] headers = overviewHeaders();
      int lastColumn = headers.length - 1;
      title(sheet, "BÁO CÁO THU NHẬP VẬN HÀNH · " + report.get("from") + " – " + report.get("to"), lastColumn, styles.title);
      note(sheet, "Kỳ so sánh: " + report.get("comparisonFrom") + " – " + report.get("comparisonTo")
        + " · Thu nhập vận hành gồm doanh thu hóa đơn hoàn tất và cọc bị tịch thu; chi đầu tư trình bày riêng, không trừ vào lợi nhuận vận hành.", lastColumn, styles.note);
      Row header = sheet.createRow(3);
      for (int c = 0; c < headers.length; c++) set(header, c, headers[c], styles.header);
      List<Map<String,Object>> rows = overviewRows(report);
      for (int i = 0; i < rows.size(); i++) {
        Row row = sheet.createRow(4 + i);
        List<Object> values = overviewValues(rows.get(i), report);
        for (int c = 0; c < values.size(); c++) {
          Object value = values.get(c);
          CellStyle style = c == 16 || c == 12 ? styles.percent : c >= 4 && c <= 15 ? styles.money : styles.text;
          if ((c == 12 || c == 16) && value != null) value = percentValue(value);
          set(row, c, value, style);
        }
      }
      int lastDataRow = Math.max(4, 3 + rows.size());
      sheet.setAutoFilter(new CellRangeAddress(3, lastDataRow, 0, lastColumn));
      sheet.createFreezePane(1, 4);
      sheet.setZoom(85);
      int[] widths = {28,14,14,14,18,18,18,18,18,18,18,18,18,18,18,18,18};
      for (int c = 0; c < widths.length; c++) sheet.setColumnWidth(c, widths[c] * 256);
      wb.write(out);
      return out.toByteArray();
    } catch (Exception ex) { throw new IllegalStateException("Không thể tạo tệp Excel báo cáo.", ex); }
  }

  private static String[] overviewHeaders() {
    return new String[]{"Cơ sở", "Từ ngày", "Đến ngày", "Kỳ so sánh", "Doanh thu hóa đơn hoàn tất", "Cọc bị tịch thu", "Thu nhập vận hành",
      "Chi phí vận hành đã thanh toán", "Lương và ứng lương", "Tổng chi phí vận hành", "Chi đầu tư", "Lợi nhuận vận hành", "Biên lợi nhuận vận hành",
      "Thu nhập kỳ so sánh", "Chi phí kỳ so sánh", "Lợi nhuận kỳ so sánh", "Biên lợi nhuận kỳ so sánh"};
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String,Object>> overviewRows(Map<String,Object> report) {
    var rows = new ArrayList<Map<String,Object>>((List<Map<String,Object>>) report.getOrDefault("branches", List.of()));
    Map<String,Object> total = new LinkedHashMap<>((Map<String,Object>) report.get("totals"));
    Map<String,Object> prior = (Map<String,Object>) report.get("comparisonTotals");
    total.put("name", "Toàn phạm vi");
    total.put("comparisonRevenue", prior.get("revenue")); total.put("comparisonExpenses", prior.get("expenses"));
    total.put("comparisonProfit", prior.get("profit")); total.put("comparisonMargin", prior.get("margin"));
    rows.add(total);
    return rows;
  }

  private static List<Object> overviewValues(Map<String,Object> row, Map<String,Object> report) {
    return Arrays.asList(row.get("name"), report.get("from"), report.get("to"),
      report.get("comparisonFrom") + " – " + report.get("comparisonTo"), row.get("billRevenue"), row.get("forfeitedDeposits"),
      row.get("revenue"), row.get("operatingExpense"), row.get("salary"), row.get("expenses"), row.get("investment"),
      row.get("profit"), row.get("margin"), row.get("comparisonRevenue"), row.get("comparisonExpenses"),
      row.get("comparisonProfit"), row.get("comparisonMargin"));
  }

  static byte[] xlsx(List<Map<String,Object>> summaryRows, String month, String scope, String periodStatus,
                     List<Map<String,Object>> detailRows) {
    try (var wb = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
      Styles styles = new Styles(wb);
      Sheet summary = wb.createSheet("Tổng hợp");
      writeSummary(summary, summaryRows, month, scope, periodStatus, styles);
      if (detailRows != null) writeDetails(wb.createSheet("Chi tiết kế toán"), detailRows, summaryRows.size(), month, scope, styles);
      XSSFFormulaEvaluator.evaluateAllFormulaCells(wb);
      wb.write(out);
      return out.toByteArray();
    } catch (Exception ex) { throw new IllegalStateException("Không thể tạo tệp Excel báo cáo.",ex); }
  }

  private static void writeSummary(Sheet sheet, List<Map<String,Object>> rows, String month, String scope,
                                   String periodStatus, Styles styles) {
    int lastColumn = SUMMARY_HEADERS.length - 1;
    title(sheet, "BÁO CÁO HIỆU QUẢ · " + month, lastColumn, styles.title);
    note(sheet, "Kỳ: " + month + " · Phạm vi: " + scope + " · Trạng thái kỳ: " + status(periodStatus)
      + " · Tổng chi phí vận hành gồm chi phí vận hành đã thanh toán, lương và ứng lương; chi đầu tư trình bày riêng, không trừ vào lợi nhuận vận hành.", lastColumn, styles.note);
    Row header = sheet.createRow(3);
    for (int c = 0; c < SUMMARY_HEADERS.length; c++) set(header, c, SUMMARY_HEADERS[c], styles.header);
    for (int i = 0; i < rows.size(); i++) {
      Map<String,Object> source = rows.get(i);
      Row row = sheet.createRow(4 + i);
      boolean total = String.valueOf(source.get("name")).toLowerCase(java.util.Locale.ROOT).startsWith("toàn chuỗi")
        || String.valueOf(source.get("name")).toLowerCase(java.util.Locale.ROOT).startsWith("tổng ");
      for (int c = 0; c < SUMMARY_KEYS.length; c++) {
        String key = SUMMARY_KEYS[c];
        Object value = "month".equals(key) ? month : "periodStatus".equals(key) ? status(periodStatus) : source.get(key);
        CellStyle style = summaryStyle(c, total, styles);
        if ("margin".equals(key) || "occupancyRate".equals(key)) value = percentValue(value);
        set(row, c, value, style);
      }
    }
    if (rows.isEmpty()) {
      Row empty = sheet.createRow(4);
      set(empty, 0, "Chưa có dữ liệu trong phạm vi đã chọn.", styles.text);
      sheet.addMergedRegion(new CellRangeAddress(4, 4, 0, lastColumn));
    }
    int lastDataRow = Math.max(4, 3 + rows.size());
    sheet.setAutoFilter(new CellRangeAddress(3, lastDataRow, 0, lastColumn));
    sheet.createFreezePane(2, 4);
    sheet.setZoom(85);
    int[] widths = {28,14,18,18,18,16,14,18,18,20,18,18,18,20,16,18,12,14,14,16,16,14,16};
    for (int c = 0; c < widths.length; c++) sheet.setColumnWidth(c, widths[c] * 256);
  }

  private static void writeDetails(Sheet sheet, List<Map<String,Object>> rows, int summaryRowCount, String month,
                                   String scope, Styles styles) {
    int lastColumn = DETAIL_HEADERS.length - 1;
    title(sheet, "CHI TIẾT GHI SỔ · " + month, lastColumn, styles.title);
    note(sheet, "Phạm vi: " + scope + ". Dữ liệu đã ghi sổ trong kỳ, lọc theo ngày ghi sổ. Số tiền giữ nguyên dấu theo sổ; phương thức và chứng từ chỉ hiện khi nguồn có lưu. Đây là dữ liệu đối soát, không phải tờ khai hay hồ sơ thuế chính thức.", lastColumn, styles.note);
    Row header = sheet.createRow(3);
    for (int c = 0; c < DETAIL_HEADERS.length; c++) set(header, c, DETAIL_HEADERS[c], styles.header);
    for (int i = 0; i < rows.size(); i++) {
      Map<String,Object> source = rows.get(i);
      Row row = sheet.createRow(4 + i);
      Object reference = join(source.get("source_reference"), source.get("documents"));
      Object[] values = {
        source.get("booking_date"), source.get("occurred_at"), branchName(source), source.get("transaction_id"),
        source.get("entry_id"), source.get("source_id"), source.get("adjusts_transaction_id"), source.get("booking_code"),
        source.get("bill_number"), source.get("flow_type"), source.get("kind"), source.get("item_category"), source.get("reason"),
        source.get("account_code"), source.get("amount_signed"), paymentMethod(source.get("payment_method")), reference
      };
      for (int c = 0; c < values.length; c++) {
        CellStyle style = c == 0 ? styles.date : c == 14 ? styles.money : styles.text;
        set(row, c, values[c], style);
      }
    }
    if (rows.isEmpty()) {
      Row empty = sheet.createRow(4);
      set(empty, 0, "Chưa có giao dịch ghi sổ trong kỳ và phạm vi đã chọn.", styles.text);
      sheet.addMergedRegion(new CellRangeAddress(4, 4, 0, lastColumn));
    }

    int lastDataExcelRow = 4 + rows.size();
    int totalRowIndex = 4 + Math.max(rows.size(), 1);
    Row total = sheet.createRow(totalRowIndex);
    set(total, 0, "TỔNG SỐ TIỀN GHI SỔ", styles.totalText);
    String amountColumn = "O";
    if (rows.isEmpty()) {
      set(total, 14, 0, styles.totalMoney);
    } else {
      setFormula(total, 14, "SUM(" + amountColumn + "5:" + amountColumn + lastDataExcelRow + ")", styles.totalMoney);
    }
    if (!rows.isEmpty()) sheet.setAutoFilter(new CellRangeAddress(3, lastDataExcelRow, 0, lastColumn));
    sheet.createFreezePane(3, 4);
    sheet.setZoom(85);
    int[] widths = {15,22,24,38,38,38,38,20,18,14,18,24,42,20,22,22,42};
    for (int c = 0; c < widths.length; c++) sheet.setColumnWidth(c, widths[c] * 256);

    int section = totalRowIndex + 3;
    Row reconcileTitle = sheet.createRow(section);
    set(reconcileTitle, 0, "ĐỐI CHIẾU VỚI BẢNG TỔNG HỢP", styles.totalText);
    sheet.addMergedRegion(new CellRangeAddress(section, section, 0, 2));
    int totalSummaryExcelRow = 4 + summaryRowCount;
    String[] labels = {
      "Tổng tiền thu theo sổ kế toán","Tổng tiền chi theo sổ kế toán","Dòng tiền ròng theo sổ kế toán",
      "Thu nhập vận hành theo báo cáo hiệu quả","Tổng chi phí vận hành theo báo cáo hiệu quả","Lợi nhuận vận hành theo báo cáo hiệu quả",
      "Chi đầu tư theo báo cáo hiệu quả","Dòng tiền thuần theo báo cáo hiệu quả",
      "Chênh lệch tiền thu theo sổ và thu nhập vận hành (tham khảo)",
      "Chênh lệch tiền chi theo sổ và tổng chi phí vận hành (tham khảo)",
      "Chênh lệch dòng tiền giữa hai báo cáo (tham khảo)",
      "Lương và ứng lương theo sổ kế toán","Lương và ứng lương theo báo cáo hiệu quả",
      "Chênh lệch lương và ứng lương (tham khảo)"
    };
    for (int i = 0; i < labels.length; i++) {
      Row row = sheet.createRow(section + 1 + i);
      set(row, 0, labels[i], styles.text);
      String formula = switch (i) {
        case 0 -> rows.isEmpty() ? null : "SUMIF(O5:O" + lastDataExcelRow + ",\">0\",O5:O" + lastDataExcelRow + ")";
        case 1 -> rows.isEmpty() ? null : "-SUMIF(O5:O" + lastDataExcelRow + ",\"<0\",O5:O" + lastDataExcelRow + ")";
        case 2 -> rows.isEmpty() ? null : "SUM(O5:O" + lastDataExcelRow + ")";
        case 3 -> "'Tổng hợp'!E" + totalSummaryExcelRow;
        case 4 -> "'Tổng hợp'!L" + totalSummaryExcelRow;
        case 5 -> "'Tổng hợp'!N" + totalSummaryExcelRow;
        case 6 -> "'Tổng hợp'!M" + totalSummaryExcelRow;
        case 7 -> "'Tổng hợp'!P" + totalSummaryExcelRow;
        case 8 -> "B" + (section + 2) + "-'Tổng hợp'!E" + totalSummaryExcelRow;
        case 9 -> "B" + (section + 3) + "-'Tổng hợp'!L" + totalSummaryExcelRow;
        case 10 -> "B" + (section + 4) + "-'Tổng hợp'!P" + totalSummaryExcelRow;
        case 11 -> rows.isEmpty() ? null : "-SUMIF(L5:L" + lastDataExcelRow + ",\"Lương\",O5:O" + lastDataExcelRow + ")-SUMIF(L5:L" + lastDataExcelRow + ",\"Ứng lương\",O5:O" + lastDataExcelRow + ")";
        case 12 -> "'Tổng hợp'!K" + totalSummaryExcelRow;
        default -> "B" + (section + 13) + "-B" + (section + 14);
      };
      if (formula == null) set(row, 1, 0, styles.money);
      else setFormula(row, 1, formula, styles.money);
    }
    Row explanation = sheet.createRow(section + labels.length + 2);
    set(explanation, 0, "Chênh lệch chỉ dùng để đối chiếu phạm vi dữ liệu, không kết luận sai lệch kế toán. Sổ kế toán lọc theo ngày ghi sổ; báo cáo hiệu quả lấy doanh thu hóa đơn, cọc bị tịch thu và khoản chi đã thanh toán theo ngày phát sinh tại cơ sở, cùng lương theo ngày ghi nhận. Một số khoản chi, thanh toán hóa đơn hoặc cọc có thể chưa liên kết với chứng từ sổ kế toán. Chi tiết sổ có thể gồm hoàn tiền, chuyển quỹ hoặc điều chỉnh ngoài lợi nhuận. Hệ thống chưa lưu phân loại thuế hay số hóa đơn thuế đã phát hành.", styles.note);
    sheet.addMergedRegion(new CellRangeAddress(section + labels.length + 2, section + labels.length + 3, 0, lastColumn));
    explanation.setHeightInPoints(50);
  }

  private static CellStyle summaryStyle(int column, boolean total, Styles styles) {
    if (total) return column == 14 || column == 20 ? styles.totalPercent : isMoneyColumn(column) ? styles.totalMoney : isCountColumn(column) ? styles.totalInteger : styles.totalText;
    if (column == 14 || column == 20) return styles.percent;
    if (isMoneyColumn(column)) return styles.money;
    if (isCountColumn(column)) return styles.integer;
    return styles.text;
  }

  private static boolean isMoneyColumn(int column) { return column >= 2 && column <= 13 || column == 15; }
  private static boolean isCountColumn(int column) { return column >= 16 && column <= 19; }

  private static void title(Sheet sheet, String value, int lastColumn, CellStyle style) {
    Row row = sheet.createRow(0); set(row, 0, value, style);
    if (lastColumn > 0) sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, lastColumn));
    row.setHeightInPoints(25);
  }

  private static void note(Sheet sheet, String value, int lastColumn, CellStyle style) {
    Row row = sheet.createRow(1); set(row, 0, value, style);
    if (lastColumn > 0) sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, lastColumn));
    row.setHeightInPoints(38);
  }

  private static void set(Row row, int column, Object value, CellStyle style) {
    Cell cell = row.createCell(column);
    cell.setCellStyle(style);
    if (value == null) return;
    if (value instanceof Number number) cell.setCellValue(number.doubleValue());
    else if (value instanceof Boolean bool) cell.setCellValue(bool);
    else if (value instanceof UUID id) cell.setCellValue(id.toString());
    else if (value instanceof LocalDate date) cell.setCellValue(DateUtil.getExcelDate(date));
    else if (value instanceof Date date) cell.setCellValue(DateUtil.getExcelDate(date.toLocalDate()));
    else cell.setCellValue(safeText(value.toString()));
  }

  private static void setFormula(Row row, int column, String formula, CellStyle style) {
    Cell cell = row.createCell(column, CellType.FORMULA);
    cell.setCellFormula(formula);
    cell.setCellStyle(style);
  }

  private static Object percentValue(Object value) {
    if (value == null) return null;
    return new java.math.BigDecimal(value.toString()).movePointLeft(2).doubleValue();
  }

  private static String status(Object value) { return "CLOSED".equals(value) ? "Đã khóa" : "Đang mở"; }

  private static String paymentMethod(Object value) {
    if ("CASH".equals(value)) return "Tiền mặt";
    if ("BANK_TRANSFER".equals(value)) return "Chuyển khoản";
    return value == null ? null : value.toString();
  }

  private static String branchName(Map<String,Object> row) {
    String name = row.get("branch_name") == null ? "" : row.get("branch_name").toString();
    String code = row.get("branch_code") == null ? "" : row.get("branch_code").toString();
    return code.isBlank() ? name : name + " · " + code;
  }

  private static String join(Object first, Object second) {
    String left = first == null ? "" : first.toString();
    String right = second == null ? "" : second.toString();
    return left.isBlank() ? right : right.isBlank() ? left : left + " · " + right;
  }

  private static String safeText(String value) {
    String clean = value.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
    return !clean.isEmpty() && "=+-@".indexOf(clean.charAt(0)) >= 0 ? "'" + clean : clean;
  }

  private static Object safe(Object value) { return value == null ? "—" : value; }
  private static String cell(Object value) {
    if(value instanceof Number)return value.toString();
    String s=value==null?"":value.toString();
    if(!s.isEmpty() && "=+-@".indexOf(s.charAt(0))>=0)s="'"+s;
    return "\""+s.replace("\"","\"\"")+"\"";
  }

  private static final class Styles {
    final CellStyle title, note, header, text, money, integer, percent, date, totalText, totalMoney, totalInteger, totalPercent;
    Styles(Workbook wb) {
      Font titleFont = wb.createFont(); titleFont.setBold(true); titleFont.setFontHeightInPoints((short)14);
      title = wb.createCellStyle(); title.setFont(titleFont);
      Font noteFont = wb.createFont(); noteFont.setItalic(true); noteFont.setFontHeightInPoints((short)9);
      note = wb.createCellStyle(); note.setFont(noteFont); note.setWrapText(true); note.setVerticalAlignment(VerticalAlignment.CENTER);
      Font headerFont = wb.createFont(); headerFont.setBold(true); headerFont.setColor(IndexedColors.WHITE.getIndex());
      header = wb.createCellStyle(); header.setFont(headerFont); header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
      header.setFillPattern(FillPatternType.SOLID_FOREGROUND); header.setAlignment(HorizontalAlignment.CENTER);
      header.setVerticalAlignment(VerticalAlignment.CENTER); header.setWrapText(true);
      text = wb.createCellStyle(); text.setVerticalAlignment(VerticalAlignment.TOP);
      money = wb.createCellStyle(); money.setDataFormat(wb.createDataFormat().getFormat("#,##0;[Red](#,##0)"));
      integer = wb.createCellStyle(); integer.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
      percent = wb.createCellStyle(); percent.setDataFormat(wb.createDataFormat().getFormat("0.00%"));
      date = wb.createCellStyle(); date.setDataFormat(wb.createDataFormat().getFormat("dd/mm/yyyy"));
      Font totalFont = wb.createFont(); totalFont.setBold(true);
      totalText = totalStyle(wb, totalFont, null);
      totalMoney = totalStyle(wb, totalFont, wb.createDataFormat().getFormat("#,##0;[Red](#,##0)"));
      totalInteger = totalStyle(wb, totalFont, wb.createDataFormat().getFormat("#,##0"));
      totalPercent = totalStyle(wb, totalFont, wb.createDataFormat().getFormat("0.00%"));
    }
    private static CellStyle totalStyle(Workbook wb, Font font, Short format) {
      CellStyle style = wb.createCellStyle(); style.setFont(font); style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
      style.setFillPattern(FillPatternType.SOLID_FOREGROUND); if (format != null) style.setDataFormat(format);
      return style;
    }
  }
}
