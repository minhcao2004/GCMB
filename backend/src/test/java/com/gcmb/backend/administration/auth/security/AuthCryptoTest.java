package com.gcmb.backend.administration.auth.security;

import com.gcmb.backend.administration.auth.security.AuthCrypto;
import com.gcmb.backend.administration.auth.validator.AuthValidation;
import com.gcmb.backend.administration.auth.exception.AuthFailure;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuthCryptoTest {
    private final AuthCrypto crypto = new AuthCrypto();
    @Test void safeHashesPreserveWhitespaceAndDoNotAcceptPlaintext() {
        String hash = crypto.encode("  mat khau moi  ");
        assertTrue(crypto.matches("  mat khau moi  ", hash));
        assertFalse(crypto.matches("mat khau moi", hash));
        assertFalse(crypto.matches("plaintext", "plaintext"));
        assertNotEquals(hash, crypto.encode("  mat khau moi  "));
    }
    @Test void codesHaveExactlySixAsciiDigitsIncludingLeadingZeros() {
        boolean leadingZero = false;
        for (int i = 0; i < 1000; i++) {
            String code = crypto.code();
            assertTrue(code.matches("[0-9]{6}"));
            leadingZero |= code.startsWith("0");
        }
        assertTrue(leadingZero);
    }
    @Test void strictCodeValidation() {
        assertDoesNotThrow(() -> AuthValidation.code("038291"));
        for (String code : new String[]{"12345", "1234567", "１２３４５６", "12345a", " 12345", "123\n45"})
            assertThrows(AuthFailure.class, () -> AuthValidation.code(code));
    }
    @Test void whitespaceOnlyAndDatabaseLimitsCannotBeBypassed() {
        for (String blank : new String[]{"", "   ", "\t\n", "\u00a0\u00a0", "\uFEFF"})
            assertThrows(AuthFailure.class, () -> AuthValidation.required(blank, "password", "mật khẩu", 128, false));
        assertEquals("user@example.com", AuthValidation.email("\u00a0 user@example.com \u00a0"));
        assertThrows(AuthFailure.class, () -> AuthValidation.required("x".repeat(101), "username", "tên đăng nhập", 100, true));
    }
}
