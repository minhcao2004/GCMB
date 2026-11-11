/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 11/11/2026
 * Description: Defines UC-63 Change Equipment Category Status manual unit test cases.
 */
package com.gcmb.backend.manual.equipmentcategorymanagement;

import static com.gcmb.backend.manual.equipmentcategorymanagement.EquipmentCategoryManagementManualRunner.*;

import java.util.List;

final class ChangeEquipmentCategoryStatusManualCases {
    private static final String UC = "UC-63 Change Equipment Category Status";
    private static final String FILE = "UC-63 - Change Equipment Category Status.docx";
    private static final String PRE = "Quản lý cấp cao qlcc hoặc quản lý cơ sở qlcs.hoabang đã đăng nhập; nhóm EQ-AUDIO tồn tại và có 2 thiết bị tham chiếu.";

    private ChangeEquipmentCategoryStatusManualCases() {}

    static List<ManualTest> cases() {
        return List.of(
            tc("ECM-018", "Quản lý cấp cao đặt active=false.",
                "Nhóm chuyển ngừng hoạt động; 2 liên kết thiết bị và lịch sử được giữ nguyên.",
                "Normal", "BR-118, BR-191", "", "White-box", () -> change(headOfficeManager(), null, false)),
            tc("ECM-019", "Quản lý cấp cao đặt active=true.",
                "Nhóm chuyển hoạt động; 2 liên kết thiết bị và lịch sử được giữ nguyên.",
                "Normal", "BR-118, BR-191", "", "White-box", () -> change(headOfficeManager(), null, true)),
            tc("ECM-020", "Quản lý cơ sở qlcs.hoabang đặt active=false cho nhóm hiện có.",
                "Nhóm chuyển ngừng hoạt động; các thiết bị tham chiếu không bị xóa hoặc thay đổi.",
                "Normal", "BR-191", "", "Black-box", () -> change(branchManager(), BRANCH_ID, false)),
            tc("ECM-021", "active=null.",
                "Hiển thị “Vui lòng bổ sung trạng thái nhóm thiết bị.”; trạng thái cũ không đổi.",
                "Abnormal", "BR-111", "MSG02", "Black-box", ChangeEquipmentCategoryStatusManualCases::missingStatus),
            tc("ECM-022", "Lễ tân yêu cầu đổi trạng thái.",
                "Hiển thị “Bạn không có quyền đổi trạng thái đối với nhóm thiết bị.”; trạng thái cũ không đổi.",
                "Abnormal", "", "MSG24", "Black-box", ChangeEquipmentCategoryStatusManualCases::roleDenied)
        );
    }

    private static ManualTest tc(String id, String input, String expected, String type, String br, String msg,
            String method, CheckedAction action) {
        return test(id, UC, "EquipmentCategoryService.setActive", PRE, input, expected, type,
                note(FILE, br, msg, "EquipmentCategoryService.setActive/requireScope", method), action);
    }

    private static TestResult change(com.gcmb.backend.auth.dto.Identity actor, java.util.UUID branchId, boolean active) {
        var f = fixture();
        var result = f.service().setActive(actor, branchId, CATEGORY_ID, active);
        return check(result.active() == active && result.assetCount() == 2,
                "active=" + result.active() + "; assetCount=" + result.assetCount());
    }

    private static TestResult missingStatus() {
        var f = fixture();
        return expectStatus(() -> f.service().setActive(headOfficeManager(), null, CATEGORY_ID, null), 400,
                "Vui lòng bổ sung trạng thái nhóm thiết bị.");
    }

    private static TestResult roleDenied() {
        var f = fixture();
        return expectStatus(() -> f.service().setActive(receptionist(), BRANCH_ID, CATEGORY_ID, false), 403,
                "Bạn không có quyền đổi trạng thái đối với nhóm thiết bị.");
    }
}
