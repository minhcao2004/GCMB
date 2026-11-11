/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 11/11/2026
 * Description: Manual unit cases for UC-72 View Equipment Incidents.
 */
package com.gcmb.backend.manual.equipmentmaintenance;

import static com.gcmb.backend.manual.equipmentmaintenance.EquipmentMaintenanceManualRunner.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

final class ViewEquipmentIncidentsManualCases {
    private static final String UC = "UC-72 View Equipment Incidents";
    private static final String FILE = "UC-72 - View Equipment Incidents.docx";
    private static final String PRE = "Người dùng đã đăng nhập; dữ liệu sự cố của HN_HOA_BANG tồn tại hoặc được thiết lập rỗng theo từng TC; thao tác xem không thay đổi bản ghi nghiệp vụ.";

    private ViewEquipmentIncidentsManualCases() {}

    static List<ManualTest> cases() {
        return List.of(
            tc("EMM-032", "Branch Manager xem danh sách sự cố của cơ sở được phân công.",
                "Trả sự cố ASSET-HN01-0001 thuộc HN_HOA_BANG; không thay đổi dữ liệu.",
                "Normal", "", "", "Black-box", ViewEquipmentIncidentsManualCases::branchManagerList),
            tc("EMM-033", "Receptionist xem danh sách sự cố của cơ sở được phân công.",
                "Trả danh sách trong phạm vi HN_HOA_BANG; không thay đổi dữ liệu.",
                "Normal", "BR-41", "", "Black-box", ViewEquipmentIncidentsManualCases::receptionistList),
            tc("EMM-034", "Head Office Manager chọn branchId=HN_HOA_BANG.",
                "Trả danh sách sự cố của cơ sở đã chọn.",
                "Normal", "", "", "Black-box", ViewEquipmentIncidentsManualCases::headOfficeList),
            tc("EMM-035", "search='  máy chiếu  '; status=IN_PROGRESS.",
                "Chuẩn hóa search thành 'máy chiếu' và chuyển đúng bộ lọc trạng thái xuống repository.",
                "Normal", "", "", "White-box", ViewEquipmentIncidentsManualCases::filterForwarding),
            tc("EMM-036", "search=null; status=null.",
                "Chuẩn hóa cả hai thành chuỗi rỗng và trả danh sách trong phạm vi.",
                "Boundary", "", "", "White-box", ViewEquipmentIncidentsManualCases::nullFilters),
            tc("EMM-037", "Không có sự cố phù hợp với tiêu chí tìm kiếm.",
                "Trả danh sách rỗng; không thay đổi bất kỳ bản ghi nghiệp vụ nào.",
                "Abnormal", "", "", "Black-box", ViewEquipmentIncidentsManualCases::emptyResult),
            tc("EMM-038", "Receptionist HN_HOA_BANG yêu cầu branchId của cơ sở khác.",
                "Hiển thị “Bạn không có quyền xem đối với sự cố thiết bị.”; không truy vấn ngoài phạm vi.",
                "Abnormal", "BR-41", "MSG24", "Black-box", ViewEquipmentIncidentsManualCases::wrongBranch),
            tc("EMM-039", "Branch Manager mở tiến trình của incident đang tồn tại trong cơ sở.",
                "Trả lịch sử tiến trình theo thứ tự repository cung cấp; không thay đổi dữ liệu.",
                "Normal", "", "", "Black-box", ViewEquipmentIncidentsManualCases::progress),
            tc("EMM-040", "Branch Manager mở chứng từ invoice của lần sửa gần nhất.",
                "Trả tên hoa-don.pdf, MIME application/pdf và đúng nội dung file.",
                "Normal", "BR-46", "", "White-box", ViewEquipmentIncidentsManualCases::repairAttachment)
        );
    }

    private static ManualTest tc(String id, String input, String expected, String type, String br, String msg,
            String method, CheckedAction action) {
        return test(id, UC, "EquipmentIncidentService.incidents/progress/repairAttachment", PRE, input, expected, type,
                note(FILE, br, msg, "EquipmentIncidentService.incidents/progress/repairAttachment/branch", method), action);
    }

    private static TestResult branchManagerList() throws Exception {
        var f = fixture();
        var result = f.service().incidents(branchManager(), null, "", "");
        return check(result.size() == 1 && INCIDENT_ID.equals(result.get(0).id()), "incidents=" + result);
    }

    private static TestResult receptionistList() throws Exception {
        var f = fixture();
        var result = f.service().incidents(receptionist(), null, "", "");
        return check(result.size() == 1 && BRANCH_ID.equals(result.get(0).branchId()), "incidents=" + result);
    }

    private static TestResult headOfficeList() throws Exception {
        var f = fixture();
        var result = f.service().incidents(headOfficeManager(), BRANCH_ID, "", "");
        return check(result.size() == 1 && BRANCH_ID.equals(result.get(0).branchId()), "incidents=" + result);
    }

    private static TestResult filterForwarding() throws Exception {
        var f = fixture();
        f.service().incidents(branchManager(), null, "  máy chiếu  ", "IN_PROGRESS");
        verify(f.repository()).incidents(BRANCH_ID, "máy chiếu", "IN_PROGRESS");
        return check(true, "repository received search='máy chiếu'; status=IN_PROGRESS");
    }

    private static TestResult nullFilters() throws Exception {
        var f = fixture();
        f.service().incidents(branchManager(), null, null, null);
        verify(f.repository()).incidents(BRANCH_ID, "", "");
        return check(true, "repository received empty search and status");
    }

    private static TestResult emptyResult() throws Exception {
        var f = fixture();
        when(f.repository().incidents(BRANCH_ID, "không tồn tại", "")).thenReturn(List.of());
        var result = f.service().incidents(branchManager(), null, "không tồn tại", "");
        return check(result.isEmpty(), "result size=" + result.size());
    }

    private static TestResult wrongBranch() throws Exception {
        var f = fixture();
        return expectStatus(() -> f.service().incidents(receptionist(), OTHER_BRANCH_ID, "", ""), 403,
                "Bạn không có quyền xem đối với sự cố thiết bị.");
    }

    private static TestResult progress() throws Exception {
        var f = fixture();
        var result = f.service().progress(branchManager(), null, INCIDENT_ID);
        return check(result.size() == 1 && PROGRESS_ID.equals(result.get(0).id()), "progress=" + result);
    }

    private static TestResult repairAttachment() throws Exception {
        var f = fixture();
        var result = f.service().repairAttachment(branchManager(), INCIDENT_ID, "invoice");
        return check("hoa-don.pdf".equals(result.originalName()) && "application/pdf".equals(result.mimeType())
                && result.bytes().length > 0, "name=" + result.originalName() + "; mime=" + result.mimeType()
                + "; bytes=" + result.bytes().length);
    }
}
