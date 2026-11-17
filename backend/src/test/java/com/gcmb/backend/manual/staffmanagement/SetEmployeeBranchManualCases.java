/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 12/11/2026
 * Description: Manual unit test cases tạm thời cho UC-81 Set Employee Branch khi đặc tả UC-81 đang thiếu.
 */
package com.gcmb.backend.manual.staffmanagement;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gcmb.backend.accounts.repository.AccountRepository;
import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.exception.AuthFailure;
import com.gcmb.backend.manual.staffmanagement.StaffManagementManualRunner.ManualCase;
import com.gcmb.backend.manual.staffmanagement.StaffManagementManualRunner.Observation;
import com.gcmb.backend.workforce.dto.StaffDtos.AssignmentCommand;
import com.gcmb.backend.workforce.repository.StaffRepository;
import com.gcmb.backend.workforce.service.StaffService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class SetEmployeeBranchManualCases {
    private static final UUID HOM_ID = UUID.fromString("47570000-0000-4000-8000-000000000005");
    private static final UUID BM_ID = UUID.fromString("47570000-0000-4000-8000-000000000003");
    private static final UUID EMPLOYEE_ID = UUID.fromString("47570000-0000-4000-8000-000000000015");
    private static final UUID HOA_BANG = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID NLB = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final String TITLE = "UC-81 Set Employee Branch";
    private static final String SOURCE = "UC-81 mapping only (specification missing); MSG24, MSG40; V14, V26; StaffService.assign";

    private SetEmployeeBranchManualCases() {
    }

    static List<ManualCase> cases() {
        return List.of(
            test("SFM-043",
                "Head Office Manager qlcc đã đăng nhập; GENZ-HOM đang ACTIVE, chưa có cơ sở; Hoa Bằng đang hoạt động.",
                "Gán GENZ-HOM vào cơ sở Hoa Bằng với phiên bản 0.",
                "Hoa Bằng trở thành cơ sở hiện tại; lịch sử phân công và audit được tạo.",
                SetEmployeeBranchManualCases::validAssignment),
            test("SFM-044",
                "Branch Manager qlcs.hoabang đã đăng nhập; nhân viên chưa có cơ sở.",
                "Gán cơ sở cho nhân viên.",
                "Hiển thị “Bạn không có quyền thực hiện thao tác đối với hồ sơ nhân viên này.”",
                SetEmployeeBranchManualCases::branchManagerDenied),
            test("SFM-045",
                "Head Office Manager đã đăng nhập; nhân viên ACTIVE, chưa có cơ sở.",
                "Không chọn cơ sở làm việc.",
                "Hiển thị “Vui lòng bổ sung cơ sở làm việc.”",
                SetEmployeeBranchManualCases::missingBranch),
            test("SFM-046",
                "Head Office Manager đã đăng nhập; nhân viên đang INACTIVE.",
                "Gán nhân viên vào Hoa Bằng.",
                "Hiển thị “Chỉ phân công cơ sở cho nhân viên đang làm việc.”",
                SetEmployeeBranchManualCases::inactiveEmployee),
            test("SFM-047",
                "Head Office Manager đã đăng nhập; nhân viên đã có cơ sở Hoa Bằng.",
                "Dùng chức năng gán cơ sở để chọn Nguyễn Lương Bằng.",
                "Hiển thị “Nhân viên đã có cơ sở. Vui lòng dùng chức năng điều chuyển.”",
                SetEmployeeBranchManualCases::alreadyAssigned),
            test("SFM-048",
                "Head Office Manager đã đăng nhập; nhân viên chưa có cơ sở; cơ sở đích đang SUSPENDED.",
                "Gán nhân viên vào cơ sở Tạm ngừng.",
                "Hiển thị “Cơ sở Tạm ngừng hiện không hoạt động. Vui lòng chọn cơ sở đang hoạt động để phân công.”",
                SetEmployeeBranchManualCases::inactiveDestination),
            test("SFM-049",
                "Head Office Manager đã đăng nhập; nhân viên có lịch phân công trong tương lai chưa kết thúc.",
                "Gán cơ sở Hoa Bằng.",
                "Hiển thị “Nhân viên có lịch phân công chưa kết thúc. Vui lòng kiểm tra lịch sử cơ sở.”",
                SetEmployeeBranchManualCases::futureAssignment)
        );
    }

    private static Observation validAssignment() {
        Scenario scenario = ready("ACTIVE", null);
        Map<String, Object> result = scenario.service.assign(headOffice(), EMPLOYEE_ID,
            new AssignmentCommand(HOA_BANG, "Phân công cơ sở", 0L), false);
        boolean passed = HOA_BANG.equals(result.get("branchId"))
            && ((List<?>) result.get("history")).size() == 1
            && scenario.assignmentCount == 1
            && scenario.auditCount == 1;
        return new Observation("branchId=" + result.get("branchId") + ", history=" + ((List<?>) result.get("history")).size()
            + ", assignments=" + scenario.assignmentCount + ", audits=" + scenario.auditCount, passed);
    }

    private static Observation branchManagerDenied() {
        Scenario scenario = ready("ACTIVE", null);
        return rejected(scenario, branchManager(), new AssignmentCommand(HOA_BANG, null, 0L),
            "Bạn không có quyền thực hiện thao tác đối với hồ sơ nhân viên này.");
    }

    private static Observation missingBranch() {
        Scenario scenario = ready("ACTIVE", null);
        return rejected(scenario, headOffice(), new AssignmentCommand(null, null, 0L), "Vui lòng bổ sung cơ sở làm việc.");
    }

    private static Observation inactiveEmployee() {
        Scenario scenario = ready("INACTIVE", null);
        return rejected(scenario, headOffice(), new AssignmentCommand(HOA_BANG, null, 0L),
            "Chỉ phân công cơ sở cho nhân viên đang làm việc.");
    }

    private static Observation alreadyAssigned() {
        Scenario scenario = ready("ACTIVE", HOA_BANG);
        return rejected(scenario, headOffice(), new AssignmentCommand(NLB, null, 0L),
            "Nhân viên đã có cơ sở. Vui lòng dùng chức năng điều chuyển.");
    }

    private static Observation inactiveDestination() {
        Scenario scenario = ready("ACTIVE", null);
        when(scenario.staff.activeBranch(NLB)).thenReturn(false);
        when(scenario.staff.branchName(NLB)).thenReturn("Tạm ngừng");
        return rejected(scenario, headOffice(), new AssignmentCommand(NLB, null, 0L),
            "Cơ sở Tạm ngừng hiện không hoạt động. Vui lòng chọn cơ sở đang hoạt động để phân công.");
    }

    private static Observation futureAssignment() {
        Scenario scenario = ready("ACTIVE", null);
        when(scenario.staff.futureAssignment(EMPLOYEE_ID)).thenReturn(true);
        return rejected(scenario, headOffice(), new AssignmentCommand(HOA_BANG, null, 0L),
            "Nhân viên có lịch phân công chưa kết thúc. Vui lòng kiểm tra lịch sử cơ sở.");
    }

    private static Observation rejected(Scenario scenario, Identity actor, AssignmentCommand command, String expected) {
        String actual;
        try {
            scenario.service.assign(actor, EMPLOYEE_ID, command, false);
            actual = "Không phát sinh lỗi";
        } catch (AuthFailure failure) {
            actual = failure.getMessage();
        }
        return new Observation("message=" + actual + ", assignments=" + scenario.assignmentCount,
            expected.equals(actual) && scenario.assignmentCount == 0 && scenario.auditCount == 0);
    }

    private static Scenario ready(String status, UUID branch) {
        StaffRepository staff = mock(StaffRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        Scenario scenario = new Scenario(staff, accounts, new StaffService(staff, accounts));
        scenario.current = row(status, branch);
        when(staff.actorActive(any(), anyString())).thenReturn(true);
        when(accounts.manages(any(), any(), anyBoolean())).thenReturn(true);
        when(staff.lock(EMPLOYEE_ID)).thenReturn(true);
        when(staff.find(EMPLOYEE_ID)).thenAnswer(invocation -> scenario.current);
        when(staff.activeBranch(HOA_BANG)).thenReturn(true);
        when(staff.activeBranch(NLB)).thenReturn(true);
        when(staff.futureAssignment(EMPLOYEE_ID)).thenReturn(false);
        when(staff.history(EMPLOYEE_ID)).thenAnswer(invocation -> scenario.assignmentCount == 0
            ? List.of() : List.of(Map.of("branchCode", "HN_HOA_BANG")));
        doAnswer(invocation -> {
            scenario.current.put("branchId", invocation.getArgument(1));
            scenario.current.put("version", ((Number) scenario.current.get("version")).longValue() + 1);
            scenario.assignmentCount++;
            return null;
        }).when(staff).assignment(eq(EMPLOYEE_ID), any(), eq(HOM_ID), nullable(String.class));
        doAnswer(invocation -> {
            scenario.auditCount++;
            return null;
        }).when(staff).audit(eq(HOM_ID), eq(EMPLOYEE_ID), eq("SET_EMPLOYEE_BRANCH"), anyMap(), anyMap(), nullable(String.class));
        return scenario;
    }

    private static Map<String, Object> row(String status, UUID branch) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", EMPLOYEE_ID);
        row.put("code", "GENZ-HOM");
        row.put("fullName", "Quản lý cấp cao");
        row.put("hiredOn", "2026-11-01");
        row.put("resignedOn", null);
        row.put("status", status);
        row.put("version", 0L);
        row.put("userId", HOM_ID);
        row.put("accountRole", "HEAD_OFFICE_MANAGER");
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
        private int assignmentCount;
        private int auditCount;

        private Scenario(StaffRepository staff, AccountRepository accounts, StaffService service) {
            this.staff = staff;
            this.accounts = accounts;
            this.service = service;
        }
    }
}
