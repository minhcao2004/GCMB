/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 11/11/2026
 * Description: Defines UC-65 Create Equipment Asset manual unit test cases.
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.mock.web.MockMultipartFile;

final class CreateEquipmentAssetManualCases {
    private static final String UC = "UC-65 Create Equipment Asset";
    private static final String FILE = "UC-65 - Create Equipment Asset.docx";
    private static final String PRE = "Quản lý cấp cao qlcc hoặc quản lý cơ sở qlcs.hoabang đã đăng nhập; cơ sở HN_HOA_BANG đang hoạt động; nhóm EQ-AUDIO và phòng P101 hợp lệ.";

    private CreateEquipmentAssetManualCases() {}

    static List<ManualTest> cases() {
        return List.of(
            tc("EQM-001", "Quản lý cơ sở tạo ASSET-HN01-0002, nhóm EQ-AUDIO, phòng P101, trạng thái IN_USE.",
                "Tạo thiết bị thành công tại HN_HOA_BANG; trạng thái sử dụng active và thông tin được chuẩn hóa.",
                "Normal", "BR-110, BR-111, BR-202", "", "White-box", CreateEquipmentAssetManualCases::branchCreateWithRoom),
            tc("EQM-002", "roomId=null; locationNote=' Kho tầng 2 '.",
                "Tạo thiết bị thành công với vị trí mô tả được chuẩn hóa thành 'Kho tầng 2'.",
                "Normal", "BR-110, BR-202", "", "White-box", CreateEquipmentAssetManualCases::createWithLocationNote),
            tc("EQM-003", "Quản lý cấp cao chọn branchId=HN_HOA_BANG và tạo thiết bị hợp lệ.",
                "Tạo thiết bị trong đúng cơ sở đã chọn; không gán sang cơ sở khác.",
                "Normal", "BR-110, BR-202", "", "Black-box", CreateEquipmentAssetManualCases::headOfficeCreate),
            tc("EQM-004", "code chỉ chứa khoảng trắng.", "Hiển thị “Vui lòng bổ sung mã thiết bị.”; không tạo dữ liệu.",
                "Abnormal", "BR-111, BR-202", "MSG02", "Black-box",
                () -> required(command("   ", "Loa sân khấu", CATEGORY_ID, ROOM_ID, null, "IN_USE"), "Vui lòng bổ sung mã thiết bị.")),
            tc("EQM-005", "name chỉ chứa khoảng trắng.", "Hiển thị “Vui lòng bổ sung tên thiết bị.”; không tạo dữ liệu.",
                "Abnormal", "BR-111, BR-202", "MSG02", "Black-box",
                () -> required(command("ASSET-HN01-0002", "   ", CATEGORY_ID, ROOM_ID, null, "IN_USE"), "Vui lòng bổ sung tên thiết bị.")),
            tc("EQM-006", "categoryId=null.", "Hiển thị “Vui lòng bổ sung nhóm thiết bị.”; không tạo dữ liệu.",
                "Abnormal", "BR-111, BR-202", "MSG02", "Black-box",
                () -> required(command("ASSET-HN01-0002", "Loa sân khấu", null, ROOM_ID, null, "IN_USE"), "Vui lòng bổ sung nhóm thiết bị.")),
            tc("EQM-007", "roomId=null và locationNote chỉ chứa khoảng trắng.",
                "Hiển thị “Vui lòng bổ sung vị trí thiết bị.”; không tạo dữ liệu.",
                "Abnormal", "BR-111, BR-202", "MSG02", "Black-box",
                () -> required(command("ASSET-HN01-0002", "Loa sân khấu", CATEGORY_ID, null, "   ", "IN_USE"), "Vui lòng bổ sung vị trí thiết bị.")),
            tc("EQM-008", "categoryId thuộc nhóm ngừng hoạt động/ngoài cơ sở.",
                "Hiển thị “Chọn nhóm đang hoạt động trong cơ sở”; không tạo dữ liệu.",
                "Abnormal", "BR-202", "", "White-box", CreateEquipmentAssetManualCases::invalidCategory),
            tc("EQM-009", "roomId thuộc cơ sở khác.",
                "Hiển thị “Phòng/vị trí phải thuộc cơ sở đang quản lý và còn sử dụng”; không tạo dữ liệu.",
                "Abnormal", "BR-202", "", "White-box", CreateEquipmentAssetManualCases::invalidRoom),
            tc("EQM-010", "code=ASSET-HN01-0001 đã tồn tại tại HN_HOA_BANG.",
                "Hiển thị “Mã thiết bị “ASSET-HN01-0001” đã tồn tại tại cơ sở này. Vui lòng nhập mã khác.”; không tạo trùng.",
                "Abnormal", "BR-202", "MSG38", "Black-box", CreateEquipmentAssetManualCases::duplicate),
            tc("EQM-011", "Quản lý cơ sở HN_HOA_BANG gửi branchId của cơ sở khác.",
                "Hiển thị “Bạn không có quyền tạo đối với thiết bị.”; không tạo dữ liệu ngoài phạm vi.",
                "Abnormal", "BR-202", "MSG24", "Black-box", CreateEquipmentAssetManualCases::wrongBranch),
            fileTc("EQM-012", "Ảnh PNG có chữ ký hợp lệ và dung lượng lớn hơn 0.",
                "File được chấp nhận, lưu bằng object key nội bộ và metadata đúng image/png.",
                "Normal", "BR-115, BR-116", "", "White-box", CreateEquipmentAssetManualCases::validPng),
            fileTc("EQM-013", "Tệp TXT khai báo text/plain.",
                "Hiển thị “Chỉ hỗ trợ tệp JPEG, PNG, WebP, HEIC, HEIF hoặc PDF. Vui lòng chọn tệp khác.”; không lưu file.",
                "Abnormal", "BR-115", "MSG78", "Black-box", CreateEquipmentAssetManualCases::unsupportedFile),
            fileTc("EQM-014", "Lần lượt file rỗng và file có getSize=20.000.001 byte.",
                "Mỗi file đều hiển thị “Tệp phải có dung lượng lớn hơn 0 và không vượt quá 20 MB.”; không lưu file.",
                "Boundary", "BR-116", "MSG79", "Black-box", CreateEquipmentAssetManualCases::sizeBoundary),
            fileTc("EQM-015", "Khai báo image/png nhưng nội dung bắt đầu bằng %PDF-.",
                "Hiển thị “Không thể đọc tệp hoặc định dạng tệp không khớp với nội dung. Vui lòng xuất lại tệp đúng định dạng và tải lên lại.”; không lưu file.",
                "Abnormal", "BR-115", "MSG80", "Black-box", CreateEquipmentAssetManualCases::mismatchedFile)
        );
    }

    private static ManualTest tc(String id, String input, String expected, String type, String br, String msg,
            String method, CheckedAction action) {
        return test(id, UC, "EquipmentAssetService.create", PRE, input, expected, type,
                note(FILE, br, msg, "EquipmentAssetService.create/validate", method), action);
    }

    private static ManualTest fileTc(String id, String input, String expected, String type, String br, String msg,
            String method, CheckedAction action) {
        return test(id, UC, "EquipmentAssetFileStorageService.store", PRE, input, expected, type,
                note(FILE, br, msg, "EquipmentAssetFileStorageService.store", method), action);
    }

    private static TestResult branchCreateWithRoom() {
        var f = fixture();
        Command input = command(" ASSET-HN01-0002 ", " Loa sân khấu ", CATEGORY_ID, ROOM_ID, null, "IN_USE");
        var result = f.service().create(branchManager(), null, input);
        verify(f.repository()).create(eq(BRANCH_ID), eq(command("ASSET-HN01-0002", "Loa sân khấu", CATEGORY_ID, ROOM_ID, null, "IN_USE")));
        return check(NEW_ASSET_ID.equals(result.id()) && BRANCH_ID.equals(result.branchId()) && result.active(),
                "id=" + result.id() + "; branchId=" + result.branchId() + "; active=" + result.active());
    }

    private static TestResult createWithLocationNote() {
        var f = fixture();
        Command input = command("ASSET-HN01-0002", "Loa sân khấu", CATEGORY_ID, null, " Kho tầng 2 ", "IN_USE");
        f.service().create(branchManager(), BRANCH_ID, input);
        verify(f.repository()).create(eq(BRANCH_ID), eq(command("ASSET-HN01-0002", "Loa sân khấu", CATEGORY_ID, null, "Kho tầng 2", "IN_USE")));
        return check(true, "repository.create received locationNote=Kho tầng 2");
    }

    private static TestResult headOfficeCreate() {
        var f = fixture();
        var result = f.service().create(headOfficeManager(), BRANCH_ID,
                command("ASSET-HN01-0002", "Loa sân khấu", CATEGORY_ID, ROOM_ID, null, "IN_USE"));
        return check(BRANCH_ID.equals(result.branchId()), "branchId=" + result.branchId());
    }

    private static TestResult required(Command input, String message) {
        var f = fixture();
        TestResult result = expectStatus(() -> f.service().create(branchManager(), BRANCH_ID, input), 400, message);
        verify(f.repository(), never()).create(eq(BRANCH_ID), any());
        return result;
    }

    private static TestResult invalidCategory() {
        var f = fixture();
        when(f.repository().categoryAvailable(BRANCH_ID, OTHER_CATEGORY_ID, null)).thenReturn(false);
        return expectStatus(() -> f.service().create(branchManager(), BRANCH_ID,
                command("ASSET-HN01-0002", "Loa", OTHER_CATEGORY_ID, ROOM_ID, null, "IN_USE")), 400,
                "Chọn nhóm đang hoạt động trong cơ sở");
    }

    private static TestResult invalidRoom() {
        var f = fixture();
        when(f.repository().roomAvailable(BRANCH_ID, OTHER_ROOM_ID, null)).thenReturn(false);
        return expectStatus(() -> f.service().create(branchManager(), BRANCH_ID,
                command("ASSET-HN01-0002", "Loa", CATEGORY_ID, OTHER_ROOM_ID, null, "IN_USE")), 400,
                "Phòng/vị trí phải thuộc cơ sở đang quản lý và còn sử dụng");
    }

    private static TestResult duplicate() {
        var f = fixture();
        when(f.repository().create(eq(BRANCH_ID), any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        return expectStatus(() -> f.service().create(branchManager(), BRANCH_ID,
                command("ASSET-HN01-0001", "Máy chiếu", CATEGORY_ID, ROOM_ID, null, "IN_USE")), 409,
                "Mã thiết bị “ASSET-HN01-0001” đã tồn tại tại cơ sở này. Vui lòng nhập mã khác.");
    }

    private static TestResult wrongBranch() {
        var f = fixture();
        return expectStatus(() -> f.service().create(branchManager(), OTHER_BRANCH_ID,
                command("ASSET-HN01-0002", "Loa", CATEGORY_ID, ROOM_ID, null, "IN_USE")), 403,
                "Bạn không có quyền tạo đối với thiết bị.");
    }

    private static TestResult validPng() throws Exception {
        var f = storageFixture();
        String key = null;
        try {
            byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
            var stored = f.storage().store(new MockMultipartFile("image", "asset.png", "image/png", png));
            key = stored.objectKey();
            return check("image/png".equals(stored.mimeType()) && stored.byteSize() == png.length,
                    "mimeType=" + stored.mimeType() + "; byteSize=" + stored.byteSize() + "; objectKey=" + key);
        } finally {
            cleanup(f, key);
        }
    }

    private static TestResult unsupportedFile() throws Exception {
        var f = storageFixture();
        try {
            return expectStatus(() -> f.storage().store(new MockMultipartFile("image", "asset.txt", "text/plain", new byte[]{1})),
                    400, "Chỉ hỗ trợ tệp JPEG, PNG, WebP, HEIC, HEIF hoặc PDF. Vui lòng chọn tệp khác.");
        } finally {
            cleanup(f, null);
        }
    }

    private static TestResult sizeBoundary() throws Exception {
        var f = storageFixture();
        try {
            TestResult empty = expectStatus(() -> f.storage().store(new MockMultipartFile("image", "asset.png", "image/png", new byte[0])),
                    400, "Tệp phải có dung lượng lớn hơn 0 và không vượt quá 20 MB.");
            MultipartFile large = org.mockito.Mockito.mock(MultipartFile.class);
            when(large.isEmpty()).thenReturn(false);
            when(large.getSize()).thenReturn(20_000_001L);
            TestResult oversized = expectStatus(() -> f.storage().store(large), 400,
                    "Tệp phải có dung lượng lớn hơn 0 và không vượt quá 20 MB.");
            return check(empty.passed() && oversized.passed(), "empty=" + empty.actual() + "; oversized=" + oversized.actual());
        } finally {
            cleanup(f, null);
        }
    }

    private static TestResult mismatchedFile() throws Exception {
        var f = storageFixture();
        try {
            return expectStatus(() -> f.storage().store(new MockMultipartFile("image", "asset.png", "image/png", "%PDF-1.7".getBytes())),
                    400, "Không thể đọc tệp hoặc định dạng tệp không khớp với nội dung. Vui lòng xuất lại tệp đúng định dạng và tải lên lại.");
        } finally {
            cleanup(f, null);
        }
    }
}
