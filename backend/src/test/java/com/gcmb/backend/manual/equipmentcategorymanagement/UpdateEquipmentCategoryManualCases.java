/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 11/11/2026
 * Description: Defines UC-62 Update Equipment Category manual unit test cases.
 */
package com.gcmb.backend.manual.equipmentcategorymanagement;

import static com.gcmb.backend.manual.equipmentcategorymanagement.EquipmentCategoryManagementManualRunner.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gcmb.backend.inventory.dto.EquipmentCategoryDtos.Command;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;

final class UpdateEquipmentCategoryManualCases {
    private static final String UC = "UC-62 Update Equipment Category";
    private static final String FILE = "UC-62 - Update Equipment Category.docx";
    private static final String PRE = "Quản lý cấp cao qlcc đã đăng nhập; nhóm EQ-AUDIO tồn tại và đang được 2 thiết bị tham chiếu.";

    private UpdateEquipmentCategoryManualCases() {}

    static List<ManualTest> cases() {
        return List.of(
            tc("ECM-013", "id=EQ-AUDIO; name=' Thiết bị âm thanh sân khấu '.",
                "Cập nhật thông tin đã chuẩn hóa; nhóm vẫn hoạt động và 2 liên kết thiết bị được giữ nguyên.",
                "Normal", "BR-111", "", "White-box", UpdateEquipmentCategoryManualCases::success),
            tc("ECM-014", "code chỉ chứa khoảng trắng.", "Hiển thị “Vui lòng bổ sung mã nhóm thiết bị.”; không cập nhật.",
                "Abnormal", "BR-111", "MSG02", "Black-box",
                () -> invalid(new Command("   ", "Thiết bị âm thanh", "Mô tả"), "Vui lòng bổ sung mã nhóm thiết bị.")),
            tc("ECM-015", "name chỉ chứa khoảng trắng.", "Hiển thị “Vui lòng bổ sung tên nhóm thiết bị.”; không cập nhật.",
                "Abnormal", "BR-111", "MSG02", "Black-box",
                () -> invalid(new Command("EQ-AUDIO", "   ", "Mô tả"), "Vui lòng bổ sung tên nhóm thiết bị.")),
            tc("ECM-016", "đổi code thành EQ-LIGHT đã tồn tại toàn chuỗi.",
                "Hiển thị “Mã nhóm thiết bị “EQ-LIGHT” đã tồn tại trong danh mục toàn chuỗi. Vui lòng nhập mã khác.”; dữ liệu cũ không đổi.",
                "Abnormal", "BR-111", "MSG38", "Black-box", UpdateEquipmentCategoryManualCases::duplicate),
            tc("ECM-017", "Quản lý cơ sở qlcs.hoabang gửi yêu cầu cập nhật EQ-AUDIO.",
                "Hiển thị “Bạn không có quyền cập nhật đối với nhóm thiết bị.”; dữ liệu không đổi.",
                "Abnormal", "", "MSG24", "Black-box", UpdateEquipmentCategoryManualCases::branchManagerDenied)
        );
    }

    private static ManualTest tc(String id, String input, String expected, String type, String br, String msg,
            String method, CheckedAction action) {
        return test(id, UC, "EquipmentCategoryService.update", PRE, input, expected, type,
                note(FILE, br, msg, "EquipmentCategoryService.update/validate", method), action);
    }

    private static TestResult success() {
        var f = fixture();
        var command = new Command(" EQ-AUDIO ", " Thiết bị âm thanh sân khấu ", " Danh mục thiết bị dùng chung ");
        var result = f.service().update(headOfficeManager(), null, CATEGORY_ID, command);
        verify(f.repository()).update(isNull(), eq(CATEGORY_ID),
                eq(new Command("EQ-AUDIO", "Thiết bị âm thanh sân khấu", "Danh mục thiết bị dùng chung")));
        return check(result.assetCount() == 2 && result.active() && "Thiết bị âm thanh sân khấu".equals(result.name()),
                "name=" + result.name() + "; active=" + result.active() + "; assetCount=" + result.assetCount());
    }

    private static TestResult invalid(Command command, String message) {
        var f = fixture();
        TestResult result = expectStatus(() -> f.service().update(headOfficeManager(), null, CATEGORY_ID, command), 400, message);
        verify(f.repository(), never()).update(isNull(), eq(CATEGORY_ID), any());
        return result;
    }

    private static TestResult duplicate() {
        var f = fixture();
        when(f.repository().update(isNull(), eq(CATEGORY_ID), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        return expectStatus(() -> f.service().update(headOfficeManager(), null, CATEGORY_ID,
                new Command("EQ-LIGHT", "Thiết bị âm thanh", "Mô tả")), 409,
                "Mã nhóm thiết bị “EQ-LIGHT” đã tồn tại trong danh mục toàn chuỗi. Vui lòng nhập mã khác.");
    }

    private static TestResult branchManagerDenied() {
        var f = fixture();
        TestResult result = expectStatus(() -> f.service().update(branchManager(), BRANCH_ID, CATEGORY_ID,
                new Command("EQ-AUDIO", "Thiết bị âm thanh", "Mô tả")), 403,
                "Bạn không có quyền cập nhật đối với nhóm thiết bị.");
        verify(f.repository(), never()).update(isNull(), eq(CATEGORY_ID), any());
        return result;
    }
}
