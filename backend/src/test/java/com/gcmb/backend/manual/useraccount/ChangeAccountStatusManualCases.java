/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 27/10/2026
 * Description: Manual unit test cases for UC-07 Change User Account Status.
 */
package com.gcmb.backend.manual.useraccount;

import com.gcmb.backend.manual.useraccount.UserAccountManualRunner.CheckedAction;
import com.gcmb.backend.manual.useraccount.UserAccountManualRunner.ManualTest;
import com.gcmb.backend.manual.useraccount.UserAccountManualRunner.TestResult;

import static com.gcmb.backend.manual.useraccount.UserAccountManualRunner.check;
import static com.gcmb.backend.manual.useraccount.UserAccountManualRunner.expectAuthFailure;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gcmb.backend.accounts.dto.AccountAccessDtos.Deactivate;
import com.gcmb.backend.accounts.dto.AccountDtos.AccountView;
import com.gcmb.backend.accounts.service.AccountAccessService;
import com.gcmb.backend.auth.repository.AuthRepository;
import com.gcmb.backend.auth.repository.model.Account;
import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.security.AuthCrypto;
import com.gcmb.backend.auth.service.AuthService;
import com.gcmb.backend.auth.service.RecoveryMailer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ChangeAccountStatusManualCases {
    private static final String UC = "UC-07 Change User Account Status";
    private static final String PRE = "Manager đã đăng nhập; target tồn tại, ACTIVE và khác actor trừ khi test case nêu khác.";
    private static final String NOTE = "Nguồn: UC-07_Change_User_Account_Status.docx; BR-111, BR-131, BR-183; MSG02, MSG03, MSG12, MSG14, MSG24, MSG33; Flyway V14/V15.";
    private static final String MSG02 = "Vui lòng bổ sung lý do vô hiệu hóa.";
    private static final String MSG03 = "lý do vô hiệu hóa chỉ được chứa tối đa 1000 ký tự.";
    private static final String MSG24 = "Bạn không có quyền đổi trạng thái đối với tài khoản.";
    private static final String MSG33 = "Tài khoản đã bị vô hiệu hóa. Vui lòng liên hệ Quản lý cấp cao để được hỗ trợ.";

    private ChangeAccountStatusManualCases() {
    }

    public static List<ManualTest> cases() {
        List<ManualTest> cases = new ArrayList<>();
        cases.add(success("UAM-103", "Nhân viên đã nghỉ việc", "Head Office Manager thu hồi account ACTIVE", "Normal"));
        cases.add(test("UAM-104", "Branch Manager ACTIVE được phân công Hoa Bằng; target là RECEPTIONIST ACTIVE tại Hoa Bằng.",
                "Vô hiệu hóa target với lý do=Nghỉ việc.",
                "Chuyển target sang REVOKED, thu hồi token, giữ phân công và ghi lịch sử thay đổi.", "Normal", () -> {
            var f = fixture(UserAccountDemoData.receptionist());
            AccountView result = f.service().changeStatus(f.before().id(), new Deactivate("Nghỉ việc"),
                    UserAccountDemoData.branchManagerIdentity(), false);
            return check("REVOKED".equals(result.status()), "status=" + result.status());
        }, "AccountAccessService.changeStatus"));
        cases.add(actorDenied("UAM-105", UserAccountDemoData.branchManagerIdentity(), "Branch Manager ngoài phạm vi"));
        cases.add(actorDenied("UAM-106", UserAccountDemoData.accountantIdentity(), "Accountant"));
        cases.add(test("UAM-107", "targetId=actorId", "Từ chối với mã 400. Hiển thị: Không thể thay đổi trạng thái chính tài khoản đang đăng nhập. Không thay đổi dữ liệu.", "Abnormal", () -> {
            var f = fixture(UserAccountDemoData.headOfficeManager());
            return expectAuthFailure(() -> f.service().changeStatus(UserAccountDemoData.HEAD_OFFICE_MANAGER_ID,
                    new Deactivate("Nghỉ việc"), UserAccountDemoData.headOfficeManagerIdentity(), false), 400,
                    "Không thể thay đổi trạng thái chính tài khoản đang đăng nhập.");
        }, "AccountAccessService.changeStatus"));
        cases.add(test("UAM-108", "targetId không tồn tại", "Từ chối với mã 404. Hiển thị: Tài khoản không tồn tại. Không thay đổi dữ liệu.", "Abnormal", () -> {
            var f = fixture(UserAccountDemoData.receptionist());
            when(f.access().lockAccount(f.before().id())).thenReturn(false);
            TestResult result = expectAuthFailure(() -> f.service().changeStatus(f.before().id(),
                    new Deactivate("Nghỉ việc"), UserAccountDemoData.headOfficeManagerIdentity(), false), 404, "Tài khoản không tồn tại.");
            verify(f.access(), never()).deactivate(any());
            return result;
        }, "AccountAccessService.changeStatus"));
        cases.add(test("UAM-109", "target đã REVOKED", "Giữ nguyên REVOKED; không cập nhật, thu hồi token hoặc ghi audit trùng.", "Normal", () -> {
            var f = fixture(status(UserAccountDemoData.receptionist(), "REVOKED"));
            AccountView result = f.service().changeStatus(f.before().id(), new Deactivate("Lặp"),
                    UserAccountDemoData.headOfficeManagerIdentity(), false);
            verify(f.access(), never()).deactivate(any());
            verify(f.access(), never()).revokeTokens(any());
            verify(f.access(), never()).audit(any(), anyString(), any(), any(), any());
            return check("REVOKED".equals(result.status()), "status=" + result.status() + ", noOp=true");
        }, "AccountAccessService.changeStatus"));
        cases.add(reasonFailure("UAM-110", null, MSG02, "reason=null", "Abnormal"));
        cases.add(reasonFailure("UAM-111", " \u2003 ", MSG02, "reason whitespace-only", "Abnormal"));
        cases.add(success("UAM-112", "A", "reason dài 1 ký tự", "Boundary"));
        cases.add(success("UAM-113", "a".repeat(1000), "reason dài 1000 ký tự", "Boundary"));
        cases.add(reasonFailure("UAM-114", "a".repeat(1001), MSG03, "reason dài 1001 ký tự", "Boundary"));
        cases.add(test("UAM-116", "Thu hồi target có token ACTIVE", "Status REVOKED và revoke tokens trong transaction.", "Normal", () -> {
            var f = fixture(UserAccountDemoData.receptionist());
            AccountView result = f.service().changeStatus(f.before().id(), new Deactivate("Nghỉ việc"),
                    UserAccountDemoData.headOfficeManagerIdentity(), false);
            verify(f.access()).deactivate(f.before().id());
            verify(f.access()).revokeTokens(f.before().id());
            return check("REVOKED".equals(result.status()), "status=" + result.status() + ", tokensRevoked=true");
        }, "AccountAccessService.changeStatus/AccountAccessRepository.revokeTokens"));
        cases.add(test("UAM-117", "Vô hiệu hóa BRANCH_MANAGER có assignment",
                "Chuyển REVOKED nhưng giữ assignment để có thể kích hoạt lại; không gọi đồng bộ xóa assignment.", "Normal", () -> {
            var f = fixture(UserAccountDemoData.branchManager());
            f.service().changeStatus(f.before().id(), new Deactivate("Nghỉ việc"),
                    UserAccountDemoData.headOfficeManagerIdentity(), false);
            verify(f.branches(), never()).syncAccountManager(any(), any());
            return check(true, "managerAssignmentPreserved=true");
        }, "AccountAccessService.changeStatus"));
        cases.add(test("UAM-118", "Thu hồi kèm business history", "Audit before/after/reason; không xóa lịch sử.", "Normal", () -> {
            var f = fixture(UserAccountDemoData.receptionist());
            String reason = "Nhân viên đã nghỉ việc";
            AccountView after = f.service().changeStatus(f.before().id(), new Deactivate(reason),
                    UserAccountDemoData.headOfficeManagerIdentity(), false);
            verify(f.access()).audit(UserAccountDemoData.HEAD_OFFICE_MANAGER_ID,
                    "DEACTIVATE_USER_ACCOUNT", f.before(), after, reason);
            return check("REVOKED".equals(after.status()), "auditReason=" + reason + ", status=" + after.status());
        }, "AccountAccessService.changeStatus/AccountAccessRepository.audit"));
        cases.add(test("UAM-119", "Đăng nhập bằng account vừa REVOKED", "Từ chối với mã 403. Hiển thị: " + MSG33 + " Không tạo phiên.", "Abnormal", ChangeAccountStatusManualCases::loginRevoked, "AuthService.login"));
        cases.add(reactivateSuccess("UAM-120", status(UserAccountDemoData.receptionist(), "REVOKED"),
                UserAccountDemoData.headOfficeManagerIdentity(), "Head Office Manager kích hoạt lại RECEPTIONIST"));
        cases.add(reactivateSuccess("UAM-121", status(UserAccountDemoData.receptionist(), "REVOKED"),
                UserAccountDemoData.branchManagerIdentity(), "Branch Manager kích hoạt lại RECEPTIONIST trong phạm vi"));
        cases.add(test("UAM-122", "Target REVOKED nhưng hồ sơ nhân viên không còn ACTIVE.",
                "Yêu cầu kích hoạt lại tài khoản.",
                "Từ chối với mã 409. Hiển thị: Chỉ có thể kích hoạt tài khoản của nhân viên đang hoạt động. Không đổi trạng thái.", "Abnormal", () -> {
            AccountView before = status(UserAccountDemoData.receptionist(), "REVOKED");
            var f = fixture(before, "ACTIVE");
            when(f.accounts().canReactivate(before.id())).thenReturn(false);
            return expectAuthFailure(() -> f.service().changeStatus(before.id(), null,
                    UserAccountDemoData.headOfficeManagerIdentity(), true), 409,
                    "Chỉ có thể kích hoạt tài khoản của nhân viên đang hoạt động.");
        }, "AccountAccessService.changeStatus"));
        cases.add(test("UAM-123", "Target REVOKED thuộc cơ sở không còn ACTIVE.",
                "Yêu cầu kích hoạt lại tài khoản.",
                "Từ chối với mã 409. Hiển thị: Cơ sở hiện không hoạt động. Vui lòng kiểm tra phân công trước khi kích hoạt. Không đổi trạng thái.", "Abnormal", () -> {
            AccountView before = status(UserAccountDemoData.receptionist(), "REVOKED");
            var f = fixture(before, "ACTIVE");
            when(f.accounts().activeBranch(before.branchId())).thenReturn(false);
            return expectAuthFailure(() -> f.service().changeStatus(before.id(), null,
                    UserAccountDemoData.headOfficeManagerIdentity(), true), 409,
                    "Cơ sở hiện không hoạt động. Vui lòng kiểm tra phân công trước khi kích hoạt.");
        }, "AccountAccessService.changeStatus"));
        cases.add(test("UAM-124", "Target đã ACTIVE và khác actor.", "Yêu cầu kích hoạt lại tài khoản.",
                "Giữ nguyên ACTIVE; không cập nhật, thu hồi token hoặc ghi audit trùng.", "Normal", () -> {
            var f = fixture(UserAccountDemoData.receptionist(), "ACTIVE");
            AccountView result = f.service().changeStatus(f.before().id(), null,
                    UserAccountDemoData.headOfficeManagerIdentity(), true);
            verify(f.access(), never()).reactivate(any());
            verify(f.access(), never()).revokeTokens(any());
            verify(f.access(), never()).audit(any(), anyString(), any(), any(), any());
            return check("ACTIVE".equals(result.status()), "status=" + result.status() + ", noOp=true");
        }, "AccountAccessService.changeStatus"));
        cases.add(test("UAM-125", "Target là BRANCH_MANAGER REVOKED; hồ sơ nhân viên và cơ sở đều ACTIVE.",
                "Head Office Manager yêu cầu kích hoạt lại.",
                "Chuyển target sang ACTIVE, đồng bộ lại assignment quản lý, thu hồi token cũ và ghi audit.", "Normal", () -> {
            AccountView before = status(UserAccountDemoData.branchManager(), "REVOKED");
            var f = fixture(before, "ACTIVE");
            AccountView result = f.service().changeStatus(before.id(), null,
                    UserAccountDemoData.headOfficeManagerIdentity(), true);
            verify(f.branches()).syncAccountManager(before.id(), UserAccountDemoData.HEAD_OFFICE_MANAGER_ID);
            return check("ACTIVE".equals(result.status()), "status=" + result.status() + ", assignmentSynced=true");
        }, "AccountAccessService.changeStatus/BranchAdministrationService.syncAccountManager"));
        return List.copyOf(cases);
    }

    private static ManualTest success(String id, String reason, String description, String type) {
        return test(id, description, "Chuyển REVOKED, revoke token và audit reason.", type, () -> {
            var f = fixture(UserAccountDemoData.receptionist());
            AccountView result = f.service().changeStatus(f.before().id(), new Deactivate(reason),
                    UserAccountDemoData.headOfficeManagerIdentity(), false);
            return check("REVOKED".equals(result.status()), "status=" + result.status() + ", reasonLength=" + reason.length());
        }, "AccountAccessService.changeStatus");
    }

    private static ManualTest actorDenied(String id, Identity actor, String description) {
        return test(id, description, "Từ chối với mã 403. Hiển thị: " + MSG24 + " Không thay đổi trạng thái.", "Abnormal", () -> {
            AccountView target = "BRANCH_MANAGER".equals(actor.role())
                    ? UserAccountDemoData.account(UserAccountDemoData.RECEPTIONIST_ID, "Lễ tân NLB", "NLB-REC",
                            "letan.nlb", "letan.nlb@example.invalid", "RECEPTIONIST", UserAccountDemoData.NLB_BRANCH_ID,
                            "NLB", "ACTIVE", List.of())
                    : UserAccountDemoData.receptionist();
            var f = fixture(target);
            return expectAuthFailure(() -> f.service().changeStatus(f.before().id(),
                    new Deactivate("Nghỉ việc"), actor, false), 403, MSG24);
        }, "AccountAccessService.changeStatus");
    }

    private static ManualTest reasonFailure(String id, String reason, String expected, String description, String type) {
        return test(id, description, "Từ chối với mã 400. Hiển thị: " + expected
                + " Không thay đổi trạng thái.", type, () -> {
            var f = fixture(UserAccountDemoData.receptionist());
            return expectAuthFailure(() -> f.service().changeStatus(f.before().id(),
                    new Deactivate(reason), UserAccountDemoData.headOfficeManagerIdentity(), false), 400, expected);
        }, "AccountAccessService.changeStatus/AuthValidation.required");
    }

    private static ManualTest reactivateSuccess(String id, AccountView before, Identity actor, String description) {
        return test(id, description, "Chuyển ACTIVE, thu hồi token cũ và ghi audit REACTIVATE_USER_ACCOUNT.",
                "Normal", () -> {
            var f = fixture(before, "ACTIVE");
            AccountView result = f.service().changeStatus(before.id(), null, actor, true);
            verify(f.access()).reactivate(before.id());
            verify(f.access()).revokeTokens(before.id());
            verify(f.access()).audit(actor.id(), "REACTIVATE_USER_ACCOUNT", before, result, null);
            return check("ACTIVE".equals(result.status()), "status=" + result.status());
        }, "AccountAccessService.changeStatus/AccountAccessRepository.reactivate");
    }

    private static TestResult loginRevoked() {
        AuthRepository repository = mock(AuthRepository.class);
        AuthCrypto crypto = mock(AuthCrypto.class);
        RecoveryMailer mailer = mock(RecoveryMailer.class);
        when(crypto.token()).thenReturn("dummy-token");
        when(crypto.encode("dummy-token")).thenReturn("{pbkdf2-gcmb}dummy");
        Account account = new Account(UserAccountDemoData.RECEPTIONIST_ID, "letan.hoabang",
                "letan.hoabang@example.invalid", "{pbkdf2-gcmb}hash", "REVOKED",
                "RECEPTIONIST", UserAccountDemoData.HOA_BANG_BRANCH_ID, "GenZ Cinema Hoa Bằng");
        when(repository.findByUsername("letan.hoabang")).thenReturn(account);
        AuthService service = new AuthService(repository, crypto, mailer);
        return expectAuthFailure(() -> service.login("letan.hoabang", "Demo@123", false), 403, MSG33);
    }

    private static UserAccountFixtures.AccessFixture fixture(AccountView before) {
        return fixture(before, "REVOKED");
    }

    private static UserAccountFixtures.AccessFixture fixture(AccountView before, String afterStatus) {
        return UserAccountFixtures.access(before, status(before, afterStatus));
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

    private static ManualTest test(String id, String preconditions, String input, String expected, String type,
            CheckedAction action, String sourceMethod) {
        return new ManualTest(id, UC, sourceMethod, preconditions, input, expected, type,
                NOTE + " Production: " + sourceMethod + ".", action);
    }
}


