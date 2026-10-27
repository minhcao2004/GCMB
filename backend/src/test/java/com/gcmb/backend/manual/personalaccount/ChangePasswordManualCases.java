/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 27/10/2026
 * Description: Manual unit test cases for UC-01 Change Password.
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

import com.gcmb.backend.accounts.dto.AccountProfileDtos.Profile;
import com.gcmb.backend.accounts.repository.AccountProfileRepository;
import com.gcmb.backend.accounts.service.AccountProfileService;
import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.repository.AuthRepository;
import com.gcmb.backend.auth.repository.model.Account;
import com.gcmb.backend.auth.security.AuthCrypto;
import com.gcmb.backend.auth.service.AuthService;
import com.gcmb.backend.auth.service.RecoveryMailer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;

public final class ChangePasswordManualCases {
    private static final String UC = "UC-01 Change Password";
    private static final String PRE = "Người dùng đã đăng nhập bằng tài khoản demo ACTIVE trong V14/V15 và đang xem hồ sơ của chính mình.";
    private static final String NOTE = "Nguồn: UC-01_Change_Password.docx; BR-111, BR-114, BR-120, BR-121, BR-130; MSG02, MSG06, MSG28, MSG29, MSG30, MSG33; Flyway V14/V15.";
    private static final String MSG29 = "Mật khẩu phải dài 8–64 ký tự và bao gồm chữ hoa, chữ thường, chữ số và ký tự đặc biệt, không chứa khoảng trắng.";

    private ChangePasswordManualCases() {
    }

    public static List<ManualTest> cases() {
        List<ManualTest> cases = new ArrayList<>();
        cases.add(test("PAM-027", "actorId=47570000-0000-4000-8000-000000000001",
                "Hiển thị đúng hồ sơ tài khoản đang đăng nhập; không làm thay đổi dữ liệu.", "Normal", () -> {
                    AccountProfileRepository repository = mock(AccountProfileRepository.class);
                    AuthCrypto crypto = mock(AuthCrypto.class);
                    Profile profile = PersonalAccountDemoData.linkedProfile();
                    when(repository.find(PersonalAccountDemoData.RECEPTIONIST_ID)).thenReturn(profile);
                    Profile actual = new AccountProfileService(repository, crypto)
                            .view(PersonalAccountDemoData.RECEPTIONIST_ID);
                    return check(actual.equals(profile), "profile=" + actual);
                }, "AccountProfileService.view"));
        cases.add(test("PAM-028", "actorId không tồn tại",
                "Từ chối 404; không hiển thị hồ sơ của tài khoản khác.", "Abnormal", () -> {
                    AccountProfileRepository repository = mock(AccountProfileRepository.class);
                    when(repository.find(any())).thenThrow(new EmptyResultDataAccessException(1));
                    return expectAuthFailure(() -> new AccountProfileService(repository, mock(AuthCrypto.class))
                            .view(UUID.randomUUID()), 404, "Tài khoản không tồn tại");
                }, "AccountProfileService.view"));
        cases.add(test("PAM-029", "tài khoản của actor có trạng thái INACTIVE",
                "Từ chối 403 và hiển thị đúng MSG33.", "Abnormal", () -> {
                    AccountProfileRepository repository = mock(AccountProfileRepository.class);
                    Profile profile = inactiveProfile();
                    when(repository.find(PersonalAccountDemoData.RECEPTIONIST_ID)).thenReturn(profile);
                    return expectAuthFailure(() -> new AccountProfileService(repository, mock(AuthCrypto.class))
                            .view(PersonalAccountDemoData.RECEPTIONIST_ID), 403,
                            "Tài khoản đã bị vô hiệu hóa. Vui lòng liên hệ Quản lý cấp cao để được hỗ trợ.");
                }, "AccountProfileService.view"));
        cases.add(test("PAM-030", "currentPassword=Demo@123, password=Moi@1234, confirmation=Moi@1234",
                "Đổi mật khẩu thành công; băm mật khẩu mới và vô hiệu mã khôi phục đang chờ.", "Normal", () -> {
                    Fixture f = fixture(PersonalAccountDemoData.receptionistAccount());
                    f.service.changePassword(PersonalAccountDemoData.receptionistIdentity(),
                            PersonalAccountDemoData.CURRENT_PASSWORD, PersonalAccountDemoData.NEW_PASSWORD,
                            PersonalAccountDemoData.NEW_PASSWORD);
                    verify(f.repository).changePassword(eq(PersonalAccountDemoData.RECEPTIONIST_ID),
                            eq("encoded:" + PersonalAccountDemoData.NEW_PASSWORD), any());
                    verify(f.repository).revokePendingResets(eq(PersonalAccountDemoData.RECEPTIONIST_ID), any());
                    return check(true, "password updated and pending reset codes revoked");
                }, "AuthService.changePassword"));
        cases.add(test("PAM-031", "currentPassword=null",
                "Từ chối 400 và hiển thị MSG02: Vui lòng bổ sung Mật khẩu hiện tại.", "Abnormal",
                () -> expectAuthFailure(() -> fixture(PersonalAccountDemoData.receptionistAccount()).service
                        .changePassword(PersonalAccountDemoData.receptionistIdentity(), null,
                                PersonalAccountDemoData.NEW_PASSWORD, PersonalAccountDemoData.NEW_PASSWORD),
                        400, "Vui lòng bổ sung Mật khẩu hiện tại."), "AuthService.changePassword"));
        cases.add(test("PAM-032", "currentPassword=Sai@1234",
                "Từ chối 400 và hiển thị đúng MSG28.", "Abnormal", () -> {
                    Fixture f = fixture(PersonalAccountDemoData.receptionistAccount());
                    when(f.crypto.matches("Sai@1234", PersonalAccountDemoData.PASSWORD_HASH)).thenReturn(false);
                    return expectAuthFailure(() -> f.service.changePassword(PersonalAccountDemoData.receptionistIdentity(),
                            "Sai@1234", PersonalAccountDemoData.NEW_PASSWORD,
                            PersonalAccountDemoData.NEW_PASSWORD), 400,
                            "Mật khẩu hiện tại không đúng. Vui lòng nhập lại.");
                }, "AuthService.changePassword"));
        cases.add(test("PAM-033", "password=Moi@1234, confirmation=Khac@1234",
                "Từ chối 400 và hiển thị đúng MSG06.", "Abnormal",
                () -> expectAuthFailure(() -> fixture(PersonalAccountDemoData.receptionistAccount()).service
                        .changePassword(PersonalAccountDemoData.receptionistIdentity(),
                                PersonalAccountDemoData.CURRENT_PASSWORD, PersonalAccountDemoData.NEW_PASSWORD,
                                "Khac@1234"), 400,
                        "Mật khẩu xác nhận không khớp với mật khẩu mới."), "AuthService.changePassword"));
        cases.add(test("PAM-034", "password=Demo@123 trùng mật khẩu hiện tại",
                "Từ chối 400 và hiển thị đúng MSG30.", "Abnormal", () -> {
                    Fixture f = fixture(PersonalAccountDemoData.receptionistAccount());
                    when(f.crypto.matches(PersonalAccountDemoData.CURRENT_PASSWORD,
                            PersonalAccountDemoData.PASSWORD_HASH)).thenReturn(true);
                    return expectAuthFailure(() -> f.service.changePassword(PersonalAccountDemoData.receptionistIdentity(),
                            PersonalAccountDemoData.CURRENT_PASSWORD, PersonalAccountDemoData.CURRENT_PASSWORD,
                            PersonalAccountDemoData.CURRENT_PASSWORD), 400,
                            "Mật khẩu mới phải khác mật khẩu hiện tại.");
                }, "AuthService.changePassword"));
        cases.add(passwordPolicyCase("PAM-035", "Aa1@bbbb", "mật khẩu đúng 8 ký tự", true, "Boundary"));
        cases.add(passwordPolicyCase("PAM-036", "Aa1@" + "b".repeat(60), "mật khẩu đúng 64 ký tự", true, "Boundary"));
        cases.add(passwordPolicyCase("PAM-037", "Aa1@bbb", "mật khẩu 7 ký tự", false, "Boundary"));
        cases.add(passwordPolicyCase("PAM-038", "Aa1@" + "b".repeat(61), "mật khẩu 65 ký tự", false, "Boundary"));
        cases.add(passwordPolicyCase("PAM-039", "aa1@bbbb", "thiếu chữ hoa", false, "Abnormal"));
        cases.add(passwordPolicyCase("PAM-040", "AA1@BBBB", "thiếu chữ thường", false, "Abnormal"));
        cases.add(passwordPolicyCase("PAM-041", "Aa@@bbbb", "thiếu chữ số", false, "Abnormal"));
        cases.add(passwordPolicyCase("PAM-042", "Aa11bbbb", "thiếu ký tự đặc biệt", false, "Abnormal"));
        cases.add(passwordPolicyCase("PAM-043", "Aa1@ bb8", "có khoảng trắng", false, "Abnormal"));
        cases.add(test("PAM-044", "identity.id không khớp tài khoản của identity.username",
                "Từ chối 401 và yêu cầu đăng nhập lại; không đổi mật khẩu.", "Abnormal", () -> {
                    Fixture f = fixture(PersonalAccountDemoData.receptionistAccount());
                    Identity forged = new Identity(UUID.randomUUID(), PersonalAccountDemoData.USERNAME,
                            "RECEPTIONIST", "/le-tan", PersonalAccountDemoData.HOA_BANG_BRANCH_ID,
                            "GenZ Cinema Hoa Bằng");
                    TestResult result = expectAuthFailure(() -> f.service.changePassword(forged,
                            PersonalAccountDemoData.CURRENT_PASSWORD, PersonalAccountDemoData.NEW_PASSWORD,
                            PersonalAccountDemoData.NEW_PASSWORD), 401, "Vui lòng đăng nhập lại");
                    verify(f.repository, never()).changePassword(any(), anyString(), any());
                    return result;
                }, "AuthService.changePassword"));
        cases.add(test("PAM-045", "tài khoản chuyển sang INACTIVE trước khi lưu",
                "Từ chối 403 theo MSG33 và không đổi mật khẩu.", "Abnormal", () -> {
                    Account account = PersonalAccountDemoData.account(PersonalAccountDemoData.RECEPTIONIST_ID,
                            PersonalAccountDemoData.USERNAME, PersonalAccountDemoData.EMAIL, "INACTIVE",
                            "RECEPTIONIST", PersonalAccountDemoData.HOA_BANG_BRANCH_ID, "GenZ Cinema Hoa Bằng");
                    Fixture f = fixture(account);
                    TestResult result = expectAuthFailure(() -> f.service.changePassword(
                            PersonalAccountDemoData.receptionistIdentity(), PersonalAccountDemoData.CURRENT_PASSWORD,
                            PersonalAccountDemoData.NEW_PASSWORD, PersonalAccountDemoData.NEW_PASSWORD), 403,
                            "Tài khoản đã bị vô hiệu hóa. Vui lòng liên hệ Quản lý cấp cao để được hỗ trợ.");
                    verify(f.repository, never()).changePassword(any(), anyString(), any());
                    return result;
                }, "AuthService.changePassword"));
        return List.copyOf(cases);
    }

    private static ManualTest passwordPolicyCase(String id, String password, String description,
            boolean valid, String type) {
        String expected = valid ? "Chấp nhận theo BR-114 và đổi mật khẩu thành công."
                : "Từ chối 400 và hiển thị đúng MSG29; không tự rút gọn mật khẩu.";
        return test(id, description + ": password=" + password, expected, type, () -> {
            Fixture f = fixture(PersonalAccountDemoData.receptionistAccount());
            if (valid) {
                f.service.changePassword(PersonalAccountDemoData.receptionistIdentity(),
                        PersonalAccountDemoData.CURRENT_PASSWORD, password, password);
                verify(f.repository).changePassword(eq(PersonalAccountDemoData.RECEPTIONIST_ID), anyString(), any());
                return check(true, "acceptedLength=" + password.length());
            }
            TestResult result = expectAuthFailure(() -> f.service.changePassword(
                    PersonalAccountDemoData.receptionistIdentity(), PersonalAccountDemoData.CURRENT_PASSWORD,
                    password, password), 400, MSG29);
            return result;
        }, "AuthService.changePassword/AuthValidation.password");
    }

    private static ManualTest test(String id, String input, String expected, String type,
            CheckedAction action, String sourceMethod) {
        return new ManualTest(id, UC, sourceMethod, PRE, input, expected, type, NOTE, action);
    }

    private static Fixture fixture(Account account) {
        AuthRepository repository = mock(AuthRepository.class);
        AuthCrypto crypto = mock(AuthCrypto.class);
        when(crypto.token()).thenReturn(PersonalAccountDemoData.TOKEN);
        when(crypto.encode(anyString())).thenAnswer(invocation -> "encoded:" + invocation.getArgument(0));
        when(repository.findByUsername(account.username())).thenReturn(account);
        when(crypto.matches(PersonalAccountDemoData.CURRENT_PASSWORD, account.hash())).thenReturn(true);
        when(crypto.matches(PersonalAccountDemoData.NEW_PASSWORD, account.hash())).thenReturn(false);
        AuthService service = new AuthService(repository, crypto, mock(RecoveryMailer.class));
        return new Fixture(repository, crypto, service);
    }

    private static Profile inactiveProfile() {
        Profile active = PersonalAccountDemoData.linkedProfile();
        var a = active.account();
        return new Profile(new com.gcmb.backend.accounts.dto.AccountDtos.AccountView(a.id(), a.employeeName(),
                a.employeeCode(), a.username(), a.email(), a.role(), a.branchId(), a.branchName(), "INACTIVE"),
                active.fullName(), active.phone(), active.linkedEmployee());
    }

    private record Fixture(AuthRepository repository, AuthCrypto crypto, AuthService service) {
    }
}


