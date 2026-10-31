/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 29/10/2026
 * Description: Manual unit cases for UC-34 Create Booking.
 */
package com.gcmb.backend.manual.bookingmanagement;

import static com.gcmb.backend.manual.bookingmanagement.BookingManagementManualRunner.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gcmb.backend.booking.dto.BookingDtos;
import com.gcmb.backend.booking.exception.BookingFailure;
import com.gcmb.backend.booking.repository.BookingRepository;
import com.gcmb.backend.booking.repository.BookingRepository.ComboRow;
import com.gcmb.backend.booking.service.BookingPricingService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class CreateBookingManualCases {
    private static final String UC = "UC-34 Create Booking";
    private static final String PRE = "Receptionist uc3437.demo đã đăng nhập, được phân công tại DEMO-UC3437 active; dùng dữ liệu seed UC34-37.";

    private CreateBookingManualCases() {
    }

    static List<ManualTest> cases() {
        return List.of(
            t("BKM-001", "BookingService.options", "Identity uc3437.demo",
                "Trả đúng branch, timezone, room type và combo; không ghi dữ liệu", "Normal", () -> {
                    Fixture f = fresh();
                    when(f.repository().roomTypes()).thenReturn(List.of(roomType()));
                    when(f.repository().combos(ROOM_TYPE_ID)).thenReturn(List.of(combo()));
                    BookingDtos.BookingOptions actual = f.service().options(identity());
                    boolean ok = actual.branchId().equals(BRANCH_ID)
                            && actual.timezone().equals("Asia/Ho_Chi_Minh")
                            && actual.roomTypes().size() == 1
                            && actual.roomTypes().get(0).combos().size() == 1;
                    return check(ok, actual.toString());
                }),
            t("BKM-002", "BookingService.availability", "Start tương lai, end sau 2 giờ, HOURLY",
                "Trả quote, cọc yêu cầu và phòng trống; không ghi booking", "Normal", () -> {
                    Fixture f = fresh();
                    Instant start = futureMinutes(120), end = start.plusSeconds(7200);
                    when(f.repository().availableRooms(BRANCH_ID, ROOM_TYPE_ID, start, end)).thenReturn(List.of(room()));
                    BookingDtos.Availability actual = f.service().availability(identity(), ROOM_TYPE_ID, start, end, "HOURLY", null);
                    verify(f.repository(), never()).insertBooking(any(), anyString(), anyString(), anyString(),
                            org.mockito.ArgumentMatchers.anyInt(), anyString(), any(), any(),
                            org.mockito.ArgumentMatchers.anyInt(), any(), any());
                    return check(actual.rooms().size() == 1
                            && actual.quote().expectedAmount().compareTo(new BigDecimal("360000")) == 0,
                            "rooms=" + actual.rooms().size() + ", quote=" + actual.quote().expectedAmount());
                }),
            t("BKM-003", "BookingService.availability", "Khoảng hợp lệ nhưng không có phòng trống",
                "Trả rooms rỗng và không tạo booking", "Normal", () -> {
                    Fixture f = fresh();
                    Instant start = futureMinutes(120), end = start.plusSeconds(7200);
                    when(f.repository().availableRooms(BRANCH_ID, ROOM_TYPE_ID, start, end)).thenReturn(List.of());
                    BookingDtos.Availability actual = f.service().availability(identity(), ROOM_TYPE_ID, start, end, "HOURLY", null);
                    return check(actual.rooms().isEmpty(), "rooms=" + actual.rooms().size());
                }),
            t("BKM-004", "BookingService.availability", "plannedStart=null",
                "MSG02: Vui lòng bổ sung Thời gian bắt đầu.", "Abnormal", () -> {
                    Fixture f = fresh();
                    return expectFailure(() -> f.service().availability(identity(), ROOM_TYPE_ID, null,
                            futureMinutes(120), "HOURLY", null), 400,
                            "Vui lòng bổ sung Thời gian bắt đầu.");
                }),
            t("BKM-005", "BookingService.availability", "End bằng hoặc trước start",
                "MSG07: Thời gian kết thúc phải sau thời gian bắt đầu.", "Abnormal", () -> {
                    Fixture f = fresh();
                    Instant start = futureMinutes(120);
                    return expectFailure(() -> f.service().availability(identity(), ROOM_TYPE_ID, start,
                            start, "HOURLY", null), 400,
                            "Thời gian kết thúc phải sau thời gian bắt đầu.");
                }),
            t("BKM-006", "BookingService.availability", "Advance booking có start trong quá khứ",
                "MSG52 đúng tài liệu", "Abnormal", () -> {
                    Fixture f = fresh();
                    Instant start = Instant.now().minusSeconds(300);
                    return expectFailure(() -> f.service().availability(identity(), ROOM_TYPE_ID, start,
                            start.plusSeconds(7200), "HOURLY", null), 400,
                            "Thời gian bắt đầu của booking đặt trước không được ở quá khứ. Vui lòng chọn lại thời gian.");
                }),
            t("BKM-007", "BookingService.availability", "Room type không tồn tại hoặc inactive",
                "MSG63 đúng tài liệu", "Abnormal", () -> {
                    Fixture f = fresh();
                    when(f.repository().roomType(ROOM_TYPE_ID)).thenReturn(null);
                    Instant start = futureMinutes(120);
                    return expectFailure(() -> f.service().availability(identity(), ROOM_TYPE_ID, start,
                            start.plusSeconds(7200), "HOURLY", null), 404,
                            "Không có phòng hoặc mức giá phù hợp với lựa chọn hiện tại. Vui lòng thay đổi thời gian, hạng phòng hoặc hình thức thuê.");
                }),
            t("BKM-008", "BookingPricingService.quote", "Combo sai room type hoặc ngoài thời hạn",
                "MSG62 đúng tài liệu", "Abnormal", () -> {
                    Instant start = futureMinutes(120), end = start.plusSeconds(7200);
                    String expected = "Combo Music Box demo 2 giờ không áp dụng cho hạng phòng hoặc giờ bắt đầu đã chọn. Vui lòng chọn combo phù hợp.";
                    BookingRepository r1 = mock(BookingRepository.class);
                    ComboRow wrongType = new ComboRow(COMBO_ID, combo().code(), 1, OTHER_ROOM_TYPE_ID,
                            combo().name(), 120, combo().price(), combo().depositRequired(),
                            combo().overtimeHourlyPrice(), start.minusSeconds(60), null);
                    when(r1.combo(COMBO_ID)).thenReturn(wrongType);
                    TestResult a = expectFailure(() -> new BookingPricingService(r1).quote(ROOM_TYPE_ID,
                            BRANCH_ID, BRANCH.timezone(), start, end, "COMBO", COMBO_ID), 400, expected);
                    BookingRepository r2 = mock(BookingRepository.class);
                    ComboRow futureCombo = new ComboRow(COMBO_ID, combo().code(), 1, ROOM_TYPE_ID,
                            combo().name(), 120, combo().price(), combo().depositRequired(),
                            combo().overtimeHourlyPrice(), start.plusSeconds(60), null);
                    when(r2.combo(COMBO_ID)).thenReturn(futureCombo);
                    TestResult b = expectFailure(() -> new BookingPricingService(r2).quote(ROOM_TYPE_ID,
                            BRANCH_ID, BRANCH.timezone(), start, end, "COMBO", COMBO_ID), 409, expected);
                    return combine(a, b);
                }),
            t("BKM-009", "BookingPricingService.quote", "Combo 120 phút nhưng end không khớp",
                "Từ chối quote và không tạo charge", "Abnormal", () -> {
                    BookingRepository repository = mock(BookingRepository.class);
                    when(repository.combo(COMBO_ID)).thenReturn(combo());
                    Instant start = futureMinutes(120);
                    return expectFailureStatus(() -> new BookingPricingService(repository).quote(ROOM_TYPE_ID,
                            BRANCH_ID, BRANCH.timezone(), start, start.plusSeconds(7100), "COMBO", COMBO_ID), 400);
                }),
            t("BKM-010", "BookingService.create", "Thời lượng bằng 0",
                "Từ chối bằng MSG07/MSG48", "Boundary", () -> {
                    Fixture f = fresh();
                    Instant start = futureMinutes(120);
                    BookingDtos.CreateBookingRequest request = createRequest(start, start, "HOURLY", null,
                            "Nguyễn Khách Demo", "0900003437", 2, null, BigDecimal.ZERO);
                    return expectFailure(() -> f.service().create(identity(), request), 400,
                            "Thời gian kết thúc phải sau thời gian bắt đầu.");
                }),
            t("BKM-011", "BookingService.create", "Booking HOURLY 90 phút",
                "Được chấp nhận vì thời lượng lớn hơn 0", "Boundary", () -> {
                    Fixture f = fresh();
                    Instant start = futureMinutes(120);
                    BookingDtos.CreateBookingRequest request = createRequest(start, start.plusSeconds(5400),
                            "HOURLY", null, "Nguyễn Khách Demo", "0900003437", 2, null, BigDecimal.ZERO);
                    try {
                        BookingDtos.BookingDetail actual = f.service().create(identity(), request);
                        return check(actual != null, "Booking accepted for 90 minutes");
                    } catch (BookingFailure failure) {
                        return check(false, "status=" + failure.status() + ", message=" + failure.getMessage());
                    }
                }),
            t("BKM-012", "BookingService.create", "Booking giờ hợp lệ, không cọc",
                "Tạo đúng booking, allocation, deposit account, charge và event", "Normal", () -> {
                    Fixture f = fresh();
                    BookingDtos.BookingDetail actual = f.service().create(identity(), validCreate());
                    verify(f.repository()).insertBooking(eq(BRANCH_ID), anyString(), anyString(), anyString(),
                            eq(2), eq("PHONE"), any(), any(), eq(40), eq(ACTOR_ID), any());
                    verify(f.repository()).insertAllocation(eq(BRANCH_ID), eq(BOOKING_ID), eq(ROOM_ID), any(), any(), eq(ACTOR_ID));
                    verify(f.repository()).createDepositAccount(BOOKING_ID);
                    verify(f.repository()).insertCharge(eq(BOOKING_ID), eq(ALLOCATION_ID), any());
                    verify(f.repository()).event(eq(BOOKING_ID), eq(ACTOR_ID), eq("BOOKING_CREATED"), eq(null), any());
                    return check(actual != null, "All booking records were requested once");
                }),
            t("BKM-013", "BookingService.create", "Booking combo 120 phút hợp lệ",
                "Lưu combo, giá snapshot và allocation", "Normal", () -> {
                    Fixture f = fresh();
                    when(f.pricing().quote(any(), any(), any(), any(), any(), eq("COMBO"), eq(COMBO_ID)))
                            .thenReturn(comboQuote());
                    Instant start = futureMinutes(120);
                    BookingDtos.CreateBookingRequest request = createRequest(start, start.plusSeconds(7200),
                            "COMBO", COMBO_ID, "Nguyễn Khách Demo", "0900003437", 2, null, BigDecimal.ZERO);
                    BookingDtos.BookingDetail actual = f.service().create(identity(), request);
                    verify(f.repository()).insertCharge(eq(BOOKING_ID), eq(ALLOCATION_ID),
                            org.mockito.ArgumentMatchers.argThat(line -> COMBO_ID.equals(line.comboId())));
                    return check(actual != null, "Combo booking created");
                }),
            t("BKM-014", "BookingService.create", "contactName null, rỗng, chỉ khoảng trắng",
                "MSG02 cho Tên khách hàng", "Abnormal", () -> {
                    TestResult[] results = new TestResult[3];
                    String[] values = {null, "", "   "};
                    for (int i = 0; i < values.length; i++) {
                        Fixture f = fresh();
                        BookingDtos.CreateBookingRequest base = validCreate();
                        BookingDtos.CreateBookingRequest request = createRequest(base.plannedStart(), base.plannedEnd(),
                                "HOURLY", null, values[i], "0900003437", 2, null, BigDecimal.ZERO);
                        results[i] = expectFailure(() -> f.service().create(identity(), request), 400,
                                "Vui lòng bổ sung Tên khách hàng.");
                    }
                    return combine(results);
                }),
            t("BKM-015", "BookingService.create", "Tên khách 200 và 201 ký tự",
                "200 được nhận; 201 trả MSG03", "Boundary", () -> {
                    Fixture accepted = fresh();
                    BookingDtos.CreateBookingRequest base = validCreate();
                    String max = "N".repeat(200);
                    BookingDtos.BookingDetail detail = accepted.service().create(identity(), createRequest(
                            base.plannedStart(), base.plannedEnd(), "HOURLY", null, max,
                            "0900003437", 2, null, BigDecimal.ZERO));
                    TestResult a = check(detail != null, "200 characters accepted");
                    Fixture rejected = fresh();
                    TestResult b = expectFailure(() -> rejected.service().create(identity(), createRequest(
                            base.plannedStart(), base.plannedEnd(), "HOURLY", null, "N".repeat(201),
                            "0900003437", 2, null, BigDecimal.ZERO)), 400,
                            "Tên khách hàng chỉ được chứa tối đa 200 ký tự.");
                    return combine(a, b);
                }),
            t("BKM-016", "BookingService.create", "contactPhone=0900003437",
                "Chấp nhận và lưu đúng số", "Normal", () -> {
                    Fixture f = fresh();
                    BookingDtos.BookingDetail detail = f.service().create(identity(), validCreate());
                    verify(f.repository()).insertBooking(eq(BRANCH_ID), anyString(), anyString(),
                            eq("0900003437"), any(Integer.class), anyString(), any(), any(),
                            any(Integer.class), eq(ACTOR_ID), any());
                    return check(detail != null, "Phone accepted");
                }),
            t("BKM-017", "BookingService.create", "contactPhone=+84900003437",
                "Chấp nhận theo BR-119", "Normal", () -> {
                    Fixture f = fresh();
                    BookingDtos.CreateBookingRequest base = validCreate();
                    try {
                        BookingDtos.BookingDetail detail = f.service().create(identity(), createRequest(
                                base.plannedStart(), base.plannedEnd(), "HOURLY", null, base.contactName(),
                                "+84900003437", 2, null, BigDecimal.ZERO));
                        return check(detail != null, "Leading plus accepted");
                    } catch (BookingFailure failure) {
                        return check(false, "status=" + failure.status() + ", message=" + failure.getMessage());
                    }
                }),
            t("BKM-018", "BookingService.create", "Phone chứa chữ, khoảng trắng hoặc nhiều dấu +",
                "MSG04 đúng tài liệu", "Abnormal", () -> {
                    String[] invalid = {"0900A03437", "0900 03437", "84+90003437", "++8490003437"};
                    List<TestResult> results = new java.util.ArrayList<>();
                    for (String phone : invalid) {
                        Fixture f = fresh();
                        BookingDtos.CreateBookingRequest base = validCreate();
                        results.add(expectFailure(() -> f.service().create(identity(), createRequest(
                                base.plannedStart(), base.plannedEnd(), "HOURLY", null, base.contactName(),
                                phone, 2, null, BigDecimal.ZERO)), 400,
                                "Số điện thoại chỉ được chứa chữ số và có thể có một dấu + ở đầu."));
                    }
                    return combine(results.toArray(TestResult[]::new));
                }),
            t("BKM-019", "BookingService.create", "guestCount=1 và capacity=4",
                "Cả hai biên được chấp nhận", "Boundary", () -> {
                    BookingDtos.CreateBookingRequest base = validCreate();
                    Fixture one = fresh();
                    Fixture four = fresh();
                    BookingDtos.BookingDetail a = one.service().create(identity(), createRequest(base.plannedStart(),
                            base.plannedEnd(), "HOURLY", null, base.contactName(), base.contactPhone(), 1, null, BigDecimal.ZERO));
                    BookingDtos.BookingDetail b = four.service().create(identity(), createRequest(base.plannedStart(),
                            base.plannedEnd(), "HOURLY", null, base.contactName(), base.contactPhone(), 4, null, BigDecimal.ZERO));
                    return check(a != null && b != null, "guestCount 1 and 4 accepted");
                }),
            t("BKM-020", "BookingService.create", "guestCount=0",
                "MSG44 đúng tài liệu", "Boundary", () -> {
                    Fixture f = fresh();
                    BookingDtos.CreateBookingRequest base = validCreate();
                    return expectFailure(() -> f.service().create(identity(), createRequest(base.plannedStart(),
                            base.plannedEnd(), "HOURLY", null, base.contactName(), base.contactPhone(), 0,
                            null, BigDecimal.ZERO)), 400, "Số khách phải là số nguyên lớn hơn 0.");
                }),
            t("BKM-021", "BookingService.create", "guestCount=5, capacity=4",
                "MSG54 đúng tài liệu", "Boundary", () -> {
                    Fixture f = fresh();
                    BookingDtos.CreateBookingRequest base = validCreate();
                    return expectFailure(() -> f.service().create(identity(), createRequest(base.plannedStart(),
                            base.plannedEnd(), "HOURLY", null, base.contactName(), base.contactPhone(), 5,
                            null, BigDecimal.ZERO)), 400,
                            "Phòng DEMO-MB-01 chỉ phù hợp tối đa 4 khách. Vui lòng giảm số khách hoặc chọn phòng có sức chứa lớn hơn.");
                }),
            t("BKM-022", "BookingService.create", "Chọn DEMO-MB-06 đang maintenance",
                "MSG55 và không tạo allocation", "Abnormal", () -> {
                    Fixture f = fresh();
                    when(f.repository().lockAvailableRoom(ROOM_ID, BRANCH_ID)).thenReturn(null);
                    TestResult result = expectFailure(() -> f.service().create(identity(), validCreate()), 409,
                            "Phòng DEMO-MB-06 đang bảo trì và chưa thể sử dụng. Vui lòng chọn phòng khác.");
                    verify(f.repository(), never()).insertAllocation(any(), any(), any(), any(), any(), any());
                    return result;
                }),
            t("BKM-023", "BookingService.create", "Phòng có allocation overlap",
                "MSG15 và không tạo allocation mới", "Abnormal", () -> {
                    Fixture f = fresh();
                    when(f.repository().hasRoomOverlap(eq(ROOM_ID), any(), any(), eq(null))).thenReturn(true);
                    TestResult result = expectFailure(() -> f.service().create(identity(), validCreate()), 409,
                            "Phòng DEMO-MB-01 đã có booking trùng với thời gian sử dụng đã chọn. Vui lòng chọn phòng hoặc thời gian khác.");
                    verify(f.repository(), never()).insertAllocation(any(), any(), any(), any(), any(), any());
                    return result;
                }),
            t("BKM-024", "BookingPricingService.depositFor", "Quoted amount=101 VND",
                "30% làm tròn lên bằng 31 VND", "Boundary", () -> {
                    BigDecimal actual = BookingPricingService.depositFor(new BigDecimal("101"));
                    return check(actual.compareTo(new BigDecimal("31")) == 0, "deposit=" + actual);
                }),
            t("BKM-025", "BookingService.create", "depositAmount khác 30% quote",
                "Từ chối và không receive deposit", "Abnormal", () -> {
                    Fixture f = fresh();
                    BookingDtos.CreateBookingRequest base = validCreate();
                    BookingDtos.CreateBookingRequest request = createRequest(base.plannedStart(), base.plannedEnd(),
                            "HOURLY", null, base.contactName(), base.contactPhone(), 2, null, new BigDecimal("100000"));
                    TestResult result = expectFailureStatus(() -> f.service().create(identity(), request), 400);
                    verify(f.repository(), never()).receiveDeposit(any(), any(), any(), any(), anyString(), any());
                    return result;
                }),
            t("BKM-026", "BookingPricingService.quote", "Giá VAT-inclusive 180000/giờ trong 2 giờ",
                "Quote=360000, không cộng VAT lần hai", "Normal", () -> {
                    BookingRepository repository = mock(BookingRepository.class);
                    when(repository.pricingRules(eq(ROOM_TYPE_ID), eq(BRANCH_ID), any(), any()))
                            .thenReturn(List.of(pricingRule(new BigDecimal("180000"))));
                    Instant start = futureMinutes(120).truncatedTo(java.time.temporal.ChronoUnit.HOURS);
                    BookingDtos.PriceQuote actual = new BookingPricingService(repository).quote(ROOM_TYPE_ID,
                            BRANCH_ID, BRANCH.timezone(), start, start.plusSeconds(7200), "HOURLY", null);
                    return check(actual.expectedAmount().compareTo(new BigDecimal("360000")) == 0,
                            "quote=" + actual.expectedAmount());
                }),
            t("BKM-027", "BookingService.create", "Receptionist không có active branch assignment",
                "MSG24 và không ghi dữ liệu", "Abnormal", () -> {
                    Fixture f = fresh();
                    when(f.repository().branchScope(ACTOR_ID)).thenReturn(null);
                    TestResult result = expectFailure(() -> f.service().create(identityWithoutBranch(), validCreate()),
                            403, "Bạn không có quyền tạo đối với booking.");
                    verify(f.repository(), never()).insertBooking(any(), anyString(), anyString(), anyString(),
                            any(Integer.class), anyString(), any(), any(), any(Integer.class), any(), any());
                    return result;
                })
        );
    }

    private static ManualTest t(String id, String source, String input, String expected,
            String type, CheckedAction action) {
        String method = id.equals("BKM-024") ? "Black-box" : "White-box";
        return test(id, UC, source, PRE, input, expected, type,
                "Nguồn: UC-34_Create_Booking.docx; Production: " + source
                        + ". Phương pháp kiểm thử: " + method + ".", action);
    }
}
