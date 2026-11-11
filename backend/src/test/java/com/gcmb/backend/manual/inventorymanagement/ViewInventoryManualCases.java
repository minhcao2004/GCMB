/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 11/11/2026
 * Description: Defines UC-55 View Inventory manual unit test cases.
 */
package com.gcmb.backend.manual.inventorymanagement;

import static com.gcmb.backend.manual.inventorymanagement.InventoryManagementManualRunner.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

final class ViewInventoryManualCases {
    private static final String UC = "UC-55 View Inventory";
    private static final String PRE = "Quản lý cơ sở qlcs.hoabang đã đăng nhập và được phân công tại HN_HOA_BANG; dữ liệu chỉ được truy vấn trong cơ sở này.";
    private static final String FILE = "UC-55 - View_Inventory.docx";

    private ViewInventoryManualCases() {}

    static List<ManualTest> cases() {
        return List.of(
            tc("INM-010", "Mở danh sách tồn kho.", "Trả các mặt hàng thuộc HN_HOA_BANG cùng số tồn và trạng thái; không ghi dữ liệu.",
                "Normal", "BR-32, BR-36, BR-37, BR-40", "", ViewInventoryManualCases::list),
            tc("INM-011", "Cơ sở không có mặt hàng kho.", "Trả danh sách rỗng; không ghi dữ liệu.",
                "Abnormal", "BR-40", "", ViewInventoryManualCases::empty),
            tc("INM-012", "Mở form cần đơn vị tính và nhóm hàng.", "Trả danh mục đơn vị tính và nhóm hàng của đúng cơ sở; không ghi dữ liệu.",
                "Normal", "BR-40", "", ViewInventoryManualCases::metadata),
            tc("INM-013", "itemId thuộc HN_HOA_BANG.", "Trả đúng chi tiết mặt hàng, số tồn, ngưỡng và stockVersion; không ghi dữ liệu.",
                "Normal", "BR-32, BR-36, BR-37, BR-40", "", ViewInventoryManualCases::detail),
            tc("INM-014", "itemId không tồn tại tại HN_HOA_BANG.", "Hiển thị “Không tìm thấy mặt hàng kho tại cơ sở này.”; không ghi dữ liệu.",
                "Abnormal", "BR-40", "", ViewInventoryManualCases::missing),
            tc("INM-015", "from=01/11/2026, to=11/11/2026, kind=receipt, q=' NK '.",
                "Chuẩn hóa kind=RECEIPT, q=NK và trả lịch sử phù hợp; không ghi dữ liệu.",
                "Normal", "BR-32, BR-36, BR-37, BR-40", "", ViewInventoryManualCases::history),
            tc("INM-016", "from sau to.", "Hiển thị “Thời điểm kết thúc phải bằng hoặc sau thời điểm bắt đầu.”; không truy vấn lịch sử.",
                "Abnormal", "BR-40", "", ViewInventoryManualCases::invalidPeriod),
            tc("INM-017", "kind=TRANSFER không thuộc danh mục biến động.", "Hiển thị “Loại biến động kho không hợp lệ.”; không truy vấn lịch sử.",
                "Abnormal", "BR-40", "", ViewInventoryManualCases::invalidKind),
            tc("INM-018", "Từ khóa tìm kiếm dài 101 ký tự.", "Hiển thị “Từ khóa tìm kiếm chỉ được chứa tối đa 100 ký tự.”; không truy vấn lịch sử.",
                "Boundary", "BR-40", "MSG03", ViewInventoryManualCases::longSearch)
        );
    }

    private static ManualTest tc(String id, String input, String expected, String type, String br, String msg,
            CheckedAction action) {
        return test(id, UC, "InventoryService.list/metadata/item/history", PRE, input, expected, type,
                note(FILE, br, msg, "InventoryService.list/metadata/item/history"), action);
    }

    private static TestResult list() {
        var f = fixture();
        var rows = f.service().list(manager());
        verify(f.repository()).list(BRANCH_ID);
        verify(f.repository(), never()).archive(any(), any());
        return check(rows.size() == 1 && ITEM_ID.equals(rows.get(0).id()), "rows=" + rows.size() + "; id=" + rows.get(0).id());
    }

    private static TestResult empty() {
        var f = fixture();
        when(f.repository().list(BRANCH_ID)).thenReturn(List.of());
        return check(f.service().list(manager()).isEmpty(), "rows=0");
    }

    private static TestResult metadata() {
        var f = fixture();
        var metadata = f.service().metadata(manager());
        return check(metadata.units().size() == 1 && metadata.categories().equals(List.of("Đồ uống")),
                "units=" + metadata.units().size() + "; categories=" + metadata.categories());
    }

    private static TestResult detail() {
        var f = fixture();
        var result = f.service().item(manager(), ITEM_ID);
        return check(ITEM_ID.equals(result.id()) && result.quantity().toPlainString().equals("10") && result.stockVersion() == 2L,
                "id=" + result.id() + "; quantity=" + result.quantity() + "; version=" + result.stockVersion());
    }

    private static TestResult missing() {
        var f = fixture();
        when(f.repository().get(BRANCH_ID, ITEM_ID)).thenReturn(null);
        return expectStatus(() -> f.service().item(manager(), ITEM_ID), 404, "Không tìm thấy mặt hàng kho tại cơ sở này.");
    }

    private static TestResult history() {
        var f = fixture();
        Instant from = Instant.parse("2026-11-01T00:00:00Z");
        Instant to = Instant.parse("2026-11-11T23:59:59Z");
        when(f.repository().history(BRANCH_ID, ITEM_ID, from, to, "RECEIPT", "NK")).thenReturn(List.of(
                new com.gcmb.backend.inventory.dto.InventoryDtos.StockMovementView(MOVEMENT_ID, NOW, "RECEIPT",
                        new java.math.BigDecimal("2"), new java.math.BigDecimal("12"), new java.math.BigDecimal("1000"),
                        "NK-0001 · Nhập", "Quản lý cơ sở - Hoa Bằng", "Nhập hàng", null, null)));
        var rows = f.service().history(manager(), ITEM_ID, from, to, "receipt", " NK ");
        verify(f.repository()).history(BRANCH_ID, ITEM_ID, from, to, "RECEIPT", "NK");
        return check(rows.size() == 1, "rows=" + rows.size() + "; kind=" + rows.get(0).kind());
    }

    private static TestResult invalidPeriod() {
        var f = fixture();
        Instant from = Instant.parse("2026-11-12T00:00:00Z");
        Instant to = Instant.parse("2026-11-11T00:00:00Z");
        TestResult result = expectStatus(() -> f.service().history(manager(), ITEM_ID, from, to, null, null), 400,
                "Thời điểm kết thúc phải bằng hoặc sau thời điểm bắt đầu.");
        verify(f.repository(), never()).history(any(), any(), any(), any(), any(), any());
        return result;
    }

    private static TestResult invalidKind() {
        var f = fixture();
        return expectStatus(() -> f.service().history(manager(), ITEM_ID, null, null, "TRANSFER", null), 400,
                "Loại biến động kho không hợp lệ.");
    }

    private static TestResult longSearch() {
        var f = fixture();
        return expectStatus(() -> f.service().history(manager(), ITEM_ID, null, null, null, "x".repeat(101)), 400,
                "Từ khóa tìm kiếm chỉ được chứa tối đa 100 ký tự.");
    }
}
