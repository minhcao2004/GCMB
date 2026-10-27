/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 27/10/2026
 * Description: Manual unit test cases for UC-128 Login using Flyway demo accounts.
 */
package com.gcmb.backend.manual.personalaccount;

import com.gcmb.backend.manual.personalaccount.PersonalAccountManualRunner.CheckedAction;
import com.gcmb.backend.manual.personalaccount.PersonalAccountManualRunner.ManualTest;
import com.gcmb.backend.manual.personalaccount.PersonalAccountManualRunner.TestResult;

import static com.gcmb.backend.manual.personalaccount.PersonalAccountManualRunner.check;
import static com.gcmb.backend.manual.personalaccount.PersonalAccountManualRunner.expectAuthFailure;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gcmb.backend.auth.dto.LoginResult;
import com.gcmb.backend.auth.repository.AuthRepository;
import com.gcmb.backend.auth.repository.model.Account;
import com.gcmb.backend.auth.security.AuthCrypto;
import com.gcmb.backend.auth.service.AuthService;
import com.gcmb.backend.auth.service.RecoveryMailer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class LoginManualCases {
    private static final String UC = "UC-128 Login";
    private static final String PRE = "Tài khoản demo đã được tạo bởi V14/V15 và người dùng chưa đăng nhập.";
    private static final String NOTE = "Nguồn: UC-128_Login.docx; BR-111, BR-131; MSG02, MSG03, MSG08, MSG24, MSG33; Flyway V14/V15.";

    private LoginManualCases() {
    }

    public static List<ManualTest> cases() {
        List<ManualTest> cases = new ArrayList<>();
        cases.add(roleCase("PAM-001", "RECEPTIONIST", PersonalAccountDemoData.RECEPTIONIST_ID,
                PersonalAccountDemoData.USERNAME, PersonalAccountDemoData.EMAIL,
                PersonalAccountDemoData.HOA_BANG_BRANCH_ID, "/le-tan"));
        cases.add(roleCase("PAM-002", "BRANCH_MANAGER", PersonalAccountDemoData.BRANCH_MANAGER_ID,
                "qlcs.hoabang", "qlcs.hoabang@example.invalid",
                PersonalAccountDemoData.HOA_BANG_BRANCH_ID, "/quan-ly-chi-nhanh"));
        cases.add(roleCase("PAM-003", "HEAD_OFFICE_MANAGER", PersonalAccountDemoData.HEAD_OFFICE_MANAGER_ID,
                "qlcc", "qlcc@example.invalid", null, "/quan-ly-he-thong"));
        cases.add(roleCase("PAM-004", "ACCOUNTANT", PersonalAccountDemoData.ACCOUNTANT_ID,
                "ketoan", "ketoan@example.invalid", null, "/ke-toan"));

        cases.add(test("PAM-005", "username='  letan.hoabang  ', password hợp lệ, remember=false",
                "Đăng nhập thành công; username được trim; thời hạn cookie phiên=-1.", "Normal", () -> {
                    Fixture f = fixture();
                    when(f.repository.findByUsername(PersonalAccountDemoData.USERNAME))
                            .thenReturn(PersonalAccountDemoData.receptionistAccount());
                    when(f.crypto.matches(PersonalAccountDemoData.CURRENT_PASSWORD,
                            PersonalAccountDemoData.PASSWORD_HASH)).thenReturn(true);
                    LoginResult result = f.service.login("  " + PersonalAccountDemoData.USERNAME + "  ",
                            PersonalAccountDemoData.CURRENT_PASSWORD, false);
                    return check(result.seconds() == -1 && PersonalAccountDemoData.USERNAME.equals(result.user().username()),
                            "username=" + result.user().username() + ", seconds=" + result.seconds());
                }));
        cases.add(test("PAM-006", "username và password hợp lệ, remember=true",
                "Đăng nhập thành công; thời hạn cookie ghi nhớ=Integer.MAX_VALUE.", "Normal", () -> {
                    Fixture f = successFixture(PersonalAccountDemoData.receptionistAccount());
                    LoginResult result = f.service.login(PersonalAccountDemoData.USERNAME,
                            PersonalAccountDemoData.CURRENT_PASSWORD, true);
                    return check(result.seconds() == Integer.MAX_VALUE,
                            "seconds=" + result.seconds() + ", tokenLength=" + result.token().length());
                }));
        cases.add(test("PAM-007", "username=null, password hợp lệ",
                "Từ chối 400 và hiển thị MSG02: Vui lòng bổ sung Tên đăng nhập.", "Abnormal",
                () -> expectAuthFailure(() -> fixture().service.login(null,
                        PersonalAccountDemoData.CURRENT_PASSWORD, false), 400,
                        "Vui lòng bổ sung Tên đăng nhập.")));
        cases.add(test("PAM-008", "username chỉ gồm khoảng trắng, password hợp lệ",
                "Từ chối 400 và hiển thị MSG02: Vui lòng bổ sung Tên đăng nhập.", "Abnormal",
                () -> expectAuthFailure(() -> fixture().service.login(" \u00A0\uFEFF ",
                        PersonalAccountDemoData.CURRENT_PASSWORD, false), 400,
                        "Vui lòng bổ sung Tên đăng nhập.")));
        cases.add(test("PAM-009", "username hợp lệ, password=null",
                "Từ chối 400 và hiển thị MSG02: Vui lòng bổ sung Mật khẩu.", "Abnormal",
                () -> expectAuthFailure(() -> fixture().service.login(PersonalAccountDemoData.USERNAME,
                        null, false), 400, "Vui lòng bổ sung Mật khẩu.")));
        cases.add(test("PAM-010", "username không tồn tại, password bất kỳ",
                "Từ chối 401 và hiển thị đúng MSG08.", "Abnormal", () -> {
                    Fixture f = fixture();
                    when(f.repository.findByUsername("khong.ton.tai")).thenReturn(null);
                    return expectAuthFailure(() -> f.service.login("khong.ton.tai", "Sai@1234", false),
                            401, "Tên đăng nhập hoặc mật khẩu không đúng. Vui lòng kiểm tra và thử lại.");
                }));
        cases.add(test("PAM-011", "tài khoản tồn tại, password sai",
                "Từ chối 401 và hiển thị đúng MSG08.", "Abnormal", () -> {
                    Fixture f = fixture();
                    when(f.repository.findByUsername(PersonalAccountDemoData.USERNAME))
                            .thenReturn(PersonalAccountDemoData.receptionistAccount());
                    when(f.crypto.matches("Sai@1234", PersonalAccountDemoData.PASSWORD_HASH)).thenReturn(false);
                    return expectAuthFailure(() -> f.service.login(PersonalAccountDemoData.USERNAME,
                            "Sai@1234", false), 401,
                            "Tên đăng nhập hoặc mật khẩu không đúng. Vui lòng kiểm tra và thử lại.");
                }));
        cases.add(inactiveCase("PAM-012", "INACTIVE"));
        cases.add(inactiveCase("PAM-013", "LOCKED"));
        cases.add(test("PAM-014", "quản lý cơ sở ACTIVE nhưng chưa được gán cơ sở",
                "Từ chối 403 theo MSG24, không tạo phiên.", "Abnormal", () -> {
                    Fixture f = fixture();
                    Account account = PersonalAccountDemoData.account(PersonalAccountDemoData.BRANCH_MANAGER_ID,
                            "qlcs.hoabang", "qlcs.hoabang@example.invalid", "ACTIVE", "BRANCH_MANAGER", null, null);
                    when(f.repository.findByUsername(account.username())).thenReturn(account);
                    TestResult result = expectAuthFailure(() -> f.service.login(account.username(),
                            PersonalAccountDemoData.CURRENT_PASSWORD, false), 403,
                            "Bạn không có quyền đăng nhập đối với cơ sở chưa được phân công.");
                    verify(f.repository, never()).createSession(any(), anyString());
                    return result;
                }));
        cases.add(test("PAM-015", "username có đúng 100 ký tự và tài khoản ACTIVE",
                "Chấp nhận biên 100 ký tự và đăng nhập thành công.", "Boundary", () -> {
                    String username = "u".repeat(100);
                    Account account = PersonalAccountDemoData.account(UUID.randomUUID(), username,
                            "boundary@example.invalid", "ACTIVE", "RECEPTIONIST",
                            PersonalAccountDemoData.HOA_BANG_BRANCH_ID, "GenZ Cinema Hoa Bằng");
                    Fixture f = successFixture(account);
                    LoginResult result = f.service.login(username, PersonalAccountDemoData.CURRENT_PASSWORD, false);
                    return check(username.equals(result.user().username()), "acceptedLength=" + username.length());
                }));
        cases.add(test("PAM-016", "username có 101 ký tự",
                "Từ chối 400 và hiển thị MSG03: Tên đăng nhập chỉ được chứa tối đa 100 ký tự.", "Boundary",
                () -> expectAuthFailure(() -> fixture().service.login("u".repeat(101),
                        PersonalAccountDemoData.CURRENT_PASSWORD, false), 400,
                        "Tên đăng nhập chỉ được chứa tối đa 100 ký tự.")));
        cases.add(test("PAM-017", "tài khoản ACTIVE nhưng role không thuộc bốn role được cấp",
                "Từ chối 403, không tạo phiên cho role không hợp lệ.", "Abnormal", () -> {
                    Account account = PersonalAccountDemoData.account(UUID.randomUUID(), "role.invalid",
                            "role.invalid@example.invalid", "ACTIVE", "UNKNOWN", null, null);
                    Fixture f = successFixture(account);
                    TestResult result = expectAuthFailure(() -> f.service.login(account.username(),
                            PersonalAccountDemoData.CURRENT_PASSWORD, false), 403,
                            "Tài khoản chưa được cấp vai trò hợp lệ");
                    verify(f.repository, never()).createSession(any(), anyString());
                    return result;
                }));
        cases.add(test("PAM-018", "tài khoản demo ACTIVE và thông tin đăng nhập hợp lệ",
                "Lưu thời điểm đăng nhập và chỉ lưu SHA-256 của token phiên, không lưu token gốc.", "Normal", () -> {
                    Fixture f = successFixture(PersonalAccountDemoData.receptionistAccount());
                    LoginResult result = f.service.login(PersonalAccountDemoData.USERNAME,
                            PersonalAccountDemoData.CURRENT_PASSWORD, false);
                    verify(f.repository).recordLogin(eq(PersonalAccountDemoData.RECEPTIONIST_ID), any());
                    verify(f.repository).createSession(PersonalAccountDemoData.RECEPTIONIST_ID,
                            AuthCrypto.hash(PersonalAccountDemoData.TOKEN));
                    return check(!AuthCrypto.hash(result.token()).equals(result.token()),
                            "rawTokenReturned=true, storedHash=" + AuthCrypto.hash(result.token()));
                }));
        return List.copyOf(cases);
    }

    private static ManualTest roleCase(String id, String role, UUID idValue, String username, String email,
            UUID branchId, String destination) {
        return test(id, "username=" + username + ", password demo hợp lệ, remember=false",
                "Đăng nhập thành công và điều hướng " + destination + " theo vai trò " + role + ".",
                "Normal", () -> {
                    Account account = PersonalAccountDemoData.account(idValue, username, email, "ACTIVE", role,
                            branchId, branchId == null ? null : "GenZ Cinema Hoa Bằng");
                    Fixture f = successFixture(account);
                    LoginResult result = f.service.login(username, PersonalAccountDemoData.CURRENT_PASSWORD, false);
                    return check(destination.equals(result.user().destination())
                                    && role.equals(result.user().role()),
                            "role=" + result.user().role() + ", destination=" + result.user().destination());
                });
    }

    private static ManualTest inactiveCase(String id, String status) {
        return test(id, "tài khoản demo có trạng thái " + status,
                "Từ chối 403 và hiển thị MSG33: Tài khoản đã bị vô hiệu hóa. Vui lòng liên hệ Quản lý cấp cao để được hỗ trợ.",
                "Abnormal", () -> {
                    Fixture f = fixture();
                    Account account = PersonalAccountDemoData.account(PersonalAccountDemoData.RECEPTIONIST_ID,
                            PersonalAccountDemoData.USERNAME, PersonalAccountDemoData.EMAIL, status,
                            "RECEPTIONIST", PersonalAccountDemoData.HOA_BANG_BRANCH_ID, "GenZ Cinema Hoa Bằng");
                    when(f.repository.findByUsername(account.username())).thenReturn(account);
                    return expectAuthFailure(() -> f.service.login(account.username(),
                            PersonalAccountDemoData.CURRENT_PASSWORD, false), 403,
                            "Tài khoản đã bị vô hiệu hóa. Vui lòng liên hệ Quản lý cấp cao để được hỗ trợ.");
                });
    }

    private static ManualTest test(String id, String input, String expected, String type,
            CheckedAction action) {
        return new ManualTest(id, UC, "AuthService.login", PRE, input, expected, type, NOTE, action);
    }

    private static Fixture successFixture(Account account) {
        Fixture fixture = fixture();
        when(fixture.repository.findByUsername(account.username())).thenReturn(account);
        when(fixture.crypto.matches(PersonalAccountDemoData.CURRENT_PASSWORD, account.hash())).thenReturn(true);
        return fixture;
    }

    private static Fixture fixture() {
        AuthRepository repository = mock(AuthRepository.class);
        AuthCrypto crypto = mock(AuthCrypto.class);
        RecoveryMailer mailer = mock(RecoveryMailer.class);
        when(crypto.token()).thenReturn(PersonalAccountDemoData.TOKEN);
        when(crypto.encode(anyString())).thenAnswer(invocation -> "encoded:" + invocation.getArgument(0));
        AuthService service = new AuthService(repository, crypto, mailer);
        return new Fixture(repository, crypto, service);
    }

    private record Fixture(AuthRepository repository, AuthCrypto crypto, AuthService service) {
    }
}


