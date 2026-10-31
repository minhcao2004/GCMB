/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 28/10/2026
 * Description: Manual unit test cases for UC-09 Create Branch.
 */
package com.gcmb.backend.manual.branchmanagement;

import static com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.combine;
import static com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.test;
import static com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.validateAccepted;
import static com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.validateRejected;

import com.gcmb.backend.branch.dto.BranchDtos.BranchCommand;
import com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.ManualTest;
import java.util.ArrayList;
import java.util.List;

public final class CreateBranchManualCases {
    private static final String UC = "UC-09 Create Branch";
    private static final String SERVICE = "src/main/java/com/gcmb/backend/branch/service/BranchAdministrationService.java";
    private static final String CONTROLLER = "src/main/java/com/gcmb/backend/branch/controller/BranchManagerAssignmentController.java";
    private static final String SECURITY = "src/main/java/com/gcmb/backend/auth/security/AuthSecurity.java";
    private static final String PAGE = "../frontend/src/features/branch/pages/BranchManagementPage.js";
    private static final String V14 = "src/main/resources/db/migration/V14__seed_genz_branches_and_accounts.sql";
    private static final String PRE = "Head Office Manager qlcc và dữ liệu cơ sở/tài khoản demo Flyway V14 tồn tại.";
    private static final String MSG02_CODE = "Vui lòng bổ sung Mã cơ sở.";
    private static final String MSG02_NAME = "Vui lòng bổ sung Tên cơ sở.";
    private static final String MSG02_ADDRESS = "Vui lòng bổ sung Địa chỉ cơ sở.";
    private static final String MSG03_NAME = "Tên cơ sở chỉ được chứa tối đa 200 ký tự.";
    private static final String MSG03_ADDRESS = "Địa chỉ cơ sở chỉ được chứa tối đa 2000 ký tự.";
    private static final String MSG03_NOTE = "Ghi chú chỉ được chứa tối đa 4000 ký tự.";
    private static final String MSG04 = "Số điện thoại chỉ được chứa chữ số và có thể có một dấu + ở đầu.";
    private static final String MSG37 = "Mã cơ sở phải có từ 2 đến 60 ký tự và chỉ được dùng chữ cái không dấu, chữ số, dấu gạch dưới (_) hoặc dấu gạch nối (-).";
    private static final String MSG38 = "Mã cơ sở “HN_HOA_BANG” đã tồn tại trong toàn hệ thống. Vui lòng nhập mã khác.";

    private CreateBranchManualCases() {
    }

    public static List<ManualTest> cases() {
        List<ManualTest> cases = new ArrayList<>();
                cases.add(tc("BRM-006", "Request body null", "body=null",
                "HTTP 400 và thông báo dữ liệu bắt buộc; không gọi DB.",
                () -> validateRejected(null, MSG02_CODE), "BranchAdministrationService.validate"));
        cases.add(tc("BRM-007", "Code null/rỗng/khoảng trắng", "code=null, '', '   '",
                "Từng giá trị bị từ chối bằng MSG02 cho Mã cơ sở.",
                () -> combine(
                        validateRejected(command(null, "Tên", "Địa chỉ", "0901234567", ""), MSG02_CODE),
                        validateRejected(command("", "Tên", "Địa chỉ", "0901234567", ""), MSG02_CODE),
                        validateRejected(command("   ", "Tên", "Địa chỉ", "0901234567", ""), MSG02_CODE)),
                "BranchAdministrationService.validate"));
        cases.add(tc("BRM-008", "Code dưới min", "code=A",
                "Từ chối bằng đúng MSG37.", () -> validateRejected(valid("A"), MSG37),
                "BranchAdministrationService.validate"));
        cases.add(tc("BRM-009", "Code đúng min", "code=A1",
                "Chấp nhận code 2 ký tự.", () -> validateAccepted(valid("A1")),
                "BranchAdministrationService.validate"));
        cases.add(tc("BRM-010", "Code đúng max", "code dài 60 ký tự hợp lệ",
                "Chấp nhận và không cắt ngắn.", () -> validateAccepted(valid("A".repeat(60))),
                "BranchAdministrationService.validate"));
        cases.add(tc("BRM-011", "Code vượt max", "code dài 61 ký tự",
                "Từ chối bằng đúng MSG37.", () -> validateRejected(valid("A".repeat(61)), MSG37),
                "BranchAdministrationService.validate"));
        cases.add(tc("BRM-012", "Code sai ký tự", "BR 01, BR.01, BR@01, CƠ_SỞ",
                "Tất cả bị từ chối bằng MSG37.", () -> combine(
                        validateRejected(valid("BR 01"), MSG37),
                        validateRejected(valid("BR.01"), MSG37),
                        validateRejected(valid("BR@01"), MSG37),
                        validateRejected(valid("CƠ_SỞ"), MSG37)), "BranchAdministrationService.validate"));
        cases.add(tc("BRM-014", "Name bắt buộc", "name=null, '', '   '",
                "Từng giá trị bị từ chối bằng MSG02 cho Tên cơ sở.", () -> combine(
                        validateRejected(command("BR01", null, "Địa chỉ", "0901234567", ""), MSG02_NAME),
                        validateRejected(command("BR01", "", "Địa chỉ", "0901234567", ""), MSG02_NAME),
                        validateRejected(command("BR01", "   ", "Địa chỉ", "0901234567", ""), MSG02_NAME)),
                "BranchAdministrationService.validate"));
        cases.add(tc("BRM-015", "Name đúng max", "name dài 200 ký tự Unicode",
                "Chấp nhận đủ 200 ký tự.", () -> validateAccepted(command("BR01", "Ừ".repeat(100), "Địa chỉ", "0901234567", "")),
                "BranchAdministrationService.validate"));
        cases.add(tc("BRM-016", "Name vượt max", "name dài 201 ký tự",
                "Từ chối bằng đúng MSG03 max_length=200.", () -> validateRejected(
                        command("BR01", "A".repeat(201), "Địa chỉ", "0901234567", ""), MSG03_NAME),
                "BranchAdministrationService.validate"));
        cases.add(tc("BRM-017", "Address bắt buộc", "address=null, '', '   '",
                "Từng giá trị bị từ chối bằng MSG02 cho Địa chỉ cơ sở.", () -> combine(
                        validateRejected(command("BR01", "Tên", null, "0901234567", ""), MSG02_ADDRESS),
                        validateRejected(command("BR01", "Tên", "", "0901234567", ""), MSG02_ADDRESS),
                        validateRejected(command("BR01", "Tên", "   ", "0901234567", ""), MSG02_ADDRESS)),
                "BranchAdministrationService.validate"));
        cases.add(tc("BRM-018", "Address đúng max", "address dài 2000 ký tự",
                "Chấp nhận đủ 2000 ký tự.", () -> validateAccepted(
                        command("BR01", "Tên", "Đ".repeat(2000), "0901234567", "")),
                "BranchAdministrationService.validate"));
        cases.add(tc("BRM-019", "Address vượt max", "address dài 2001 ký tự",
                "Từ chối bằng đúng MSG03 max_length=2000.", () -> validateRejected(
                        command("BR01", "Tên", "A".repeat(2001), "0901234567", ""), MSG03_ADDRESS),
                "BranchAdministrationService.validate"));
        cases.add(tc("BRM-020", "Phone tùy chọn", "phone=null, '', '   '",
                "Chấp nhận và clean thành chuỗi rỗng.", () -> combine(
                        validateAccepted(command("BR01", "Tên", "Địa chỉ", null, "")),
                        validateAccepted(command("BR02", "Tên", "Địa chỉ", "", "")),
                        validateAccepted(command("BR03", "Tên", "Địa chỉ", "   ", ""))),
                "BranchAdministrationService.validate/clean"));
        cases.add(tc("BRM-021", "Phone biên source", "7 và 15 chữ số bắt đầu 0",
                "Chấp nhận cả hai biên.", () -> combine(
                        validateAccepted(command("BR01", "Tên", "Địa chỉ", "0123456", "")),
                        validateAccepted(command("BR02", "Tên", "Địa chỉ", "0" + "1".repeat(14), ""))),
                "BranchAdministrationService.validate"));
        cases.add(tc("BRM-022", "Phone quốc tế theo BR-119", "phone=+84901234567",
                "Chấp nhận một dấu + ở đầu.", () -> validateAccepted(
                        command("BR01", "Tên", "Địa chỉ", "+84901234567", "")),
                "BranchAdministrationService.validate/BranchManagementPage.phoneError"));
        cases.add(tc("BRM-023", "Phone sai định dạng", "chữ, khoảng trắng, + giữa, nhiều +",
                "Tất cả bị từ chối bằng đúng MSG04.", () -> combine(
                        validateRejected(command("BR01", "Tên", "Địa chỉ", "09A123456", ""), MSG04),
                        validateRejected(command("BR02", "Tên", "Địa chỉ", "090 123 456", ""), MSG04),
                        validateRejected(command("BR03", "Tên", "Địa chỉ", "84+901234567", ""), MSG04),
                        validateRejected(command("BR04", "Tên", "Địa chỉ", "++84901234567", ""), MSG04)),
                "BranchAdministrationService.validate"));
        cases.add(tc("BRM-024", "Notes đúng max", "notes dài 4000 ký tự",
                "Chấp nhận đủ 4000 ký tự.", () -> validateAccepted(
                        command("BR01", "Tên", "Địa chỉ", "0901234567", "N".repeat(4000))),
                "BranchAdministrationService.validate"));
        cases.add(tc("BRM-025", "Notes vượt max", "notes dài 4001 ký tự",
                "Từ chối bằng đúng MSG03 max_length=4000.", () -> validateRejected(
                        command("BR01", "Tên", "Địa chỉ", "0901234567", "N".repeat(4001)), MSG03_NOTE),
                "BranchAdministrationService.validate"));
return List.copyOf(cases);
    }

    private static ManualTest tc(String id, String title, String input, String expected,
            BranchManagementManualRunner.CheckedAction action, String source) {
        return test(id, UC + " - " + title, source, PRE, input, expected, action);
    }

    private static BranchCommand valid(String code) {
        return command(code, "Cơ sở kiểm thử", "01 Đường Test", "0901234567", "Ghi chú");
    }

    private static BranchCommand command(String code, String name, String address, String phone, String notes) {
        return new BranchCommand(code, name, address, phone, notes);
    }
}


