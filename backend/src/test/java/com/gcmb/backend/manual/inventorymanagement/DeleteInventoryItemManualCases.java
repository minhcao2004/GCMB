/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 11/11/2026
 * Description: Defines UC-57 Delete Inventory Item manual unit test cases.
 */
package com.gcmb.backend.manual.inventorymanagement;

import static com.gcmb.backend.manual.inventorymanagement.InventoryManagementManualRunner.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

final class DeleteInventoryItemManualCases {
    private static final String UC = "UC-57 Delete Inventory Item";
    private static final String PRE = "Quản lý cơ sở qlcs.hoabang đã đăng nhập; HN_HOA_BANG đang ACTIVE; mặt hàng INV-001 tồn tại tại cơ sở này.";
    private static final String FILE = "UC-57 - Delete_Inventory_Item.docx";

    private DeleteInventoryItemManualCases() {}

    static List<ManualTest> cases() {
        return List.of(
            tc("INM-028", "Xóa mặt hàng ACTIVE không còn liên kết F&B đang bán.",
                "Mặt hàng bị loại khỏi sử dụng bằng archive; lịch sử kho không bị xóa.", "Normal", "BR-118", "", DeleteInventoryItemManualCases::success),
            tc("INM-029", "Xóa lại mặt hàng đã inactive.", "Trả thành công idempotent; không archive lần hai và giữ lịch sử.",
                "Abnormal", "BR-118", "", DeleteInventoryItemManualCases::alreadyInactive),
            tc("INM-030", "itemId không tồn tại tại cơ sở.", "Hiển thị “Không tìm thấy mặt hàng kho tại cơ sở này.”; không archive.",
                "Abnormal", "BR-118", "", DeleteInventoryItemManualCases::missing),
            tc("INM-031", "Mặt hàng còn gắn với món F&B đang bán.",
                "Hiển thị “Mặt hàng đang được gắn với món F&B đang bán. Hãy ngừng bán hoặc bỏ liên kết trước khi xóa.”; không archive.",
                "Abnormal", "BR-118", "", DeleteInventoryItemManualCases::linked),
            tc("INM-032", "Cơ sở HN_HOA_BANG đang SUSPENDED.",
                "Hiển thị “Cơ sở GenZ Cinema Hoa Bằng đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi.”; không archive.",
                "Abnormal", "BR-177", "MSG43", DeleteInventoryItemManualCases::suspended),
            tc("INM-033", "Tài khoản RECEPTIONIST gọi xóa.",
                "Hiển thị “Bạn không có quyền xóa đối với mặt hàng kho.”; không archive.",
                "Abnormal", "BR-118", "MSG24", DeleteInventoryItemManualCases::denied)
        );
    }

    private static ManualTest tc(String id, String input, String expected, String type, String br, String msg,
            CheckedAction action) {
        return test(id, UC, "InventoryService.delete", PRE, input, expected, type,
                note(FILE, br, msg, "InventoryService.delete/InventoryRepository.archive"), action);
    }

    private static TestResult success() {
        var f = fixture();
        f.service().delete(manager(), ITEM_ID);
        verify(f.repository()).archive(BRANCH_ID, ITEM_ID);
        return check(true, "archive requested once; no physical delete operation exists");
    }

    private static TestResult alreadyInactive() {
        var f = fixture();
        when(f.repository().lockItem(BRANCH_ID, ITEM_ID)).thenReturn(locked(false, UNIT_ID, new BigDecimal("10"), 2L));
        f.service().delete(manager(), ITEM_ID);
        verify(f.repository(), never()).archive(any(), any());
        return check(true, "inactive item returned without second archive");
    }

    private static TestResult missing() {
        var f = fixture();
        when(f.repository().lockItem(BRANCH_ID, ITEM_ID)).thenReturn(null);
        TestResult result = expectStatus(() -> f.service().delete(manager(), ITEM_ID), 404,
                "Không tìm thấy mặt hàng kho tại cơ sở này.");
        verify(f.repository(), never()).archive(any(), any());
        return result;
    }

    private static TestResult linked() {
        var f = fixture();
        when(f.repository().hasActiveMenuLink(ITEM_ID)).thenReturn(true);
        TestResult result = expectStatus(() -> f.service().delete(manager(), ITEM_ID), 409,
                "Mặt hàng đang được gắn với món F&B đang bán. Hãy ngừng bán hoặc bỏ liên kết trước khi xóa.");
        verify(f.repository(), never()).archive(any(), any());
        return result;
    }

    private static TestResult suspended() {
        var f = fixture();
        when(f.repository().activeBranch(BRANCH_ID)).thenReturn(false);
        return expectStatus(() -> f.service().delete(manager(), ITEM_ID), 403,
                "Cơ sở GenZ Cinema Hoa Bằng đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi.");
    }

    private static TestResult denied() {
        var f = fixture();
        return expectStatus(() -> f.service().delete(receptionist(), ITEM_ID), 403,
                "Bạn không có quyền xóa đối với mặt hàng kho.");
    }
}
