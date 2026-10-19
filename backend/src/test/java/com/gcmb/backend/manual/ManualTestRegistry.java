/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 13/10/2026
 * Description: Registers selectable manual test suites and rejects duplicate case keys.
 */

package com.gcmb.backend.manual;

import com.gcmb.backend.manual.administration.accounts.service.AccountAccessManualTest;
import com.gcmb.backend.manual.administration.accounts.service.AccountManualTest;
import com.gcmb.backend.manual.administration.accounts.service.AccountProfileManualTest;
import com.gcmb.backend.manual.administration.audit.repository.AuditRepositoryManualTest;
import com.gcmb.backend.manual.administration.audit.service.AuditManualTest;
import com.gcmb.backend.manual.administration.audit.service.AuditSnapshotsManualTest;
import com.gcmb.backend.manual.administration.audit.service.MutationAuditManualTest;
import com.gcmb.backend.manual.administration.auth.security.AuthCryptoManualTest;
import com.gcmb.backend.manual.administration.auth.service.AuthChangePasswordManualTest;
import com.gcmb.backend.manual.administration.auth.service.AuthOtpExpiryManualTest;
import com.gcmb.backend.manual.administration.auth.service.AuthServiceManualTest;
import com.gcmb.backend.manual.administration.auth.validator.AuthPasswordPolicyManualTest;
import com.gcmb.backend.manual.administration.branch.service.BranchAdministrationManualTest;
import com.gcmb.backend.manual.administration.branch.service.BranchRoomManualTest;
import com.gcmb.backend.manual.administration.pricing.service.RoomCatalogManualTest;
import com.gcmb.backend.manual.booking.service.BookingManualTest;
import com.gcmb.backend.manual.booking.service.BookingPricingManualTest;
import com.gcmb.backend.manual.booking.service.CheckoutManualTest;
import com.gcmb.backend.manual.booking.service.DepositManagementManualTest;
import com.gcmb.backend.manual.booking.service.RoomStayManualTest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ManualTestRegistry {
    private ManualTestRegistry() {
    }

    public static List<ManualTestSuite> suites() {
        List<ManualTestSuite> suites = new ArrayList<>(List.of(
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
            AuthChangePasswordManualTest.suite(),
            AuthOtpExpiryManualTest.suite()));
        suites.addAll(BranchAdministrationManualTest.suites());
        suites.addAll(BranchRoomManualTest.suites());
        suites.addAll(RoomCatalogManualTest.suites());
        suites.addAll(BookingManualTest.suites());
        suites.addAll(BookingPricingManualTest.suites());
        suites.addAll(RoomStayManualTest.suites());
        suites.addAll(CheckoutManualTest.suites());
        suites.addAll(DepositManagementManualTest.suites());
        validateUniqueKeys(suites);
        Map<String,ManualTestSuite> grouped=new LinkedHashMap<>();
        for(ManualTestSuite suite:suites) {
            String module=suite.name().equals("Room Management")?"Administration":suite.module();
            String key=module+"/"+suite.name();
            ManualTestSuite previous=grouped.get(key);
            List<ManualTestCase> cases=new ArrayList<>();
            if(previous!=null)cases.addAll(previous.cases());
            cases.addAll(suite.cases());
            grouped.put(key,new ManualTestSuite(module,suite.name(),cases));
        }
        return List.copyOf(grouped.values());
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
