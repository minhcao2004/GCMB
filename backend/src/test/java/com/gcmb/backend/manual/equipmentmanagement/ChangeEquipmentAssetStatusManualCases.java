/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 11/11/2026
 * Description: Defines UC-68 Change Equipment Asset Status manual unit test cases.
 */
package com.gcmb.backend.manual.equipmentmanagement;

import static com.gcmb.backend.manual.equipmentmanagement.EquipmentManagementManualRunner.*;
import static org.mockito.Mockito.when;

import java.util.List;

final class ChangeEquipmentAssetStatusManualCases {
    private static final String UC = "UC-68 Change Equipment Asset Status";
    private static final String FILE = "UC-68 - Change Equipment Asset Status.docx";
    private static final String PRE = "Quản lý cấp cao qlcc hoặc quản lý cơ sở qlcs.hoabang đã đăng nhập; ASSET-HN01-0001 tồn tại tại HN_HOA_BANG.";

    private ChangeEquipmentAssetStatusManualCases() {}

    static List<ManualTest> cases() {
        return List.of(
            tc("EQM-031", "Quản lý cơ sở đặt active=false.",
                "Thiết bị ngừng sử dụng; tình trạng vận hành IN_USE và lịch sử sự cố vẫn giữ nguyên.",
                "Normal", "", "", "White-box", () -> change(branchManager(), BRANCH_ID, false)),
            tc("EQM-032", "Quản lý cấp cao chọn HN_HOA_BANG và đặt active=true.",
                "Thiết bị hoạt động; tình trạng vận hành và lịch sử không thay đổi.",
                "Normal", "", "", "White-box", () -> change(headOfficeManager(), BRANCH_ID, true)),
            tc("EQM-033", "active=null.",
                "Hiển thị “Vui lòng bổ sung trạng thái sử dụng thiết bị.”; trạng thái cũ không đổi.",
                "Abnormal", "BR-111", "MSG02", "Black-box", ChangeEquipmentAssetStatusManualCases::missingStatus),
            tc("EQM-034", "Thiết bị đang IN_TRANSIT; yêu cầu active=false.",
                "Hiển thị “Không đổi trạng thái sử dụng khi thiết bị đang điều chuyển”; không thay đổi dữ liệu.",
                "Abnormal", "BR-110", "", "White-box", ChangeEquipmentAssetStatusManualCases::inTransit),
            tc("EQM-035", "Quản lý cơ sở HN_HOA_BANG gửi branchId của cơ sở khác.",
                "Hiển thị “Bạn không có quyền đổi trạng thái đối với thiết bị.”; trạng thái cũ không đổi.",
                "Abnormal", "BR-202", "MSG24", "Black-box", ChangeEquipmentAssetStatusManualCases::wrongBranch)
        );
    }

    private static ManualTest tc(String id, String input, String expected, String type, String br, String msg,
            String method, CheckedAction action) {
        return test(id, UC, "EquipmentAssetService.setActive", PRE, input, expected, type,
                note(FILE, br, msg, "EquipmentAssetService.setActive/scope", method), action);
    }

    private static TestResult change(com.gcmb.backend.auth.dto.Identity actor, java.util.UUID branchId, boolean active) {
        var f = fixture();
        var result = f.service().setActive(actor, branchId, ASSET_ID, active);
        return check(result.active() == active && "IN_USE".equals(result.status()) && INCIDENT_ID.equals(result.latestIncidentId()),
                "active=" + result.active() + "; status=" + result.status() + "; latestIncidentId=" + result.latestIncidentId());
    }

    private static TestResult missingStatus() {
        var f = fixture();
        return expectStatus(() -> f.service().setActive(branchManager(), BRANCH_ID, ASSET_ID, null), 400,
                "Vui lòng bổ sung trạng thái sử dụng thiết bị.");
    }

    private static TestResult inTransit() {
        var f = fixture();
        when(f.repository().get(BRANCH_ID, ASSET_ID))
                .thenReturn(view(ASSET_ID, "ASSET-HN01-0001", "Máy chiếu", "IN_TRANSIT", true, false, null));
        return expectStatus(() -> f.service().setActive(branchManager(), BRANCH_ID, ASSET_ID, false), 409,
                "Không đổi trạng thái sử dụng khi thiết bị đang điều chuyển");
    }

    private static TestResult wrongBranch() {
        var f = fixture();
        return expectStatus(() -> f.service().setActive(branchManager(), OTHER_BRANCH_ID, ASSET_ID, false), 403,
                "Bạn không có quyền đổi trạng thái đối với thiết bị.");
    }
}
