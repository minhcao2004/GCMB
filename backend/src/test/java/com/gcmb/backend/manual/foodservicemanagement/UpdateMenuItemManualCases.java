/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 11/11/2026
 * Description: Defines UC-47 Update Menu Item manual unit test cases.
 */
package com.gcmb.backend.manual.foodservicemanagement;

import static com.gcmb.backend.manual.foodservicemanagement.CreateMenuItemManualCases.note;
import static com.gcmb.backend.manual.foodservicemanagement.FoodServiceManagementManualRunner.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gcmb.backend.booking.service.RefundEvidenceStorageService.StoredEvidence;
import com.gcmb.backend.inventory.dto.MenuItemDtos.ImageAsset;
import com.gcmb.backend.inventory.dto.MenuItemDtos.MenuItemCommand;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

final class UpdateMenuItemManualCases {
    private static final String UC = "UC-47 Update Menu Item";
    private static final String PRE = "Quản lý quanly.hoabang đăng nhập; HN_HOA_BANG hoạt động; món FB-001 tồn tại tại cơ sở.";

    private UpdateMenuItemManualCases() {}

    static List<FoodServiceManagementManualRunner.ManualTest> cases() {
        return List.of(
            tc("FSM-022", PRE + " Món đã xuất hiện trong order lịch sử.", "Đổi tên và giá món.", "Cập nhật món; order lịch sử vẫn giữ snapshot cũ.", "Normal", "BR-27, BR-38", "", UpdateMenuItemManualCases::validUpdate),
            tc("FSM-023", PRE + " Món PACKAGED đang liên kết kho Coca Cola.", "Cập nhật mô tả, giữ liên kết kho.", "Cập nhật thành công và giữ inventoryItemId.", "Normal", "BR-38", "", UpdateMenuItemManualCases::keepInventory),
            tc("FSM-024", "Quản lý quanly.hoabang đăng nhập; món không tồn tại hoặc thuộc cơ sở khác.", "Cập nhật ITEM_ID.", "Hiển thị “Không tìm thấy món trong cơ sở được phân quyền.”; không ghi dữ liệu.", "Abnormal", "BR-177", "", UpdateMenuItemManualCases::notFound),
            tc("FSM-025", PRE, "Tên món rỗng.", "Hiển thị “Vui lòng bổ sung tên món.”; không cập nhật.", "Abnormal", "BR-111", "MSG02", () -> invalid(command(" ", "20000"), "Vui lòng bổ sung tên món.")),
            tc("FSM-026", PRE, "Giá bán=-1đ.", "Hiển thị “Giá bán phải lớn hơn hoặc bằng 0.”; không cập nhật.", "Boundary", "BR-137", "MSG45", () -> invalid(command("Coca Cola", "-1"), "Giá bán phải lớn hơn hoặc bằng 0.")),
            tc("FSM-027", PRE + " Món có ảnh old.png.", "Tải ảnh new.png hợp lệ và transaction commit.", "Lưu ảnh mới; chỉ xóa ảnh cũ sau commit.", "Normal", "BR-115, BR-116", "", () -> replaceImage(TransactionSynchronization.STATUS_COMMITTED)),
            tc("FSM-028", PRE + " Món có ảnh old.png.", "Tải ảnh new.png rồi transaction rollback.", "Xóa ảnh mới; giữ ảnh cũ.", "Abnormal", "BR-115, BR-116", "", () -> replaceImage(TransactionSynchronization.STATUS_ROLLED_BACK))
        );
    }

    private static FoodServiceManagementManualRunner.ManualTest tc(String id, String pre, String input,
            String expected, String type, String br, String msg, FoodServiceManagementManualRunner.CheckedAction action) {
        return test(id, UC, "MenuItemService.update", pre, input, expected, type,
                note("UC-47 - Update_Menu_Item.docx", br, msg, "MenuItemService.update", "White-box"), action);
    }

    private static MenuItemCommand command(String name, String price) {
        return new MenuItemCommand(name, "Nước uống", "PACKAGED", INVENTORY_ID,
                new BigDecimal(price), true, "Cập nhật", false);
    }

    private static FoodServiceManagementManualRunner.TestResult validUpdate() {
        var f = menuFixture();
        var result = f.service().update(manager(), ITEM_ID, command("Coca Cola mới", "25000"), null);
        verify(f.repository()).update(eq(BRANCH_ID), eq(ITEM_ID), any(), any(), eq(false));
        return check(result != null, "updated=true; order snapshot is not written by update");
    }

    private static FoodServiceManagementManualRunner.TestResult keepInventory() {
        var f = menuFixture();
        f.service().update(manager(), ITEM_ID, command("Coca Cola", "20000"), null);
        verify(f.repository()).activeInventory(BRANCH_ID, INVENTORY_ID);
        return check(true, "inventoryItemId=" + INVENTORY_ID);
    }

    private static FoodServiceManagementManualRunner.TestResult notFound() {
        var f = menuFixture();
        when(f.repository().get(BRANCH_ID, ITEM_ID)).thenReturn(null);
        return expectResponse(() -> f.service().update(manager(), ITEM_ID, command("Coca Cola", "20000"), null),
                404, "Không tìm thấy món trong cơ sở được phân quyền.");
    }

    private static FoodServiceManagementManualRunner.TestResult invalid(MenuItemCommand command, String expected) {
        var f = menuFixture();
        var result = expectResponse(() -> f.service().update(manager(), ITEM_ID, command, null), 400, expected);
        verify(f.repository(), never()).update(any(), any(), any(), any(), any(Boolean.class));
        return result;
    }

    private static FoodServiceManagementManualRunner.TestResult replaceImage(int completionStatus) {
        var f = menuFixture();
        when(f.storage().store(any())).thenReturn(new StoredEvidence(UUID.randomUUID(), "s3/new.png", "new.png", "image/png", 8, "hash"));
        when(f.repository().image(BRANCH_ID, ITEM_ID, false)).thenReturn(new ImageAsset("s3/old.png", "old.png", "image/png"));
        TransactionSynchronizationManager.initSynchronization();
        try {
            f.service().update(manager(), ITEM_ID, command("Coca Cola", "20000"),
                    new MockMultipartFile("image", "new.png", "image/png", new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}));
            TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(completionStatus));
            if (completionStatus == TransactionSynchronization.STATUS_COMMITTED) {
                verify(f.storage()).deleteQuietly("s3/old.png");
                verify(f.storage(), never()).deleteQuietly("s3/new.png");
                return check(true, "commit: deleted old image only");
            }
            verify(f.storage()).deleteQuietly("s3/new.png");
            verify(f.storage(), never()).deleteQuietly("s3/old.png");
            return check(true, "rollback: deleted new image only");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
