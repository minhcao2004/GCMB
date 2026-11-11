/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 11/11/2026
 * Description: Defines UC-69 Delete Equipment Asset manual unit test cases.
 */
package com.gcmb.backend.manual.equipmentmanagement;

import static com.gcmb.backend.manual.equipmentmanagement.EquipmentManagementManualRunner.*;
import static org.mockito.Mockito.when;

import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;

final class DeleteEquipmentAssetManualCases {
    private static final String UC = "UC-69 Delete Equipment Asset";
    private static final String FILE = "UC-69 - Delete Equipment Asset.docx";
    private static final String PRE = "Quản lý cấp cao qlcc hoặc quản lý cơ sở qlcs.hoabang đã đăng nhập; ASSET-HN01-0001 tồn tại tại HN_HOA_BANG; người dùng đã xác nhận xóa.";
    private static final String PRESERVE_MESSAGE = "Không thể thực hiện cách xóa này vì sẽ làm mất hoặc thay đổi dữ liệu liên quan. Lịch sử nghiệp vụ và các bản ghi liên quan phải được giữ nguyên.";

    private DeleteEquipmentAssetManualCases() {}

    static List<ManualTest> cases() {
        return List.of(
            tc("EQM-036", "Quản lý cơ sở xóa thiết bị chưa có lịch sử nghiệp vụ.",
                "Xóa thiết bị thành công trong đúng cơ sở; không ảnh hưởng dữ liệu khác.",
                "Normal", "BR-118", "", "White-box", DeleteEquipmentAssetManualCases::branchDelete),
            tc("EQM-037", "Quản lý cấp cao chọn HN_HOA_BANG và xóa thiết bị chưa có lịch sử.",
                "Xóa đúng thiết bị thuộc HN_HOA_BANG; không ảnh hưởng cơ sở khác.",
                "Normal", "BR-118", "", "Black-box", DeleteEquipmentAssetManualCases::headOfficeDelete),
            tc("EQM-038", "Thiết bị đã có sự cố, sửa chữa, điều chuyển hoặc lịch sử thay thế.",
                "Hiển thị “" + PRESERVE_MESSAGE + "”; thiết bị và lịch sử giữ nguyên.",
                "Abnormal", "BR-118", "MSG104", "Black-box", DeleteEquipmentAssetManualCases::linkedHistory),
            tc("EQM-039", "Ràng buộc dữ liệu phát hiện liên kết nghiệp vụ trong lúc xóa.",
                "Hiển thị “" + PRESERVE_MESSAGE + "”; không mất dữ liệu liên quan.",
                "Abnormal", "BR-118", "MSG104", "White-box", DeleteEquipmentAssetManualCases::constraintFailure),
            tc("EQM-040", "Quản lý cơ sở HN_HOA_BANG gửi branchId của cơ sở khác.",
                "Hiển thị “Bạn không có quyền xóa đối với thiết bị.”; thiết bị giữ nguyên.",
                "Abnormal", "BR-118", "MSG24", "Black-box", DeleteEquipmentAssetManualCases::wrongBranch)
        );
    }

    private static ManualTest tc(String id, String input, String expected, String type, String br, String msg,
            String method, CheckedAction action) {
        return test(id, UC, "EquipmentAssetService.delete", PRE, input, expected, type,
                note(FILE, br, msg, "EquipmentAssetService.delete/scope", method), action);
    }

    private static TestResult branchDelete() {
        var f = fixture();
        f.service().delete(branchManager(), BRANCH_ID, ASSET_ID);
        return check(true, "repository.deleteUnused returned true; delete completed");
    }

    private static TestResult headOfficeDelete() {
        var f = fixture();
        f.service().delete(headOfficeManager(), BRANCH_ID, ASSET_ID);
        return check(true, "repository.deleteUnused returned true; delete completed");
    }

    private static TestResult linkedHistory() {
        var f = fixture();
        when(f.repository().deleteUnused(BRANCH_ID, ASSET_ID)).thenReturn(false);
        return expectStatus(() -> f.service().delete(branchManager(), BRANCH_ID, ASSET_ID), 409, PRESERVE_MESSAGE);
    }

    private static TestResult constraintFailure() {
        var f = fixture();
        when(f.repository().deleteUnused(BRANCH_ID, ASSET_ID)).thenThrow(new DataIntegrityViolationException("foreign key"));
        return expectStatus(() -> f.service().delete(branchManager(), BRANCH_ID, ASSET_ID), 409, PRESERVE_MESSAGE);
    }

    private static TestResult wrongBranch() {
        var f = fixture();
        return expectStatus(() -> f.service().delete(branchManager(), OTHER_BRANCH_ID, ASSET_ID), 403,
                "Bạn không có quyền xóa đối với thiết bị.");
    }
}
