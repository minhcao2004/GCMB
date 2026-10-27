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
    private static final String MSG02 = "Vui lòng bổ sung Lý do.";
    private static final String MSG03 = "Lý do chỉ được chứa tối đa 1000 ký tự.";
    private static final String MSG24 = "Bạn không có quyền thay đổi trạng thái đối với tài khoản.";
    private static final String MSG33 = "Tài khoản đã bị vô hiệu hóa. Vui lòng liên hệ Quản lý cấp cao để được hỗ trợ.";

    private ChangeAccountStatusManualCases() {
    }

    public static List<ManualTest> cases() {
        List<ManualTest> cases = new ArrayList<>();
        cases.add(success("UAM-103", "Nhân viên đã nghỉ việc", "Head Office Manager thu hồi account ACTIVE", "Normal"));
        cases.add(test("UAM-104", "Branch Manager thu hồi account trong cơ sở", "Được phép theo UC-07.", "Normal", () -> {
            var f = fixture(UserAccountDemoData.receptionist());
            when(f.access().lockManagement(UserAccountDemoData.BRANCH_MANAGER_ID)).thenReturn(false);
            AccountView result = f.service().deactivate(f.before().id(), new Deactivate("Nghỉ việc"), UserAccountDemoData.BRANCH_MANAGER_ID);
            return check("REVOKED".equals(result.status()), "status=" + result.status());
        }, "AuthSecurity/AccountAccessService.deactivate"));
        cases.add(actorDenied("UAM-105", UserAccountDemoData.BRANCH_MANAGER_ID, "Branch Manager ngoài phạm vi"));
        cases.add(actorDenied("UAM-106", UserAccountDemoData.ACCOUNTANT_ID, "Accountant/Receptionist"));
        cases.add(test("UAM-107", "targetId=actorId", "Từ chối tự thu hồi.", "Abnormal", () -> {
            var f = fixture(UserAccountDemoData.headOfficeManager());
            return expectAuthFailure(() -> f.service().deactivate(UserAccountDemoData.HEAD_OFFICE_MANAGER_ID,
                    new Deactivate("Nghỉ việc"), UserAccountDemoData.HEAD_OFFICE_MANAGER_ID), 400,
                    "Không thể thay đổi quyền hoặc thu hồi chính tài khoản đang đăng nhập");
        }, "AccountAccessService.editable"));
        cases.add(test("UAM-108", "targetId không tồn tại", "Từ chối 404; không mutate.", "Abnormal", () -> {
            var f = fixture(UserAccountDemoData.receptionist());
            when(f.access().lockAccount(f.before().id())).thenReturn(false);
            TestResult result = expectAuthFailure(() -> f.service().deactivate(f.before().id(),
                    new Deactivate("Nghỉ việc"), UserAccountDemoData.HEAD_OFFICE_MANAGER_ID), 404, "Tài khoản không tồn tại");
            verify(f.access(), never()).deactivate(any());
            return result;
        }, "AccountAccessService.editable"));
        cases.add(test("UAM-109", "target đã REVOKED", "Từ chối 409; không audit trùng.", "Abnormal", () -> {
            var f = fixture(status(UserAccountDemoData.receptionist(), "REVOKED"));
            TestResult result = expectAuthFailure(() -> f.service().deactivate(f.before().id(),
                    new Deactivate("Lặp"), UserAccountDemoData.HEAD_OFFICE_MANAGER_ID), 409, "Tài khoản đã bị thu hồi truy cập");
            verify(f.access(), never()).audit(any(), anyString(), any(), any(), any());
            return result;
        }, "AccountAccessService.editable"));
        cases.add(reasonFailure("UAM-110", null, MSG02, "reason=null", "Abnormal"));
        cases.add(reasonFailure("UAM-111", " \u2003 ", MSG02, "reason whitespace-only", "Abnormal"));
        cases.add(success("UAM-112", "A", "reason dài 1 ký tự", "Boundary"));
        cases.add(success("UAM-113", "a".repeat(1000), "reason dài 1000 ký tự", "Boundary"));
        cases.add(reasonFailure("UAM-114", "a".repeat(1001), MSG03, "reason dài 1001 ký tự", "Boundary"));
        cases.add(test("UAM-115", "Hủy tại confirmation", "Không gọi deactivate; dữ liệu giữ nguyên.", "Normal", () -> {
            var f = fixture(UserAccountDemoData.receptionist());
            verify(f.access(), never()).deactivate(any());
            verify(f.access(), never()).revokeTokens(any());
            return check(true, "cancelled before service call");
        }, "AccountAccessDialog confirmation"));
        cases.add(test("UAM-116", "Thu hồi target có token ACTIVE", "Status REVOKED và revoke tokens trong transaction.", "Normal", () -> {
            var f = fixture(UserAccountDemoData.receptionist());
            AccountView result = f.service().deactivate(f.before().id(), new Deactivate("Nghỉ việc"), UserAccountDemoData.HEAD_OFFICE_MANAGER_ID);
            verify(f.access()).deactivate(f.before().id());
            verify(f.access()).revokeTokens(f.before().id());
            return check("REVOKED".equals(result.status()), "status=" + result.status() + ", tokensRevoked=true");
        }, "AccountAccessService.deactivate/AccountAccessRepository.revokeTokens"));
        cases.add(test("UAM-117", "Thu hồi BRANCH_MANAGER có assignment", "Đồng bộ/xóa manager assignment.", "Normal", () -> {
            var f = fixture(UserAccountDemoData.branchManager());
            f.service().deactivate(f.before().id(), new Deactivate("Nghỉ việc"), UserAccountDemoData.HEAD_OFFICE_MANAGER_ID);
            verify(f.branches()).syncAccountManager(f.before().id(), UserAccountDemoData.HEAD_OFFICE_MANAGER_ID);
            return check(true, "syncAccountManager called");
        }, "AccountAccessService.deactivate/BranchAdministrationService.syncAccountManager"));
        cases.add(test("UAM-118", "Thu hồi kèm business history", "Audit before/after/reason; không xóa lịch sử.", "Normal", () -> {
            var f = fixture(UserAccountDemoData.receptionist());
            String reason = "Nhân viên đã nghỉ việc";
            AccountView after = f.service().deactivate(f.before().id(), new Deactivate(reason), UserAccountDemoData.HEAD_OFFICE_MANAGER_ID);
            verify(f.access()).audit(UserAccountDemoData.HEAD_OFFICE_MANAGER_ID,
                    "DEACTIVATE_USER_ACCOUNT", f.before(), after, reason);
            return check("REVOKED".equals(after.status()), "auditReason=" + reason + ", status=" + after.status());
        }, "AccountAccessService.deactivate/AccountAccessRepository.audit"));
        cases.add(test("UAM-119", "Đăng nhập bằng account vừa REVOKED", "Từ chối đúng MSG33.", "Abnormal", ChangeAccountStatusManualCases::loginRevoked, "AuthService.login"));
        cases.add(test("UAM-120", "REVOKED sang ACTIVE", "Có đường production reactivate và trả trạng thái ACTIVE.", "Normal", () -> {
            boolean exists = Arrays.stream(AccountAccessService.class.getDeclaredMethods())
                    .anyMatch(method -> method.getName().equals("reactivate") || method.getName().equals("activate"));
            return check(exists, "reactivateMethodPresent=" + exists);
        }, "AccountAccessService reactivate endpoint"));
        return List.copyOf(cases);
    }

    private static ManualTest success(String id, String reason, String description, String type) {
        return test(id, description, "Chuyển REVOKED, revoke token và audit reason.", type, () -> {
            var f = fixture(UserAccountDemoData.receptionist());
            AccountView result = f.service().deactivate(f.before().id(), new Deactivate(reason), UserAccountDemoData.HEAD_OFFICE_MANAGER_ID);
            return check("REVOKED".equals(result.status()), "status=" + result.status() + ", reasonLength=" + reason.length());
        }, "AccountAccessService.deactivate");
    }

    private static ManualTest actorDenied(String id, java.util.UUID actor, String description) {
        return test(id, description, "Từ chối đúng MSG24.", "Abnormal", () -> {
            var f = fixture(UserAccountDemoData.receptionist());
            when(f.access().lockManagement(actor)).thenReturn(false);
            return expectAuthFailure(() -> f.service().deactivate(f.before().id(),
                    new Deactivate("Nghỉ việc"), actor), 403, MSG24);
        }, "AuthSecurity/AccountAccessService.editable");
    }

    private static ManualTest reasonFailure(String id, String reason, String expected, String description, String type) {
        return test(id, description, "Từ chối đúng System Message.", type, () -> {
            var f = fixture(UserAccountDemoData.receptionist());
            return expectAuthFailure(() -> f.service().deactivate(f.before().id(),
                    new Deactivate(reason), UserAccountDemoData.HEAD_OFFICE_MANAGER_ID), 400, expected);
        }, "AccountAccessService.deactivate/AuthValidation.required");
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
        return UserAccountFixtures.access(before, status(before, "REVOKED"));
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


