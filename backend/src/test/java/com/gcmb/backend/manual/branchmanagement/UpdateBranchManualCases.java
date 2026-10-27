/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 28/10/2026
 * Description: Manual unit test cases for UC-11 Update Branch.
 */
package com.gcmb.backend.manual.branchmanagement;

import static com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.combine;
import static com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.sourceContains;
import static com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.test;
import static com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.validateAccepted;
import static com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.validateRejected;

import com.gcmb.backend.branch.dto.BranchDtos.BranchCommand;
import com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.ManualTest;
import java.util.List;

public final class UpdateBranchManualCases {
    private static final String UC = "UC-11 Update Branch";
    private static final String SERVICE = "src/main/java/com/gcmb/backend/branch/service/BranchAdministrationService.java";
    private static final String CONTROLLER = "src/main/java/com/gcmb/backend/branch/controller/BranchManagerAssignmentController.java";
    private static final String SECURITY = "src/main/java/com/gcmb/backend/auth/security/AuthSecurity.java";
    private static final String PAGE = "../frontend/src/features/branch/pages/BranchManagementPage.js";
    private static final String PRE = "Head Office Manager qlcc đã đăng nhập; branch demo Flyway V14 tồn tại.";
    private static final String MSG02_CODE = "Vui lòng bổ sung Mã cơ sở.";
    private static final String MSG02_NAME = "Vui lòng bổ sung Tên cơ sở.";
    private static final String MSG02_ADDRESS = "Vui lòng bổ sung Địa chỉ cơ sở.";
    private static final String MSG03_NAME = "Tên cơ sở chỉ được chứa tối đa 200 ký tự.";
    private static final String MSG03_ADDRESS = "Địa chỉ cơ sở chỉ được chứa tối đa 2000 ký tự.";
    private static final String MSG03_NOTE = "Ghi chú chỉ được chứa tối đa 4000 ký tự.";
    private static final String MSG04 = "Số điện thoại chỉ được chứa chữ số và có thể có một dấu + ở đầu.";
    private static final String MSG37 = "Mã cơ sở phải có từ 2 đến 60 ký tự và chỉ được dùng chữ cái không dấu, chữ số, dấu gạch dưới (_) hoặc dấu gạch nối (-).";
    private static final String MSG38 = "Mã cơ sở “HN_TAN_KHAI” đã tồn tại trong toàn hệ thống. Vui lòng nhập mã khác.";

    private UpdateBranchManualCases() {
    }

    public static List<ManualTest> cases() {
        return List.of(
                tc("BRM-042", "Update hợp lệ", "Đổi code/name/address/phone/notes",
                        "Giữ id và lưu toàn bộ thay đổi đã xác nhận.",
                        () -> combine(validateAccepted(valid("UPDATED_BRANCH")),
                                sourceContains(SERVICE, "UPDATE gcmb.branch SET code=?,name=?,address=?,phone=?,notes=?,updated_at=CURRENT_TIMESTAMP", "return get(id)")),
                        "BranchAdministrationService.update"),
                tc("BRM-043", "Không có phiên update", "PUT dữ liệu hợp lệ",
                        "HTTP 401; branch/history không đổi.",
                        () -> sourceContains(SECURITY, "/api/v1/head-office/**", "response.setStatus(401)"),
                        "AuthSecurity/SessionFilter"),
                tc("BRM-044", "Sai vai trò update", "BRANCH_MANAGER/RECEPTIONIST/ACCOUNTANT PUT update",
                        "HTTP 403 và đúng MSG24.",
                        () -> sourceContains(SECURITY, "/api/v1/head-office/**",
                                "Bạn không có quyền cập nhật đối với cơ sở."),
                        "AuthSecurity"),
                tc("BRM-045", "Hủy update", "Nhấn Hủy trước submit",
                        "Đóng modal, không gọi api.update.",
                        () -> sourceContains(PAGE, "onClick={() => setModal(null)}",
                                "else if (modal === 'edit') result = await api.update(selectedId, form)"),
                        "BranchManagementPage confirmation flow"),
                tc("BRM-046", "Update không đổi giá trị", "Form giữ nguyên",
                        "Không tạo record mới; quan hệ/history cũ còn nguyên.",
                        () -> sourceContains(SERVICE, "lock(id)", "UPDATE gcmb.branch SET", "WHERE id=?", "return get(id)"),
                        "BranchAdministrationService.update"),
                tc("BRM-047", "Code bắt buộc", "code=null, '', '   '",
                        "Từng giá trị bị từ chối bằng MSG02 cho Mã cơ sở.",
                        () -> combine(
                                validateRejected(command(null, "Tên", "Địa chỉ", "0901234567", ""), MSG02_CODE),
                                validateRejected(command("", "Tên", "Địa chỉ", "0901234567", ""), MSG02_CODE),
                                validateRejected(command("   ", "Tên", "Địa chỉ", "0901234567", ""), MSG02_CODE)),
                        "BranchAdministrationService.validate/update"),
                tc("BRM-048", "Code ngoài biên", "Code dài 1 và 61",
                        "Từ chối cả hai bằng MSG37.",
                        () -> combine(validateRejected(valid("A"), MSG37),
                                validateRejected(valid("A".repeat(61)), MSG37)),
                        "BranchAdministrationService.validate"),
                tc("BRM-049", "Code đúng biên", "Code dài 2 và 60",
                        "Chấp nhận cả hai biên.",
                        () -> combine(validateAccepted(valid("A1")), validateAccepted(valid("A".repeat(60)))),
                        "BranchAdministrationService.validate"),
                tc("BRM-050", "Code sai ký tự", "Khoảng trắng, dấu chấm, @, chữ có dấu",
                        "Tất cả bị từ chối bằng MSG37.",
                        () -> combine(validateRejected(valid("BR 01"), MSG37),
                                validateRejected(valid("BR.01"), MSG37),
                                validateRejected(valid("BR@01"), MSG37),
                                validateRejected(valid("CƠ_SỞ"), MSG37)),
                        "BranchAdministrationService.validate"),
                tc("BRM-051", "Chuẩn hóa code update", "code='  updated_branch_01  '",
                        "Trim và lưu uppercase.",
                        () -> combine(validateAccepted(valid("  updated_branch_01  ")),
                                sourceContains(SERVICE, "input.code().trim().toUpperCase(Locale.ROOT)")),
                        "BranchAdministrationService.update"),
                tc("BRM-052", "Name bắt buộc", "name=null/rỗng/khoảng trắng",
                        "Từ chối bằng MSG02 cho Tên cơ sở.",
                        () -> combine(validateRejected(command("BR01", null, "Địa chỉ", "0901234567", ""), MSG02_NAME),
                                validateRejected(command("BR01", "", "Địa chỉ", "0901234567", ""), MSG02_NAME),
                                validateRejected(command("BR01", "   ", "Địa chỉ", "0901234567", ""), MSG02_NAME)),
                        "BranchAdministrationService.validate"),
                tc("BRM-053", "Name đúng max", "name 200 ký tự",
                        "Chấp nhận đủ 200 ký tự.",
                        () -> validateAccepted(command("BR01", "N".repeat(200), "Địa chỉ", "0901234567", "")),
                        "BranchAdministrationService.validate"),
                tc("BRM-054", "Name vượt max", "name 201 ký tự",
                        "Từ chối bằng đúng MSG03.",
                        () -> validateRejected(command("BR01", "N".repeat(201), "Địa chỉ", "0901234567", ""), MSG03_NAME),
                        "BranchAdministrationService.validate"),
                tc("BRM-055", "Address bắt buộc", "address=null/rỗng/khoảng trắng",
                        "Từ chối bằng MSG02 cho Địa chỉ cơ sở.",
                        () -> combine(validateRejected(command("BR01", "Tên", null, "0901234567", ""), MSG02_ADDRESS),
                                validateRejected(command("BR01", "Tên", "", "0901234567", ""), MSG02_ADDRESS),
                                validateRejected(command("BR01", "Tên", "   ", "0901234567", ""), MSG02_ADDRESS)),
                        "BranchAdministrationService.validate"),
                tc("BRM-056", "Address đúng max", "address 2000 ký tự",
                        "Chấp nhận đủ 2000 ký tự.",
                        () -> validateAccepted(command("BR01", "Tên", "A".repeat(2000), "0901234567", "")),
                        "BranchAdministrationService.validate"),
                tc("BRM-057", "Address vượt max", "address 2001 ký tự",
                        "Từ chối bằng đúng MSG03.",
                        () -> validateRejected(command("BR01", "Tên", "A".repeat(2001), "0901234567", ""), MSG03_ADDRESS),
                        "BranchAdministrationService.validate"),
                tc("BRM-058", "Phone tùy chọn", "phone=null/rỗng/khoảng trắng",
                        "Chấp nhận và clean rỗng.",
                        () -> combine(validateAccepted(command("BR01", "Tên", "Địa chỉ", null, "")),
                                validateAccepted(command("BR02", "Tên", "Địa chỉ", "", "")),
                                validateAccepted(command("BR03", "Tên", "Địa chỉ", "   ", ""))),
                        "BranchAdministrationService.validate/clean"),
                tc("BRM-059", "Phone đúng biên source", "7 và 15 chữ số bắt đầu 0",
                        "Chấp nhận hai biên.",
                        () -> combine(validateAccepted(command("BR01", "Tên", "Địa chỉ", "0123456", "")),
                                validateAccepted(command("BR02", "Tên", "Địa chỉ", "0" + "1".repeat(14), ""))),
                        "BranchAdministrationService.validate"),
                tc("BRM-060", "Phone quốc tế BR-119", "phone=+84901234567",
                        "Chấp nhận một + ở đầu.",
                        () -> validateAccepted(command("BR01", "Tên", "Địa chỉ", "+84901234567", "")),
                        "BranchAdministrationService.validate/BranchManagementPage.phoneError"),
                tc("BRM-061", "Phone sai định dạng", "chữ/khoảng trắng/+ giữa/nhiều +",
                        "Từ chối bằng đúng MSG04.",
                        () -> combine(validateRejected(command("BR01", "Tên", "Địa chỉ", "09A123456", ""), MSG04),
                                validateRejected(command("BR02", "Tên", "Địa chỉ", "090 123 456", ""), MSG04),
                                validateRejected(command("BR03", "Tên", "Địa chỉ", "84+901234567", ""), MSG04),
                                validateRejected(command("BR04", "Tên", "Địa chỉ", "++84901234567", ""), MSG04)),
                        "BranchAdministrationService.validate"),
                tc("BRM-062", "Notes đúng max", "notes 4000 ký tự",
                        "Chấp nhận đủ 4000 ký tự.",
                        () -> validateAccepted(command("BR01", "Tên", "Địa chỉ", "0901234567", "N".repeat(4000))),
                        "BranchAdministrationService.validate"),
                tc("BRM-063", "Notes vượt max", "notes 4001 ký tự",
                        "Từ chối bằng đúng MSG03.",
                        () -> validateRejected(command("BR01", "Tên", "Địa chỉ", "0901234567", "N".repeat(4001)), MSG03_NOTE),
                        "BranchAdministrationService.validate"),
                tc("BRM-064", "Code trùng branch khác", "Dùng code HN_TAN_KHAI khác hoa thường",
                        "Từ chối bằng đúng MSG38.",
                        () -> sourceContains(SERVICE, "upper(code)=? AND id<>?", MSG38),
                        "BranchAdministrationService.update"),
                tc("BRM-065", "Giữ code của chính branch", "Update HN_HOA_BANG với cùng code",
                        "Không coi chính record là trùng.",
                        () -> sourceContains(SERVICE, "upper(code)=? AND id<>?"),
                        "BranchAdministrationService.update"),
                tc("BRM-066", "Branch không tồn tại", "PUT UUID không tồn tại",
                        "HTTP 404 Không tìm thấy cơ sở; không tạo record mới.",
                        () -> sourceContains(SERVICE, "SELECT id FROM gcmb.branch WHERE id=? FOR UPDATE",
                                "HttpStatus.NOT_FOUND, \"Không tìm thấy cơ sở.\""),
                        "BranchAdministrationService.lock/update"),
                tc("BRM-067", "Giữ status, opened_on và history", "Update thông tin branch có quan hệ",
                        "Chỉ 5 trường thông tin và updated_at thay đổi.",
                        () -> sourceContains(SERVICE,
                                "UPDATE gcmb.branch SET code=?,name=?,address=?,phone=?,notes=?,updated_at=CURRENT_TIMESTAMP"),
                        "BranchAdministrationService.update"),
                tc("BRM-068", "Audit/history và MSG11", "Update thành công rồi xem history",
                        "Một UPDATE_BRANCH audit event, history đọc được và đúng MSG11.",
                        () -> combine(sourceContains(CONTROLLER, "audit.record(\"UPDATE_BRANCH\""),
                                sourceContains(SERVICE, "public List<Map<String, Object>> history"),
                                sourceContains(PAGE, "Đã cập nhật cơ sở.")),
                        "BranchManagerAssignmentController.update/MutationAudit/BranchManagementPage.save")
        );
    }

    private static ManualTest tc(String id, String title, String input, String expected,
            BranchManagementManualRunner.CheckedAction action, String source) {
        return test(id, UC + " - " + title, source, PRE, input, expected, action);
    }

    private static BranchCommand valid(String code) {
        return command(code, "Cơ sở cập nhật", "Địa chỉ cập nhật", "0901234567", "Ghi chú");
    }

    private static BranchCommand command(String code, String name, String address, String phone, String notes) {
        return new BranchCommand(code, name, address, phone, notes);
    }
}
