/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 14/10/2026
 * Description: Covers audit search, detail and access-control behavior.
 */

package com.gcmb.backend.manual.administration;

import com.gcmb.backend.administration.audit.dto.AuditDtos.AuditDetail;
import com.gcmb.backend.administration.audit.dto.AuditDtos.AuditEntry;
import com.gcmb.backend.administration.audit.dto.AuditDtos.AuditPage;
import com.gcmb.backend.administration.audit.dto.AuditDtos.Snapshot;
import com.gcmb.backend.administration.audit.repository.AuditRepository;
import com.gcmb.backend.administration.audit.service.AuditService;
import com.gcmb.backend.manual.ManualTestCase;
import com.gcmb.backend.manual.ManualTestSuite;
import com.gcmb.backend.manual.ManualTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class AuditManualTest {
    private static final UUID BRANCH_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID AUDIT_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");

    private AuditManualTest() {
    }

    public static ManualTestSuite suite() {
        List<ManualTestCase> cases = new ArrayList<>();

        cases.add(test("list-valid", "UTC001", "listTrimsSearchTextAndDelegatesEveryFilterWithoutChangingResult", 13,
                "Repository returns a matching AuditPage",
                "q='  manager  ', action=SET_ACCOUNT_ROLE, branchId, from=2026-10-01, to=2026-10-13, page=2, size=25",
                "Trim q, delegate filters unchanged and return the same AuditPage", () -> {
                    AuditRepository repository = mock(AuditRepository.class);
                    AuditPage page = new AuditPage(List.of(entry()), 1, 2, 25);
                    when(repository.list("manager", "SET_ACCOUNT_ROLE", BRANCH_ID,
                            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 13), 2, 25)).thenReturn(page);
                    return ManualTestSupport.observe(
                            () -> new AuditService(repository).list("  manager  ", "SET_ACCOUNT_ROLE", BRANCH_ID,
                                    LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 13), 2, 25), repository);
                }));

        cases.add(test("list-boundaries", "UTC002", "listAcceptsDocumentedTechnicalBoundaries", 14,
                "Repository supports documented technical boundaries",
                "q length=200, action length=60, to=9999-01-01, page=0, size=100",
                "Accept boundary values, delegate and return the same AuditPage", () -> {
                    AuditRepository repository = mock(AuditRepository.class);
                    String q = "q".repeat(200);
                    String action = "A".repeat(60);
                    LocalDate to = LocalDate.of(9999, 1, 1);
                    AuditPage page = new AuditPage(List.of(), 0, 0, 100);
                    when(repository.list(q, action, null, null, to, 0, 100)).thenReturn(page);
                    return ManualTestSupport.observe(
                            () -> new AuditService(repository).list(q, action, null, null, to, 0, 100), repository);
                }));

        Object[][] filters = {
                {"UTC003", "negative page", "", "", -1, 10, 15},
                {"UTC004", "zero size", "", "", 0, 0, 16},
                {"UTC005", "size above maximum", "", "", 0, 101, 17},
                {"UTC006", "search text above maximum", "q".repeat(201), "", 0, 10, 18},
                {"UTC007", "action above maximum", "", "A".repeat(61), 0, 10, 19}
        };
        for (Object[] data : filters) {
            String id = (String) data[0];
            String label = (String) data[1];
            String q = (String) data[2];
            String action = (String) data[3];
            int page = (Integer) data[4];
            int size = (Integer) data[5];
            int row = (Integer) data[6];
            cases.add(test("list-invalid-" + label.replace(' ', '-'), id,
                    "listRejectsInvalidPaginationOrTextLengthBeforeRepositoryCall[" + label + "]", row,
                    "No dependency setup required",
                    "q length=" + q.length() + ", action length=" + action.length() + ", page=" + page + ", size=" + size,
                    "AuthFailure status=400 with invalid-filter message; repository is not called", () -> {
                        AuditRepository repository = mock(AuditRepository.class);
                        return ManualTestSupport.observe(
                                () -> new AuditService(repository).list(q, action, null, null, null, page, size), repository);
                    }));
        }

        cases.add(test("date-order", "UTC008", "listRejectsDateRangeWhenStartIsAfterEnd", 20,
                "No dependency setup required", "from=2026-10-14, to=2026-10-13",
                "AuthFailure status=400 with date-order message; repository is not called", () -> {
                    AuditRepository repository = mock(AuditRepository.class);
                    return ManualTestSupport.observe(
                            () -> new AuditService(repository).list("", "", null,
                                    LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 13), 0, 10), repository);
                }));

        cases.add(test("date-year", "UTC009", "listRejectsEndDateOutsideSupportedDatabaseYear", 21,
                "No dependency setup required", "to=+10000-01-01",
                "AuthFailure status=400 with invalid-end-date message; repository is not called", () -> {
                    AuditRepository repository = mock(AuditRepository.class);
                    return ManualTestSupport.observe(
                            () -> new AuditService(repository).list("", "", null, null,
                                    LocalDate.of(10_000, 1, 1), 0, 10), repository);
                }));

        cases.add(test("actions", "UTC010", "actionsReturnsRepositoryValuesWithoutModification", 23,
                "Repository returns action names", "No parameters",
                "Return the same action list and call repository.actions", () -> {
                    AuditRepository repository = mock(AuditRepository.class);
                    List<String> actions = List.of("DEACTIVATE_USER_ACCOUNT", "SET_ACCOUNT_ROLE");
                    when(repository.actions()).thenReturn(actions);
                    return ManualTestSupport.observe(() -> new AuditService(repository).actions(), repository);
                }));

        cases.add(test("detail-found", "UTC011", "detailReturnsExistingReadOnlyProjection", 24,
                "Audit entry exists", "audit id",
                "Return the same AuditDetail including beforeValues, afterValues and actorRole; call repository.detail", () -> {
                    AuditRepository repository = mock(AuditRepository.class);
                    AuditDetail detail = detail();
                    when(repository.detail(AUDIT_ID)).thenReturn(detail);
                    return ManualTestSupport.observe(() -> new AuditService(repository).detail(AUDIT_ID), repository);
                }));

        cases.add(test("detail-missing", "UTC012", "detailReturnsNotFoundWhenAuditEntryDoesNotExist", 25,
                "repository.detail returns null", "unknown audit id",
                "AuthFailure status=404 with not-found message; repository.actions is not called", () -> {
                    AuditRepository repository = mock(AuditRepository.class);
                    when(repository.detail(AUDIT_ID)).thenReturn(null);
                    return ManualTestSupport.observe(() -> new AuditService(repository).detail(AUDIT_ID), repository);
                }));

        return new ManualTestSuite("Administration", "AuditService", cases);
    }

    private static AuditEntry entry() {
        return new AuditEntry(
                AUDIT_ID, Instant.parse("2026-10-13T00:00:00Z"), "Head Office Manager", "manager",
                "SET_ACCOUNT_ROLE", "USER_ACCOUNT", UUID.fromString("30000000-0000-0000-0000-000000000003"),
                "staff", "Toan chuoi", null);
    }

    private static AuditDetail detail() {
        Snapshot before = new Snapshot(
                "staff", "RECEPTIONIST", "Ho Tay", "ACTIVE", "Nhan vien", "staff@example.com", "0900000000");
        Snapshot after = new Snapshot(
                "staff", "ACCOUNTANT", "Toan chuoi", "ACTIVE", "Nhan vien", "staff@example.com", "0900000000");
        return new AuditDetail(entry(), before, after,
                Map.of("username", "staff", "role", "RECEPTIONIST"),
                Map.of("username", "staff", "role", "ACCOUNTANT"), "HEAD_OFFICE_MANAGER");
    }

    private static ManualTestCase test(
            String key,
            String documentId,
            String method,
            int row,
            String preconditions,
            String input,
            String expected,
            ManualTestCase.Executor executor) {
        return new ManualTestCase(
                "audit/" + key, documentId, "UC-08", "AuditManualTest", method, "Unit",
                "Audit Log Management!A" + row, preconditions, input, expected, executor);
    }
}
