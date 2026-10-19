/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 20/10/2026
 * Description: Supports BackendApplicationTests test execution and verification.
 */

package com.gcmb.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@SpringBootTest(properties = {
        "spring.datasource.url=${AUTH_TEST_DB_URL}",
        "spring.datasource.username=${AUTH_TEST_DB_USERNAME:gcmb_auth_test}",
        "spring.datasource.password=${AUTH_TEST_DB_PASSWORD:}",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.open-in-view=false",
        "spring.mail.host=localhost",
        "gcmb.auth.secure-cookie=false",
        "gcmb.auth.frontend-origin=http://localhost:3000",
        "gcmb.auth.mail-from=test@example.invalid"
})
@ActiveProfiles("auth-test")
@EnabledIfEnvironmentVariable(named = "AUTH_TEST_DB_URL", matches = ".+")
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
