package com.gcmb.backend.dashboard;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class MonthlyPerformanceExport {
  private MonthlyPerformanceExport() {}
  static final List<String> KEYS = List.of("name","billRevenue","forfeitedDeposits","revenue","roomRevenue","fnbRevenue","overtimeRevenue","otherRevenue","expenses","salary","investment","profit","margin","netCashFlow","bookingCount","completedBookings","cancelledNoShowBookings","occupancyRate","periodStatus");
  static final List<String> HEADERS = List.of("Cơ sở","Doanh thu từ bill","Thu từ cọc tịch thu","Tổng doanh thu","Tiền phòng","F&B","Phụ phí quá giờ","Khác (phụ phí/giảm giá)","Chi phí vận hành","Lương và ứng lương","Đầu tư","Lợi nhuận vận hành (Tổng doanh thu − chi vận hành)","Biên lợi nhuận vận hành (%)","Dòng tiền thuần","Số booking","Hoàn tất","Hủy/no-show","Lấp đầy (%)","Kỳ");

  static byte[] csv(List<Map<String,Object>> rows) {
    var out = new StringBuilder("\uFEFF").append(String.join(",", HEADERS.stream().map(MonthlyPerformanceExport::cell).toList())).append("\r\n");
    for (var row : rows) out.append(String.join(",", KEYS.stream().map(key -> cell(safe(row.get(key)))).toList())).append("\r\n");
    return out.toString().getBytes(StandardCharsets.UTF_8);
  }

  static byte[] xlsx(List<Map<String,Object>> rows) {
    try {
      var bytes = new ByteArrayOutputStream();
      try (var zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
        put(zip,"[Content_Types].xml","<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
        put(zip,"_rels/.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
        put(zip,"xl/workbook.xml","<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Hiệu quả tháng\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
        put(zip,"xl/_rels/workbook.xml.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
        var xml = new StringBuilder("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        row(xml, HEADERS);
        for (var item : rows) row(xml, KEYS.stream().map(key -> safe(item.get(key))).toList());
        put(zip,"xl/worksheets/sheet1.xml",xml.append("</sheetData></worksheet>").toString());
      }
      return bytes.toByteArray();
    } catch (Exception ex) { throw new IllegalStateException("Không thể tạo tệp Excel báo cáo.",ex); }
  }

  private static Object safe(Object value) { return value == null ? "—" : value; }
  private static String cell(Object value) { if(value instanceof Number)return value.toString(); String s=value==null?"":value.toString(); if(!s.isEmpty() && "=+-@".indexOf(s.charAt(0))>=0)s="'"+s; return "\""+s.replace("\"","\"\"")+"\""; }
  private static void row(StringBuilder xml,List<?> values) {
    xml.append("<row>");
    for(Object value:values) {
      if(value instanceof Number) xml.append("<c><v>").append(value).append("</v></c>");
      else xml.append("<c t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(escape(value==null?"":value.toString())).append("</t></is></c>");
    }
    xml.append("</row>");
  }
  private static void put(ZipOutputStream zip,String name,String value)throws Exception {zip.putNextEntry(new ZipEntry(name));zip.write(value.getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
  private static String escape(String s){return s.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]","").replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
}
