/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 11/11/2026
 * Description: Defines UC-66 View Equipment Assets manual unit test cases.
 */
package com.gcmb.backend.manual.equipmentmanagement;

import static com.gcmb.backend.manual.equipmentmanagement.EquipmentManagementManualRunner.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

final class ViewEquipmentAssetsManualCases {
    private static final String UC = "UC-66 View Equipment Assets";
    private static final String FILE = "UC-66 - View Equipment Assets.docx";
    private static final String PRE = "Quản lý cấp cao qlcc hoặc quản lý cơ sở qlcs.hoabang đã đăng nhập; thiết bị ASSET-HN01-0001 tồn tại tại HN_HOA_BANG.";

    private ViewEquipmentAssetsManualCases() {}

    static List<ManualTest> cases() {
        return List.of(
            tc("EQM-016", "Quản lý cơ sở xem danh sách của cơ sở được phân công.",
                "Trả thiết bị cùng nhóm, phòng, tình trạng và lịch sử sự cố gần nhất; không thay đổi dữ liệu.",
                "Normal", "", "", "Black-box", ViewEquipmentAssetsManualCases::branchList),
            tc("EQM-017", "Quản lý cấp cao chọn branchId=HN_HOA_BANG.",
                "Trả đúng danh sách thiết bị của HN_HOA_BANG; không lẫn cơ sở khác.",
                "Normal", "", "", "Black-box", ViewEquipmentAssetsManualCases::headOfficeList),
            tc("EQM-018", "search=' may chieu '; roomId=P101; status=BROKEN; active=false.",
                "Chuẩn hóa search và chuyển nguyên vẹn các bộ lọc đến truy vấn danh sách.",
                "Normal", "", "", "White-box", ViewEquipmentAssetsManualCases::filters),
            tc("EQM-019", "search=khong-ton-tai; không có kết quả.",
                "Trả danh sách rỗng để tầng hiển thị xử lý trạng thái không có kết quả; không thay đổi dữ liệu.",
                "Abnormal", "", "", "Black-box", ViewEquipmentAssetsManualCases::empty),
            tc("EQM-020", "Quản lý cơ sở HN_HOA_BANG yêu cầu branchId của cơ sở khác.",
                "Hiển thị “Bạn không có quyền xem đối với thiết bị.”; không truy vấn ngoài phạm vi.",
                "Abnormal", "BR-202", "MSG24", "Black-box", ViewEquipmentAssetsManualCases::wrongBranch)
        );
    }

    private static ManualTest tc(String id, String input, String expected, String type, String br, String msg,
            String method, CheckedAction action) {
        return test(id, UC, "EquipmentAssetService.list", PRE, input, expected, type,
                note(FILE, br, msg, "EquipmentAssetService.list/scope", method), action);
    }

    private static TestResult branchList() {
        var f = fixture();
        var result = f.service().list(branchManager(), null, "", null, "", null);
        return check(result.size() == 1 && BRANCH_ID.equals(result.get(0).branchId()),
                "size=" + result.size() + "; branchId=" + result.get(0).branchId());
    }

    private static TestResult headOfficeList() {
        var f = fixture();
        var result = f.service().list(headOfficeManager(), BRANCH_ID, "", null, "", null);
        return check(result.size() == 1 && BRANCH_ID.equals(result.get(0).branchId()),
                "size=" + result.size() + "; branchId=" + result.get(0).branchId());
    }

    private static TestResult filters() {
        var f = fixture();
        f.service().list(branchManager(), BRANCH_ID, " may chieu ", ROOM_ID, "BROKEN", false);
        verify(f.repository()).list(BRANCH_ID, "may chieu", ROOM_ID, "BROKEN", false);
        return check(true, "repository.list received normalized search and all filters");
    }

    private static TestResult empty() {
        var f = fixture();
        when(f.repository().list(BRANCH_ID, "khong-ton-tai", null, "", null)).thenReturn(List.of());
        var result = f.service().list(branchManager(), BRANCH_ID, "khong-ton-tai", null, "", null);
        return check(result.isEmpty(), "size=" + result.size());
    }

    private static TestResult wrongBranch() {
        var f = fixture();
        return expectStatus(() -> f.service().list(branchManager(), OTHER_BRANCH_ID, "", null, "", null), 403,
                "Bạn không có quyền xem đối với thiết bị.");
    }
}
