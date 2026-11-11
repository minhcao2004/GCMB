/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 11/11/2026
 * Description: Defines UC-59 Adjust Stock Quantity manual unit test cases.
 */
package com.gcmb.backend.manual.inventorymanagement;

import static com.gcmb.backend.manual.inventorymanagement.InventoryManagementManualRunner.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gcmb.backend.booking.exception.BookingFailure;
import com.gcmb.backend.inventory.dto.InventoryDtos.StockAdjustmentCommand;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

final class AdjustStockQuantityManualCases {
    private static final String UC = "UC-59 Adjust Stock Quantity";
    private static final String PRE = "Quản lý cơ sở qlcs.hoabang đã đăng nhập; HN_HOA_BANG đang ACTIVE; INV-001 tồn tại, active, có tồn hiển thị 10 và stockVersion=2.";
    private static final String FILE = "UC-59 - Adjust_Stock_Quantity.docx";

    private AdjustStockQuantityManualCases() {}

    static List<ManualTest> cases() {
        return List.of(
            tc("INM-045", "expected=10/version=2, actual=12, reason=Kiểm kê tăng.",
                "Ghi một biến động ADJUSTMENT delta=+2, before=10, after=12, lưu người, thời gian và lý do.",
                "Normal", "BR-34, BR-35, BR-40, BR-189", "", AdjustStockQuantityManualCases::increase),
            tc("INM-046", "expected=10/version=2, actual=7.5, reason=Hao hụt.",
                "Ghi một biến động ADJUSTMENT delta=-2.5, before=10, after=7.5 và giữ lịch sử.",
                "Normal", "BR-34, BR-35, BR-39, BR-40, BR-189", "", AdjustStockQuantityManualCases::decrease),
            tc("INM-047", "Lần lượt thiếu itemId, occurredAt và expected stock/version.",
                "Lần lượt hiển thị “Vui lòng chọn mặt hàng cần kiểm kê.”, “Vui lòng nhập thời điểm kiểm kê.” và “Không tìm thấy số tồn ban đầu. Hãy tải lại mặt hàng và kiểm tra lại.”; không ghi biến động.",
                "Abnormal", "BR-111, BR-189", "MSG02", AdjustStockQuantityManualCases::requiredFields),
            tc("INM-048", "actualQuantity lần lượt -0.001 và 1.0001.",
                "Mỗi giá trị bị từ chối với “Tồn thực tế phải lớn hơn hoặc bằng 0 và tối đa 3 chữ số thập phân.”; không ghi biến động.",
                "Boundary", "BR-189", "MSG45", AdjustStockQuantityManualCases::actualBoundary),
            tc("INM-049", "reason chỉ có khoảng trắng; sau đó reason dài 1001 ký tự.",
                "Lần lượt hiển thị “Vui lòng bổ sung lý do điều chỉnh.” và “Lý do điều chỉnh chỉ được chứa tối đa 1000 ký tự.”; không ghi biến động.",
                "Boundary", "BR-34, BR-111", "MSG02, MSG03", AdjustStockQuantityManualCases::reasonBoundary),
            tc("INM-050", "Mặt hàng không tồn tại; sau đó mặt hàng đã inactive.",
                "Lần lượt hiển thị “Không tìm thấy mặt hàng kho tại cơ sở này.” và “Mặt hàng đã ngừng sử dụng nên không thể điều chỉnh tồn.”; không ghi biến động.",
                "Abnormal", "BR-34, BR-40", "", AdjustStockQuantityManualCases::missingOrInactive),
            tc("INM-051", "Màn hình giữ expected=10/version=2 nhưng DB đã thành 11/version=3.",
                "Hiển thị “Tồn kho đã thay đổi từ 10 thành 11 Cái. Vui lòng kiểm tra lại trước khi xác nhận điều chỉnh.”; không ghi biến động.",
                "Abnormal", "BR-190", "MSG77", AdjustStockQuantityManualCases::stale),
            tc("INM-052", "actualQuantity=10 bằng tồn hiện tại.",
                "Hiển thị “Tồn thực tế không thay đổi. Không cần ghi nhận điều chỉnh.”; không ghi biến động.",
                "Abnormal", "BR-35", "", AdjustStockQuantityManualCases::unchanged),
            tc("INM-053", "Tải lên file TXT không được hỗ trợ.",
                "Hiển thị “Chỉ hỗ trợ tệp JPEG, PNG, WebP, HEIC, HEIF hoặc PDF. Vui lòng chọn tệp khác.”; không ghi biến động.",
                "Abnormal", "BR-115, BR-116", "MSG78, MSG79, MSG80", AdjustStockQuantityManualCases::invalidEvidence),
            tc("INM-054", "Cơ sở HN_HOA_BANG đang SUSPENDED.",
                "Hiển thị “Cơ sở GenZ Cinema Hoa Bằng đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi.”; không ghi biến động.",
                "Abnormal", "BR-177", "MSG43", AdjustStockQuantityManualCases::suspended)
        );
    }

    private static ManualTest tc(String id, String input, String expected, String type, String br, String msg,
            CheckedAction action) {
        return test(id, UC, "InventoryService.adjust", PRE, input, expected, type,
                note(FILE, br, msg, "InventoryService.adjust/InventoryRepository.insertAdjustment"), action);
    }

    private static StockAdjustmentCommand command(BigDecimal actual, String reason) {
        return new StockAdjustmentCommand(ITEM_ID, NOW, 2L, new BigDecimal("10"), actual, reason);
    }

    private static TestResult increase() {
        var f = fixture();
        var result = f.service().adjust(manager(), command(new BigDecimal("12"), "Kiểm kê tăng"), null);
        verify(f.repository()).insertAdjustment(ITEM_ID, new BigDecimal("2"), new BigDecimal("10"), NOW, ACTOR_ID,
                "Kiểm kê tăng", null, null, null);
        return check(result.quantityDelta().compareTo(new BigDecimal("2")) == 0
                        && result.quantityAfter().compareTo(new BigDecimal("12")) == 0,
                "delta=" + result.quantityDelta() + "; after=" + result.quantityAfter());
    }

    private static TestResult decrease() {
        var f = fixture();
        when(f.repository().movement(BRANCH_ID, ITEM_ID, MOVEMENT_ID)).thenReturn(movement(new BigDecimal("-2.5"), new BigDecimal("7.5")));
        var result = f.service().adjust(manager(), command(new BigDecimal("7.5"), "Hao hụt"), null);
        verify(f.repository()).insertAdjustment(ITEM_ID, new BigDecimal("-2.5"), new BigDecimal("10"), NOW, ACTOR_ID,
                "Hao hụt", null, null, null);
        return check(result.quantityDelta().compareTo(new BigDecimal("-2.5")) == 0,
                "delta=" + result.quantityDelta() + "; after=" + result.quantityAfter());
    }

    private static TestResult requiredFields() {
        var f1 = fixture();
        TestResult item = expectStatus(() -> f1.service().adjust(manager(),
                new StockAdjustmentCommand(null, NOW, 2L, BigDecimal.TEN, BigDecimal.ONE, "Kiểm kê"), null), 400,
                "Vui lòng chọn mặt hàng cần kiểm kê.");
        var f2 = fixture();
        TestResult time = expectStatus(() -> f2.service().adjust(manager(),
                new StockAdjustmentCommand(ITEM_ID, null, 2L, BigDecimal.TEN, BigDecimal.ONE, "Kiểm kê"), null), 400,
                "Vui lòng nhập thời điểm kiểm kê.");
        var f3 = fixture();
        TestResult stock = expectStatus(() -> f3.service().adjust(manager(),
                new StockAdjustmentCommand(ITEM_ID, NOW, null, null, BigDecimal.ONE, "Kiểm kê"), null), 400,
                "Không tìm thấy số tồn ban đầu. Hãy tải lại mặt hàng và kiểm tra lại.");
        return check(item.passed() && time.passed() && stock.passed(),
                "item=" + item.actual() + "; time=" + time.actual() + "; stock=" + stock.actual());
    }

    private static TestResult actualBoundary() {
        var f1 = fixture();
        String expected = "Tồn thực tế phải lớn hơn hoặc bằng 0 và tối đa 3 chữ số thập phân.";
        TestResult negative = expectStatus(() -> f1.service().adjust(manager(), command(new BigDecimal("-0.001"), "Kiểm kê"), null), 400, expected);
        var f2 = fixture();
        TestResult scale = expectStatus(() -> f2.service().adjust(manager(), command(new BigDecimal("1.0001"), "Kiểm kê"), null), 400, expected);
        return check(negative.passed() && scale.passed(), "negative=" + negative.actual() + "; scale4=" + scale.actual());
    }

    private static TestResult reasonBoundary() {
        var f1 = fixture();
        TestResult blank = expectStatus(() -> f1.service().adjust(manager(), command(new BigDecimal("12"), "   "), null), 400,
                "Vui lòng bổ sung lý do điều chỉnh.");
        var f2 = fixture();
        TestResult longReason = expectStatus(() -> f2.service().adjust(manager(), command(new BigDecimal("12"), "x".repeat(1001)), null), 400,
                "Lý do điều chỉnh chỉ được chứa tối đa 1000 ký tự.");
        return check(blank.passed() && longReason.passed(), "blank=" + blank.actual() + "; length1001=" + longReason.actual());
    }

    private static TestResult missingOrInactive() {
        var f1 = fixture();
        when(f1.repository().lockItem(BRANCH_ID, ITEM_ID)).thenReturn(null);
        TestResult missing = expectStatus(() -> f1.service().adjust(manager(), command(new BigDecimal("12"), "Kiểm kê"), null), 404,
                "Không tìm thấy mặt hàng kho tại cơ sở này.");
        var f2 = fixture();
        when(f2.repository().lockItem(BRANCH_ID, ITEM_ID)).thenReturn(locked(false, UNIT_ID, new BigDecimal("10"), 2L));
        TestResult inactive = expectStatus(() -> f2.service().adjust(manager(), command(new BigDecimal("12"), "Kiểm kê"), null), 409,
                "Mặt hàng đã ngừng sử dụng nên không thể điều chỉnh tồn.");
        return check(missing.passed() && inactive.passed(), "missing=" + missing.actual() + "; inactive=" + inactive.actual());
    }

    private static TestResult stale() {
        var f = fixture();
        when(f.repository().lockItem(BRANCH_ID, ITEM_ID)).thenReturn(locked(true, UNIT_ID, new BigDecimal("11"), 3L));
        return expectStatus(() -> f.service().adjust(manager(), command(new BigDecimal("12"), "Kiểm kê"), null), 409,
                "Tồn kho đã thay đổi từ 10 thành 11 Cái. Vui lòng kiểm tra lại trước khi xác nhận điều chỉnh.");
    }

    private static TestResult unchanged() {
        var f = fixture();
        TestResult result = expectStatus(() -> f.service().adjust(manager(), command(new BigDecimal("10"), "Kiểm kê"), null), 400,
                "Tồn thực tế không thay đổi. Không cần ghi nhận điều chỉnh.");
        verify(f.repository(), never()).insertAdjustment(any(), any(), any(), any(), any(), any(), any(), any(), any());
        return result;
    }

    private static TestResult invalidEvidence() {
        var f = fixture();
        MultipartFile file = mock(MultipartFile.class);
        when(f.storage().store(file)).thenThrow(new BookingFailure(400, "image",
                "Chỉ hỗ trợ tệp JPEG, PNG, WebP, HEIC, HEIF hoặc PDF. Vui lòng chọn tệp khác."));
        return expectStatus(() -> f.service().adjust(manager(), command(new BigDecimal("12"), "Kiểm kê"), file), 400,
                "Chỉ hỗ trợ tệp JPEG, PNG, WebP, HEIC, HEIF hoặc PDF. Vui lòng chọn tệp khác.");
    }

    private static TestResult suspended() {
        var f = fixture();
        when(f.repository().activeBranch(BRANCH_ID)).thenReturn(false);
        return expectStatus(() -> f.service().adjust(manager(), command(new BigDecimal("12"), "Kiểm kê"), null), 403,
                "Cơ sở GenZ Cinema Hoa Bằng đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi.");
    }
}
