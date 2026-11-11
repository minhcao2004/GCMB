/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 11/11/2026
 * Description: Manual unit cases for UC-70 Create Equipment Incident Report.
 */
package com.gcmb.backend.manual.equipmentmaintenance;

import static com.gcmb.backend.manual.equipmentmaintenance.EquipmentMaintenanceManualRunner.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gcmb.backend.inventory.dto.EquipmentIncidentDtos.IncidentCommand;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

final class CreateEquipmentIncidentReportManualCases {
    private static final String UC = "UC-70 Create Equipment Incident Report";
    private static final String FILE = "UC-70 - Create Equipment Incident Report.docx";
    private static final String PRE = "Người dùng đã đăng nhập; cơ sở HN_HOA_BANG đang hoạt động; thiết bị ASSET-HN01-0001 đang hoạt động tại cơ sở; vai trò và phạm vi cụ thể theo từng TC.";
    private static final String REQUIRED = "Thiết bị, severity, priority, phương án, đơn vị sửa chữa/thay thế và ngày dự kiến là bắt buộc; chi phí dự kiến không được âm, phương án thay thế cần mô tả thiết bị thay thế";

    private CreateEquipmentIncidentReportManualCases() {}

    static List<ManualTest> cases() {
        return List.of(
            tc("EMM-001", "Receptionist HN_HOA_BANG gửi báo cáo REPAIR hợp lệ, không đính kèm file.",
                "Tạo báo cáo thành công trong đúng cơ sở; trả về incidentId; trạng thái khởi tạo do repository xác định theo vai trò.",
                "Normal", "BR-41, BR-111", "", "White-box", CreateEquipmentIncidentReportManualCases::receptionistCreate),
            tc("EMM-002", "Branch Manager HN_HOA_BANG gửi báo cáo REPAIR hợp lệ.",
                "Tạo báo cáo thành công và cập nhật tình trạng thiết bị theo kế hoạch REPAIR.",
                "Normal", "BR-41, BR-42", "", "White-box", CreateEquipmentIncidentReportManualCases::managerCreate),
            tc("EMM-003", "Head Office Manager chọn HN_HOA_BANG và gửi báo cáo hợp lệ.",
                "Theo đặc tả UC-70, Head Office Manager tạo báo cáo thành công trong cơ sở đã chọn.",
                "Normal", "BR-41", "", "Black-box", CreateEquipmentIncidentReportManualCases::headOfficeCreate),
            tc("EMM-004", "Receptionist xem danh sách thiết bị có thể báo sự cố tại cơ sở được phân công.",
                "Trả đúng ASSET-HN01-0001 thuộc HN_HOA_BANG.",
                "Normal", "BR-41", "", "Black-box", CreateEquipmentIncidentReportManualCases::receptionistAssets),
            tc("EMM-005", "Head Office Manager chọn HN_HOA_BANG để lấy danh sách thiết bị.",
                "Trả danh sách thiết bị của cơ sở được chọn.",
                "Normal", "BR-41", "", "Black-box", CreateEquipmentIncidentReportManualCases::headOfficeAssets),
            tc("EMM-006", "Receptionist HN_HOA_BANG gửi requestedBranchId của cơ sở khác.",
                "Hiển thị “Bạn không có quyền báo cáo đối với sự cố thiết bị.”; không tạo báo cáo ngoài phạm vi.",
                "Abnormal", "BR-41", "MSG24", "Black-box", CreateEquipmentIncidentReportManualCases::wrongBranch),
            tc("EMM-007", "command=null.",
                "Hiển thị “Vui lòng bổ sung thông tin báo cáo sự cố.”; không tạo dữ liệu.",
                "Abnormal", "BR-111", "MSG02", "Black-box", CreateEquipmentIncidentReportManualCases::nullCommand),
            tc("EMM-008", "assetId=null.",
                "Hiển thị “Vui lòng bổ sung thiết bị.”; không tạo dữ liệu.",
                "Abnormal", "BR-111", "MSG02", "Black-box", CreateEquipmentIncidentReportManualCases::missingAsset),
            tc("EMM-009", "description chỉ chứa khoảng trắng.",
                "Hiển thị “Vui lòng bổ sung mô tả sự cố.”; không tạo dữ liệu.",
                "Abnormal", "BR-111", "MSG02", "Black-box", CreateEquipmentIncidentReportManualCases::blankDescription),
            tc("EMM-010", "expectedCompletionOn=null.",
                "Hiển thị “Vui lòng bổ sung ngày dự kiến hoàn thành.”; không tạo dữ liệu.",
                "Abnormal", "BR-111", "MSG02", "Black-box", CreateEquipmentIncidentReportManualCases::missingExpectedDate),
            tc("EMM-011", "handlingPlan=null.",
                "Hiển thị “Vui lòng bổ sung phương án xử lý.”; không tạo dữ liệu.",
                "Abnormal", "BR-111", "MSG02", "Black-box", CreateEquipmentIncidentReportManualCases::missingPlan),
            tc("EMM-012", "serviceProvider chỉ chứa khoảng trắng.",
                "Hiển thị “Vui lòng bổ sung đơn vị sửa chữa/thay thế.”; không tạo dữ liệu.",
                "Abnormal", "BR-111", "MSG02", "Black-box", CreateEquipmentIncidentReportManualCases::blankProvider),
            tc("EMM-013", "estimatedCost=-1.",
                "Từ chối với HTTP 400 và thông báo “" + REQUIRED + "”; không tạo dữ liệu.",
                "Boundary", "BR-111", "", "White-box", CreateEquipmentIncidentReportManualCases::negativeCost),
            tc("EMM-014", "handlingPlan=REPLACE nhưng replacementDetails chỉ chứa khoảng trắng.",
                "Hiển thị “Vui lòng bổ sung mô tả thiết bị thay thế.”; không tạo dữ liệu.",
                "Abnormal", "BR-111", "MSG02", "Black-box", CreateEquipmentIncidentReportManualCases::missingReplacementDetails),
            tc("EMM-015", "Repository xác định asset không thuộc cơ sở hoặc đã ngừng sử dụng.",
                "Hiển thị “Thiết bị không thuộc cơ sở hoặc đã ngừng sử dụng”; không tạo báo cáo.",
                "Abnormal", "BR-41", "", "White-box", CreateEquipmentIncidentReportManualCases::inactiveAsset),
            tc("EMM-016", "Báo cáo hợp lệ kèm một ảnh chứng cứ đã qua kiểm tra định dạng/dung lượng.",
                "Lưu metadata, liên kết chứng cứ với đúng incident và trả incidentId.",
                "Normal", "BR-115, BR-116", "", "White-box", CreateEquipmentIncidentReportManualCases::evidenceSuccess),
            tc("EMM-017", "File đã lưu nhưng incident không còn tồn tại khi liên kết chứng cứ.",
                "Hiển thị “Không tìm thấy báo cáo sự cố” và xóa file vừa lưu để không để lại dữ liệu rác.",
                "Abnormal", "BR-115, BR-116", "", "White-box", CreateEquipmentIncidentReportManualCases::evidenceRollback)
        );
    }

    private static ManualTest tc(String id, String input, String expected, String type, String br, String msg,
            String method, CheckedAction action) {
        return test(id, UC, "EquipmentIncidentService.create/assets", PRE, input, expected, type,
                note(FILE, br, msg, "EquipmentIncidentService.create/assets/branch", method), action);
    }

    private static TestResult receptionistCreate() throws Exception {
        var f = fixture();
        var id = f.service().create(receptionist(), null, validIncident(), null);
        verify(f.repository()).createIncident(BRANCH_ID, RECEPTIONIST_ID, "RECEPTIONIST", validIncident());
        return check(INCIDENT_ID.equals(id), "incidentId=" + id);
    }

    private static TestResult managerCreate() throws Exception {
        var f = fixture();
        var id = f.service().create(branchManager(), null, validIncident(), null);
        verify(f.repository()).updateAssetForPlan(BRANCH_ID, INCIDENT_ID, "REPAIR");
        return check(INCIDENT_ID.equals(id), "incidentId=" + id + "; asset plan updated");
    }

    private static TestResult headOfficeCreate() throws Exception {
        var f = fixture();
        try {
            var id = f.service().create(headOfficeManager(), BRANCH_ID, validIncident(), null);
            return check(INCIDENT_ID.equals(id), "incidentId=" + id);
        } catch (ResponseStatusException failure) {
            return check(false, "HTTP " + failure.getStatusCode().value() + "; message=" + failure.getReason());
        }
    }

    private static TestResult receptionistAssets() throws Exception {
        var f = fixture();
        var result = f.service().assets(receptionist(), null);
        return check(result.size() == 1 && ASSET_ID.equals(result.get(0).id()), "assets=" + result);
    }

    private static TestResult headOfficeAssets() throws Exception {
        var f = fixture();
        var result = f.service().assets(headOfficeManager(), BRANCH_ID);
        return check(result.size() == 1 && ASSET_ID.equals(result.get(0).id()), "assets=" + result);
    }

    private static TestResult wrongBranch() throws Exception {
        var f = fixture();
        return expectStatus(() -> f.service().create(receptionist(), OTHER_BRANCH_ID, validIncident(), null), 403,
                "Bạn không có quyền báo cáo đối với sự cố thiết bị.");
    }

    private static TestResult nullCommand() throws Exception {
        var f = fixture();
        return expectStatus(() -> f.service().create(receptionist(), null, null, null), 400,
                "Vui lòng bổ sung thông tin báo cáo sự cố.");
    }

    private static TestResult missingAsset() throws Exception {
        var f = fixture();
        IncidentCommand c = new IncidentCommand(null, "Mất nguồn", true, "HIGH", "URGENT",
                LocalDate.of(2026, 11, 15), "REPAIR", "Nhà cung cấp A", null, BigDecimal.TEN, null);
        return expectStatus(() -> f.service().create(receptionist(), null, c, null), 400, "Vui lòng bổ sung thiết bị.");
    }

    private static TestResult blankDescription() throws Exception {
        var f = fixture();
        var c = incidentCommand("   ", LocalDate.of(2026, 11, 15), "REPAIR", "Nhà cung cấp A", null, BigDecimal.TEN);
        return expectStatus(() -> f.service().create(receptionist(), null, c, null), 400,
                "Vui lòng bổ sung mô tả sự cố.");
    }

    private static TestResult missingExpectedDate() throws Exception {
        var f = fixture();
        var c = incidentCommand("Mất nguồn", null, "REPAIR", "Nhà cung cấp A", null, BigDecimal.TEN);
        return expectStatus(() -> f.service().create(receptionist(), null, c, null), 400,
                "Vui lòng bổ sung ngày dự kiến hoàn thành.");
    }

    private static TestResult missingPlan() throws Exception {
        var f = fixture();
        var c = incidentCommand("Mất nguồn", LocalDate.of(2026, 11, 15), null, "Nhà cung cấp A", null, BigDecimal.TEN);
        return expectStatus(() -> f.service().create(receptionist(), null, c, null), 400,
                "Vui lòng bổ sung phương án xử lý.");
    }

    private static TestResult blankProvider() throws Exception {
        var f = fixture();
        var c = incidentCommand("Mất nguồn", LocalDate.of(2026, 11, 15), "REPAIR", "   ", null, BigDecimal.TEN);
        return expectStatus(() -> f.service().create(receptionist(), null, c, null), 400,
                "Vui lòng bổ sung đơn vị sửa chữa/thay thế.");
    }

    private static TestResult negativeCost() throws Exception {
        var f = fixture();
        var c = incidentCommand("Mất nguồn", LocalDate.of(2026, 11, 15), "REPAIR", "Nhà cung cấp A", null,
                BigDecimal.ONE.negate());
        return expectStatus(() -> f.service().create(receptionist(), null, c, null), 400, REQUIRED);
    }

    private static TestResult missingReplacementDetails() throws Exception {
        var f = fixture();
        var c = incidentCommand("Mất nguồn", LocalDate.of(2026, 11, 15), "REPLACE", "Nhà cung cấp A", "   ",
                BigDecimal.TEN);
        return expectStatus(() -> f.service().create(receptionist(), null, c, null), 400,
                "Vui lòng bổ sung mô tả thiết bị thay thế.");
    }

    private static TestResult inactiveAsset() throws Exception {
        var f = fixture();
        when(f.repository().createIncident(eq(BRANCH_ID), eq(RECEPTIONIST_ID), eq("RECEPTIONIST"), any())).thenReturn(null);
        return expectStatus(() -> f.service().create(receptionist(), null, validIncident(), null), 400,
                "Thiết bị không thuộc cơ sở hoặc đã ngừng sử dụng");
    }

    private static TestResult evidenceSuccess() throws Exception {
        var f = fixture();
        var file = new MockMultipartFile("evidence", "incident.png", "image/png", new byte[]{1});
        var id = f.service().create(receptionist(), null, validIncident(), file);
        verify(f.repository()).saveEvidence(any(), eq(RECEPTIONIST_ID));
        verify(f.repository()).setIncidentEvidence(BRANCH_ID, INCIDENT_ID, evidenceFile().id());
        return check(INCIDENT_ID.equals(id), "incidentId=" + id + "; evidence linked");
    }

    private static TestResult evidenceRollback() throws Exception {
        var f = fixture();
        when(f.repository().setIncidentEvidence(BRANCH_ID, INCIDENT_ID, evidenceFile().id())).thenReturn(false);
        var file = new MockMultipartFile("evidence", "incident.png", "image/png", new byte[]{1});
        TestResult result = expectStatus(() -> f.service().create(receptionist(), null, validIncident(), file), 404,
                "Không tìm thấy báo cáo sự cố");
        verify(f.fileStorage()).deleteQuietly(evidenceFile().objectKey());
        verify(f.repository(), never()).updateAssetForPlan(eq(BRANCH_ID), eq(INCIDENT_ID), any());
        return result;
    }
}
