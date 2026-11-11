/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 11/11/2026
 * Description: Runs Equipment Category Management manual unit test cases.
 */
package com.gcmb.backend.manual.equipmentcategorymanagement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.inventory.dto.EquipmentCategoryDtos.Command;
import com.gcmb.backend.inventory.dto.EquipmentCategoryDtos.View;
import com.gcmb.backend.inventory.repository.EquipmentCategoryRepository;
import com.gcmb.backend.inventory.service.EquipmentCategoryService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.UUID;
import org.springframework.web.server.ResponseStatusException;

public final class EquipmentCategoryManagementManualRunner {
    private static final int EXPECTED_CASES = 27;

    static final UUID HEAD_OFFICE_ID = UUID.fromString("47570000-0000-4000-8000-000000000005");
    static final UUID BRANCH_MANAGER_ID = UUID.fromString("47570000-0000-4000-8000-000000000003");
    static final UUID BRANCH_ID = UUID.fromString("54590000-0000-4000-8000-000000000001");
    static final UUID OTHER_BRANCH_ID = UUID.fromString("54590000-0000-4000-8000-000000000009");
    static final UUID CATEGORY_ID = UUID.fromString("60600000-0000-4000-8000-000000000001");
    static final UUID NEW_CATEGORY_ID = UUID.fromString("60600000-0000-4000-8000-000000000002");
    static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-11-11T09:00:00+07:00");

    private EquipmentCategoryManagementManualRunner() {}

    public static void main(String[] args) {
        List<ManualTest> cases = allTests();
        if (args.length == 0) {
            menu(cases);
            return;
        }
        switch (args[0]) {
            case "--all" -> run(cases);
            case "--list" -> list(cases);
            case "--case" -> run(args.length < 2 ? List.of()
                    : cases.stream().filter(test -> test.id().equalsIgnoreCase(args[1])).toList());
            default -> System.out.println("Options: --all | --list | --case <ID>");
        }
    }

    private static List<ManualTest> allTests() {
        List<ManualTest> cases = new ArrayList<>();
        cases.addAll(CreateEquipmentCategoryManualCases.cases());
        cases.addAll(ViewEquipmentCategoriesManualCases.cases());
        cases.addAll(UpdateEquipmentCategoryManualCases.cases());
        cases.addAll(ChangeEquipmentCategoryStatusManualCases.cases());
        cases.addAll(DeleteEquipmentCategoryManualCases.cases());
        cases.sort(Comparator.comparing(ManualTest::id));
        if (cases.size() != EXPECTED_CASES) {
            throw new IllegalStateException("Expected " + EXPECTED_CASES + " cases but found " + cases.size());
        }
        if (cases.stream().map(ManualTest::id).distinct().count() != cases.size()) {
            throw new IllegalStateException("Duplicate Equipment Category Management test case ID");
        }
        return List.copyOf(cases);
    }

    private static void menu(List<ManualTest> cases) {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.println("\nEQUIPMENT CATEGORY MANAGEMENT MANUAL UNIT TEST");
                System.out.println("1. List cases\n2. Run all\n3. Run one case\n0. Exit");
                String choice = scanner.nextLine().trim();
                if ("0".equals(choice)) return;
                if ("1".equals(choice)) list(cases);
                else if ("2".equals(choice)) run(cases);
                else if ("3".equals(choice)) {
                    System.out.print("Test Case ID: ");
                    String id = scanner.nextLine().trim();
                    run(cases.stream().filter(test -> test.id().equalsIgnoreCase(id)).toList());
                }
            }
        }
    }

    private static void list(List<ManualTest> cases) {
        cases.forEach(test -> System.out.println(test.id() + " | " + test.useCase() + " | " + test.type()));
    }

    private static void run(List<ManualTest> cases) {
        if (cases.isEmpty()) {
            System.out.println("No matching test case.");
            return;
        }
        int passed = 0;
        for (ManualTest test : cases) {
            TestResult result = test.execute();
            if (result.passed()) passed++;
            System.out.printf("%n[%s] %s%nSource      : %s%nPrecondition: %s%nInput       : %s%nExpected    : %s%nActual      : %s%nVERDICT     : %s%n",
                    test.id(), test.useCase(), test.sourceMethod(), test.preconditions(), test.input(), test.expected(),
                    result.actual(), result.passed() ? "PASSED" : "FAILED");
        }
        System.out.printf("%nSUMMARY: total=%d, passed=%d, failed=%d%n", cases.size(), passed, cases.size() - passed);
    }

    static ManualTest test(String id, String useCase, String sourceMethod, String preconditions, String input,
            String expected, String type, String note, CheckedAction action) {
        return new ManualTest(id, useCase, sourceMethod, preconditions, input, expected, type, note, action);
    }

    static String note(String ucFile, String br, String msg, String production, String method) {
        StringBuilder value = new StringBuilder("Nguồn: ").append(ucFile).append(";");
        if (br != null && !br.isBlank()) value.append(" ").append(br).append(";");
        if (msg != null && !msg.isBlank()) value.append(" ").append(msg).append(";");
        value.append(" Flyway V14__seed_genz_branches_and_accounts.sql; Production: ")
                .append(production).append(". Phương pháp kiểm thử: ").append(method).append(".");
        return value.toString();
    }

    static Fixture fixture() {
        EquipmentCategoryRepository repository = mock(EquipmentCategoryRepository.class);
        EquipmentCategoryService service = new EquipmentCategoryService(repository);
        View existing = view(CATEGORY_ID, "EQ-AUDIO", "Thiết bị âm thanh", true, 2);
        View created = view(NEW_CATEGORY_ID, "EQ-LIGHT", "Thiết bị ánh sáng", true, 0);
        View updated = view(CATEGORY_ID, "EQ-AUDIO", "Thiết bị âm thanh sân khấu", true, 2);
        when(repository.list(isNull(), anyString(), nullable(Boolean.class))).thenReturn(List.of(existing));
        when(repository.create(isNull(), any(Command.class))).thenReturn(created);
        when(repository.update(isNull(), eq(CATEGORY_ID), any(Command.class))).thenReturn(updated);
        when(repository.setActive(isNull(), eq(CATEGORY_ID), anyBoolean())).thenAnswer(invocation ->
                view(CATEGORY_ID, "EQ-AUDIO", "Thiết bị âm thanh", invocation.getArgument(2), 2));
        when(repository.deleteUnused(isNull(), eq(CATEGORY_ID))).thenReturn(true);
        return new Fixture(repository, service);
    }

    static Identity headOfficeManager() {
        return new Identity(HEAD_OFFICE_ID, "qlcc", "HEAD_OFFICE_MANAGER", "/head-office", null, null);
    }

    static Identity branchManager() {
        return new Identity(BRANCH_MANAGER_ID, "qlcs.hoabang", "BRANCH_MANAGER", "/manager", BRANCH_ID,
                "GenZ Cinema Hoa Bằng");
    }

    static Identity receptionist() {
        return new Identity(BRANCH_MANAGER_ID, "letan.hoabang", "RECEPTIONIST", "/reception", BRANCH_ID,
                "GenZ Cinema Hoa Bằng");
    }

    static View view(UUID id, String code, String name, boolean active, long assetCount) {
        return new View(id, null, code, name, "Danh mục thiết bị dùng chung", active, assetCount, CREATED_AT);
    }

    static TestResult expectStatus(ThrowingRunnable action, int status, String message) {
        try {
            action.run();
            return check(false, "No exception; expected HTTP " + status + " and message: " + message);
        } catch (ResponseStatusException failure) {
            int actual = failure.getStatusCode().value();
            return check(actual == status && Objects.equals(message, failure.getReason()),
                    "HTTP " + actual + "; message=" + failure.getReason());
        } catch (Throwable failure) {
            return unexpected(failure);
        }
    }

    static TestResult check(boolean passed, String actual) { return new TestResult(passed, actual); }
    static TestResult unexpected(Throwable failure) {
        return new TestResult(false, "Unexpected " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
    }

    record Fixture(EquipmentCategoryRepository repository, EquipmentCategoryService service) {}
    record TestResult(boolean passed, String actual) {}
    record ManualTest(String id, String useCase, String sourceMethod, String preconditions, String input,
                      String expected, String type, String note, CheckedAction action) {
        ManualTest { Objects.requireNonNull(action, "action"); }
        TestResult execute() {
            try {
                TestResult result = action.run();
                return result == null ? check(false, "Test action returned no result") : result;
            } catch (Throwable failure) {
                return unexpected(failure);
            }
        }
    }

    @FunctionalInterface interface CheckedAction { TestResult run() throws Exception; }
    @FunctionalInterface interface ThrowingRunnable { void run() throws Exception; }
}
