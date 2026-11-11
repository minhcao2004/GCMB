/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 11/11/2026
 * Description: Defines UC-64 Delete Equipment Category manual unit test cases.
 */
package com.gcmb.backend.manual.equipmentcategorymanagement;

import static com.gcmb.backend.manual.equipmentcategorymanagement.EquipmentCategoryManagementManualRunner.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;

final class DeleteEquipmentCategoryManualCases {
    private static final String UC = "UC-64 Delete Equipment Category";
    private static final String FILE = "UC-64 - Delete Equipment Category.docx";
    private static final String PRE = "Quản lý cấp cao qlcc hoặc quản lý cơ sở qlcs.hoabang đã đăng nhập; nhóm thiết bị cần xóa tồn tại; người dùng đã xác nhận xóa.";
    private static final String PRESERVE_MESSAGE = "Không thể thực hiện cách xóa này vì sẽ làm mất hoặc thay đổi dữ liệu liên quan. Lịch sử nghiệp vụ và các bản ghi liên quan phải được giữ nguyên.";

    private DeleteEquipmentCategoryManualCases() {}

    static List<ManualTest> cases() {
        return List.of(
            tc("ECM-023", "Quản lý cấp cao xóa nhóm chưa từng được thiết bị tham chiếu.",
                "Xóa nhóm thành công; không ảnh hưởng bản ghi nghiệp vụ hoặc thiết bị.",
                "Normal", "BR-118, BR-191", "", "White-box", DeleteEquipmentCategoryManualCases::headOfficeDelete),
            tc("ECM-024", "Quản lý cơ sở qlcs.hoabang xóa nhóm chưa từng được thiết bị tham chiếu.",
                "Xóa nhóm thành công; không ảnh hưởng bản ghi nghiệp vụ hoặc thiết bị.",
                "Normal", "BR-118, BR-191", "", "Black-box", DeleteEquipmentCategoryManualCases::branchManagerDelete),
            tc("ECM-025", "Nhóm đang được thiết bị tham chiếu; repository từ chối xóa.",
                "Hiển thị “" + PRESERVE_MESSAGE + "”; nhóm và thiết bị liên quan giữ nguyên.",
                "Abnormal", "BR-118, BR-191", "MSG104", "Black-box", DeleteEquipmentCategoryManualCases::linkedCategory),
            tc("ECM-026", "Ràng buộc dữ liệu phát hiện lịch sử/thiết bị tham chiếu trong lúc xóa.",
                "Hiển thị “" + PRESERVE_MESSAGE + "”; không mất hoặc thay đổi dữ liệu liên quan.",
                "Abnormal", "BR-118, BR-191", "MSG104", "White-box", DeleteEquipmentCategoryManualCases::constraintFailure),
            tc("ECM-027", "Lễ tân yêu cầu xóa nhóm thiết bị.",
                "Hiển thị “Bạn không có quyền xóa đối với nhóm thiết bị.”; nhóm giữ nguyên.",
                "Abnormal", "", "MSG24", "Black-box", DeleteEquipmentCategoryManualCases::roleDenied)
        );
    }

    private static ManualTest tc(String id, String input, String expected, String type, String br, String msg,
            String method, CheckedAction action) {
        return test(id, UC, "EquipmentCategoryService.delete", PRE, input, expected, type,
                note(FILE, br, msg, "EquipmentCategoryService.delete/requireScope", method), action);
    }

    private static TestResult headOfficeDelete() {
        var f = fixture();
        f.service().delete(headOfficeManager(), null, CATEGORY_ID);
        return check(true, "repository.deleteUnused returned true; delete completed");
    }

    private static TestResult branchManagerDelete() {
        var f = fixture();
        f.service().delete(branchManager(), BRANCH_ID, CATEGORY_ID);
        return check(true, "repository.deleteUnused returned true; delete completed");
    }

    private static TestResult linkedCategory() {
        var f = fixture();
        when(f.repository().deleteUnused(isNull(), eq(CATEGORY_ID))).thenReturn(false);
        return expectStatus(() -> f.service().delete(headOfficeManager(), null, CATEGORY_ID), 409, PRESERVE_MESSAGE);
    }

    private static TestResult constraintFailure() {
        var f = fixture();
        when(f.repository().deleteUnused(isNull(), eq(CATEGORY_ID)))
                .thenThrow(new DataIntegrityViolationException("foreign key"));
        return expectStatus(() -> f.service().delete(headOfficeManager(), null, CATEGORY_ID), 409, PRESERVE_MESSAGE);
    }

    private static TestResult roleDenied() {
        var f = fixture();
        return expectStatus(() -> f.service().delete(receptionist(), BRANCH_ID, CATEGORY_ID), 403,
                "Bạn không có quyền xóa đối với nhóm thiết bị.");
    }
}
