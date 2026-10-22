/**
 * Copyright (c) 2026 SEP490-G12.
 * Author: CaoNM
 * Description: Manual observations for hashing, OTP formatting and Unicode email trimming.
 */
package com.gcmb.backend.manual.administration.auth.security;

import com.gcmb.backend.administration.auth.security.AuthCrypto;
import com.gcmb.backend.administration.auth.validator.AuthValidation;
import com.gcmb.backend.manual.ManualTestCase;
import com.gcmb.backend.manual.ManualTestResult;
import com.gcmb.backend.manual.ManualTestSuite;
import com.gcmb.backend.manual.ManualTestSupport;
import java.security.SecureRandom;
import java.util.List;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class AuthCryptoManualTest {
    private AuthCryptoManualTest() {}

    public static ManualTestSuite suite() {
        return new ManualTestSuite("Authentication", "AuthCrypto", List.of(
            test("hash-whitespace", "UTC134", "safeHashesPreserveWhitespaceAndDoNotAcceptPlaintext",
                "Real AuthCrypto; raw crypto input includes outer spaces. This checks the crypto primitive, not password-policy acceptance.",
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
                }),
            test("six-digit-codes", "UTC135", "codesFormatBoundaryValuesWithLeadingZeros",
                "Real AuthCrypto.code; only its SecureRandom dependency is a controlled test double. No random sampling verdict.",
                "SecureRandom.nextInt(1000000) returns 0, 38291, 999999 in that order",
                "Generated codes are [000000, 038291, 999999]; exactly three nextInt(1000000) calls; no exception", () -> {
                    AuthCrypto crypto = new AuthCrypto();
                    SecureRandom random = mock(SecureRandom.class);
                    when(random.nextInt(1_000_000)).thenReturn(0, 38_291, 999_999);
                    var field = AuthCrypto.class.getDeclaredField("random");
                    field.setAccessible(true);
                    field.set(crypto, random);
                    return ManualTestSupport.observe(
                        () -> List.of(crypto.code(), crypto.code(), crypto.code()), random);
                }),
            test("email-nbsp", "UTC136", "emailTrimsOuterNbspAndSpaces",
                "Call the real AuthValidation.email; no repository, mail service or database.",
                "email='\\u00a0 user@example.com \\u00a0' (NBSP and ordinary outer spaces)",
                "Return user@example.com; no exception", () -> ManualTestSupport.observe(
                    () -> AuthValidation.email("\u00a0 user@example.com \u00a0")))
        ));
    }

    private static ManualTestCase test(String key, String documentId, String method,
            String preconditions, String input, String expected, ManualTestCase.Executor executor) {
        return new ManualTestCase("auth-crypto/" + key,
            ManualTestCase.identifier(documentId, null), "AuthCryptoManualTest", method,
            preconditions, input, expected, executor);
    }
}
