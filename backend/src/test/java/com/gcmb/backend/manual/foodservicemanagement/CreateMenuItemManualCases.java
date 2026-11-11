/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 11/11/2026
 * Description: Defines UC-45 Create Menu Item manual unit test cases.
 */
package com.gcmb.backend.manual.foodservicemanagement;

import static com.gcmb.backend.manual.foodservicemanagement.FoodServiceManagementManualRunner.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.booking.service.RefundEvidenceStorageService;
import com.gcmb.backend.inventory.dto.MenuItemDtos.MenuItemCommand;
import com.gcmb.backend.inventory.service.MenuItemService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.mock.web.MockMultipartFile;

final class CreateMenuItemManualCases {
    private static final String UC = "UC-45 Create Menu Item";
    private static final String PRE = "Quản lý cơ sở quanly.hoabang đăng nhập; HN_HOA_BANG đang hoạt động; mở quản lý thực đơn.";

    private CreateMenuItemManualCases() {}

    static List<FoodServiceManagementManualRunner.ManualTest> cases() {
        return List.of(
            tc("FSM-001", PRE, "Tên=Mì bò; loại=PREPARED; giá=65.000đ.", "Tạo món thành công, không liên kết kho.", "Normal", "BR-39", "", () -> create(valid("Mì bò", "PREPARED", null, "65000"), manager(), true)),
            tc("FSM-002", PRE + " Có mặt hàng kho Coca Cola đang hoạt động.", "Tên=Coca Cola; loại=PACKAGED; giá=20.000đ; chọn mặt hàng kho.", "Tạo món thành công và liên kết đúng mặt hàng kho.", "Normal", "BR-38, BR-111", "", () -> create(valid("Coca Cola", "PACKAGED", INVENTORY_ID, "20000"), manager(), true)),
            tc("FSM-003", PRE, "Giá bán=0đ.", "Tạo món thành công với giá 0đ.", "Boundary", "BR-137", "", () -> create(valid("Nước lọc tặng", "PREPARED", null, "0"), manager(), true)),
            tc("FSM-004", PRE, "Tên món chỉ gồm khoảng trắng.", "Hiển thị “Vui lòng bổ sung tên món.”; không tạo dữ liệu.", "Abnormal", "BR-111", "MSG02", () -> invalid(valid("   ", "PREPARED", null, "65000"), "Vui lòng bổ sung tên món.")),
            tc("FSM-005", PRE, "Giá bán=null.", "Hiển thị “Vui lòng bổ sung giá bán.”; không tạo dữ liệu.", "Abnormal", "BR-111", "MSG02", () -> invalid(new MenuItemCommand("Mì bò", "Đồ ăn", "PREPARED", null, null, true, null, false), "Vui lòng bổ sung giá bán.")),
            tc("FSM-006", PRE, "Giá bán=-1đ.", "Hiển thị “Giá bán phải lớn hơn hoặc bằng 0.”; không tạo dữ liệu.", "Boundary", "BR-137", "MSG45", () -> invalid(valid("Mì bò", "PREPARED", null, "-1"), "Giá bán phải lớn hơn hoặc bằng 0.")),
            tc("FSM-007", PRE, "Giá bán=20.000,5đ.", "Hiển thị “Giá bán phải là số tiền nguyên đồng, không có phần thập phân.”; không tạo dữ liệu.", "Boundary", "BR-137", "MSG45", () -> invalid(valid("Mì bò", "PREPARED", null, "20000.5"), "Giá bán phải là số tiền nguyên đồng, không có phần thập phân.")),
            tc("FSM-008", PRE, "Loại món=UNKNOWN.", "Hiển thị “Vui lòng chọn loại món hợp lệ.”; không tạo dữ liệu.", "Abnormal", "BR-38, BR-39", "MSG02", () -> invalid(valid("Mì bò", "UNKNOWN", null, "65000"), "Vui lòng chọn loại món hợp lệ.")),
            tc("FSM-009", PRE, "Loại=PACKAGED nhưng không chọn mặt hàng kho.", "Hiển thị “Vui lòng bổ sung mặt hàng kho.”; không tạo dữ liệu.", "Abnormal", "BR-38, BR-111", "MSG02", () -> invalid(valid("Coca Cola", "PACKAGED", null, "20000"), "Vui lòng bổ sung mặt hàng kho.")),
            tc("FSM-010", PRE + " Mặt hàng kho đã ngừng dùng hoặc thuộc cơ sở khác.", "Tạo PACKAGED với mặt hàng kho không hợp lệ.", "Từ chối và không tạo món.", "Abnormal", "BR-38", "", CreateMenuItemManualCases::inactiveInventory),
            tc("FSM-011", "Kế toán ketoan đã đăng nhập.", "Kế toán tạo món.", "Hiển thị “Bạn không có quyền tạo món đối với thực đơn.”; không đọc/ghi thực đơn.", "Abnormal", "Không có BR riêng", "MSG24", CreateMenuItemManualCases::wrongRole),
            tc("FSM-012", "Quản lý quanly.hoabang đăng nhập; HN_HOA_BANG đang tạm ngừng.", "Tạo món hợp lệ.", "Hiển thị “Cơ sở HN_HOA_BANG đang tạm ngưng hoạt động nên chưa thể thực hiện thao tác này.”; không tạo món.", "Abnormal", "BR-177", "MSG43", CreateMenuItemManualCases::suspendedBranch),
            tc("FSM-013", PRE, "Ảnh đính kèm .txt.", "Hiển thị “Chỉ hỗ trợ tệp JPEG, PNG, WebP, HEIC, HEIF hoặc PDF. Vui lòng chọn tệp khác.”; không tạo món.", "Abnormal", "BR-115", "MSG78", () -> image(new MockMultipartFile("image", "note.txt", "text/plain", "abc".getBytes()), "Chỉ hỗ trợ tệp JPEG, PNG, WebP, HEIC, HEIF hoặc PDF. Vui lòng chọn tệp khác.")),
            tc("FSM-014", PRE, "Ảnh PNG dung lượng 0 byte.", "Hiển thị “Tệp phải có dung lượng lớn hơn 0 và không vượt quá 20 MB.”; không tạo món.", "Boundary", "BR-116", "MSG79", () -> image(new MockMultipartFile("image", "empty.png", "image/png", new byte[0]), "Tệp phải có dung lượng lớn hơn 0 và không vượt quá 20 MB.")),
            tc("FSM-015", PRE, "Ảnh PNG dung lượng 20.000.001 byte.", "Hiển thị “Tệp phải có dung lượng lớn hơn 0 và không vượt quá 20 MB.”; không tạo món.", "Boundary", "BR-116", "MSG79", () -> image(new MockMultipartFile("image", "large.png", "image/png", new byte[20_000_001]), "Tệp phải có dung lượng lớn hơn 0 và không vượt quá 20 MB.")),
            tc("FSM-016", PRE, "Khai báo PNG nhưng nội dung không phải PNG.", "Hiển thị “Không thể đọc tệp hoặc định dạng tệp không khớp với nội dung. Vui lòng xuất lại tệp đúng định dạng và tải lên lại.”; không tạo món.", "Abnormal", "BR-115", "MSG80", () -> image(new MockMultipartFile("image", "fake.png", "image/png", "not-png".getBytes()), "Không thể đọc tệp hoặc định dạng tệp không khớp với nội dung. Vui lòng xuất lại tệp đúng định dạng và tải lên lại."))
        );
    }

    private static FoodServiceManagementManualRunner.ManualTest tc(String id, String pre, String input,
            String expected, String type, String br, String msg, FoodServiceManagementManualRunner.CheckedAction action) {
        return test(id, UC, "MenuItemService.create", pre, input, expected, type,
                note("UC-45 - Create_Menu_Item.docx", br, msg, "MenuItemService.create", "White-box"), action);
    }

    private static MenuItemCommand valid(String name, String kind, java.util.UUID inventoryId, String price) {
        return new MenuItemCommand(name, "Đồ ăn", kind, inventoryId, new BigDecimal(price), true, null, false);
    }

    private static FoodServiceManagementManualRunner.TestResult create(MenuItemCommand command, Identity actor, boolean expected) {
        var f = menuFixture();
        var result = f.service().create(actor, command, null);
        verify(f.repository()).create(any(), any(), any());
        return check(expected && result != null, "created=" + (result != null));
    }

    private static FoodServiceManagementManualRunner.TestResult invalid(MenuItemCommand command, String message) {
        var f = menuFixture();
        var result = expectResponse(() -> f.service().create(manager(), command, null), 400, message);
        verify(f.repository(), never()).create(any(), any(), any());
        return result;
    }

    private static FoodServiceManagementManualRunner.TestResult inactiveInventory() {
        var f = menuFixture();
        when(f.repository().activeInventory(BRANCH_ID, INVENTORY_ID)).thenReturn(false);
        return expectResponse(() -> f.service().create(manager(), valid("Coca Cola", "PACKAGED", INVENTORY_ID, "20000"), null),
                400, "Mặt hàng kho không tồn tại, đã ngừng dùng hoặc ngoài cơ sở được phân quyền.");
    }

    private static FoodServiceManagementManualRunner.TestResult wrongRole() {
        var f = menuFixture();
        Identity accountant = new Identity(ACTOR_ID, "ketoan", "ACCOUNTANT", "/accounting", null, null);
        return expectResponse(() -> f.service().create(accountant, valid("Mì bò", "PREPARED", null, "65000"), null),
                403, "Bạn không có quyền tạo món đối với thực đơn.");
    }

    private static FoodServiceManagementManualRunner.TestResult suspendedBranch() {
        var f = menuFixture();
        when(f.repository().activeBranch(BRANCH_ID)).thenReturn(false);
        return expectResponse(() -> f.service().create(manager(), valid("Mì bò", "PREPARED", null, "65000"), null),
                403, "Cơ sở HN_HOA_BANG đang tạm ngưng hoạt động nên chưa thể thực hiện thao tác này.");
    }

    private static FoodServiceManagementManualRunner.TestResult image(MockMultipartFile file, String message) {
        var f = menuFixture();
        RefundEvidenceStorageService actualStorage = new RefundEvidenceStorageService("build/manual-images", "", "us-east-1", "", false);
        MenuItemService service = new MenuItemService(f.repository(), actualStorage);
        return expectResponse(() -> service.create(manager(), valid("Mì bò", "PREPARED", null, "65000"), file), 400, message);
    }

    static String note(String doc, String br, String msg, String source, String method) {
        return "Nguồn: " + doc + "; " + br + (msg.isBlank() ? "" : "; " + msg)
                + "; Flyway V14, V34-V37; Production: " + source + ". Phương pháp kiểm thử: " + method + ".";
    }
}
