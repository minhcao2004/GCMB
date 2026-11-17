/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 12/11/2026
 * Description: Manual unit test cases cho UC-80 Change Staff Record Status.
 */
package com.gcmb.backend.manual.staffmanagement;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gcmb.backend.accounts.repository.AccountRepository;
import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.exception.AuthFailure;
import com.gcmb.backend.manual.staffmanagement.StaffManagementManualRunner.ManualCase;
import com.gcmb.backend.manual.staffmanagement.StaffManagementManualRunner.Observation;
import com.gcmb.backend.workforce.dto.StaffDtos.StatusCommand;
import com.gcmb.backend.workforce.repository.StaffRepository;
import com.gcmb.backend.workforce.service.StaffService;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ChangeStaffRecordStatusManualCases {
    private static final UUID HOM_ID = UUID.fromString("47570000-0000-4000-8000-000000000005");
    private static final UUID BM_ID = UUID.fromString("47570000-0000-4000-8000-000000000003");
    private static final UUID EMPLOYEE_ID = UUID.fromString("47570000-0000-4000-8000-000000000011");
    private static final UUID RECEPTION_ACCOUNT = UUID.fromString("47570000-0000-4000-8000-000000000001");
    private static final UUID MANAGER_ACCOUNT = UUID.fromString("47570000-0000-4000-8000-000000000004");
    private static final UUID HOA_BANG = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final LocalDate HIRED_ON = LocalDate.of(2026, 10, 1);
    private static final String TITLE = "UC-80 Change Staff Record Status";
    private static final String SOURCE = "UC-80; BR-52, BR-171, BR-177, BR-199; MSG24, MSG43, MSG88; V14, V26; StaffService.status";

    private ChangeStaffRecordStatusManualCases() {
    }

    static List<ManualCase> cases() {
        return List.of(
            test("SFM-033",
                "Head Office Manager đã đăng nhập; nhân viên đang ACTIVE; phiên bản là 0.",
                "Chuyển hồ sơ sang không hoạt động, không nhập ngày nghỉ việc.",
                "Hồ sơ chuyển sang INACTIVE; lịch sử được giữ và thao tác được ghi nhận.",
                () -> changeStatus("ACTIVE", RECEPTION_ACCOUNT, "RECEPTIONIST", false, null, headOffice())),
            test("SFM-034",
                "Head Office Manager đã đăng nhập; ngày vào làm 01/10/2026.",
                "Chuyển sang không hoạt động với ngày nghỉ 01/11/2026.",
                "Hồ sơ chuyển sang INACTIVE và lưu đúng ngày nghỉ việc.",
                () -> changeStatus("ACTIVE", RECEPTION_ACCOUNT, "RECEPTIONIST", false, LocalDate.of(2026, 11, 1), headOffice())),
            test("SFM-035",
                "Head Office Manager đã đăng nhập; nhân viên đang INACTIVE và tài khoản trước đó đã bị thu hồi.",
                "Kích hoạt lại hồ sơ.",
                "Hồ sơ chuyển sang ACTIVE; StaffService không tự khôi phục quyền tài khoản.",
                ChangeStaffRecordStatusManualCases::reactivate),
            test("SFM-036",
                "Head Office Manager đã đăng nhập; ngày vào làm 01/10/2026.",
                "Ngừng hoạt động với ngày nghỉ 30/09/2026.",
                "Hiển thị “Ngày nghỉ việc phải bằng hoặc sau ngày vào làm.”; trạng thái không thay đổi.",
                ChangeStaffRecordStatusManualCases::invalidDate),
            test("SFM-037",
                "Hồ sơ đã được cập nhật lên phiên bản 2 sau khi người dùng mở biểu mẫu.",
                "Đổi trạng thái bằng phiên bản 1.",
                "Hiển thị “Hồ sơ đã thay đổi. Vui lòng tải lại và kiểm tra thông tin trước khi xác nhận.”",
                ChangeStaffRecordStatusManualCases::staleVersion),
            test("SFM-038",
                "Head Office Manager đăng nhập bằng tài khoản liên kết với chính hồ sơ đang thao tác.",
                "Ngừng hoạt động hồ sơ của chính mình.",
                "Hiển thị “Không thể ngừng hồ sơ đang liên kết với tài khoản bạn đang đăng nhập.”",
                ChangeStaffRecordStatusManualCases::selfDeactivate),
            test("SFM-039",
                "qlcs.hoabang đã đăng nhập; Receptionist thuộc Hoa Bằng và đang ACTIVE.",
                "Ngừng hoạt động hồ sơ Receptionist.",
                "Hồ sơ chuyển sang INACTIVE trong đúng phạm vi quản lý.",
                () -> changeStatus("ACTIVE", RECEPTION_ACCOUNT, "RECEPTIONIST", false, null, branchManager())),
            test("SFM-040",
                "Branch Manager đã đăng nhập; hồ sơ cùng cơ sở liên kết tài khoản không phải Receptionist.",
                "Ngừng hoạt động hồ sơ.",
                "Hiển thị “Bạn không có quyền thực hiện thao tác đối với hồ sơ nhân viên này.”",
                ChangeStaffRecordStatusManualCases::managerRestriction),
            test("SFM-041",
                "Branch Manager thuộc cơ sở Hoa Bằng đang SUSPENDED; hồ sơ thuộc cơ sở đó.",
                "Đổi trạng thái hồ sơ.",
                "Hiển thị “Cơ sở GenZ Cinema Hoa Bằng đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi.”",
                ChangeStaffRecordStatusManualCases::suspendedBranch),
            test("SFM-042",
                "Hồ sơ đang ACTIVE và không có ngày nghỉ việc.",
                "Xác nhận lại trạng thái ACTIVE và không nhập ngày nghỉ việc.",
                "Trả hồ sơ hiện tại; không cập nhật phiên bản và không tạo audit event trùng.",
                ChangeStaffRecordStatusManualCases::sameStatus)
        );
    }

    private static Observation changeStatus(String initialStatus, UUID userId, String accountRole, boolean active,
                                            LocalDate resignedOn, Identity actor) {
        Scenario scenario = ready(initialStatus, 0L, userId, accountRole);
        Map<String, Object> result = scenario.service.status(actor, EMPLOYEE_ID, new StatusCommand(active, "Xác nhận", resignedOn, 0L));
        String expectedStatus = active ? "ACTIVE" : "INACTIVE";
        String expectedDate = resignedOn == null ? null : resignedOn.toString();
        boolean passed = expectedStatus.equals(result.get("status"))
            && java.util.Objects.equals(expectedDate, result.get("resignedOn"))
            && scenario.statusCount == 1
            && scenario.auditCount == 1;
        return new Observation("status=" + result.get("status") + ", resignedOn=" + result.get("resignedOn")
            + ", statusWrites=" + scenario.statusCount + ", audits=" + scenario.auditCount, passed);
    }

    private static Observation reactivate() {
        Scenario scenario = ready("INACTIVE", 0L, RECEPTION_ACCOUNT, "RECEPTIONIST");
        Map<String, Object> result = scenario.service.status(headOffice(), EMPLOYEE_ID,
            new StatusCommand(true, "Quay lại làm việc", null, 0L));
        verifyNoInteractions(scenario.accounts);
        boolean passed = "ACTIVE".equals(result.get("status")) && scenario.statusCount == 1 && scenario.auditCount == 1;
        return new Observation("status=" + result.get("status") + ", accountRestoreCalls=0", passed);
    }

    private static Observation invalidDate() {
        Scenario scenario = ready("ACTIVE", 0L, RECEPTION_ACCOUNT, "RECEPTIONIST");
        String actual = failure(scenario, headOffice(), new StatusCommand(false, null, LocalDate.of(2026, 9, 30), 0L));
        return new Observation("message=" + actual + ", statusWrites=" + scenario.statusCount,
            "Ngày nghỉ việc phải bằng hoặc sau ngày vào làm.".equals(actual) && scenario.statusCount == 0);
    }

    private static Observation staleVersion() {
        Scenario scenario = ready("ACTIVE", 2L, RECEPTION_ACCOUNT, "RECEPTIONIST");
        String actual = failure(scenario, headOffice(), new StatusCommand(false, null, null, 1L));
        String expected = "Hồ sơ đã thay đổi. Vui lòng tải lại và kiểm tra thông tin trước khi xác nhận.";
        return new Observation("message=" + actual + ", statusWrites=" + scenario.statusCount,
            expected.equals(actual) && scenario.statusCount == 0);
    }

    private static Observation selfDeactivate() {
        Scenario scenario = ready("ACTIVE", 0L, HOM_ID, "HEAD_OFFICE_MANAGER");
        String actual = failure(scenario, headOffice(), new StatusCommand(false, null, null, 0L));
        String expected = "Không thể ngừng hồ sơ đang liên kết với tài khoản bạn đang đăng nhập.";
        return new Observation("message=" + actual + ", statusWrites=" + scenario.statusCount,
            expected.equals(actual) && scenario.statusCount == 0);
    }

    private static Observation managerRestriction() {
        Scenario scenario = ready("ACTIVE", 0L, MANAGER_ACCOUNT, "BRANCH_MANAGER");
        String actual = failure(scenario, branchManager(), new StatusCommand(false, null, null, 0L));
        String expected = "Bạn không có quyền thực hiện thao tác đối với hồ sơ nhân viên này.";
        return new Observation("message=" + actual + ", statusWrites=" + scenario.statusCount,
            expected.equals(actual) && scenario.statusCount == 0);
    }

    private static Observation suspendedBranch() {
        Scenario scenario = ready("ACTIVE", 0L, RECEPTION_ACCOUNT, "RECEPTIONIST");
        when(scenario.accounts.manages(BM_ID, HOA_BANG, false)).thenReturn(true);
        when(scenario.accounts.manages(BM_ID, HOA_BANG, true)).thenReturn(false);
        String actual = failure(scenario, branchManager(), new StatusCommand(false, null, null, 0L));
        String expected = "Cơ sở GenZ Cinema Hoa Bằng đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi.";
        return new Observation("message=" + actual + ", statusWrites=" + scenario.statusCount,
            expected.equals(actual) && scenario.statusCount == 0);
    }

    private static Observation sameStatus() {
        Scenario scenario = ready("ACTIVE", 0L, RECEPTION_ACCOUNT, "RECEPTIONIST");
        Map<String, Object> result = scenario.service.status(headOffice(), EMPLOYEE_ID,
            new StatusCommand(true, null, null, 0L));
        boolean passed = "ACTIVE".equals(result.get("status")) && scenario.statusCount == 0 && scenario.auditCount == 0;
        return new Observation("status=" + result.get("status") + ", statusWrites=" + scenario.statusCount
            + ", audits=" + scenario.auditCount, passed);
    }

    private static String failure(Scenario scenario, Identity actor, StatusCommand command) {
        try {
            scenario.service.status(actor, EMPLOYEE_ID, command);
            return "Không phát sinh lỗi";
        } catch (AuthFailure failure) {
            return failure.getMessage();
        }
    }

    private static Scenario ready(String status, long version, UUID userId, String accountRole) {
        StaffRepository staff = mock(StaffRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        Scenario scenario = new Scenario(staff, accounts, new StaffService(staff, accounts));
        scenario.current = row(status, version, userId, accountRole);
        when(staff.actorActive(any(), anyString())).thenReturn(true);
        when(accounts.manages(any(), any(), anyBoolean())).thenReturn(true);
        when(staff.lock(EMPLOYEE_ID)).thenReturn(true);
        when(staff.find(EMPLOYEE_ID)).thenAnswer(invocation -> scenario.current);
        when(staff.history(EMPLOYEE_ID)).thenReturn(List.of(Map.of("branchCode", "HN_HOA_BANG")));
        doAnswer(invocation -> {
            scenario.current.put("status", invocation.getArgument(1));
            LocalDate resigned = invocation.getArgument(2);
            scenario.current.put("resignedOn", resigned == null ? null : resigned.toString());
            scenario.current.put("version", ((Number) scenario.current.get("version")).longValue() + 1);
            scenario.statusCount++;
            return null;
        }).when(staff).status(eq(EMPLOYEE_ID), anyString(), nullable(LocalDate.class));
        doAnswer(invocation -> {
            scenario.auditCount++;
            return null;
        }).when(staff).audit(any(), eq(EMPLOYEE_ID), anyString(), anyMap(), anyMap(), nullable(String.class));
        return scenario;
    }

    private static Map<String, Object> row(String status, long version, UUID userId, String accountRole) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", EMPLOYEE_ID);
        row.put("code", "GENZ-REC-HOA-BANG");
        row.put("fullName", "Lễ tân - Hoa Bằng");
        row.put("hiredOn", HIRED_ON.toString());
        row.put("resignedOn", null);
        row.put("status", status);
        row.put("version", version);
        row.put("userId", userId);
        row.put("accountRole", accountRole);
        row.put("branchId", HOA_BANG);
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
        private int statusCount;
        private int auditCount;

        private Scenario(StaffRepository staff, AccountRepository accounts, StaffService service) {
            this.staff = staff;
            this.accounts = accounts;
            this.service = service;
        }
    }
}
