/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 13/10/2026
 * Description: Covers password policy validation and rejection cases.
 */

package com.gcmb.backend.manual.administration.auth.validator;

import com.gcmb.backend.administration.auth.validator.AuthValidation;
import com.gcmb.backend.manual.ManualTestCase;
import com.gcmb.backend.manual.ManualTestSuite;
import com.gcmb.backend.manual.ManualTestSupport;
import java.util.ArrayList;
import java.util.List;

public final class AuthPasswordPolicyManualTest {
    private static final String VALID = "Aa1!bcde";

    private AuthPasswordPolicyManualTest() {
    }

    public static ManualTestSuite suite() {
        List<ManualTestCase> cases = new ArrayList<>();
        cases.add(policy("lower-boundary", "UTC076", "UTC-PAM-CHANGE-PASSWORD-001.1",
                "acceptsPasswordAtEightCharacterLowerBoundary", 90, VALID,
                "Password and confirmation match at length 8", "Complete without exception"));
        cases.add(policy("upper-boundary", "UTC077", "UTC-PAM-CHANGE-PASSWORD-001.2",
                "acceptsPasswordAtSixtyFourCharacterUpperBoundary", 91, "Aa1!" + "b".repeat(60),
                "Password and confirmation match at length 64", "Complete without exception"));

        // UTC053 covers the same real validator mismatch through AuthService.reset.

        Object[][] lengths = {
                {"UTC078", "seven characters", "Aa1!bcd", 7, 92},
                {"UTC079", "sixty-five characters", "Aa1!" + "b".repeat(61), 65, 93},
                {"UTC080", "one hundred twenty-eight characters", "Aa1!" + "b".repeat(124), 128, 94},
                {"UTC081", "one hundred twenty-nine characters", "Aa1!" + "b".repeat(125), 129, 95}
        };
        for (Object[] data : lengths) {
            cases.add(policy("length-" + data[3], (String) data[0], null,
                    "rejectsPasswordOutsideEightToSixtyFourCharacters[" + data[1] + "]",
                    (Integer) data[4], (String) data[2],
                    "Actual length=" + data[3], "AuthFailure status=400"));
        }

        Object[][] groups = {
                {"UTC082", "uppercase letter", "aa1!bcde", 96},
                {"UTC083", "lowercase letter", "AA1!BCDE", 97},
                {"UTC084", "digit", "Aa!bcdef", 98},
                {"UTC085", "special character", "Aa1bcdef", 99}
        };
        for (Object[] data : groups) {
            cases.add(policy("missing-" + ((String) data[1]).replace(' ', '-'), (String) data[0], null,
                    "rejectsPasswordMissingRequiredCharacterGroup[" + data[1] + "]",
                    (Integer) data[3], (String) data[2],
                    "Password length=8", "AuthFailure status=400"));
        }

        Object[][] spaces = {
                {"UTC086", "leading space", " Aa1!bcde", 100},
                {"UTC087", "internal space", "Aa1! bcde", 101},
                {"UTC088", "trailing space", "Aa1!bcde ", 102}
        };
        for (Object[] data : spaces) {
            cases.add(policy("space-" + ((String) data[1]).replace(' ', '-'), (String) data[0], null,
                    "rejectsPasswordContainingSpace[" + data[1] + "]", (Integer) data[3], (String) data[2],
                    "Password and confirmation contain a " + data[1], "AuthFailure status=400"));
        }
        return new ManualTestSuite("Authentication", "AuthPasswordPolicy", cases);
    }

    private static ManualTestCase policy(
            String key,
            String documentId,
            String sourceId,
            String method,
            int row,
            String password,
            String preconditions,
            String expected) {
        return new ManualTestCase("password-policy/" + key,
                ManualTestCase.identifier(documentId, sourceId),
                "AuthPasswordPolicyTest",
                method,
                preconditions,
                "password=confirmation='" + password.replace(" ", "\\u0020") + "'; length=" + password.length(),
                expected,
                () -> ManualTestSupport.observeVoid(() -> AuthValidation.password(password, password)));
    }
}
