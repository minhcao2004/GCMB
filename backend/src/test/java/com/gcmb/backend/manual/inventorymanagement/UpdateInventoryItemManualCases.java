/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 11/11/2026
 * Description: Defines UC-56 Update Inventory Item manual unit test cases.
 */
package com.gcmb.backend.manual.inventorymanagement;

import static com.gcmb.backend.manual.inventorymanagement.CreateInventoryItemManualCases.command;
import static com.gcmb.backend.manual.inventorymanagement.InventoryManagementManualRunner.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gcmb.backend.inventory.dto.InventoryDtos.UnitOption;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;

final class UpdateInventoryItemManualCases {
    private static final String UC = "UC-56 Update Inventory Item";
    private static final String PRE = "Quản lý cơ sở qlcs.hoabang đã đăng nhập; HN_HOA_BANG đang ACTIVE; mặt hàng INV-001 tồn tại tại cơ sở này.";
    private static final String FILE = "UC-56 - Update_Inventory_Item.docx";

    private UpdateInventoryItemManualCases() {}

    static List<ManualTest> cases() {
        return List.of(
            tc("INM-019", "Đổi name=Nước suối 500ml và minimumQuantity=3.",
                "Cập nhật thành công trong đúng cơ sở; giữ lịch sử kho.", "Normal", "BR-111", "", UpdateInventoryItemManualCases::success),
            tc("INM-020", "itemId không tồn tại tại cơ sở.", "Hiển thị “Không tìm thấy mặt hàng kho tại cơ sở này.”; không cập nhật.",
                "Abnormal", "BR-111", "", UpdateInventoryItemManualCases::missing),
            tc("INM-021", "name chỉ chứa khoảng trắng.", "Hiển thị “Vui lòng bổ sung tên mặt hàng.”; không cập nhật.",
                "Abnormal", "BR-111", "MSG02", UpdateInventoryItemManualCases::blankName),
            tc("INM-022", "active=false khi mặt hàng còn gắn với món F&B đang bán.",
                "Hiển thị “Mặt hàng đang được gắn với món F&B đang bán. Hãy ngừng bán hoặc bỏ liên kết trước khi ngừng sử dụng.”; không cập nhật.",
                "Abnormal", "BR-118", "", UpdateInventoryItemManualCases::activeMenuLink),
            tc("INM-023", "Đổi unitId khi mặt hàng đã có lịch sử kho.",
                "Hiển thị “Không thể đổi đơn vị vì mặt hàng đã có lịch sử kho. Hãy tạo mặt hàng mới nếu cần dùng đơn vị khác.”; không cập nhật.",
                "Abnormal", "BR-34, BR-40, BR-118", "", UpdateInventoryItemManualCases::unitWithHistory),
            tc("INM-024", "Đổi unitId khi mặt hàng chưa có biến động.", "Cập nhật đơn vị thành công; không tạo hoặc xóa lịch sử.",
                "Normal", "BR-40, BR-118", "", UpdateInventoryItemManualCases::unitWithoutHistory),
            tc("INM-025", "Đổi sku thành mã đã tồn tại.",
                "Hiển thị “Mã mặt hàng “INV-002” đã tồn tại tại cơ sở này. Vui lòng nhập mã khác.”; không tạo trùng.",
                "Abnormal", "BR-111", "MSG38", UpdateInventoryItemManualCases::duplicate),
            tc("INM-026", "Cơ sở HN_HOA_BANG đang SUSPENDED.",
                "Hiển thị “Cơ sở GenZ Cinema Hoa Bằng đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi.”; không cập nhật.",
                "Abnormal", "BR-177", "MSG43", UpdateInventoryItemManualCases::suspended),
            tc("INM-027", "Tài khoản RECEPTIONIST gọi cập nhật.",
                "Hiển thị “Bạn không có quyền cập nhật đối với mặt hàng kho.”; không cập nhật.",
                "Abnormal", "BR-111", "MSG24", UpdateInventoryItemManualCases::denied)
        );
    }

    private static ManualTest tc(String id, String input, String expected, String type, String br, String msg,
            CheckedAction action) {
        return test(id, UC, "InventoryService.update", PRE, input, expected, type,
                note(FILE, br, msg, "InventoryService.update/validate"), action);
    }

    private static TestResult success() {
        var f = fixture();
        when(f.repository().get(BRANCH_ID, ITEM_ID)).thenReturn(new com.gcmb.backend.inventory.dto.InventoryDtos.InventoryItemView(
                ITEM_ID, "INV-001", "Nước suối 500ml", "Đồ uống", UNIT_ID, "Cái", "AUTO_ON_SALE",
                new BigDecimal("10"), new BigDecimal("3"), 2L, true, true, "AVAILABLE"));
        var result = f.service().update(manager(), ITEM_ID,
                command("INV-001", "Nước suối 500ml", "Đồ uống", UNIT_ID, "AUTO_ON_SALE", new BigDecimal("3")));
        verify(f.repository()).update(BRANCH_ID, ITEM_ID, "INV-001", "Nước suối 500ml", "Đồ uống", UNIT_ID,
                "AUTO_ON_SALE", new BigDecimal("3"), true);
        return check("Nước suối 500ml".equals(result.name()), "name=" + result.name() + "; minimum=" + result.minimumQuantity());
    }

    private static TestResult missing() {
        var f = fixture();
        when(f.repository().lockItem(BRANCH_ID, ITEM_ID)).thenReturn(null);
        return expectStatus(() -> f.service().update(manager(), ITEM_ID,
                command("INV-001", "Nước suối", "Đồ uống", UNIT_ID, "MANUAL", BigDecimal.ZERO)), 404,
                "Không tìm thấy mặt hàng kho tại cơ sở này.");
    }

    private static TestResult blankName() {
        var f = fixture();
        return expectStatus(() -> f.service().update(manager(), ITEM_ID,
                command("INV-001", "   ", "Đồ uống", UNIT_ID, "MANUAL", BigDecimal.ZERO)), 400,
                "Vui lòng bổ sung tên mặt hàng.");
    }

    private static TestResult activeMenuLink() {
        var f = fixture();
        when(f.repository().hasActiveMenuLink(ITEM_ID)).thenReturn(true);
        return expectStatus(() -> f.service().update(manager(), ITEM_ID,
                new com.gcmb.backend.inventory.dto.InventoryDtos.InventoryItemCommand("INV-001", "Nước suối", "Đồ uống",
                        UNIT_ID, "AUTO_ON_SALE", BigDecimal.ZERO, false)), 409,
                "Mặt hàng đang được gắn với món F&B đang bán. Hãy ngừng bán hoặc bỏ liên kết trước khi ngừng sử dụng.");
    }

    private static TestResult unitWithHistory() {
        var f = fixture();
        when(f.repository().units()).thenReturn(List.of(new UnitOption(UNIT_ID, "PCS", "Cái"),
                new UnitOption(OTHER_UNIT_ID, "BOX", "Hộp")));
        when(f.repository().hasMovements(ITEM_ID)).thenReturn(true);
        return expectStatus(() -> f.service().update(manager(), ITEM_ID,
                command("INV-001", "Nước suối", "Đồ uống", OTHER_UNIT_ID, "MANUAL", BigDecimal.ZERO)), 409,
                "Không thể đổi đơn vị vì mặt hàng đã có lịch sử kho. Hãy tạo mặt hàng mới nếu cần dùng đơn vị khác.");
    }

    private static TestResult unitWithoutHistory() {
        var f = fixture();
        when(f.repository().units()).thenReturn(List.of(new UnitOption(UNIT_ID, "PCS", "Cái"),
                new UnitOption(OTHER_UNIT_ID, "BOX", "Hộp")));
        when(f.repository().lockItem(BRANCH_ID, ITEM_ID)).thenReturn(locked(true, UNIT_ID, BigDecimal.ZERO, 0L));
        when(f.repository().hasMovements(ITEM_ID)).thenReturn(false);
        when(f.repository().get(BRANCH_ID, ITEM_ID)).thenReturn(item(ITEM_ID, OTHER_UNIT_ID, true, BigDecimal.ZERO, 0L, false));
        var result = f.service().update(manager(), ITEM_ID,
                command("INV-001", "Nước suối", "Đồ uống", OTHER_UNIT_ID, "MANUAL", BigDecimal.ZERO));
        verify(f.repository()).update(BRANCH_ID, ITEM_ID, "INV-001", "Nước suối", "Đồ uống", OTHER_UNIT_ID,
                "MANUAL", BigDecimal.ZERO, true);
        return check(OTHER_UNIT_ID.equals(result.unitId()), "unitId=" + result.unitId());
    }

    private static TestResult duplicate() {
        var f = fixture();
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("duplicate")).when(f.repository())
                .update(any(), any(), anyString(), anyString(), anyString(), any(), anyString(), any(), org.mockito.ArgumentMatchers.anyBoolean());
        return expectStatus(() -> f.service().update(manager(), ITEM_ID,
                command("INV-002", "Nước suối", "Đồ uống", UNIT_ID, "MANUAL", BigDecimal.ZERO)), 409,
                "Mã mặt hàng “INV-002” đã tồn tại tại cơ sở này. Vui lòng nhập mã khác.");
    }

    private static TestResult suspended() {
        var f = fixture();
        when(f.repository().activeBranch(BRANCH_ID)).thenReturn(false);
        TestResult result = expectStatus(() -> f.service().update(manager(), ITEM_ID,
                command("INV-001", "Nước suối", "Đồ uống", UNIT_ID, "MANUAL", BigDecimal.ZERO)), 403,
                "Cơ sở GenZ Cinema Hoa Bằng đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi.");
        verify(f.repository(), never()).lockItem(any(), any());
        return result;
    }

    private static TestResult denied() {
        var f = fixture();
        return expectStatus(() -> f.service().update(receptionist(), ITEM_ID,
                command("INV-001", "Nước suối", "Đồ uống", UNIT_ID, "MANUAL", BigDecimal.ZERO)), 403,
                "Bạn không có quyền cập nhật đối với mặt hàng kho.");
    }
}
