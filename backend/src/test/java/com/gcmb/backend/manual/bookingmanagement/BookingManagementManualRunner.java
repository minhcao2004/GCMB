/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 29/10/2026
 * Description: Runs Booking Management manual unit tests with spreadsheet test case IDs.
 */
package com.gcmb.backend.manual.bookingmanagement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.booking.dto.BookingDtos;
import com.gcmb.backend.booking.exception.BookingFailure;
import com.gcmb.backend.booking.repository.BookingRepository;
import com.gcmb.backend.booking.repository.BookingRepository.AllocationRow;
import com.gcmb.backend.booking.repository.BookingRepository.BookingRow;
import com.gcmb.backend.booking.repository.BookingRepository.BranchScope;
import com.gcmb.backend.booking.repository.BookingRepository.ChargeRow;
import com.gcmb.backend.booking.repository.BookingRepository.ComboRow;
import com.gcmb.backend.booking.repository.BookingRepository.PricingRule;
import com.gcmb.backend.booking.repository.BookingRepository.RoomRow;
import com.gcmb.backend.booking.repository.BookingRepository.RoomTypeRow;
import com.gcmb.backend.booking.service.BookingPricingService;
import com.gcmb.backend.booking.service.BookingService;
import com.gcmb.backend.inventory.service.FoodOrderService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;

public final class BookingManagementManualRunner {
    private static final int EXPECTED_CASES = 88;

    static final UUID ACTOR_ID = UUID.fromString("34370000-0000-4000-8000-000000000003");
    static final UUID BRANCH_ID = UUID.fromString("34370000-0000-4000-8000-000000000001");
    static final UUID ROOM_TYPE_ID = UUID.fromString("34370000-0000-4000-8000-000000000010");
    static final UUID OTHER_ROOM_TYPE_ID = UUID.fromString("34370000-0000-4000-8000-000000000011");
    static final UUID ROOM_ID = UUID.fromString("34370000-0000-4000-8000-000000000021");
    static final UUID OTHER_ROOM_ID = UUID.fromString("34370000-0000-4000-8000-000000000022");
    static final UUID MAINTENANCE_ROOM_ID = UUID.fromString("34370000-0000-4000-8000-000000000026");
    static final UUID COMBO_ID = UUID.fromString("34370000-0000-4000-8000-000000000041");
    static final UUID PRICE_RULE_ID = UUID.fromString("34370000-0000-4000-8000-000000000031");
    static final UUID BOOKING_ID = UUID.fromString("34370000-0000-4000-8000-000000000101");
    static final UUID ALLOCATION_ID = UUID.fromString("34370000-0000-4000-8000-000000000111");
    static final UUID DEPOSIT_ID = UUID.fromString("34370000-0000-4000-8000-000000000131");
    static final UUID REFUND_ID = UUID.fromString("34370000-0000-4000-8000-000000000191");

    static final BranchScope BRANCH = new BranchScope(BRANCH_ID, "Chi nhánh demo UC34-37", ZoneId.of("Asia/Ho_Chi_Minh"));

    private BookingManagementManualRunner() {
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
        cases.addAll(CreateBookingManualCases.cases());
        cases.addAll(ViewBookingsManualCases.cases());
        cases.addAll(UpdateBookingManualCases.cases());
        cases.addAll(CancelBookingManualCases.cases());
        cases.addAll(UpdateTaxInvoiceRequestStatusManualCases.cases());
        cases.sort(java.util.Comparator.comparing(ManualTest::id));
        validate(cases);
        return List.copyOf(cases);
    }

    private static void menu(List<ManualTest> cases) {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.println("\nBOOKING MANAGEMENT - MANUAL UNIT TEST");
                System.out.println("1. List test cases");
                System.out.println("2. Run all test cases");
                System.out.println("3. Run one test case");
                System.out.println("0. Exit");
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
        cases.forEach(test -> System.out.printf("%-8s | %-18s | %s%n",
                test.id(), test.useCase(), test.sourceMethod()));
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
        System.out.printf("%nSUMMARY: total=%d, passed=%d, failed=%d%n",
                cases.size(), passed, cases.size() - passed);
    }

    private static void validate(List<ManualTest> cases) {
        if (cases.size() != EXPECTED_CASES) {
            throw new IllegalStateException("Expected " + EXPECTED_CASES + " cases but found " + cases.size());
        }
        Set<String> ids = new HashSet<>();
        for (ManualTest test : cases) {
            if (!test.id().matches("BKM-\\d{3}") || !ids.add(test.id())) {
                throw new IllegalStateException("Invalid or duplicate Test Case ID: " + test.id());
            }
        }
    }

    static ManualTest test(String id, String useCase, String sourceMethod, String preconditions,
            String input, String expected, String type, String note, CheckedAction action) {
        return new ManualTest(id, useCase, sourceMethod, preconditions, input, expected, type, note, action);
    }

    static Fixture fresh() {
        BookingRepository repository = mock(BookingRepository.class);
        BookingPricingService pricing = mock(BookingPricingService.class);
        FoodOrderService foodOrders = mock(FoodOrderService.class);
        BookingService service = new BookingService(repository, pricing, new ObjectMapper(), foodOrders);
        Instant start = futureMinutes(180);
        BookingRow booking = booking("CONFIRMED", start, start.plusSeconds(7200));
        AllocationRow allocation = allocation(ROOM_ID, ROOM_TYPE_ID, start, start.plusSeconds(7200), "RESERVED");

        when(repository.branchScope(ACTOR_ID)).thenReturn(BRANCH);
        when(repository.roomType(ROOM_TYPE_ID)).thenReturn(roomType());
        when(repository.lockAvailableRoom(any(UUID.class), eq(BRANCH_ID))).thenReturn(room());
        when(repository.hasRoomOverlap(any(UUID.class), any(Instant.class), any(Instant.class), any())).thenReturn(false);
        when(pricing.quote(any(UUID.class), eq(BRANCH_ID), eq(BRANCH.timezone()),
                any(Instant.class), any(Instant.class), any(), any())).thenReturn(hourlyQuote());
        when(repository.insertBooking(eq(BRANCH_ID), anyString(), anyString(), anyString(), anyInt(),
                anyString(), any(Instant.class), any(Instant.class), anyInt(), eq(ACTOR_ID), any())).thenReturn(BOOKING_ID);
        when(repository.insertAllocation(eq(BRANCH_ID), eq(BOOKING_ID), any(UUID.class),
                any(Instant.class), any(Instant.class), eq(ACTOR_ID))).thenReturn(ALLOCATION_ID);
        when(repository.lockBookingId(BOOKING_ID, BRANCH_ID)).thenReturn(BOOKING_ID);
        when(repository.booking(BOOKING_ID, BRANCH_ID)).thenReturn(booking);
        when(repository.currentAllocationForUpdate(BOOKING_ID)).thenReturn(allocation);
        when(repository.currentAllocation(BOOKING_ID)).thenReturn(allocation);
        when(repository.activeChargesForAllocation(ALLOCATION_ID)).thenReturn(List.of(hourlyCharge()));
        when(repository.markNoShow(BOOKING_ID)).thenReturn(1);
        when(repository.lockDeposit(BOOKING_ID)).thenReturn(DEPOSIT_ID);
        when(repository.createDepositRefundRequest(eq(BRANCH_ID), eq(BOOKING_ID), eq(DEPOSIT_ID),
                any(BigDecimal.class), anyString(), anyString(), anyString(), anyString(), eq(ACTOR_ID))).thenReturn(REFUND_ID);
        stubDetail(repository, booking, allocation, BigDecimal.ZERO);
        return new Fixture(repository, pricing, foodOrders, service, booking, allocation);
    }

    static void stubDetail(BookingRepository repository, BookingRow booking, AllocationRow allocation,
            BigDecimal depositBalance) {
        when(repository.booking(BOOKING_ID, BRANCH_ID)).thenReturn(booking);
        when(repository.allocations(BOOKING_ID)).thenReturn(List.of(allocation));
        when(repository.charges(BOOKING_ID)).thenReturn(List.of());
        when(repository.meter(BOOKING_ID)).thenReturn(null);
        when(repository.activeChargeTotal(BOOKING_ID)).thenReturn(new BigDecimal("360000"));
        when(repository.depositBalance(BOOKING_ID)).thenReturn(depositBalance);
        when(repository.depositReceivedAmount(BOOKING_ID)).thenReturn(depositBalance);
        when(repository.depositForfeitedAmount(BOOKING_ID)).thenReturn(BigDecimal.ZERO);
        when(repository.depositCancellationOutcome(BOOKING_ID)).thenReturn(null);
        when(repository.depositPaymentMethod(BOOKING_ID)).thenReturn(null);
    }

    static Identity identity() {
        return new Identity(ACTOR_ID, "uc3437.demo", "RECEPTIONIST", "/reception", BRANCH_ID, BRANCH.name());
    }

    static Identity identityWithoutBranch() {
        return new Identity(ACTOR_ID, "uc3437.demo", "RECEPTIONIST", "/reception", null, null);
    }

    static Instant futureMinutes(long minutes) {
        return Instant.now().plusSeconds(minutes * 60);
    }

    static RoomTypeRow roomType() {
        return new RoomTypeRow(ROOM_TYPE_ID, "DEMO-UC3437-MUSIC", "Music Box demo", "MUSIC_BOX", 4);
    }

    static RoomRow room() {
        return new RoomRow(ROOM_ID, "DEMO-MB-01", "Music Box demo 01", 4,
                "AVAILABLE", ROOM_TYPE_ID, "Music Box demo");
    }

    static RoomRow room(UUID id, UUID typeId, int capacity, String status) {
        return new RoomRow(id, "DEMO-ROOM", "Phòng demo", capacity, status, typeId, "Hạng demo");
    }

    static ComboRow combo() {
        return new ComboRow(COMBO_ID, "DEMO-UC3437-MB-2H", 1, ROOM_TYPE_ID,
                "Music Box demo 2 giờ", 120, new BigDecimal("450000"), new BigDecimal("100000"),
                new BigDecimal("180000"), Instant.now().minusSeconds(86400), null, 2, 1);
    }

    static PricingRule pricingRule(BigDecimal hourlyPrice) {
        return new PricingRule(PRICE_RULE_ID, "UC34-37 Music Box ngày thường", BRANCH_ID,
                LocalDate.now(BRANCH.timezone()).minusDays(30), null, "ALL", null,
                0, 1440, hourlyPrice, hourlyPrice, 60, 1);
    }

    static BookingRow booking(String status, Instant start, Instant end) {
        return new BookingRow(BOOKING_ID, BRANCH_ID, "DEMO-UC3437-EDIT", "Nguyễn Khách Cập Nhật",
                "0900000101", 2, "PHONE", start, end, null, null, status, null, null,
                40, ACTOR_ID, "Booking demo để thử UC36", Instant.now().minusSeconds(86400));
    }

    static AllocationRow allocation(UUID roomId, UUID roomTypeId, Instant start, Instant end, String state) {
        return new AllocationRow(ALLOCATION_ID, BOOKING_ID, roomId, roomTypeId, "DEMO-MB-01",
                "Music Box demo 01", "Music Box demo", "AVAILABLE", start, end, state,
                null, Instant.now().minusSeconds(3600));
    }

    static ChargeRow hourlyCharge() {
        return new ChargeRow(UUID.fromString("34370000-0000-4000-8000-000000000121"), ALLOCATION_ID,
                "ROOM", "Music Box demo 2 giờ", new BigDecimal("2"), new BigDecimal("180000"),
                new BigDecimal("360000"), "ACTIVE", PRICE_RULE_ID, null,
                "{\"pricingMode\":\"HOURLY\",\"confirmedDurationMinutes\":120,\"confirmedAmount\":360000}",
                Instant.now().minusSeconds(3600));
    }

    static ChargeRow comboCharge() {
        return new ChargeRow(UUID.fromString("34370000-0000-4000-8000-000000000122"), ALLOCATION_ID,
                "COMBO", "Music Box demo 2 giờ", BigDecimal.ONE, new BigDecimal("450000"),
                new BigDecimal("450000"), "ACTIVE", null, COMBO_ID,
                "{\"pricingMode\":\"COMBO\",\"durationMinutes\":120,\"overtimeHourlyPrice\":180000,\"name\":\"Music Box demo 2 giờ\"}",
                Instant.now().minusSeconds(3600));
    }

    static BookingDtos.ChargeLine hourlyLine() {
        return new BookingDtos.ChargeLine("ROOM", "Music Box demo 2 giờ", new BigDecimal("2"),
                new BigDecimal("180000"), new BigDecimal("360000"), PRICE_RULE_ID, null,
                Map.of("pricingMode", "HOURLY", "confirmedDurationMinutes", 120,
                        "confirmedAmount", new BigDecimal("360000")));
    }

    static BookingDtos.PriceQuote hourlyQuote() {
        return new BookingDtos.PriceQuote("HOURLY", new BigDecimal("360000"),
                new BigDecimal("108000"), List.of(hourlyLine()));
    }

    static BookingDtos.PriceQuote comboQuote() {
        BookingDtos.ChargeLine line = new BookingDtos.ChargeLine("COMBO", "Music Box demo 2 giờ",
                BigDecimal.ONE, new BigDecimal("450000"), new BigDecimal("450000"), null, COMBO_ID,
                Map.of("pricingMode", "COMBO", "durationMinutes", 120));
        return new BookingDtos.PriceQuote("COMBO", new BigDecimal("450000"),
                new BigDecimal("100000"), List.of(line));
    }

    static BookingDtos.CreateBookingRequest createRequest(Instant start, Instant end, String mode,
            UUID comboId, String name, String phone, Integer guests, String note, BigDecimal deposit) {
        return new BookingDtos.CreateBookingRequest(ROOM_ID, start, end, mode, comboId, name, phone,
                "PHONE", note, guests, deposit, deposit != null && deposit.signum() > 0 ? "CASH" : null, null);
    }

    static BookingDtos.CreateBookingRequest validCreate() {
        Instant start = futureMinutes(120);
        return createRequest(start, start.plusSeconds(7200), "HOURLY", null,
                "Nguyễn Khách Demo", "0900003437", 2, "Booking hợp lệ", BigDecimal.ZERO);
    }

    static BookingDtos.UpdateBookingRequest updateRequest(UUID roomId, Instant start, Instant end,
            String mode, UUID comboId, String name, String phone, String note) {
        return new BookingDtos.UpdateBookingRequest(roomId, start, end, mode, comboId, name, phone, note);
    }

    static TestResult expectFailure(ThrowingRunnable action, int expectedStatus, String expectedMessage) {
        try {
            action.run();
            return check(false, "No BookingFailure was thrown");
        } catch (BookingFailure failure) {
            boolean passed = failure.status() == expectedStatus && Objects.equals(expectedMessage, failure.getMessage());
            return check(passed, "status=" + failure.status() + ", message=" + failure.getMessage());
        } catch (Throwable failure) {
            return check(false, "Unexpected " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    static TestResult expectFailureStatus(ThrowingRunnable action, int expectedStatus) {
        try {
            action.run();
            return check(false, "No BookingFailure was thrown");
        } catch (BookingFailure failure) {
            return check(failure.status() == expectedStatus,
                    "status=" + failure.status() + ", message=" + failure.getMessage());
        } catch (Throwable failure) {
            return check(false, "Unexpected " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    static TestResult combine(TestResult... results) {
        boolean passed = true;
        List<String> actual = new ArrayList<>();
        for (TestResult result : results) {
            passed &= result.passed();
            actual.add(result.actual());
        }
        return check(passed, String.join("; ", actual));
    }

    static TestResult check(boolean condition, String actual) {
        return new TestResult(condition, actual);
    }

    record Fixture(BookingRepository repository, BookingPricingService pricing, FoodOrderService foodOrders,
                   BookingService service, BookingRow booking, AllocationRow allocation) {
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }

    @FunctionalInterface
    interface CheckedAction {
        TestResult run() throws Exception;
    }

    record TestResult(boolean passed, String actual) {
    }

    record ManualTest(String id, String useCase, String sourceMethod, String preconditions,
                      String input, String expected, String type, String note, CheckedAction action) {
        ManualTest {
            Objects.requireNonNull(action, "action");
        }

        TestResult execute() {
            try {
                TestResult result = action.run();
                return result == null ? check(false, "Test action returned no result") : result;
            } catch (Throwable failure) {
                return check(false, "Unexpected " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
            }
        }
    }
}
