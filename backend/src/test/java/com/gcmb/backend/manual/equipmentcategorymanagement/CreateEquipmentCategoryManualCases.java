/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 11/11/2026
 * Description: Defines UC-60 Create Equipment Category manual unit test cases.
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

final class CreateEquipmentCategoryManualCases {
    private static final String UC = "UC-60 Create Equipment Category";
    private static final String FILE = "UC-60 - Create Equipment Category.docx";
    private static final String PRE = "Quản lý cấp cao qlcc đã đăng nhập; mở quản lý nhóm thiết bị; danh mục dùng chung toàn chuỗi sẵn sàng.";

    private CreateEquipmentCategoryManualCases() {}

    static List<ManualTest> cases() {
        return List.of(
            tc("ECM-001", "code=' EQ-LIGHT '; name=' Thiết bị ánh sáng '; description có khoảng trắng đầu/cuối.",
                "Tạo nhóm thành công; dữ liệu được chuẩn hóa; nhóm mới ở trạng thái hoạt động và chưa có thiết bị liên kết.",
                "Normal", "BR-111", "", "White-box", CreateEquipmentCategoryManualCases::success),
            tc("ECM-002", "command=null.", "Hiển thị “Vui lòng bổ sung thông tin nhóm thiết bị.”; không tạo dữ liệu.",
                "Abnormal", "BR-111", "MSG02", "Black-box",
                () -> invalid(null, "Vui lòng bổ sung thông tin nhóm thiết bị.")),
            tc("ECM-003", "code chỉ chứa khoảng trắng.", "Hiển thị “Vui lòng bổ sung mã nhóm thiết bị.”; không tạo dữ liệu.",
                "Abnormal", "BR-111", "MSG02", "Black-box",
                () -> invalid(command("   ", "Thiết bị ánh sáng"), "Vui lòng bổ sung mã nhóm thiết bị.")),
            tc("ECM-004", "name chỉ chứa khoảng trắng.", "Hiển thị “Vui lòng bổ sung tên nhóm thiết bị.”; không tạo dữ liệu.",
                "Abnormal", "BR-111", "MSG02", "Black-box",
                () -> invalid(command("EQ-LIGHT", "   "), "Vui lòng bổ sung tên nhóm thiết bị.")),
            tc("ECM-005", "code=EQ-AUDIO đã tồn tại trong danh mục toàn chuỗi.",
                "Hiển thị “Mã nhóm thiết bị “EQ-AUDIO” đã tồn tại trong danh mục toàn chuỗi. Vui lòng nhập mã khác.”; không tạo trùng.",
                "Abnormal", "BR-111", "MSG38", "Black-box", CreateEquipmentCategoryManualCases::duplicate),
            tc("ECM-006", "Quản lý cơ sở qlcs.hoabang gửi yêu cầu tạo nhóm.",
                "Hiển thị “Bạn không có quyền tạo đối với nhóm thiết bị.”; không tạo dữ liệu.",
                "Abnormal", "", "MSG24", "Black-box", CreateEquipmentCategoryManualCases::branchManagerDenied)
        );
    }

    private static ManualTest tc(String id, String input, String expected, String type, String br, String msg,
            String method, CheckedAction action) {
        return test(id, UC, "EquipmentCategoryService.create", PRE, input, expected, type,
                note(FILE, br, msg, "EquipmentCategoryService.create/validate", method), action);
    }

    private static Command command(String code, String name) {
        return new Command(code, name, "Danh mục thiết bị dùng chung");
    }

    private static TestResult success() {
        var f = fixture();
        var result = f.service().create(headOfficeManager(), null,
                new Command(" EQ-LIGHT ", " Thiết bị ánh sáng ", " Danh mục thiết bị dùng chung "));
        verify(f.repository()).create(isNull(),
                eq(new Command("EQ-LIGHT", "Thiết bị ánh sáng", "Danh mục thiết bị dùng chung")));
        return check(NEW_CATEGORY_ID.equals(result.id()) && result.active() && result.assetCount() == 0,
                "id=" + result.id() + "; active=" + result.active() + "; assetCount=" + result.assetCount());
    }

    private static TestResult invalid(Command command, String message) {
        var f = fixture();
        TestResult result = expectStatus(() -> f.service().create(headOfficeManager(), null, command), 400, message);
        verify(f.repository(), never()).create(isNull(), any());
        return result;
    }

    private static TestResult duplicate() {
        var f = fixture();
        when(f.repository().create(isNull(), any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        return expectStatus(() -> f.service().create(headOfficeManager(), null, command("EQ-AUDIO", "Thiết bị âm thanh")),
                409, "Mã nhóm thiết bị “EQ-AUDIO” đã tồn tại trong danh mục toàn chuỗi. Vui lòng nhập mã khác.");
    }

    private static TestResult branchManagerDenied() {
        var f = fixture();
        TestResult result = expectStatus(() -> f.service().create(branchManager(), BRANCH_ID,
                command("EQ-LIGHT", "Thiết bị ánh sáng")), 403,
                "Bạn không có quyền tạo đối với nhóm thiết bị.");
        verify(f.repository(), never()).create(isNull(), any());
        return result;
    }
}
