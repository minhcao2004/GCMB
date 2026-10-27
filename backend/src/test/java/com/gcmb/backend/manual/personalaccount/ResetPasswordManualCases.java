/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 27/10/2026
 * Description: Manual unit test cases for UC-02 Reset Password.
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gcmb.backend.auth.dto.Recovery;
import com.gcmb.backend.auth.exception.AuthErrorsProbe;
import com.gcmb.backend.auth.repository.AuthRepository;
import com.gcmb.backend.auth.repository.model.Account;
import com.gcmb.backend.auth.repository.model.Reset;
import com.gcmb.backend.auth.security.AuthCrypto;
import com.gcmb.backend.auth.service.AuthService;
import com.gcmb.backend.auth.service.RecoveryMailer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ResetPasswordManualCases {
    private static final String UC = "UC-02 Reset Password";
    private static final String PRE = "Tài khoản demo tồn tại, ACTIVE và đã có email khôi phục trong V14/V15; người dùng ở màn hình Quên mật khẩu.";
    private static final String NOTE = "Nguồn: UC-02_Reset_Password.docx; BR-111, BR-113, BR-114, BR-120, BR-126; MSG02, MSG03, MSG05, MSG06, MSG09, MSG25, MSG29, MSG31, MSG32, MSG33; Flyway V14/V15.";
    private static final String MSG29 = "Mật khẩu phải dài 8–64 ký tự và bao gồm chữ hoa, chữ thường, chữ số và ký tự đặc biệt, không chứa khoảng trắng.";

    private ResetPasswordManualCases() {
    }

    public static List<ManualTest> cases() {
        List<ManualTest> cases = new ArrayList<>();
        cases.add(test("PAM-046", "email='  LETAN.HOABANG@EXAMPLE.INVALID  '",
                "Chuẩn hóa email, vô hiệu mã cũ, lưu challenge/code dạng hash, gửi mã và trả challenge 43 ký tự; UI dùng MSG09.",
                "Normal", () -> {
                    Fixture f = fixture();
                    Recovery recovery = f.service.sendCode("  LETAN.HOABANG@EXAMPLE.INVALID  ");
                    verify(f.repository).revokePendingResets(eq(PersonalAccountDemoData.RECEPTIONIST_ID), any());
                    verify(f.repository).createReset(eq(PersonalAccountDemoData.RECEPTIONIST_ID),
                            eq(AuthCrypto.hash(PersonalAccountDemoData.CHALLENGE)), eq("encoded:" +
                                    PersonalAccountDemoData.EMAIL + ":" + PersonalAccountDemoData.CODE), any());
                    verify(f.mailer).send(PersonalAccountDemoData.EMAIL, PersonalAccountDemoData.CODE);
                    return check(PersonalAccountDemoData.EMAIL.equals(recovery.email())
                                    && recovery.challenge().length() == 43,
                            "email=" + recovery.email() + ", challengeLength=" + recovery.challenge().length());
                }, "AuthService.sendCode"));
        cases.add(emailInvalidCase("PAM-047", null, "email=null", "MSG02: Vui lòng bổ sung Email."));
        cases.add(emailInvalidCase("PAM-048", "   ", "email chỉ gồm khoảng trắng", "MSG02: Vui lòng bổ sung Email."));
        cases.add(emailInvalidCase("PAM-049", "abc", "email thiếu @ và tên miền", "MSG05: Nhập địa chỉ email theo dạng ten@tenmien.com."));
        cases.add(emailInvalidCase("PAM-050", "a..b@example.com", "email có hai dấu chấm liên tiếp", "MSG05: Nhập địa chỉ email theo dạng ten@tenmien.com."));
        cases.add(emailInvalidCase("PAM-051", "a".repeat(65) + "@example.com",
                "local-part 65 ký tự", "MSG05: Nhập địa chỉ email theo dạng ten@tenmien.com."));
        cases.add(test("PAM-052", "email dài 255 ký tự",
                "Từ chối 400 và hiển thị MSG03: Email chỉ được chứa tối đa 254 ký tự.", "Boundary",
                () -> expectAuthFailure(() -> fixture().service.sendCode("a@" + "b".repeat(249) + ".com"),
                        400, "Email chỉ được chứa tối đa 254 ký tự."),
                "AuthService.sendCode/AuthValidation.email"));
        cases.add(test("PAM-053", "email đúng định dạng nhưng không tồn tại",
                "Từ chối 404, không tạo hoặc gửi mã.", "Abnormal", () -> {
                    Fixture f = fixture();
                    when(f.repository.findByEmail("unknown@example.com")).thenReturn(null);
                    TestResult result = expectAuthFailure(() -> f.service.sendCode("unknown@example.com"),
                            404, "Email không tồn tại trong hệ thống");
                    verify(f.repository, never()).createReset(any(), anyString(), anyString(), any());
                    return result;
                }, "AuthService.sendCode"));
        cases.add(test("PAM-054", "email thuộc tài khoản INACTIVE",
                "Từ chối 403 và hiển thị đúng MSG33; không gửi mã.", "Abnormal", () -> {
                    Fixture f = fixture();
                    Account inactive = PersonalAccountDemoData.account(PersonalAccountDemoData.RECEPTIONIST_ID,
                            PersonalAccountDemoData.USERNAME, PersonalAccountDemoData.EMAIL, "INACTIVE",
                            "RECEPTIONIST", PersonalAccountDemoData.HOA_BANG_BRANCH_ID, "GenZ Cinema Hoa Bằng");
                    when(f.repository.findByEmail(PersonalAccountDemoData.EMAIL)).thenReturn(inactive);
                    return expectAuthFailure(() -> f.service.sendCode(PersonalAccountDemoData.EMAIL), 403,
                            "Tài khoản đã bị vô hiệu hóa. Vui lòng liên hệ Quản lý cấp cao để được hỗ trợ.");
                }, "AuthService.sendCode"));
        cases.add(test("PAM-055", "SMTP không sẵn sàng khi gửi mã",
                "Hoàn tác giao dịch và trả thông báo dịch vụ ngoài theo MSG25.", "Abnormal", () -> {
                    Fixture f = fixture();
                    doThrow(new RecoveryMailer.MailUnavailable()).when(f.mailer)
                            .send(PersonalAccountDemoData.EMAIL, PersonalAccountDemoData.CODE);
                    try {
                        f.service.sendCode(PersonalAccountDemoData.EMAIL);
                        return check(false, "No MailUnavailable was thrown");
                    } catch (RecoveryMailer.MailUnavailable expected) {
                        var response = AuthErrorsProbe.mailUnavailable();
                        String actual = AuthErrorsProbe.message(response);
                        String documented = "Chưa nhận được phản hồi từ dịch vụ email. Nếu đang thanh toán, hãy kiểm tra trạng thái thanh toán trước khi thực hiện lại.";
                        return check(response.getStatusCode().value() == 503 && documented.equals(actual),
                                "status=" + response.getStatusCode().value() + ", message=" + actual);
                    }
                }, "AuthService.sendCode/RecoveryMailer.send/AuthErrors.mail"));
        cases.add(test("PAM-056", "email, challenge, code=123456, password=Moi@1234 hợp lệ",
                "Đặt lại mật khẩu thành công; code dùng một lần; toàn bộ phiên bị thu hồi.", "Normal", () -> {
                    Fixture f = fixture();
                    f.service.reset(PersonalAccountDemoData.EMAIL, PersonalAccountDemoData.CHALLENGE,
                            PersonalAccountDemoData.CODE, PersonalAccountDemoData.NEW_PASSWORD,
                            PersonalAccountDemoData.NEW_PASSWORD);
                    verify(f.repository).changePassword(eq(PersonalAccountDemoData.RECEPTIONIST_ID),
                            eq("encoded:" + PersonalAccountDemoData.NEW_PASSWORD), any());
                    verify(f.repository).consumeReset(eq(f.reset.id()), any());
                    verify(f.repository).revokeTokens(eq(PersonalAccountDemoData.RECEPTIONIST_ID), any());
                    return check(true, "password changed, reset consumed, sessions revoked");
                }, "AuthService.reset"));
        cases.add(test("PAM-057", "code=null",
                "Từ chối 400 theo MSG02 cho Mã xác minh.", "Abnormal",
                () -> expectAuthFailure(() -> fixture().service.reset(PersonalAccountDemoData.EMAIL,
                        PersonalAccountDemoData.CHALLENGE, null, PersonalAccountDemoData.NEW_PASSWORD,
                        PersonalAccountDemoData.NEW_PASSWORD), 400, "Vui lòng bổ sung Mã xác minh."),
                "AuthService.reset/AuthValidation.code"));
        cases.add(codeFormatCase("PAM-058", "12345", "5 chữ số"));
        cases.add(codeFormatCase("PAM-059", "1234567", "7 chữ số"));
        cases.add(codeFormatCase("PAM-060", "１２３４５６", "6 chữ số Unicode"));
        cases.add(codeFormatCase("PAM-061", "12A456", "chứa chữ cái"));
        cases.add(test("PAM-062", "challenge dài 42 ký tự",
                "Từ chối 400 vì yêu cầu khôi phục không hợp lệ.", "Boundary",
                () -> expectAuthFailure(() -> fixture().service.reset(PersonalAccountDemoData.EMAIL,
                        "a".repeat(42), PersonalAccountDemoData.CODE, PersonalAccountDemoData.NEW_PASSWORD,
                        PersonalAccountDemoData.NEW_PASSWORD), 400,
                        "Yêu cầu khôi phục không hợp lệ. Vui lòng gửi lại mã"), "AuthService.reset"));
        cases.add(test("PAM-063", "challenge/code không khớp yêu cầu đã phát",
                "Từ chối 400 và hiển thị đúng MSG31.", "Abnormal", () -> {
                    Fixture f = fixture();
                    when(f.repository.findReset(any(), anyString())).thenReturn(null);
                    return expectAuthFailure(() -> f.service.reset(PersonalAccountDemoData.EMAIL,
                            PersonalAccountDemoData.CHALLENGE, PersonalAccountDemoData.CODE,
                            PersonalAccountDemoData.NEW_PASSWORD, PersonalAccountDemoData.NEW_PASSWORD), 400,
                            "Mã xác minh không đúng. Vui lòng kiểm tra mã được gửi đến email của bạn.");
                }, "AuthService.reset"));
        cases.add(test("PAM-064", "code đã được dùng trước đó",
                "Từ chối 400; code không được tái sử dụng.", "Abnormal", () -> {
                    Fixture f = fixture();
                    when(f.repository.findReset(any(), anyString())).thenReturn(new Reset(f.reset.id(),
                            f.reset.hash(), Instant.now().minusSeconds(60), null));
                    return expectAuthFailure(() -> f.service.reset(PersonalAccountDemoData.EMAIL,
                            PersonalAccountDemoData.CHALLENGE, PersonalAccountDemoData.CODE,
                            PersonalAccountDemoData.NEW_PASSWORD, PersonalAccountDemoData.NEW_PASSWORD), 400,
                            "Mã xác nhận đã được sử dụng");
                }, "AuthService.reset"));
        cases.add(test("PAM-065", "code đã bị thu hồi bởi yêu cầu mới",
                "Từ chối 400 và hiển thị đúng MSG32.", "Abnormal", () -> {
                    Fixture f = fixture();
                    when(f.repository.findReset(any(), anyString())).thenReturn(new Reset(f.reset.id(),
                            f.reset.hash(), null, Instant.now().minusSeconds(60)));
                    return expectAuthFailure(() -> f.service.reset(PersonalAccountDemoData.EMAIL,
                            PersonalAccountDemoData.CHALLENGE, PersonalAccountDemoData.CODE,
                            PersonalAccountDemoData.NEW_PASSWORD, PersonalAccountDemoData.NEW_PASSWORD), 400,
                            "Mã xác minh đã hết hiệu lực. Vui lòng yêu cầu mã mới để tiếp tục.");
                }, "AuthService.reset"));
        cases.add(test("PAM-066", "code đúng nhưng đã phát quá 10 phút",
                "Từ chối 400 và hiển thị đúng MSG32 theo BR-126; không đổi mật khẩu.", "Boundary", () -> {
                    Fixture f = fixture();
                    TestResult result = expectAuthFailure(() -> f.service.reset(PersonalAccountDemoData.EMAIL,
                            PersonalAccountDemoData.CHALLENGE, PersonalAccountDemoData.CODE,
                            PersonalAccountDemoData.NEW_PASSWORD, PersonalAccountDemoData.NEW_PASSWORD), 400,
                            "Mã xác minh đã hết hiệu lực. Vui lòng yêu cầu mã mới để tiếp tục.");
                    return result;
                }, "AuthService.reset/AuthRepository.findReset"));
        cases.add(test("PAM-067", "code 654321 không khớp code đã phát",
                "Từ chối 400 và hiển thị đúng MSG31.", "Abnormal", () -> {
                    Fixture f = fixture();
                    when(f.crypto.matches(PersonalAccountDemoData.EMAIL + ":654321", f.reset.hash()))
                            .thenReturn(false);
                    return expectAuthFailure(() -> f.service.reset(PersonalAccountDemoData.EMAIL,
                            PersonalAccountDemoData.CHALLENGE, "654321", PersonalAccountDemoData.NEW_PASSWORD,
                            PersonalAccountDemoData.NEW_PASSWORD), 400,
                            "Mã xác minh không đúng. Vui lòng kiểm tra mã được gửi đến email của bạn.");
                }, "AuthService.reset"));
        cases.add(test("PAM-068", "password=Moi@1234, confirmation=Khac@1234",
                "Từ chối 400 và hiển thị đúng MSG06.", "Abnormal",
                () -> expectAuthFailure(() -> fixture().service.reset(PersonalAccountDemoData.EMAIL,
                        PersonalAccountDemoData.CHALLENGE, PersonalAccountDemoData.CODE,
                        PersonalAccountDemoData.NEW_PASSWORD, "Khac@1234"), 400,
                        "Mật khẩu xác nhận không khớp với mật khẩu mới."), "AuthService.reset/AuthValidation.password"));
        cases.add(resetPasswordPolicyCase("PAM-069", "Aa1@bbbb", true, "đúng 8 ký tự", "Boundary"));
        cases.add(resetPasswordPolicyCase("PAM-070", "Aa1@" + "b".repeat(60), true, "đúng 64 ký tự", "Boundary"));
        cases.add(resetPasswordPolicyCase("PAM-071", "Aa1@bbb", false, "7 ký tự", "Boundary"));
        cases.add(resetPasswordPolicyCase("PAM-072", "Aa1@" + "b".repeat(61), false, "65 ký tự", "Boundary"));
        cases.add(resetPasswordPolicyCase("PAM-073", "aa1@bbbb", false, "thiếu chữ hoa", "Abnormal"));
        cases.add(resetPasswordPolicyCase("PAM-074", "AA1@BBBB", false, "thiếu chữ thường", "Abnormal"));
        cases.add(resetPasswordPolicyCase("PAM-075", "Aa@@bbbb", false, "thiếu chữ số", "Abnormal"));
        cases.add(resetPasswordPolicyCase("PAM-076", "Aa11bbbb", false, "thiếu ký tự đặc biệt", "Abnormal"));
        cases.add(resetPasswordPolicyCase("PAM-077", "Aa1@ bb8", false, "chứa khoảng trắng", "Abnormal"));
        return List.copyOf(cases);
    }

    private static ManualTest emailInvalidCase(String id, String email, String description, String expectedMessage) {
        String exact = expectedMessage.startsWith("MSG02") ? "Vui lòng bổ sung Email."
                : "Nhập địa chỉ email theo dạng ten@tenmien.com.";
        return test(id, description, "Từ chối 400 và hiển thị " + expectedMessage,
                description.contains("vượt") || description.contains("65") ? "Boundary" : "Abnormal",
                () -> expectAuthFailure(() -> fixture().service.sendCode(email), 400, exact),
                "AuthService.sendCode/AuthValidation.email");
    }

    private static ManualTest codeFormatCase(String id, String code, String description) {
        return test(id, "code=" + code + " (" + description + ")",
                "Từ chối 400 vì mã xác minh phải đúng 6 chữ số ASCII.", "Boundary",
                () -> expectAuthFailure(() -> fixture().service.reset(PersonalAccountDemoData.EMAIL,
                        PersonalAccountDemoData.CHALLENGE, code, PersonalAccountDemoData.NEW_PASSWORD,
                        PersonalAccountDemoData.NEW_PASSWORD), 400,
                        "Mã xác nhận phải gồm đúng 6 chữ số từ 0–9"),
                "AuthService.reset/AuthValidation.code");
    }

    private static ManualTest resetPasswordPolicyCase(String id, String password, boolean valid,
            String description, String type) {
        return test(id, "password=" + password + " (" + description + ")",
                valid ? "Chấp nhận theo BR-114 và đặt lại mật khẩu thành công."
                        : "Từ chối 400 và hiển thị đúng MSG29; không tự rút gọn mật khẩu.",
                type, () -> {
                    Fixture f = fixture();
                    if (valid) {
                        f.service.reset(PersonalAccountDemoData.EMAIL, PersonalAccountDemoData.CHALLENGE,
                                PersonalAccountDemoData.CODE, password, password);
                        return check(true, "acceptedLength=" + password.length());
                    }
                    return expectAuthFailure(() -> f.service.reset(PersonalAccountDemoData.EMAIL,
                            PersonalAccountDemoData.CHALLENGE, PersonalAccountDemoData.CODE, password, password),
                            400, MSG29);
                }, "AuthService.reset/AuthValidation.password");
    }

    private static ManualTest test(String id, String input, String expected, String type,
            CheckedAction action, String sourceMethod) {
        return new ManualTest(id, UC, sourceMethod, PRE, input, expected, type, NOTE, action);
    }

    private static Fixture fixture() {
        AuthRepository repository = mock(AuthRepository.class);
        AuthCrypto crypto = mock(AuthCrypto.class);
        RecoveryMailer mailer = mock(RecoveryMailer.class);
        Account account = PersonalAccountDemoData.receptionistAccount();
        Reset reset = new Reset(UUID.fromString("47570000-0000-4000-8000-000000000031"),
                "reset-code-hash", null, null);
        when(crypto.token()).thenReturn(PersonalAccountDemoData.CHALLENGE);
        when(crypto.code()).thenReturn(PersonalAccountDemoData.CODE);
        when(crypto.encode(anyString())).thenAnswer(invocation -> "encoded:" + invocation.getArgument(0));
        when(repository.findByEmail(PersonalAccountDemoData.EMAIL)).thenReturn(account);
        when(repository.findReset(eq(account.id()), eq(AuthCrypto.hash(PersonalAccountDemoData.CHALLENGE))))
                .thenReturn(reset);
        when(crypto.matches(PersonalAccountDemoData.EMAIL + ":" + PersonalAccountDemoData.CODE, reset.hash()))
                .thenReturn(true);
        AuthService service = new AuthService(repository, crypto, mailer);
        return new Fixture(repository, crypto, mailer, service, reset);
    }

    private record Fixture(AuthRepository repository, AuthCrypto crypto, RecoveryMailer mailer,
            AuthService service, Reset reset) {
    }
}


