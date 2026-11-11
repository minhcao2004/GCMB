/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 29/10/2026
 * Description: Manual unit cases for UC-36 Update Booking.
 */
package com.gcmb.backend.manual.bookingmanagement;

import static com.gcmb.backend.manual.bookingmanagement.BookingManagementManualRunner.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gcmb.backend.booking.dto.BookingDtos;
import com.gcmb.backend.booking.exception.BookingFailure;
import com.gcmb.backend.booking.repository.BookingRepository.AllocationRow;
import com.gcmb.backend.booking.repository.BookingRepository.BookingRow;
import com.gcmb.backend.booking.repository.BookingRepository.RoomRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

final class UpdateBookingManualCases {
    private static final String UC = "UC-36 Update Booking";
    private static final String PRE = "Receptionist uc3437.demo đã đăng nhập tại DEMO-UC3437 active; booking DEMO-UC3437-EDIT đang CONFIRMED, có allocation RESERVED và snapshot giá đã chốt.";

    private UpdateBookingManualCases() {
    }

    static List<ManualTest> cases() {
        return List.of(
            t("BKM-036", "BookingService.update", "Chỉ đổi name, phone và note",
                "Cập nhật contact; giữ phòng, thời gian, allocation và giá", "Normal", () -> {
                    Fixture f = fresh();
                    BookingDtos.UpdateBookingRequest request = updateRequest(null, null, null, null, null,
                            "Khách đã cập nhật", "0900009999", "Ghi chú mới");
                    BookingDtos.BookingDetail actual = f.service().update(identity(), BOOKING_ID, request);
                    verify(f.repository()).updateBooking(BOOKING_ID, "Khách đã cập nhật", "0900009999",
                            f.booking().plannedStart(), f.booking().plannedEnd(), "Ghi chú mới");
                    verify(f.repository(), never()).releaseAllocation(any(), anyString());
                    return check(actual != null, "Contact updated without allocation replacement");
                }),
            t("BKM-037", "BookingService.update", "Đổi phòng và thời gian hợp lệ",
                "Recheck availability, release allocation cũ và insert allocation mới", "Normal", () -> {
                    Fixture f = fresh();
                    Instant start = futureMinutes(300), end = start.plusSeconds(7200);
                    RoomRow target = room(OTHER_ROOM_ID, ROOM_TYPE_ID, 4, "AVAILABLE");
                    when(f.repository().lockAvailableRoom(OTHER_ROOM_ID, BRANCH_ID)).thenReturn(target);
                    BookingDtos.UpdateBookingRequest request = updateRequest(OTHER_ROOM_ID, start, end,
                            "HOURLY", null, null, null, null);
                    BookingDtos.BookingDetail actual = f.service().update(identity(), BOOKING_ID, request);
                    verify(f.repository()).releaseAllocation(ALLOCATION_ID, "Thay đổi lịch đặt");
                    verify(f.repository()).hasRoomOverlap(OTHER_ROOM_ID, start, end, BOOKING_ID);
                    verify(f.repository()).insertAllocation(BRANCH_ID, BOOKING_ID, OTHER_ROOM_ID, start, end, ACTOR_ID);
                    return check(actual != null, "Allocation replaced after availability check");
                }),
            t("BKM-038", "BookingService.update", "Booking CANCELLED hoặc IN_HOUSE",
                "Không thể sửa thông tin đặt phòng vì booking đã bị hủy hoặc khách đã nhận phòng.; giữ nguyên booking", "Abnormal", () -> {
                    TestResult[] results = new TestResult[2];
                    String[] statuses = {"CANCELLED", "IN_HOUSE"};
                    for (int i = 0; i < statuses.length; i++) {
                        Fixture f = fresh();
                        BookingRow row = booking(statuses[i], futureMinutes(180), futureMinutes(300));
                        when(f.repository().booking(BOOKING_ID, BRANCH_ID)).thenReturn(row);
                        results[i] = expectFailure(() -> f.service().update(identity(), BOOKING_ID,
                                updateRequest(null, null, null, null, null, "Khách", null, null)), 409,
                                "Không thể sửa thông tin đặt phòng vì booking đã bị hủy hoặc khách đã nhận phòng.");
                    }
                    return combine(results);
                }),
            t("BKM-039", "BookingService.update", "Booking CONFIRMED nhưng start đã qua",
                "Thời gian bắt đầu của booking đặt trước không được ở quá khứ. Vui lòng chọn lại thời gian.; giữ nguyên booking", "Abnormal", () -> {
                    Fixture f = fresh();
                    Instant start = Instant.now().minusSeconds(300);
                    when(f.repository().booking(BOOKING_ID, BRANCH_ID))
                            .thenReturn(booking("CONFIRMED", start, start.plusSeconds(7200)));
                    return expectFailure(() -> f.service().update(identity(), BOOKING_ID,
                            updateRequest(null, null, null, null, null, "Khách", null, null)), 409,
                            "Thời gian bắt đầu của booking đặt trước không được ở quá khứ. Vui lòng chọn lại thời gian.");
                }),
            t("BKM-040", "BookingService.update", "End bằng hoặc trước start mới",
                "Thời gian kết thúc phải sau thời gian bắt đầu.; không thay allocation", "Abnormal", () -> {
                    Fixture f = fresh();
                    Instant start = futureMinutes(300);
                    TestResult result = expectFailure(() -> f.service().update(identity(), BOOKING_ID,
                            updateRequest(null, start, start, null, null, null, null, null)), 400,
                            "Thời gian kết thúc phải sau thời gian bắt đầu.");
                    verify(f.repository(), never()).releaseAllocation(any(), anyString());
                    return result;
                }),
            t("BKM-041", "BookingService.update", "Chuyển sang phòng maintenance",
                "Phòng DEMO-MB-06 đang bảo trì và chưa thể sử dụng. Vui lòng chọn phòng khác.; giữ allocation gốc", "Abnormal", () -> {
                    Fixture f = fresh();
                    when(f.repository().lockAvailableRoom(MAINTENANCE_ROOM_ID, BRANCH_ID)).thenReturn(null);
                    when(f.repository().lockRoomForValidation(MAINTENANCE_ROOM_ID, BRANCH_ID))
                            .thenReturn(new RoomRow(MAINTENANCE_ROOM_ID, "DEMO-MB-06", "Music Box demo 06", 4,
                                    "UNDER_MAINTENANCE", ROOM_TYPE_ID, "Music Box demo"));
                    Instant start = futureMinutes(300), end = start.plusSeconds(7200);
                    TestResult result = expectFailure(() -> f.service().update(identity(), BOOKING_ID,
                            updateRequest(MAINTENANCE_ROOM_ID, start, end, "HOURLY", null,
                                    null, null, null)), 409,
                            "Phòng DEMO-MB-06 đang bảo trì và chưa thể sử dụng. Vui lòng chọn phòng khác.");
                    verify(f.repository(), never()).releaseAllocation(any(), anyString());
                    return result;
                }),
            t("BKM-042", "BookingService.update", "Phòng hoặc thời gian mới bị overlap",
                "Phòng DEMO-ROOM đã có booking trùng với thời gian sử dụng đã chọn. Vui lòng chọn phòng hoặc thời gian khác.; không insert allocation mới", "Abnormal", () -> {
                    Fixture f = fresh();
                    Instant start = futureMinutes(300), end = start.plusSeconds(7200);
                    when(f.repository().lockAvailableRoom(OTHER_ROOM_ID, BRANCH_ID))
                            .thenReturn(room(OTHER_ROOM_ID, ROOM_TYPE_ID, 4, "AVAILABLE"));
                    when(f.repository().hasRoomOverlap(OTHER_ROOM_ID, start, end, BOOKING_ID)).thenReturn(true);
                    TestResult result = expectFailure(() -> f.service().update(identity(), BOOKING_ID,
                            updateRequest(OTHER_ROOM_ID, start, end, "HOURLY", null,
                                    null, null, null)), 409,
                            "Phòng DEMO-ROOM đã có booking trùng với thời gian sử dụng đã chọn. Vui lòng chọn phòng hoặc thời gian khác.");
                    verify(f.repository(), never()).insertAllocation(any(), any(), any(), any(), any(), any());
                    return result;
                }),
            t("BKM-043", "BookingService.update", "Phòng đích capacity=1, booking có 2 khách",
                "Phòng DEMO-ROOM chỉ phù hợp tối đa 1 khách. Vui lòng giảm số khách hoặc chọn phòng có sức chứa lớn hơn.; giữ phòng cũ", "Abnormal", () -> {
                    Fixture f = fresh();
                    Instant start = futureMinutes(300), end = start.plusSeconds(7200);
                    when(f.repository().lockAvailableRoom(OTHER_ROOM_ID, BRANCH_ID))
                            .thenReturn(room(OTHER_ROOM_ID, ROOM_TYPE_ID, 1, "AVAILABLE"));
                    return expectFailure(() -> f.service().update(identity(), BOOKING_ID,
                            updateRequest(OTHER_ROOM_ID, start, end, "HOURLY", null,
                                    null, null, null)), 400,
                            "Phòng DEMO-ROOM chỉ phù hợp tối đa 1 khách. Vui lòng giảm số khách hoặc chọn phòng có sức chứa lớn hơn.");
                }),
            t("BKM-044", "BookingService.update", "Combo booking đổi sang room type khác",
                "Combo Music Box demo 2 giờ không áp dụng cho hạng phòng hoặc giờ bắt đầu đã chọn. Vui lòng chọn combo phù hợp.; giữ booking", "Abnormal", () -> {
                    Fixture f = fresh();
                    when(f.repository().activeChargesForAllocation(ALLOCATION_ID)).thenReturn(List.of(comboCharge()));
                    when(f.repository().combo(COMBO_ID, BRANCH_ID)).thenReturn(combo());
                    Instant start = futureMinutes(300), end = start.plusSeconds(7200);
                    when(f.repository().lockAvailableRoom(OTHER_ROOM_ID, BRANCH_ID))
                            .thenReturn(room(OTHER_ROOM_ID, OTHER_ROOM_TYPE_ID, 8, "AVAILABLE"));
                    return expectFailure(() -> f.service().update(identity(), BOOKING_ID,
                            updateRequest(OTHER_ROOM_ID, start, end, "COMBO", COMBO_ID,
                                    null, null, null)), 400,
                            "Combo Music Box demo 2 giờ không áp dụng cho hạng phòng hoặc giờ bắt đầu đã chọn. Vui lòng chọn combo phù hợp.");
                }),
            t("BKM-045", "BookingService.update", "Tên mới 200 và 201 ký tự",
                "Tên khách 200 ký tự được lưu; 201 ký tự trả: Tên khách chỉ được chứa tối đa 200 ký tự.", "Boundary", () -> {
                    Fixture accepted = fresh();
                    BookingDtos.BookingDetail detail = accepted.service().update(identity(), BOOKING_ID,
                            updateRequest(null, null, null, null, null, "N".repeat(200), null, null));
                    TestResult a = check(detail != null, "200 characters accepted");
                    Fixture rejected = fresh();
                    TestResult b = expectFailure(() -> rejected.service().update(identity(), BOOKING_ID,
                            updateRequest(null, null, null, null, null, "N".repeat(201), null, null)), 400,
                            "Tên khách chỉ được chứa tối đa 200 ký tự.");
                    return combine(a, b);
                }),
            t("BKM-046", "BookingService.update", "Phone +84900003437 và phone sai format",
                "+84900003437 được nhận; phone sai trả: Số điện thoại chỉ được chứa chữ số và có thể có một dấu + ở đầu.", "Boundary", () -> {
                    Fixture plus = fresh();
                    TestResult a;
                    try {
                        BookingDtos.BookingDetail detail = plus.service().update(identity(), BOOKING_ID,
                                updateRequest(null, null, null, null, null, null, "+84900003437", null));
                        a = check(detail != null, "Leading plus accepted");
                    } catch (BookingFailure failure) {
                        a = check(false, "status=" + failure.status() + ", message=" + failure.getMessage());
                    }
                    Fixture invalid = fresh();
                    TestResult b = expectFailure(() -> invalid.service().update(identity(), BOOKING_ID,
                            updateRequest(null, null, null, null, null, null, "0900A03437", null)), 400,
                            "Số điện thoại chỉ được chứa chữ số và có thể có một dấu + ở đầu.");
                    return combine(a, b);
                }),
            t("BKM-047", "BookingService.update", "Note 2000 và 2001 ký tự",
                "Ghi chú 2000 ký tự được lưu; 2001 ký tự trả: Ghi chú chỉ được chứa tối đa 2000 ký tự.", "Boundary", () -> {
                    Fixture accepted = fresh();
                    BookingDtos.BookingDetail detail = accepted.service().update(identity(), BOOKING_ID,
                            updateRequest(null, null, null, null, null, null, null, "N".repeat(2000)));
                    TestResult a = check(detail != null, "2000 characters accepted");
                    Fixture rejected = fresh();
                    TestResult b = expectFailure(() -> rejected.service().update(identity(), BOOKING_ID,
                            updateRequest(null, null, null, null, null, null, null, "N".repeat(2001))), 400,
                            "Ghi chú chỉ được chứa tối đa 2000 ký tự.");
                    return combine(a, b);
                }),
            t("BKM-048", "BookingService.update", "Catalog đổi nhưng chỉ sửa contact",
                "Giữ giá và package terms đã chốt", "Normal", () -> {
                    Fixture f = fresh();
                    when(f.repository().activeChargeTotal(BOOKING_ID)).thenReturn(new BigDecimal("360000"));
                    BookingDtos.BookingDetail actual = f.service().update(identity(), BOOKING_ID,
                            updateRequest(null, null, null, null, null, "Khách mới", null, null));
                    verify(f.pricing(), never()).quote(any(), any(), any(), any(), any(), any(), any());
                    verify(f.repository(), never()).insertCharge(any(), any(), any());
                    return check(actual.booking().expectedAmount().compareTo(new BigDecimal("360000")) == 0,
                            "expectedAmount=" + actual.booking().expectedAmount());
                }),
            t("BKM-049", "BookingService.update", "Booking ID ngoài branch assignment",
                "Bạn không có quyền sửa booking đối với booking.; không cập nhật dữ liệu", "Abnormal", () -> {
                    Fixture f = fresh();
                    when(f.repository().lockBookingId(BOOKING_ID, BRANCH_ID)).thenReturn(null);
                    when(f.repository().bookingExists(BOOKING_ID)).thenReturn(true);
                    TestResult result = expectFailure(() -> f.service().update(identity(), BOOKING_ID,
                            updateRequest(null, null, null, null, null, "Khách", null, null)), 403,
                            "Bạn không có quyền sửa booking đối với booking.");
                    verify(f.repository(), never()).updateBooking(any(), any(), any(), any(), any(), any());
                    return result;
                }),
            t("BKM-068", "BookingService.update", "Booking tồn tại nhưng cơ sở DEMO-UC3437 đang SUSPENDED",
                "Cơ sở đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi.; booking giữ nguyên", "Abnormal", () -> {
                    Fixture f = fresh();
                    when(f.repository().lockBookingId(BOOKING_ID, BRANCH_ID)).thenThrow(new BookingFailure(403, "form",
                            "Cơ sở đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi."));
                    TestResult result = expectFailure(() -> f.service().update(identity(), BOOKING_ID,
                            updateRequest(null, null, null, null, null, "Khách", null, null)), 403,
                            "Cơ sở đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi.");
                    verify(f.repository(), never()).updateBooking(any(), any(), any(), any(), any(), any());
                    return result;
                }),
            t("BKM-069", "BookingService.update", "Đổi thời gian làm thay đổi báo giá khi còn yêu cầu cọc PENDING",
                "Hủy yêu cầu thanh toán cọc PENDING trước khi ghi giá mới", "Normal", () -> {
                    Fixture f = fresh();
                    Instant start = futureMinutes(300), end = start.plusSeconds(7200);
                    f.service().update(identity(), BOOKING_ID,
                            updateRequest(null, start, end, "HOURLY", null, null, null, null));
                    verify(f.repository()).cancelPendingDepositPaymentRequests(BOOKING_ID);
                    return check(true, "pending deposit payment requests cancelled");
                }),
            t("BKM-070", "BookingService.update", "Đổi từ giá giờ sang combo có 2 đồ uống và 1 đồ ăn tặng kèm",
                "Ghi lại snapshot quà combo: đồ uống=2, đồ ăn=1", "Normal", () -> {
                    Fixture f = fresh();
                    when(f.repository().combo(COMBO_ID, BRANCH_ID)).thenReturn(combo());
                    when(f.pricing().quote(eq(ROOM_TYPE_ID), eq(BRANCH_ID), eq(BRANCH.timezone()),
                            any(), any(), eq("COMBO"), eq(COMBO_ID))).thenReturn(comboQuote());
                    f.service().update(identity(), BOOKING_ID,
                            updateRequest(null, null, null, "COMBO", COMBO_ID, null, null, null));
                    verify(f.repository()).setComboGiftQuantities(BOOKING_ID, 2, 1);
                    return check(true, "comboIncludedFnbQuantity=2, comboIncludedFoodQuantity=1");
                })
        );
    }

    private static ManualTest t(String id, String source, String input, String expected,
            String type, CheckedAction action) {
        return test(id, UC, source, PRE, input, expected, type,
                "Nguồn: UC-36_Update_Booking.docx; Production: " + source
                        + ". Phương pháp kiểm thử: White-box.", action);
    }
}
