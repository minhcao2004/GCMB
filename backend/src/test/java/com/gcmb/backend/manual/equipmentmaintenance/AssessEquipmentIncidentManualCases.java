/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 11/11/2026
 * Description: Manual unit cases for UC-73 Assess Equipment Incident.
 */
package com.gcmb.backend.manual.equipmentmaintenance;

import static com.gcmb.backend.manual.equipmentmaintenance.EquipmentMaintenanceManualRunner.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gcmb.backend.inventory.dto.EquipmentIncidentDtos.AssessmentCommand;
import com.gcmb.backend.inventory.dto.EquipmentIncidentDtos.ReviewCommand;
import java.util.List;

final class AssessEquipmentIncidentManualCases {
    private static final String UC = "UC-73 Assess Equipment Incident";
    private static final String SOURCE = "GenZ_Use_Case_List_Updated.xlsm - Use Case Changes (UC-73; chưa có file đặc tả UC-73 trong 01_Phan_II_Dac_Ta_UC)";
    private static final String PRE = "Branch Manager đã đăng nhập; incident ASSET-HN01-0001 đang mở tại HN_HOA_BANG và ở trạng thái chờ đánh giá; thiết bị thay thế hợp lệ khi phương án là REPLACE.";

    private AssessEquipmentIncidentManualCases() {}

    static List<ManualTest> cases() {
        return List.of(
            tc("EMM-041", "Branch Manager chọn plan=REPAIR, nhập ghi chú đánh giá.",
                "Ghi nhận đánh giá; cập nhật thiết bị theo REPAIR; xử lý chuyển phòng sang bảo trì nếu sự cố làm phòng không sử dụng được.",
                "Normal", "BR-42, BR-43, BR-44", "", "White-box", AssessEquipmentIncidentManualCases::assessRepair),
            tc("EMM-042", "Branch Manager chọn plan=REPLACE và replacementAssetId đã đăng ký trong cơ sở.",
                "Ghi nhận đánh giá REPLACE; liên kết thiết bị thay thế; cập nhật kế hoạch tài sản và xử lý trạng thái phòng.",
                "Normal", "BR-42, BR-43, BR-44", "", "White-box", AssessEquipmentIncidentManualCases::assessReplace),
            tc("EMM-043", "Branch Manager chọn plan=NO_ACTION.",
                "Theo BR-44, hiển thị “Vui lòng chọn phương án sửa chữa hoặc thay thế.”; không đánh giá sự cố bằng NO_ACTION.",
                "Abnormal", "BR-44", "MSG02", "Black-box", AssessEquipmentIncidentManualCases::noAction),
            tc("EMM-044", "assessmentCommand=null.",
                "Hiển thị “Vui lòng bổ sung thông tin đánh giá sự cố.”; không cập nhật dữ liệu.",
                "Abnormal", "BR-111, BR-42", "MSG02", "Black-box", AssessEquipmentIncidentManualCases::nullCommand),
            tc("EMM-045", "plan=INVALID.",
                "Hiển thị “Vui lòng bổ sung phương án đánh giá hợp lệ.”; không cập nhật dữ liệu.",
                "Abnormal", "BR-111, BR-44", "MSG02", "Black-box", AssessEquipmentIncidentManualCases::invalidPlan),
            tc("EMM-046", "note chỉ chứa khoảng trắng.",
                "Hiển thị “Vui lòng bổ sung ghi chú đánh giá.”; không cập nhật dữ liệu.",
                "Abnormal", "BR-111, BR-42", "MSG02", "Black-box", AssessEquipmentIncidentManualCases::blankNote),
            tc("EMM-047", "plan=REPLACE nhưng replacementAssetId=null.",
                "Hiển thị “Vui lòng bổ sung thiết bị thay thế đã đăng ký.”; không cập nhật dữ liệu.",
                "Abnormal", "BR-111, BR-44", "MSG02", "Black-box", AssessEquipmentIncidentManualCases::missingReplacement),
            tc("EMM-048", "Receptionist yêu cầu đánh giá sự cố.",
                "Hiển thị “Bạn không có quyền đánh giá đối với sự cố thiết bị.”; dữ liệu giữ nguyên.",
                "Abnormal", "BR-42", "MSG24", "Black-box", AssessEquipmentIncidentManualCases::receptionistDenied),
            tc("EMM-049", "Head Office Manager yêu cầu đánh giá sự cố tại HN_HOA_BANG.",
                "Theo BR-42, hiển thị “Bạn không có quyền đánh giá đối với sự cố thiết bị.”; dữ liệu giữ nguyên.",
                "Abnormal", "BR-42", "MSG24", "Black-box", AssessEquipmentIncidentManualCases::headOfficeDenied),
            tc("EMM-050", "Repository trả 0 vì sự cố không còn chờ đánh giá hoặc thiết bị thay thế không hợp lệ.",
                "Hiển thị “Sự cố không còn ở trạng thái chờ đánh giá hoặc thiết bị thay thế không hợp lệ”; không cập nhật thiết bị/phòng.",
                "Abnormal", "BR-42, BR-44", "", "White-box", AssessEquipmentIncidentManualCases::staleIncident),
            tc("EMM-051", "Branch Manager duyệt báo cáo của Receptionist với approved=true và ghi chú hợp lệ.",
                "Duyệt thành công; chuyển thiết bị theo handlingPlan đang lưu của incident.",
                "Normal", "BR-42, BR-44", "", "White-box", AssessEquipmentIncidentManualCases::approveReview),
            tc("EMM-052", "Branch Manager từ chối báo cáo với approved=false và ghi chú hợp lệ.",
                "Từ chối thành công; không cập nhật thiết bị theo handlingPlan.",
                "Normal", "BR-42", "", "White-box", AssessEquipmentIncidentManualCases::rejectReview)
        );
    }

    private static ManualTest tc(String id, String input, String expected, String type, String br, String msg,
            String method, CheckedAction action) {
        return test(id, UC, "EquipmentIncidentService.assess/review", PRE, input, expected, type,
                note(SOURCE, br, msg, "EquipmentIncidentService.assess/review/manager", method), action);
    }

    private static TestResult assessRepair() throws Exception {
        var f = fixture();
        var command = new AssessmentCommand("REPAIR", "Gửi nhà cung cấp sửa chữa", null);
        f.service().assess(branchManager(), null, INCIDENT_ID, command);
        verify(f.repository()).assess(BRANCH_ID, BRANCH_MANAGER_ID, INCIDENT_ID, command);
        verify(f.repository()).updateAssetForPlan(BRANCH_ID, INCIDENT_ID, "REPAIR");
        verify(f.repository()).markRoomMaintenance(BRANCH_ID, BRANCH_MANAGER_ID, INCIDENT_ID,
                "Gửi nhà cung cấp sửa chữa");
        return check(true, "assessment=REPAIR; asset plan updated; room maintenance evaluated");
    }

    private static TestResult assessReplace() throws Exception {
        var f = fixture();
        var command = new AssessmentCommand("REPLACE", "Thay bằng thiết bị dự phòng", REPLACEMENT_ASSET_ID);
        f.service().assess(branchManager(), null, INCIDENT_ID, command);
        verify(f.repository()).assess(BRANCH_ID, BRANCH_MANAGER_ID, INCIDENT_ID, command);
        verify(f.repository()).updateAssetForPlan(BRANCH_ID, INCIDENT_ID, "REPLACE");
        verify(f.repository()).markRoomMaintenance(BRANCH_ID, BRANCH_MANAGER_ID, INCIDENT_ID,
                "Thay bằng thiết bị dự phòng");
        return check(true, "assessment=REPLACE; replacement asset retained; room maintenance evaluated");
    }

    private static TestResult noAction() throws Exception {
        var f = fixture();
        return expectStatus(() -> f.service().assess(branchManager(), null, INCIDENT_ID,
                new AssessmentCommand("NO_ACTION", "Không xử lý", null)), 400,
                "Vui lòng chọn phương án sửa chữa hoặc thay thế.");
    }

    private static TestResult nullCommand() throws Exception {
        var f = fixture();
        return expectStatus(() -> f.service().assess(branchManager(), null, INCIDENT_ID, null), 400,
                "Vui lòng bổ sung thông tin đánh giá sự cố.");
    }

    private static TestResult invalidPlan() throws Exception {
        var f = fixture();
        return expectStatus(() -> f.service().assess(branchManager(), null, INCIDENT_ID,
                new AssessmentCommand("INVALID", "Đánh giá", null)), 400,
                "Vui lòng bổ sung phương án đánh giá hợp lệ.");
    }

    private static TestResult blankNote() throws Exception {
        var f = fixture();
        return expectStatus(() -> f.service().assess(branchManager(), null, INCIDENT_ID,
                new AssessmentCommand("REPAIR", "   ", null)), 400,
                "Vui lòng bổ sung ghi chú đánh giá.");
    }

    private static TestResult missingReplacement() throws Exception {
        var f = fixture();
        return expectStatus(() -> f.service().assess(branchManager(), null, INCIDENT_ID,
                new AssessmentCommand("REPLACE", "Cần thay", null)), 400,
                "Vui lòng bổ sung thiết bị thay thế đã đăng ký.");
    }

    private static TestResult receptionistDenied() throws Exception {
        var f = fixture();
        return expectStatus(() -> f.service().assess(receptionist(), null, INCIDENT_ID,
                new AssessmentCommand("REPAIR", "Gửi sửa", null)), 403,
                "Bạn không có quyền đánh giá đối với sự cố thiết bị.");
    }

    private static TestResult headOfficeDenied() throws Exception {
        var f = fixture();
        return expectStatus(() -> f.service().assess(headOfficeManager(), BRANCH_ID, INCIDENT_ID,
                new AssessmentCommand("REPAIR", "Gửi sửa", null)), 403,
                "Bạn không có quyền đánh giá đối với sự cố thiết bị.");
    }

    private static TestResult staleIncident() throws Exception {
        var f = fixture();
        when(f.repository().assess(eq(BRANCH_ID), eq(BRANCH_MANAGER_ID), eq(INCIDENT_ID), any())).thenReturn(0);
        return expectStatus(() -> f.service().assess(branchManager(), null, INCIDENT_ID,
                new AssessmentCommand("REPAIR", "Gửi sửa", null)), 409,
                "Sự cố không còn ở trạng thái chờ đánh giá hoặc thiết bị thay thế không hợp lệ");
    }

    private static TestResult approveReview() throws Exception {
        var f = fixture();
        var command = new ReviewCommand(true, "Duyệt xử lý");
        f.service().review(branchManager(), null, INCIDENT_ID, command);
        verify(f.repository()).review(BRANCH_ID, BRANCH_MANAGER_ID, INCIDENT_ID, command);
        verify(f.repository()).updateAssetForPlan(BRANCH_ID, INCIDENT_ID, "REPAIR");
        return check(true, "review approved; asset plan=REPAIR");
    }

    private static TestResult rejectReview() throws Exception {
        var f = fixture();
        var command = new ReviewCommand(false, "Thông tin chưa đủ");
        f.service().review(branchManager(), null, INCIDENT_ID, command);
        verify(f.repository()).review(BRANCH_ID, BRANCH_MANAGER_ID, INCIDENT_ID, command);
        verify(f.repository(), never()).updateAssetForPlan(eq(BRANCH_ID), eq(INCIDENT_ID), any());
        return check(true, "review rejected; asset plan unchanged");
    }
}
