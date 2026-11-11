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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gcmb.backend.accounts.dto.AccountDtos.AccountPage;
import com.gcmb.backend.accounts.dto.AccountDtos.AccountView;
import com.gcmb.backend.accounts.dto.AccountDtos.EmployeeOption;
import com.gcmb.backend.accounts.dto.AccountDtos.Option;
import com.gcmb.backend.accounts.dto.AccountDtos.Options;
import com.gcmb.backend.accounts.repository.AccountRepository;
import java.util.ArrayList;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

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
        cases.add(test("UAM-054", "Branch Manager ACTIVE được phân công Hoa Bằng và đã đăng nhập.",
                "Xem danh sách tài khoản không nhập bộ lọc.",
                "Chỉ trả tài khoản RECEPTIONIST thuộc Hoa Bằng; không thay đổi dữ liệu.", "Normal", () -> {
            List<AccountView> scoped = List.of(UserAccountDemoData.receptionist());
            var f = UserAccountFixtures.list(new AccountPage(scoped, scoped.size(), 0, 10));
            AccountPage result = f.service().list("", "", null, "", 0, 10, UserAccountDemoData.branchManagerIdentity());
            verify(f.repository()).list("", "RECEPTIONIST", UserAccountDemoData.HOA_BANG_BRANCH_ID, "", 0, 10);
            return check(result.items().equals(scoped), "returnedIds=" + result.items().stream().map(AccountView::username).toList());
        }, "AccountService.list(Identity)", "Nguồn: UC-05 - View_User_Accounts.docx; Flyway V14/V15; Production: AccountService.list/managerScope. Phương pháp kiểm thử: White-box."));
        cases.add(test("UAM-055", "Branch Manager ACTIVE được phân công Hoa Bằng; tài khoản cần tìm thuộc NLB.",
                "Tìm username=outside mà không truyền branchId.",
                "Trả danh sách rỗng trong phạm vi Hoa Bằng; không làm lộ tài khoản NLB và không thay đổi dữ liệu.", "Abnormal", () -> {
            var f = UserAccountFixtures.list(new AccountPage(List.of(), 0, 0, 10));
            AccountPage result = f.service().list("outside", "", null, "", 0, 10, UserAccountDemoData.branchManagerIdentity());
            verify(f.repository()).list("outside", "RECEPTIONIST", UserAccountDemoData.HOA_BANG_BRANCH_ID, "", 0, 10);
            return check(result.items().isEmpty(), "returned=" + result.items());
        }, "AccountService.list(Identity)", "Nguồn: UC-05 - View_User_Accounts.docx; Flyway V14/V15; Production: AccountService.list/managerScope. Phương pháp kiểm thử: White-box."));
        cases.add(test("UAM-056", "Accountant ACTIVE đã đăng nhập nhưng không có quyền quản lý tài khoản.",
                "Yêu cầu xem danh sách tài khoản.",
                "Từ chối với mã 403. Hiển thị: Bạn không có quyền xem đối với tài khoản. Không trả dữ liệu tài khoản.", "Abnormal", () -> {
            var f = UserAccountFixtures.list(new AccountPage(demoAccounts(), 4, 0, 10));
            return expectAuthFailure(() -> f.service().list("", "", null, "", 0, 10,
                    UserAccountDemoData.accountantIdentity()), 403, MSG24);
        }, "AccountService.list(Identity)", "Nguồn: UC-05 - View_User_Accounts.docx; MSG24; Flyway V14/V15; Production: AccountService.list/managerScope. Phương pháp kiểm thử: Black-box."));
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
        cases.add(test("UAM-064", "Head Office Manager ACTIVE đã đăng nhập; tồn tại tài khoản phạm vi toàn chuỗi.",
                "Lọc danh sách với branchId=Hoa Bằng.",
                "Repository áp dụng điều kiện branch khiến HEAD_OFFICE_MANAGER và ACCOUNTANT có branch NULL, nên không thuộc kết quả Hoa Bằng.", "Normal", () -> {
            JdbcTemplate db = org.mockito.Mockito.mock(JdbcTemplate.class);
            when(db.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
            when(db.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
            AccountRepository repository = new AccountRepository(db);
            AccountPage result = repository.list("", "", UserAccountDemoData.HOA_BANG_BRANCH_ID, "", 0, 10);
            ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> dataSql = ArgumentCaptor.forClass(String.class);
            verify(db).queryForObject(countSql.capture(), eq(Long.class), any(Object[].class));
            verify(db).query(dataSql.capture(), any(RowMapper.class), any(Object[].class));
            String chainScope = "CASE WHEN r.code IN ('HEAD_OFFICE_MANAGER','ACCOUNTANT') THEN NULL";
            boolean scoped = countSql.getValue().contains(chainScope) && dataSql.getValue().contains(chainScope)
                    && result.items().isEmpty();
            return check(scoped, "branchPredicateApplied=" + scoped + ", returned=" + result.items());
        }, "AccountRepository.list", "Nguồn: UC-05 - View_User_Accounts.docx; Flyway V14/V15; Production: AccountRepository.list. Phương pháp kiểm thử: White-box."));
        cases.add(filter("UAM-065", "", "", null, "ACTIVE", "filter ACTIVE/REVOKED status"));
        cases.add(filter("UAM-066", "letan", "RECEPTIONIST", UserAccountDemoData.HOA_BANG_BRANCH_ID, "ACTIVE", "combined filters use AND"));
        cases.add(test("UAM-067", "q không có kết quả", "Service trả items rỗng và total=0.", "Abnormal", () -> {
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
        cases.add(test("UAM-126", "Head Office Manager ACTIVE đã đăng nhập.",
                "Mở form tạo tài khoản và yêu cầu danh sách lựa chọn.",
                "Trả danh sách nhân viên chưa có tài khoản, toàn bộ vai trò và cơ sở ACTIVE theo phạm vi toàn chuỗi.", "Normal", () -> {
            Options options = new Options(List.of(new EmployeeOption(UserAccountDemoData.AVAILABLE_EMPLOYEE_ID,
                    "Nhân viên mới", "GENZ-NEW")), List.of(new Option("RECEPTIONIST", "Lễ tân")),
                    List.of(new Option(UserAccountDemoData.HOA_BANG_BRANCH_ID.toString(), "GenZ Cinema Hoa Bằng")));
            var f = UserAccountFixtures.list(new AccountPage(List.of(), 0, 0, 10));
            when(f.repository().options()).thenReturn(options);
            return check(f.service().options(UserAccountDemoData.headOfficeManagerIdentity()).equals(options),
                    "optionsScope=chain");
        }, "AccountService.options(Identity)", "Nguồn: UC-04 - Create_User_Account.docx; BR-133; Flyway V14/V15; Production: AccountService.options/AccountRepository.options. Phương pháp kiểm thử: White-box."));
        cases.add(test("UAM-127", "Branch Manager ACTIVE được phân công Hoa Bằng và đã đăng nhập.",
                "Mở form tạo tài khoản và yêu cầu danh sách lựa chọn.",
                "Chỉ trả nhân viên RECEPTIONIST ACTIVE, chưa có tài khoản và thuộc Hoa Bằng; chỉ có vai trò RECEPTIONIST và cơ sở Hoa Bằng.", "Normal", () -> {
            Options options = new Options(List.of(new EmployeeOption(UserAccountDemoData.AVAILABLE_EMPLOYEE_ID,
                    "Nhân viên mới", "GENZ-NEW", "RECEPTIONIST", "new@example.invalid",
                    UserAccountDemoData.HOA_BANG_BRANCH_ID, "GenZ Cinema Hoa Bằng")),
                    List.of(new Option("RECEPTIONIST", "Lễ tân")),
                    List.of(new Option(UserAccountDemoData.HOA_BANG_BRANCH_ID.toString(), "GenZ Cinema Hoa Bằng")));
            var f = UserAccountFixtures.list(new AccountPage(List.of(), 0, 0, 10));
            when(f.repository().branchOptions(UserAccountDemoData.HOA_BANG_BRANCH_ID)).thenReturn(options);
            Options result = f.service().options(UserAccountDemoData.branchManagerIdentity());
            verify(f.repository()).branchOptions(UserAccountDemoData.HOA_BANG_BRANCH_ID);
            return check(result.equals(options), "roles=" + result.roles() + ", branches=" + result.branches());
        }, "AccountService.options(Identity)", "Nguồn: UC-04 - Create_User_Account.docx; BR-133; Flyway V14/V15; Production: AccountService.options/managerScope/AccountRepository.branchOptions. Phương pháp kiểm thử: White-box."));
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

    private static ManualTest test(String id, String preconditions, String input, String expected, String type,
            CheckedAction action, String sourceMethod, String note) {
        return new ManualTest(id, UC, sourceMethod, preconditions, input, expected, type, note, action);
    }
}


