/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 14/10/2026
 * Description: Covers login, recovery, password reset, session authentication and logout.
 */

package com.gcmb.backend.manual.auth;

import com.gcmb.backend.administration.auth.dto.response.Identity;
import com.gcmb.backend.administration.auth.entity.Account;
import com.gcmb.backend.administration.auth.entity.Reset;
import com.gcmb.backend.administration.auth.repository.AuthRepository;
import com.gcmb.backend.administration.auth.security.AuthCrypto;
import com.gcmb.backend.administration.auth.service.AuthService;
import com.gcmb.backend.administration.auth.service.RecoveryMailer;
import com.gcmb.backend.manual.ManualTestCase;
import com.gcmb.backend.manual.ManualTestSuite;
import com.gcmb.backend.manual.ManualTestSupport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class AuthServiceManualTest {
    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID RESET_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final String USERNAME = "staff";
    private static final String EMAIL = "staff@example.com";
    private static final String PASSWORD = "  Original password 123  ";
    private static final String NEW_PASSWORD = "ReplacementPassword123!";
    private static final String PASSWORD_HASH = "{pbkdf2-gcmb}stored-password";
    private static final String NEW_PASSWORD_HASH = "{pbkdf2-gcmb}new-password";
    private static final String CODE_HASH = "{pbkdf2-gcmb}stored-code";
    private static final String CODE = "038291";
    private static final String DUMMY_TOKEN = "D".repeat(43);
    private static final String DUMMY_HASH = "{pbkdf2-gcmb}dummy";
    private static final String SESSION_TOKEN = "S".repeat(43);
    private static final String CHALLENGE = "C".repeat(43);

    private AuthServiceManualTest() {
    }

    public static ManualTestSuite suite() {
        List<ManualTestCase> cases = new ArrayList<>();
        addLoginCases(cases);
        addSendCodeCases(cases);
        addResetCases(cases);
        addAuthenticateCases(cases);
        addLogoutCases(cases);
        return new ManualTestSuite("Authentication", "AuthService", cases);
    }

    private static void addLoginCases(List<ManualTestCase> cases) {
        Object[][] destinations = {
                {"UTC003", "HEAD_OFFICE_MANAGER", "/quan-ly-he-thong", false, 15},
                {"UTC004", "HEAD_OFFICE_MANAGER", "/quan-ly-he-thong", true, 16},
                {"UTC005", "BRANCH_MANAGER", "/quan-ly-chi-nhanh", false, 17},
                {"UTC006", "BRANCH_MANAGER", "/quan-ly-chi-nhanh", true, 18},
                {"UTC001", "RECEPTIONIST", "/le-tan", false, 13},
                {"UTC002", "RECEPTIONIST", "/le-tan", true, 14},
                {"UTC007", "ACCOUNTANT", "/ke-toan", false, 19},
                {"UTC008", "ACCOUNTANT", "/ke-toan", true, 20}
        };
        for (Object[] data : destinations) {
            String id = (String) data[0];
            String role = (String) data[1];
            String destination = (String) data[2];
            boolean remember = (Boolean) data[3];
            int row = (Integer) data[4];
            cases.add(test("login-" + role.toLowerCase() + "-" + remember, id, "UTC-PAM-LOGIN-001",
                    "loginReturnsIdentityDestinationAndCreatesHashedSession[" + role + ", remember=" + remember + "]",
                    row, "ACTIVE account; password matches; role is supported",
                    "username='  staff  ', remember=" + remember,
                    "Identity role=" + role + ", destination=" + destination
                            + "; token=" + SESSION_TOKEN + "; seconds=" + (remember ? Integer.MAX_VALUE : -1)
                            + "; record login and create hashed session", () -> {
                        Context context = context();
                        arrangeLogin(context, role);
                        return ManualTestSupport.observe(
                                () -> context.service.login("  " + USERNAME + "  ", PASSWORD, remember),
                                context.repository, context.crypto, context.mailer);
                    }));
        }

        Object[][] missingUsernames = {
                {"UTC011", "null", null, 23}, {"UTC012", "empty", "", 24},
                {"UTC013", "spaces", "   ", 25}, {"UTC014", "tab/newline", "\t\n", 26},
                {"UTC015", "NBSP", "\u00a0", 27}, {"UTC016", "BOM", "\uFEFF", 28}
        };
        for (Object[] data : missingUsernames) {
            addInvalidLoginInput(cases, (String) data[0], "UTC-PAM-LOGIN-002", "username",
                    (String) data[1], (String) data[2], PASSWORD, (Integer) data[3]);
        }

        Object[][] missingPasswords = {
                {"UTC017", "null", null, 29}, {"UTC018", "empty", "", 30},
                {"UTC019", "spaces", "   ", 31}, {"UTC020", "tab/newline", "\t\n", 32},
                {"UTC021", "NBSP", "\u00a0", 33}, {"UTC022", "BOM", "\uFEFF", 34}
        };
        for (Object[] data : missingPasswords) {
            addInvalidLoginInput(cases, (String) data[0], "UTC-PAM-LOGIN-003", "password",
                    (String) data[1], USERNAME, (String) data[2], (Integer) data[3]);
        }

        cases.add(test("login-unknown-user", "UTC023", "UTC-PAM-LOGIN-004",
                "loginUsesDummyHashAndGenericErrorForUnknownUsername", 35,
                "Repository returns no account", "username=staff; valid password",
                "AuthFailure status=401 with generic message; match against dummy hash; no session write", () -> {
                    Context context = context();
                    when(context.repository.findByUsername(USERNAME)).thenReturn(null);
                    return ManualTestSupport.observe(
                            () -> context.service.login(USERNAME, PASSWORD, false),
                            context.repository, context.crypto, context.mailer);
                }));

        Object[][] statuses = {{"UTC024", "LOCKED", 36}, {"UTC025", "REVOKED", 37}, {"UTC026", "INACTIVE", 38}};
        for (Object[] data : statuses) {
            String id = (String) data[0];
            String status = (String) data[1];
            int row = (Integer) data[2];
            cases.add(test("login-status-" + status.toLowerCase(), id, "UTC-PAM-LOGIN-005",
                    "loginRejectsAccountThatIsNotActive[" + status + "]", row,
                    "Account status=" + status, "username=staff; valid password",
                    "AuthFailure status=403; no password check or session write", () -> {
                        Context context = context();
                        when(context.repository.findByUsername(USERNAME)).thenReturn(account(status, "RECEPTIONIST"));
                        return ManualTestSupport.observe(
                                () -> context.service.login(USERNAME, PASSWORD, false),
                                context.repository, context.crypto, context.mailer);
                    }));
        }

        cases.add(test("login-wrong-password", "UTC027", "UTC-PAM-LOGIN-006",
                "loginRejectsWrongPasswordWithoutWritingSession", 39,
                "ACTIVE account; crypto.matches=false", "username=staff; supplied password does not match",
                "AuthFailure status=401; no login record or session write", () -> {
                    Context context = context();
                    when(context.repository.findByUsername(USERNAME)).thenReturn(account("ACTIVE", "RECEPTIONIST"));
                    when(context.crypto.matches(PASSWORD, PASSWORD_HASH)).thenReturn(false);
                    return ManualTestSupport.observe(
                            () -> context.service.login(USERNAME, PASSWORD, false),
                            context.repository, context.crypto, context.mailer);
                }));

        cases.add(test("login-unsupported-role", "UTC028", "UTC-PAM-LOGIN-007",
                "loginRejectsUnsupportedRoleBeforeWritingSession", 40,
                "ACTIVE account; password matches; role=UNKNOWN_ROLE", "username=staff",
                "AuthFailure status=403; no login record or session write", () -> {
                    Context context = context();
                    when(context.repository.findByUsername(USERNAME)).thenReturn(account("ACTIVE", "UNKNOWN_ROLE"));
                    when(context.crypto.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);
                    return ManualTestSupport.observe(
                            () -> context.service.login(USERNAME, PASSWORD, false),
                            context.repository, context.crypto, context.mailer);
                }));

        cases.add(test("login-max-username", "UTC009", "UTC-PAM-LOGIN-008",
                "loginAcceptsUsernameAtMaximumLength", 21,
                "ACTIVE RECEPTIONIST account with a 100-character username; password matches",
                "username has outer spaces and is 100 characters after trim",
                "Accept boundary, return /le-tan identity and create hashed session", () -> {
                    Context context = context();
                    String username = "u".repeat(100);
                    when(context.repository.findByUsername(username))
                            .thenReturn(new Account(USER_ID, username, EMAIL, PASSWORD_HASH, "ACTIVE", "RECEPTIONIST", null, null));
                    when(context.crypto.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);
                    when(context.crypto.token()).thenReturn(SESSION_TOKEN);
                    return ManualTestSupport.observe(
                            () -> context.service.login("  " + username + "  ", PASSWORD, false),
                            context.repository, context.crypto, context.mailer);
                }));

        cases.add(test("login-over-max-username", "UTC010", "UTC-PAM-LOGIN-009",
                "loginRejectsUsernameAboveMaximumLength", 22,
                "No dependency setup required", "username length=101",
                "AuthFailure status=400 before repository or mailer call", () -> {
                    Context context = context();
                    return ManualTestSupport.observe(
                            () -> context.service.login("u".repeat(101), PASSWORD, false),
                            context.repository, context.crypto, context.mailer);
                }));
    }

    private static void addSendCodeCases(List<ManualTestCase> cases) {
        cases.add(test("send-code-success", "UTC029", null,
                "sendCodeNormalizesEmailRevokesOldResetAndSendsRawCode", 42,
                "ACTIVE account exists by email; mailer works", "email='  STAFF@EXAMPLE.COM  '",
                "Return Recovery(challenge, normalized email); revoke old resets, create reset and send raw code in order", () -> {
                    Context context = context();
                    arrangeRecovery(context);
                    return ManualTestSupport.observe(
                            () -> context.service.sendCode("  STAFF@EXAMPLE.COM  "),
                            context.repository, context.crypto, context.mailer);
                }));

        Object[][] invalidEmails = {
                {"UTC030", "null", null, 43}, {"UTC031", "empty", "", 44},
                {"UTC032", "spaces", "   ", 45}, {"UTC033", "not-an-email", "not-an-email", 46},
                {"UTC034", "leading dot", ".user@example.com", 47},
                {"UTC035", "double dot", "a..b@example.com", 48}
        };
        for (Object[] data : invalidEmails) {
            String id = (String) data[0];
            String label = (String) data[1];
            String email = (String) data[2];
            int row = (Integer) data[3];
            cases.add(test("send-code-invalid-email-" + label.replace(' ', '-'), id, null,
                    "sendCodeRejectsInvalidEmail[" + label + "]", row,
                    "No dependency setup required", "email=" + printable(email),
                    "AuthFailure status=400; no repository or mailer interaction", () -> {
                        Context context = context();
                        return ManualTestSupport.observe(
                                () -> context.service.sendCode(email),
                                context.repository, context.crypto, context.mailer);
                    }));
        }

        cases.add(test("send-code-unknown-email", "UTC036", null,
                "sendCodeRejectsUnknownEmailWithoutSendingMail", 49,
                "Repository returns no account for email", "email=staff@example.com",
                "AuthFailure status=404; no reset creation or mail send", () -> {
                    Context context = context();
                    when(context.repository.findByEmail(EMAIL)).thenReturn(null);
                    return ManualTestSupport.observe(
                            () -> context.service.sendCode(EMAIL),
                            context.repository, context.crypto, context.mailer);
                }));

        Object[][] statuses = {{"UTC037", "LOCKED", 50}, {"UTC038", "REVOKED", 51}, {"UTC039", "INACTIVE", 52}};
        for (Object[] data : statuses) {
            String id = (String) data[0];
            String status = (String) data[1];
            int row = (Integer) data[2];
            cases.add(test("send-code-status-" + status.toLowerCase(), id, null,
                    "sendCodeRejectsAccountThatIsNotActive[" + status + "]", row,
                    "Account status=" + status, "email=staff@example.com",
                    "AuthFailure status=403; no reset creation or mail send", () -> {
                        Context context = context();
                        when(context.repository.findByEmail(EMAIL)).thenReturn(account(status, "RECEPTIONIST"));
                        return ManualTestSupport.observe(
                                () -> context.service.sendCode(EMAIL),
                                context.repository, context.crypto, context.mailer);
                    }));
        }

        cases.add(test("send-code-mail-failure", "UTC040", null, "sendCodePropagatesMailFailure", 53,
                "ACTIVE account; reset is prepared; mailer throws MailUnavailable", "email=staff@example.com",
                "MailUnavailable propagates; reset revocation and creation were invoked", () -> {
                    Context context = context();
                    arrangeRecovery(context);
                    doThrow(new RecoveryMailer.MailUnavailable()).when(context.mailer).send(EMAIL, CODE);
                    return ManualTestSupport.observe(
                            () -> context.service.sendCode(EMAIL),
                            context.repository, context.crypto, context.mailer);
                }));
    }

    private static void addResetCases(List<ManualTestCase> cases) {
        cases.add(test("reset-success", "UTC041", null, "resetChangesPasswordConsumesResetAndRevokesTokens", 55,
                "ACTIVE account; reset is unused/unrevoked; code matches",
                "normalized email, valid challenge, code=038291, matching new password",
                "Change password, consume reset and revoke tokens in order using the same timestamp", () -> {
                    Context context = context();
                    arrangeReset(context, reset(null, null));
                    when(context.crypto.matches(EMAIL + ":" + CODE, CODE_HASH)).thenReturn(true);
                    when(context.crypto.encode(NEW_PASSWORD)).thenReturn(NEW_PASSWORD_HASH);
                    return ManualTestSupport.observeVoid(
                            () -> context.service.reset("  STAFF@EXAMPLE.COM  ", CHALLENGE, CODE,
                                    NEW_PASSWORD, NEW_PASSWORD),
                            context.repository, context.crypto, context.mailer);
                }));

        Object[][] invalidEmails = {
                {"UTC042", "null", null, 56}, {"UTC043", "empty", "", 57},
                {"UTC044", "spaces", "   ", 58}, {"UTC046", "not-an-email", "not-an-email", 60},
                {"UTC047", "double dot", "a..b@example.com", 61}
        };
        for (Object[] data : invalidEmails) {
            String id = (String) data[0];
            String label = (String) data[1];
            String email = (String) data[2];
            int row = (Integer) data[3];
            cases.add(test("reset-email-" + label.replace(' ', '-'), id, null,
                    "resetRejectsInvalidEmail[" + label + "]", row,
                    "No dependency setup required", "email=" + printable(email),
                    "AuthFailure status=400; no repository or mailer interaction", () -> {
                        Context context = context();
                        return ManualTestSupport.observeVoid(
                                () -> context.service.reset(email, CHALLENGE, CODE, NEW_PASSWORD, NEW_PASSWORD),
                                context.repository, context.crypto, context.mailer);
                    }));
        }

        Object[][] passwordPairs = {
                {"UTC048", "password null", null, NEW_PASSWORD, 62},
                {"UTC049", "password empty", "", NEW_PASSWORD, 63},
                {"UTC050", "password spaces", "   ", "   ", 64},
                {"UTC051", "confirmation null", NEW_PASSWORD, null, 65},
                {"UTC052", "confirmation empty", NEW_PASSWORD, "", 66},
                {"UTC053", "confirmation mismatch", NEW_PASSWORD, "DifferentPassword123!", 67}
        };
        for (Object[] data : passwordPairs) {
            String id = (String) data[0];
            String label = (String) data[1];
            String password = (String) data[2];
            String confirmation = (String) data[3];
            int row = (Integer) data[4];
            cases.add(test("reset-password-" + label.replace(' ', '-'), id, null,
                    "resetRejectsMissingOrMismatchedPassword[" + label + "]", row,
                    "No dependency setup required",
                    "password=" + printable(password) + ", confirmation=" + printable(confirmation),
                    "AuthFailure status=400; no repository or mailer interaction", () -> {
                        Context context = context();
                        return ManualTestSupport.observeVoid(
                                () -> context.service.reset(EMAIL, CHALLENGE, CODE, password, confirmation),
                                context.repository, context.crypto, context.mailer);
                    }));
        }

        Object[][] invalidCodes = {
                {"UTC054", "null", null, 68}, {"UTC055", "empty", "", 69},
                {"UTC056", "space", " ", 70}, {"UTC057", "five digits", "12345", 71},
                {"UTC058", "seven digits", "1234567", 72}, {"UTC059", "full-width", "１２３４５６", 73},
                {"UTC060", "contains letter", "12345a", 74}, {"UTC061", "leading space", " 12345", 75}
        };
        for (Object[] data : invalidCodes) {
            String id = (String) data[0];
            String label = (String) data[1];
            String code = (String) data[2];
            int row = (Integer) data[3];
            cases.add(test("reset-code-" + label.replace(' ', '-'), id, null,
                    "resetRejectsInvalidCode[" + label + "]", row,
                    "No dependency setup required", "code=" + printable(code),
                    "AuthFailure status=400; no repository or mailer interaction", () -> {
                        Context context = context();
                        return ManualTestSupport.observeVoid(
                                () -> context.service.reset(EMAIL, CHALLENGE, code, NEW_PASSWORD, NEW_PASSWORD),
                                context.repository, context.crypto, context.mailer);
                    }));
        }

        Object[][] invalidChallenges = {
                {"UTC062", "null", null, 76}, {"UTC063", "empty", "", 77},
                {"UTC064", "short", "short", 78},
                {"UTC065", "invalid character", "A" + "!".repeat(42), 79},
                {"UTC066", "42 characters", "A".repeat(42), 80},
                {"UTC067", "44 characters", "A".repeat(44), 81}
        };
        for (Object[] data : invalidChallenges) {
            String id = (String) data[0];
            String label = (String) data[1];
            String challenge = (String) data[2];
            int row = (Integer) data[3];
            cases.add(test("reset-challenge-" + label.replace(' ', '-'), id, null,
                    "resetRejectsInvalidChallenge[" + label + "]", row,
                    "No dependency setup required", "challenge=" + printable(challenge),
                    "AuthFailure status=400; no repository or mailer interaction", () -> {
                        Context context = context();
                        return ManualTestSupport.observeVoid(
                                () -> context.service.reset(EMAIL, challenge, CODE, NEW_PASSWORD, NEW_PASSWORD),
                                context.repository, context.crypto, context.mailer);
                    }));
        }

        cases.add(test("reset-unknown-email", "UTC068", null, "resetHidesWhetherEmailExists", 82,
                "Repository returns no account", "Valid email/challenge/code/password formats",
                "AuthFailure status=400 with generic invalid-code message; no password change", () -> {
                    Context context = context();
                    when(context.repository.findByEmail(EMAIL)).thenReturn(null);
                    return ManualTestSupport.observeVoid(
                            () -> context.service.reset(EMAIL, CHALLENGE, CODE, NEW_PASSWORD, NEW_PASSWORD),
                            context.repository, context.crypto, context.mailer);
                }));

        Object[][] statuses = {{"UTC069", "LOCKED", 83}, {"UTC070", "REVOKED", 84}, {"UTC071", "INACTIVE", 85}};
        for (Object[] data : statuses) {
            String id = (String) data[0];
            String status = (String) data[1];
            int row = (Integer) data[2];
            cases.add(test("reset-status-" + status.toLowerCase(), id, null,
                    "resetRejectsAccountThatIsNotActive[" + status + "]", row,
                    "Account status=" + status, "Valid reset request",
                    "AuthFailure status=403; do not find reset or change password", () -> {
                        Context context = context();
                        when(context.repository.findByEmail(EMAIL)).thenReturn(account(status, "RECEPTIONIST"));
                        return ManualTestSupport.observeVoid(
                                () -> context.service.reset(EMAIL, CHALLENGE, CODE, NEW_PASSWORD, NEW_PASSWORD),
                                context.repository, context.crypto, context.mailer);
                    }));
        }

        cases.add(resetStateCase("unknown-challenge", "UTC072", "resetRejectsUnknownChallenge", 86, null,
                "Reset lookup returns null", "AuthFailure status=400 with generic invalid-code message"));
        cases.add(resetStateCase("used-code", "UTC073", "resetRejectsAlreadyUsedCode", 87,
                reset(Instant.parse("2026-10-13T00:00:00Z"), null),
                "Reset has usedAt", "AuthFailure status=400 with already-used message; no code match or password change"));
        cases.add(resetStateCase("revoked-code", "UTC074", "resetRejectsRevokedCode", 88,
                reset(null, Instant.parse("2026-10-13T00:00:00Z")),
                "Reset has revokedAt", "AuthFailure status=400 with expired message; no code match or password change"));

        cases.add(test("reset-wrong-code", "UTC075", null, "resetRejectsWrongCodeWithoutChangingState", 89,
                "ACTIVE account; reset remains valid; crypto.matches=false", "Valid reset request with wrong code",
                "AuthFailure status=400; no password change, reset consumption or token revocation", () -> {
                    Context context = context();
                    arrangeReset(context, reset(null, null));
                    when(context.crypto.matches(EMAIL + ":" + CODE, CODE_HASH)).thenReturn(false);
                    return ManualTestSupport.observeVoid(
                            () -> context.service.reset(EMAIL, CHALLENGE, CODE, NEW_PASSWORD, NEW_PASSWORD),
                            context.repository, context.crypto, context.mailer);
                }));
    }

    private static void addAuthenticateCases(List<ManualTestCase> cases) {
        Object[][] invalidTokens = {
                {"UTC089", "null", null, 104}, {"UTC090", "empty", "", 105},
                {"UTC091", "short", "short", 106},
                {"UTC092", "invalid character", "A" + "!".repeat(42), 107},
                {"UTC093", "42 characters", "A".repeat(42), 108},
                {"UTC094", "44 characters", "A".repeat(44), 109}
        };
        for (Object[] data : invalidTokens) {
            String id = (String) data[0];
            String label = (String) data[1];
            String token = (String) data[2];
            int row = (Integer) data[3];
            cases.add(test("authenticate-" + label.replace(' ', '-'), id, "UTC-PAM-AUTHENTICATE-001",
                    "authenticateRejectsMalformedTokenWithoutRepositoryCall[" + label + "]", row,
                    "No dependency setup required", "token=" + printable(token),
                    "Return null; no repository or mailer interaction", () -> {
                        Context context = context();
                        return ManualTestSupport.observe(
                                () -> context.service.authenticate(token),
                                context.repository, context.crypto, context.mailer);
                    }));
        }

        cases.add(test("authenticate-found", "UTC095", "UTC-PAM-AUTHENTICATE-002",
                "authenticateReturnsIdentityFromHashedToken", 110,
                "Repository returns Identity for hashed token", "token='S' repeated 43",
                "Return same Identity and look up SHA-256 hash of token", () -> {
                    Context context = context();
                    Identity identity = new Identity(USER_ID, USERNAME, "RECEPTIONIST", "/le-tan", null,null);
                    when(context.repository.findSession(AuthCrypto.hash(SESSION_TOKEN))).thenReturn(identity);
                    return ManualTestSupport.observe(
                            () -> context.service.authenticate(SESSION_TOKEN),
                            context.repository, context.crypto, context.mailer);
                }));

        cases.add(test("authenticate-missing", "UTC096", "UTC-PAM-AUTHENTICATE-003",
                "authenticateReturnsNullWhenSessionDoesNotExist", 111,
                "Repository returns null for hashed token", "token='S' repeated 43",
                "Return null", () -> {
                    Context context = context();
                    when(context.repository.findSession(AuthCrypto.hash(SESSION_TOKEN))).thenReturn(null);
                    return ManualTestSupport.observe(
                            () -> context.service.authenticate(SESSION_TOKEN),
                            context.repository, context.crypto, context.mailer);
                }));
    }

    private static void addLogoutCases(List<ManualTestCase> cases) {
        cases.add(test("logout-null", "UTC097", "UTC-PAM-LOGOUT-001", "logoutIgnoresNullToken", 113,
                "No dependency setup required", "token=null",
                "Complete without exception; no repository or mailer interaction", () -> {
                    Context context = context();
                    return ManualTestSupport.observeVoid(
                            () -> context.service.logout(null),
                            context.repository, context.crypto, context.mailer);
                }));

        Object[][] tokens = {
                {"UTC098", "valid", "S" + "x".repeat(42), 114},
                {"UTC099", "empty", "", 115}, {"UTC100", "space", " ", 116},
                {"UTC101", "42 characters", "A".repeat(42), 117},
                {"UTC102", "44 characters", "A".repeat(44), 118},
                {"UTC103", "malformed", "malformed", 119}
        };
        for (Object[] data : tokens) {
            String id = (String) data[0];
            String label = (String) data[1];
            String token = (String) data[2];
            int row = (Integer) data[3];
            cases.add(test("logout-" + label.replace(' ', '-'), id, "UTC-PAM-LOGOUT-002",
                    "logoutRevokesHashOfEveryNonNullToken[" + label + "]", row,
                    "Token is non-null", "token=" + printable(token),
                    "Complete without exception; revokeSession receives exact SHA-256 hash", () -> {
                        Context context = context();
                        return ManualTestSupport.observeVoid(
                                () -> context.service.logout(token),
                                context.repository, context.crypto, context.mailer);
                    }));
        }
    }

    private static void addInvalidLoginInput(
            List<ManualTestCase> cases,
            String documentId,
            String sourceId,
            String field,
            String label,
            String username,
            String password,
            int row) {
        cases.add(test("login-missing-" + field + "-" + label.replace('/', '-'), documentId, sourceId,
                "loginRejectsMissing" + ("username".equals(field) ? "Username" : "Password") + "[" + label + "]",
                row, "No dependency setup required",
                "username=" + printable(username) + ", password=" + printable(password),
                "AuthFailure status=400; no repository or mailer interaction", () -> {
                    Context context = context();
                    return ManualTestSupport.observe(
                            () -> context.service.login(username, password, false),
                            context.repository, context.crypto, context.mailer);
                }));
    }

    private static ManualTestCase resetStateCase(
            String key,
            String id,
            String method,
            int row,
            Reset reset,
            String precondition,
            String expected) {
        return test("reset-" + key, id, null, method, row,
                "ACTIVE account; " + precondition, "Valid reset request", expected, () -> {
                    Context context = context();
                    arrangeReset(context, reset);
                    return ManualTestSupport.observeVoid(
                            () -> context.service.reset(EMAIL, CHALLENGE, CODE, NEW_PASSWORD, NEW_PASSWORD),
                            context.repository, context.crypto, context.mailer);
                });
    }

    private static Context context() {
        AuthRepository repository = mock(AuthRepository.class);
        AuthCrypto crypto = mock(AuthCrypto.class);
        RecoveryMailer mailer = mock(RecoveryMailer.class);
        when(crypto.token()).thenReturn(DUMMY_TOKEN);
        when(crypto.encode(DUMMY_TOKEN)).thenReturn(DUMMY_HASH);
        AuthService service = new AuthService(repository, crypto, mailer);
        clearInvocations(repository, crypto, mailer);
        return new Context(repository, crypto, mailer, service);
    }

    private static void arrangeLogin(Context context, String role) {
        when(context.repository.findByUsername(USERNAME)).thenReturn(account("ACTIVE", role));
        when(context.crypto.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);
        when(context.crypto.token()).thenReturn(SESSION_TOKEN);
    }

    private static void arrangeRecovery(Context context) {
        when(context.repository.findByEmail(EMAIL)).thenReturn(account("ACTIVE", "RECEPTIONIST"));
        when(context.crypto.token()).thenReturn(CHALLENGE);
        when(context.crypto.code()).thenReturn(CODE);
        when(context.crypto.encode(EMAIL + ":" + CODE)).thenReturn(CODE_HASH);
    }

    private static void arrangeReset(Context context, Reset reset) {
        when(context.repository.findByEmail(EMAIL)).thenReturn(account("ACTIVE", "RECEPTIONIST"));
        when(context.repository.findReset(USER_ID, AuthCrypto.hash(CHALLENGE))).thenReturn(reset);
    }

    private static Account account(String status, String role) {
        return new Account(USER_ID, USERNAME, EMAIL, PASSWORD_HASH, status, role,null,null);
    }

    private static Reset reset(Instant usedAt, Instant revokedAt) {
        return new Reset(RESET_ID, CODE_HASH, usedAt, revokedAt);
    }

    private static String printable(String value) {
        if (value == null) {
            return "null";
        }
        return "'" + value.replace("\n", "\\n").replace("\t", "\\t") + "'";
    }

    private static ManualTestCase test(
            String key,
            String documentId,
            String sourceId,
            String method,
            int row,
            String preconditions,
            String input,
            String expected,
            ManualTestCase.Executor executor) {
        return new ManualTestCase(
                "auth-service/" + key, documentId, sourceId, "AuthServiceTest", method, "Unit",
                "Personal Account Management!A" + row, preconditions, input, expected, executor);
    }

    private record Context(
            AuthRepository repository,
            AuthCrypto crypto,
            RecoveryMailer mailer,
            AuthService service) {
    }
}
