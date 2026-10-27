/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 27/10/2026
 * Description: Manual unit test cases for UC-04 Create User Account.
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gcmb.backend.accounts.dto.AccountDtos.AccountView;
import com.gcmb.backend.accounts.dto.AccountDtos.CreateAccount;
import com.gcmb.backend.accounts.repository.AccountRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

public final class CreateUserAccountManualCases {
    private static final String UC = "UC-04 Create User Account";
    private static final String PRE = "Manager đã đăng nhập; dùng danh tính/cơ sở demo Flyway V14/V15 và hồ sơ nhân viên theo trạng thái nêu trong test case.";
    private static final String NOTE = "Nguồn: UC-04_Create_User_Account.docx; BR-111, BR-112, BR-113, BR-114, BR-129, BR-132, BR-133, BR-134, BR-164; MSG02, MSG03, MSG05, MSG10, MSG14, MSG24, MSG27, MSG29, MSG34, MSG35, MSG36, MSG39, MSG40; Flyway V14/V15.";
    private static final String MSG02_USERNAME = "Vui lòng bổ sung Tên đăng nhập.";
    private static final String MSG02_EMAIL = "Vui lòng bổ sung Email.";
    private static final String MSG02_PASSWORD = "Vui lòng bổ sung Mật khẩu.";
    private static final String MSG02_EMPLOYEE = "Vui lòng bổ sung Hồ sơ nhân viên.";
    private static final String MSG02_ROLE = "Vui lòng bổ sung Vai trò.";
    private static final String MSG05 = "Nhập địa chỉ email theo dạng ten@tenmien.com.";
    private static final String MSG29 = "Mật khẩu phải có từ 8 đến 64 ký tự, gồm ít nhất một chữ hoa, một chữ thường, một chữ số và một ký tự đặc biệt; không được chứa khoảng trắng.";
    private static final String MSG34 = "Tên đăng nhập này đã được sử dụng. Vui lòng chọn tên khác.";
    private static final String MSG35 = "Email này đã được sử dụng cho tài khoản khác. Vui lòng nhập email khác.";
    private static final String MSG36 = "Tên đăng nhập phải có từ 3 đến 100 ký tự và chỉ được dùng chữ cái không dấu, chữ số, dấu chấm (.), dấu gạch dưới (_) hoặc dấu gạch nối (-).";
    private static final String MSG39 = "Chỉ có thể tạo tài khoản cho nhân viên đang hoạt động và chưa có tài khoản. Vui lòng chọn nhân viên khác.";
    private static final String MSG40 = "Cơ sở GenZ hiện không hoạt động. Vui lòng chọn cơ sở đang hoạt động để phân công.";
    private static final String MSG24 = "Bạn không có quyền tạo đối với tài khoản.";

    private CreateUserAccountManualCases() {
    }

    public static List<ManualTest> cases() {
        List<ManualTest> cases = new ArrayList<>();
        cases.add(test("UAM-001", "Tạo RECEPTIONIST hợp lệ tại Hoa Bằng", "Tạo ACTIVE, liên kết nhân viên và phạm vi Hoa Bằng.", "Normal",
                () -> success(valid("nv.moi", "nv.moi@example.invalid", "Demo@123", "RECEPTIONIST", UserAccountDemoData.HOA_BANG_BRANCH_ID), "RECEPTIONIST", UserAccountDemoData.HOA_BANG_BRANCH_ID), "AccountService.create"));
        cases.add(test("UAM-002", "Tạo BRANCH_MANAGER tại một cơ sở ACTIVE", "Tạo account và đồng bộ assignment quản lý.", "Normal", () -> {
            CreateAccount input = valid("bm.moi", "bm.moi@example.invalid", "Demo@123", "BRANCH_MANAGER", UserAccountDemoData.HOA_BANG_BRANCH_ID);
            var f = UserAccountFixtures.create(input);
            f.service().create(input, UserAccountDemoData.HEAD_OFFICE_MANAGER_ID);
            verify(f.branches()).syncAccountManager(UserAccountDemoData.NEW_ACCOUNT_ID, UserAccountDemoData.HEAD_OFFICE_MANAGER_ID);
            return check(true, "account created and syncAccountManager called");
        }, "AccountService.create/BranchAdministrationService.syncAccountManager"));
        cases.add(test("UAM-003", "Tạo HEAD_OFFICE_MANAGER với branchId=null", "Tạo phạm vi Toàn chuỗi.", "Normal",
                () -> success(valid("hom.moi", "hom.moi@example.invalid", "Demo@123", "HEAD_OFFICE_MANAGER", null), "HEAD_OFFICE_MANAGER", null), "AccountService.create"));
        cases.add(test("UAM-004", "Tạo ACCOUNTANT với branchId=null", "Tạo phạm vi Toàn chuỗi.", "Normal",
                () -> success(valid("acc.moi", "acc.moi@example.invalid", "Demo@123", "ACCOUNTANT", null), "ACCOUNTANT", null), "AccountService.create"));
        cases.add(test("UAM-005", "Branch Manager tạo account trong cơ sở của mình", "Được phép theo UC-04.", "Normal", () -> {
            CreateAccount input = valid("rec.branch", "rec.branch@example.invalid", "Demo@123", "RECEPTIONIST", UserAccountDemoData.HOA_BANG_BRANCH_ID);
            var f = UserAccountFixtures.create(input);
            AccountView result = f.service().create(input, UserAccountDemoData.BRANCH_MANAGER_ID);
            return check(result != null, "createdByBranchManager=" + (result != null));
        }, "AuthSecurity/AccountService.create"));
        cases.add(test("UAM-006", "Branch Manager tạo account ngoài phạm vi", "Từ chối MSG24.", "Abnormal", () -> {
            CreateAccount input = valid("rec.other", "rec.other@example.invalid", "Demo@123", "RECEPTIONIST", UserAccountDemoData.NLB_BRANCH_ID);
            var f = UserAccountFixtures.create(input);
            return expectAuthFailure(() -> f.service().create(input, UserAccountDemoData.BRANCH_MANAGER_ID), 403, MSG24);
        }, "AuthSecurity/AccountService.create"));
        cases.add(test("UAM-007", "ACCOUNTANT/RECEPTIONIST gọi create", "Từ chối MSG24 và không tạo dữ liệu.", "Abnormal", () -> {
            CreateAccount input = valid("forbidden", "forbidden@example.invalid", "Demo@123", "RECEPTIONIST", UserAccountDemoData.HOA_BANG_BRANCH_ID);
            var f = UserAccountFixtures.create(input);
            return expectAuthFailure(() -> f.service().create(input, UserAccountDemoData.ACCOUNTANT_ID), 403, MSG24);
        }, "AuthSecurity/AccountService.create"));
        cases.add(test("UAM-008", "Hủy tại bước xác nhận", "Không gọi source create và không thay đổi dữ liệu.", "Normal", () -> {
            var f = UserAccountFixtures.create(valid("cancelled", "cancelled@example.invalid", "Demo@123", "RECEPTIONIST", UserAccountDemoData.HOA_BANG_BRANCH_ID));
            verify(f.repository(), never()).insert(any(), anyString(), anyString(), anyString(), any(), any());
            return check(true, "creation cancelled before service call");
        }, "CreateAccountDrawer confirmation"));
        cases.add(test("UAM-009", "Gửi lặp cùng confirmation", "Chỉ tạo đúng một account theo BR-164.", "Abnormal", () -> {
            CreateAccount input = valid("repeat", "repeat@example.invalid", "Demo@123", "RECEPTIONIST", UserAccountDemoData.HOA_BANG_BRANCH_ID);
            var f = UserAccountFixtures.create(input);
            when(f.repository().lockAvailableEmployee(input.employeeId())).thenReturn(true, false);
            f.service().create(input, UserAccountDemoData.HEAD_OFFICE_MANAGER_ID);
            try { f.service().create(input, UserAccountDemoData.HEAD_OFFICE_MANAGER_ID); } catch (RuntimeException ignored) { }
            verify(f.repository(), times(1)).insert(any(), anyString(), anyString(), anyString(), any(), any());
            return check(true, "insertCount=1");
        }, "AccountService.create/AccountRepository.lockAvailableEmployee"));
        cases.add(required("UAM-010", withUsername(null), MSG02_USERNAME, "username=null"));
        cases.add(required("UAM-011", withUsername(" \u2003 "), MSG02_USERNAME, "username whitespace-only"));
        cases.add(usernameCase("UAM-012", "ab", false, "2 ký tự", "Boundary"));
        cases.add(usernameCase("UAM-013", "a_1", true, "3 ký tự", "Boundary"));
        cases.add(usernameCase("UAM-014", "a".repeat(100), true, "100 ký tự", "Boundary"));
        cases.add(usernameCase("UAM-015", "a".repeat(101), false, "101 ký tự", "Boundary"));
        cases.add(usernameCase("UAM-016", "Abc.def_01-test", true, "ký tự hợp lệ", "Normal"));
        cases.add(usernameCase("UAM-017", "abc def", false, "chứa khoảng trắng", "Abnormal"));
        cases.add(usernameCase("UAM-018", "nhânviên", false, "chữ có dấu", "Abnormal"));
        cases.add(duplicate("UAM-019", "LETAN.HOABANG", "new@example.invalid", "username", MSG34, "trùng ACTIVE không phân biệt hoa thường"));
        cases.add(duplicate("UAM-020", "OLD.USER", "new@example.invalid", "username", MSG34, "trùng REVOKED"));
        cases.add(required("UAM-021", withEmail(null), MSG02_EMAIL, "email=null"));
        cases.add(required("UAM-022", withEmail("   "), MSG02_EMAIL, "email whitespace-only"));
        cases.add(emailCase("UAM-023", "nhanvien.example.com", false, "thiếu @", "Abnormal"));
        cases.add(emailCase("UAM-024", "a..b@example.com", false, "local-part không hợp lệ", "Abnormal"));
        cases.add(emailCase("UAM-025", "a".repeat(64) + "@example.com", true, "local-part 64 byte", "Boundary"));
        cases.add(emailCase("UAM-026", "a".repeat(65) + "@example.com", false, "local-part 65 byte", "Boundary"));
        cases.add(emailCase("UAM-027", "a".repeat(64) + "@" + "b".repeat(185) + ".com", true, "tổng 254 byte", "Boundary"));
        cases.add(emailCase("UAM-028", "a".repeat(64) + "@" + "b".repeat(186) + ".com", false, "tổng 255 byte", "Boundary"));
        cases.add(duplicate("UAM-029", "new.user", "LETAN.HOABANG@EXAMPLE.INVALID", "email", MSG35, "email trùng ACTIVE"));
        cases.add(duplicate("UAM-030", "new.user", "OLD@EXAMPLE.INVALID", "email", MSG35, "email trùng REVOKED"));
        cases.add(required("UAM-031", withPassword(null), MSG02_PASSWORD, "password=null"));
        cases.add(required("UAM-032", withPassword("   "), MSG29, "password whitespace-only"));
        cases.add(passwordCase("UAM-033", "Aa1@aaa", false, "7 ký tự", "Boundary"));
        cases.add(passwordCase("UAM-034", "Aa1@aaaa", true, "8 ký tự", "Boundary"));
        cases.add(passwordCase("UAM-035", "Aa1@" + "a".repeat(60), true, "64 ký tự", "Boundary"));
        cases.add(passwordCase("UAM-036", "Aa1@" + "a".repeat(61), false, "65 ký tự", "Boundary"));
        cases.add(passwordCase("UAM-037", "abc1@abc", false, "thiếu chữ hoa", "Abnormal"));
        cases.add(passwordCase("UAM-038", "ABC1@ABC", false, "thiếu chữ thường", "Abnormal"));
        cases.add(passwordCase("UAM-039", "Abc@defg", false, "thiếu chữ số", "Abnormal"));
        cases.add(passwordCase("UAM-040", "Abc1defg", false, "thiếu ký tự đặc biệt", "Abnormal"));
        cases.add(passwordCase("UAM-041", "Abc1@ def", false, "chứa khoảng trắng", "Abnormal"));
        cases.add(required("UAM-042", new CreateAccount(null, "new.user", "Demo@123", "new@example.invalid", "RECEPTIONIST", UserAccountDemoData.HOA_BANG_BRANCH_ID), MSG02_EMPLOYEE, "employeeId=null"));
        cases.add(employeeUnavailable("UAM-043", "employee INACTIVE"));
        cases.add(employeeUnavailable("UAM-044", "employee ACTIVE đã có account"));
        cases.add(required("UAM-045", new CreateAccount(UserAccountDemoData.AVAILABLE_EMPLOYEE_ID, "new.user", "Demo@123", "new@example.invalid", null, UserAccountDemoData.HOA_BANG_BRANCH_ID), MSG02_ROLE, "role=null"));
        cases.add(test("UAM-046", "role=UNKNOWN_ROLE", "Từ chối vai trò không hợp lệ.", "Abnormal", () -> {
            CreateAccount input = valid("new.user", "new@example.invalid", "Demo@123", "UNKNOWN_ROLE", UserAccountDemoData.HOA_BANG_BRANCH_ID);
            var f = UserAccountFixtures.create(input);
            when(f.repository().roleId("UNKNOWN_ROLE")).thenReturn(null);
            return expectAuthFailure(() -> f.service().create(input, UserAccountDemoData.HEAD_OFFICE_MANAGER_ID), 400, "Vui lòng bổ sung Vai trò.");
        }, "AccountService.create/AccountRepository.roleId"));
        cases.add(branchCase("UAM-047", null, "RECEPTIONIST", false, "branchId=null"));
        cases.add(test("UAM-048", "branchId trỏ cơ sở INACTIVE", "Từ chối đúng MSG40.", "Abnormal", () -> {
            CreateAccount input = valid("new.user", "new@example.invalid", "Demo@123", "RECEPTIONIST", UserAccountDemoData.INACTIVE_BRANCH_ID);
            var f = UserAccountFixtures.create(input);
            when(f.repository().activeBranch(UserAccountDemoData.INACTIVE_BRANCH_ID)).thenReturn(false);
            return expectAuthFailure(() -> f.service().create(input, UserAccountDemoData.HEAD_OFFICE_MANAGER_ID), 400, MSG40);
        }, "AccountService.create/AccountRepository.activeBranch"));
        cases.add(branchCase("UAM-049", UserAccountDemoData.HOA_BANG_BRANCH_ID, "ACCOUNTANT", false, "chain role có branchId"));
        cases.add(test("UAM-050", "password=Demo@123", "Hash PBKDF2-GCMB; không truyền plaintext xuống cột hash.", "Normal", () -> {
            CreateAccount input = valid("hash.user", "hash@example.invalid", "Demo@123", "RECEPTIONIST", UserAccountDemoData.HOA_BANG_BRANCH_ID);
            var f = UserAccountFixtures.create(input);
            f.service().create(input, UserAccountDemoData.HEAD_OFFICE_MANAGER_ID);
            ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
            verify(f.repository()).insert(any(), anyString(), anyString(), hash.capture(), any(), any());
            return check(hash.getValue().startsWith("{pbkdf2-gcmb}") && !hash.getValue().contains("Demo@123"), "storedHash=" + hash.getValue());
        }, "AccountService.create/AuthCrypto.encode"));
        cases.add(test("UAM-051", "Tạo account hợp lệ", "Insert account và liên kết đúng employee trong một transaction.", "Normal", () -> {
            CreateAccount input = valid("link.user", "link@example.invalid", "Demo@123", "RECEPTIONIST", UserAccountDemoData.HOA_BANG_BRANCH_ID);
            var f = UserAccountFixtures.create(input);
            AccountView result = f.service().create(input, UserAccountDemoData.HEAD_OFFICE_MANAGER_ID);
            verify(f.repository()).insert(any(), anyString(), anyString(), anyString(), any(), any());
            return check(result.id().equals(UserAccountDemoData.NEW_ACCOUNT_ID) && result.employeeName().equals("Nhân viên mới"), "id=" + result.id() + ", employee=" + result.employeeName());
        }, "AccountService.create/AccountRepository.insert"));
        cases.add(test("UAM-052", "Tạo account hợp lệ bởi qlcc", "Audit CREATE_USER_ACCOUNT không chứa password/token.", "Normal", CreateUserAccountManualCases::auditInsert, "AccountRepository.insert"));
        return List.copyOf(cases);
    }

    private static TestResult success(CreateAccount input, String role, UUID branchId) {
        var f = UserAccountFixtures.create(input);
        AccountView result = f.service().create(input, UserAccountDemoData.HEAD_OFFICE_MANAGER_ID);
        return check(result != null && role.equals(result.role()) && java.util.Objects.equals(branchId, result.branchId()),
                "role=" + result.role() + ", branchId=" + result.branchId() + ", status=" + result.status());
    }

    private static ManualTest required(String id, CreateAccount input, String expected, String description) {
        return test(id, description, "Từ chối 400 và hiển thị đúng message catalogue.", "Abnormal", () -> {
            var f = UserAccountFixtures.create(input);
            return expectAuthFailure(() -> f.service().create(input, UserAccountDemoData.HEAD_OFFICE_MANAGER_ID), 400, expected);
        }, "AccountService.create/AuthValidation.required");
    }

    private static ManualTest usernameCase(String id, String username, boolean valid, String description, String type) {
        CreateAccount input = withUsername(username);
        return test(id, "username=" + description, valid ? "Chấp nhận theo BR-129." : "Từ chối đúng MSG36.", type, () -> {
            var f = UserAccountFixtures.create(input);
            if (!valid) return expectAuthFailure(() -> f.service().create(input, UserAccountDemoData.HEAD_OFFICE_MANAGER_ID), 400, MSG36);
            AccountView result = f.service().create(input, UserAccountDemoData.HEAD_OFFICE_MANAGER_ID);
            return check(username.equals(result.username()), "storedUsernameLength=" + result.username().length());
        }, "AccountService.create");
    }

    private static ManualTest emailCase(String id, String email, boolean valid, String description, String type) {
        CreateAccount input = withEmail(email);
        return test(id, "email=" + description, valid ? "Chấp nhận theo BR-113." : "Từ chối đúng MSG05/MSG03.", type, () -> {
            var f = UserAccountFixtures.create(input);
            if (!valid) return expectAuthFailure(() -> f.service().create(input, UserAccountDemoData.HEAD_OFFICE_MANAGER_ID), 400,
                    email.length() > 254 ? "Email chỉ được chứa tối đa 254 ký tự." : MSG05);
            AccountView result = f.service().create(input, UserAccountDemoData.HEAD_OFFICE_MANAGER_ID);
            return check(result.email().length() == email.length(), "storedEmailLength=" + result.email().length());
        }, "AccountService.create/AuthValidation.email");
    }

    private static ManualTest passwordCase(String id, String password, boolean valid, String description, String type) {
        CreateAccount input = withPassword(password);
        return test(id, "password=" + description, valid ? "Chấp nhận theo BR-114." : "Từ chối đúng MSG29.", type, () -> {
            var f = UserAccountFixtures.create(input);
            if (!valid) return expectAuthFailure(() -> f.service().create(input, UserAccountDemoData.HEAD_OFFICE_MANAGER_ID), 400, MSG29);
            AccountView result = f.service().create(input, UserAccountDemoData.HEAD_OFFICE_MANAGER_ID);
            return check(result != null, "created=" + (result != null));
        }, "AccountService.create/AuthValidation.required");
    }

    private static ManualTest duplicate(String id, String username, String email, String field, String expected, String description) {
        CreateAccount input = valid(username, email, "Demo@123", "RECEPTIONIST", UserAccountDemoData.HOA_BANG_BRANCH_ID);
        return test(id, description, "Từ chối đúng message duplicate catalogue.", "Abnormal", () -> {
            var f = UserAccountFixtures.create(input);
            when(f.repository().exists("username", username.trim())).thenReturn("username".equals(field));
            when(f.repository().exists("email", email.trim().toLowerCase())).thenReturn("email".equals(field));
            return expectAuthFailure(() -> f.service().create(input, UserAccountDemoData.HEAD_OFFICE_MANAGER_ID), 409, expected);
        }, "AccountService.create/AccountRepository.exists");
    }

    private static ManualTest employeeUnavailable(String id, String description) {
        CreateAccount input = valid("new.user", "new@example.invalid", "Demo@123", "RECEPTIONIST", UserAccountDemoData.HOA_BANG_BRANCH_ID);
        return test(id, description, "Từ chối đúng MSG39.", "Abnormal", () -> {
            var f = UserAccountFixtures.create(input);
            when(f.repository().lockAvailableEmployee(input.employeeId())).thenReturn(false);
            return expectAuthFailure(() -> f.service().create(input, UserAccountDemoData.HEAD_OFFICE_MANAGER_ID), 409, MSG39);
        }, "AccountService.create/AccountRepository.lockAvailableEmployee");
    }

    private static ManualTest branchCase(String id, UUID branchId, String role, boolean valid, String description) {
        CreateAccount input = valid("branch.user", "branch@example.invalid", "Demo@123", role, branchId);
        return test(id, description, valid ? "Chấp nhận branch scope." : "Từ chối branch scope không hợp lệ.", "Abnormal", () -> {
            var f = UserAccountFixtures.create(input);
            return expectAuthFailure(() -> f.service().create(input, UserAccountDemoData.HEAD_OFFICE_MANAGER_ID), 400,
                    branchId == null ? MSG40 : "Cơ sở GenZ hiện không hoạt động. Vui lòng chọn cơ sở đang hoạt động để phân công.");
        }, "AccountService.create/AccountRepository.activeBranch");
    }

    private static TestResult auditInsert() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.update(anyString(), any(Object[].class))).thenReturn(1);
        AccountRepository repository = new AccountRepository(db);
        CreateAccount input = valid("audit.user", "audit@example.invalid", "Demo@123", "RECEPTIONIST", UserAccountDemoData.HOA_BANG_BRANCH_ID);
        repository.insert(input, input.username(), input.email(), "{pbkdf2-gcmb}hash",
                UserAccountDemoData.ROLE_ID, UserAccountDemoData.HEAD_OFFICE_MANAGER_ID);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(db, times(3)).update(sql.capture(), any(Object[].class));
        String auditSql = sql.getAllValues().stream().filter(value -> value.contains("audit_event")).findFirst().orElse("");
        boolean passed = auditSql.contains("CREATE_USER_ACCOUNT") && !auditSql.contains("password_hash") && !auditSql.contains("auth_token");
        return check(passed, "auditSql=" + auditSql.replaceAll("\\s+", " ").trim());
    }

    private static CreateAccount valid(String username, String email, String password, String role, UUID branchId) {
        return new CreateAccount(UserAccountDemoData.AVAILABLE_EMPLOYEE_ID, username, password, email, role, branchId);
    }

    private static CreateAccount withUsername(String username) {
        return valid(username, "new@example.invalid", "Demo@123", "RECEPTIONIST", UserAccountDemoData.HOA_BANG_BRANCH_ID);
    }

    private static CreateAccount withEmail(String email) {
        return valid("new.user", email, "Demo@123", "RECEPTIONIST", UserAccountDemoData.HOA_BANG_BRANCH_ID);
    }

    private static CreateAccount withPassword(String password) {
        return valid("new.user", "new@example.invalid", password, "RECEPTIONIST", UserAccountDemoData.HOA_BANG_BRANCH_ID);
    }

    private static ManualTest test(String id, String input, String expected, String type,
            CheckedAction action, String sourceMethod) {
        return new ManualTest(id, UC, sourceMethod, PRE, input, expected, type,
                NOTE + " Production: " + sourceMethod + ".", action);
    }
}


