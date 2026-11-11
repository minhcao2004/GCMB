/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 11/11/2026
 * Description: Defines UC-58 Record Stock Receipt manual unit test cases.
 */
package com.gcmb.backend.manual.inventorymanagement;

import static com.gcmb.backend.manual.inventorymanagement.InventoryManagementManualRunner.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gcmb.backend.booking.exception.BookingFailure;
import com.gcmb.backend.inventory.dto.InventoryDtos.InventoryItemView;
import com.gcmb.backend.inventory.dto.InventoryDtos.ReceiptLineCommand;
import com.gcmb.backend.inventory.dto.InventoryDtos.StockReceiptCommand;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

final class RecordStockReceiptManualCases {
    private static final String UC = "UC-58 Record Stock Receipt";
    private static final String PRE = "Quản lý cơ sở qlcs.hoabang đã đăng nhập; HN_HOA_BANG đang ACTIVE; mặt hàng nhập thuộc cơ sở và đang active.";
    private static final String FILE = "UC-58 - Record_Stock_Receipt.docx";
    private static final UUID SECOND_ITEM_ID = UUID.fromString("54590000-0000-4000-8000-000000000009");
    private static final UUID SECOND_LINE_ID = UUID.fromString("54590000-0000-4000-8000-000000000010");

    private RecordStockReceiptManualCases() {}

    static List<ManualTest> cases() {
        return List.of(
            tc("INM-034", "receivedAt hợp lệ; nhà cung cấp Demo; 1 dòng số lượng=2, đơn giá=1000; không có file.",
                "Tạo NK-0001, post phiếu, cộng tồn từ 10 lên 12, thành tiền 2.000đ; không ghi thanh toán nhà cung cấp.",
                "Normal", "BR-31, BR-32, BR-33, BR-40, BR-197", "", RecordStockReceiptManualCases::single),
            tc("INM-035", "Hai mặt hàng: 2.5×1001đ và 3×2000đ.",
                "Tổng số lượng=5.5; từng dòng làm tròn HALF_UP; tổng giá trị=8.503đ; mỗi mặt hàng chỉ cộng kho một lần.",
                "Boundary", "BR-31, BR-32, BR-33, BR-40, BR-197", "", RecordStockReceiptManualCases::multiple),
            tc("INM-036", "command=null.", "Hiển thị “Vui lòng bổ sung thông tin phiếu nhập.”; không tạo phiếu hoặc biến động.",
                "Abnormal", "BR-111", "MSG02", () -> invalid(null, "Vui lòng bổ sung thông tin phiếu nhập.")),
            tc("INM-037", "Lần lượt thiếu receivedAt, supplierName và danh sách mặt hàng.",
                "Lần lượt hiển thị “Vui lòng nhập ngày nhận hàng.”, “Vui lòng bổ sung nhà cung cấp.” và “Thêm ít nhất một mặt hàng vào phiếu nhập.”; không tạo phiếu.",
                "Abnormal", "BR-111", "MSG02", RecordStockReceiptManualCases::requiredFields),
            tc("INM-038", "Dòng nhập không chọn item; sau đó cùng item xuất hiện hai lần.",
                "Lần lượt hiển thị “Vui lòng chọn mặt hàng cho từng dòng nhập.” và “Mỗi mặt hàng chỉ xuất hiện một lần trong phiếu nhập.”; không tạo phiếu.",
                "Abnormal", "BR-31, BR-111", "MSG02", RecordStockReceiptManualCases::invalidLines),
            tc("INM-039", "Mặt hàng đã inactive hoặc không thuộc HN_HOA_BANG.",
                "Hiển thị “Một mặt hàng đã ngừng dùng hoặc không thuộc cơ sở này. Hãy tải lại danh sách.”; không tạo phiếu.",
                "Abnormal", "BR-31, BR-177", "", RecordStockReceiptManualCases::inactiveItem),
            tc("INM-040", "quantity lần lượt bằng 0, -1 và 1.0001.",
                "Mỗi giá trị bị từ chối với “Số lượng nhận phải lớn hơn 0 và tối đa 3 chữ số thập phân.”; không tạo phiếu.",
                "Boundary", "BR-31", "MSG44", RecordStockReceiptManualCases::quantityBoundary),
            tc("INM-041", "unitCost lần lượt -1, 1.5 và 1000000000000000000.",
                "Mỗi giá trị bị từ chối với “Đơn giá phải là số nguyên VND không âm, tối đa 18 chữ số.”; không tạo phiếu.",
                "Boundary", "BR-33", "MSG45, MSG47", RecordStockReceiptManualCases::costBoundary),
            tc("INM-042", "Tải lên file TXT không được hỗ trợ.",
                "Hiển thị “Chỉ hỗ trợ tệp JPEG, PNG, WebP, HEIC, HEIF hoặc PDF. Vui lòng chọn tệp khác.”; không post phiếu.",
                "Abnormal", "BR-115, BR-116", "MSG78, MSG79, MSG80", RecordStockReceiptManualCases::invalidEvidence),
            tc("INM-043", "Cơ sở HN_HOA_BANG đang SUSPENDED.",
                "Hiển thị “Cơ sở GenZ Cinema Hoa Bằng đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi.”; không tạo phiếu.",
                "Abnormal", "BR-177", "MSG43", RecordStockReceiptManualCases::suspended),
            tc("INM-044", "Tài khoản RECEPTIONIST ghi nhận phiếu nhập.",
                "Hiển thị “Bạn không có quyền tạo đối với phiếu nhập kho.”; không tạo phiếu.",
                "Abnormal", "BR-31, BR-111", "MSG24", RecordStockReceiptManualCases::denied)
        );
    }

    private static ManualTest tc(String id, String input, String expected, String type, String br, String msg,
            CheckedAction action) {
        return test(id, UC, "InventoryService.receive", PRE, input, expected, type,
                note(FILE, br, msg, "InventoryService.receive/validateReceipt"), action);
    }

    private static StockReceiptCommand command(List<ReceiptLineCommand> lines) {
        return new StockReceiptCommand(NOW, "Nhà cung cấp Demo", "HD-001", lines);
    }

    private static ReceiptLineCommand line(UUID itemId, String quantity, String cost) {
        return new ReceiptLineCommand(itemId, new BigDecimal(quantity), new BigDecimal(cost));
    }

    private static TestResult single() {
        var f = fixture();
        var result = f.service().receive(manager(), command(List.of(line(ITEM_ID, "2", "1000"))), null);
        verify(f.repository()).postReceipt(RECEIPT_ID, ACTOR_ID);
        verify(f.repository()).insertReceiptMovement(org.mockito.ArgumentMatchers.eq(ITEM_ID),
                org.mockito.ArgumentMatchers.eq(LINE_ID), org.mockito.ArgumentMatchers.eq(new BigDecimal("2")),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("10")),
                org.mockito.ArgumentMatchers.argThat(value -> value.compareTo(new BigDecimal("1000")) == 0),
                org.mockito.ArgumentMatchers.eq(ACTOR_ID),
                org.mockito.ArgumentMatchers.eq("Nhập hàng theo phiếu NK-0001"));
        return check(result.itemCount() == 1 && result.totalQuantity().compareTo(new BigDecimal("2")) == 0
                        && result.totalValue().compareTo(new BigDecimal("2000")) == 0
                        && result.items().get(0).quantityAfter().compareTo(new BigDecimal("12")) == 0,
                "code=" + result.code() + "; quantity=" + result.totalQuantity() + "; value=" + result.totalValue()
                        + "; after=" + result.items().get(0).quantityAfter());
    }

    private static TestResult multiple() {
        var f = fixture();
        InventoryItemView second = new InventoryItemView(SECOND_ITEM_ID, "INV-002", "Bắp", "Nguyên liệu", UNIT_ID,
                "Cái", "MANUAL", new BigDecimal("4"), BigDecimal.ONE, 1L, true, true, "AVAILABLE");
        when(f.repository().get(BRANCH_ID, SECOND_ITEM_ID)).thenReturn(second);
        when(f.repository().lockItem(BRANCH_ID, SECOND_ITEM_ID)).thenReturn(new com.gcmb.backend.inventory.repository.InventoryRepository.LockedItem(
                SECOND_ITEM_ID, UNIT_ID, true, new BigDecimal("4"), 1L));
        when(f.repository().insertReceiptLine(any(), any(), any())).thenReturn(LINE_ID, SECOND_LINE_ID);
        var result = f.service().receive(manager(), command(List.of(line(ITEM_ID, "2.5", "1001"),
                line(SECOND_ITEM_ID, "3", "2000"))), null);
        return check(result.itemCount() == 2 && result.totalQuantity().compareTo(new BigDecimal("5.5")) == 0
                        && result.totalValue().compareTo(new BigDecimal("8503")) == 0,
                "items=" + result.itemCount() + "; quantity=" + result.totalQuantity() + "; value=" + result.totalValue());
    }

    private static TestResult invalid(StockReceiptCommand input, String expected) {
        var f = fixture();
        TestResult result = expectStatus(() -> f.service().receive(manager(), input, null), 400, expected);
        verify(f.repository(), never()).insertReceipt(any(), any(), any(), any(), any(), any(), any(), any(), any());
        return result;
    }

    private static TestResult requiredFields() {
        TestResult date = invalid(new StockReceiptCommand(null, "Nhà cung cấp", null, List.of(line(ITEM_ID, "1", "0"))),
                "Vui lòng nhập ngày nhận hàng.");
        TestResult supplier = invalid(new StockReceiptCommand(NOW, "   ", null, List.of(line(ITEM_ID, "1", "0"))),
                "Vui lòng bổ sung nhà cung cấp.");
        TestResult items = invalid(new StockReceiptCommand(NOW, "Nhà cung cấp", null, List.of()),
                "Thêm ít nhất một mặt hàng vào phiếu nhập.");
        return check(date.passed() && supplier.passed() && items.passed(),
                "date=" + date.actual() + "; supplier=" + supplier.actual() + "; items=" + items.actual());
    }

    private static TestResult invalidLines() {
        TestResult missing = invalid(command(List.of(line(null, "1", "0"))), "Vui lòng chọn mặt hàng cho từng dòng nhập.");
        TestResult duplicate = invalid(command(List.of(line(ITEM_ID, "1", "0"), line(ITEM_ID, "2", "0"))),
                "Mỗi mặt hàng chỉ xuất hiện một lần trong phiếu nhập.");
        return check(missing.passed() && duplicate.passed(), "missing=" + missing.actual() + "; duplicate=" + duplicate.actual());
    }

    private static TestResult inactiveItem() {
        var f = fixture();
        when(f.repository().get(BRANCH_ID, ITEM_ID)).thenReturn(item(ITEM_ID, UNIT_ID, false, new BigDecimal("10"), 2L, true));
        return expectStatus(() -> f.service().receive(manager(), command(List.of(line(ITEM_ID, "1", "0"))), null), 409,
                "Một mặt hàng đã ngừng dùng hoặc không thuộc cơ sở này. Hãy tải lại danh sách.");
    }

    private static TestResult quantityBoundary() {
        String expected = "Số lượng nhận phải lớn hơn 0 và tối đa 3 chữ số thập phân.";
        TestResult zero = invalid(command(List.of(line(ITEM_ID, "0", "0"))), expected);
        TestResult negative = invalid(command(List.of(line(ITEM_ID, "-1", "0"))), expected);
        TestResult scale = invalid(command(List.of(line(ITEM_ID, "1.0001", "0"))), expected);
        return check(zero.passed() && negative.passed() && scale.passed(),
                "zero=" + zero.actual() + "; negative=" + negative.actual() + "; scale4=" + scale.actual());
    }

    private static TestResult costBoundary() {
        String expected = "Đơn giá phải là số nguyên VND không âm, tối đa 18 chữ số.";
        TestResult negative = invalid(command(List.of(line(ITEM_ID, "1", "-1"))), expected);
        TestResult decimal = invalid(command(List.of(line(ITEM_ID, "1", "1.5"))), expected);
        TestResult large = invalid(command(List.of(line(ITEM_ID, "1", "1000000000000000000"))), expected);
        return check(negative.passed() && decimal.passed() && large.passed(),
                "negative=" + negative.actual() + "; decimal=" + decimal.actual() + "; large=" + large.actual());
    }

    private static TestResult invalidEvidence() {
        var f = fixture();
        MultipartFile file = mock(MultipartFile.class);
        when(f.storage().store(file)).thenThrow(new BookingFailure(400, "image",
                "Chỉ hỗ trợ tệp JPEG, PNG, WebP, HEIC, HEIF hoặc PDF. Vui lòng chọn tệp khác."));
        return expectStatus(() -> f.service().receive(manager(), command(List.of(line(ITEM_ID, "1", "0"))), file), 400,
                "Chỉ hỗ trợ tệp JPEG, PNG, WebP, HEIC, HEIF hoặc PDF. Vui lòng chọn tệp khác.");
    }

    private static TestResult suspended() {
        var f = fixture();
        when(f.repository().activeBranch(BRANCH_ID)).thenReturn(false);
        return expectStatus(() -> f.service().receive(manager(), command(List.of(line(ITEM_ID, "1", "0"))), null), 403,
                "Cơ sở GenZ Cinema Hoa Bằng đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi.");
    }

    private static TestResult denied() {
        var f = fixture();
        return expectStatus(() -> f.service().receive(receptionist(), command(List.of(line(ITEM_ID, "1", "0"))), null), 403,
                "Bạn không có quyền tạo đối với phiếu nhập kho.");
    }
}
