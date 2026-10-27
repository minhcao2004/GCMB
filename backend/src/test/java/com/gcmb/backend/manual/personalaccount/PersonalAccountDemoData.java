/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 27/10/2026
 * Description: Reuses the Personal Account identities seeded by Flyway V14 and V15.
 */
package com.gcmb.backend.manual.personalaccount;

import com.gcmb.backend.accounts.dto.AccountDtos.AccountView;
import com.gcmb.backend.accounts.dto.AccountProfileDtos.Profile;
import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.repository.model.Account;
import java.util.UUID;

public final class PersonalAccountDemoData {
    public static final UUID RECEPTIONIST_ID = UUID.fromString("47570000-0000-4000-8000-000000000001");
    public static final UUID BRANCH_MANAGER_ID = UUID.fromString("47570000-0000-4000-8000-000000000003");
    public static final UUID HEAD_OFFICE_MANAGER_ID = UUID.fromString("47570000-0000-4000-8000-000000000005");
    public static final UUID ACCOUNTANT_ID = UUID.fromString("47570000-0000-4000-8000-000000000006");
    public static final UUID HOA_BANG_BRANCH_ID = UUID.fromString("47570000-0000-4000-8000-000000000101");

    public static final String USERNAME = "letan.hoabang";
    public static final String EMAIL = "letan.hoabang@example.invalid";
    public static final String CURRENT_PASSWORD = "Demo@123";
    public static final String NEW_PASSWORD = "Moi@1234";
    public static final String PASSWORD_HASH = "{pbkdf2-gcmb}demo-fixture-hash";
    public static final String TOKEN = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopq";
    public static final String CHALLENGE = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefg";
    public static final String CODE = "123456";

    private PersonalAccountDemoData() {
    }

    public static Account receptionistAccount() {
        return account(RECEPTIONIST_ID, USERNAME, EMAIL, "ACTIVE", "RECEPTIONIST", HOA_BANG_BRANCH_ID,
                "GenZ Cinema Hoa Bằng");
    }

    public static Account account(UUID id, String username, String email, String status, String role,
            UUID branchId, String branchName) {
        return new Account(id, username, email, PASSWORD_HASH, status, role, branchId, branchName);
    }

    public static Identity receptionistIdentity() {
        return new Identity(RECEPTIONIST_ID, USERNAME, "RECEPTIONIST", "/reception", HOA_BANG_BRANCH_ID,
                "GenZ Cinema Hoa Bằng");
    }

    public static Profile linkedProfile() {
        AccountView account = new AccountView(RECEPTIONIST_ID, "Lễ tân - Hoa Bằng", "GENZ-REC-HOA-BANG",
                USERNAME, EMAIL, "RECEPTIONIST", HOA_BANG_BRANCH_ID, "GenZ Cinema Hoa Bằng", "ACTIVE");
        return new Profile(account, "Lễ tân - Hoa Bằng", "0877155379", true);
    }

    public static Profile unlinkedProfile() {
        AccountView account = new AccountView(HEAD_OFFICE_MANAGER_ID, null, null, "qlcc",
                "qlcc@example.invalid", "HEAD_OFFICE_MANAGER", null, null, "ACTIVE");
        return new Profile(account, null, null, false);
    }
}
