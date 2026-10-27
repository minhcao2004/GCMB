/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 27/10/2026
 * Description: Manual unit test cases for UC-03 Update Account Information.
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

import com.gcmb.backend.accounts.dto.AccountDtos.AccountView;
import com.gcmb.backend.accounts.dto.AccountProfileDtos.Profile;
import com.gcmb.backend.accounts.dto.AccountProfileDtos.UpdateProfile;
import com.gcmb.backend.accounts.repository.AccountProfileRepository;
import com.gcmb.backend.accounts.service.AccountProfileService;
import com.gcmb.backend.auth.security.AuthCrypto;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;

public final class UpdateAccountInformationManualCases {
    private static final String UC = "UC-03 Update Account Information";
    private static final String PRE = "Người dùng đã đăng nhập bằng tài khoản demo ACTIVE trong V14/V15 và mở hồ sơ của chính mình.";
    private static final String NOTE = "Nguồn: UC-03_Update_Account_Information.docx; BR-111, BR-113, BR-119, BR-130, BR-134; MSG02, MSG03, MSG04, MSG05, MSG11, MSG28, MSG35; Flyway V14/V15.";
    private static final String MSG04 = "Số điện thoại chỉ được chứa chữ số và có thể có một dấu + ở đầu.";

    private UpdateAccountInformationManualCases() {
    }

    public static List<ManualTest> cases() {
        List<ManualTest> cases = new ArrayList<>();
        cases.add(test("PAM-080", "fullName='Lễ tân Hoa Bằng', phone='0877155379', email giữ nguyên",
                "Cập nhật họ tên/số điện thoại, giữ role và branch, ghi lịch sử; UI hiển thị MSG11.", "Normal", () -> {
                    Fixture f = linkedFixture(profile("Lễ tân Hoa Bằng", "0877155379",
                            PersonalAccountDemoData.EMAIL));
                    Profile actual = f.service.update(PersonalAccountDemoData.RECEPTIONIST_ID,
                            new UpdateProfile("Lễ tân Hoa Bằng", "0877155379",
                                    PersonalAccountDemoData.EMAIL, null));
                    verify(f.repository).update(PersonalAccountDemoData.RECEPTIONIST_ID,
                            "Lễ tân Hoa Bằng", "0877155379", PersonalAccountDemoData.EMAIL, false);
                    verify(f.repository).audit(f.before, actual);
                    return check(sameAccess(f.before, actual), "updated=" + actual + ", accessScopeUnchanged=true");
                }, "AccountProfileService.update"));
        cases.add(test("PAM-081", "email='  NEW.EMAIL@EXAMPLE.COM  ', currentPassword đúng",
                "Chuẩn hóa email thành chữ thường, xác nhận mật khẩu hiện tại, cập nhật và thu hồi mã khôi phục cũ.",
                "Normal", () -> {
                    Fixture f = linkedFixture(profile(fName(), phone(), "new.email@example.com"));
                    when(f.crypto.matches(PersonalAccountDemoData.CURRENT_PASSWORD,
                            PersonalAccountDemoData.PASSWORD_HASH)).thenReturn(true);
                    Profile actual = f.service.update(PersonalAccountDemoData.RECEPTIONIST_ID,
                            new UpdateProfile(fName(), phone(), "  NEW.EMAIL@EXAMPLE.COM  ",
                                    PersonalAccountDemoData.CURRENT_PASSWORD));
                    verify(f.repository).update(PersonalAccountDemoData.RECEPTIONIST_ID,
                            fName(), phone(), "new.email@example.com", true);
                    return check("new.email@example.com".equals(actual.account().email()),
                            "email=" + actual.account().email());
                }, "AccountProfileService.update/AuthValidation.email"));
        cases.add(test("PAM-082", "dữ liệu nhập giống hoàn toàn dữ liệu hiện tại",
                "Không cập nhật database và không tạo lịch sử mới.", "Normal", () -> {
                    Fixture f = linkedFixture(PersonalAccountDemoData.linkedProfile());
                    Profile actual = f.service.update(PersonalAccountDemoData.RECEPTIONIST_ID,
                            new UpdateProfile(fName(), phone(), PersonalAccountDemoData.EMAIL, null));
                    verify(f.repository, never()).update(any(), any(), any(), anyString(), eq(false));
                    verify(f.repository, never()).audit(any(), any());
                    return check(actual.equals(f.before), "no-op returned original profile");
                }, "AccountProfileService.update"));
        cases.add(test("PAM-083", "fullName=null với tài khoản đã liên kết nhân viên",
                "Từ chối 400 và hiển thị MSG02: Vui lòng bổ sung Họ tên.", "Abnormal", () -> {
                    Fixture f = linkedFixture(PersonalAccountDemoData.linkedProfile());
                    return expectAuthFailure(() -> f.service.update(PersonalAccountDemoData.RECEPTIONIST_ID,
                            new UpdateProfile(null, phone(), PersonalAccountDemoData.EMAIL, null)), 400,
                            "Vui lòng bổ sung Họ tên.");
                }, "AccountProfileService.update/AuthValidation.required"));
        cases.add(fullNameBoundary("PAM-084", "N".repeat(200), true));
        cases.add(fullNameBoundary("PAM-085", "N".repeat(201), false));
        cases.add(phoneCase("PAM-086", "1234567", true, "7 chữ số", "Boundary"));
        cases.add(phoneCase("PAM-087", "+84987654321", true, "một dấu + ở đầu", "Normal"));
        cases.add(phoneCase("PAM-088", "", true, "để trống nên lưu null", "Normal"));
        cases.add(phoneCase("PAM-089", "123 456 789", false, "chứa khoảng trắng", "Abnormal"));
        cases.add(phoneCase("PAM-090", "123-456-789", false, "chứa dấu gạch ngang", "Abnormal"));
        cases.add(phoneCase("PAM-091", "(123)456789", false, "chứa dấu ngoặc", "Abnormal"));
        cases.add(phoneCase("PAM-092", "12+3456789", false, "dấu + không ở đầu", "Abnormal"));
        cases.add(phoneCase("PAM-093", "++1234567", false, "hai dấu +", "Abnormal"));
        cases.add(phoneCase("PAM-094", "12345A789", false, "chứa chữ cái", "Abnormal"));
        cases.add(test("PAM-095", "email='abc'",
                "Từ chối 400 và hiển thị đúng MSG05.", "Abnormal",
                () -> expectAuthFailure(() -> linkedFixture(profile(fName(), phone(),
                        PersonalAccountDemoData.EMAIL)).service.update(PersonalAccountDemoData.RECEPTIONIST_ID,
                                new UpdateProfile(fName(), phone(), "abc", PersonalAccountDemoData.CURRENT_PASSWORD)),
                        400, "Nhập địa chỉ email theo dạng ten@tenmien.com."),
                "AccountProfileService.update/AuthValidation.email"));
        cases.add(test("PAM-096", "đổi email nhưng currentPassword=null",
                "Từ chối 400 theo MSG02 cho Mật khẩu hiện tại.", "Abnormal", () -> {
                    Fixture f = linkedFixture(profile(fName(), phone(), "new@example.com"));
                    return expectAuthFailure(() -> f.service.update(PersonalAccountDemoData.RECEPTIONIST_ID,
                            new UpdateProfile(fName(), phone(), "new@example.com", null)), 400,
                            "Vui lòng bổ sung Mật khẩu hiện tại.");
                }, "AccountProfileService.update"));
        cases.add(test("PAM-097", "đổi email với currentPassword sai",
                "Từ chối 400 và hiển thị đúng MSG28.", "Abnormal", () -> {
                    Fixture f = linkedFixture(profile(fName(), phone(), "new@example.com"));
                    when(f.crypto.matches("Sai@1234", PersonalAccountDemoData.PASSWORD_HASH)).thenReturn(false);
                    return expectAuthFailure(() -> f.service.update(PersonalAccountDemoData.RECEPTIONIST_ID,
                            new UpdateProfile(fName(), phone(), "new@example.com", "Sai@1234")), 400,
                            "Mật khẩu hiện tại không đúng. Vui lòng nhập lại.");
                }, "AccountProfileService.update"));
        cases.add(test("PAM-098", "email mới đã thuộc tài khoản khác",
                "Từ chối 409 và hiển thị đúng MSG35.", "Abnormal", () -> {
                    Fixture f = linkedFixture(profile(fName(), phone(), "used@example.com"));
                    when(f.crypto.matches(PersonalAccountDemoData.CURRENT_PASSWORD,
                            PersonalAccountDemoData.PASSWORD_HASH)).thenReturn(true);
                    when(f.repository.emailTaken(PersonalAccountDemoData.RECEPTIONIST_ID, "used@example.com"))
                            .thenReturn(true);
                    return expectAuthFailure(() -> f.service.update(PersonalAccountDemoData.RECEPTIONIST_ID,
                            new UpdateProfile(fName(), phone(), "used@example.com",
                                    PersonalAccountDemoData.CURRENT_PASSWORD)), 409,
                            "Email này đã được sử dụng cho tài khoản khác. Vui lòng nhập email khác.");
                }, "AccountProfileService.update"));
        cases.add(test("PAM-099", "email bị tài khoản khác chiếm giữa bước kiểm tra và lưu",
                "Từ chối 409 và hiển thị đúng MSG35; không ghi audit sai.", "Abnormal", () -> {
                    Fixture f = linkedFixture(profile(fName(), phone(), "race@example.com"));
                    when(f.crypto.matches(PersonalAccountDemoData.CURRENT_PASSWORD,
                            PersonalAccountDemoData.PASSWORD_HASH)).thenReturn(true);
                    when(f.repository.emailTaken(any(), eq("race@example.com"))).thenReturn(false);
                    org.mockito.Mockito.doThrow(new DuplicateKeyException("duplicate"))
                            .when(f.repository).update(any(), any(), any(), eq("race@example.com"), eq(true));
                    TestResult result = expectAuthFailure(() -> f.service.update(
                            PersonalAccountDemoData.RECEPTIONIST_ID,
                            new UpdateProfile(fName(), phone(), "race@example.com",
                                    PersonalAccountDemoData.CURRENT_PASSWORD)), 409,
                            "Email này đã được sử dụng cho tài khoản khác. Vui lòng nhập email khác.");
                    verify(f.repository, never()).audit(any(), any());
                    return result;
                }, "AccountProfileService.update"));
        cases.add(test("PAM-100", "tài khoản không liên kết nhân viên, chỉ đổi email và xác nhận mật khẩu đúng",
                "Cho phép đổi email; không sửa họ tên/số điện thoại.", "Normal", () -> {
                    Profile before = PersonalAccountDemoData.unlinkedProfile();
                    Profile after = profileForAccount(before.account(), null, null, "qlcc.new@example.com");
                    Fixture f = fixture(before, after);
                    when(f.crypto.matches(PersonalAccountDemoData.CURRENT_PASSWORD,
                            PersonalAccountDemoData.PASSWORD_HASH)).thenReturn(true);
                    Profile actual = f.service.update(PersonalAccountDemoData.HEAD_OFFICE_MANAGER_ID,
                            new UpdateProfile(null, null, "qlcc.new@example.com",
                                    PersonalAccountDemoData.CURRENT_PASSWORD));
                    verify(f.repository).update(PersonalAccountDemoData.HEAD_OFFICE_MANAGER_ID,
                            null, null, "qlcc.new@example.com", true);
                    return check(actual.fullName() == null && actual.phone() == null,
                            "email=" + actual.account().email() + ", fullName=null, phone=null");
                }, "AccountProfileService.update"));
        cases.add(test("PAM-101", "tài khoản không liên kết nhân viên nhưng nhập fullName hoặc phone",
                "Từ chối 409; không tạo hồ sơ nhân viên ngoài UC.", "Abnormal", () -> {
                    Profile before = PersonalAccountDemoData.unlinkedProfile();
                    Fixture f = fixture(before, before);
                    return expectAuthFailure(() -> f.service.update(PersonalAccountDemoData.HEAD_OFFICE_MANAGER_ID,
                            new UpdateProfile("Tên mới", "0987654321", before.account().email(), null)), 409,
                            "Tài khoản chưa liên kết hồ sơ nhân viên");
                }, "AccountProfileService.update"));
        cases.add(test("PAM-102", "tài khoản bị chuyển INACTIVE trước khi lưu",
                "Từ chối 403; không cập nhật và không ghi lịch sử.", "Abnormal", () -> {
                    Fixture f = linkedFixture(PersonalAccountDemoData.linkedProfile());
                    when(f.repository.lockActiveAccount(PersonalAccountDemoData.RECEPTIONIST_ID)).thenReturn(null);
                    TestResult result = expectAuthFailure(() -> f.service.update(
                            PersonalAccountDemoData.RECEPTIONIST_ID,
                            new UpdateProfile(fName(), phone(), PersonalAccountDemoData.EMAIL, null)), 403,
                            "Tài khoản đã bị vô hiệu hóa. Vui lòng liên hệ Quản lý cấp cao để được hỗ trợ.");
                    verify(f.repository, never()).update(any(), any(), any(), anyString(), any(Boolean.class));
                    return result;
                }, "AccountProfileService.update"));
        return List.copyOf(cases);
    }

    private static ManualTest fullNameBoundary(String id, String fullName, boolean valid) {
        return test(id, "fullName dài " + fullName.length() + " ký tự",
                valid ? "Chấp nhận biên 200 ký tự và cập nhật thành công."
                        : "Từ chối 400 và hiển thị MSG03: Họ tên chỉ được chứa tối đa 200 ký tự.",
                "Boundary", () -> {
                    Fixture f = linkedFixture(profile(fullName, phone(), PersonalAccountDemoData.EMAIL));
                    if (valid) {
                        f.service.update(PersonalAccountDemoData.RECEPTIONIST_ID,
                                new UpdateProfile(fullName, phone(), PersonalAccountDemoData.EMAIL, null));
                        return check(true, "acceptedLength=" + fullName.length());
                    }
                    return expectAuthFailure(() -> f.service.update(PersonalAccountDemoData.RECEPTIONIST_ID,
                            new UpdateProfile(fullName, phone(), PersonalAccountDemoData.EMAIL, null)), 400,
                            "Họ tên chỉ được chứa tối đa 200 ký tự.");
                }, "AccountProfileService.update/AuthValidation.required");
    }

    private static ManualTest phoneCase(String id, String phone, boolean valid, String description, String type) {
        return test(id, "phone='" + phone + "' (" + description + ")",
                valid ? "Chấp nhận theo BR-119 và cập nhật thành công."
                        : "Từ chối 400 và hiển thị đúng MSG04.",
                type, () -> {
                    String storedPhone = phone.isBlank() ? null : phone;
                    Fixture f = linkedFixture(profile(fName(), storedPhone, PersonalAccountDemoData.EMAIL));
                    if (valid) {
                        Profile actual = f.service.update(PersonalAccountDemoData.RECEPTIONIST_ID,
                                new UpdateProfile(fName(), phone, PersonalAccountDemoData.EMAIL, null));
                        return check(java.util.Objects.equals(storedPhone, actual.phone()),
                                "storedPhone=" + actual.phone());
                    }
                    return expectAuthFailure(() -> f.service.update(PersonalAccountDemoData.RECEPTIONIST_ID,
                            new UpdateProfile(fName(), phone, PersonalAccountDemoData.EMAIL, null)), 400, MSG04);
                }, "AccountProfileService.update");
    }

    private static ManualTest test(String id, String input, String expected, String type,
            CheckedAction action, String sourceMethod) {
        return new ManualTest(id, UC, sourceMethod, PRE, input, expected, type, NOTE, action);
    }

    private static Fixture linkedFixture(Profile after) {
        return fixture(PersonalAccountDemoData.linkedProfile(), after);
    }

    private static Fixture fixture(Profile before, Profile after) {
        AccountProfileRepository repository = mock(AccountProfileRepository.class);
        AuthCrypto crypto = mock(AuthCrypto.class);
        when(repository.lockActiveAccount(before.account().id())).thenReturn(PersonalAccountDemoData.PASSWORD_HASH);
        when(repository.find(before.account().id())).thenReturn(before, after);
        AccountProfileService service = new AccountProfileService(repository, crypto);
        return new Fixture(repository, crypto, service, before);
    }

    private static Profile profile(String fullName, String phone, String email) {
        Profile base = PersonalAccountDemoData.linkedProfile();
        return profileForAccount(base.account(), fullName, phone, email);
    }

    private static Profile profileForAccount(AccountView base, String fullName, String phone, String email) {
        AccountView account = new AccountView(base.id(), base.employeeName(), base.employeeCode(), base.username(),
                email, base.role(), base.branchId(), base.branchName(), base.status(), base.managedBranchIds());
        return new Profile(account, fullName, phone, base.employeeName() != null);
    }

    private static boolean sameAccess(Profile before, Profile after) {
        return before.account().role().equals(after.account().role())
                && java.util.Objects.equals(before.account().branchId(), after.account().branchId())
                && java.util.Objects.equals(before.account().managedBranchIds(), after.account().managedBranchIds());
    }

    private static String fName() {
        return PersonalAccountDemoData.linkedProfile().fullName();
    }

    private static String phone() {
        return PersonalAccountDemoData.linkedProfile().phone();
    }

    private record Fixture(AccountProfileRepository repository, AuthCrypto crypto,
            AccountProfileService service, Profile before) {
    }
}


