/** Author: HuyDV. UC-93 / BR-79: xuất XLSX bằng OOXML, ô văn bản không thực thi công thức. */
package com.gcmb.backend.workforce.service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.zip.*;

public final class PayrollExport {

  private PayrollExport() {}

  public static byte[] xlsx(List<Map<String, Object>> rows) {
    return workbook("Bảng lương", List.of(
      "Mã NV", "Họ tên", "Tổng lương", "Khấu trừ", "Ứng đã trả", "Thực nhận",
      "Ngân hàng", "Số tài khoản", "Chủ tài khoản", "Đã thanh toán"
    ), rows, entry -> {
      var values = new ArrayList<Object>();
      for (String key : List.of("code", "name", "gross_amount", "deduction_amount", "advances",
          "net_amount", "bank_name_snapshot", "bank_account_snapshot", "bank_holder_snapshot")) {
        values.add(entry.get(key));
      }
      values.add(Boolean.TRUE.equals(entry.get("paid")) ? "Đã trả" : "Chưa trả");
      return values;
    });
  }

  public static byte[] batchXlsx(String month, List<Map<String, Object>> rows) {
    return workbook("Danh sách chuyển khoản", List.of(
      "Mã nhân viên", "Ngân hàng nhận", "Số tài khoản nhận", "Chủ tài khoản", "Số tiền (VND)", "Nội dung"
    ), rows, entry -> List.of(entry.get("code"), entry.get("bank_name_snapshot"),
      entry.get("bank_account_snapshot"), entry.get("bank_holder_snapshot"), entry.get("net_amount"),
      "Luong " + month + " " + entry.get("code")));
  }

  private static byte[] workbook(String sheetName, List<String> headers, List<Map<String, Object>> rows,
      Function<Map<String, Object>, List<?>> lineValues) {
    try {
      var bytes = new ByteArrayOutputStream();
      try (var zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
        put(
          zip,
          "[Content_Types].xml",
          "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>"
        );
        put(
          zip,
          "_rels/.rels",
          "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>"
        );
        put(
          zip,
          "xl/workbook.xml",
          "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"" + escape(sheetName) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>"
        );
        put(
          zip,
          "xl/_rels/workbook.xml.rels",
          "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>"
        );
        var sheet = new StringBuilder(
          "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" state=\"frozen\"/></sheetView></sheetViews><cols><col min=\"1\" max=\"" + headers.size() + "\" width=\"25\" customWidth=\"1\"/></cols><sheetData>"
        );
        row(sheet, headers);
        for (var entry : rows) row(sheet, lineValues.apply(entry));
        sheet.append("</sheetData></worksheet>");
        put(zip, "xl/worksheets/sheet1.xml", sheet.toString());
      }
      return bytes.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("Không thể xuất bảng lương", e);
    }
  }

  private static void row(StringBuilder xml, List<?> values) {
    xml.append("<row>");
    for (Object value : values) {
      if (value instanceof Number) xml.append("<c><v>").append(value).append("</v></c>");
      else xml
        .append("<c t=\"inlineStr\"><is><t xml:space=\"preserve\">")
        .append(escape(value == null ? "" : value.toString()))
        .append("</t></is></c>");
    }
    xml.append("</row>");
  }

  private static String escape(String s) {
    return s
      .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "")
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;");
  }

  private static void put(ZipOutputStream zip, String name, String value) throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(value.getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }
}
