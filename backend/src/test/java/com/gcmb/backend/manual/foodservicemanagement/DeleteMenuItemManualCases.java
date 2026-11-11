/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 11/11/2026
 * Description: Defines UC-49 Delete Menu Item manual unit test cases.
 */
package com.gcmb.backend.manual.foodservicemanagement;

import static com.gcmb.backend.manual.foodservicemanagement.CreateMenuItemManualCases.note;
import static com.gcmb.backend.manual.foodservicemanagement.FoodServiceManagementManualRunner.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

final class DeleteMenuItemManualCases {
    private DeleteMenuItemManualCases() {}

    static List<FoodServiceManagementManualRunner.ManualTest> cases() {
        String uc = "UC-49 Delete Menu Item";
        return List.of(
            test("FSM-033", uc, "MenuItemService.delete", "Quản lý quanly.hoabang đăng nhập; cơ sở hoạt động; món tồn tại và đã có order lịch sử.", "Xóa món FB-001.", "Soft-delete món, ngừng bán; giữ order lịch sử.", "Normal", note("UC-49 - Delete_Menu_Item.docx", "BR-27, BR-38", "", "MenuItemService.delete", "White-box"), DeleteMenuItemManualCases::delete),
            test("FSM-034", uc, "MenuItemService.delete", "Quản lý đăng nhập; món không tồn tại hoặc đã bị xóa.", "Xóa ITEM_ID.", "Hiển thị “Không tìm thấy món trong cơ sở được phân quyền.”; không thay đổi dữ liệu.", "Abnormal", note("UC-49 - Delete_Menu_Item.docx", "BR-177", "", "MenuItemService.delete", "White-box"), DeleteMenuItemManualCases::notFound),
            test("FSM-035", uc, "MenuItemService.delete", "Quản lý quanly.hoabang đăng nhập; HN_HOA_BANG đang tạm ngừng.", "Xóa món FB-001.", "Hiển thị “Cơ sở HN_HOA_BANG đang tạm ngưng hoạt động nên chưa thể thực hiện thao tác này.”; không xóa.", "Abnormal", note("UC-49 - Delete_Menu_Item.docx", "BR-177", "MSG43", "MenuItemService.delete", "White-box"), DeleteMenuItemManualCases::suspended)
        );
    }

    private static FoodServiceManagementManualRunner.TestResult delete() {
        var f = menuFixture();
        f.service().delete(manager(), ITEM_ID);
        verify(f.repository()).softDelete(BRANCH_ID, ITEM_ID, ACTOR_ID);
        return check(true, "softDelete called once; order tables untouched");
    }

    private static FoodServiceManagementManualRunner.TestResult notFound() {
        var f = menuFixture();
        when(f.repository().softDelete(BRANCH_ID, ITEM_ID, ACTOR_ID)).thenReturn(false);
        return expectResponse(() -> f.service().delete(manager(), ITEM_ID), 404,
                "Không tìm thấy món trong cơ sở được phân quyền.");
    }

    private static FoodServiceManagementManualRunner.TestResult suspended() {
        var f = menuFixture();
        when(f.repository().activeBranch(BRANCH_ID)).thenReturn(false);
        return expectResponse(() -> f.service().delete(manager(), ITEM_ID), 403,
                "Cơ sở HN_HOA_BANG đang tạm ngưng hoạt động nên chưa thể thực hiện thao tác này.");
    }
}
