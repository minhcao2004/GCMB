/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 27/10/2026
 * Description: Manual unit test cases for UC-05 View User Accounts.
 */
package com.gcmb.backend.manual.useraccount;

import com.gcmb.backend.manual.useraccount.UserAccountManualRunner.CheckedAction;
import com.gcmb.backend.manual.useraccount.UserAccountManualRunner.ManualTest;
import com.gcmb.backend.manual.useraccount.UserAccountManualRunner.TestResult;

import static com.gcmb.backend.manual.useraccount.UserAccountManualRunner.check;
import static com.gcmb.backend.manual.useraccount.UserAccountManualRunner.expectAuthFailure;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gcmb.backend.accounts.dto.AccountDtos.AccountPage;
import com.gcmb.backend.accounts.dto.AccountDtos.AccountView;
import com.gcmb.backend.accounts.dto.AccountDtos.EmployeeOption;
import com.gcmb.backend.accounts.dto.AccountDtos.Option;
import com.gcmb.backend.accounts.dto.AccountDtos.Options;
import java.util.ArrayList;
import java.util.List;

public final class ViewUserAccountsManualCases {
    private static final String UC = "UC-05 View User Accounts";
    private static final String PRE = "Manager đã đăng nhập; danh sách tài khoản demo Flyway V14/V15 tồn tại theo phạm vi nêu trong test case.";
    private static final String NOTE = "Nguồn: UC-05_View_User_Accounts.docx; MSG01, MSG24; Flyway V14/V15.";
    private static final String MSG24 = "Bạn không có quyền xem đối với tài khoản.";

    private ViewUserAccountsManualCases() {
    }

    public static List<ManualTest> cases() {
        List<ManualTest> cases = new ArrayList<>();
        cases.add(test("UAM-053", "Bộ lọc mặc định, page=0,size=10", "Trả page đúng trường và total.", "Normal", () -> {
            List<AccountView> items = demoAccounts();
            var f = UserAccountFixtures.list(new AccountPage(items, items.size(), 0, 10));
            AccountPage result = f.service().list("", "", null, "", 0, 10);
            return check(result.items().size() == 4 && result.total() == 4 && result.page() == 0 && result.size() == 10,
                    "items=" + result.items().size() + ", total=" + result.total() + ", page=" + result.page());
        }, "AccountService.list/AccountRepository.list"));
        cases.add(test("UAM-054", "Branch Manager xem danh sách không bộ lọc", "Chỉ thấy account trong phạm vi.", "Normal", () -> {
            List<AccountView> unscoped = demoAccounts();
            var f = UserAccountFixtures.list(new AccountPage(unscoped, unscoped.size(), 0, 10));
            AccountPage result = f.service().list("", "", null, "", 0, 10);
            boolean scoped = result.items().stream().allMatch(account -> UserAccountDemoData.HOA_BANG_BRANCH_ID.equals(account.branchId()));
            return check(scoped, "returnedIds=" + result.items().stream().map(AccountView::username).toList());
        }, "AuthSecurity/AccountService.list"));
        cases.add(test("UAM-055", "Branch Manager tìm username ngoài cơ sở", "Không lộ account ngoài phạm vi; trả rỗng.", "Abnormal", () -> {
            AccountView outside = UserAccountDemoData.account(UserAccountDemoData.NEW_ACCOUNT_ID, "Ngoài phạm vi", "OUT-01", "outside", "outside@example.invalid", "RECEPTIONIST", UserAccountDemoData.NLB_BRANCH_ID, "NLB", "ACTIVE", List.of());
            var f = UserAccountFixtures.list(new AccountPage(List.of(outside), 1, 0, 10));
            AccountPage result = f.service().list("outside", "", null, "", 0, 10);
            return check(result.items().isEmpty(), "returned=" + result.items().stream().map(AccountView::username).toList());
        }, "AuthSecurity/AccountService.list"));
        cases.add(test("UAM-056", "ACCOUNTANT/RECEPTIONIST gọi list", "Từ chối MSG24.", "Abnormal", () -> {
            var f = UserAccountFixtures.list(new AccountPage(demoAccounts(), 4, 0, 10));
            return expectAuthFailure(() -> f.service().list("", "", null, "", 0, 10), 403, MSG24);
        }, "AuthSecurity/AccountService.list"));
        cases.add(filter("UAM-057", "letan.hoa", "", null, "", "search username partial"));
        cases.add(filter("UAM-058", "KETOAN@EXAMPLE", "", null, "", "search email case-insensitive"));
        cases.add(filter("UAM-059", "Lễ tân - Hoa Bằng", "", null, "", "search employee name"));
        cases.add(test("UAM-060", "q='  QLCC  '", "Trim thành QLCC trước repository.", "Normal", () -> {
            var f = UserAccountFixtures.list(new AccountPage(List.of(UserAccountDemoData.headOfficeManager()), 1, 0, 10));
            f.service().list("  QLCC  ", "", null, "", 0, 10);
            verify(f.repository()).list("QLCC", "", null, "", 0, 10);
            return check(true, "repositoryQuery=QLCC");
        }, "AccountService.list"));
        cases.add(filter("UAM-061", "", "RECEPTIONIST", null, "", "filter role"));
        cases.add(filter("UAM-062", "", "", UserAccountDemoData.HOA_BANG_BRANCH_ID, "", "filter branch for staff assignment"));
        cases.add(filter("UAM-063", "", "BRANCH_MANAGER", UserAccountDemoData.HOA_BANG_BRANCH_ID, "", "filter manager assignment"));
        cases.add(test("UAM-064", "branchId=Hoa Bằng", "Không đưa chain-level account vào kết quả branch.", "Normal", () -> {
            List<AccountView> wrong = List.of(UserAccountDemoData.headOfficeManager(), UserAccountDemoData.accountant());
            var f = UserAccountFixtures.list(new AccountPage(wrong, 2, 0, 10));
            AccountPage result = f.service().list("", "", UserAccountDemoData.HOA_BANG_BRANCH_ID, "", 0, 10);
            boolean noChain = result.items().stream().noneMatch(account -> account.branchId() == null);
            return check(noChain, "returnedRoles=" + result.items().stream().map(AccountView::role).toList());
        }, "AccountRepository.list"));
        cases.add(filter("UAM-065", "", "", null, "ACTIVE", "filter ACTIVE/REVOKED status"));
        cases.add(filter("UAM-066", "letan", "RECEPTIONIST", UserAccountDemoData.HOA_BANG_BRANCH_ID, "ACTIVE", "combined filters use AND"));
        cases.add(test("UAM-067", "q không có kết quả", "items rỗng,total=0; UI dùng MSG01.", "Abnormal", () -> {
            var f = UserAccountFixtures.list(new AccountPage(List.of(), 0, 0, 10));
            AccountPage result = f.service().list("khong-ton-tai-xyz", "", null, "", 0, 10);
            return check(result.items().isEmpty() && result.total() == 0, "items=" + result.items().size() + ", total=" + result.total());
        }, "AccountService.list/AccountsPage"));
        cases.add(test("UAM-068", "page=0,size=10,total=15", "Trả 10 dòng đầu và total=15.", "Normal", () -> {
            var f = UserAccountFixtures.list(new AccountPage(demoAccounts(), 15, 0, 10));
            AccountPage result = f.service().list("", "", null, "", 0, 10);
            return check(result.total() == 15 && result.page() == 0 && result.size() == 10, "total=" + result.total() + ", page=" + result.page());
        }, "AccountService.list/AccountRepository.list"));
        cases.add(test("UAM-069", "page=1,size=10", "Repository nhận offset page 1.", "Normal", () -> {
            var f = UserAccountFixtures.list(new AccountPage(List.of(UserAccountDemoData.accountant()), 11, 1, 10));
            AccountPage result = f.service().list("", "", null, "", 1, 10);
            verify(f.repository()).list("", "", null, "", 1, 10);
            return check(result.page() == 1 && result.total() == 11, "page=" + result.page() + ", total=" + result.total());
        }, "AccountService.list/AccountRepository.list"));
        cases.add(invalidPaging("UAM-070", "", -1, 10, "page=-1"));
        cases.add(invalidPaging("UAM-071", "", 0, 0, "size=0"));
        cases.add(test("UAM-072", "size=100 rồi 101", "100 hợp lệ; 101 bị từ chối.", "Boundary", () -> {
            var f = UserAccountFixtures.list(new AccountPage(List.of(), 0, 0, 100));
            f.service().list("", "", null, "", 0, 100);
            TestResult invalid = expectAuthFailure(() -> f.service().list("", "", null, "", 0, 101), 400, "Bộ lọc hoặc phân trang không hợp lệ");
            return check(invalid.passed(), "size100=accepted; size101=" + invalid.actual());
        }, "AccountService.list"));
        cases.add(test("UAM-073", "q dài 200 rồi 201 ký tự", "200 hợp lệ; 201 bị từ chối.", "Boundary", () -> {
            var f = UserAccountFixtures.list(new AccountPage(List.of(), 0, 0, 10));
            f.service().list("a".repeat(200), "", null, "", 0, 10);
            TestResult invalid = expectAuthFailure(() -> f.service().list("a".repeat(201), "", null, "", 0, 10), 400, "Bộ lọc hoặc phân trang không hợp lệ");
            return check(invalid.passed(), "q200=accepted; q201=" + invalid.actual());
        }, "AccountService.list"));
        cases.add(test("UAM-074", "Xem chi tiết letan.hoabang", "Đủ trường và không mutate dữ liệu.", "Normal", () -> {
            AccountView account = UserAccountDemoData.receptionist();
            var f = UserAccountFixtures.list(new AccountPage(List.of(account), 1, 0, 10));
            AccountView detail = f.service().list("letan.hoabang", "", null, "", 0, 10).items().get(0);
            boolean complete = detail.id() != null && detail.employeeName() != null && detail.employeeCode() != null
                    && detail.username() != null && detail.email() != null && detail.role() != null
                    && detail.branchName() != null && detail.status() != null && detail.managedBranchIds() != null;
            return check(complete, "detail=" + detail);
        }, "AccountService.list/AccountDtos.AccountView"));
        return List.copyOf(cases);
    }

    private static ManualTest filter(String id, String q, String role, java.util.UUID branch, String status, String description) {
        return test(id, description, "Repository nhận đúng điều kiện lọc.", "Normal", () -> {
            var f = UserAccountFixtures.list(new AccountPage(List.of(UserAccountDemoData.receptionist()), 1, 0, 10));
            f.service().list(q, role, branch, status, 0, 10);
            verify(f.repository()).list(q.trim(), role, branch, status, 0, 10);
            return check(true, "q=" + q.trim() + ", role=" + role + ", branch=" + branch + ", status=" + status);
        }, "AccountService.list/AccountRepository.list");
    }

    private static ManualTest invalidPaging(String id, String q, int page, int size, String description) {
        return test(id, description, "Từ chối 400 trước khi gọi repository.", "Boundary", () -> {
            var f = UserAccountFixtures.list(new AccountPage(List.of(), 0, page, size));
            TestResult result = expectAuthFailure(() -> f.service().list(q, "", null, "", page, size), 400, "Bộ lọc hoặc phân trang không hợp lệ");
            verify(f.repository(), never()).list(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
            return result;
        }, "AccountService.list");
    }

    private static List<AccountView> demoAccounts() {
        return List.of(UserAccountDemoData.receptionist(), UserAccountDemoData.branchManager(),
                UserAccountDemoData.headOfficeManager(), UserAccountDemoData.accountant());
    }

    private static ManualTest test(String id, String input, String expected, String type,
            CheckedAction action, String sourceMethod) {
        return new ManualTest(id, UC, sourceMethod, PRE, input, expected, type,
                NOTE + " Production: " + sourceMethod + ".", action);
    }
}


