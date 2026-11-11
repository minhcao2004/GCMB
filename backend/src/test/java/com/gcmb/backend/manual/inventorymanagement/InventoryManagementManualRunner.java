/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 11/11/2026
 * Description: Runs Inventory Management manual unit test cases.
 */
package com.gcmb.backend.manual.inventorymanagement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.exception.AuthFailure;
import com.gcmb.backend.booking.service.RefundEvidenceStorageService;
import com.gcmb.backend.inventory.dto.InventoryDtos.InventoryItemView;
import com.gcmb.backend.inventory.dto.InventoryDtos.StockMovementView;
import com.gcmb.backend.inventory.dto.InventoryDtos.UnitOption;
import com.gcmb.backend.inventory.repository.InventoryRepository;
import com.gcmb.backend.inventory.repository.InventoryRepository.LockedItem;
import com.gcmb.backend.inventory.service.InventoryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.UUID;
import org.springframework.web.server.ResponseStatusException;

public final class InventoryManagementManualRunner {
    private static final int EXPECTED_CASES = 54;

    static final UUID ACTOR_ID = UUID.fromString("47570000-0000-4000-8000-000000000003");
    static final UUID BRANCH_ID = UUID.fromString("54590000-0000-4000-8000-000000000001");
    static final UUID ITEM_ID = UUID.fromString("54590000-0000-4000-8000-000000000002");
    static final UUID NEW_ITEM_ID = UUID.fromString("54590000-0000-4000-8000-000000000003");
    static final UUID UNIT_ID = UUID.fromString("54590000-0000-4000-8000-000000000004");
    static final UUID OTHER_UNIT_ID = UUID.fromString("54590000-0000-4000-8000-000000000005");
    static final UUID RECEIPT_ID = UUID.fromString("54590000-0000-4000-8000-000000000006");
    static final UUID LINE_ID = UUID.fromString("54590000-0000-4000-8000-000000000007");
    static final UUID MOVEMENT_ID = UUID.fromString("54590000-0000-4000-8000-000000000008");
    static final Instant NOW = Instant.parse("2026-11-11T02:00:00Z");

    private InventoryManagementManualRunner() {}

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
        cases.addAll(CreateInventoryItemManualCases.cases());
        cases.addAll(ViewInventoryManualCases.cases());
        cases.addAll(UpdateInventoryItemManualCases.cases());
        cases.addAll(DeleteInventoryItemManualCases.cases());
        cases.addAll(RecordStockReceiptManualCases.cases());
        cases.addAll(AdjustStockQuantityManualCases.cases());
        cases.sort(Comparator.comparing(ManualTest::id));
        if (cases.size() != EXPECTED_CASES) throw new IllegalStateException("Expected " + EXPECTED_CASES + " cases but found " + cases.size());
        long distinct = cases.stream().map(ManualTest::id).distinct().count();
        if (distinct != cases.size()) throw new IllegalStateException("Duplicate Inventory Management test case ID");
        return List.copyOf(cases);
    }

    private static void menu(List<ManualTest> cases) {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.println("\nINVENTORY MANAGEMENT MANUAL UNIT TEST");
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

    static String note(String ucFile, String br, String msg, String production) {
        StringBuilder value = new StringBuilder("Nguồn: ").append(ucFile).append(";");
        if (br != null && !br.isBlank()) value.append(" ").append(br).append(";");
        if (msg != null && !msg.isBlank()) value.append(" ").append(msg).append(";");
        value.append(" Flyway V14__seed_genz_branches_and_accounts.sql; Production: ")
                .append(production).append(". Phương pháp kiểm thử: White-box.");
        return value.toString();
    }

    static Fixture fixture() {
        InventoryRepository repository = mock(InventoryRepository.class);
        RefundEvidenceStorageService storage = mock(RefundEvidenceStorageService.class);
        InventoryService service = new InventoryService(repository, storage);
        UnitOption unit = new UnitOption(UNIT_ID, "PCS", "Cái");
        InventoryItemView item = item(ITEM_ID, UNIT_ID, true, new BigDecimal("10"), 2L, true);
        InventoryItemView created = item(NEW_ITEM_ID, UNIT_ID, true, BigDecimal.ZERO, 0L, false);
        when(repository.activeBranch(BRANCH_ID)).thenReturn(true);
        when(repository.units()).thenReturn(List.of(unit));
        when(repository.categories(BRANCH_ID)).thenReturn(List.of("Đồ uống"));
        when(repository.list(BRANCH_ID)).thenReturn(List.of(item));
        when(repository.get(BRANCH_ID, ITEM_ID)).thenReturn(item);
        when(repository.get(BRANCH_ID, NEW_ITEM_ID)).thenReturn(created);
        when(repository.create(any(), anyString(), anyString(), anyString(), any(), anyString(), any())).thenReturn(NEW_ITEM_ID);
        when(repository.lockItem(BRANCH_ID, ITEM_ID)).thenReturn(locked(true, UNIT_ID, new BigDecimal("10"), 2L));
        when(repository.nextReceiptCode()).thenReturn("NK-0001");
        when(repository.insertReceipt(any(), anyString(), anyString(), any(), any(), any(), any(), any(), any())).thenReturn(RECEIPT_ID);
        when(repository.insertReceiptLine(any(), any(), any())).thenReturn(LINE_ID);
        when(repository.insertAdjustment(any(), any(), any(), any(), any(), anyString(), any(), any(), any())).thenReturn(MOVEMENT_ID);
        when(repository.movement(BRANCH_ID, ITEM_ID, MOVEMENT_ID)).thenReturn(movement(new BigDecimal("2"), new BigDecimal("12")));
        when(repository.history(any(), any(), any(), any(), any(), any())).thenReturn(List.of(movement(new BigDecimal("2"), new BigDecimal("12"))));
        return new Fixture(repository, storage, service);
    }

    static Identity manager() {
        return new Identity(ACTOR_ID, "qlcs.hoabang", "BRANCH_MANAGER", "/manager", BRANCH_ID, "GenZ Cinema Hoa Bằng");
    }

    static Identity receptionist() {
        return new Identity(ACTOR_ID, "letan.hoabang", "RECEPTIONIST", "/reception", BRANCH_ID, "GenZ Cinema Hoa Bằng");
    }

    static InventoryItemView item(UUID id, UUID unitId, boolean active, BigDecimal quantity, long version, boolean movements) {
        return new InventoryItemView(id, "INV-001", "Nước suối", "Đồ uống", unitId, "Cái", "AUTO_ON_SALE",
                quantity, new BigDecimal("2"), version, active, movements, "AVAILABLE");
    }

    static LockedItem locked(boolean active, UUID unitId, BigDecimal quantity, long version) {
        return new LockedItem(ITEM_ID, unitId, active, quantity, version);
    }

    static StockMovementView movement(BigDecimal delta, BigDecimal after) {
        return new StockMovementView(MOVEMENT_ID, NOW, "ADJUSTMENT", delta, after, null, "DC-545900 · Kiểm kê",
                "Quản lý cơ sở - Hoa Bằng", "Kiểm kê", null, null);
    }

    static TestResult expectStatus(ThrowingRunnable action, int status, String message) {
        try {
            action.run();
            return check(false, "No exception; expected HTTP " + status + " and message: " + message);
        } catch (AuthFailure failure) {
            return check(failure.status() == status && Objects.equals(message, failure.getMessage()),
                    "HTTP " + failure.status() + "; message=" + failure.getMessage());
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

    record Fixture(InventoryRepository repository, RefundEvidenceStorageService storage, InventoryService service) {}
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
