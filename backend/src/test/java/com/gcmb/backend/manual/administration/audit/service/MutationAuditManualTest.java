/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 14/10/2026
 * Description: Covers transactional audit recording around data mutations.
 */

package com.gcmb.backend.manual.administration.audit.service;

import com.gcmb.backend.administration.audit.service.MutationAudit;
import com.gcmb.backend.administration.auth.dto.response.Identity;
import com.gcmb.backend.manual.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Mock SQL/transaction observations are not evidence of real PostgreSQL rollback. */
public final class MutationAuditManualTest {
    private static final UUID ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID NEXT = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID ACTOR = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final String BEFORE = "{\"name\":\"Before\",\"branch_id\":\"" + ID + "\"}";
    private static final String AFTER = "{\"name\":\"After\",\"branch_id\":\"" + ID + "\"}";
    private MutationAuditManualTest() {}

    public static ManualTestSuite suite() {
        List<ManualTestCase> cases = new ArrayList<>();
        cases.add(test(19, "create", "ROOM", "CREATE_ROOM; id=null; resultId returns ID",
            "Return ID; one audit insert with actor, ROOM, new ID, before=null, after=After and actorRole=BRANCH_MANAGER; commit requested."));
        cases.add(test(20, "update", "ROOM", "UPDATE_ROOM; before=Before; after=After",
            "Return operation result; before snapshot/lock precedes operation, then after snapshot, one insert with original ID and both values, then commit."));
        cases.add(test(21, "delete", "ROOM", "DELETE_ROOM; before=Before; after row absent",
            "One insert retains original entity ID and before snapshot; after argument=null; SQL retains actorRole; commit requested."));
        cases.add(test(22, "unchanged", "ROOM", "UPDATE_ROOM; identical snapshots",
            "Operation runs once; return its result; no audit insert; commit requested."));
        cases.add(test(23, "missing", "ROOM", "DELETE_ROOM; before and after absent; operation returns normally",
            "Return operation result; no audit insert because both snapshots are null."));
        cases.add(test(24, "unsupported", "UNSUPPORTED", "Unsupported entity type",
            "IllegalArgumentException: Unsupported audit entity; no operation, SQL or transaction calls."));
        cases.add(test(25, "no-actor", "ROOM", "Authentication=null",
            "IllegalStateException: authenticated actor required; no operation, SQL or transaction calls."));
        cases.add(test(26, "wrong-principal", "ROOM", "Authentication principal is a String, not Identity",
            "IllegalStateException: authenticated actor required; no operation, SQL or transaction calls."));
        cases.add(test(27, "operation-error", "ROOM", "Operation throws IllegalStateException(business failure)",
            "Propagate business failure; no after snapshot/insert; transaction manager receives rollback, not commit. Real DB rollback is not tested."));
        cases.add(test(28, "insert-error", "ROOM", "Different snapshots; db.update throws IllegalStateException(audit unavailable)",
            "Operation runs once; propagate audit unavailable; rollback requested, no commit. Real DB rollback is not tested."));
        cases.add(test(29, "versioned", "PRICE_RULE", "Original ID; resultId returns NEXT; new version snapshot",
            "After snapshot is read using NEXT; insert entity ID remains original ID; both snapshots retained; commit requested."));
        cases.add(test(30, "room-type", "ROOM_TYPE", "ROOM_TYPE update; snapshot contains combos/priceRules",
            "Snapshot SELECT includes combos and priceRules; insert retains both stubbed snapshots. Observe SQL text/arguments only; no live database."));
        cases.add(test(31, "booking", "BOOKING", "BOOKING update; nested event/reason/details",
            "Snapshot SELECT reads latest booking_event ordered created_at/id DESC; insert retains nested event. SQL execution not tested."));
        cases.add(test(32, "branch", "BRANCH", "BRANCH update; managerId in snapshots",
            "Snapshot SELECT includes branch_id=t.id and manager assignment; insert retains before/after managerId. SQL execution not tested."));
        return new ManualTestSuite("Administration", "MutationAudit", cases);
    }

    private static ManualTestCase test(int number, String key, String type, String input, String expected) {
        return new ManualTestCase("audit-mutation/" + key,
                ManualTestCase.identifier("UTC%03d".formatted(number), "UC-08/NFR Audit Trail"),
                "MutationAuditManualTest",
                "record(" + key + ")",
                "Real MutationAudit; mocked JdbcTemplate/transaction manager/operation; synthetic Identity; context restored after case.",
                input,
                expected,
                () -> observe(key, type));
    }

    @SuppressWarnings("unchecked")
    private static ManualTestResult observe(String key, String type) {
        JdbcTemplate db = mock(JdbcTemplate.class);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        Supplier<UUID> operation = mock(Supplier.class);
        when(operation.get()).thenReturn(ID);
        String before = BEFORE;
        String after = AFTER;
        if (key.equals("room-type")) { before = "{\"name\":\"A\",\"combos\":[],\"priceRules\":[]}"; after = "{\"name\":\"B\",\"combos\":[{\"name\":\"Combo\"}],\"priceRules\":[]}"; }
        if (key.equals("booking")) { before = "{\"status\":\"CONFIRMED\"}"; after = "{\"status\":\"CANCELLED\",\"event\":{\"type\":\"BOOKING_CANCELLED\",\"reason\":\"Customer cancelled\",\"details\":{}}}"; }
        if (key.equals("branch")) { before = "{\"branch_id\":\"" + ID + "\",\"managerId\":null}"; after = "{\"branch_id\":\"" + ID + "\",\"managerId\":\"" + ACTOR + "\"}"; }
        when(db.queryForList(anyString(), eq(String.class), eq(ID)))
            .thenReturn(List.of(before), key.equals("delete") ? List.of() : List.of(key.equals("unchanged") ? before : after));
        when(db.queryForList(anyString(), eq(UUID.class), eq(ID)))
            .thenReturn(List.of(ID), key.equals("delete") ? List.of() : List.of(ID));
        if (key.equals("create")) when(db.queryForList(anyString(), eq(String.class), eq(ID))).thenReturn(List.of(after));
        if (key.equals("missing")) {
            when(db.queryForList(anyString(), eq(String.class), eq(ID))).thenReturn(List.of());
            when(db.queryForList(anyString(), eq(UUID.class), eq(ID))).thenReturn(List.of());
        }
        if (key.equals("operation-error")) when(operation.get()).thenThrow(new IllegalStateException("business failure"));
        if (key.equals("insert-error")) when(db.update(anyString(), any(Object[].class))).thenThrow(new IllegalStateException("audit unavailable"));
        if (key.equals("versioned")) {
            when(operation.get()).thenReturn(NEXT);
            when(db.queryForList(anyString(), eq(UUID.class), eq(NEXT))).thenReturn(List.of(NEXT));
            when(db.queryForList(anyString(), eq(String.class), eq(NEXT))).thenReturn(List.of(after));
        }
        var saved = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        if (!key.equals("no-actor")) context.setAuthentication(new UsernamePasswordAuthenticationToken(
            key.equals("wrong-principal") ? "anonymous" : new Identity(ACTOR, "staff", "BRANCH_MANAGER", "/quan-ly-chi-nhanh", ID, "Test Branch"), null, List.of()));
        SecurityContextHolder.setContext(context);
        try {
            UUID id = key.equals("create") ? null : ID;
            String action = key.equals("create") ? "CREATE_ROOM" : key.equals("delete") || key.equals("missing") ? "DELETE_ROOM" : "UPDATE_" + type;
            return ManualTestSupport.observe(() -> new MutationAudit(db, manager).record(action, type, id, operation,
                key.equals("create") || key.equals("versioned") ? value -> value : null), db, manager, operation);
        } finally {
            SecurityContextHolder.setContext(saved);
        }
    }
}
