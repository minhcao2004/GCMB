/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 27/10/2026
 * Description: Manual unit test cases for UC-06 Set Account Role.
 */
package com.gcmb.backend.manual.useraccount;

import com.gcmb.backend.manual.useraccount.UserAccountManualRunner.CheckedAction;
import com.gcmb.backend.manual.useraccount.UserAccountManualRunner.ManualTest;
import com.gcmb.backend.manual.useraccount.UserAccountManualRunner.TestResult;

import static com.gcmb.backend.manual.useraccount.UserAccountManualRunner.check;
import static com.gcmb.backend.manual.useraccount.UserAccountManualRunner.expectAuthFailure;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gcmb.backend.accounts.dto.AccountAccessDtos.PermissionView;
import com.gcmb.backend.accounts.dto.AccountAccessDtos.SetRole;
import com.gcmb.backend.accounts.dto.AccountDtos.AccountView;
import java.util.ArrayList;
import java.util.List;

public final class SetAccountRoleManualCases {
    private static final String UC = "UC-06 Set Account Role";
    private static final String PRE = "Head Office Manager qlcc ACTIVE đã đăng nhập; target tồn tại, ACTIVE và khác actor trừ khi test case nêu khác.";
    private static final String NOTE = "Nguồn: UC-06_Set_Account_Role.docx; BR-132; MSG02, MSG11, MSG14, MSG24, MSG40; Flyway V14/V15.";
    private static final String MSG24 = "Bạn không có quyền phân quyền đối với tài khoản.";
    private static final String MSG40 = "Cơ sở GenZ hiện không hoạt động. Vui lòng chọn cơ sở đang hoạt động để phân công.";

    private SetAccountRoleManualCases() {
    }

    public static List<ManualTest> cases() {
        List<ManualTest> cases = new ArrayList<>();
        cases.add(success("UAM-075", UserAccountDemoData.receptionist(), new SetRole("ACCOUNTANT", null, null), "RECEPTIONIST sang ACCOUNTANT", "Normal"));
        cases.add(success("UAM-076", UserAccountDemoData.accountant(), new SetRole("RECEPTIONIST", UserAccountDemoData.HOA_BANG_BRANCH_ID, null), "ACCOUNTANT sang RECEPTIONIST", "Normal"));
        cases.add(success("UAM-077", UserAccountDemoData.receptionist(), new SetRole("BRANCH_MANAGER", UserAccountDemoData.HOA_BANG_BRANCH_ID, List.of(UserAccountDemoData.HOA_BANG_BRANCH_ID)), "RECEPTIONIST sang BRANCH_MANAGER một cơ sở", "Normal"));
        cases.add(success("UAM-078", UserAccountDemoData.branchManager(), new SetRole("BRANCH_MANAGER", UserAccountDemoData.HOA_BANG_BRANCH_ID, List.of(UserAccountDemoData.HOA_BANG_BRANCH_ID, UserAccountDemoData.NLB_BRANCH_ID)), "BRANCH_MANAGER hai cơ sở", "Normal"));
        cases.add(test("UAM-079", "Thay hai assignment bằng một assignment", "Giữ đúng tập branchIds mới, xóa assignment cũ.", "Normal", () -> {
            AccountView before = UserAccountDemoData.account(UserAccountDemoData.BRANCH_MANAGER_ID, "BM", "BM-01", "qlcs.hoabang", "bm@example.invalid", "BRANCH_MANAGER", UserAccountDemoData.HOA_BANG_BRANCH_ID, "Hai cơ sở", "ACTIVE", List.of(UserAccountDemoData.HOA_BANG_BRANCH_ID, UserAccountDemoData.NLB_BRANCH_ID));
            SetRole input = new SetRole("BRANCH_MANAGER", UserAccountDemoData.HOA_BANG_BRANCH_ID, List.of(UserAccountDemoData.HOA_BANG_BRANCH_ID));
            var f = fixture(before, input);
            f.service().setRole(before.id(), input, UserAccountDemoData.HEAD_OFFICE_MANAGER_ID);
            verify(f.branches()).replaceAccountManagers(before.id(), input.branchIds(), UserAccountDemoData.HEAD_OFFICE_MANAGER_ID);
            return check(true, "managedBranches=" + input.branchIds());
        }, "AccountAccessService.setRole/BranchAdministrationService.replaceAccountManagers"));
        cases.add(actorDenied("UAM-080", UserAccountDemoData.BRANCH_MANAGER_ID, "Branch Manager"));
        cases.add(actorDenied("UAM-081", UserAccountDemoData.ACCOUNTANT_ID, "Accountant/Receptionist"));
        cases.add(test("UAM-082", "targetId=actorId", "Từ chối tự thay đổi quyền.", "Abnormal", () -> {
            SetRole input = new SetRole("ACCOUNTANT", null, null);
            var f = fixture(UserAccountDemoData.headOfficeManager(), input);
            return expectAuthFailure(() -> f.service().setRole(UserAccountDemoData.HEAD_OFFICE_MANAGER_ID, input, UserAccountDemoData.HEAD_OFFICE_MANAGER_ID), 400, "Không thể thay đổi quyền hoặc thu hồi chính tài khoản đang đăng nhập");
        }, "AccountAccessService.editable"));
        cases.add(test("UAM-083", "actor không ACTIVE hoặc không còn HOM", "Từ chối MSG24.", "Abnormal", () -> {
            SetRole input = new SetRole("ACCOUNTANT", null, null);
            var f = fixture(UserAccountDemoData.receptionist(), input);
            when(f.access().lockManagement(UserAccountDemoData.HEAD_OFFICE_MANAGER_ID)).thenReturn(false);
            return expectAuthFailure(() -> f.service().setRole(f.before().id(), input, UserAccountDemoData.HEAD_OFFICE_MANAGER_ID), 403, MSG24);
        }, "AccountAccessService.editable/AccountAccessRepository.lockManagement"));
        cases.add(test("UAM-084", "targetId không tồn tại", "Từ chối 404; không audit.", "Abnormal", () -> {
            SetRole input = new SetRole("ACCOUNTANT", null, null);
            var f = fixture(UserAccountDemoData.receptionist(), input);
            when(f.access().lockAccount(f.before().id())).thenReturn(false);
            TestResult result = expectAuthFailure(() -> f.service().setRole(f.before().id(), input, UserAccountDemoData.HEAD_OFFICE_MANAGER_ID), 404, "Tài khoản không tồn tại");
            verify(f.access(), never()).audit(any(), anyString(), any(), any(), any());
            return result;
        }, "AccountAccessService.editable"));
        cases.add(test("UAM-085", "target status=REVOKED", "Từ chối 409.", "Abnormal", () -> {
            AccountView revoked = status(UserAccountDemoData.receptionist(), "REVOKED");
            SetRole input = new SetRole("ACCOUNTANT", null, null);
            var f = fixture(revoked, input);
            return expectAuthFailure(() -> f.service().setRole(revoked.id(), input, UserAccountDemoData.HEAD_OFFICE_MANAGER_ID), 409, "Tài khoản đã bị thu hồi truy cập");
        }, "AccountAccessService.editable"));
        cases.add(roleInvalid("UAM-086", null, "role=null", "Vui lòng bổ sung Vai trò."));
        cases.add(roleInvalid("UAM-087", "UNKNOWN_ROLE", "role không tồn tại", "Vai trò không hợp lệ"));
        cases.add(invalidBranch("UAM-088", new SetRole("ACCOUNTANT", UserAccountDemoData.HOA_BANG_BRANCH_ID, null), "chain role có branchId", MSG40));
        cases.add(invalidBranch("UAM-089", new SetRole("RECEPTIONIST", null, null), "RECEPTIONIST thiếu branchId", MSG40));
        cases.add(test("UAM-090", "RECEPTIONIST dùng branch INACTIVE", "Từ chối đúng MSG40.", "Abnormal", () -> {
            SetRole input = new SetRole("RECEPTIONIST", UserAccountDemoData.INACTIVE_BRANCH_ID, null);
            var f = fixture(UserAccountDemoData.accountant(), input);
            when(f.accounts().activeBranch(UserAccountDemoData.INACTIVE_BRANCH_ID)).thenReturn(false);
            return expectAuthFailure(() -> f.service().setRole(f.before().id(), input, UserAccountDemoData.HEAD_OFFICE_MANAGER_ID), 400, MSG40);
        }, "AccountAccessService.setRole/AccountRepository.activeBranch"));
        cases.add(invalidBranch("UAM-091", new SetRole("RECEPTIONIST", UserAccountDemoData.HOA_BANG_BRANCH_ID, List.of(UserAccountDemoData.HOA_BANG_BRANCH_ID)), "branchIds cho role không phải BM", "Chỉ quản lý cơ sở được phân công nhiều cơ sở"));
        cases.add(invalidBranch("UAM-092", new SetRole("BRANCH_MANAGER", null, List.of()), "branchIds rỗng", "Chọn ít nhất một cơ sở, không chọn trùng"));
        cases.add(invalidBranch("UAM-093", new SetRole("BRANCH_MANAGER", null, java.util.Arrays.asList(UserAccountDemoData.HOA_BANG_BRANCH_ID, null)), "branchIds chứa null", "Chọn ít nhất một cơ sở, không chọn trùng"));
        cases.add(invalidBranch("UAM-094", new SetRole("BRANCH_MANAGER", null, List.of(UserAccountDemoData.HOA_BANG_BRANCH_ID, UserAccountDemoData.HOA_BANG_BRANCH_ID)), "branchIds trùng", "Chọn ít nhất một cơ sở, không chọn trùng"));
        cases.add(test("UAM-095", "branchIds gồm ACTIVE và INACTIVE", "Từ chối toàn bộ, đúng MSG40.", "Abnormal", () -> {
            SetRole input = new SetRole("BRANCH_MANAGER", UserAccountDemoData.HOA_BANG_BRANCH_ID, List.of(UserAccountDemoData.HOA_BANG_BRANCH_ID, UserAccountDemoData.INACTIVE_BRANCH_ID));
            var f = fixture(UserAccountDemoData.branchManager(), input);
            when(f.accounts().activeBranch(UserAccountDemoData.HOA_BANG_BRANCH_ID)).thenReturn(true);
            when(f.accounts().activeBranch(UserAccountDemoData.INACTIVE_BRANCH_ID)).thenReturn(false);
            return expectAuthFailure(() -> f.service().setRole(f.before().id(), input, UserAccountDemoData.HEAD_OFFICE_MANAGER_ID), 400, MSG40);
        }, "AccountAccessService.setRole/AccountRepository.activeBranch"));
        cases.add(test("UAM-096", "Gửi lại cùng role và branch", "No-op: không update, revoke token hoặc audit.", "Normal", () -> {
            SetRole input = new SetRole("RECEPTIONIST", UserAccountDemoData.HOA_BANG_BRANCH_ID, null);
            var f = fixture(UserAccountDemoData.receptionist(), input);
            f.service().setRole(f.before().id(), input, UserAccountDemoData.HEAD_OFFICE_MANAGER_ID);
            verify(f.access(), never()).setRole(any(), any(), any());
            verify(f.access(), never()).revokeTokens(any());
            verify(f.access(), never()).audit(any(), anyString(), any(), any(), any());
            return check(true, "no-op verified");
        }, "AccountAccessService.setRole"));
        cases.add(test("UAM-097", "Hủy tại confirmation", "Không gọi setRole và giữ dữ liệu.", "Normal", () -> {
            SetRole input = new SetRole("ACCOUNTANT", null, null);
            var f = fixture(UserAccountDemoData.receptionist(), input);
            verify(f.access(), never()).setRole(any(), any(), any());
            return check(true, "cancelled before service call");
        }, "AccountAccessDialog confirmation"));
        cases.add(test("UAM-098", "Target có token ACTIVE", "Revoke tất cả token sau đổi role.", "Normal", () -> {
            SetRole input = new SetRole("ACCOUNTANT", null, null);
            var f = fixture(UserAccountDemoData.receptionist(), input);
            f.service().setRole(f.before().id(), input, UserAccountDemoData.HEAD_OFFICE_MANAGER_ID);
            verify(f.access()).revokeTokens(f.before().id());
            return check(true, "revokeTokens called");
        }, "AccountAccessService.setRole/AccountAccessRepository.revokeTokens"));
        cases.add(test("UAM-099", "Role/scope thay đổi", "Audit before/after và không có secrets.", "Normal", () -> {
            SetRole input = new SetRole("ACCOUNTANT", null, null);
            var f = fixture(UserAccountDemoData.receptionist(), input);
            AccountView after = f.service().setRole(f.before().id(), input, UserAccountDemoData.HEAD_OFFICE_MANAGER_ID);
            verify(f.access()).audit(UserAccountDemoData.HEAD_OFFICE_MANAGER_ID, "SET_ACCOUNT_ROLE", f.before(), after, null);
            return check(true, "audit SET_ACCOUNT_ROLE called with before/after");
        }, "AccountAccessService.setRole/AccountAccessRepository.audit"));
        cases.add(test("UAM-100", "BM đổi sang ACCOUNTANT", "Đồng bộ/xóa manager assignments cũ.", "Normal", () -> {
            SetRole input = new SetRole("ACCOUNTANT", null, null);
            var f = fixture(UserAccountDemoData.branchManager(), input);
            f.service().setRole(f.before().id(), input, UserAccountDemoData.HEAD_OFFICE_MANAGER_ID);
            verify(f.branches()).syncAccountManager(f.before().id(), UserAccountDemoData.HEAD_OFFICE_MANAGER_ID);
            return check(true, "syncAccountManager called");
        }, "AccountAccessService.setRole/BranchAdministrationService.syncAccountManager"));
        cases.add(test("UAM-101", "permissions(RECEPTIONIST)", "Trả code/name/scope đúng role.", "Normal", () -> {
            SetRole input = new SetRole("RECEPTIONIST", UserAccountDemoData.HOA_BANG_BRANCH_ID, null);
            var f = fixture(UserAccountDemoData.receptionist(), input);
            List<PermissionView> permissions = List.of(new PermissionView("BOOKING_VIEW", "Xem booking", "BRANCH"));
            when(f.access().permissions("RECEPTIONIST")).thenReturn(permissions);
            List<PermissionView> result = f.service().permissions("RECEPTIONIST");
            return check(result.equals(permissions), "permissions=" + result);
        }, "AccountAccessService.permissions/AccountAccessRepository.permissions"));
        cases.add(test("UAM-102", "permissions(UNKNOWN_ROLE)", "Từ chối vai trò không hợp lệ.", "Abnormal", () -> {
            SetRole input = new SetRole("RECEPTIONIST", UserAccountDemoData.HOA_BANG_BRANCH_ID, null);
            var f = fixture(UserAccountDemoData.receptionist(), input);
            when(f.accounts().roleId("UNKNOWN_ROLE")).thenReturn(null);
            TestResult result = expectAuthFailure(() -> f.service().permissions("UNKNOWN_ROLE"), 400, "Vai trò không hợp lệ");
            verify(f.access(), never()).permissions(anyString());
            return result;
        }, "AccountAccessService.permissions"));
        return List.copyOf(cases);
    }

    private static ManualTest success(String id, AccountView before, SetRole input, String description, String type) {
        return test(id, description, "Role/scope được thay thế và token cũ bị revoke.", type, () -> {
            var f = fixture(before, input);
            AccountView result = f.service().setRole(before.id(), input, UserAccountDemoData.HEAD_OFFICE_MANAGER_ID);
            boolean passed = input.role().equals(result.role())
                    && (input.branchIds() == null || result.managedBranchIds().equals(input.branchIds()));
            return check(passed, "role=" + result.role() + ", branch=" + result.branchId() + ", managed=" + result.managedBranchIds());
        }, "AccountAccessService.setRole");
    }

    private static ManualTest actorDenied(String id, java.util.UUID actor, String description) {
        return test(id, description + " gọi setRole", "Từ chối đúng MSG24.", "Abnormal", () -> {
            SetRole input = new SetRole("ACCOUNTANT", null, null);
            var f = fixture(UserAccountDemoData.receptionist(), input);
            when(f.access().lockManagement(actor)).thenReturn(false);
            return expectAuthFailure(() -> f.service().setRole(f.before().id(), input, actor), 403, MSG24);
        }, "AuthSecurity/AccountAccessService.editable");
    }

    private static ManualTest roleInvalid(String id, String role, String description, String expected) {
        return test(id, description, "Từ chối role không hợp lệ.", "Abnormal", () -> {
            SetRole input = new SetRole(role, null, null);
            var f = fixture(UserAccountDemoData.receptionist(), input);
            if (role != null) when(f.accounts().roleId(role)).thenReturn(null);
            return expectAuthFailure(() -> f.service().setRole(f.before().id(), input, UserAccountDemoData.HEAD_OFFICE_MANAGER_ID), 400, expected);
        }, "AccountAccessService.setRole/AccountRepository.roleId");
    }

    private static ManualTest invalidBranch(String id, SetRole input, String description, String expected) {
        return test(id, description, "Từ chối branch scope không hợp lệ.", "Abnormal", () -> {
            var f = fixture(UserAccountDemoData.receptionist(), input);
            return expectAuthFailure(() -> f.service().setRole(f.before().id(), input, UserAccountDemoData.HEAD_OFFICE_MANAGER_ID), 400, expected);
        }, "AccountAccessService.setRole");
    }

    private static UserAccountFixtures.AccessFixture fixture(AccountView before, SetRole input) {
        return UserAccountFixtures.access(before, UserAccountFixtures.changed(before, input));
    }

    private static AccountView status(AccountView account, String status) {
        return UserAccountDemoData.account(account.id(), account.employeeName(), account.employeeCode(),
                account.username(), account.email(), account.role(), account.branchId(), account.branchName(),
                status, account.managedBranchIds());
    }

    private static ManualTest test(String id, String input, String expected, String type,
            CheckedAction action, String sourceMethod) {
        return new ManualTest(id, UC, sourceMethod, PRE, input, expected, type,
                NOTE + " Production: " + sourceMethod + ".", action);
    }
}


