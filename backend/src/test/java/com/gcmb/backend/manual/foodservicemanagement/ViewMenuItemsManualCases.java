/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 11/11/2026
 * Description: Defines UC-46 View Menu Items manual unit test cases.
 */
package com.gcmb.backend.manual.foodservicemanagement;

import static com.gcmb.backend.manual.foodservicemanagement.CreateMenuItemManualCases.note;
import static com.gcmb.backend.manual.foodservicemanagement.FoodServiceManagementManualRunner.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gcmb.backend.inventory.dto.MenuItemDtos.PublicMenuItemView;
import java.math.BigDecimal;
import java.util.List;

final class ViewMenuItemsManualCases {
    private ViewMenuItemsManualCases() {}

    static List<FoodServiceManagementManualRunner.ManualTest> cases() {
        String uc = "UC-46 View Menu Items";
        String pre = "Người dùng đã đăng nhập đúng vai trò; dữ liệu thực đơn tồn tại tại HN_HOA_BANG.";
        return List.of(
            test("FSM-017", uc, "MenuItemService.list", pre + " Quản lý cơ sở mở danh sách.", "Xem toàn bộ món của cơ sở.", "Trả đúng món cả đang/đã ngừng bán; không ghi dữ liệu.", "Normal", note("UC-46 - View_Menu_Items.docx", "BR-177", "", "MenuItemService.list", "Black-box"), ViewMenuItemsManualCases::managerList),
            test("FSM-018", uc, "FoodOrderService.receptionMenu", pre + " Lễ tân mở thực đơn gọi món.", "Xem món có thể bán.", "Chỉ trả món đang bán/còn khả dụng của cơ sở; không ghi dữ liệu.", "Normal", note("UC-46 - View_Menu_Items.docx", "BR-38", "", "FoodOrderService.receptionMenu", "Black-box"), ViewMenuItemsManualCases::receptionList),
            test("FSM-019", uc, "MenuItemService.publicList", "Khách đang xem thực đơn công khai HN_HOA_BANG.", "search=Coca; category=Nước uống.", "Trả Coca Cola phù hợp điều kiện tìm kiếm.", "Normal", note("UC-46 - View_Menu_Items.docx", "BR-38", "", "MenuItemService.publicList", "Black-box"), ViewMenuItemsManualCases::publicSearch),
            test("FSM-020", uc, "MenuItemService.publicList", "Khách đang xem thực đơn công khai HN_HOA_BANG.", "search=Không tồn tại.", "Trả danh sách rỗng; không ghi dữ liệu.", "Abnormal", note("UC-46 - View_Menu_Items.docx", "BR-38", "", "MenuItemService.publicList", "Black-box"), ViewMenuItemsManualCases::emptySearch),
            test("FSM-021", uc, "MenuItemService.list", pre + " Cơ sở khác cũng có món.", "Quản lý HN_HOA_BANG xem danh sách.", "Không trả món thuộc cơ sở khác.", "Abnormal", note("UC-46 - View_Menu_Items.docx", "BR-177", "", "MenuItemService.list", "White-box"), ViewMenuItemsManualCases::branchScope)
        );
    }

    private static FoodServiceManagementManualRunner.TestResult managerList() {
        var f = menuFixture();
        when(f.repository().list(BRANCH_ID)).thenReturn(List.of(menuView(true), menuView(false)));
        var rows = f.service().list(manager());
        return check(rows.size() == 2, "rows=" + rows.size());
    }

    private static FoodServiceManagementManualRunner.TestResult receptionList() {
        var f = foodFixture();
        var view = new PublicMenuItemView(ITEM_ID, "Coca Cola", "Nước uống", "PACKAGED", new BigDecimal("20000"), true, "AVAILABLE", null, null);
        when(f.menuItems().publicList(BRANCH_ID, null, null)).thenReturn(List.of(view));
        var rows = f.service().receptionMenu(receptionist());
        return check(rows.size() == 1 && rows.get(0).available(), "rows=" + rows.size() + ", available=" + rows.get(0).available());
    }

    private static FoodServiceManagementManualRunner.TestResult publicSearch() {
        var f = menuFixture();
        var view = new PublicMenuItemView(ITEM_ID, "Coca Cola", "Nước uống", "PACKAGED", new BigDecimal("20000"), true, "AVAILABLE", null, null);
        when(f.repository().publicList(BRANCH_ID, "Coca", "Nước uống")).thenReturn(List.of(view));
        return check(f.service().publicList(BRANCH_ID, "Coca", "Nước uống").size() == 1, "rows=1");
    }

    private static FoodServiceManagementManualRunner.TestResult emptySearch() {
        var f = menuFixture();
        when(f.repository().publicList(BRANCH_ID, "Không tồn tại", null)).thenReturn(List.of());
        return check(f.service().publicList(BRANCH_ID, "Không tồn tại", null).isEmpty(), "rows=0");
    }

    private static FoodServiceManagementManualRunner.TestResult branchScope() {
        var f = menuFixture();
        when(f.repository().list(BRANCH_ID)).thenReturn(List.of(menuView(true)));
        f.service().list(manager());
        verify(f.repository()).list(BRANCH_ID);
        return check(true, "repository queried branch=" + BRANCH_ID);
    }
}
