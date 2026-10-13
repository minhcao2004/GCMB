/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: Kiểm thử API và SQL trên PostgreSQL; mọi dữ liệu thử được rollback sau mỗi test.
 */
package com.gcmb.backend.administration.accounts;

import com.gcmb.backend.administration.accounts.dto.AccountAccessDtos.*;
import com.gcmb.backend.administration.accounts.service.AccountAccessService;
import com.gcmb.backend.administration.accounts.service.AccountProfileService;
import com.gcmb.backend.administration.accounts.dto.AccountProfileDtos.UpdateProfile;
import com.gcmb.backend.administration.auth.security.AuthCrypto;
import com.gcmb.backend.administration.audit.service.AuditService;
import com.gcmb.backend.administration.auth.dto.response.Identity;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@ActiveProfiles("auth-test")
@EnabledIfEnvironmentVariable(named = "AUTH_TEST_DB_URL", matches = ".+")
@Transactional
class AccountAccessIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired AccountAccessService access;
    @Autowired AuditService audit;
    @Autowired AccountProfileService profiles;
    @Autowired AuthCrypto crypto;
    @Autowired WebApplicationContext context;
    private UUID actor;
    private UUID target;
    private UUID employee;
    private UUID branch;
    private String username;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        for (String role : List.of("HEAD_OFFICE_MANAGER", "RECEPTIONIST", "ACCOUNTANT")) {
            db.update("INSERT INTO gcmb.role(code,name) VALUES (?,?) ON CONFLICT(code) DO NOTHING", role, role);
        }
        actor = createUser("HEAD_OFFICE_MANAGER");
        target = createUser("RECEPTIONIST");
        username = db.queryForObject("SELECT username FROM gcmb.user_account WHERE id=?", String.class, target);
        employee = UUID.randomUUID();
        branch = UUID.randomUUID();
        db.update("""
                INSERT INTO gcmb.branch(id,code,name,address,timezone,status)
                VALUES (?,?,'Test Branch','Test','Asia/Ho_Chi_Minh','ACTIVE')
                """, branch, "test_" + branch);
        db.update("UPDATE gcmb.user_account SET branch_id=? WHERE id=?", branch, target);
        db.update("""
                INSERT INTO gcmb.employee(id,code,full_name,hired_on,status,user_id)
                VALUES (?,?,'Test Employee',CURRENT_DATE,'ACTIVE',?)
                """, employee, "test_" + employee, target);
        db.update("""
                INSERT INTO gcmb.auth_token(user_id,token_hash,kind,expires_at)
                VALUES (?,?,'SESSION',CURRENT_TIMESTAMP + interval '1 day')
                """, target, UUID.randomUUID().toString());
    }

    private UUID createUser(String role) {
        UUID id = UUID.randomUUID();
        String name = "access_test_" + id;
        db.update("""
                INSERT INTO gcmb.user_account(id,username,email,password_hash,role_id,status,failed_login_count)
                SELECT ?,?,?,'test-hash',id,'ACTIVE',0 FROM gcmb.role WHERE code=?
                """, id, name, name + "@example.invalid", role);
        return id;
    }

    private UsernamePasswordAuthenticationToken manager() {
        return new UsernamePasswordAuthenticationToken(
                new Identity(actor, "manager", "HEAD_OFFICE_MANAGER", "/quan-ly-he-thong"),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_HEAD_OFFICE_MANAGER"))
        );
    }

    @Test
    void roleChangeWritesSnapshotAndRevokesToken() {
        var result = access.setRole(target, new SetRole("ACCOUNTANT", null), actor);
        assertEquals("ACCOUNTANT", result.role());
        assertNull(result.branchId());
        assertEquals(0, db.queryForObject(
                "SELECT count(*) FROM gcmb.auth_token WHERE user_id=? AND revoked_at IS NULL", Integer.class, target));
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        var page = audit.list(username, "SET_ACCOUNT_ROLE", null, today, today, 0, 10);
        assertEquals(1, page.total());
        var detail = audit.detail(page.items().get(0).id());
        assertEquals("RECEPTIONIST", detail.before().role());
        assertEquals("ACCOUNTANT", detail.after().role());
        assertEquals("Toàn chuỗi", detail.after().branchName());
        assertTrue(access.permissions("ACCOUNTANT") != null);
    }

    @Test
    void deactivationKeepsEmployeeAndCanBeFilteredByBranch() throws Exception {
        mvc.perform(post("/api/v1/head-office/accounts/" + target + "/deactivate")
                .with(authentication(manager())).with(csrf())
                .contentType("application/json").content("{\"reason\":\"Test revoke\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));
        assertEquals(target, db.queryForObject("SELECT user_id FROM gcmb.employee WHERE id=?", UUID.class, employee));
        assertEquals(0, db.queryForObject(
                "SELECT count(*) FROM gcmb.auth_token WHERE user_id=? AND revoked_at IS NULL", Integer.class, target));
        var page = audit.list(username, "DEACTIVATE_USER_ACCOUNT", branch, null, null, 0, 10);
        assertEquals(1, page.total());
        assertEquals("Test revoke", audit.detail(page.items().get(0).id()).event().reason());
        mvc.perform(get("/api/v1/head-office/audit-logs/" + page.items().get(0).id())
                .with(authentication(manager())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.after.status").value("REVOKED"))
                .andExpect(jsonPath("$.after.password_hash").doesNotExist());
    }

    @Test
    void endpointsRequireManagerAndCsrf() throws Exception {
        mvc.perform(get("/api/v1/head-office/audit-logs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/head-office/audit-logs").with(user("staff").roles("RECEPTIONIST")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/head-office/accounts/" + target + "/role")
                .with(user("staff").roles("RECEPTIONIST")).with(csrf())
                .contentType("application/json").content("{\"role\":\"ACCOUNTANT\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/head-office/accounts/" + target + "/deactivate")
                .with(authentication(manager())).contentType("application/json")
                .content("{\"reason\":\"test\"}"))
                .andExpect(status().isForbidden());
    }

    private UsernamePasswordAuthenticationToken staff() {
        return new UsernamePasswordAuthenticationToken(
                new Identity(target, username, "RECEPTIONIST", "/le-tan"),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_RECEPTIONIST"))
        );
    }

    @Test
    void profileApiOnlyUpdatesSessionOwnerAndRecordsSafeSnapshot() throws Exception {
        mvc.perform(get("/api/v1/account/profile").with(authentication(staff())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.id").value(target.toString()))
                .andExpect(jsonPath("$.linkedEmployee").value(true))
                .andExpect(jsonPath("$.password_hash").doesNotExist());
        String email = db.queryForObject("SELECT email FROM gcmb.user_account WHERE id=?", String.class, target);
        // Dữ liệu thừa không được phép đổi đối tượng, quyền hoặc trạng thái tài khoản.
        mvc.perform(post("/api/v1/account/profile").with(authentication(staff())).with(csrf())
                .contentType("application/json").content("""
                        {"fullName":"Updated Employee","phone":"0901234567","email":"%s",
                         "id":"%s","role":"HEAD_OFFICE_MANAGER","status":"REVOKED"}
                        """.formatted(email, actor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Employee"))
                .andExpect(jsonPath("$.account.role").value("RECEPTIONIST"))
                .andExpect(jsonPath("$.account.status").value("ACTIVE"));
        assertEquals("Updated Employee", db.queryForObject(
                "SELECT full_name FROM gcmb.employee WHERE id=?", String.class, employee));
        var page = audit.list(username, "UPDATE_ACCOUNT_INFORMATION", branch, null, null, 0, 10);
        assertEquals(1, page.total());
        var detail = audit.detail(page.items().get(0).id());
        assertEquals("Test Employee", detail.before().fullName());
        assertEquals("0901234567", detail.after().phone());
        assertEquals(email, detail.after().email());
    }

    @Test
    void profileEmailChangeRevokesRecoveryCodesButKeepsSession() {
        db.update("UPDATE gcmb.user_account SET password_hash=? WHERE id=?", crypto.encode("123"), target);
        db.update("""
                INSERT INTO gcmb.auth_token(user_id,token_hash,kind,expires_at)
                VALUES (?,?,'PASSWORD_RESET',CURRENT_TIMESTAMP + interval '1 day')
                """, target, UUID.randomUUID().toString());
        String email = "updated_" + target + "@example.com";
        var result = profiles.update(target, new UpdateProfile("Test Employee", null, email, "123"));
        assertEquals(email, result.account().email());
        assertEquals(0, db.queryForObject("""
                SELECT count(*) FROM gcmb.auth_token
                WHERE user_id=? AND kind='PASSWORD_RESET' AND revoked_at IS NULL
                """, Integer.class, target));
        assertEquals(1, db.queryForObject("""
                SELECT count(*) FROM gcmb.auth_token
                WHERE user_id=? AND kind='SESSION' AND revoked_at IS NULL
                """, Integer.class, target));
        assertFalse(db.queryForObject("""
                SELECT before_data::text || after_data::text FROM gcmb.audit_event
                WHERE entity_id=? AND action='UPDATE_ACCOUNT_INFORMATION'
                """, String.class, target).contains("currentPassword"));
    }

    @Test
    void profileRequiresLoginCsrfAndSupportsAccountWithoutEmployee() throws Exception {
        mvc.perform(get("/api/v1/account/profile")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/account/profile").with(authentication(staff()))
                .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/account/profile").with(authentication(manager())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedEmployee").value(false));
        for (String role : List.of("BRANCH_MANAGER", "ACCOUNTANT")) {
            var principal = new UsernamePasswordAuthenticationToken(
                    new Identity(target, username, role, "/"),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role))
            );
            mvc.perform(get("/api/v1/account/profile").with(authentication(principal)))
                    .andExpect(status().isOk());
        }
    }
}
