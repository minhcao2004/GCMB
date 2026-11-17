/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 12/11/2026
 * Description: Manual unit test cases cho UC-77 Create Staff Record.
 */
package com.gcmb.backend.manual.staffmanagement;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gcmb.backend.accounts.repository.AccountRepository;
import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.exception.AuthFailure;
import com.gcmb.backend.manual.staffmanagement.StaffManagementManualRunner.ManualCase;
import com.gcmb.backend.manual.staffmanagement.StaffManagementManualRunner.Observation;
import com.gcmb.backend.workforce.dto.StaffDtos.StaffCommand;
import com.gcmb.backend.workforce.repository.StaffRepository;
import com.gcmb.backend.workforce.service.StaffService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class CreateStaffRecordManualCases {
    private static final UUID HOM_ID = UUID.fromString("47570000-0000-4000-8000-000000000005");
    private static final UUID BM_ID = UUID.fromString("47570000-0000-4000-8000-000000000003");
    private static final UUID ACCOUNTANT_ID = UUID.fromString("47570000-0000-4000-8000-000000000006");
    private static final UUID HOA_BANG = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final LocalDate HIRED_ON = LocalDate.of(2026, 11, 1);
    private static final String TITLE = "UC-77 Create Staff Record";
    private static final String SOURCE = "UC-77; BR-52, BR-111, BR-119, BR-177; MSG02, MSG03, MSG04, MSG05, MSG24, MSG43; V14, V26; StaffService.create";

    private CreateStaffRecordManualCases() {
    }

    static List<ManualCase> cases() {
        return List.of(
            test("SFM-001",
                "Head Office Manager qlcc đã đăng nhập.",
                "Tạo nhân viên SFM-NV-001 với họ tên Nguyễn Minh An, vị trí OTHER, số điện thoại 0901234567 và ngày vào làm 01/11/2026.",
                "Hồ sơ được tạo với đúng thông tin và trạng thái ACTIVE.",
                () -> validCreate(false)),
            test("SFM-002",
                "Branch Manager qlcs.hoabang đã đăng nhập và được phân công tại cơ sở Hoa Bằng đang hoạt động.",
                "Tạo hồ sơ SFM-NV-002 với dữ liệu hợp lệ.",
                "Hồ sơ được tạo và tự động phân công vào cơ sở Hoa Bằng.",
                () -> validCreate(true)),
            test("SFM-003",
                "Head Office Manager đã đăng nhập; các trường không bị bỏ trống đều hợp lệ.",
                "Lần lượt để trống hoặc nhập khoảng trắng tại mã nhân viên, họ tên, vị trí; hoặc không nhập ngày vào làm.",
                "Hiển thị đúng thông báo yêu cầu bổ sung từng trường; không tạo hồ sơ.",
                CreateStaffRecordManualCases::requiredFields),
            test("SFM-004",
                "Head Office Manager đã đăng nhập.",
                "Nhập số điện thoại 09A1234567.",
                "Hiển thị “Số điện thoại chỉ được chứa chữ số và có thể có một dấu + ở đầu.”; không tạo hồ sơ.",
                () -> rejected(command("SFM-NV-004", "Nguyễn Minh An", "09A1234567", "OTHER", null),
                    "Số điện thoại chỉ được chứa chữ số và có thể có một dấu + ở đầu.", ready())),
            test("SFM-005",
                "Head Office Manager đã đăng nhập.",
                "Nhập email liên hệ caonm@invalid.",
                "Hiển thị “Nhập địa chỉ email theo dạng ten@tenmien.com.”; không tạo hồ sơ.",
                () -> rejected(command("SFM-NV-005", "Nguyễn Minh An", null, "OTHER", "caonm@invalid"),
                    "Nhập địa chỉ email theo dạng ten@tenmien.com.", ready())),
            test("SFM-006",
                "Head Office Manager đã đăng nhập; mã, số định danh, email và số điện thoại tương ứng đã tồn tại ở hồ sơ khác.",
                "Lần lượt tạo hồ sơ dùng dữ liệu trùng.",
                "Hiển thị đúng bốn thông báo trùng dữ liệu; không tạo hồ sơ.",
                CreateStaffRecordManualCases::duplicateFields),
            test("SFM-007",
                "Head Office Manager đã đăng nhập.",
                "Nhập vị trí DIRECTOR.",
                "Hiển thị “Vị trí nhân viên không hợp lệ.”; không tạo hồ sơ.",
                () -> rejected(command("SFM-NV-007", "Nguyễn Minh An", null, "DIRECTOR", null),
                    "Vị trí nhân viên không hợp lệ.", ready())),
            test("SFM-008",
                "Head Office Manager đã đăng nhập; các trường còn lại hợp lệ.",
                "Mã nhân viên lần lượt có đúng 60 và 61 ký tự.",
                "Mã 60 ký tự được chấp nhận; mã 61 ký tự hiển thị “mã nhân viên chỉ được chứa tối đa 60 ký tự.”",
                CreateStaffRecordManualCases::codeBoundary),
            test("SFM-009",
                "Branch Manager được phân công tại cơ sở Hoa Bằng đang SUSPENDED.",
                "Tạo hồ sơ hợp lệ.",
                "Hiển thị “Cơ sở GenZ Cinema Hoa Bằng đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi.”",
                CreateStaffRecordManualCases::suspendedBranch),
            test("SFM-010",
                "Accountant ketoan đã đăng nhập.",
                "Tạo hồ sơ nhân viên hợp lệ.",
                "Hiển thị “Bạn không có quyền thực hiện thao tác đối với hồ sơ nhân viên này.”; không tạo hồ sơ.",
                CreateStaffRecordManualCases::unauthorized)
        );
    }

    private static Observation validCreate(boolean branchManager) {
        Scenario scenario = ready();
        Identity actor = branchManager ? branchManager() : headOffice();
        StaffCommand input = command(branchManager ? "SFM-NV-002" : "SFM-NV-001", "Nguyễn Minh An", "0901234567", "OTHER", null);
        Map<String, Object> result = scenario.service.create(actor, input);
        boolean assigned = !branchManager || HOA_BANG.equals(result.get("branchId"));
        boolean passed = "ACTIVE".equals(result.get("status"))
            && input.code().equals(result.get("code"))
            && assigned
            && scenario.createCount == 1
            && scenario.auditCount == 1
            && scenario.assignmentCount == (branchManager ? 1 : 0);
        return new Observation("code=" + result.get("code") + ", status=" + result.get("status")
            + ", branchId=" + result.get("branchId") + ", createWrites=" + scenario.createCount
            + ", assignments=" + scenario.assignmentCount + ", audits=" + scenario.auditCount, passed);
    }

    private static Observation requiredFields() {
        List<String> actual = new ArrayList<>();
        actual.add(message(command("   ", "Nguyễn Minh An", null, "OTHER", null), ready()));
        actual.add(message(command("SFM-NV-003", "   ", null, "OTHER", null), ready()));
        actual.add(message(command("SFM-NV-003", "Nguyễn Minh An", null, "   ", null), ready()));
        actual.add(message(new StaffCommand("SFM-NV-003", "Nguyễn Minh An", null, "OTHER", null, null, null, "", null, null, null), ready()));
        List<String> expected = List.of(
            "Vui lòng bổ sung mã nhân viên.",
            "Vui lòng bổ sung họ tên.",
            "Vui lòng bổ sung vị trí.",
            "Vui lòng bổ sung ngày vào làm."
        );
        return new Observation("messages=" + actual, expected.equals(actual));
    }

    private static Observation duplicateFields() {
        List<String> actual = new ArrayList<>();

        Scenario code = ready();
        when(code.staff.duplicate(eq("code"), eq("DUP-CODE"), nullable(UUID.class))).thenReturn(true);
        actual.add(message(command("DUP-CODE", "Nguyễn Minh An", null, "OTHER", null), code));

        Scenario identity = ready();
        when(identity.staff.duplicate(eq("identity_number"), eq("012345678901"), nullable(UUID.class))).thenReturn(true);
        actual.add(message(new StaffCommand("SFM-NV-006", "Nguyễn Minh An", null, "OTHER", null,
            "012345678901", null, "", HIRED_ON, null, null), identity));

        Scenario email = ready();
        when(email.accounts.emailReserved(eq("used@example.com"), nullable(UUID.class), nullable(UUID.class))).thenReturn(true);
        actual.add(message(command("SFM-NV-006", "Nguyễn Minh An", null, "OTHER", "used@example.com"), email));

        Scenario phone = ready();
        when(phone.accounts.staffPhoneTaken(eq("0901234567"), nullable(UUID.class), nullable(UUID.class))).thenReturn(true);
        actual.add(message(command("SFM-NV-006", "Nguyễn Minh An", "0901234567", "OTHER", null), phone));

        List<String> expected = List.of(
            "Mã nhân viên này đã được sử dụng. Vui lòng nhập mã khác.",
            "Số định danh này đã được sử dụng cho hồ sơ khác.",
            "Email này đã được sử dụng cho hồ sơ nhân viên hoặc tài khoản khác. Vui lòng nhập email khác.",
            "Số điện thoại này đã được sử dụng cho hồ sơ nhân viên khác. Vui lòng nhập số khác."
        );
        return new Observation("messages=" + actual, expected.equals(actual));
    }

    private static Observation codeBoundary() {
        Scenario accepted = ready();
        String max = "A".repeat(60);
        Map<String, Object> result = accepted.service.create(headOffice(), command(max, "Nguyễn Minh An", null, "OTHER", null));
        Scenario rejected = ready();
        String actual = message(command("A".repeat(61), "Nguyễn Minh An", null, "OTHER", null), rejected);
        boolean passed = max.equals(result.get("code"))
            && "mã nhân viên chỉ được chứa tối đa 60 ký tự.".equals(actual)
            && rejected.createCount == 0;
        return new Observation("acceptedLength=" + ((String) result.get("code")).length() + ", rejectedMessage=" + actual, passed);
    }

    private static Observation suspendedBranch() {
        Scenario scenario = ready();
        when(scenario.accounts.manages(BM_ID, HOA_BANG, false)).thenReturn(true);
        when(scenario.accounts.manages(BM_ID, HOA_BANG, true)).thenReturn(false);
        String expected = "Cơ sở GenZ Cinema Hoa Bằng đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi.";
        return rejected(command("SFM-NV-009", "Nguyễn Minh An", null, "OTHER", null), expected, scenario, branchManager());
    }

    private static Observation unauthorized() {
        Scenario scenario = ready();
        String expected = "Bạn không có quyền thực hiện thao tác đối với hồ sơ nhân viên này.";
        Identity accountant = new Identity(ACCOUNTANT_ID, "ketoan", "ACCOUNTANT", "/accountant", null, null);
        return rejected(command("SFM-NV-010", "Nguyễn Minh An", null, "OTHER", null), expected, scenario, accountant);
    }

    private static Observation rejected(StaffCommand input, String expected, Scenario scenario) {
        return rejected(input, expected, scenario, headOffice());
    }

    private static Observation rejected(StaffCommand input, String expected, Scenario scenario, Identity actor) {
        String actual = message(input, scenario, actor);
        return new Observation("message=" + actual + ", createWrites=" + scenario.createCount,
            expected.equals(actual) && scenario.createCount == 0 && scenario.auditCount == 0);
    }

    private static String message(StaffCommand input, Scenario scenario) {
        return message(input, scenario, headOffice());
    }

    private static String message(StaffCommand input, Scenario scenario, Identity actor) {
        try {
            scenario.service.create(actor, input);
            return "Không phát sinh lỗi";
        } catch (AuthFailure failure) {
            return failure.getMessage();
        }
    }

    private static Scenario ready() {
        StaffRepository staff = mock(StaffRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        Scenario scenario = new Scenario(staff, accounts, new StaffService(staff, accounts));
        when(staff.actorActive(any(), anyString())).thenReturn(true);
        when(staff.history(any())).thenReturn(List.of());
        when(accounts.manages(any(), any(), anyBoolean())).thenReturn(true);
        when(staff.duplicate(anyString(), anyString(), nullable(UUID.class))).thenReturn(false);
        when(accounts.emailReserved(anyString(), nullable(UUID.class), nullable(UUID.class))).thenReturn(false);
        when(accounts.staffPhoneTaken(anyString(), nullable(UUID.class), nullable(UUID.class))).thenReturn(false);
        doAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            StaffCommand input = invocation.getArgument(1);
            scenario.current = row(id, input.code(), input.fullName(), input.phone(), input.position(), input.contactEmail(), null, "ACTIVE", 0L);
            scenario.createCount++;
            return null;
        }).when(staff).create(any(), any());
        doAnswer(invocation -> {
            scenario.current.put("branchId", invocation.getArgument(1));
            scenario.assignmentCount++;
            return null;
        }).when(staff).assignment(any(), any(), any(), any());
        when(staff.find(any())).thenAnswer(invocation -> scenario.current);
        doAnswer(invocation -> {
            scenario.auditCount++;
            return null;
        }).when(staff).audit(any(), any(), anyString(), nullable(Map.class), anyMap(), nullable(String.class));
        return scenario;
    }

    private static StaffCommand command(String code, String name, String phone, String position, String email) {
        return new StaffCommand(code, name, phone, position, email, null, null, "", HIRED_ON, null, null);
    }

    private static Map<String, Object> row(UUID id, String code, String name, String phone, String position,
                                           String email, UUID branch, String status, long version) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("code", code);
        row.put("fullName", name);
        row.put("phone", phone);
        row.put("position", position);
        row.put("contactEmail", email);
        row.put("hiredOn", HIRED_ON.toString());
        row.put("resignedOn", null);
        row.put("status", status);
        row.put("version", version);
        row.put("userId", null);
        row.put("accountRole", null);
        row.put("branchId", branch);
        return row;
    }

    private static Identity headOffice() {
        return new Identity(HOM_ID, "qlcc", "HEAD_OFFICE_MANAGER", "/head-office", null, null);
    }

    private static Identity branchManager() {
        return new Identity(BM_ID, "qlcs.hoabang", "BRANCH_MANAGER", "/branch-management", HOA_BANG, "GenZ Cinema Hoa Bằng");
    }

    private static ManualCase test(String id, String precondition, String input, String expected,
                                   StaffManagementManualRunner.Execution execution) {
        return new ManualCase(id, TITLE, SOURCE, precondition, input, expected, execution);
    }

    private static final class Scenario {
        private final StaffRepository staff;
        private final AccountRepository accounts;
        private final StaffService service;
        private Map<String, Object> current;
        private int createCount;
        private int assignmentCount;
        private int auditCount;

        private Scenario(StaffRepository staff, AccountRepository accounts, StaffService service) {
            this.staff = staff;
            this.accounts = accounts;
            this.service = service;
        }
    }
}
