/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 11/11/2026
 * Description: Manual unit cases for UC-71 Record Repair Outcome.
 */
package com.gcmb.backend.manual.equipmentmaintenance;

import static com.gcmb.backend.manual.equipmentmaintenance.EquipmentMaintenanceManualRunner.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gcmb.backend.inventory.dto.EquipmentIncidentDtos.RepairCommand;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

final class RecordRepairOutcomeManualCases {
    private static final String UC = "UC-71 Record Repair Outcome";
    private static final String FILE = "UC-71 - Record Repair Outcome.docx";
    private static final String PRE = "Người quản lý đã đăng nhập; sự cố mở ASSET-HN01-0001 tồn tại trong phạm vi; sự cố đã có kế hoạch REPAIR và trạng thái cụ thể theo từng TC.";
    private static final String REQUIRED = "Nhập đủ kết quả sửa chữa, hóa đơn, chứng từ chuyển khoản, chi phí và ngày hoàn tất";
    private static final String MSG83 = "Thiết bị chưa được xác nhận sửa chữa đạt yêu cầu. Chưa thể đưa thiết bị trở lại sử dụng bình thường hoặc đóng sự cố.";

    private RecordRepairOutcomeManualCases() {}

    static List<ManualTest> cases() {
        return List.of(
            tc("EMM-018", "Branch Manager ghi kết quả đạt yêu cầu, actualCost=1.500.000, ngày hoàn tất, condition=IN_USE, đủ hai chứng từ.",
                "Lưu kết quả và hai chứng từ; cập nhật thiết bị IN_USE; ghi tiến trình VERIFIED; không tự động chuyển phòng sang trạng thái sẵn sàng.",
                "Normal", "BR-45, BR-46, BR-165, BR-205", "", "White-box", RecordRepairOutcomeManualCases::validRepair),
            tc("EMM-019", "Head Office Manager chọn HN_HOA_BANG và ghi kết quả hợp lệ.",
                "Theo đặc tả UC-71, Head Office Manager ghi nhận kết quả thành công trong phạm vi được chọn.",
                "Normal", "BR-45, BR-46, BR-205", "", "Black-box", RecordRepairOutcomeManualCases::headOfficeRepair),
            tc("EMM-020", "repairCommand=null.",
                "Hiển thị “Vui lòng bổ sung thông tin kết quả sửa chữa.”; không cập nhật sự cố.",
                "Abnormal", "BR-111, BR-205", "MSG02", "Black-box", RecordRepairOutcomeManualCases::nullCommand),
            tc("EMM-021", "outcome chỉ chứa khoảng trắng.",
                "Hiển thị “Vui lòng bổ sung kết quả sửa chữa.”; không cập nhật sự cố.",
                "Abnormal", "BR-111, BR-205", "MSG02", "Black-box", RecordRepairOutcomeManualCases::blankOutcome),
            tc("EMM-022", "actualCost=null.",
                "Hiển thị “Vui lòng bổ sung chi phí sửa chữa thực tế.”; không cập nhật sự cố.",
                "Abnormal", "BR-46, BR-205", "MSG02", "Black-box", RecordRepairOutcomeManualCases::missingCost),
            tc("EMM-023", "actualCost=-1.",
                "Từ chối với HTTP 400 và thông báo “" + REQUIRED + "”; không cập nhật sự cố.",
                "Boundary", "BR-46, BR-205", "", "White-box", RecordRepairOutcomeManualCases::negativeCost),
            tc("EMM-024", "completedOn=null.",
                "Hiển thị “Vui lòng bổ sung ngày hoàn tất sửa chữa.”; không cập nhật sự cố.",
                "Abnormal", "BR-111, BR-205", "MSG02", "Black-box", RecordRepairOutcomeManualCases::missingCompletionDate),
            tc("EMM-025", "assetCondition=RETIRED, không thuộc tập tình trạng sau sửa chữa hợp lệ.",
                "Hiển thị “Vui lòng bổ sung tình trạng thiết bị sau sửa chữa hợp lệ.”; không cập nhật thiết bị.",
                "Abnormal", "BR-205", "MSG02", "Black-box", RecordRepairOutcomeManualCases::invalidCondition),
            tc("EMM-026", "invoice=null, transferProof hợp lệ.",
                "Hiển thị “Vui lòng bổ sung hóa đơn sửa chữa.”; không lưu kết quả.",
                "Abnormal", "BR-46", "MSG02", "Black-box", RecordRepairOutcomeManualCases::missingInvoice),
            tc("EMM-027", "invoice hợp lệ, transferProof=null.",
                "Hiển thị “Vui lòng bổ sung chứng từ chuyển khoản.”; không lưu kết quả.",
                "Abnormal", "BR-46", "MSG02", "Black-box", RecordRepairOutcomeManualCases::missingTransferProof),
            tc("EMM-028", "Sự cố chưa được đánh giá REPAIR hoặc không thuộc cơ sở nên repository.repair trả 0.",
                "Hiển thị “" + MSG83 + "”; không đưa thiết bị trở lại sử dụng.",
                "Abnormal", "BR-45", "MSG83", "Black-box", RecordRepairOutcomeManualCases::notVerified),
            tc("EMM-029", "Kết quả đã lưu nhưng không thể liên kết invoice/transferProof với lần sửa mới nhất.",
                "Hiển thị “Không tìm thấy kết quả sửa chữa”; xóa cả hai file vừa tải lên.",
                "Abnormal", "BR-46", "", "White-box", RecordRepairOutcomeManualCases::evidenceLinkFailure),
            tc("EMM-030", "Invoice tải lên thành công nhưng transferProof có định dạng không được hỗ trợ.",
                "Hiển thị “Chỉ hỗ trợ tệp JPEG, PNG, WebP, HEIC, HEIF hoặc PDF. Vui lòng chọn tệp khác.” và xóa invoice đã tải.",
                "Abnormal", "BR-115", "MSG78", "White-box", RecordRepairOutcomeManualCases::secondUploadFailure),
            tc("EMM-031", "Branch Manager yêu cầu đóng sự cố khi repository xác định sửa chữa chưa được nghiệm thu.",
                "Hiển thị “" + MSG83 + "”; sự cố vẫn mở.",
                "Abnormal", "BR-45", "MSG83", "Black-box", RecordRepairOutcomeManualCases::closeBeforeVerified)
        );
    }

    private static ManualTest tc(String id, String input, String expected, String type, String br, String msg,
            String method, CheckedAction action) {
        return test(id, UC, "EquipmentIncidentService.repair/close", PRE, input, expected, type,
                note(FILE, br, msg, "EquipmentIncidentService.repair/close/manager", method), action);
    }

    private static RepairCommand validCommand() {
        return new RepairCommand("Nhà cung cấp A", "Đã thay bộ nguồn", new BigDecimal("1500000"),
                LocalDate.of(2026, 11, 14), "IN_USE");
    }

    private static MockMultipartFile pdf(String name) {
        return new MockMultipartFile(name, name + ".pdf", "application/pdf", "%PDF-1.7".getBytes());
    }

    private static TestResult validRepair() throws Exception {
        var f = fixture();
        f.service().repair(branchManager(), null, INCIDENT_ID, validCommand(), pdf("invoice"), pdf("transfer"));
        verify(f.fileStorage()).requireDriveConfigured();
        verify(f.repository()).setRepairEvidence(BRANCH_ID, INCIDENT_ID, invoiceFile().id(), transferFile().id());
        verify(f.repository()).updateAssetCondition(BRANCH_ID, INCIDENT_ID, "IN_USE");
        verify(f.repository()).logProgress(BRANCH_MANAGER_ID, INCIDENT_ID, "VERIFIED", "Đã thay bộ nguồn",
                LocalDate.of(2026, 11, 14));
        verify(f.repository(), never()).markRoomMaintenance(any(), any(), any(), any());
        return check(true, "repair stored; evidence linked; asset condition=IN_USE; progress=VERIFIED; room unchanged");
    }

    private static TestResult headOfficeRepair() throws Exception {
        var f = fixture();
        try {
            f.service().repair(headOfficeManager(), BRANCH_ID, INCIDENT_ID, validCommand(), pdf("invoice"), pdf("transfer"));
            return check(true, "repair recorded by Head Office Manager");
        } catch (ResponseStatusException failure) {
            return check(false, "HTTP " + failure.getStatusCode().value() + "; message=" + failure.getReason());
        }
    }

    private static TestResult nullCommand() throws Exception {
        var f = fixture();
        return expectStatus(() -> f.service().repair(branchManager(), null, INCIDENT_ID, null, pdf("invoice"), pdf("transfer")),
                400, "Vui lòng bổ sung thông tin kết quả sửa chữa.");
    }

    private static TestResult blankOutcome() throws Exception {
        var f = fixture();
        var c = new RepairCommand("Nhà cung cấp A", "   ", BigDecimal.TEN, LocalDate.of(2026, 11, 14), "IN_USE");
        return expectStatus(() -> f.service().repair(branchManager(), null, INCIDENT_ID, c, pdf("invoice"), pdf("transfer")),
                400, "Vui lòng bổ sung kết quả sửa chữa.");
    }

    private static TestResult missingCost() throws Exception {
        var f = fixture();
        var c = new RepairCommand("Nhà cung cấp A", "Đạt", null, LocalDate.of(2026, 11, 14), "IN_USE");
        return expectStatus(() -> f.service().repair(branchManager(), null, INCIDENT_ID, c, pdf("invoice"), pdf("transfer")),
                400, "Vui lòng bổ sung chi phí sửa chữa thực tế.");
    }

    private static TestResult negativeCost() throws Exception {
        var f = fixture();
        var c = new RepairCommand("Nhà cung cấp A", "Đạt", BigDecimal.ONE.negate(), LocalDate.of(2026, 11, 14), "IN_USE");
        return expectStatus(() -> f.service().repair(branchManager(), null, INCIDENT_ID, c, pdf("invoice"), pdf("transfer")),
                400, REQUIRED);
    }

    private static TestResult missingCompletionDate() throws Exception {
        var f = fixture();
        var c = new RepairCommand("Nhà cung cấp A", "Đạt", BigDecimal.TEN, null, "IN_USE");
        return expectStatus(() -> f.service().repair(branchManager(), null, INCIDENT_ID, c, pdf("invoice"), pdf("transfer")),
                400, "Vui lòng bổ sung ngày hoàn tất sửa chữa.");
    }

    private static TestResult invalidCondition() throws Exception {
        var f = fixture();
        var c = new RepairCommand("Nhà cung cấp A", "Đạt", BigDecimal.TEN, LocalDate.of(2026, 11, 14), "RETIRED");
        return expectStatus(() -> f.service().repair(branchManager(), null, INCIDENT_ID, c, pdf("invoice"), pdf("transfer")),
                400, "Vui lòng bổ sung tình trạng thiết bị sau sửa chữa hợp lệ.");
    }

    private static TestResult missingInvoice() throws Exception {
        var f = fixture();
        return expectStatus(() -> f.service().repair(branchManager(), null, INCIDENT_ID, validCommand(), null, pdf("transfer")),
                400, "Vui lòng bổ sung hóa đơn sửa chữa.");
    }

    private static TestResult missingTransferProof() throws Exception {
        var f = fixture();
        return expectStatus(() -> f.service().repair(branchManager(), null, INCIDENT_ID, validCommand(), pdf("invoice"), null),
                400, "Vui lòng bổ sung chứng từ chuyển khoản.");
    }

    private static TestResult notVerified() throws Exception {
        var f = fixture();
        when(f.repository().repair(eq(BRANCH_ID), eq(INCIDENT_ID), eq(BRANCH_MANAGER_ID), any())).thenReturn(0);
        return expectStatus(() -> f.service().repair(branchManager(), null, INCIDENT_ID, validCommand(), pdf("invoice"), pdf("transfer")),
                409, MSG83);
    }

    private static TestResult evidenceLinkFailure() throws Exception {
        var f = fixture();
        when(f.repository().setRepairEvidence(BRANCH_ID, INCIDENT_ID, invoiceFile().id(), transferFile().id())).thenReturn(false);
        TestResult result = expectStatus(() -> f.service().repair(branchManager(), null, INCIDENT_ID, validCommand(), pdf("invoice"), pdf("transfer")),
                404, "Không tìm thấy kết quả sửa chữa");
        verify(f.fileStorage()).deleteQuietly(invoiceFile().objectKey());
        verify(f.fileStorage()).deleteQuietly(transferFile().objectKey());
        return result;
    }

    private static TestResult secondUploadFailure() throws Exception {
        var f = fixture();
        var failure = new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Chỉ hỗ trợ tệp JPEG, PNG, WebP, HEIC, HEIF hoặc PDF. Vui lòng chọn tệp khác.");
        when(f.fileStorage().storeInDrive(any())).thenReturn(invoiceFile()).thenThrow(failure);
        TestResult result = expectStatus(() -> f.service().repair(branchManager(), null, INCIDENT_ID, validCommand(), pdf("invoice"), pdf("transfer")),
                400, "Chỉ hỗ trợ tệp JPEG, PNG, WebP, HEIC, HEIF hoặc PDF. Vui lòng chọn tệp khác.");
        verify(f.fileStorage()).deleteQuietly(invoiceFile().objectKey());
        return result;
    }

    private static TestResult closeBeforeVerified() throws Exception {
        var f = fixture();
        return expectStatus(() -> f.service().close(branchManager(), null, INCIDENT_ID, "Chưa nghiệm thu"), 409, MSG83);
    }
}
