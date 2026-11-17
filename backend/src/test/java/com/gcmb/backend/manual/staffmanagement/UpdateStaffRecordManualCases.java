/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 12/11/2026
 * Description: Manual unit test cases cho UC-79 Update Staff Record.
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

final class UpdateStaffRecordManualCases {
    private static final UUID HOM_ID = UUID.fromString("47570000-0000-4000-8000-000000000005");
    private static final UUID BM_ID = UUID.fromString("47570000-0000-4000-8000-000000000003");
    private static final UUID EMPLOYEE_ID = UUID.fromString("47570000-0000-4000-8000-000000000011");
    private static final UUID HOA_BANG = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID NLB = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final LocalDate HIRED_ON = LocalDate.of(2026, 10, 1);
    private static final String TITLE = "UC-79 Update Staff Record";
    private static final String SOURCE = "UC-79; BR-52, BR-111, BR-119, BR-177, BR-199; MSG02, MSG03, MSG04, MSG24, MSG43, MSG88; V14, V26; StaffService.update";

    private UpdateStaffRecordManualCases() {
    }

    static List<ManualCase> cases() {
        return List.of(
            test("SFM-022",
                "Head Office Manager đã đăng nhập; hồ sơ GENZ-REC-HOA-BANG tồn tại và phiên bản hiện tại là 0.",
                "Đổi họ tên thành Lễ tân Hoa Bằng A, số điện thoại 0901111111, địa chỉ và ghi chú hợp lệ.",
                "Hồ sơ chứa thông tin đã xác nhận; lịch sử cơ sở được giữ và thao tác được ghi nhận.",
                () -> validUpdate(false)),
            test("SFM-023",
                "Branch Manager qlcs.hoabang đã đăng nhập; hồ sơ thuộc cơ sở Hoa Bằng.",
                "Cập nhật thông tin hồ sơ bằng phiên bản hiện tại.",
                "Hồ sơ được cập nhật trong đúng phạm vi Hoa Bằng.",
                () -> validUpdate(true)),
            test("SFM-024",
                "Head Office Manager đã đăng nhập; hồ sơ tồn tại.",
                "Lần lượt để trống mã, họ tên, vị trí hoặc ngày vào làm.",
                "Hiển thị đúng thông báo yêu cầu bổ sung từng trường; hồ sơ không thay đổi.",
                UpdateStaffRecordManualCases::requiredFields),
            test("SFM-025",
                "Head Office Manager đã đăng nhập; hồ sơ tồn tại.",
                "Đổi số điện thoại thành 09A1234567.",
                "Hiển thị “Số điện thoại chỉ được chứa chữ số và có thể có một dấu + ở đầu.”; hồ sơ không thay đổi.",
                () -> rejected(command("GENZ-REC-HOA-BANG", "Lễ tân Hoa Bằng", "09A1234567", "RECEPTIONIST", null, null, "", 0L),
                    "Số điện thoại chỉ được chứa chữ số và có thể có một dấu + ở đầu.", ready(HOA_BANG, 0L))),
            test("SFM-026",
                "Head Office Manager đã đăng nhập; ngày vào làm là 01/10/2026.",
                "Nhập ngày nghỉ việc 30/09/2026.",
                "Hiển thị “Ngày nghỉ việc phải bằng hoặc sau ngày vào làm.”; hồ sơ không thay đổi.",
                () -> rejected(command("GENZ-REC-HOA-BANG", "Lễ tân Hoa Bằng", null, "RECEPTIONIST", null,
                    LocalDate.of(2026, 9, 30), "", 0L), "Ngày nghỉ việc phải bằng hoặc sau ngày vào làm.", ready(HOA_BANG, 0L))),
            test("SFM-027",
                "Hồ sơ đã được cập nhật lên phiên bản 2 sau khi người dùng mở biểu mẫu.",
                "Gửi thay đổi bằng phiên bản 1.",
                "Hiển thị “Hồ sơ đã thay đổi. Vui lòng tải lại và kiểm tra thông tin trước khi xác nhận.”",
                () -> rejected(command("GENZ-REC-HOA-BANG", "Lễ tân Hoa Bằng", null, "RECEPTIONIST", null, null, "", 1L),
                    "Hồ sơ đã thay đổi. Vui lòng tải lại và kiểm tra thông tin trước khi xác nhận.", ready(HOA_BANG, 2L))),
            test("SFM-028",
                "Head Office Manager đã đăng nhập; mã, số định danh, email và số điện thoại tương ứng thuộc hồ sơ khác.",
                "Lần lượt cập nhật sang dữ liệu trùng.",
                "Hiển thị đúng bốn thông báo trùng dữ liệu; hồ sơ không thay đổi.",
                UpdateStaffRecordManualCases::duplicateFields),
            test("SFM-029",
                "Head Office Manager đã đăng nhập; mã hồ sơ được yêu cầu không tồn tại.",
                "Cập nhật hồ sơ không tồn tại.",
                "Hiển thị “Không tìm thấy hồ sơ nhân viên.”",
                UpdateStaffRecordManualCases::missingRecord),
            test("SFM-030",
                "qlcs.hoabang đã đăng nhập; hồ sơ thuộc Nguyễn Lương Bằng.",
                "Cập nhật hồ sơ ngoài phạm vi.",
                "Hiển thị “Bạn không có quyền thực hiện thao tác đối với hồ sơ nhân viên này.”",
                UpdateStaffRecordManualCases::crossBranch),
            test("SFM-031",
                "Branch Manager được phân công tại cơ sở Hoa Bằng đang SUSPENDED; hồ sơ thuộc cơ sở đó.",
                "Cập nhật thông tin hợp lệ.",
                "Hiển thị “Cơ sở GenZ Cinema Hoa Bằng đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi.”",
                UpdateStaffRecordManualCases::suspendedBranch),
            test("SFM-032",
                "Head Office Manager đã đăng nhập; hồ sơ tồn tại.",
                "Ghi chú lần lượt có đúng 4000 và 4001 ký tự.",
                "4000 ký tự được chấp nhận; 4001 ký tự hiển thị “Ghi chú chỉ được chứa tối đa 4000 ký tự.”",
                UpdateStaffRecordManualCases::notesBoundary)
        );
    }

    private static Observation validUpdate(boolean branchScoped) {
        Scenario scenario = ready(HOA_BANG, 0L);
        StaffCommand input = command("GENZ-REC-HOA-BANG", "Lễ tân Hoa Bằng A", "0901111111", "RECEPTIONIST",
            null, null, "Đã cập nhật", 0L);
        Map<String, Object> result = scenario.service.update(branchScoped ? branchManager() : headOffice(), EMPLOYEE_ID, input);
        boolean passed = "Lễ tân Hoa Bằng A".equals(result.get("fullName"))
            && ((List<?>) result.get("history")).size() == 1
            && scenario.updateCount == 1
            && scenario.auditCount == 1;
        return new Observation("fullName=" + result.get("fullName") + ", version=" + result.get("version")
            + ", history=" + ((List<?>) result.get("history")).size() + ", updates=" + scenario.updateCount
            + ", audits=" + scenario.auditCount, passed);
    }

    private static Observation requiredFields() {
        List<String> actual = new ArrayList<>();
        actual.add(message(command("   ", "Lễ tân Hoa Bằng", null, "RECEPTIONIST", null, null, "", 0L), ready(HOA_BANG, 0L), headOffice()));
        actual.add(message(command("GENZ-REC-HOA-BANG", "   ", null, "RECEPTIONIST", null, null, "", 0L), ready(HOA_BANG, 0L), headOffice()));
        actual.add(message(command("GENZ-REC-HOA-BANG", "Lễ tân Hoa Bằng", null, "   ", null, null, "", 0L), ready(HOA_BANG, 0L), headOffice()));
        Scenario noDate = ready(HOA_BANG, 0L);
        StaffCommand input = new StaffCommand("GENZ-REC-HOA-BANG", "Lễ tân Hoa Bằng", null, "RECEPTIONIST",
            null, null, null, "", null, null, 0L);
        actual.add(message(input, noDate, headOffice()));
        List<String> expected = List.of("Vui lòng bổ sung mã nhân viên.", "Vui lòng bổ sung họ tên.",
            "Vui lòng bổ sung vị trí.", "Vui lòng bổ sung ngày vào làm.");
        return new Observation("messages=" + actual, expected.equals(actual));
    }

    private static Observation duplicateFields() {
        List<String> actual = new ArrayList<>();
        Scenario code = ready(HOA_BANG, 0L);
        when(code.staff.duplicate(eq("code"), eq("DUP-CODE"), eq(EMPLOYEE_ID))).thenReturn(true);
        actual.add(message(command("DUP-CODE", "Lễ tân Hoa Bằng", null, "RECEPTIONIST", null, null, "", 0L), code, headOffice()));

        Scenario identity = ready(HOA_BANG, 0L);
        when(identity.staff.duplicate(eq("identity_number"), eq("012345678901"), eq(EMPLOYEE_ID))).thenReturn(true);
        StaffCommand identityCommand = new StaffCommand("GENZ-REC-HOA-BANG", "Lễ tân Hoa Bằng", null, "RECEPTIONIST",
            null, "012345678901", null, "", HIRED_ON, null, 0L);
        actual.add(message(identityCommand, identity, headOffice()));

        Scenario email = ready(HOA_BANG, 0L);
        when(email.accounts.emailReserved(eq("used@example.com"), eq(EMPLOYEE_ID), nullable(UUID.class))).thenReturn(true);
        actual.add(message(command("GENZ-REC-HOA-BANG", "Lễ tân Hoa Bằng", null, "RECEPTIONIST", "used@example.com", null, "", 0L), email, headOffice()));

        Scenario phone = ready(HOA_BANG, 0L);
        when(phone.accounts.staffPhoneTaken(eq("0901234567"), eq(EMPLOYEE_ID), nullable(UUID.class))).thenReturn(true);
        actual.add(message(command("GENZ-REC-HOA-BANG", "Lễ tân Hoa Bằng", "0901234567", "RECEPTIONIST", null, null, "", 0L), phone, headOffice()));

        List<String> expected = List.of(
            "Mã nhân viên này đã được sử dụng. Vui lòng nhập mã khác.",
            "Số định danh này đã được sử dụng cho hồ sơ khác.",
            "Email này đã được sử dụng cho hồ sơ nhân viên hoặc tài khoản khác. Vui lòng nhập email khác.",
            "Số điện thoại này đã được sử dụng cho hồ sơ nhân viên khác. Vui lòng nhập số khác."
        );
        return new Observation("messages=" + actual, expected.equals(actual));
    }

    private static Observation missingRecord() {
        Scenario scenario = ready(HOA_BANG, 0L);
        when(scenario.staff.lock(EMPLOYEE_ID)).thenReturn(false);
        String actual = message(command("GENZ-REC-HOA-BANG", "Lễ tân Hoa Bằng", null, "RECEPTIONIST", null, null, "", 0L), scenario, headOffice());
        return new Observation("message=" + actual, "Không tìm thấy hồ sơ nhân viên.".equals(actual) && scenario.updateCount == 0);
    }

    private static Observation crossBranch() {
        Scenario scenario = ready(NLB, 0L);
        String actual = message(command("GENZ-REC-NLB-86", "Lễ tân NLB", null, "RECEPTIONIST", null, null, "", 0L), scenario, branchManager());
        String expected = "Bạn không có quyền thực hiện thao tác đối với hồ sơ nhân viên này.";
        return new Observation("message=" + actual + ", updates=" + scenario.updateCount, expected.equals(actual) && scenario.updateCount == 0);
    }

    private static Observation suspendedBranch() {
        Scenario scenario = ready(HOA_BANG, 0L);
        when(scenario.accounts.manages(BM_ID, HOA_BANG, false)).thenReturn(true);
        when(scenario.accounts.manages(BM_ID, HOA_BANG, true)).thenReturn(false);
        String actual = message(command("GENZ-REC-HOA-BANG", "Lễ tân Hoa Bằng", null, "RECEPTIONIST", null, null, "", 0L), scenario, branchManager());
        String expected = "Cơ sở GenZ Cinema Hoa Bằng đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi.";
        return new Observation("message=" + actual + ", updates=" + scenario.updateCount, expected.equals(actual) && scenario.updateCount == 0);
    }

    private static Observation notesBoundary() {
        Scenario accepted = ready(HOA_BANG, 0L);
        accepted.service.update(headOffice(), EMPLOYEE_ID,
            command("GENZ-REC-HOA-BANG", "Lễ tân Hoa Bằng", null, "RECEPTIONIST", null, null, "A".repeat(4000), 0L));
        Scenario rejected = ready(HOA_BANG, 0L);
        String actual = message(command("GENZ-REC-HOA-BANG", "Lễ tân Hoa Bằng", null, "RECEPTIONIST", null, null,
            "A".repeat(4001), 0L), rejected, headOffice());
        boolean passed = accepted.updateCount == 1
            && "Ghi chú chỉ được chứa tối đa 4000 ký tự.".equals(actual)
            && rejected.updateCount == 0;
        return new Observation("acceptedLength=4000, rejectedMessage=" + actual, passed);
    }

    private static Observation rejected(StaffCommand input, String expected, Scenario scenario) {
        String actual = message(input, scenario, headOffice());
        return new Observation("message=" + actual + ", updates=" + scenario.updateCount,
            expected.equals(actual) && scenario.updateCount == 0 && scenario.auditCount == 0);
    }

    private static String message(StaffCommand input, Scenario scenario, Identity actor) {
        try {
            scenario.service.update(actor, EMPLOYEE_ID, input);
            return "Không phát sinh lỗi";
        } catch (AuthFailure failure) {
            return failure.getMessage();
        }
    }

    private static Scenario ready(UUID branch, long version) {
        StaffRepository staff = mock(StaffRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        Scenario scenario = new Scenario(staff, accounts, new StaffService(staff, accounts));
        scenario.current = row(branch, version);
        when(staff.actorActive(any(), anyString())).thenReturn(true);
        when(accounts.manages(any(), any(), anyBoolean())).thenReturn(true);
        when(staff.lock(EMPLOYEE_ID)).thenReturn(true);
        when(staff.find(EMPLOYEE_ID)).thenAnswer(invocation -> scenario.current);
        when(staff.history(EMPLOYEE_ID)).thenReturn(List.of(Map.of("branchCode", "HN_HOA_BANG")));
        when(staff.duplicate(anyString(), anyString(), eq(EMPLOYEE_ID))).thenReturn(false);
        when(accounts.emailReserved(anyString(), eq(EMPLOYEE_ID), nullable(UUID.class))).thenReturn(false);
        when(accounts.staffPhoneTaken(anyString(), eq(EMPLOYEE_ID), nullable(UUID.class))).thenReturn(false);
        doAnswer(invocation -> {
            StaffCommand input = invocation.getArgument(1);
            scenario.current.put("code", input.code().trim());
            scenario.current.put("fullName", input.fullName().trim());
            scenario.current.put("phone", input.phone());
            scenario.current.put("position", input.position().trim());
            scenario.current.put("contactEmail", input.contactEmail());
            scenario.current.put("notes", input.notes());
            scenario.current.put("resignedOn", input.resignedOn() == null ? null : input.resignedOn().toString());
            scenario.current.put("version", ((Number) scenario.current.get("version")).longValue() + 1);
            scenario.updateCount++;
            return null;
        }).when(staff).update(eq(EMPLOYEE_ID), any());
        doAnswer(invocation -> {
            scenario.auditCount++;
            return null;
        }).when(staff).audit(any(), eq(EMPLOYEE_ID), eq("UPDATE_STAFF"), anyMap(), anyMap(), nullable(String.class));
        return scenario;
    }

    private static Map<String, Object> row(UUID branch, long version) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", EMPLOYEE_ID);
        row.put("code", "GENZ-REC-HOA-BANG");
        row.put("fullName", "Lễ tân - Hoa Bằng");
        row.put("phone", null);
        row.put("position", "RECEPTIONIST");
        row.put("contactEmail", null);
        row.put("notes", "");
        row.put("hiredOn", HIRED_ON.toString());
        row.put("resignedOn", null);
        row.put("status", "ACTIVE");
        row.put("version", version);
        row.put("userId", UUID.fromString("47570000-0000-4000-8000-000000000001"));
        row.put("accountRole", "RECEPTIONIST");
        row.put("branchId", branch);
        return row;
    }

    private static StaffCommand command(String code, String name, String phone, String position, String email,
                                        LocalDate resignedOn, String notes, Long version) {
        return new StaffCommand(code, name, phone, position, email, null, "24 Hoa Bằng", notes,
            HIRED_ON, resignedOn, version);
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
        private int updateCount;
        private int auditCount;

        private Scenario(StaffRepository staff, AccountRepository accounts, StaffService service) {
            this.staff = staff;
            this.accounts = accounts;
            this.service = service;
        }
    }
}
