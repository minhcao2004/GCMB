/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 15/10/2026
 * Description: Registers selectable manual test suites and rejects duplicate case keys.
 */

package com.gcmb.backend.manual;

import com.gcmb.backend.manual.administration.AccountAccessManualTest;
import com.gcmb.backend.manual.administration.AccountManualTest;
import com.gcmb.backend.manual.administration.AccountProfileManualTest;
import com.gcmb.backend.manual.administration.AuditManualTest;
import com.gcmb.backend.manual.administration.AuditSnapshotsManualTest;
import com.gcmb.backend.manual.administration.MutationAuditManualTest;
import com.gcmb.backend.manual.administration.AuditRepositoryManualTest;
import com.gcmb.backend.manual.auth.AuthChangePasswordManualTest;
import com.gcmb.backend.manual.auth.AuthCryptoManualTest;
import com.gcmb.backend.manual.auth.AuthPasswordPolicyManualTest;
import com.gcmb.backend.manual.auth.AuthServiceManualTest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ManualTestRegistry {
    private ManualTestRegistry() {
    }

    public static List<ManualTestSuite> suites() {
        List<ManualTestSuite> suites = List.of(
                AccountAccessManualTest.suite(),
                AccountProfileManualTest.suite(),
                AccountManualTest.suite(),
                AuditManualTest.suite(),
                AuditSnapshotsManualTest.suite(),
                MutationAuditManualTest.suite(),
                AuditRepositoryManualTest.suite(),
                AuthCryptoManualTest.suite(),
                AuthServiceManualTest.suite(),
                AuthPasswordPolicyManualTest.suite(),
                AuthChangePasswordManualTest.suite());
        validateUniqueKeys(suites);
        return suites;
    }

    private static void validateUniqueKeys(List<ManualTestSuite> suites) {
        Set<String> keys = new HashSet<>();
        for (ManualTestSuite suite : suites) {
            for (ManualTestCase testCase : suite.cases()) {
                if (!keys.add(testCase.key())) {
                    throw new IllegalStateException("Duplicate manual test key: " + testCase.key());
                }
            }
        }
    }
}
