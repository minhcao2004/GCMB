/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 13/10/2026
 * Description: Kiểm thử biên độ dài và các nhóm ký tự bắt buộc của mật khẩu mới theo BR-114.
 */

package com.gcmb.backend.auth.service;

import com.gcmb.backend.auth.exception.AuthFailure;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

class AuthPasswordPolicyTest {
    private static final String VALID_EIGHT_CHARACTERS = "Aa1!bcde";

    @Test
    void acceptsPasswordAtEightCharacterLowerBoundary() {
        assertDoesNotThrow(() -> AuthValidation.password(VALID_EIGHT_CHARACTERS, VALID_EIGHT_CHARACTERS));
    }

    @Test
    void acceptsPasswordAtSixtyFourCharacterUpperBoundary() {
        String password = "Aa1!" + "b".repeat(60);

        assertEquals(64, password.length());
        assertDoesNotThrow(() -> AuthValidation.password(password, password));
    }

    static Stream<Arguments> passwordsOutsideLengthBoundary() {
        return Stream.of(
            Arguments.of("seven characters", "Aa1!bcd", 7),
            Arguments.of("sixty-five characters", "Aa1!" + "b".repeat(61), 65),
            Arguments.of("one hundred twenty-eight characters", "Aa1!" + "b".repeat(124), 128),
            Arguments.of("one hundred twenty-nine characters", "Aa1!" + "b".repeat(125), 129)
        );
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("passwordsOutsideLengthBoundary")
    void rejectsPasswordOutsideEightToSixtyFourCharacters(String caseName, String password, int length) {
        assertEquals(length, password.length(), caseName);

        AuthFailure failure = assertThrows(AuthFailure.class,
            () -> AuthValidation.password(password, password));

        assertEquals(400, failure.status());
    }

    static Stream<Arguments> passwordsMissingRequiredCharacterGroup() {
        return Stream.of(
            Arguments.of("uppercase letter", "aa1!bcde"),
            Arguments.of("lowercase letter", "AA1!BCDE"),
            Arguments.of("digit", "Aa!bcdef"),
            Arguments.of("special character", "Aa1bcdef")
        );
    }

    @ParameterizedTest(name = "rejects password without {0}")
    @MethodSource("passwordsMissingRequiredCharacterGroup")
    void rejectsPasswordMissingRequiredCharacterGroup(String missingGroup, String password) {
        assertEquals(8, password.length(), missingGroup);

        AuthFailure failure = assertThrows(AuthFailure.class,
            () -> AuthValidation.password(password, password));

        assertEquals(400, failure.status());
    }

    static Stream<Arguments> passwordsContainingSpaces() {
        return Stream.of(
            Arguments.of("leading space", " Aa1!bcde"),
            Arguments.of("internal space", "Aa1! bcde"),
            Arguments.of("trailing space", "Aa1!bcde ")
        );
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("passwordsContainingSpaces")
    void rejectsPasswordContainingSpace(String caseName, String password) {
        AuthFailure failure = assertThrows(AuthFailure.class,
            () -> AuthValidation.password(password, password));

        assertEquals(400, failure.status(), caseName);
    }
}
