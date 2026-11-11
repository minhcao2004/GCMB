/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 11/11/2026
 * Description: Runs Food Service Management manual unit tests with spreadsheet test case IDs.
 */
package com.gcmb.backend.manual.foodservicemanagement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.security.AuthCrypto;
import com.gcmb.backend.booking.exception.BookingFailure;
import com.gcmb.backend.booking.service.RefundEvidenceStorageService;
import com.gcmb.backend.inventory.dto.FoodOrderDtos.OrderItem;
import com.gcmb.backend.inventory.dto.FoodOrderDtos.OrderView;
import com.gcmb.backend.inventory.dto.MenuItemDtos.MenuItemView;
import com.gcmb.backend.inventory.repository.FoodOrderRepository;
import com.gcmb.backend.inventory.repository.FoodOrderRepository.ActiveRoom;
import com.gcmb.backend.inventory.repository.FoodOrderRepository.PendingLine;
import com.gcmb.backend.inventory.repository.FoodOrderRepository.RoomQrSession;
import com.gcmb.backend.inventory.repository.FoodOrderRepository.SellableItem;
import com.gcmb.backend.inventory.repository.MenuItemRepository;
import com.gcmb.backend.inventory.service.FoodOrderService;
import com.gcmb.backend.inventory.service.MenuItemService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

public final class FoodServiceManagementManualRunner {
    private static final int EXPECTED_CASES = 74;

    static final UUID ACTOR_ID = UUID.fromString("45500000-0000-4000-8000-000000000001");
    static final UUID OTHER_ACTOR_ID = UUID.fromString("45500000-0000-4000-8000-000000000002");
    static final UUID BRANCH_ID = UUID.fromString("45500000-0000-4000-8000-000000000010");
    static final UUID OTHER_BRANCH_ID = UUID.fromString("45500000-0000-4000-8000-000000000011");
    static final UUID BOOKING_ID = UUID.fromString("45500000-0000-4000-8000-000000000020");
    static final UUID ALLOCATION_ID = UUID.fromString("45500000-0000-4000-8000-000000000021");
    static final UUID ORDER_ID = UUID.fromString("45500000-0000-4000-8000-000000000030");
    static final UUID SESSION_ID = UUID.fromString("45500000-0000-4000-8000-000000000031");
    static final UUID ITEM_ID = UUID.fromString("45500000-0000-4000-8000-000000000040");
    static final UUID OTHER_ITEM_ID = UUID.fromString("45500000-0000-4000-8000-000000000041");
    static final UUID INVENTORY_ID = UUID.fromString("45500000-0000-4000-8000-000000000050");
    static final UUID EMPLOYEE_ID = UUID.fromString("45500000-0000-4000-8000-000000000060");
    static final UUID LINE_ID = UUID.fromString("45500000-0000-4000-8000-000000000070");
    static final String MR_CODE = "MR-45500000000040008000000000000001";
    static final String TOKEN = "room-token";

    private FoodServiceManagementManualRunner() {
    }

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
        cases.addAll(CreateMenuItemManualCases.cases());
        cases.addAll(ViewMenuItemsManualCases.cases());
        cases.addAll(UpdateMenuItemManualCases.cases());
        cases.addAll(ChangeMenuItemStatusManualCases.cases());
        cases.addAll(DeleteMenuItemManualCases.cases());
        cases.addAll(AddItemsToBookingManualCases.cases());
        cases.addAll(SubmitItemRequestManualCases.cases());
        cases.addAll(ViewBookingItemsManualCases.cases());
        cases.addAll(ConfirmItemRequestManualCases.cases());
        cases.addAll(UpdateOrderServiceStatusManualCases.cases());
        cases.sort(Comparator.comparing(ManualTest::id));
        validate(cases);
        return List.copyOf(cases);
    }

    private static void menu(List<ManualTest> cases) {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.println("\nFOOD SERVICE MANAGEMENT - MANUAL UNIT TEST");
                System.out.println("1. List test cases\n2. Run all test cases\n3. Run one test case\n0. Exit");
                System.out.print("Select: ");
                String option = scanner.nextLine().trim();
                if ("0".equals(option)) return;
                if ("1".equals(option)) list(cases);
                if ("2".equals(option)) run(cases);
                if ("3".equals(option)) {
                    System.out.print("Test Case ID: ");
                    String id = scanner.nextLine().trim();
                    run(cases.stream().filter(test -> test.id().equalsIgnoreCase(id)).toList());
                }
            }
        }
    }

    private static void list(List<ManualTest> cases) {
        cases.forEach(test -> System.out.printf("%-8s | %-34s | %s%n", test.id(), test.useCase(), test.sourceMethod()));
        System.out.println("Total: " + cases.size());
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
            System.out.println("\n[" + test.id() + "] " + test.useCase());
            System.out.println("Source      : " + test.sourceMethod());
            System.out.println("Precondition: " + test.preconditions());
            System.out.println("Input       : " + test.input());
            System.out.println("Expected    : " + test.expected());
            System.out.println("Actual      : " + result.actual());
            System.out.println("VERDICT     : " + (result.passed() ? "PASSED" : "FAILED"));
        }
        System.out.printf("%nSUMMARY: total=%d, passed=%d, failed=%d%n", cases.size(), passed, cases.size() - passed);
    }

    private static void validate(List<ManualTest> cases) {
        if (cases.size() != EXPECTED_CASES) throw new IllegalStateException("Expected 74 cases but found " + cases.size());
        Set<String> ids = new HashSet<>();
        for (ManualTest test : cases) {
            if (!test.id().matches("FSM-\\d{3}") || !ids.add(test.id())) {
                throw new IllegalStateException("Invalid or duplicate Test Case ID: " + test.id());
            }
        }
    }

    static ManualTest test(String id, String useCase, String sourceMethod, String preconditions,
            String input, String expected, String type, String note, CheckedAction action) {
        return new ManualTest(id, useCase, sourceMethod, preconditions, input, expected, type, note, action);
    }

    static MenuFixture menuFixture() {
        MenuItemRepository repository = mock(MenuItemRepository.class);
        RefundEvidenceStorageService storage = mock(RefundEvidenceStorageService.class);
        when(repository.activeBranch(BRANCH_ID)).thenReturn(true);
        when(repository.activeInventory(BRANCH_ID, INVENTORY_ID)).thenReturn(true);
        when(repository.create(any(), any(), any())).thenReturn(menuView(true));
        when(repository.get(BRANCH_ID, ITEM_ID)).thenReturn(menuView(true));
        when(repository.update(any(), any(), any(), any(), eq(false))).thenReturn(menuView(true));
        when(repository.changeStatus(any(), any(), any(Boolean.class))).thenAnswer(invocation -> menuView(invocation.getArgument(2)));
        when(repository.softDelete(BRANCH_ID, ITEM_ID, ACTOR_ID)).thenReturn(true);
        return new MenuFixture(repository, storage, new MenuItemService(repository, storage));
    }

    static FoodFixture foodFixture() {
        FoodOrderRepository orders = mock(FoodOrderRepository.class);
        MenuItemRepository menuItems = mock(MenuItemRepository.class);
        AuthCrypto crypto = mock(AuthCrypto.class);
        FoodOrderService service = new FoodOrderService(orders, menuItems, crypto);
        ActiveRoom active = new ActiveRoom(UUID.randomUUID(), "P101", "Phòng 101", BRANCH_ID,
                "HN_HOA_BANG", BOOKING_ID, "BK-DEMO", ALLOCATION_ID);
        RoomQrSession session = new RoomQrSession(SESSION_ID, BOOKING_ID, ALLOCATION_ID, BRANCH_ID,
                "BK-DEMO", active.roomId(), "P101", "Phòng 101", "HN_HOA_BANG", Instant.now().plusSeconds(3600));
        SellableItem item = sellable(new BigDecimal("10"));
        PendingLine line = new PendingLine(LINE_ID, ITEM_ID, "Coca Cola", BigDecimal.valueOf(3), new BigDecimal("20000"));
        when(orders.lockActiveBooking(BOOKING_ID, BRANCH_ID)).thenReturn(active);
        when(orders.validSession(eq(MR_CODE), any())).thenReturn(session);
        when(orders.eligibleOnShift(EMPLOYEE_ID, BRANCH_ID)).thenReturn(true);
        when(orders.sellableItems(eq(BRANCH_ID), anyList())).thenReturn(List.of(item));
        when(orders.createOrder(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(ORDER_ID);
        when(orders.insertLine(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(LINE_ID);
        when(orders.pendingLines(ORDER_ID)).thenReturn(List.of(line));
        when(orders.lockStock(INVENTORY_ID)).thenReturn(new BigDecimal("10"));
        when(orders.order(ORDER_ID, BRANCH_ID)).thenReturn(order("ACCEPTED", "STAFF", EMPLOYEE_ID));
        when(orders.qrOrder(ORDER_ID, SESSION_ID)).thenReturn(order("PENDING", "ROOM_QR", null));
        when(orders.accept(ORDER_ID, BRANCH_ID, ACTOR_ID, EMPLOYEE_ID)).thenReturn(1);
        when(orders.serve(ORDER_ID, BRANCH_ID)).thenReturn(1);
        return new FoodFixture(orders, menuItems, service, active, session, item, line);
    }

    static Identity manager() {
        return new Identity(ACTOR_ID, "quanly.hoabang", "BRANCH_MANAGER", "/manager", BRANCH_ID, "HN_HOA_BANG");
    }

    static Identity receptionist() {
        return new Identity(ACTOR_ID, "letan.hoabang", "RECEPTIONIST", "/reception", BRANCH_ID, "HN_HOA_BANG");
    }

    static Identity otherReceptionist() {
        return new Identity(OTHER_ACTOR_ID, "letan.khac", "RECEPTIONIST", "/reception", BRANCH_ID, "HN_HOA_BANG");
    }

    static MenuItemView menuView(boolean active) {
        return new MenuItemView(ITEM_ID, "FB-001", "Coca Cola", "Nước uống", "PACKAGED", INVENTORY_ID,
                "INV-001", "Coca Cola", new BigDecimal("10"), BigDecimal.ONE, "chai",
                new BigDecimal("20000"), active, "AVAILABLE", "Demo", null, null, null);
    }

    static SellableItem sellable(BigDecimal stock) {
        return new SellableItem(ITEM_ID, "Coca Cola", "PACKAGED", INVENTORY_ID,
                new BigDecimal("20000"), "AUTO_ON_SALE", "chai", stock);
    }

    static OrderView order(String status, String channel, UUID employeeId) {
        OrderItem line = new OrderItem(LINE_ID, ITEM_ID, "Coca Cola", BigDecimal.ONE,
                new BigDecimal("20000"), new BigDecimal("20000"), false);
        return new OrderView(ORDER_ID, "OD-001", BOOKING_ID, "BK-DEMO", "P101", "Phòng 101",
                "Nguyễn Khách", channel, status, employeeId, "Lễ tân demo", Instant.now(),
                "PENDING".equals(status) ? null : Instant.now(), "SERVED".equals(status) ? Instant.now() : null,
                new BigDecimal("20000"), List.of(line));
    }

    static TestResult expectResponse(ThrowingRunnable action, int status, String message) {
        try {
            action.run();
            return check(false, "No ResponseStatusException was thrown");
        } catch (ResponseStatusException failure) {
            boolean passed = failure.getStatusCode().value() == status && Objects.equals(message, failure.getReason());
            return check(passed, "status=" + failure.getStatusCode().value() + ", message=" + failure.getReason());
        } catch (BookingFailure failure) {
            boolean passed = failure.status() == status && Objects.equals(message, failure.getMessage());
            return check(passed, "status=" + failure.status() + ", message=" + failure.getMessage());
        } catch (Throwable failure) {
            return unexpected(failure);
        }
    }

    static TestResult check(boolean passed, String actual) {
        return new TestResult(passed, actual);
    }

    static TestResult unexpected(Throwable failure) {
        return new TestResult(false, failure.getClass().getSimpleName() + ": " + failure.getMessage());
    }

    record MenuFixture(MenuItemRepository repository, RefundEvidenceStorageService storage, MenuItemService service) {}
    record FoodFixture(FoodOrderRepository orders, MenuItemRepository menuItems, FoodOrderService service,
                       ActiveRoom active, RoomQrSession session, SellableItem item, PendingLine line) {}
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
