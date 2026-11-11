/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 11/11/2026
 * Description: Defines UC-54 Create Inventory Item manual unit test cases.
 */
package com.gcmb.backend.manual.inventorymanagement;

import static com.gcmb.backend.manual.inventorymanagement.InventoryManagementManualRunner.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gcmb.backend.inventory.dto.InventoryDtos.InventoryItemCommand;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;

final class CreateInventoryItemManualCases {
    private static final String UC = "UC-54 Create Inventory Item";
    private static final String PRE = "Quản lý cơ sở qlcs.hoabang đã đăng nhập; được phân công tại cơ sở HN_HOA_BANG đang ACTIVE; danh mục đơn vị tính đã có dữ liệu.";
    private static final String FILE = "UC-54 - Create_Inventory_Item.docx";

    private CreateInventoryItemManualCases() {}

    static List<ManualTest> cases() {
        return List.of(
            tc("INM-001", "sku=inv-002, name=Nước ngọt, category=Đồ uống, unit=Cái, tracking=AUTO_ON_SALE, minimum=2.",
                "Tạo mặt hàng thành công; SKU chuẩn hóa thành INV-002; tồn ban đầu bằng 0.", "Normal", "BR-111", "",
                CreateInventoryItemManualCases::success),
            tc("INM-002", "command=null.", "Hiển thị “Vui lòng bổ sung thông tin mặt hàng.”; không tạo dữ liệu.",
                "Abnormal", "BR-111", "MSG02", () -> invalid(null, "Vui lòng bổ sung thông tin mặt hàng.")),
            tc("INM-003", "sku chỉ chứa khoảng trắng.", "Hiển thị “Vui lòng bổ sung mã mặt hàng.”; không tạo dữ liệu.",
                "Abnormal", "BR-111", "MSG02", () -> invalid(command("   ", "Nước suối", "Đồ uống", UNIT_ID,
                        "AUTO_ON_SALE", BigDecimal.ZERO), "Vui lòng bổ sung mã mặt hàng.")),
            tc("INM-004", "sku=INV 002 có khoảng trắng.",
                "Hiển thị “Mã mặt hàng chỉ dùng chữ không dấu, số, dấu gạch ngang hoặc gạch dưới.”; không tạo dữ liệu.",
                "Abnormal", "BR-111", "", () -> invalid(command("INV 002", "Nước suối", "Đồ uống", UNIT_ID,
                        "AUTO_ON_SALE", BigDecimal.ZERO), "Mã mặt hàng chỉ dùng chữ không dấu, số, dấu gạch ngang hoặc gạch dưới.")),
            tc("INM-005", "name chỉ chứa khoảng trắng.", "Hiển thị “Vui lòng bổ sung tên mặt hàng.”; không tạo dữ liệu.",
                "Abnormal", "BR-111", "MSG02", () -> invalid(command("INV-002", "   ", "Đồ uống", UNIT_ID,
                        "AUTO_ON_SALE", BigDecimal.ZERO), "Vui lòng bổ sung tên mặt hàng.")),
            tc("INM-006", "unitId không có trong danh mục.", "Hiển thị “Vui lòng chọn đơn vị tính hợp lệ.”; không tạo dữ liệu.",
                "Abnormal", "BR-111", "", () -> invalid(command("INV-002", "Nước suối", "Đồ uống", OTHER_UNIT_ID,
                        "AUTO_ON_SALE", BigDecimal.ZERO), "Vui lòng chọn đơn vị tính hợp lệ.")),
            tc("INM-007", "trackingMode=UNKNOWN.", "Hiển thị “Vui lòng chọn cách theo dõi tồn hợp lệ.”; không tạo dữ liệu.",
                "Abnormal", "BR-111", "", () -> invalid(command("INV-002", "Nước suối", "Đồ uống", UNIT_ID,
                        "UNKNOWN", BigDecimal.ZERO), "Vui lòng chọn cách theo dõi tồn hợp lệ.")),
            tc("INM-008", "minimumQuantity=-0.001 và 0.0001.",
                "Mỗi giá trị đều bị từ chối với “Ngưỡng tồn thấp phải lớn hơn hoặc bằng 0 và tối đa 3 chữ số thập phân.”; không tạo dữ liệu.",
                "Boundary", "BR-111", "MSG45", CreateInventoryItemManualCases::minimumBoundary),
            tc("INM-009", "sku=INV-001 đã tồn tại tại cơ sở.",
                "Hiển thị “Mã mặt hàng “INV-001” đã tồn tại tại cơ sở này. Vui lòng nhập mã khác.”; không tạo trùng.",
                "Abnormal", "BR-111", "MSG38", CreateInventoryItemManualCases::duplicate)
        );
    }

    private static ManualTest tc(String id, String input, String expected, String type, String br, String msg,
            CheckedAction action) {
        return test(id, UC, "InventoryService.create", PRE, input, expected, type,
                note(FILE, br, msg, "InventoryService.create/validate"), action);
    }

    static InventoryItemCommand command(String sku, String name, String category, java.util.UUID unitId,
            String tracking, BigDecimal minimum) {
        return new InventoryItemCommand(sku, name, category, unitId, tracking, minimum, true);
    }

    private static TestResult success() {
        var f = fixture();
        var result = f.service().create(manager(), command("inv-002", "Nước ngọt", "Đồ uống", UNIT_ID,
                "AUTO_ON_SALE", new BigDecimal("2")));
        verify(f.repository()).create(BRANCH_ID, "INV-002", "Nước ngọt", "Đồ uống", UNIT_ID,
                "AUTO_ON_SALE", new BigDecimal("2"));
        return check(NEW_ITEM_ID.equals(result.id()) && result.quantity().compareTo(BigDecimal.ZERO) == 0,
                "id=" + result.id() + "; quantity=" + result.quantity());
    }

    private static TestResult invalid(InventoryItemCommand input, String expected) {
        var f = fixture();
        TestResult result = expectStatus(() -> f.service().create(manager(), input), 400, expected);
        verify(f.repository(), never()).create(any(), anyString(), anyString(), anyString(), any(), anyString(), any());
        return result;
    }

    private static TestResult minimumBoundary() {
        TestResult negative = invalid(command("INV-002", "Nước suối", "Đồ uống", UNIT_ID, "MANUAL",
                new BigDecimal("-0.001")), "Ngưỡng tồn thấp phải lớn hơn hoặc bằng 0 và tối đa 3 chữ số thập phân.");
        TestResult scale = invalid(command("INV-002", "Nước suối", "Đồ uống", UNIT_ID, "MANUAL",
                new BigDecimal("0.0001")), "Ngưỡng tồn thấp phải lớn hơn hoặc bằng 0 và tối đa 3 chữ số thập phân.");
        return check(negative.passed() && scale.passed(), "negative=" + negative.actual() + "; scale4=" + scale.actual());
    }

    private static TestResult duplicate() {
        var f = fixture();
        when(f.repository().create(any(), anyString(), anyString(), anyString(), any(), anyString(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        return expectStatus(() -> f.service().create(manager(), command("INV-001", "Nước suối", "Đồ uống",
                UNIT_ID, "AUTO_ON_SALE", BigDecimal.ZERO)), 409,
                "Mã mặt hàng “INV-001” đã tồn tại tại cơ sở này. Vui lòng nhập mã khác.");
    }
}
