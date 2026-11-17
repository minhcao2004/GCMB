/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 12/11/2026
 * Description: Manual unit test cases cho UC-82 Transfer Employee.
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

final class TransferEmployeeManualCases {
    private static final UUID HOM_ID = UUID.fromString("47570000-0000-4000-8000-000000000005");
    private static final UUID BM_ID = UUID.fromString("47570000-0000-4000-8000-000000000003");
    private static final UUID EMPLOYEE_ID = UUID.fromString("47570000-0000-4000-8000-000000000011");
    private static final UUID RECEPTION_ACCOUNT = UUID.fromString("47570000-0000-4000-8000-000000000001");
    private static final UUID HOA_BANG = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID NLB = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final String TITLE = "UC-82 Transfer Employee";
    private static final String SOURCE = "UC-82; MSG24, MSG40; V14, V26; StaffService.assign, StaffRepository.assignment/moveReceptionAccess";

    private TransferEmployeeManualCases() {
    }

    static List<ManualCase> cases() {
        return List.of(
            test("SFM-050",
                "Head Office Manager qlcc đã đăng nhập; nhân viên ACTIVE có cơ sở Hoa Bằng; Nguyễn Lương Bằng đang hoạt động.",
                "Điều chuyển sang Nguyễn Lương Bằng.",
                "Phân công Hoa Bằng được kết thúc, Nguyễn Lương Bằng trở thành cơ sở hiện tại; lịch sử cũ vẫn còn.",
                () -> validTransfer(false)),
            test("SFM-051",
                "Nhân viên được điều chuyển có tài khoản Receptionist liên kết.",
                "Điều chuyển từ Hoa Bằng sang Nguyễn Lương Bằng.",
                "StaffService yêu cầu chuyển phạm vi Receptionist sang cơ sở mới và thu hồi phiên đăng nhập hiện tại.",
                () -> validTransfer(true)),
            test("SFM-052",
                "Head Office Manager đã đăng nhập; nhân viên ACTIVE nhưng chưa có cơ sở hiện tại.",
                "Điều chuyển sang Hoa Bằng.",
                "Hiển thị “Nhân viên chưa có cơ sở. Vui lòng gán cơ sở trước.”",
                TransferEmployeeManualCases::withoutCurrentBranch),
            test("SFM-053",
                "Head Office Manager đã đăng nhập; nhân viên đang thuộc Hoa Bằng.",
                "Chọn Hoa Bằng làm cơ sở mới.",
                "Hiển thị “Cơ sở mới phải khác cơ sở hiện tại.”; lịch sử không thay đổi.",
                TransferEmployeeManualCases::sameDestination),
            test("SFM-054",
                "Head Office Manager đã đăng nhập; nhân viên có cơ sở hiện tại; cơ sở đích đang SUSPENDED.",
                "Điều chuyển sang Tạm ngừng.",
                "Hiển thị “Cơ sở Tạm ngừng hiện không hoạt động. Vui lòng chọn cơ sở đang hoạt động để phân công.”",
                TransferEmployeeManualCases::inactiveDestination),
            test("SFM-055",
                "Head Office Manager đã đăng nhập; nhân viên INACTIVE nhưng còn lịch sử cơ sở.",
                "Điều chuyển sang Nguyễn Lương Bằng.",
                "Hiển thị “Chỉ phân công cơ sở cho nhân viên đang làm việc.”",
                TransferEmployeeManualCases::inactiveEmployee),
            test("SFM-056",
                "Head Office Manager đã đăng nhập; nhân viên có lịch phân công chưa kết thúc.",
                "Điều chuyển sang Nguyễn Lương Bằng.",
                "Hiển thị “Nhân viên có lịch phân công chưa kết thúc. Vui lòng kiểm tra lịch sử cơ sở.”",
                TransferEmployeeManualCases::futureAssignment),
            test("SFM-057",
                "Branch Manager qlcs.hoabang đã đăng nhập; nhân viên có cơ sở hiện tại.",
                "Điều chuyển nhân viên sang Nguyễn Lương Bằng.",
                "Hiển thị “Bạn không có quyền thực hiện thao tác đối với hồ sơ nhân viên này.”; cơ sở hiện tại không đổi.",
                TransferEmployeeManualCases::branchManagerDenied)
        );
    }

    private static Observation validTransfer(boolean receptionist) {
        Scenario scenario = ready("ACTIVE", HOA_BANG, receptionist ? RECEPTION_ACCOUNT : null,
            receptionist ? "RECEPTIONIST" : null);
        Map<String, Object> result = scenario.service.assign(headOffice(), EMPLOYEE_ID,
            new AssignmentCommand(NLB, "Điều chuyển theo kế hoạch", 0L), true);
        boolean passed = NLB.equals(result.get("branchId"))
            && ((List<?>) result.get("history")).size() == 2
            && scenario.assignmentCount == 1
            && scenario.auditCount == 1
            && scenario.receptionMoveCount == (receptionist ? 1 : 0);
        return new Observation("branchId=" + result.get("branchId") + ", history=" + ((List<?>) result.get("history")).size()
            + ", assignments=" + scenario.assignmentCount + ", receptionMoves=" + scenario.receptionMoveCount
            + ", audits=" + scenario.auditCount, passed);
    }

    private static Observation withoutCurrentBranch() {
        Scenario scenario = ready("ACTIVE", null, null, null);
        return rejected(scenario, headOffice(), new AssignmentCommand(HOA_BANG, null, 0L),
            "Nhân viên chưa có cơ sở. Vui lòng gán cơ sở trước.");
    }

    private static Observation sameDestination() {
        Scenario scenario = ready("ACTIVE", HOA_BANG, null, null);
        return rejected(scenario, headOffice(), new AssignmentCommand(HOA_BANG, null, 0L),
            "Cơ sở mới phải khác cơ sở hiện tại.");
    }

    private static Observation inactiveDestination() {
        Scenario scenario = ready("ACTIVE", HOA_BANG, null, null);
        when(scenario.staff.activeBranch(NLB)).thenReturn(false);
        when(scenario.staff.branchName(NLB)).thenReturn("Tạm ngừng");
        return rejected(scenario, headOffice(), new AssignmentCommand(NLB, null, 0L),
            "Cơ sở Tạm ngừng hiện không hoạt động. Vui lòng chọn cơ sở đang hoạt động để phân công.");
    }

    private static Observation inactiveEmployee() {
        Scenario scenario = ready("INACTIVE", HOA_BANG, null, null);
        return rejected(scenario, headOffice(), new AssignmentCommand(NLB, null, 0L),
            "Chỉ phân công cơ sở cho nhân viên đang làm việc.");
    }

    private static Observation futureAssignment() {
        Scenario scenario = ready("ACTIVE", HOA_BANG, null, null);
        when(scenario.staff.futureAssignment(EMPLOYEE_ID)).thenReturn(true);
        return rejected(scenario, headOffice(), new AssignmentCommand(NLB, null, 0L),
            "Nhân viên có lịch phân công chưa kết thúc. Vui lòng kiểm tra lịch sử cơ sở.");
    }

    private static Observation branchManagerDenied() {
        Scenario scenario = ready("ACTIVE", HOA_BANG, null, null);
        return rejected(scenario, branchManager(), new AssignmentCommand(NLB, null, 0L),
            "Bạn không có quyền thực hiện thao tác đối với hồ sơ nhân viên này.");
    }

    private static Observation rejected(Scenario scenario, Identity actor, AssignmentCommand command, String expected) {
        UUID before = (UUID) scenario.current.get("branchId");
        String actual;
        try {
            scenario.service.assign(actor, EMPLOYEE_ID, command, true);
            actual = "Không phát sinh lỗi";
        } catch (AuthFailure failure) {
            actual = failure.getMessage();
        }
        boolean unchanged = java.util.Objects.equals(before, scenario.current.get("branchId"));
        return new Observation("message=" + actual + ", branchId=" + scenario.current.get("branchId")
            + ", assignments=" + scenario.assignmentCount, expected.equals(actual) && unchanged
            && scenario.assignmentCount == 0 && scenario.auditCount == 0);
    }

    private static Scenario ready(String status, UUID branch, UUID userId, String accountRole) {
        StaffRepository staff = mock(StaffRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        Scenario scenario = new Scenario(staff, accounts, new StaffService(staff, accounts));
        scenario.current = row(status, branch, userId, accountRole);
        when(staff.actorActive(any(), anyString())).thenReturn(true);
        when(accounts.manages(any(), any(), anyBoolean())).thenReturn(true);
        when(staff.lock(EMPLOYEE_ID)).thenReturn(true);
        when(staff.find(EMPLOYEE_ID)).thenAnswer(invocation -> scenario.current);
        when(staff.activeBranch(HOA_BANG)).thenReturn(true);
        when(staff.activeBranch(NLB)).thenReturn(true);
        when(staff.futureAssignment(EMPLOYEE_ID)).thenReturn(false);
        when(staff.history(EMPLOYEE_ID)).thenAnswer(invocation -> scenario.assignmentCount == 0
            ? List.of(Map.of("branchCode", "HN_HOA_BANG"))
            : List.of(Map.of("branchCode", "HN_NGUYEN_LUONG_BANG_86"), Map.of("branchCode", "HN_HOA_BANG")));
        doAnswer(invocation -> {
            scenario.current.put("branchId", invocation.getArgument(1));
            scenario.current.put("version", ((Number) scenario.current.get("version")).longValue() + 1);
            scenario.assignmentCount++;
            return null;
        }).when(staff).assignment(eq(EMPLOYEE_ID), any(), eq(HOM_ID), nullable(String.class));
        doAnswer(invocation -> {
            scenario.receptionMoveCount++;
            return null;
        }).when(staff).moveReceptionAccess(eq(RECEPTION_ACCOUNT), eq(NLB));
        doAnswer(invocation -> {
            scenario.auditCount++;
            return null;
        }).when(staff).audit(eq(HOM_ID), eq(EMPLOYEE_ID), eq("TRANSFER_EMPLOYEE"), anyMap(), anyMap(), nullable(String.class));
        return scenario;
    }

    private static Map<String, Object> row(String status, UUID branch, UUID userId, String accountRole) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", EMPLOYEE_ID);
        row.put("code", "GENZ-REC-HOA-BANG");
        row.put("fullName", "Lễ tân - Hoa Bằng");
        row.put("hiredOn", "2026-11-01");
        row.put("resignedOn", null);
        row.put("status", status);
        row.put("version", 0L);
        row.put("userId", userId);
        row.put("accountRole", accountRole);
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
        private int receptionMoveCount;
        private int auditCount;

        private Scenario(StaffRepository staff, AccountRepository accounts, StaffService service) {
            this.staff = staff;
            this.accounts = accounts;
            this.service = service;
        }
    }
}
