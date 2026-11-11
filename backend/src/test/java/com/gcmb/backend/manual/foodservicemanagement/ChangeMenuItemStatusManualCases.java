/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 11/11/2026
 * Description: Defines UC-48 Change Menu Item Status manual unit test cases.
 */
package com.gcmb.backend.manual.foodservicemanagement;

import static com.gcmb.backend.manual.foodservicemanagement.CreateMenuItemManualCases.note;
import static com.gcmb.backend.manual.foodservicemanagement.FoodServiceManagementManualRunner.*;
import static org.mockito.Mockito.when;

import com.gcmb.backend.inventory.dto.MenuItemDtos.MenuItemStatusCommand;
import java.util.List;

final class ChangeMenuItemStatusManualCases {
    private ChangeMenuItemStatusManualCases() {}

    static List<FoodServiceManagementManualRunner.ManualTest> cases() {
        String uc = "UC-48 Change Menu Item Status";
        String pre = "Quản lý quanly.hoabang đăng nhập; HN_HOA_BANG hoạt động; món FB-001 tồn tại.";
        return List.of(
            test("FSM-029", uc, "MenuItemService.changeStatus", pre + " Món đang bán và đã có order lịch sử.", "active=false.", "Món ngừng bán; dữ liệu order lịch sử được giữ nguyên.", "Normal", note("UC-48 - Change_Menu_Item_Status.docx", "BR-38", "", "MenuItemService.changeStatus", "White-box"), () -> status(false)),
            test("FSM-030", uc, "MenuItemService.changeStatus", pre + " Món đang ngừng bán.", "active=true.", "Món được bán lại.", "Normal", note("UC-48 - Change_Menu_Item_Status.docx", "BR-38", "", "MenuItemService.changeStatus", "Black-box"), () -> status(true)),
            test("FSM-031", uc, "MenuItemService.changeStatus", pre, "active=null.", "Hiển thị “Vui lòng chọn trạng thái bán hợp lệ.”; không cập nhật.", "Abnormal", note("UC-48 - Change_Menu_Item_Status.docx", "BR-111", "MSG02", "MenuItemService.changeStatus", "White-box"), ChangeMenuItemStatusManualCases::nullStatus),
            test("FSM-032", uc, "MenuItemService.changeStatus", "Quản lý đăng nhập; món không tồn tại hoặc thuộc cơ sở khác.", "active=false.", "Hiển thị “Không tìm thấy món trong cơ sở được phân quyền.”; không cập nhật.", "Abnormal", note("UC-48 - Change_Menu_Item_Status.docx", "BR-177", "", "MenuItemService.changeStatus", "White-box"), ChangeMenuItemStatusManualCases::notFound)
        );
    }

    private static FoodServiceManagementManualRunner.TestResult status(boolean active) {
        var f = menuFixture();
        var row = f.service().changeStatus(manager(), ITEM_ID, new MenuItemStatusCommand(active));
        return check(row.active() == active, "active=" + row.active());
    }

    private static FoodServiceManagementManualRunner.TestResult nullStatus() {
        var f = menuFixture();
        return expectResponse(() -> f.service().changeStatus(manager(), ITEM_ID, new MenuItemStatusCommand(null)),
                400, "Vui lòng chọn trạng thái bán hợp lệ.");
    }

    private static FoodServiceManagementManualRunner.TestResult notFound() {
        var f = menuFixture();
        when(f.repository().changeStatus(BRANCH_ID, ITEM_ID, false)).thenReturn(null);
        return expectResponse(() -> f.service().changeStatus(manager(), ITEM_ID, new MenuItemStatusCommand(false)),
                404, "Không tìm thấy món trong cơ sở được phân quyền.");
    }
}
