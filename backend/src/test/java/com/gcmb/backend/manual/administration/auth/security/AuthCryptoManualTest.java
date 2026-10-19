/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 13/10/2026
 * Description: Covers token generation, hashing and password verification behavior.
 */

package com.gcmb.backend.manual.administration.auth.security;

import com.gcmb.backend.administration.auth.security.AuthCrypto;
import com.gcmb.backend.administration.auth.validator.AuthValidation;
import com.gcmb.backend.manual.ManualTestCase;
import com.gcmb.backend.manual.ManualTestResult;
import com.gcmb.backend.manual.ManualTestSuite;
import com.gcmb.backend.manual.ManualTestSupport;
import java.util.ArrayList;
import java.util.List;

public final class AuthCryptoManualTest {
    private AuthCryptoManualTest() {
    }

    public static ManualTestSuite suite() {
        List<ManualTestCase> cases = new ArrayList<>();

        cases.add(test("hash-whitespace", "safeHashesPreserveWhitespaceAndDoNotAcceptPlaintext",
                "Input text includes leading and trailing spaces",
                "raw='  mat khau moi  '; trimmed='mat khau moi'; plaintext stored='plaintext'",
                "Original raw value matches its hash; trimmed and plaintext values do not; a second hash differs", () -> {
                    AuthCrypto crypto = new AuthCrypto();
                    String hash = crypto.encode("  mat khau moi  ");
                    String second = crypto.encode("  mat khau moi  ");
                    String actual = "matches original=" + crypto.matches("  mat khau moi  ", hash)
                            + System.lineSeparator() + "matches trimmed=" + crypto.matches("mat khau moi", hash)
                            + System.lineSeparator() + "accepts plaintext storage=" + crypto.matches("plaintext", "plaintext")
                            + System.lineSeparator() + "hashes equal=" + hash.equals(second);
                    return new ManualTestResult(actual, "None", "Two real PBKDF2 hashes were generated");
                }));

        cases.add(test("six-digit-codes", "codesHaveExactlySixAsciiDigitsIncludingLeadingZeros",
                "SecureRandom is available", "Generate 1000 recovery codes",
                "Every code matches [0-9]{6}; at least one generated sample starts with 0", () -> {
                    AuthCrypto crypto = new AuthCrypto();
                    int valid = 0;
                    int leadingZero = 0;
                    String first = null;
                    for (int index = 0; index < 1000; index++) {
                        String code = crypto.code();
                        if (first == null) {
                            first = code;
                        }
                        if (code.matches("[0-9]{6}")) {
                            valid++;
                        }
                        if (code.startsWith("0")) {
                            leadingZero++;
                        }
                    }
                    return new ManualTestResult(
                            "validFormatCount=" + valid + ", leadingZeroCount=" + leadingZero + ", firstSample=" + first,
                            "None", "1000 values generated from the production AuthCrypto instance");
                }));

        cases.add(test("valid-code", "strictCodeValidation[valid]", "No setup required", "code='038291'",
                "Method completes without exception", () -> ManualTestSupport.observeVoid(
                        () -> AuthValidation.code("038291"))));
        for (String code : new String[]{"12345", "1234567", "１２３４５６", "12345a", " 12345", "123\n45"}) {
            cases.add(test("invalid-code-" + cases.size(), "strictCodeValidation[" + printable(code) + "]",
                    "No setup required", "code=" + printable(code),
                    "AuthFailure is thrown", () -> ManualTestSupport.observeVoid(() -> AuthValidation.code(code))));
        }

        for (String blank : new String[]{"", "   ", "\t\n", "\u00a0\u00a0", "\uFEFF"}) {
            cases.add(test("blank-required-" + cases.size(),
                    "whitespaceOnlyAndDatabaseLimitsCannotBeBypassed[blank=" + printable(blank) + "]",
                    "No setup required", "required(value, password, mật khẩu, 128, false)",
                    "AuthFailure is thrown", () -> ManualTestSupport.observe(
                            () -> AuthValidation.required(blank, "password", "mật khẩu", 128, false))));
        }
        cases.add(test("email-nbsp", "whitespaceOnlyAndDatabaseLimitsCannotBeBypassed[email]",
                "No setup required", "email='NBSP user@example.com NBSP'",
                "Return user@example.com", () -> ManualTestSupport.observe(
                        () -> AuthValidation.email("\u00a0 user@example.com \u00a0"))));
        cases.add(test("username-limit", "whitespaceOnlyAndDatabaseLimitsCannotBeBypassed[username]",
                "No setup required", "username length=101, max=100",
                "AuthFailure is thrown", () -> ManualTestSupport.observe(
                        () -> AuthValidation.required("x".repeat(101), "username", "tên đăng nhập", 100, true))));

        return new ManualTestSuite("Authentication", "AuthCrypto", cases);
    }

    private static ManualTestCase test(
            String key,
            String method,
            String preconditions,
            String input,
            String expected,
            ManualTestCase.Executor executor) {
        return new ManualTestCase("auth-crypto/" + key,
                ManualTestCase.identifier(null, null),
                "AuthCryptoTest",
                method,
                preconditions,
                input,
                expected,
                executor);
    }

    private static String printable(String value) {
        return "'" + value.replace("\n", "\\n").replace("\t", "\\t") + "'";
    }
}
