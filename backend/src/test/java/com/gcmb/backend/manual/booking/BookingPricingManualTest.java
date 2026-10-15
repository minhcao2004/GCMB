/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created:
 * Description: Manual unit cases for booking price quotation and metered-rate selection.
 */

package com.gcmb.backend.manual.booking;

import com.gcmb.backend.booking.dto.BookingDtos;
import com.gcmb.backend.booking.repository.BookingRepository;
import com.gcmb.backend.booking.repository.BookingRepository.ComboRow;
import com.gcmb.backend.booking.repository.BookingRepository.PricingRule;
import com.gcmb.backend.booking.service.BookingPricingService;
import com.gcmb.backend.manual.ManualTestCase;
import com.gcmb.backend.manual.ManualTestSuite;
import com.gcmb.backend.manual.ManualTestSupport;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Each executor runs only after a tester selects its case in the manual runner. */
public final class BookingPricingManualTest {
    private static final ZoneId TIMEZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final UUID ROOM_TYPE_ID = uuid("room-type-standard");
    private static final UUID OTHER_ROOM_TYPE_ID = uuid("room-type-vip");
    private static final UUID BRANCH_ID = uuid("branch-ho-tay");
    private static final UUID COMBO_ID = uuid("combo-2-hours");
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);

    private BookingPricingManualTest() {
    }

    public static ManualTestSuite suite() {
        List<ManualTestCase> cases = new ArrayList<>();

        cases.add(test(1, "null-start", "quote", "SRS UC-34 step 4; BR-02",
                "No repository setup is required.",
                "start=null; end=2026-10-19 11:00 Asia/Ho_Chi_Minh; mode=HOURLY",
                "BookingFailure status=400 with the invalid-period message; no repository interaction.", () -> {
                    Context context = context();
                    return ManualTestSupport.observe(
                            () -> context.service.quote(ROOM_TYPE_ID, BRANCH_ID, TIMEZONE, null,
                                    local(2026, 10, 19, 11, 0), "HOURLY", null),
                            context.repository);
                }));

        cases.add(test(2, "end-equals-start", "quote", "SRS UC-34 step 4; BR-02; BVA",
                "No repository setup is required.",
                "start=end=2026-10-19 10:00 Asia/Ho_Chi_Minh; mode=HOURLY",
                "BookingFailure status=400 because the end must be later than the start; no repository interaction.", () -> {
                    Context context = context();
                    Instant at = local(2026, 10, 19, 10, 0);
                    return ManualTestSupport.observe(
                            () -> context.service.quote(ROOM_TYPE_ID, BRANCH_ID, TIMEZONE, at, at,
                                    "HOURLY", null),
                            context.repository);
                }));

        cases.add(test(3, "unsupported-mode", "quote", "SRS UC-34 step 3; source-derived validation",
                "No repository setup is required.",
                "mode='DAILY' for a valid one-hour interval",
                "BookingFailure status=400 for unsupported pricing mode; no repository interaction.", () -> {
                    Context context = context();
                    return ManualTestSupport.observe(
                            () -> context.service.quote(ROOM_TYPE_ID, BRANCH_ID, TIMEZONE,
                                    local(2026, 10, 19, 10, 0), local(2026, 10, 19, 11, 0),
                                    "DAILY", null),
                            context.repository);
                }));

        cases.add(test(4, "hourly-with-combo", "quote", "SRS UC-34 step 3; source-derived validation",
                "No repository setup is required.",
                "mode=HOURLY and comboId is also supplied",
                "BookingFailure status=400 because comboId is incompatible with HOURLY; no repository interaction.", () -> {
                    Context context = context();
                    return ManualTestSupport.observe(
                            () -> context.service.quote(ROOM_TYPE_ID, BRANCH_ID, TIMEZONE,
                                    local(2026, 10, 19, 10, 0), local(2026, 10, 19, 11, 0),
                                    "HOURLY", COMBO_ID),
                            context.repository);
                }));

        cases.add(test(5, "blank-mode-defaults-hourly", "quote",
                "SRS UC-34 steps 5 and 7; source-derived default mode",
                "A weekday ALL rule covers the complete interval.",
                "mode is blank; interval=10:00-11:00; hourlyPrice=120000; billingStep=30",
                "Return HOURLY quote, expectedAmount=120000, deposit=0 and one ROOM charge; observe one pricingRules lookup.", () -> {
                    Context context = context();
                    PricingRule rule = rule("all-day", "Standard hourly", BRANCH_ID, MONDAY, null,
                            "ALL", null, 0, 1440, "120000", 30, 1);
                    when(context.repository.pricingRules(ROOM_TYPE_ID, BRANCH_ID, MONDAY, MONDAY))
                            .thenReturn(List.of(rule));
                    return ManualTestSupport.observe(
                            () -> context.service.quote(ROOM_TYPE_ID, BRANCH_ID, TIMEZONE,
                                    local(2026, 10, 19, 10, 0), local(2026, 10, 19, 11, 0),
                                    "  ", null),
                            context.repository);
                }));

        cases.add(test(6, "hourly-no-rules", "quote", "SRS UC-34 steps 5 and 7; error guessing",
                "The repository returns no price rule for the requested date.",
                "mode=HOURLY; interval=10:00-11:00",
                "BookingFailure status=409 indicating that hourly pricing is not configured.", () -> {
                    Context context = context();
                    when(context.repository.pricingRules(ROOM_TYPE_ID, BRANCH_ID, MONDAY, MONDAY))
                            .thenReturn(List.of());
                    return ManualTestSupport.observe(
                            () -> context.service.quote(ROOM_TYPE_ID, BRANCH_ID, TIMEZONE,
                                    local(2026, 10, 19, 10, 0), local(2026, 10, 19, 11, 0),
                                    "HOURLY", null),
                            context.repository);
                }));

        cases.add(test(7, "hourly-gap-between-rules", "quote",
                "SRS UC-34 steps 5 and 7; source-derived complete-coverage guard",
                "A price rule covers only 10:00-10:30.",
                "mode=HOURLY; requested interval=10:00-11:00",
                "BookingFailure status=409 when quotation reaches the uncovered 10:30-11:00 segment.", () -> {
                    Context context = context();
                    PricingRule partial = rule("partial", "Morning partial", BRANCH_ID, MONDAY, null,
                            "ALL", null, 600, 630, "120000", 30, 1);
                    when(context.repository.pricingRules(ROOM_TYPE_ID, BRANCH_ID, MONDAY, MONDAY))
                            .thenReturn(List.of(partial));
                    return ManualTestSupport.observe(
                            () -> context.service.quote(ROOM_TYPE_ID, BRANCH_ID, TIMEZONE,
                                    local(2026, 10, 19, 10, 0), local(2026, 10, 19, 11, 0),
                                    "HOURLY", null),
                            context.repository);
                }));

        cases.add(test(8, "hourly-rounds-each-segment", "quote",
                "SRS UC-34 step 7; source-derived billing-step calculation; BVA",
                "One price rule covers the complete interval.",
                "interval=10:00-11:01 (61 actual minutes); hourlyPrice=120000; billingStep=30",
                "Return one charge with actualMinutes=61, billedMinutes=90, quantity=1.500 and amount/total=180000.", () -> {
                    Context context = context();
                    PricingRule rule = rule("rounding", "Thirty-minute steps", BRANCH_ID, MONDAY, null,
                            "ALL", null, 0, 1440, "120000", 30, 1);
                    when(context.repository.pricingRules(ROOM_TYPE_ID, BRANCH_ID, MONDAY, MONDAY))
                            .thenReturn(List.of(rule));
                    return ManualTestSupport.observe(
                            () -> context.service.quote(ROOM_TYPE_ID, BRANCH_ID, TIMEZONE,
                                    local(2026, 10, 19, 10, 0), local(2026, 10, 19, 11, 1),
                                    "HOURLY", null),
                            context.repository);
                }));

        cases.add(test(9, "hourly-splits-rule-boundary", "quote",
                "SRS UC-34 steps 5 and 7; source-derived time-band calculation",
                "Two non-overlapping rules meet at 10:30.",
                "10:00-10:30 at 120000/hour; 10:30-11:00 at 180000/hour; both step=30",
                "Return two charges of 60000 and 90000 with total=150000; snapshots retain each segment boundary and rule.", () -> {
                    Context context = context();
                    PricingRule first = rule("band-1", "Early band", BRANCH_ID, MONDAY, null,
                            "ALL", null, 600, 630, "120000", 30, 1);
                    PricingRule second = rule("band-2", "Late band", BRANCH_ID, MONDAY, null,
                            "ALL", null, 630, 660, "180000", 30, 1);
                    when(context.repository.pricingRules(ROOM_TYPE_ID, BRANCH_ID, MONDAY, MONDAY))
                            .thenReturn(List.of(first, second));
                    return ManualTestSupport.observe(
                            () -> context.service.quote(ROOM_TYPE_ID, BRANCH_ID, TIMEZONE,
                                    local(2026, 10, 19, 10, 0), local(2026, 10, 19, 11, 0),
                                    "HOURLY", null),
                            context.repository);
                }));

        cases.add(test(10, "hourly-branch-overrides-global", "quote",
                "SRS UC-34 step 5; source-derived rule precedence",
                "A global and a branch-specific rule both cover the same interval.",
                "global price=999000 priority=99; branch price=100000 priority=0",
                "Select the branch-specific rule before comparing priority; return total=100000 and its rule ID in the charge.", () -> {
                    Context context = context();
                    PricingRule global = rule("global", "Global", null, MONDAY, null,
                            "ALL", null, 0, 1440, "999000", 60, 99);
                    PricingRule branch = rule("branch", "Branch override", BRANCH_ID, MONDAY, null,
                            "ALL", null, 0, 1440, "100000", 60, 0);
                    when(context.repository.pricingRules(ROOM_TYPE_ID, BRANCH_ID, MONDAY, MONDAY))
                            .thenReturn(List.of(global, branch));
                    return ManualTestSupport.observe(
                            () -> context.service.quote(ROOM_TYPE_ID, BRANCH_ID, TIMEZONE,
                                    local(2026, 10, 19, 10, 0), local(2026, 10, 19, 11, 0),
                                    "HOURLY", null),
                            context.repository);
                }));

        cases.add(test(11, "hourly-priority-precedence", "quote",
                "SRS UC-34 step 5; source-derived rule precedence",
                "Two branch rules with the same day kind cover the interval.",
                "priority 1 price=100000; priority 2 price=150000",
                "Select priority 2 and return total=150000.", () -> {
                    Context context = context();
                    PricingRule low = rule("priority-low", "Low priority", BRANCH_ID, MONDAY, null,
                            "ALL", null, 0, 1440, "100000", 60, 1);
                    PricingRule high = rule("priority-high", "High priority", BRANCH_ID, MONDAY, null,
                            "ALL", null, 0, 1440, "150000", 60, 2);
                    when(context.repository.pricingRules(ROOM_TYPE_ID, BRANCH_ID, MONDAY, MONDAY))
                            .thenReturn(List.of(low, high));
                    return ManualTestSupport.observe(
                            () -> context.service.quote(ROOM_TYPE_ID, BRANCH_ID, TIMEZONE,
                                    local(2026, 10, 19, 10, 0), local(2026, 10, 19, 11, 0),
                                    "HOURLY", null),
                            context.repository);
                }));

        cases.add(test(12, "hourly-specific-date-precedence", "quote",
                "SRS UC-34 step 5; source-derived rule precedence",
                "WEEKDAY and SPECIFIC branch rules have equal priority and cover the same Monday.",
                "weekday price=100000; specific-date price=160000; equal priority",
                "Select SPECIFIC as the more specific day kind and return total=160000.", () -> {
                    Context context = context();
                    PricingRule weekday = rule("weekday", "Weekday", BRANCH_ID, MONDAY, null,
                            "WEEKDAY", null, 0, 1440, "100000", 60, 1);
                    PricingRule specific = rule("specific", "Specific date", BRANCH_ID, MONDAY, null,
                            "SPECIFIC", MONDAY, 0, 1440, "160000", 60, 1);
                    when(context.repository.pricingRules(ROOM_TYPE_ID, BRANCH_ID, MONDAY, MONDAY))
                            .thenReturn(List.of(weekday, specific));
                    return ManualTestSupport.observe(
                            () -> context.service.quote(ROOM_TYPE_ID, BRANCH_ID, TIMEZONE,
                                    local(2026, 10, 19, 10, 0), local(2026, 10, 19, 11, 0),
                                    "HOURLY", null),
                            context.repository);
                }));

        cases.add(test(13, "hourly-crosses-midnight", "quote",
                "SRS UC-34 step 7; source-derived date-boundary handling",
                "One ALL rule remains valid across both local dates.",
                "interval=2026-10-19 23:30 to 2026-10-20 00:30; price=120000; step=30",
                "Query rules for 2026-10-19 through 2026-10-20 and return a merged 60-minute charge totaling 120000.", () -> {
                    Context context = context();
                    LocalDate tuesday = MONDAY.plusDays(1);
                    PricingRule rule = rule("cross-midnight", "All day", BRANCH_ID, MONDAY, null,
                            "ALL", null, 0, 1440, "120000", 30, 1);
                    when(context.repository.pricingRules(ROOM_TYPE_ID, BRANCH_ID, MONDAY, tuesday))
                            .thenReturn(List.of(rule));
                    return ManualTestSupport.observe(
                            () -> context.service.quote(ROOM_TYPE_ID, BRANCH_ID, TIMEZONE,
                                    local(2026, 10, 19, 23, 30), local(2026, 10, 20, 0, 30),
                                    "HOURLY", null),
                            context.repository);
                }));

        cases.add(test(14, "combo-id-required", "quote", "SRS UC-34 step 3; source-derived validation",
                "No repository setup is required.",
                "mode=COMBO; comboId=null; interval=2 hours",
                "BookingFailure status=400 requiring a combo; no repository interaction.", () -> {
                    Context context = context();
                    return ManualTestSupport.observe(
                            () -> context.service.quote(ROOM_TYPE_ID, BRANCH_ID, TIMEZONE,
                                    local(2026, 10, 19, 10, 0), local(2026, 10, 19, 12, 0),
                                    "COMBO", null),
                            context.repository);
                }));

        cases.add(test(15, "combo-room-type-mismatch", "quote", "SRS UC-34 steps 3 and 7",
                "The selected combo exists but belongs to another room type.",
                "requested roomType=STANDARD; combo roomType=VIP",
                "BookingFailure status=400; no quote is returned.", () -> {
                    Context context = context();
                    when(context.repository.combo(COMBO_ID)).thenReturn(combo(OTHER_ROOM_TYPE_ID,
                            local(2026, 10, 1, 0, 0), null));
                    return ManualTestSupport.observe(
                            () -> context.service.quote(ROOM_TYPE_ID, BRANCH_ID, TIMEZONE,
                                    local(2026, 10, 19, 10, 0), local(2026, 10, 19, 12, 0),
                                    "COMBO", COMBO_ID),
                            context.repository);
                }));

        cases.add(test(16, "combo-valid-to-exclusive", "quote",
                "SRS UC-34 steps 3 and 7; source-derived validity boundary; BVA",
                "The selected combo belongs to the room type and has validTo equal to booking start.",
                "combo validTo=booking start=2026-10-19 10:00",
                "BookingFailure status=409 because validTo is an exclusive boundary.", () -> {
                    Context context = context();
                    Instant start = local(2026, 10, 19, 10, 0);
                    when(context.repository.combo(COMBO_ID)).thenReturn(combo(ROOM_TYPE_ID,
                            local(2026, 10, 1, 0, 0), start));
                    return ManualTestSupport.observe(
                            () -> context.service.quote(ROOM_TYPE_ID, BRANCH_ID, TIMEZONE,
                                    start, local(2026, 10, 19, 12, 0), "COMBO", COMBO_ID),
                            context.repository);
                }));

        cases.add(test(17, "combo-duration-mismatch", "quote", "SRS UC-34 steps 3 and 7",
                "An active 120-minute combo belongs to the requested room type.",
                "booking interval=119 minutes; combo duration=120 minutes",
                "BookingFailure status=400 because the reserved duration does not equal the combo duration.", () -> {
                    Context context = context();
                    when(context.repository.combo(COMBO_ID)).thenReturn(combo(ROOM_TYPE_ID,
                            local(2026, 10, 1, 0, 0), null));
                    return ManualTestSupport.observe(
                            () -> context.service.quote(ROOM_TYPE_ID, BRANCH_ID, TIMEZONE,
                                    local(2026, 10, 19, 10, 0), local(2026, 10, 19, 11, 59),
                                    "COMBO", COMBO_ID),
                            context.repository);
                }));

        cases.add(test(18, "combo-success-preserves-snapshot", "quote",
                "SRS UC-34 steps 7 and 11; BR-13",
                "An active 120-minute combo belongs to the requested room type.",
                "price=300000; deposit=100000; overtime=150000; interval exactly 120 minutes",
                "Return COMBO quote amount=300000 and deposit=100000 with one COMBO charge; snapshot retains code, version, name, duration, interval, deposit and overtime price.", () -> {
                    Context context = context();
                    when(context.repository.combo(COMBO_ID)).thenReturn(combo(ROOM_TYPE_ID,
                            local(2026, 10, 1, 0, 0), null));
                    return ManualTestSupport.observe(
                            () -> context.service.quote(ROOM_TYPE_ID, BRANCH_ID, TIMEZONE,
                                    local(2026, 10, 19, 10, 0), local(2026, 10, 19, 12, 0),
                                    "COMBO", COMBO_ID),
                            context.repository);
                }));

        cases.add(test(19, "metered-rate-branch-precedence", "meteredRate",
                "Source-derived metered-rate selection; risk-based support for UC-38/UC-40",
                "A global and a branch-specific rule both cover the local start minute.",
                "at=Monday 10:15; global priority=99; branch priority=0",
                "Return the branch-specific PricingRule; observe date/minute conversion in Asia/Ho_Chi_Minh and one repository lookup.", () -> {
                    Context context = context();
                    PricingRule global = rule("meter-global", "Meter global", null, MONDAY, null,
                            "ALL", null, 0, 1440, "999000", 1, 99);
                    PricingRule branch = rule("meter-branch", "Meter branch", BRANCH_ID, MONDAY, null,
                            "ALL", null, 600, 660, "120000", 1, 0);
                    when(context.repository.pricingRules(ROOM_TYPE_ID, BRANCH_ID, MONDAY, MONDAY))
                            .thenReturn(List.of(global, branch));
                    return ManualTestSupport.observe(
                            () -> context.service.meteredRate(ROOM_TYPE_ID, BRANCH_ID, TIMEZONE,
                                    local(2026, 10, 19, 10, 15)),
                            context.repository);
                }));

        cases.add(test(20, "metered-rate-no-applicable-rule", "meteredRate",
                "Source-derived metered-rate selection; error guessing",
                "The repository returns a HOLIDAY rule only; current implementation has no holiday calendar.",
                "at=Monday 10:15; rule dayKind=HOLIDAY",
                "BookingFailure status=409 indicating that no metered rate applies. This is a documented requirement gap, not proof that holiday pricing is unsupported by business.", () -> {
                    Context context = context();
                    PricingRule holiday = rule("holiday", "Holiday", BRANCH_ID, MONDAY, null,
                            "HOLIDAY", null, 0, 1440, "200000", 1, 1);
                    when(context.repository.pricingRules(ROOM_TYPE_ID, BRANCH_ID, MONDAY, MONDAY))
                            .thenReturn(List.of(holiday));
                    return ManualTestSupport.observe(
                            () -> context.service.meteredRate(ROOM_TYPE_ID, BRANCH_ID, TIMEZONE,
                                    local(2026, 10, 19, 10, 15)),
                            context.repository);
                }));

        return new ManualTestSuite("Booking", "BookingPricingService", cases);
    }

    private static Context context() {
        BookingRepository repository = mock(BookingRepository.class);
        return new Context(repository, new BookingPricingService(repository));
    }

    private static ComboRow combo(UUID roomTypeId, Instant validFrom, Instant validTo) {
        return new ComboRow(COMBO_ID, "CB-2H", 3, roomTypeId, "Combo 2 Hours", 120,
                new BigDecimal("300000"), new BigDecimal("100000"), new BigDecimal("150000"),
                validFrom, validTo);
    }

    private static PricingRule rule(
            String id,
            String name,
            UUID branchId,
            LocalDate validFrom,
            LocalDate validTo,
            String dayKind,
            LocalDate specificDate,
            int minuteFrom,
            int minuteTo,
            String hourlyPrice,
            int billingStepMinutes,
            int priority) {
        BigDecimal price = new BigDecimal(hourlyPrice);
        return new PricingRule(uuid(id), name, branchId, validFrom, validTo, dayKind, specificDate,
                minuteFrom, minuteTo, price, price, billingStepMinutes, priority);
    }

    private static Instant local(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, TIMEZONE).toInstant();
    }

    private static UUID uuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static ManualTestCase test(
            int number,
            String key,
            String method,
            String traceability,
            String preconditions,
            String input,
            String expected,
            ManualTestCase.Executor executor) {
        String id = "UT-BKP-%03d".formatted(number);
        return new ManualTestCase(
                "booking-pricing/" + key,
                id,
                null,
                "BookingPricingService",
                method,
                "Unit",
                traceability,
                preconditions,
                input,
                expected,
                executor);
    }

    private record Context(BookingRepository repository, BookingPricingService service) {
    }
}
