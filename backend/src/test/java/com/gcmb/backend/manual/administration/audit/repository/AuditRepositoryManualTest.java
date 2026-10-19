/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 14/10/2026
 * Description: Covers audit repository queries, row mapping and snapshot sanitization.
 */

package com.gcmb.backend.manual.administration.audit.repository;

import com.gcmb.backend.administration.audit.repository.AuditRepository;
import com.gcmb.backend.manual.*;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Arrays;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

/** Exercises real repository/row mapper against stubs, not a PostgreSQL integration. */
public final class AuditRepositoryManualTest {
    private static final UUID ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private AuditRepositoryManualTest() {}

    public static ManualTestSuite suite() {
        List<ManualTestCase> cases = new ArrayList<>();
        cases.add(test(33, "list-filters", "list", "q=Room; action=UPDATE_ROOM; branch=ID; from=to=2026-10-15; page=2,size=10",
            "Count/select share filters; timestamp >=2026-10-14T17:00Z and <2026-10-15T17:00Z; LIMIT=10 OFFSET=20; total=7 and mapped row returned. Observe SQL code/name search and stable DESC sort; no database execution."));
        cases.add(test(34, "list-empty", "list", "q/action empty; branch/dates null; page=0,size=10; SQL stubs return count=0, items=[]",
            "Return items=[], total=0, page=0, size=10; SQL has no optional action/branch/date conditions; limit=10, offset=0."));
        cases.add(test(35, "detail-safe", "detail", "Row has new before/after JSON with nested secrets; stored actor_role=BRANCH_MANAGER",
            "Return mapped event, legacy Snapshot fields and safe beforeValues/afterValues; nested password/token removed; actorRole=BRANCH_MANAGER."));
        cases.add(test(36, "detail-missing", "detail", "SQL query returns no rows",
            "Repository returns null (service, not repository, owns HTTP-style 404 conversion)."));
        cases.add(test(37, "detail-deleted", "detail", "before={code:ROOM_A,name:Room A}; after=null; SQL projection entity_name=ROOM_A",
            "Return entityName=ROOM_A, retained beforeValues and empty afterValues; mapper does not require current entity row. Fallback SQL semantics need real DB test."));
        cases.add(test(38, "actions", "actions", "SQL stub returns CREATE_ROOM,UPDATE_ROOM",
            "Return the supplied action list; observe SELECT DISTINCT action with ORDER BY action. Distinct/sort execution requires real DB test."));
        cases.add(test(39, "detail-legacy", "detail", "before/after JSON null; legacy Snapshot columns present; actor_role=HEAD_OFFICE_MANAGER from SQL stub",
            "Keep legacy Snapshot values; beforeValues/afterValues empty; actorRole follows row projection. SQL role fallback itself is not tested by this stub."));
        cases.add(test(40, "list-offset", "list", "page=2147483647,size=100; all optional filters empty; count=0, items=[]",
            "Return empty page with original page/size; SQL OFFSET=214748364700 (long multiplication, no int overflow)."));
        return new ManualTestSuite("Administration", "AuditRepository (stubbed SQL)", cases);
    }

    private static ManualTestCase test(int number, String key, String method, String input, String expected) {
        return new ManualTestCase("audit-repository/" + key,
                ManualTestCase.identifier("UTC%03d".formatted(number), "UC-08"),
                "AuditRepositoryManualTest",
                method + "(" + key + ")",
                "Real repository; mocked JdbcTemplate/ResultSet. Tester inspects returned data and generated SQL/arguments; no live DB.",
                input,
                expected,
                () -> observe(key));
    }

    @SuppressWarnings("unchecked")
    private static ManualTestResult observe(String key) throws Exception {
        JdbcTemplate db = mock(JdbcTemplate.class);
        AuditRepository repository = new AuditRepository(db);
        ResultSet row = mock(ResultSet.class);
        when(row.getObject("id", UUID.class)).thenReturn(ID);
        when(row.getObject("entity_id", UUID.class)).thenReturn(ID);
        when(row.getTimestamp("created_at")).thenReturn(Timestamp.from(Instant.parse("2026-10-15T00:00:00Z")));
        when(row.getString("actor_name")).thenReturn("Synthetic Manager");
        when(row.getString("actor_username")).thenReturn("manager");
        when(row.getString("action")).thenReturn("UPDATE_ROOM");
        when(row.getString("entity_type")).thenReturn("ROOM");
        when(row.getString("entity_name")).thenReturn("ROOM_A");
        when(row.getString("branch_name")).thenReturn("Test Branch");
        when(row.getString("actor_role")).thenReturn("BRANCH_MANAGER");
        when(row.getString("before_username")).thenReturn("staff");
        when(row.getString("after_username")).thenReturn("staff");
        when(row.getString("before_role")).thenReturn("RECEPTIONIST");
        when(row.getString("after_role")).thenReturn("ACCOUNTANT");
        when(row.getString("before_values")).thenReturn("{\"code\":\"ROOM_A\",\"password\":\"secret\"}");
        when(row.getString("after_values")).thenReturn("{\"name\":\"Room B\",\"event\":{\"details\":{\"amount\":100,\"token\":\"secret\"}}}");
        if (key.equals("detail-deleted")) {
            when(row.getString("before_values")).thenReturn("{\"code\":\"ROOM_A\",\"name\":\"Room A\"}");
            when(row.getString("after_values")).thenReturn(null);
        }
        if (key.equals("detail-legacy")) {
            when(row.getString("before_values")).thenReturn(null);
            when(row.getString("after_values")).thenReturn(null);
            when(row.getString("actor_role")).thenReturn("HEAD_OFFICE_MANAGER");
        }
        when(db.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            if (key.equals("list-empty") || key.equals("list-offset") || key.equals("detail-missing")) return List.of();
            RowMapper<?> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(row, 0));
        });
        when(db.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
            .thenReturn(key.equals("list-filters") ? 7L : 0L);
        when(db.queryForList(anyString(), eq(String.class))).thenReturn(List.of("CREATE_ROOM", "UPDATE_ROOM"));
        if (key.equals("actions")) return ManualTestSupport.observe(repository::actions, db);
        if (key.startsWith("detail")) return ManualTestSupport.observe(() -> repository.detail(ID), db);
        boolean filters = key.equals("list-filters");
        ManualTestResult observed = ManualTestSupport.observe(() -> repository.list(filters ? "Room" : "", filters ? "UPDATE_ROOM" : "",
            filters ? ID : null, filters ? LocalDate.of(2026, 10, 15) : null,
            filters ? LocalDate.of(2026, 10, 15) : null, key.equals("list-offset") ? Integer.MAX_VALUE : filters ? 2 : 0,
            key.equals("list-offset") ? 100 : 10), db);
        var utcArguments = mockingDetails(db).getInvocations().stream()
            .flatMap(invocation -> Arrays.stream(invocation.getArguments()))
            .filter(Timestamp.class::isInstance).map(value -> ((Timestamp) value).toInstant()).toList();
        return new ManualTestResult(observed.actualResult(), observed.exception(),
            observed.sideEffects() + "\nTimestamp arguments (UTC instants, in call order): " + utcArguments);
    }
}
