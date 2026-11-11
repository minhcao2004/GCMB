/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 11/11/2026
 * Description: Defines UC-61 View Equipment Categories manual unit test cases.
 */
package com.gcmb.backend.manual.equipmentcategorymanagement;

import static com.gcmb.backend.manual.equipmentcategorymanagement.EquipmentCategoryManagementManualRunner.*;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

final class ViewEquipmentCategoriesManualCases {
    private static final String UC = "UC-61 View Equipment Categories";
    private static final String FILE = "UC-61 - View Equipment Categories.docx";
    private static final String PRE = "Quản lý cấp cao qlcc hoặc quản lý cơ sở qlcs.hoabang đã đăng nhập; mở danh sách nhóm thiết bị.";

    private ViewEquipmentCategoriesManualCases() {}

    static List<ManualTest> cases() {
        return List.of(
            tc("ECM-007", "Quản lý cấp cao xem toàn bộ; search rỗng; active=null.",
                "Trả danh sách nhóm thiết bị dùng chung và số thiết bị đang liên kết; không thay đổi dữ liệu.",
                "Normal", "", "", "Black-box", ViewEquipmentCategoriesManualCases::headOfficeList),
            tc("ECM-008", "Quản lý cơ sở HN_HOA_BANG xem toàn bộ; branchId đúng cơ sở được phân công.",
                "Trả danh sách nhóm thiết bị dùng chung; không thay đổi dữ liệu.",
                "Normal", "", "", "Black-box", ViewEquipmentCategoriesManualCases::branchManagerList),
            tc("ECM-009", "search=' audio '; active=false.",
                "Chuẩn hóa search thành 'audio', chuyển đúng bộ lọc ngừng hoạt động và trả kết quả phù hợp.",
                "Normal", "", "", "White-box", ViewEquipmentCategoriesManualCases::searchAndFilter),
            tc("ECM-010", "search=khong-ton-tai; repository không có kết quả.",
                "Trả danh sách rỗng để tầng hiển thị xử lý trạng thái không có kết quả; không thay đổi dữ liệu.",
                "Abnormal", "", "", "Black-box", ViewEquipmentCategoriesManualCases::emptyList),
            tc("ECM-011", "Quản lý cơ sở HN_HOA_BANG yêu cầu branchId thuộc cơ sở khác.",
                "Hiển thị “Bạn không có quyền xem đối với nhóm thiết bị.”; không truy vấn dữ liệu ngoài phạm vi.",
                "Abnormal", "", "MSG24", "Black-box", ViewEquipmentCategoriesManualCases::otherBranchDenied),
            tc("ECM-012", "Lễ tân yêu cầu xem danh sách nhóm thiết bị.",
                "Hiển thị “Bạn không có quyền xem đối với nhóm thiết bị.”; không truy vấn danh sách.",
                "Abnormal", "", "MSG24", "Black-box", ViewEquipmentCategoriesManualCases::roleDenied)
        );
    }

    private static ManualTest tc(String id, String input, String expected, String type, String br, String msg,
            String method, CheckedAction action) {
        return test(id, UC, "EquipmentCategoryService.list", PRE, input, expected, type,
                note(FILE, br, msg, "EquipmentCategoryService.list/requireScope", method), action);
    }

    private static TestResult headOfficeList() {
        var f = fixture();
        var result = f.service().list(headOfficeManager(), null, "", null);
        return check(result.size() == 1 && result.get(0).assetCount() == 2,
                "size=" + result.size() + "; assetCount=" + result.get(0).assetCount());
    }

    private static TestResult branchManagerList() {
        var f = fixture();
        var result = f.service().list(branchManager(), BRANCH_ID, "", null);
        return check(result.size() == 1 && result.get(0).branchId() == null,
                "size=" + result.size() + "; category.branchId=" + result.get(0).branchId());
    }

    private static TestResult searchAndFilter() {
        var f = fixture();
        var inactive = view(CATEGORY_ID, "EQ-AUDIO", "Thiết bị âm thanh", false, 2);
        when(f.repository().list(isNull(), org.mockito.ArgumentMatchers.eq("audio"), org.mockito.ArgumentMatchers.eq(false)))
                .thenReturn(List.of(inactive));
        var result = f.service().list(headOfficeManager(), null, " audio ", false);
        verify(f.repository()).list(null, "audio", false);
        return check(result.size() == 1 && !result.get(0).active(), "size=" + result.size() + "; active=" + result.get(0).active());
    }

    private static TestResult emptyList() {
        var f = fixture();
        when(f.repository().list(null, "khong-ton-tai", null)).thenReturn(List.of());
        var result = f.service().list(headOfficeManager(), null, "khong-ton-tai", null);
        return check(result.isEmpty(), "size=" + result.size());
    }

    private static TestResult otherBranchDenied() {
        var f = fixture();
        return expectStatus(() -> f.service().list(branchManager(), OTHER_BRANCH_ID, "", null), 403,
                "Bạn không có quyền xem đối với nhóm thiết bị.");
    }

    private static TestResult roleDenied() {
        var f = fixture();
        return expectStatus(() -> f.service().list(receptionist(), BRANCH_ID, "", null), 403,
                "Bạn không có quyền xem đối với nhóm thiết bị.");
    }
}
