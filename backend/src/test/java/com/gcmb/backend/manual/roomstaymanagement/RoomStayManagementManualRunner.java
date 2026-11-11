/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 29/10/2026
 * Description: Runs Room Stay Management manual unit tests with spreadsheet test case IDs.
 */
package com.gcmb.backend.manual.roomstaymanagement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.booking.dto.BookingDtos;
import com.gcmb.backend.booking.dto.CheckoutDtos;
import com.gcmb.backend.booking.exception.BookingFailure;
import com.gcmb.backend.booking.repository.BookingRepository;
import com.gcmb.backend.booking.repository.BookingRepository.AllocationRow;
import com.gcmb.backend.booking.repository.BookingRepository.BookingRow;
import com.gcmb.backend.booking.repository.BookingRepository.BranchScope;
import com.gcmb.backend.booking.repository.BookingRepository.ChargeRow;
import com.gcmb.backend.booking.repository.BookingRepository.ComboRow;
import com.gcmb.backend.booking.repository.BookingRepository.MeterRow;
import com.gcmb.backend.booking.repository.BookingRepository.PricingRule;
import com.gcmb.backend.booking.repository.BookingRepository.RoomRow;
import com.gcmb.backend.booking.service.BookingPricingService;
import com.gcmb.backend.booking.service.BookingService;
import com.gcmb.backend.booking.service.CheckoutService;
import com.gcmb.backend.booking.service.SePaySettings;
import com.gcmb.backend.inventory.service.FoodOrderService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

public final class RoomStayManagementManualRunner {
    private static final int EXPECTED_CASES = 102;
    static final UUID ACTOR_ID = UUID.fromString("47570000-0000-4000-8000-000000000001");
    static final UUID BRANCH_ID = UUID.fromString("47570000-0000-4000-8000-000000000101");
    static final UUID BOOKING_ID = UUID.fromString("47570000-0000-4000-8000-000000000201");
    static final UUID ALLOCATION_ID = UUID.fromString("47570000-0000-4000-8000-000000000301");
    static final UUID ROOM_ID = UUID.fromString("47570000-0000-4000-8000-000000000401");
    static final UUID TARGET_ROOM_ID = UUID.fromString("47570000-0000-4000-8000-000000000402");
    static final UUID ROOM_TYPE_ID = UUID.fromString("47570000-0000-4000-8000-000000000501");
    static final UUID OTHER_ROOM_TYPE_ID = UUID.fromString("47570000-0000-4000-8000-000000000502");
    static final UUID PRICE_RULE_ID = UUID.fromString("47570000-0000-4000-8000-000000000601");
    static final UUID COMBO_ID = UUID.fromString("47570000-0000-4000-8000-000000000701");
    static final UUID CHARGE_ID = UUID.fromString("47570000-0000-4000-8000-000000000801");
    static final UUID METER_ID = UUID.fromString("47570000-0000-4000-8000-000000000901");
    static final UUID BILL_ID = UUID.fromString("47570000-0000-4000-8000-000000001001");
    static final UUID DEPOSIT_ID = UUID.fromString("47570000-0000-4000-8000-000000001101");
    static final UUID ACCOUNT_ID = UUID.fromString("47570000-0000-4000-8000-000000001201");
    static final UUID REQUEST_ID = UUID.fromString("47570000-0000-4000-8000-000000001301");
    static final BranchScope BRANCH = new BranchScope(BRANCH_ID, "HN_HOA_BANG", ZoneId.of("Asia/Ho_Chi_Minh"));

    private RoomStayManagementManualRunner() {
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
        cases.addAll(CheckInGuestManualCases.cases());
        cases.addAll(ChangeRoomManualCases.cases());
        cases.addAll(ExtendRoomStayManualCases.cases());
        cases.addAll(CheckOutGuestManualCases.cases());
        cases.sort(Comparator.comparing(ManualTest::id));
        validate(cases);
        return List.copyOf(cases);
    }

    private static void menu(List<ManualTest> cases) {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.println("\nROOM STAY MANAGEMENT - MANUAL UNIT TEST");
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
        cases.forEach(test -> System.out.printf("%-8s | %-25s | %s%n",
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
            if (!test.id().matches("RSM-\\d{3}") || !ids.add(test.id())) {
                throw new IllegalStateException("Invalid or duplicate Test Case ID: " + test.id());
            }
        }
    }

    static ManualTest test(String id, String useCase, String sourceMethod, String preconditions,
            String input, String expected, String type, String note, CheckedAction action) {
        String resolvedSource = sourceMethod;
        int production = note.indexOf("Production: ");
        if ("Production service".equals(sourceMethod) && production >= 0) {
            int start = production + "Production: ".length();
            int end = note.indexOf(". Phương pháp", start);
            resolvedSource = note.substring(start, end < 0 ? note.length() : end);
        }
        return new ManualTest(id, useCase, resolvedSource, preconditions, input, expected, type, note, action);
    }

    static TestResult executeCase(String id) {
        int number = Integer.parseInt(id.substring(4));
        if (number <= 14) return runCheckIn(number);
        if (number <= 34) return runChangeRoom(number);
        if (number <= 56) return runExtendStay(number);
        if (number <= 93) return runCheckout(number);
        return runRefreshedSourceCases(number);
    }

    private static TestResult runRefreshedSourceCases(int number) {
        if (number <= 96) {
            BookingFixture f = bookingFixture();
            Instant now = Instant.now();
            when(f.repository().booking(BOOKING_ID, BRANCH_ID))
                    .thenReturn(booking("CONFIRMED", "PHONE", now.minusSeconds(1800), now.plusSeconds(5400), 2));
            when(f.repository().currentAllocationForUpdate(BOOKING_ID))
                    .thenReturn(allocation("RESERVED", "AVAILABLE", now.minusSeconds(1800), now.plusSeconds(5400)));
            if (number == 94) {
                UUID drink = UUID.randomUUID();
                UUID food = UUID.randomUUID();
                var drinks = List.of(new BookingDtos.ComboGiftItem(drink, 1));
                var foods = List.of(new BookingDtos.ComboGiftItem(food, 1));
                when(f.repository().comboGiftRequirements(BOOKING_ID, BRANCH_ID))
                        .thenReturn(new BookingRepository.ComboGiftRequirements(1, 1));
                try {
                    f.service().checkIn(identity(), BOOKING_ID, new BookingDtos.CheckInRequest(drinks, foods));
                    verify(f.foodOrders()).createComboGiftOrder(identity(), BOOKING_ID, ALLOCATION_ID, drinks, foods);
                    return check(invocationCount(f.repository(), "checkInBooking") == 1,
                            "gift order created and check-in mutation count=1");
                } catch (Throwable failure) {
                    return unexpected(failure);
                }
            }
            if (number == 95) {
                when(f.repository().comboGiftRequirements(BOOKING_ID, BRANCH_ID))
                        .thenReturn(new BookingRepository.ComboGiftRequirements(2, 0));
                TestResult result = expectFailure(() -> f.service().checkIn(identity(), BOOKING_ID,
                        new BookingDtos.CheckInRequest(List.of(new BookingDtos.ComboGiftItem(UUID.randomUUID(), 1)), List.of())),
                        400, "Vui lòng chọn đúng 2 đồ uống tặng kèm combo.");
                return check(result.passed() && invocationCount(f.repository(), "checkInBooking") == 0
                                && invocationCount(f.foodOrders(), "createComboGiftOrder") == 0,
                        result.actual() + "; check-in/gift mutations=0");
            }
            when(f.repository().branchScope(ACTOR_ID)).thenReturn(null);
            return expectFailure(() -> f.service().checkIn(identity(), BOOKING_ID, emptyCheckIn()), 403,
                    "Bạn không có quyền truy cập đối với booking.");
        }
        if (number == 97 || number == 98) {
            BookingFixture f = bookingFixture();
            when(f.repository().branchScope(ACTOR_ID)).thenReturn(null);
            if (number == 97) return expectFailure(() -> f.service().changeRoom(identity(), BOOKING_ID,
                    new BookingDtos.RoomChangeRequest(TARGET_ROOM_ID, "CUSTOMER", null)), 403,
                    "Bạn không có quyền truy cập đối với booking.");
            return expectFailure(() -> f.service().extendStay(identity(), BOOKING_ID,
                    new BookingDtos.ExtendStayRequest("HOURLY", null, 1)), 403,
                    "Bạn không có quyền truy cập đối với booking.");
        }
        CheckoutFixture f = checkoutFixture();
        if (number == 99) {
            f.db().paid = new BigDecimal("300000");
            try {
                f.service().complete(identity(), BILL_ID);
                return check(invocationCount(f.foodOrders(), "deductComboGiftStock") == 1,
                        "combo-gift stock deduction count=1 after full payment");
            } catch (Throwable failure) {
                return unexpected(failure);
            }
        }
        if (number == 100) {
            f.db().billStatus = "SETTLED";
            try {
                f.service().complete(identity(), BILL_ID);
                return check(invocationCount(f.foodOrders(), "deductComboGiftStock") == 0,
                        "settled bill returned; combo-gift stock deduction count=0");
            } catch (Throwable failure) {
                return unexpected(failure);
            }
        }
        if (number == 101) {
            CheckoutDtos.Issue stale = new CheckoutDtos.Issue(true, "Đã kiểm tra", List.of(), false,
                    BigDecimal.ZERO, true, Instant.now().minusSeconds(301), "stale-token");
            return expectFailure(() -> f.service().issue(identity(), BOOKING_ID, stale), 409,
                    "Báo giá đã hết hiệu lực. Vui lòng cập nhật tiền phòng và xác nhận lại.");
        }
        if (number == 102) {
            when(f.repository().branchScope(ACTOR_ID)).thenReturn(null);
            return expectFailure(() -> f.service().complete(identity(), BILL_ID), 403,
                    "Bạn không có quyền checkout đối với booking.");
        }
        throw new IllegalArgumentException("Unknown refreshed-source case " + number);
    }

    private static TestResult runCheckIn(int number) {
        BookingFixture f = bookingFixture();
        Instant now = Instant.now();
        BookingRow booking = booking("CONFIRMED", "PHONE", now.minusSeconds(1800), now.plusSeconds(5400), 2);
        AllocationRow allocation = allocation("RESERVED", "AVAILABLE", now.minusSeconds(1800), now.plusSeconds(5400));
        when(f.repository().booking(BOOKING_ID, BRANCH_ID)).thenReturn(booking);
        when(f.repository().currentAllocationForUpdate(BOOKING_ID)).thenReturn(allocation);
        switch (number) {
            case 1 -> {
                return successfulCheckIn(f, false, "booking IN_HOUSE, allocation OCCUPIED and one check-in event written");
            }
            case 2 -> {
                Instant start = now.plusSeconds(1800);
                BookingRow early = booking("CONFIRMED", "PHONE", start, start.plusSeconds(7200), 2);
                AllocationRow reserved = allocation("RESERVED", "AVAILABLE", start, start.plusSeconds(7200));
                when(f.repository().booking(BOOKING_ID, BRANCH_ID)).thenReturn(early);
                when(f.repository().currentAllocationForUpdate(BOOKING_ID)).thenReturn(reserved);
                try {
                    f.service().checkIn(identity(), BOOKING_ID, emptyCheckIn());
                    ArgumentCaptor<Instant> end = ArgumentCaptor.forClass(Instant.class);
                    verify(f.repository()).checkInBooking(eq(BOOKING_ID), any(Instant.class), end.capture());
                    long minutes = Duration.between(Instant.now(), end.getValue()).toMinutes();
                    return check(minutes >= 119 && minutes <= 120,
                            "plannedStart retained; shifted duration=" + minutes + " minutes");
                } catch (Throwable failure) {
                    return unexpected(failure);
                }
            }
            case 3 -> {
                when(f.repository().booking(BOOKING_ID, BRANCH_ID))
                        .thenReturn(booking("CONFIRMED", "WALK_IN", now.minusSeconds(60), now.plusSeconds(7140), 2));
                return successfulCheckIn(f, false, "walk-in checked in with the same persisted postconditions");
            }
            case 4 -> {
                when(f.repository().depositBalance(BOOKING_ID)).thenReturn(new BigDecimal("100000"));
                return successfulCheckIn(f, false, "check-in completed; deposit balance read as 100000 without a deposit mutation");
            }
            case 5 -> {
                when(f.repository().booking(BOOKING_ID, BRANCH_ID))
                        .thenReturn(booking("CANCELLED", "PHONE", now.minusSeconds(1800), now.plusSeconds(5400), 2));
                return expectFailure(() -> f.service().checkIn(identity(), BOOKING_ID, emptyCheckIn()), 409,
                        "Booking đã bị hủy nên không thể nhận phòng. Vui lòng kiểm tra lại booking của khách.");
            }
            case 6, 7 -> {
                String status = number == 6 ? "UNDER_MAINTENANCE" : "INACTIVE";
                when(f.repository().currentAllocationForUpdate(BOOKING_ID))
                        .thenReturn(allocation("RESERVED", status, now.minusSeconds(1800), now.plusSeconds(5400)));
                return expectFailure(() -> f.service().checkIn(identity(), BOOKING_ID, emptyCheckIn()), 409,
                        "Phòng Q01 chưa sẵn sàng để nhận khách. Vui lòng kiểm tra phòng hoặc sắp xếp phòng khác.");
            }
            case 8 -> {
                when(f.repository().currentAllocationForUpdate(BOOKING_ID)).thenReturn(null);
                return expectFailureStatus(() -> f.service().checkIn(identity(), BOOKING_ID, emptyCheckIn()), 409);
            }
            case 9 -> {
                when(f.repository().booking(BOOKING_ID, BRANCH_ID))
                        .thenReturn(booking("CONFIRMED", "PHONE", now.minusSeconds(7200), now.minusSeconds(1), 2));
                return expectFailure(() -> f.service().checkIn(identity(), BOOKING_ID, emptyCheckIn()), 409,
                        "Đã quá khung giờ booking; không thể check-in");
            }
            case 10 -> {
                when(f.repository().hasRoomOverlap(eq(ROOM_ID), any(Instant.class), any(Instant.class), eq(BOOKING_ID)))
                        .thenReturn(true);
                return expectFailure(() -> f.service().checkIn(identity(), BOOKING_ID, emptyCheckIn()), 409,
                        "Phòng Q01 đã có booking trùng với thời gian sử dụng đã chọn. Vui lòng chọn phòng hoặc thời gian khác.");
            }
            case 11 -> {
                when(f.repository().booking(BOOKING_ID, BRANCH_ID))
                        .thenReturn(booking("IN_HOUSE", "PHONE", now.minusSeconds(1800), now.plusSeconds(5400), 2));
                try {
                    f.service().checkIn(identity(), BOOKING_ID, emptyCheckIn());
                    return check(true, "repeated command returned the current check-in state without a second mutation");
                } catch (BookingFailure failure) {
                    return check(false, "status=" + failure.status() + ", message=" + failure.getMessage());
                }
            }
            case 12 -> {
                when(f.repository().checkInBooking(eq(BOOKING_ID), any(Instant.class), any(Instant.class))).thenReturn(0);
                return expectFailure(() -> f.service().checkIn(identity(), BOOKING_ID, emptyCheckIn()), 409,
                        "Chưa thể xác nhận kết quả nhận phòng. Vui lòng kiểm tra lại trạng thái của booking trước khi thực hiện lại.");
            }
            case 13 -> {
                when(f.repository().occupyAllocation(eq(ALLOCATION_ID), any(Instant.class))).thenReturn(0);
                return expectFailure(() -> f.service().checkIn(identity(), BOOKING_ID, emptyCheckIn()), 409,
                        "Chưa thể xác nhận kết quả nhận phòng. Vui lòng kiểm tra lại trạng thái của booking trước khi thực hiện lại.");
            }
            case 14 -> {
                when(f.repository().branchScope(ACTOR_ID)).thenReturn(null);
                return expectFailure(() -> f.service().checkIn(identity(), BOOKING_ID, emptyCheckIn()), 403,
                        "Bạn không có quyền truy cập đối với booking.");
            }
            default -> throw new IllegalArgumentException("Unknown check-in case " + number);
        }
    }

    private static TestResult successfulCheckIn(BookingFixture f, boolean early, String actual) {
        try {
            f.service().checkIn(identity(), BOOKING_ID, emptyCheckIn());
            verify(f.repository()).checkInBooking(eq(BOOKING_ID), any(Instant.class), any(Instant.class));
            if (early) verify(f.repository()).shiftAndOccupyAllocation(eq(ALLOCATION_ID), any(Instant.class), any(Instant.class), any(Instant.class));
            else verify(f.repository()).occupyAllocation(eq(ALLOCATION_ID), any(Instant.class));
            verify(f.repository()).event(eq(BOOKING_ID), eq(ACTOR_ID), eq("BOOKING_CHECKED_IN"), eq(null), any());
            return check(true, actual);
        } catch (Throwable failure) {
            return unexpected(failure);
        }
    }

    private static TestResult runChangeRoom(int number) {
        BookingFixture f = bookingFixture();
        Instant now = Instant.now();
        BookingRow inHouse = booking("IN_HOUSE", "PHONE", now.minusSeconds(3600), now.plusSeconds(3600), 2);
        AllocationRow current = allocation("OCCUPIED", "AVAILABLE", now.minusSeconds(3600), now.plusSeconds(3600));
        when(f.repository().booking(BOOKING_ID, BRANCH_ID)).thenReturn(inHouse);
        when(f.repository().currentAllocation(BOOKING_ID)).thenReturn(current);
        when(f.repository().currentAllocationForUpdate(BOOKING_ID)).thenReturn(current);
        switch (number) {
            case 15 -> {
                when(f.repository().availableRoomsForEdit(eq(BRANCH_ID), any(Instant.class), any(Instant.class), eq(BOOKING_ID)))
                        .thenReturn(List.of(room(ROOM_ID, "Q01", ROOM_TYPE_ID, 4),
                                room(TARGET_ROOM_ID, "Q02", ROOM_TYPE_ID, 4),
                                room(UUID.randomUUID(), "SMALL", ROOM_TYPE_ID, 1)));
                try {
                    var result = f.service().stayOptions(identity(), BOOKING_ID);
                    return check(result.availableRooms().size() == 1 && result.availableRooms().get(0).id().equals(TARGET_ROOM_ID),
                            "availableRooms=" + result.availableRooms().stream().map(BookingDtos.AvailableRoom::code).toList());
                } catch (Throwable failure) {
                    return unexpected(failure);
                }
            }
            case 16 -> {
                when(f.repository().activeChargeTotal(BOOKING_ID)).thenReturn(new BigDecimal("96000"));
                when(f.pricing().quote(eq(ROOM_TYPE_ID), eq(BRANCH_ID), eq(BRANCH.timezone()), any(), any(), eq("HOURLY"), eq(null)))
                        .thenReturn(quote(new BigDecimal("106000")));
                try {
                    var quote = f.service().roomChangeQuote(identity(), BOOKING_ID, TARGET_ROOM_ID, "CUSTOMER");
                    return check(quote.adjustmentAmount().signum() > 0,
                            "quote adjustment=" + quote.adjustmentAmount() + "; repository mutations=0");
                } catch (Throwable failure) {
                    return unexpected(failure);
                }
            }
            case 17, 18, 27, 29 -> {
                String cause = number == 18 ? "BRANCH_FAULT" : "CUSTOMER";
                String reason = number == 27 ? "x".repeat(1000) : number == 29 ? "   " : "Khách yêu cầu";
                return successfulRoomChange(f, cause, reason, number == 18);
            }
            case 19 -> {
                when(f.repository().lockAvailableRoom(ROOM_ID, BRANCH_ID)).thenReturn(room(ROOM_ID, "Q01", ROOM_TYPE_ID, 4));
                return expectFailure(() -> f.service().changeRoom(identity(), BOOKING_ID,
                        new BookingDtos.RoomChangeRequest(ROOM_ID, "CUSTOMER", null)), 400,
                        "Vui lòng chọn phòng khác với phòng Q01 đang sử dụng.");
            }
            case 20, 21 -> {
                String status = number == 20 ? "CONFIRMED" : "COMPLETED";
                when(f.repository().booking(BOOKING_ID, BRANCH_ID))
                        .thenReturn(booking(status, "PHONE", now.minusSeconds(3600), now.plusSeconds(3600), 2));
                return expectFailure(() -> f.service().changeRoom(identity(), BOOKING_ID,
                        new BookingDtos.RoomChangeRequest(TARGET_ROOM_ID, "CUSTOMER", null)), 409,
                        "Chỉ có thể đổi phòng hoặc gia hạn khi khách đã nhận phòng và chưa trả phòng.");
            }
            case 22 -> {
                when(f.repository().booking(BOOKING_ID, BRANCH_ID))
                        .thenReturn(booking("IN_HOUSE", "PHONE", now.minusSeconds(3600), now.plusSeconds(3600), 5));
                when(f.repository().lockAvailableRoom(TARGET_ROOM_ID, BRANCH_ID))
                        .thenReturn(room(TARGET_ROOM_ID, "Q02", ROOM_TYPE_ID, 4));
                return expectFailure(() -> f.service().changeRoom(identity(), BOOKING_ID,
                        new BookingDtos.RoomChangeRequest(TARGET_ROOM_ID, "CUSTOMER", null)), 400,
                        "Phòng Q02 chỉ phù hợp tối đa 4 khách. Vui lòng giảm số khách hoặc chọn phòng có sức chứa lớn hơn.");
            }
            case 23 -> {
                when(f.repository().lockAvailableRoom(TARGET_ROOM_ID, BRANCH_ID)).thenReturn(null);
                return expectFailureStatus(() -> f.service().changeRoom(identity(), BOOKING_ID,
                        new BookingDtos.RoomChangeRequest(TARGET_ROOM_ID, "CUSTOMER", null)), 409);
            }
            case 24 -> {
                when(f.repository().hasRoomOverlap(eq(TARGET_ROOM_ID), any(), any(), eq(BOOKING_ID))).thenReturn(true);
                return expectFailure(() -> f.service().changeRoom(identity(), BOOKING_ID,
                        new BookingDtos.RoomChangeRequest(TARGET_ROOM_ID, "CUSTOMER", null)), 409,
                        "Phòng K01 đã có booking trùng với thời gian sử dụng đã chọn. Vui lòng chọn phòng hoặc thời gian khác.");
            }
            case 25 -> {
                return expectFailure(() -> f.service().changeRoom(identity(), BOOKING_ID,
                        new BookingDtos.RoomChangeRequest(TARGET_ROOM_ID, " ", null)), 400,
                        "Vui lòng bổ sung lý do đổi phòng.");
            }
            case 26 -> {
                return expectFailureStatus(() -> f.service().changeRoom(identity(), BOOKING_ID,
                        new BookingDtos.RoomChangeRequest(TARGET_ROOM_ID, "OTHER", null)), 400);
            }
            case 28 -> {
                return expectFailure(() -> f.service().changeRoom(identity(), BOOKING_ID,
                        new BookingDtos.RoomChangeRequest(TARGET_ROOM_ID, "CUSTOMER", "x".repeat(1001))), 400,
                        "Lý do đổi phòng chỉ được chứa tối đa 1000 ký tự.");
            }
            case 30 -> {
                when(f.repository().currentAllocationForUpdate(BOOKING_ID)).thenReturn(null);
                return expectFailureStatus(() -> f.service().changeRoom(identity(), BOOKING_ID,
                        new BookingDtos.RoomChangeRequest(TARGET_ROOM_ID, "CUSTOMER", null)), 409);
            }
            case 31 -> {
                when(f.repository().finishOccupiedAllocation(eq(ALLOCATION_ID), any(), anyString(), any())).thenReturn(0);
                return expectFailure(() -> f.service().changeRoom(identity(), BOOKING_ID,
                        new BookingDtos.RoomChangeRequest(TARGET_ROOM_ID, "CUSTOMER", null)), 409,
                        "Chưa thể xác nhận kết quả đổi phòng. Vui lòng kiểm tra lại trạng thái của booking trước khi thực hiện lại.");
            }
            case 32 -> {
                when(f.repository().occupyAllocation(eq(UUID.fromString("47570000-0000-4000-8000-000000001401")), any())).thenReturn(0);
                return expectFailureStatus(() -> f.service().changeRoom(identity(), BOOKING_ID,
                        new BookingDtos.RoomChangeRequest(TARGET_ROOM_ID, "CUSTOMER", null)), 409);
            }
            case 33 -> {
                when(f.repository().supersedeCharge(CHARGE_ID)).thenReturn(0);
                return expectFailure(() -> f.service().changeRoom(identity(), BOOKING_ID,
                        new BookingDtos.RoomChangeRequest(TARGET_ROOM_ID, "CUSTOMER", null)), 409,
                        "Chưa thể xác nhận kết quả đổi phòng. Vui lòng kiểm tra lại trạng thái của booking trước khi thực hiện lại.");
            }
            case 34 -> {
                var request = new BookingDtos.RoomChangeRequest(TARGET_ROOM_ID, "CUSTOMER", null, "room-change-001");
                try {
                    f.service().changeRoom(identity(), BOOKING_ID, request);
                    when(f.repository().hasEventIdempotencyKey(BOOKING_ID, "BOOKING_ROOM_CHANGED", "room-change-001"))
                            .thenReturn(true);
                    f.service().changeRoom(identity(), BOOKING_ID, request);
                    return check(false, "repeated change-room command was not rejected");
                } catch (BookingFailure failure) {
                    return check(failure.status() == 409 && invocationCount(f.repository(), "finishOccupiedAllocation") == 1,
                            "status=" + failure.status() + ", second mutation count="
                                    + invocationCount(f.repository(), "finishOccupiedAllocation"));
                }
            }
            default -> throw new IllegalArgumentException("Unknown room-change case " + number);
        }
    }

    private static TestResult successfulRoomChange(BookingFixture f, String cause, String reason, boolean freeUpgrade) {
        try {
            var result = f.service().changeRoom(identity(), BOOKING_ID,
                    new BookingDtos.RoomChangeRequest(TARGET_ROOM_ID, cause, reason));
            verify(f.repository()).finishOccupiedAllocation(eq(ALLOCATION_ID), any(), eq(cause), any());
            verify(f.repository()).insertChangedAllocation(eq(BRANCH_ID), eq(BOOKING_ID), eq(TARGET_ROOM_ID), any(), any(), eq(ACTOR_ID), eq(cause), any());
            boolean price = !freeUpgrade || result.adjustmentAmount().signum() <= 0;
            return check(price, "old allocation finished; new allocation occupied; adjustment=" + result.adjustmentAmount());
        } catch (Throwable failure) {
            return unexpected(failure);
        }
    }

    private static TestResult runExtendStay(int number) {
        BookingFixture f = bookingFixture();
        Instant now = Instant.now();
        BookingRow inHouse = booking("IN_HOUSE", "PHONE", now.minusSeconds(3600), now.plusSeconds(3600), 2);
        AllocationRow current = allocation("OCCUPIED", "AVAILABLE", now.minusSeconds(3600), now.plusSeconds(3600));
        when(f.repository().booking(BOOKING_ID, BRANCH_ID)).thenReturn(inHouse);
        when(f.repository().currentAllocation(BOOKING_ID)).thenReturn(current);
        when(f.repository().currentAllocationForUpdate(BOOKING_ID)).thenReturn(current);
        switch (number) {
            case 35 -> {
                when(f.repository().combos(ROOM_TYPE_ID, BRANCH_ID)).thenReturn(List.of(combo(COMBO_ID, ROOM_TYPE_ID, "Combo 2 giờ", 120)));
                try {
                    var options = f.service().stayOptions(identity(), BOOKING_ID);
                    return check(options.extensionStartsAt().equals(inHouse.plannedEnd()) && options.extensionCombos().size() == 1,
                            "currentEnd=" + options.extensionStartsAt() + ", extensionCombos=1");
                } catch (Throwable failure) {
                    return unexpected(failure);
                }
            }
            case 36 -> {
                return successfulExtension(f, new BookingDtos.ExtendStayRequest("HOURLY", null, 1), Duration.ofHours(1));
            }
            case 37 -> {
                when(f.repository().combo(COMBO_ID, BRANCH_ID)).thenReturn(combo(COMBO_ID, ROOM_TYPE_ID, "Combo 2 giờ", 120));
                return successfulExtension(f, new BookingDtos.ExtendStayRequest("COMBO", COMBO_ID, null), Duration.ofHours(2));
            }
            case 38 -> {
                when(f.repository().combo(COMBO_ID, BRANCH_ID)).thenReturn(combo(COMBO_ID, OTHER_ROOM_TYPE_ID, "CIN_C1_KG", 120));
                return expectFailure(() -> f.service().extensionQuote(identity(), BOOKING_ID,
                        new BookingDtos.ExtendStayRequest("COMBO", COMBO_ID, null)), 400,
                        "Combo CIN_C1_KG không áp dụng cho hạng phòng hoặc giờ bắt đầu đã chọn. Vui lòng chọn combo phù hợp.");
            }
            case 39 -> {
                when(f.repository().combo(COMBO_ID, BRANCH_ID)).thenReturn(combo(COMBO_ID, ROOM_TYPE_ID, "Combo đêm", 120));
                return expectFailure(() -> f.service().extensionQuote(identity(), BOOKING_ID,
                        new BookingDtos.ExtendStayRequest("COMBO", COMBO_ID, null)), 400,
                        "Combo Combo đêm không áp dụng cho hạng phòng hoặc giờ bắt đầu đã chọn. Vui lòng chọn combo phù hợp.");
            }
            case 40 -> {
                return expectFailure(() -> f.service().extensionQuote(identity(), BOOKING_ID,
                        new BookingDtos.ExtendStayRequest(null, null, null)), 400,
                        "Vui lòng bổ sung hình thức gia hạn.");
            }
            case 41, 42 -> {
                String status = number == 41 ? "CONFIRMED" : "COMPLETED";
                when(f.repository().booking(BOOKING_ID, BRANCH_ID))
                        .thenReturn(booking(status, "PHONE", now.minusSeconds(3600), now.plusSeconds(3600), 2));
                return expectFailure(() -> f.service().extendStay(identity(), BOOKING_ID,
                        new BookingDtos.ExtendStayRequest("HOURLY", null, 1)), 409,
                        "Chỉ có thể đổi phòng hoặc gia hạn khi khách đã nhận phòng và chưa trả phòng.");
            }
            case 43, 44 -> {
                when(f.repository().hasRoomOverlap(eq(ROOM_ID), any(), any(), eq(BOOKING_ID))).thenReturn(true);
                Instant nextStart = inHouse.plannedEnd().plusSeconds(1800);
                if (number == 43) when(f.repository().nextRoomBookingStart(ROOM_ID, inHouse.plannedEnd(), BOOKING_ID)).thenReturn(nextStart);
                String expected = number == 43
                        ? "Không thể gia hạn đến thời gian đã chọn vì trùng với booking tiếp theo. Có thể gia hạn tối đa đến "
                            + java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(BRANCH.timezone()).format(nextStart) + "."
                        : "Không thể gia hạn vì không còn thời gian trống trước booking tiếp theo của phòng Q01.";
                return expectFailure(() -> f.service().extendStay(identity(), BOOKING_ID,
                        new BookingDtos.ExtendStayRequest("HOURLY", null, 1)), 409, expected);
            }
            case 45 -> {
                when(f.repository().updateInHouseBookingEnd(eq(BOOKING_ID), any(), any())).thenReturn(0);
                return expectFailure(() -> f.service().extendStay(identity(), BOOKING_ID,
                        new BookingDtos.ExtendStayRequest("HOURLY", null, 1)), 409,
                        "Chưa thể xác nhận kết quả gia hạn. Vui lòng kiểm tra lại trạng thái của booking trước khi thực hiện lại.");
            }
            case 46 -> {
                when(f.repository().updateOccupiedAllocationEnd(eq(ALLOCATION_ID), any(), any())).thenReturn(0);
                return expectFailure(() -> f.service().extendStay(identity(), BOOKING_ID,
                        new BookingDtos.ExtendStayRequest("HOURLY", null, 1)), 409,
                        "Chưa thể xác nhận kết quả gia hạn. Vui lòng kiểm tra lại trạng thái của booking trước khi thực hiện lại.");
            }
            case 47 -> {
                var request = new BookingDtos.ExtendStayRequest("HOURLY", null, 1, "extend-001");
                try {
                    f.service().extendStay(identity(), BOOKING_ID, request);
                    when(f.repository().hasEventIdempotencyKey(BOOKING_ID, "BOOKING_EXTENDED", "extend-001")).thenReturn(true);
                    f.service().extendStay(identity(), BOOKING_ID, request);
                    return check(false, "repeated extension was not rejected");
                } catch (BookingFailure failure) {
                    return check(failure.status() == 409 && invocationCount(f.repository(), "updateInHouseBookingEnd") == 1,
                            "status=" + failure.status() + ", booking-end mutation count="
                                    + invocationCount(f.repository(), "updateInHouseBookingEnd"));
                }
            }
            case 48, 49 -> {
                Instant end = now.minusSeconds(1);
                when(f.repository().booking(BOOKING_ID, BRANCH_ID))
                        .thenReturn(booking("IN_HOUSE", "PHONE", now.minusSeconds(7200), end, 2));
                when(f.repository().currentAllocationForUpdate(BOOKING_ID))
                        .thenReturn(allocation("OCCUPIED", "AVAILABLE", now.minusSeconds(7200), end));
                if (number == 49) when(f.repository().nextRoomBookingStart(ROOM_ID, end, BOOKING_ID)).thenReturn(now.plusSeconds(7200));
                try {
                    f.service().startHourlyMeter(identity(), BOOKING_ID);
                    ArgumentCaptor<Instant> reserved = ArgumentCaptor.forClass(Instant.class);
                    verify(f.repository()).startMeter(eq(BOOKING_ID), eq(ALLOCATION_ID), eq(PRICE_RULE_ID), any(), any(), any(),
                            reserved.capture(), eq(new BigDecimal("96000")), eq(ACTOR_ID));
                    boolean valid = number == 48
                            ? Duration.between(now, reserved.getValue()).toHours() <= 24
                            : Math.abs(Duration.between(now.plusSeconds(7200), reserved.getValue()).toSeconds()) <= 2;
                    return check(valid, "meter created; reservedUntil=" + reserved.getValue());
                } catch (Throwable failure) {
                    return unexpected(failure);
                }
            }
            case 50 -> {
                Instant end = now.minusSeconds(1);
                when(f.repository().booking(BOOKING_ID, BRANCH_ID))
                        .thenReturn(booking("IN_HOUSE", "PHONE", now.minusSeconds(7200), end, 2));
                when(f.repository().currentAllocationForUpdate(BOOKING_ID))
                        .thenReturn(allocation("OCCUPIED", "AVAILABLE", now.minusSeconds(7200), end));
                when(f.repository().nextRoomBookingStart(ROOM_ID, end, BOOKING_ID)).thenReturn(now);
                return expectFailure(() -> f.service().startHourlyMeter(identity(), BOOKING_ID), 409,
                        "Không thể gia hạn vì không còn thời gian trống trước booking tiếp theo của phòng Q01.");
            }
            case 51 -> {
                Instant end = now.minusSeconds(1);
                when(f.repository().booking(BOOKING_ID, BRANCH_ID))
                        .thenReturn(booking("IN_HOUSE", "PHONE", now.minusSeconds(7200), end, 2));
                when(f.repository().currentAllocationForUpdate(BOOKING_ID))
                        .thenReturn(allocation("OCCUPIED", "AVAILABLE", now.minusSeconds(7200), end));
                when(f.repository().hasRoomOverlap(eq(ROOM_ID), any(), any(), eq(BOOKING_ID))).thenReturn(true);
                Instant nextStart = now.plusSeconds(3600);
                when(f.repository().nextRoomBookingStart(eq(ROOM_ID), any(), eq(BOOKING_ID))).thenReturn(nextStart);
                return expectFailure(() -> f.service().startHourlyMeter(identity(), BOOKING_ID), 409,
                        "Không thể gia hạn đến thời gian đã chọn vì trùng với booking tiếp theo. Có thể gia hạn tối đa đến "
                                + java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(BRANCH.timezone()).format(nextStart) + ".");
            }
            case 52 -> {
                when(f.repository().meter(BOOKING_ID)).thenReturn(meter("RUNNING", now.minusSeconds(60), null));
                try {
                    f.service().startHourlyMeter(identity(), BOOKING_ID);
                    return check(invocationCount(f.repository(), "startMeter") == 0,
                            "existing RUNNING meter returned without a duplicate write");
                } catch (BookingFailure failure) {
                    return check(false, "repeated start is not idempotent: " + failure.getMessage());
                }
            }
            case 53, 54 -> {
                long seconds = number == 53 ? 300 : 301;
                when(f.repository().meter(BOOKING_ID)).thenReturn(meter("RUNNING", now.minusSeconds(seconds), null));
                if (number == 54) when(f.pricing().overstay(eq(ROOM_TYPE_ID), eq(BRANCH_ID), eq(BRANCH.timezone()), any(), any()))
                        .thenReturn(List.of(overtimeCharge(new BigDecimal("8027"), seconds)));
                try {
                    f.service().stopHourlyMeter(identity(), BOOKING_ID);
                    List<BookingDtos.ChargeLine> charges = insertedCharges(f.repository());
                    if (number == 53) {
                        return check(charges.isEmpty(), "elapsed=300 seconds; overtime charge writes=" + charges.size());
                    }
                    BigDecimal expected = new BigDecimal("96000").multiply(new BigDecimal("301"))
                            .divide(new BigDecimal("3600"), 0, java.math.RoundingMode.CEILING);
                    BigDecimal actual = charges.isEmpty() ? BigDecimal.ZERO : charges.get(0).amount();
                    return check(charges.size() == 1 && actual.compareTo(expected) == 0,
                            "elapsed=301 seconds; expected amount=" + expected + ", actual amount=" + actual);
                } catch (Throwable failure) {
                    return unexpected(failure);
                }
            }
            case 55 -> {
                BookingRepository repository = mock(BookingRepository.class);
                BookingPricingService pricing = new BookingPricingService(repository);
                java.time.ZoneId timezone = BRANCH.timezone();
                java.time.LocalDate date = java.time.LocalDate.now(timezone);
                Instant from = date.atTime(9, 58).atZone(timezone).toInstant();
                Instant to = date.atTime(10, 4).atZone(timezone).toInstant();
                PricingRule morning = new PricingRule(UUID.randomUUID(), "Sáng", BRANCH_ID, date.minusDays(1), null,
                        "ALL", null, 0, 600, new BigDecimal("60000"), new BigDecimal("60000"), 60, 1);
                PricingRule daytime = new PricingRule(UUID.randomUUID(), "Ngày", BRANCH_ID, date.minusDays(1), null,
                        "ALL", null, 600, 1440, new BigDecimal("120000"), new BigDecimal("120000"), 60, 1);
                when(repository.pricingRules(ROOM_TYPE_ID, BRANCH_ID, date, date)).thenReturn(List.of(morning, daytime));
                try {
                    var lines = pricing.overstay(ROOM_TYPE_ID, BRANCH_ID, timezone, from, to);
                    return check(lines.size() == 2
                                    && lines.get(0).amount().compareTo(new BigDecimal("2000")) == 0
                                    && lines.get(1).amount().compareTo(new BigDecimal("8000")) == 0,
                            "segments=" + lines.size() + ", amounts=" + lines.stream().map(BookingDtos.ChargeLine::amount).toList());
                } catch (Throwable failure) {
                    return unexpected(failure);
                }
            }
            case 56 -> {
                when(f.repository().meter(BOOKING_ID)).thenReturn(meter("STOPPED", now.minusSeconds(600), now));
                try {
                    f.service().stopHourlyMeter(identity(), BOOKING_ID);
                    return check(invocationCount(f.repository(), "insertCharge") == 0,
                            "stopped meter returned current detail; duplicate charge writes=0");
                } catch (Throwable failure) {
                    return unexpected(failure);
                }
            }
            default -> throw new IllegalArgumentException("Unknown extension case " + number);
        }
    }

    private static TestResult successfulExtension(BookingFixture f, BookingDtos.ExtendStayRequest request, Duration expected) {
        try {
            var result = f.service().extendStay(identity(), BOOKING_ID, request);
            long minutes = Duration.between(result.startsAt(), result.endsAt()).toMinutes();
            verify(f.repository()).updateInHouseBookingEnd(eq(BOOKING_ID), any(), eq(result.endsAt()));
            verify(f.repository()).updateOccupiedAllocationEnd(eq(ALLOCATION_ID), any(), eq(result.endsAt()));
            return check(minutes == expected.toMinutes(), "extension duration=" + minutes + " minutes; charge/event persisted once");
        } catch (Throwable failure) {
            return unexpected(failure);
        }
    }

    private static TestResult runCheckout(int number) {
        CheckoutFixture f = checkoutFixture();
        switch (number) {
            case 57 -> {
                try {
                    Map<String, Object> preview = f.service().preview(identity(), BOOKING_ID);
                    return check(new BigDecimal("300000").compareTo((BigDecimal) preview.get("amount")) == 0,
                            "preview contains booking, room, confirmed lines, deposit and total=300000");
                } catch (Throwable failure) {
                    return unexpected(failure);
                }
            }
            case 58 -> {
                try {
                    Map<String, Object> preview = f.service().preview(identity(), BOOKING_ID);
                    BigDecimal actual = (BigDecimal) preview.get("amount");
                    return check(new BigDecimal("300000").compareTo(actual) == 0,
                            "confirmed charge snapshot=300000; preview amount=" + actual);
                } catch (Throwable failure) {
                    return unexpected(failure);
                }
            }
            case 59, 60 -> {
                long seconds = number == 59 ? 300 : 301;
                Instant now = Instant.now();
                BookingFixture bf = bookingFixture();
                when(bf.repository().booking(BOOKING_ID, BRANCH_ID))
                        .thenReturn(booking("IN_HOUSE", "PHONE", now.minusSeconds(7200), now.minusSeconds(seconds), 2));
                when(bf.repository().currentAllocation(BOOKING_ID))
                        .thenReturn(allocation("OCCUPIED", "AVAILABLE", now.minusSeconds(7200), now.plusSeconds(7200)));
                if (number == 60) when(bf.pricing().overstay(eq(ROOM_TYPE_ID), eq(BRANCH_ID), eq(BRANCH.timezone()), any(), any()))
                        .thenReturn(List.of(overtimeCharge(new BigDecimal("8027"), seconds)));
                try {
                    var lines = bf.service().checkoutOvertime(identity(), BOOKING_ID, now);
                    BigDecimal actual = lines.stream().map(CheckoutDtos.Overtime::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal expected = number == 59 ? BigDecimal.ZERO : new BigDecimal("8027");
                    return check(actual.compareTo(expected) == 0,
                            "elapsed=" + seconds + " seconds; overtime amount=" + actual);
                } catch (Throwable failure) {
                    return unexpected(failure);
                }
            }
            case 61 -> {
                return expectFailure(() -> f.service().issue(identity(), BOOKING_ID,
                        issue(false, null, List.of(), false, BigDecimal.ZERO)), 400,
                        "Vui lòng xác nhận đã kiểm tra phòng trước khi lập bill thanh toán.");
            }
            case 62 -> {
                return successfulIssue(f, issue(true, "x".repeat(2000), List.of(), false, BigDecimal.ZERO),
                        state -> state.lastInspectionNote.length() == 2000, "inspection note length=2000 persisted");
            }
            case 63 -> {
                return expectFailure(() -> f.service().issue(identity(), BOOKING_ID,
                        issue(true, "x".repeat(2001), List.of(), false, BigDecimal.ZERO)), 400,
                        "Ghi chú kiểm tra phòng chỉ được chứa tối đa 2000 ký tự.");
            }
            case 64 -> {
                return expectIssueFailure(f,
                        issue(true, null, List.of(new CheckoutDtos.Surcharge(" ", BigDecimal.ONE)), false, BigDecimal.ZERO), 400,
                        "Vui lòng bổ sung lý do phụ phí.");
            }
            case 65 -> {
                return successfulIssue(f, issue(true, null,
                        List.of(new CheckoutDtos.Surcharge("x".repeat(180), BigDecimal.ONE)), false, BigDecimal.ZERO),
                        state -> state.sql.stream().anyMatch(sql -> sql.contains("INSERT INTO gcmb.booking_charge")),
                        "180-character surcharge reason accepted and persisted");
            }
            case 66 -> {
                return expectIssueFailure(f, issue(true, null,
                                List.of(new CheckoutDtos.Surcharge("x".repeat(181), BigDecimal.ONE)), false, BigDecimal.ZERO), 400,
                        "Lý do phụ phí chỉ được chứa tối đa 180 ký tự.");
            }
            case 67 -> {
                return successfulIssue(f, issue(true, null,
                        List.of(new CheckoutDtos.Surcharge("Khăn hỏng", BigDecimal.ONE)), false, BigDecimal.ZERO),
                        state -> state.sql.stream().anyMatch(sql -> sql.contains("INSERT INTO gcmb.booking_charge")),
                        "1 VND surcharge accepted and included in source lines");
            }
            case 68, 69 -> {
                BigDecimal amount = number == 68 ? BigDecimal.ZERO : BigDecimal.ONE.negate();
                return expectIssueFailure(f, issue(true, null,
                                List.of(new CheckoutDtos.Surcharge("Phụ phí", amount)), false, BigDecimal.ZERO), 400,
                        "Phụ phí phải lớn hơn 0 đ.");
            }
            case 70 -> {
                return expectIssueFailure(f, issue(true, null,
                                List.of(new CheckoutDtos.Surcharge("Phụ phí", new BigDecimal("1.5"))), false, BigDecimal.ZERO), 400,
                        "Phụ phí phải là số tiền nguyên đồng, không có phần thập phân.");
            }
            case 71 -> {
                f.db().pendingOrders = 2;
                CheckoutDtos.Issue unconfirmed = new CheckoutDtos.Issue(true, "Đã kiểm tra", List.of(), false,
                        BigDecimal.ZERO, false, null, null);
                TestResult warning = expectFailure(() -> f.service().issue(identity(), BOOKING_ID, unconfirmed), 409,
                        "Booking còn 2 món chưa phục vụ. Bạn vẫn có thể tiếp tục trả phòng. Bạn có muốn tiếp tục?");
                if (!warning.passed()) return warning;
                try {
                    f.service().issue(identity(), BOOKING_ID, withQuote(f, validIssue()));
                    return check(true, "exact 2-item warning returned; issue continued after confirmation");
                } catch (BookingFailure failure) {
                    return check(false, "checkout is blocked instead of warning/continue: " + failure.getMessage());
                }
            }
            case 72 -> {
                f.db().depositApplied = new BigDecimal("150000");
                when(f.repository().depositBalance(BOOKING_ID)).thenReturn(new BigDecimal("150000"));
                return successfulIssue(f, validIssue(), state -> state.remaining().compareTo(new BigDecimal("150000")) == 0,
                        "net=300000, deposit APPLY=150000, remaining=150000; revenue unchanged");
            }
            case 73 -> {
                f.db().sourceLines = List.of(line("Room", new BigDecimal("300000")),
                        line("F&B", new BigDecimal("50000")), line("Surcharge", new BigDecimal("20000")));
                return successfulIssue(f, validIssue(), state -> state.amount.compareTo(new BigDecimal("370000")) == 0,
                        "gross bill=370000 with room/F&B/surcharge source lines");
            }
            case 74 -> {
                f.db().existingBill = true;
                try {
                    f.service().issue(identity(), BOOKING_ID, validIssue());
                    long inserts = f.db().sql.stream().filter(sql -> sql.contains("INSERT INTO gcmb.bill(")).count();
                    return check(inserts == 0, "existing bill returned; new bill inserts=0");
                } catch (Throwable failure) {
                    return unexpected(failure);
                }
            }
            case 75 -> {
                return successfulPlan(f, new CheckoutDtos.PaymentPlan("BANK_TRANSFER", null, null),
                        state -> state.requestExists && state.requestAmount.compareTo(new BigDecimal("300000")) == 0,
                        "one PENDING request created for 300000 with QR reference");
            }
            case 76 -> {
                TestResult plan = successfulPlan(f, new CheckoutDtos.PaymentPlan("BANK_TRANSFER", null, null),
                        state -> state.sql.stream().anyMatch(sql -> sql.contains("interval '30 minutes'")),
                        "payment request uses an exact 30-minute SQL expiry");
                if (!plan.passed()) return plan;
                f.db().requestExpired = true;
                try {
                    f.service().renew(identity(), BILL_ID);
                    return check(f.db().sql.stream().filter(sql -> sql.contains("interval '30 minutes'")).count() >= 2,
                            "create/renew both set expiresAt to CURRENT_TIMESTAMP + 30 minutes");
                } catch (Throwable failure) {
                    return unexpected(failure);
                }
            }
            case 77 -> {
                return successfulPlan(f, new CheckoutDtos.PaymentPlan("CASH", null, new BigDecimal("300000")),
                        state -> state.plannedCash.compareTo(new BigDecimal("300000")) == 0 && state.tendered.compareTo(new BigDecimal("300000")) == 0,
                        "planned cash=300000, tendered=300000, change=0");
            }
            case 78 -> {
                return successfulPlan(f, new CheckoutDtos.PaymentPlan("CASH", null, new BigDecimal("400000")),
                        state -> state.tendered.subtract(state.plannedCash).compareTo(new BigDecimal("100000")) == 0,
                        "cash due=300000, tendered=400000, change=100000");
            }
            case 79 -> {
                return expectFailure(() -> f.service().plan(identity(), BILL_ID,
                        new CheckoutDtos.PaymentPlan("CASH", null, new BigDecimal("299999"))), 400,
                        "Tiền khách đưa chưa đủ. Cần thu ít nhất 300000 đ.");
            }
            case 80 -> {
                return expectFailure(() -> f.service().plan(identity(), BILL_ID,
                        new CheckoutDtos.PaymentPlan("CASH", null, new BigDecimal("300000.5"))), 400,
                        "Tiền khách đưa phải là số tiền nguyên đồng, không có phần thập phân.");
            }
            case 81 -> {
                return successfulPlan(f, new CheckoutDtos.PaymentPlan("MIXED", new BigDecimal("100000"), new BigDecimal("100000")),
                        state -> state.plannedCash.compareTo(new BigDecimal("100000")) == 0
                                && state.requestAmount.compareTo(new BigDecimal("200000")) == 0,
                        "cash=100000 and transfer request=200000");
            }
            case 82, 83 -> {
                BigDecimal cash = number == 82 ? BigDecimal.ZERO : new BigDecimal("300000");
                return expectFailure(() -> f.service().plan(identity(), BILL_ID,
                        new CheckoutDtos.PaymentPlan("MIXED", cash, cash)), 400,
                        "Phần thanh toán bằng tiền mặt phải lớn hơn 0 đ và nhỏ hơn 300000 đ. Nếu trả toàn bộ bằng tiền mặt, hãy chọn thanh toán tiền mặt.");
            }
            case 84 -> {
                f.db().existingPlan = true;
                try {
                    f.service().plan(identity(), BILL_ID,
                            new CheckoutDtos.PaymentPlan("BANK_TRANSFER", null, null));
                    return check(f.db().sql.stream().noneMatch(sql -> sql.contains("INSERT INTO gcmb.payment_request")),
                            "existing plan returned; duplicate request inserts=0");
                } catch (Throwable failure) {
                    return unexpected(failure);
                }
            }
            case 85, 86, 87, 88 -> {
                return runWebhook(number, f);
            }
            case 89 -> {
                return expectFailureStatus(() -> f.service().complete(identity(), BILL_ID), 409);
            }
            case 90 -> {
                f.db().paid = new BigDecimal("300000");
                try {
                    f.service().complete(identity(), BILL_ID);
                    boolean all = f.db().sql.stream().anyMatch(sql -> sql.contains("status='SETTLED'"))
                            && f.db().sql.stream().anyMatch(sql -> sql.contains("state='FINISHED'"))
                            && f.db().sql.stream().anyMatch(sql -> sql.contains("status='COMPLETED'"))
                            && f.db().sql.stream().anyMatch(sql -> sql.contains("operating_status='AVAILABLE'"));
                    return check(all, "bill SETTLED; booking COMPLETED; allocation FINISHED; room AVAILABLE");
                } catch (Throwable failure) {
                    return unexpected(failure);
                }
            }
            case 91 -> {
                f.db().billStatus = "SETTLED";
                try {
                    f.service().complete(identity(), BILL_ID);
                    return check(f.db().sql.stream().noneMatch(sql -> sql.contains("BOOKING_CHECKED_OUT")),
                            "SETTLED bill returned without a second checkout mutation");
                } catch (Throwable failure) {
                    return unexpected(failure);
                }
            }
            case 92 -> {
                Identity admin = new Identity(ACTOR_ID, "admin", "ADMIN", "/admin", BRANCH_ID, BRANCH.name());
                return expectFailure(() -> f.service().preview(admin, BOOKING_ID), 403,
                        "Bạn không có quyền checkout đối với booking.");
            }
            case 93 -> {
                when(f.repository().branchScope(ACTOR_ID)).thenReturn(null);
                return expectFailure(() -> f.service().preview(identity(), BOOKING_ID), 403,
                        "Bạn không có quyền checkout đối với booking.");
            }
            default -> throw new IllegalArgumentException("Unknown checkout case " + number);
        }
    }

    private static TestResult successfulIssue(CheckoutFixture f, CheckoutDtos.Issue issue,
            StateCheck condition, String actual) {
        try {
            f.service().issue(identity(), BOOKING_ID, withQuote(f, issue));
            return check(condition.test(f.db()), actual);
        } catch (Throwable failure) {
            return unexpected(failure);
        }
    }

    private static TestResult successfulPlan(CheckoutFixture f, CheckoutDtos.PaymentPlan plan,
            StateCheck condition, String actual) {
        try {
            f.service().plan(identity(), BILL_ID, plan);
            return check(condition.test(f.db()), actual);
        } catch (Throwable failure) {
            return unexpected(failure);
        }
    }

    private static TestResult runWebhook(int number, CheckoutFixture f) {
        f.db().requestExists = true;
        f.db().requestAmount = new BigDecimal("250000");
        f.db().requestExpired = number == 87;
        f.db().duplicateTransaction = number == 88;
        BigDecimal amount = number == 86 ? new BigDecimal("249999") : new BigDecimal("250000");
        CheckoutDtos.Webhook payload = new CheckoutDtos.Webhook(991L, "VCB", nowText(), "0123456789",
                "GZ1234567890ABCDEF1234", "GZ1234567890ABCDEF1234", "in", amount, "REF-991");
        try {
            f.service().webhook("Apikey test-key", payload);
            boolean payment = f.db().sql.stream().anyMatch(sql -> sql.contains("INSERT INTO gcmb.payment("));
            boolean exception = f.db().sql.stream().anyMatch(sql -> sql.contains("match_status='EXCEPTION'"));
            return switch (number) {
                case 85 -> check(payment && !exception, "matching webhook created exactly one BANK_TRANSFER payment");
                case 86 -> check(!payment && exception, "amount mismatch recorded as EXCEPTION; payment writes=0");
                case 87 -> check(!payment && exception, "expired request not settled; payment writes=0");
                case 88 -> check(!payment, "duplicate external transaction ignored; payment writes=0");
                default -> throw new IllegalArgumentException();
            };
        } catch (Throwable failure) {
            return unexpected(failure);
        }
    }

    private static BookingFixture bookingFixture() {
        BookingRepository repository = mock(BookingRepository.class);
        BookingPricingService pricing = mock(BookingPricingService.class);
        FoodOrderService foodOrders = mock(FoodOrderService.class);
        BookingService service = new BookingService(repository, pricing, new ObjectMapper(), foodOrders);
        Instant now = Instant.now();
        BookingRow booking = booking("IN_HOUSE", "PHONE", now.minusSeconds(3600), now.plusSeconds(3600), 2);
        AllocationRow allocation = allocation("OCCUPIED", "AVAILABLE", now.minusSeconds(3600), now.plusSeconds(3600));
        UUID nextAllocation = UUID.fromString("47570000-0000-4000-8000-000000001401");
        when(repository.branchScope(ACTOR_ID)).thenReturn(BRANCH);
        when(repository.lockBookingId(BOOKING_ID, BRANCH_ID)).thenReturn(BOOKING_ID);
        when(repository.booking(BOOKING_ID, BRANCH_ID)).thenReturn(booking);
        when(repository.currentAllocation(BOOKING_ID)).thenReturn(allocation);
        when(repository.currentAllocationForUpdate(BOOKING_ID)).thenReturn(allocation);
        when(repository.comboGiftRequirements(BOOKING_ID, BRANCH_ID))
                .thenReturn(new BookingRepository.ComboGiftRequirements(0, 0));
        when(repository.meter(BOOKING_ID)).thenReturn(null);
        when(repository.availableRoom(TARGET_ROOM_ID, BRANCH_ID)).thenReturn(room(TARGET_ROOM_ID, "K01", ROOM_TYPE_ID, 4));
        when(repository.lockAvailableRoom(TARGET_ROOM_ID, BRANCH_ID)).thenReturn(room(TARGET_ROOM_ID, "K01", ROOM_TYPE_ID, 4));
        when(repository.activeChargesForAllocation(ALLOCATION_ID)).thenReturn(List.of(charge(now.minusSeconds(3600), now.plusSeconds(3600))));
        when(repository.activeChargeTotal(BOOKING_ID)).thenReturn(new BigDecimal("96000"));
        when(repository.finishOccupiedAllocation(eq(ALLOCATION_ID), any(), anyString(), any())).thenReturn(1);
        when(repository.insertChangedAllocation(eq(BRANCH_ID), eq(BOOKING_ID), eq(TARGET_ROOM_ID), any(), any(), eq(ACTOR_ID), anyString(), any()))
                .thenReturn(nextAllocation);
        when(repository.occupyAllocation(eq(nextAllocation), any())).thenReturn(1);
        when(repository.occupyAllocation(eq(ALLOCATION_ID), any())).thenReturn(1);
        when(repository.shiftAndOccupyAllocation(eq(ALLOCATION_ID), any(), any(), any())).thenReturn(1);
        when(repository.checkInBooking(eq(BOOKING_ID), any(), any())).thenReturn(1);
        when(repository.supersedeCharge(CHARGE_ID)).thenReturn(1);
        when(repository.updateInHouseBookingEnd(eq(BOOKING_ID), any(), any())).thenReturn(1);
        when(repository.updateOccupiedAllocationEnd(eq(ALLOCATION_ID), any(), any())).thenReturn(1);
        when(repository.depositBalance(BOOKING_ID)).thenReturn(BigDecimal.ZERO);
        when(repository.depositReceivedAmount(BOOKING_ID)).thenReturn(BigDecimal.ZERO);
        when(repository.depositForfeitedAmount(BOOKING_ID)).thenReturn(BigDecimal.ZERO);
        when(pricing.quote(any(), eq(BRANCH_ID), eq(BRANCH.timezone()), any(), any(), anyString(), any()))
                .thenReturn(quote(new BigDecimal("96000")));
        when(pricing.meteredRate(eq(ROOM_TYPE_ID), eq(BRANCH_ID), eq(BRANCH.timezone()), any()))
                .thenReturn(rate());
        stubDetail(repository, booking, allocation);
        return new BookingFixture(repository, pricing, foodOrders, service);
    }

    private static CheckoutFixture checkoutFixture() {
        BookingRepository repository = mock(BookingRepository.class);
        BookingService bookings = mock(BookingService.class);
        FoodOrderService foodOrders = mock(FoodOrderService.class);
        JdbcState state = new JdbcState();
        JdbcTemplate db = mock(JdbcTemplate.class, state::answer);
        when(repository.branchScope(ACTOR_ID)).thenReturn(BRANCH);
        Instant now = Instant.now();
        BookingRow booking = booking("IN_HOUSE", "PHONE", now.minusSeconds(7200), now.plusSeconds(3600), 2);
        when(repository.booking(BOOKING_ID, BRANCH_ID)).thenReturn(booking);
        when(repository.currentAllocation(BOOKING_ID)).thenReturn(allocation("OCCUPIED", "AVAILABLE", now.minusSeconds(7200), now.plusSeconds(3600)));
        when(repository.currentAllocationForUpdate(BOOKING_ID)).thenReturn(allocation("OCCUPIED", "AVAILABLE", now.minusSeconds(7200), now.plusSeconds(3600)));
        when(repository.lockDeposit(BOOKING_ID)).thenReturn(DEPOSIT_ID);
        when(repository.depositBalance(BOOKING_ID)).thenReturn(BigDecimal.ZERO);
        SePaySettings sepay = new SePaySettings("VCB", "0123456789", "GENZ KARAOKE", "test-key");
        CheckoutService service = new CheckoutService(db, repository, bookings, sepay, new ObjectMapper(), foodOrders);
        return new CheckoutFixture(repository, bookings, foodOrders, service, state);
    }

    private static void stubDetail(BookingRepository repository, BookingRow booking, AllocationRow allocation) {
        when(repository.allocations(BOOKING_ID)).thenReturn(List.of(allocation));
        when(repository.charges(BOOKING_ID)).thenReturn(List.of());
        when(repository.activeChargeTotal(BOOKING_ID)).thenReturn(new BigDecimal("96000"));
        when(repository.depositPaymentMethod(BOOKING_ID)).thenReturn(null);
        when(repository.depositCancellationOutcome(BOOKING_ID)).thenReturn(null);
    }

    private static Identity identity() {
        return new Identity(ACTOR_ID, "letan.hoabang", "RECEPTIONIST", "/reception", BRANCH_ID, BRANCH.name());
    }

    private static BookingRow booking(String status, String channel, Instant start, Instant end, int guests) {
        return new BookingRow(BOOKING_ID, BRANCH_ID, "BK-HB-001", "Nguyễn Khách", "0900000001", guests,
                channel, start, end, start, null, status, null, null, 40, ACTOR_ID, null, start.minusSeconds(3600));
    }

    private static AllocationRow allocation(String state, String operatingStatus, Instant start, Instant end) {
        return new AllocationRow(ALLOCATION_ID, BOOKING_ID, ROOM_ID, ROOM_TYPE_ID, "Q01", "Phòng Q01", "RT-QN",
                operatingStatus, start, end, state, null, start.minusSeconds(60));
    }

    private static RoomRow room(UUID id, String code, UUID type, int capacity) {
        return new RoomRow(id, code, "Phòng " + code, capacity, "AVAILABLE", type, "RT-QN");
    }

    private static ChargeRow charge(Instant from, Instant to) {
        String snapshot = "{\"pricingMode\":\"HOURLY\",\"from\":\"" + from
                + "\",\"to\":\"" + to + "\",\"confirmedDurationMinutes\":120}";
        return new ChargeRow(CHARGE_ID, ALLOCATION_ID, "ROOM", "Phí phòng Q01", BigDecimal.ONE,
                new BigDecimal("96000"), new BigDecimal("96000"), "ACTIVE", PRICE_RULE_ID, null,
                snapshot, from);
    }

    private static PricingRule rate() {
        return new PricingRule(PRICE_RULE_ID, "Giá QN", BRANCH_ID, LocalDate.now().minusDays(1), null,
                "ALL", null, 0, 1440, new BigDecimal("96000"), new BigDecimal("96000"), 60, 1);
    }

    private static ComboRow combo(UUID id, UUID type, String name, int minutes) {
        return new ComboRow(id, "CIN_C1_QN", 1, type, name, minutes, new BigDecimal("192000"),
                BigDecimal.ZERO, new BigDecimal("96000"), Instant.now().minusSeconds(86400), null, 0, 0);
    }

    private static MeterRow meter(String status, Instant billedFrom, Instant stoppedAt) {
        return new MeterRow(METER_ID, ALLOCATION_ID, PRICE_RULE_ID, billedFrom, billedFrom,
                billedFrom, billedFrom.plusSeconds(86400), new BigDecimal("96000"), status,
                stoppedAt, stoppedAt == null ? null : 10, stoppedAt == null ? null : new BigDecimal("16000"));
    }

    private static BookingDtos.PriceQuote quote(BigDecimal amount) {
        BookingDtos.ChargeLine line = new BookingDtos.ChargeLine("ROOM", "Giá phòng", BigDecimal.ONE,
                amount, amount, PRICE_RULE_ID, null, Map.of("pricingMode", "HOURLY"));
        return new BookingDtos.PriceQuote("HOURLY", amount, BigDecimal.ZERO, List.of(line));
    }

    private static CheckoutDtos.Issue validIssue() {
        return issue(true, "Đã kiểm tra", List.of(), false, BigDecimal.ZERO);
    }

    private static BookingDtos.CheckInRequest emptyCheckIn() {
        return new BookingDtos.CheckInRequest(List.of(), List.of());
    }

    private static CheckoutDtos.Issue issue(boolean inspected, String note, List<CheckoutDtos.Surcharge> surcharges,
            boolean overtimeConfirmed, BigDecimal overtimeAmount) {
        return new CheckoutDtos.Issue(inspected, note, surcharges, overtimeConfirmed, overtimeAmount,
                true, null, null);
    }

    private static CheckoutDtos.Issue withQuote(CheckoutFixture f, CheckoutDtos.Issue issue) {
        Map<String, Object> preview = f.service().preview(identity(), BOOKING_ID);
        return new CheckoutDtos.Issue(issue.inspected(), issue.inspectionNote(), issue.surcharges(),
                issue.overtimeConfirmed(), issue.overtimeAmount(), issue.unservedConfirmed(),
                (Instant) preview.get("checkoutAt"), (String) preview.get("quoteToken"));
    }

    private static TestResult expectIssueFailure(CheckoutFixture f, CheckoutDtos.Issue issue,
            int status, String message) {
        return expectFailure(() -> f.service().issue(identity(), BOOKING_ID, withQuote(f, issue)), status, message);
    }

    private static Map<String, Object> line(String description, BigDecimal amount) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("chargeId", UUID.randomUUID());
        line.put("orderId", null);
        line.put("description", description);
        line.put("quantity", BigDecimal.ONE);
        line.put("unitPrice", amount);
        line.put("amount", amount);
        line.put("category", "ROOM");
        return line;
    }

    private static BookingDtos.ChargeLine overtimeCharge(BigDecimal amount, long seconds) {
        return new BookingDtos.ChargeLine("OVERTIME", "Quá giờ", BigDecimal.valueOf(seconds),
                new BigDecimal("96000"), amount, PRICE_RULE_ID, null, Map.of());
    }

    private static String nowText() {
        return java.time.LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static int invocationCount(Object mock, String method) {
        return (int) org.mockito.Mockito.mockingDetails(mock).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals(method)).count();
    }

    private static List<BookingDtos.ChargeLine> insertedCharges(BookingRepository repository) {
        return org.mockito.Mockito.mockingDetails(repository).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("insertCharge"))
                .map(invocation -> (BookingDtos.ChargeLine) invocation.getArguments()[2])
                .toList();
    }

    private static TestResult expectFailure(ThrowingRunnable action, int status, String message) {
        try {
            action.run();
            return check(false, "No BookingFailure was thrown");
        } catch (BookingFailure failure) {
            return check(failure.status() == status && Objects.equals(message, failure.getMessage()),
                    "status=" + failure.status() + ", message=" + failure.getMessage());
        } catch (Throwable failure) {
            return unexpected(failure);
        }
    }

    private static TestResult expectFailureStatus(ThrowingRunnable action, int status) {
        try {
            action.run();
            return check(false, "No BookingFailure was thrown");
        } catch (BookingFailure failure) {
            return check(failure.status() == status,
                    "status=" + failure.status() + ", message=" + failure.getMessage());
        } catch (Throwable failure) {
            return unexpected(failure);
        }
    }

    private static TestResult unexpected(Throwable failure) {
        return check(false, "Unexpected " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
    }

    private static TestResult check(boolean condition, String actual) {
        return new TestResult(condition, actual);
    }

    record BookingFixture(BookingRepository repository, BookingPricingService pricing,
                          FoodOrderService foodOrders, BookingService service) {
    }

    record CheckoutFixture(BookingRepository repository, BookingService bookings, FoodOrderService foodOrders,
                           CheckoutService service, JdbcState db) {
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }

    @FunctionalInterface
    interface CheckedAction {
        TestResult run() throws Exception;
    }

    @FunctionalInterface
    interface StateCheck {
        boolean test(JdbcState state);
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
                return unexpected(failure);
            }
        }
    }

    static final class JdbcState {
        final List<String> sql = new ArrayList<>();
        List<Map<String, Object>> sourceLines = new ArrayList<>(List.of(line("Phí phòng", new BigDecimal("300000"))));
        boolean existingBill;
        boolean pendingRefund;
        int pendingOrders;
        boolean existingPlan;
        boolean requestExists;
        boolean requestExpired;
        boolean duplicateTransaction;
        String billStatus = "ISSUED";
        String lastInspectionNote = "";
        BigDecimal amount = new BigDecimal("300000");
        BigDecimal depositApplied = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal plannedCash = BigDecimal.ZERO;
        BigDecimal tendered = BigDecimal.ZERO;
        BigDecimal requestAmount = BigDecimal.ZERO;

        BigDecimal remaining() {
            return amount.subtract(depositApplied).subtract(paid);
        }

        Object answer(InvocationOnMock invocation) {
            String method = invocation.getMethod().getName();
            Object[] args = invocation.getArguments();
            String statement = args.length == 0 || !(args[0] instanceof String value) ? "" : value;
            if (!statement.isBlank()) sql.add(statement);
            if ("queryForObject".equals(method)) return ACCOUNT_ID;
            if ("queryForMap".equals(method)) return requestRow();
            if ("update".equals(method)) return update(statement, args);
            if (!"queryForList".equals(method)) return null;
            if (statement.contains("FROM gcmb.bill b JOIN gcmb.booking")) return List.of(billRow());
            if (statement.contains("FROM gcmb.bill_line")) return sourceLines;
            if (statement.contains("FROM gcmb.payment_request q JOIN")) return requestExists ? List.of(requestView()) : List.of();
            if (statement.contains("FROM gcmb.payment p WHERE bill_id") && statement.contains("ORDER BY")) return List.of();
            if (statement.contains("UNION ALL SELECT id FROM gcmb.payment_request")) return existingPlan ? List.of(Map.of("id", REQUEST_ID)) : List.of();
            if (statement.contains("SELECT id FROM gcmb.bill WHERE booking_id=? AND branch_id=?")) return existingBill ? List.of(Map.of("id", BILL_ID)) : List.of();
            if (statement.contains("SELECT id FROM gcmb.bill WHERE booking_id=?")) return existingBill ? List.of(Map.of("id", BILL_ID)) : List.of();
            if (statement.contains("SELECT id FROM gcmb.booking WHERE id=?") && statement.contains("FOR UPDATE")) return List.of(Map.of("id", BOOKING_ID));
            if (statement.contains("SELECT id FROM gcmb.bill WHERE id=? FOR UPDATE")) return List.of(Map.of("id", BILL_ID));
            if (statement.contains("FROM gcmb.refund_request")) return pendingRefund ? List.of(Map.of("id", UUID.randomUUID())) : List.of();
            if (statement.contains("FROM gcmb.fb_order") && statement.contains("PENDING")) {
                List<Map<String, Object>> rows = new ArrayList<>();
                for (int i = 0; i < pendingOrders; i++) rows.add(Map.of(
                        "id", UUID.randomUUID(), "status", "PENDING", "itemCount", BigDecimal.ONE));
                return rows;
            }
            if (statement.contains("FROM gcmb.booking_charge c")) return sourceLines;
            if (statement.contains("SELECT * FROM gcmb.payment_request WHERE reference_code")) return requestExists ? List.of(requestRow()) : List.of();
            return List.of();
        }

        private int update(String statement, Object[] args) {
            if (statement.contains("INSERT INTO gcmb.bill(")) {
                existingBill = true;
                amount = (BigDecimal) args[5];
                lastInspectionNote = String.valueOf(args[8]);
            }
            if (statement.contains("INSERT INTO gcmb.deposit_entry") && statement.contains("'APPLY'")) {
                depositApplied = (BigDecimal) args[2];
            }
            if (statement.contains("UPDATE gcmb.bill SET planned_cash")) {
                plannedCash = (BigDecimal) args[1];
                tendered = (BigDecimal) args[2];
            }
            if (statement.contains("INSERT INTO gcmb.payment_request")) {
                requestExists = true;
                requestAmount = (BigDecimal) args[6];
            }
            if (statement.contains("status='SETTLED'")) billStatus = "SETTLED";
            if (statement.contains("UPDATE gcmb.payment_request SET expires_at")) return requestExpired ? 1 : 0;
            if (statement.contains("INSERT INTO gcmb.bank_transaction")) return duplicateTransaction ? 0 : 1;
            return 1;
        }

        private Map<String, Object> billRow() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", BILL_ID);
            row.put("bookingId", BOOKING_ID);
            row.put("number", "HD-RSM-001");
            row.put("status", billStatus);
            row.put("amount", amount);
            row.put("checkoutAt", Timestamp.from(Instant.now()));
            row.put("inspectionNote", lastInspectionNote);
            row.put("plannedCash", plannedCash);
            row.put("plannedCashTendered", tendered);
            row.put("depositApplied", depositApplied);
            row.put("bookingCode", "BK-HB-001");
            row.put("customer", "Nguyễn Khách");
            row.put("checkin", Timestamp.from(Instant.now().minusSeconds(7200)));
            row.put("room", "Q01 · Phòng Q01");
            row.put("paid", paid);
            return row;
        }

        private Map<String, Object> requestView() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", REQUEST_ID);
            row.put("reference", "GZ1234567890ABCDEF1234");
            row.put("amount", requestAmount);
            row.put("status", "PENDING");
            row.put("expiresAt", Timestamp.from(Instant.now().plusSeconds(1800)));
            row.put("bank", "VCB");
            row.put("account", "0123456789");
            row.put("holder", "GENZ KARAOKE");
            return row;
        }

        private Map<String, Object> requestRow() {
            Map<String, Object> row = new HashMap<>();
            row.put("id", REQUEST_ID);
            row.put("branch_id", BRANCH_ID);
            row.put("booking_id", BOOKING_ID);
            row.put("bill_id", BILL_ID);
            row.put("purpose", "BILL");
            row.put("account_id", ACCOUNT_ID);
            row.put("expected_amount", requestAmount);
            row.put("status", "PENDING");
            row.put("created_at", Timestamp.from(Instant.now().minusSeconds(60)));
            row.put("expires_at", Timestamp.from(requestExpired ? Instant.now().minusSeconds(1) : Instant.now().plusSeconds(1740)));
            return row;
        }
    }
}
