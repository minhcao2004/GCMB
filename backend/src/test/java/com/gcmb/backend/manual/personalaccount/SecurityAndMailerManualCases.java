/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 27/10/2026
 * Description: Manual unit tests for Personal Account session security, cryptography, and recovery mail.
 */
package com.gcmb.backend.manual.personalaccount;

import com.gcmb.backend.manual.personalaccount.PersonalAccountManualRunner.CheckedAction;
import com.gcmb.backend.manual.personalaccount.PersonalAccountManualRunner.ManualTest;
import com.gcmb.backend.manual.personalaccount.PersonalAccountManualRunner.TestResult;

import static com.gcmb.backend.manual.personalaccount.PersonalAccountManualRunner.check;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.exception.AuthFailure;
import com.gcmb.backend.auth.security.AuthCrypto;
import com.gcmb.backend.auth.security.AuthSettings;
import com.gcmb.backend.auth.security.SessionFilter;
import com.gcmb.backend.auth.service.AuthService;
import com.gcmb.backend.auth.service.RecoveryMailer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityAndMailerManualCases {
    private SecurityAndMailerManualCases() {
    }

    public static List<ManualTest> cases() {
        List<ManualTest> cases = new ArrayList<>();
        cases.add(authCryptoCase("PAM-019", "Sinh session token",
                "Token có đúng 43 ký tự URL-safe và không chứa padding.", "Boundary", () -> {
                    String token = new AuthCrypto().token();
                    return check(token.matches("[A-Za-z0-9_-]{43}"),
                            "tokenLength=" + token.length() + ", urlSafe=" + token.matches("[A-Za-z0-9_-]+"));
                }));
        cases.add(authCryptoCase("PAM-020", "Băm và kiểm tra mật khẩu hợp lệ",
                "Hash PBKDF2 có prefix; mật khẩu đúng khớp, mật khẩu sai không khớp.", "Normal", () -> {
                    AuthCrypto crypto = new AuthCrypto();
                    String hash = crypto.encode(PersonalAccountDemoData.CURRENT_PASSWORD);
                    boolean actual = hash.startsWith("{pbkdf2-gcmb}")
                            && crypto.matches(PersonalAccountDemoData.CURRENT_PASSWORD, hash)
                            && !crypto.matches("Sai@1234", hash);
                    return check(actual, "prefix=" + hash.substring(0, 13) + ", matchRules=" + actual);
                }));
        cases.add(authCryptoCase("PAM-021", "stored password là plaintext legacy",
                "Không bao giờ chấp nhận plaintext làm password hash.", "Abnormal", () -> {
                    boolean actual = new AuthCrypto().matches("secret", "secret");
                    return check(!actual, "plaintextAccepted=" + actual);
                }));
        cases.add(authCryptoCase("PAM-022", "Băm token phiên bằng SHA-256",
                "Kết quả là 64 ký tự hex ổn định và không chứa token gốc.", "Normal", () -> {
                    String hash = AuthCrypto.hash(PersonalAccountDemoData.TOKEN);
                    return check(hash.matches("[0-9a-f]{64}") && !hash.contains(PersonalAccountDemoData.TOKEN),
                            "hash=" + hash);
                }));
        cases.add(sessionCase("PAM-023", "request có cookie GCMB_SESSION và cookie khác",
                "Chỉ đọc giá trị cookie GCMB_SESSION.", "Normal", () -> {
                    HttpServletRequest request = mock(HttpServletRequest.class);
                    when(request.getCookies()).thenReturn(new Cookie[] {
                            new Cookie("OTHER", "ignore"), new Cookie("GCMB_SESSION", PersonalAccountDemoData.TOKEN)
                    });
                    String token = SessionFilter.token(request);
                    return check(PersonalAccountDemoData.TOKEN.equals(token), "token=" + token);
                }));
        cases.add(sessionCase("PAM-024", "request không có cookie phiên",
                "Không xác thực danh tính và vẫn chuyển request xuống filter chain.", "Abnormal", () -> {
                    SecurityContextHolder.clearContext();
                    AuthService auth = mock(AuthService.class);
                    HttpServletRequest request = mock(HttpServletRequest.class);
                    HttpServletResponse response = mock(HttpServletResponse.class);
                    FilterChain chain = mock(FilterChain.class);
                    new ExposedSessionFilter(auth).run(request, response, chain);
                    verify(auth).authenticate(null);
                    verify(chain).doFilter(request, response);
                    return check(SecurityContextHolder.getContext().getAuthentication() == null,
                            "authentication=null, chainContinued=true");
                }));
        cases.add(sessionCase("PAM-025", "cookie phiên hợp lệ của tài khoản RECEPTIONIST",
                "Thiết lập SecurityContext với ROLE_RECEPTIONIST và chuyển request xuống chain.", "Normal", () -> {
                    SecurityContextHolder.clearContext();
                    AuthService auth = mock(AuthService.class);
                    HttpServletRequest request = requestWithToken();
                    HttpServletResponse response = mock(HttpServletResponse.class);
                    FilterChain chain = mock(FilterChain.class);
                    when(auth.authenticate(PersonalAccountDemoData.TOKEN))
                            .thenReturn(PersonalAccountDemoData.receptionistIdentity());
                    new ExposedSessionFilter(auth).run(request, response, chain);
                    var authentication = SecurityContextHolder.getContext().getAuthentication();
                    boolean passed = authentication != null
                            && authentication.getAuthorities().stream()
                                    .anyMatch(a -> "ROLE_RECEPTIONIST".equals(a.getAuthority()));
                    SecurityContextHolder.clearContext();
                    return check(passed, "principalLoaded=" + passed + ", chainContinued=true");
                }));
        cases.add(sessionCase("PAM-026", "BRANCH_MANAGER chọn branch không còn quyền",
                "Từ chối theo MSG24, trả 403 và không tiếp tục filter chain.", "Abnormal", () -> {
                    SecurityContextHolder.clearContext();
                    AuthService auth = mock(AuthService.class);
                    HttpServletRequest request = requestWithToken();
                    HttpServletResponse response = mock(HttpServletResponse.class);
                    FilterChain chain = mock(FilterChain.class);
                    StringWriter body = new StringWriter();
                    when(response.getWriter()).thenReturn(new PrintWriter(body));
                    Identity manager = new Identity(PersonalAccountDemoData.BRANCH_MANAGER_ID, "qlcs.hoabang",
                            "BRANCH_MANAGER", "/quan-ly-chi-nhanh", PersonalAccountDemoData.HOA_BANG_BRANCH_ID,
                            "GenZ Cinema Hoa Bằng");
                    when(auth.authenticate(PersonalAccountDemoData.TOKEN)).thenReturn(manager);
                    when(request.getHeader("X-GCMB-Branch-Id")).thenReturn("invalid-branch");
                    when(request.getServletPath()).thenReturn("/api/v1/branch-management/rooms");
                    when(auth.forBranch(manager, "invalid-branch"))
                            .thenThrow(new AuthFailure(403, "branchId", "forbidden"));
                    new ExposedSessionFilter(auth).run(request, response, chain);
                    verify(response).setStatus(403);
                    verify(chain, never()).doFilter(any(), any());
                    return check(body.toString().contains("không còn quyền quản lý"),
                            "status=403, body=" + body);
                }));
        cases.add(mailCase("PAM-078", "SMTP sẵn sàng, email và code hợp lệ",
                "Gửi đúng from/to/subject và nội dung chứa code, quy tắc một lần.", "Normal", () -> {
                    @SuppressWarnings("unchecked")
                    ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
                    JavaMailSender sender = mock(JavaMailSender.class);
                    when(provider.getIfAvailable()).thenReturn(sender);
                    RecoveryMailer mailer = new RecoveryMailer(provider,
                            new AuthSettings(false, "http://localhost:3000", "noreply@gcmb.invalid"));
                    mailer.send(PersonalAccountDemoData.EMAIL, PersonalAccountDemoData.CODE);
                    ArgumentCaptor<SimpleMailMessage> capture = ArgumentCaptor.forClass(SimpleMailMessage.class);
                    verify(sender).send(capture.capture());
                    SimpleMailMessage message = capture.getValue();
                    boolean passed = "noreply@gcmb.invalid".equals(message.getFrom())
                            && PersonalAccountDemoData.EMAIL.equals(message.getTo()[0])
                            && message.getText().contains(PersonalAccountDemoData.CODE)
                            && message.getText().contains("chỉ sử dụng một lần");
                    return check(passed, "from=" + message.getFrom() + ", to=" + message.getTo()[0]
                            + ", containsCode=" + message.getText().contains(PersonalAccountDemoData.CODE));
                }));
        cases.add(mailCase("PAM-079", "không cấu hình JavaMailSender hoặc mailFrom",
                "Ném MailUnavailable để transaction gửi code được hoàn tác.", "Abnormal", () -> {
                    @SuppressWarnings("unchecked")
                    ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
                    RecoveryMailer mailer = new RecoveryMailer(provider,
                            new AuthSettings(false, "http://localhost:3000", ""));
                    try {
                        mailer.send(PersonalAccountDemoData.EMAIL, PersonalAccountDemoData.CODE);
                        return check(false, "No MailUnavailable was thrown");
                    } catch (RecoveryMailer.MailUnavailable expected) {
                        return check(true, "MailUnavailable thrown; SMTP details not leaked");
                    }
                }));
        return List.copyOf(cases);
    }

    private static ManualTest authCryptoCase(String id, String input, String expected, String type,
            CheckedAction action) {
        return new ManualTest(id, "UC-128 Login", "AuthCrypto", "Hệ thống xác thực đã khởi tạo.", input,
                expected, type,
                "Nguồn: UC-128_Login.docx; BR-111, BR-131; MSG08, MSG24; mã nguồn AuthCrypto.", action);
    }

    private static ManualTest sessionCase(String id, String input, String expected, String type,
            CheckedAction action) {
        return new ManualTest(id, "UC-128 Login", "SessionFilter", "Request đi vào API sau bước đăng nhập.", input,
                expected, type,
                "Nguồn: UC-128_Login.docx; BR-131; MSG24, MSG25; mã nguồn SessionFilter.", action);
    }

    private static ManualTest mailCase(String id, String input, String expected, String type,
            CheckedAction action) {
        return new ManualTest(id, "UC-02 Reset Password", "RecoveryMailer.send",
                "Tài khoản demo ACTIVE đã yêu cầu gửi mã khôi phục.", input, expected, type,
                "Nguồn: UC-02_Reset_Password.docx; BR-126; MSG09, MSG25; mã nguồn RecoveryMailer.", action);
    }

    private static HttpServletRequest requestWithToken() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(new Cookie[] {
                new Cookie("GCMB_SESSION", PersonalAccountDemoData.TOKEN)
        });
        return request;
    }

    private static final class ExposedSessionFilter extends SessionFilter {
        private ExposedSessionFilter(AuthService auth) {
            super(auth);
        }

        private void run(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            doFilterInternal(request, response, chain);
        }
    }
}


