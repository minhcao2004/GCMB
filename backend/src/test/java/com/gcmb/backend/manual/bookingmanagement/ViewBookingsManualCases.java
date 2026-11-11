/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 29/10/2026
 * Description: Manual unit cases for UC-35 View Bookings.
 */
package com.gcmb.backend.manual.bookingmanagement;

import static com.gcmb.backend.manual.bookingmanagement.BookingManagementManualRunner.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gcmb.backend.booking.dto.BookingDtos;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

final class ViewBookingsManualCases {
    private static final String UC = "UC-35 View Bookings";
    private static final String PRE = "Receptionist uc3437.demo đã đăng nhập, được phân công tại DEMO-UC3437 active; booking demo UC34-37 đã tồn tại.";

    private ViewBookingsManualCases() {
    }

    static List<ManualTest> cases() {
        return List.of(
            t("BKM-028", "BookingService.list", "Không filter, page=0, size=20",
                "Trả đúng page trong branch và không ghi dữ liệu", "Normal", () -> {
                    Fixture f = fresh();
                    BookingDtos.BookingSummary summary = summary();
                    when(f.repository().countBookings(BRANCH_ID, null, null, null, BRANCH.timezone(), null, null)).thenReturn(1L);
                    when(f.repository().findBookings(BRANCH_ID, null, null, null, BRANCH.timezone(), null, null, 0, 20))
                            .thenReturn(List.of(summary));
                    BookingDtos.BookingPage actual = f.service().list(identity(), null, null, null, null, null, 0, 20);
                    verify(f.repository(), never()).updateBooking(any(), any(), any(), any(), any(), any());
                    return check(actual.totalElements() == 1 && actual.items().equals(List.of(summary)), actual.toString());
                }),
            t("BKM-029", "BookingService.list", "Status/date/search/hasDeposit hợp lệ",
                "Trả đúng kết quả đồng thời thỏa filter", "Normal", () -> {
                    Fixture f = fresh();
                    LocalDate from = LocalDate.now(), to = from.plusDays(1);
                    when(f.repository().countBookings(BRANCH_ID, "CONFIRMED", from, to,
                            BRANCH.timezone(), "Nguyễn", true)).thenReturn(1L);
                    when(f.repository().findBookings(BRANCH_ID, "CONFIRMED", from, to,
                            BRANCH.timezone(), "Nguyễn", true, 0, 10)).thenReturn(List.of(summary()));
                    BookingDtos.BookingPage actual = f.service().list(identity(), " confirmed ", from, to,
                            " Nguyễn ", true, 0, 10);
                    verify(f.repository()).findBookings(BRANCH_ID, "CONFIRMED", from, to,
                            BRANCH.timezone(), "Nguyễn", true, 0, 10);
                    return check(actual.totalElements() == 1 && actual.items().size() == 1, actual.toString());
                }),
            t("BKM-030", "BookingService.list", "Filter hợp lệ nhưng không có kết quả",
                "Page rỗng, total=0, không mutation", "Normal", () -> {
                    Fixture f = fresh();
                    when(f.repository().countBookings(BRANCH_ID, null, null, null, BRANCH.timezone(), "không-có", null))
                            .thenReturn(0L);
                    when(f.repository().findBookings(BRANCH_ID, null, null, null, BRANCH.timezone(), "không-có", null, 0, 20))
                            .thenReturn(List.of());
                    BookingDtos.BookingPage actual = f.service().list(identity(), null, null, null,
                            "không-có", null, 0, 20);
                    return check(actual.items().isEmpty() && actual.totalElements() == 0 && actual.totalPages() == 0,
                            actual.toString());
                }),
            t("BKM-031", "BookingService.list", "status=UNKNOWN",
                "Từ chối status sai trước khi query", "Abnormal", () -> {
                    Fixture f = fresh();
                    TestResult result = expectFailure(() -> f.service().list(identity(), "UNKNOWN",
                            null, null, null, null, 0, 20), 400, "Trạng thái booking không hợp lệ");
                    verify(f.repository(), never()).findBookings(any(), any(), any(), any(), any(), any(), any(),
                            org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
                    return result;
                }),
            t("BKM-032", "BookingService.detail", "Booking thuộc DEMO-UC3437",
                "Trả summary, allocation, charge và deposit; không mutation", "Normal", () -> {
                    Fixture f = fresh();
                    when(f.repository().charges(BOOKING_ID)).thenReturn(List.of(hourlyCharge()));
                    BookingDtos.BookingDetail actual = f.service().detail(identity(), BOOKING_ID);
                    return check(actual.branchId().equals(BRANCH_ID)
                                    && actual.allocations().size() == 1
                                    && actual.charges().size() == 1
                                    && actual.booking().expectedAmount().compareTo(new BigDecimal("360000")) == 0,
                            "branch=" + actual.branchId() + ", allocations=" + actual.allocations().size()
                                    + ", charges=" + actual.charges().size());
                }),
            t("BKM-033", "BookingService.detail", "Booking ID ngoài branch assignment",
                "Bạn không có quyền truy cập đối với booking.; không trả dữ liệu", "Abnormal", () -> {
                    Fixture f = fresh();
                    when(f.repository().booking(BOOKING_ID, BRANCH_ID)).thenReturn(null);
                    when(f.repository().bookingExists(BOOKING_ID)).thenReturn(true);
                    return expectFailure(() -> f.service().detail(identity(), BOOKING_ID), 403,
                            "Bạn không có quyền truy cập đối với booking.");
                }),
            t("BKM-034", "BookingService.list/detail", "Receptionist không có active assignment",
                "Bạn không có quyền truy cập đối với booking.; không trả booking", "Abnormal", () -> {
                    Fixture listFixture = fresh();
                    when(listFixture.repository().branchScope(ACTOR_ID)).thenReturn(null);
                    TestResult a = expectFailure(() -> listFixture.service().list(identityWithoutBranch(),
                            null, null, null, null, null, 0, 20), 403,
                            "Bạn không có quyền truy cập đối với booking.");
                    Fixture detailFixture = fresh();
                    when(detailFixture.repository().branchScope(ACTOR_ID)).thenReturn(null);
                    TestResult b = expectFailure(() -> detailFixture.service().detail(identityWithoutBranch(), BOOKING_ID),
                            403, "Bạn không có quyền truy cập đối với booking.");
                    return combine(a, b);
                }),
            t("BKM-035", "BookingService.detail", "Catalog đã đổi, booking có snapshot giá cũ",
                "Trả đúng giá/snapshot đã chốt", "Normal", () -> {
                    Fixture f = fresh();
                    when(f.repository().charges(BOOKING_ID)).thenReturn(List.of(hourlyCharge()));
                    when(f.repository().activeChargeTotal(BOOKING_ID)).thenReturn(new BigDecimal("360000"));
                    BookingDtos.BookingDetail actual = f.service().detail(identity(), BOOKING_ID);
                    BookingDtos.ChargeView charge = actual.charges().get(0);
                    boolean ok = actual.booking().expectedAmount().compareTo(new BigDecimal("360000")) == 0
                            && charge.amount().compareTo(new BigDecimal("360000")) == 0
                            && "HOURLY".equals(charge.pricingSnapshot().get("pricingMode"));
                    return check(ok, "expectedAmount=" + actual.booking().expectedAmount()
                            + ", snapshot=" + charge.pricingSnapshot());
                }),
            t("BKM-067", "BookingService.list/detail", "Cơ sở DEMO-UC3437 đang SUSPENDED",
                "Vẫn xem được danh sách và chi tiết; không thực hiện mutation", "Normal", () -> {
                    Fixture f = fresh();
                    when(f.repository().countBookings(BRANCH_ID, null, null, null, BRANCH.timezone(), null, null)).thenReturn(1L);
                    when(f.repository().findBookings(BRANCH_ID, null, null, null, BRANCH.timezone(), null, null, 0, 20))
                            .thenReturn(List.of(summary()));
                    BookingDtos.BookingPage page = f.service().list(identity(), null, null, null, null, null, 0, 20);
                    BookingDtos.BookingDetail detail = f.service().detail(identity(), BOOKING_ID);
                    verify(f.repository(), never()).requireActiveBranch(BRANCH_ID);
                    return check(page.totalElements() == 1 && detail != null, "list=1, detail available");
                })
        );
    }

    private static BookingDtos.BookingSummary summary() {
        return new BookingDtos.BookingSummary(BOOKING_ID, "DEMO-UC3437-EDIT", "Nguyễn Khách Cập Nhật",
                "0900000101", "PHONE", futureMinutes(180), futureMinutes(300), null, "CONFIRMED",
                ROOM_ID, "DEMO-MB-01", "Music Box demo 01", "Music Box demo",
                new BigDecimal("360000"), BigDecimal.ZERO, BigDecimal.ZERO, 2, "AVAILABLE");
    }

    private static ManualTest t(String id, String source, String input, String expected,
            String type, CheckedAction action) {
        return test(id, UC, source, PRE, input, expected, type,
                "Nguồn: UC-35_View_Bookings.docx; Production: " + source
                        + ". Phương pháp kiểm thử: White-box.", action);
    }
}
