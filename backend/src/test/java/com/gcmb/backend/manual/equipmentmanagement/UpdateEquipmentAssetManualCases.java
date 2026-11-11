/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 11/11/2026
 * Description: Defines UC-67 Update Equipment Asset manual unit test cases.
 */
package com.gcmb.backend.manual.equipmentmanagement;

import static com.gcmb.backend.manual.equipmentmanagement.EquipmentManagementManualRunner.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gcmb.backend.inventory.dto.EquipmentAssetDtos.Command;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;

final class UpdateEquipmentAssetManualCases {
    private static final String UC = "UC-67 Update Equipment Asset";
    private static final String FILE = "UC-67 - Update Equipment Asset.docx";
    private static final String PRE = "Quản lý cấp cao qlcc hoặc quản lý cơ sở qlcs.hoabang đã đăng nhập; ASSET-HN01-0001 tồn tại tại HN_HOA_BANG.";

    private UpdateEquipmentAssetManualCases() {}

    static List<ManualTest> cases() {
        return List.of(
            tc("EQM-021", "Quản lý cơ sở đổi name thành ' Máy chiếu laser ' và giữ branch hiện tại.",
                "Cập nhật thông tin đã chuẩn hóa; branch không đổi và lịch sử sự cố vẫn được trả về.",
                "Normal", "BR-110, BR-111, BR-202", "", "White-box", UpdateEquipmentAssetManualCases::branchUpdate),
            tc("EQM-022", "Quản lý cấp cao chọn branchId=HN_HOA_BANG và cập nhật thiết bị.",
                "Cập nhật đúng thiết bị trong HN_HOA_BANG; không đổi cơ sở ngoài quy trình điều chuyển.",
                "Normal", "BR-110, BR-202", "", "Black-box", UpdateEquipmentAssetManualCases::headOfficeUpdate),
            tc("EQM-023", "input=null.", "Hiển thị “Vui lòng bổ sung thông tin thiết bị.”; dữ liệu cũ không đổi.",
                "Abnormal", "BR-111, BR-202", "MSG02", "Black-box", UpdateEquipmentAssetManualCases::missingInput),
            tc("EQM-024", "name chỉ chứa khoảng trắng.", "Hiển thị “Vui lòng bổ sung tên thiết bị.”; dữ liệu cũ không đổi.",
                "Abnormal", "BR-111, BR-202", "MSG02", "Black-box", UpdateEquipmentAssetManualCases::missingName),
            tc("EQM-025", "categoryId thuộc nhóm ngừng hoạt động/ngoài cơ sở.",
                "Hiển thị “Chọn nhóm đang hoạt động trong cơ sở”; không cập nhật.",
                "Abnormal", "BR-202", "", "White-box", UpdateEquipmentAssetManualCases::invalidCategory),
            tc("EQM-026", "roomId thuộc cơ sở khác.",
                "Hiển thị “Phòng/vị trí phải thuộc cơ sở đang quản lý và còn sử dụng”; không cập nhật.",
                "Abnormal", "BR-202", "", "White-box", UpdateEquipmentAssetManualCases::invalidRoom),
            tc("EQM-027", "Thiết bị hiện đang IN_TRANSIT.",
                "Hiển thị “Thiết bị đang chuyển cơ sở; chỉ UC75–UC76 được cập nhật sau xác nhận bàn giao”; không bỏ qua quy trình điều chuyển.",
                "Abnormal", "BR-110", "", "White-box", UpdateEquipmentAssetManualCases::currentInTransit),
            tc("EQM-028", "Thiết bị đang IN_USE nhưng input.status=IN_TRANSIT.",
                "Hiển thị “Chỉ quy trình điều chuyển UC74–UC76 được chuyển thiết bị sang trạng thái đang chuyển”; không cập nhật.",
                "Abnormal", "BR-110", "", "White-box", UpdateEquipmentAssetManualCases::requestInTransit),
            tc("EQM-029", "đổi code thành ASSET-HN01-0002 đã tồn tại tại cơ sở.",
                "Hiển thị “Mã thiết bị “ASSET-HN01-0002” đã tồn tại tại cơ sở này. Vui lòng nhập mã khác.”; dữ liệu cũ không đổi.",
                "Abnormal", "BR-202", "MSG38", "Black-box", UpdateEquipmentAssetManualCases::duplicate),
            tc("EQM-030", "Quản lý cơ sở HN_HOA_BANG gửi branchId của cơ sở khác.",
                "Hiển thị “Bạn không có quyền cập nhật đối với thiết bị.”; không cập nhật ngoài phạm vi.",
                "Abnormal", "BR-202", "MSG24", "Black-box", UpdateEquipmentAssetManualCases::wrongBranch)
        );
    }

    private static ManualTest tc(String id, String input, String expected, String type, String br, String msg,
            String method, CheckedAction action) {
        return test(id, UC, "EquipmentAssetService.update", PRE, input, expected, type,
                note(FILE, br, msg, "EquipmentAssetService.update/validate", method), action);
    }

    private static TestResult branchUpdate() {
        var f = fixture();
        Command input = command(" ASSET-HN01-0001 ", " Máy chiếu laser ", CATEGORY_ID, ROOM_ID, " Sân khấu ", "IN_USE");
        var result = f.service().update(branchManager(), null, ASSET_ID, input);
        verify(f.repository()).update(eq(BRANCH_ID), eq(ASSET_ID),
                eq(command("ASSET-HN01-0001", "Máy chiếu laser", CATEGORY_ID, ROOM_ID, "Sân khấu", "IN_USE")));
        return check(BRANCH_ID.equals(result.branchId()) && INCIDENT_ID.equals(result.latestIncidentId()),
                "branchId=" + result.branchId() + "; latestIncidentId=" + result.latestIncidentId());
    }

    private static TestResult headOfficeUpdate() {
        var f = fixture();
        var result = f.service().update(headOfficeManager(), BRANCH_ID, ASSET_ID,
                command("ASSET-HN01-0001", "Máy chiếu laser", CATEGORY_ID, ROOM_ID, null, "IN_USE"));
        return check(BRANCH_ID.equals(result.branchId()), "branchId=" + result.branchId());
    }

    private static TestResult missingInput() {
        var f = fixture();
        return expectStatus(() -> f.service().update(branchManager(), BRANCH_ID, ASSET_ID, null), 400,
                "Vui lòng bổ sung thông tin thiết bị.");
    }

    private static TestResult missingName() {
        var f = fixture();
        return expectStatus(() -> f.service().update(branchManager(), BRANCH_ID, ASSET_ID,
                command("ASSET-HN01-0001", "   ", CATEGORY_ID, ROOM_ID, null, "IN_USE")), 400,
                "Vui lòng bổ sung tên thiết bị.");
    }

    private static TestResult invalidCategory() {
        var f = fixture();
        when(f.repository().categoryAvailable(BRANCH_ID, OTHER_CATEGORY_ID, ASSET_ID)).thenReturn(false);
        return expectStatus(() -> f.service().update(branchManager(), BRANCH_ID, ASSET_ID,
                command("ASSET-HN01-0001", "Máy chiếu", OTHER_CATEGORY_ID, ROOM_ID, null, "IN_USE")), 400,
                "Chọn nhóm đang hoạt động trong cơ sở");
    }

    private static TestResult invalidRoom() {
        var f = fixture();
        when(f.repository().roomAvailable(BRANCH_ID, OTHER_ROOM_ID, ASSET_ID)).thenReturn(false);
        return expectStatus(() -> f.service().update(branchManager(), BRANCH_ID, ASSET_ID,
                command("ASSET-HN01-0001", "Máy chiếu", CATEGORY_ID, OTHER_ROOM_ID, null, "IN_USE")), 400,
                "Phòng/vị trí phải thuộc cơ sở đang quản lý và còn sử dụng");
    }

    private static TestResult currentInTransit() {
        var f = fixture();
        when(f.repository().get(BRANCH_ID, ASSET_ID))
                .thenReturn(view(ASSET_ID, "ASSET-HN01-0001", "Máy chiếu", "IN_TRANSIT", true, false, null));
        return expectStatus(() -> f.service().update(branchManager(), BRANCH_ID, ASSET_ID,
                command("ASSET-HN01-0001", "Máy chiếu", CATEGORY_ID, ROOM_ID, null, "IN_USE")), 409,
                "Thiết bị đang chuyển cơ sở; chỉ UC75–UC76 được cập nhật sau xác nhận bàn giao");
    }

    private static TestResult requestInTransit() {
        var f = fixture();
        return expectStatus(() -> f.service().update(branchManager(), BRANCH_ID, ASSET_ID,
                command("ASSET-HN01-0001", "Máy chiếu", CATEGORY_ID, ROOM_ID, null, "IN_TRANSIT")), 409,
                "Chỉ quy trình điều chuyển UC74–UC76 được chuyển thiết bị sang trạng thái đang chuyển");
    }

    private static TestResult duplicate() {
        var f = fixture();
        when(f.repository().update(eq(BRANCH_ID), eq(ASSET_ID), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        return expectStatus(() -> f.service().update(branchManager(), BRANCH_ID, ASSET_ID,
                command("ASSET-HN01-0002", "Máy chiếu", CATEGORY_ID, ROOM_ID, null, "IN_USE")), 409,
                "Mã thiết bị “ASSET-HN01-0002” đã tồn tại tại cơ sở này. Vui lòng nhập mã khác.");
    }

    private static TestResult wrongBranch() {
        var f = fixture();
        TestResult result = expectStatus(() -> f.service().update(branchManager(), OTHER_BRANCH_ID, ASSET_ID,
                command("ASSET-HN01-0001", "Máy chiếu", CATEGORY_ID, ROOM_ID, null, "IN_USE")), 403,
                "Bạn không có quyền cập nhật đối với thiết bị.");
        verify(f.repository(), never()).update(eq(BRANCH_ID), eq(ASSET_ID), any());
        return result;
    }
}
