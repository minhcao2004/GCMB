/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 29/10/2026
 * Description: Manual unit cases for UC-37 Cancel Booking.
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class CancelBookingManualCases {
    private static final String UC = "UC-37 Cancel Booking";
    private static final String PRE = "Receptionist uc3437.demo đã đăng nhập tại DEMO-UC3437 active; booking demo thuộc đúng branch và có allocation RESERVED.";

    private CancelBookingManualCases() {
    }

    static List<ManualTest> cases() {
        return List.of(
            t("BKM-050", "BookingService.cancel", "Booking chưa dùng, deposit balance=0, reason hợp lệ",
                "CANCELLED, release phòng, NO_DEPOSIT, không refund request", "Normal", () -> {
                    Fixture f = cancelFixture(futureMinutes(360), BigDecimal.ZERO);
                    BookingDtos.CancellationResult actual = f.service().cancel(identity(), BOOKING_ID,
                            new BookingDtos.CancelBookingRequest("Khách thay đổi kế hoạch", null, null, null));
                    verify(f.repository()).releaseAllocation(ALLOCATION_ID, "Booking đã hủy");
                    verify(f.repository()).cancelBooking(eq(BOOKING_ID), any(Instant.class), eq("Khách thay đổi kế hoạch"));
                    verify(f.repository(), never()).createDepositRefundRequest(any(), any(), any(), any(), anyString(), anyString(), anyString(), anyString(), any());
                    return check("NO_DEPOSIT".equals(actual.depositOutcome()) && actual.refundRequestId() == null,
                            "outcome=" + actual.depositOutcome() + ", refund=" + actual.refundRequestId());
                }),
            t("BKM-051", "BookingService.cancel", "Booking còn trên 40 phút, deposit=100000, đủ thông tin hoàn",
                "REFUND_ELIGIBLE; tạo refund request nhưng chưa trả tiền", "Normal", () -> {
                    Fixture f = cancelFixture(futureMinutes(360), new BigDecimal("100000"));
                    BookingDtos.CancellationResult actual = f.service().cancel(identity(), BOOKING_ID,
                            new BookingDtos.CancelBookingRequest("Khách hủy sớm", "Trần Khách Hoàn Cọc",
                                    "0123456789", "Demo Bank"));
                    verify(f.repository()).createDepositRefundRequest(BRANCH_ID, BOOKING_ID, DEPOSIT_ID,
                            new BigDecimal("100000"), "Khách hủy sớm", "Trần Khách Hoàn Cọc",
                            "Demo Bank", "0123456789", ACTOR_ID);
                    verify(f.repository(), never()).forfeitDeposit(any(), any(), any(), anyString(), anyString());
                    return check("REFUND_ELIGIBLE".equals(actual.depositOutcome())
                                    && REFUND_ID.equals(actual.refundRequestId()),
                            "outcome=" + actual.depositOutcome() + ", refund=" + actual.refundRequestId());
                }),
            t("BKM-052", "BookingService.cancel", "Booking còn dưới 40 phút, deposit=100000",
                "NON_REFUNDABLE; forfeit cọc và không refund request", "Normal", () -> {
                    Fixture f = cancelFixture(futureMinutes(20), new BigDecimal("100000"));
                    BookingDtos.CancellationResult actual = f.service().cancel(identity(), BOOKING_ID,
                            new BookingDtos.CancelBookingRequest("Khách hủy gần giờ", null, null, null));
                    verify(f.repository()).forfeitDeposit(eq(BOOKING_ID), eq(ACTOR_ID),
                            eq(new BigDecimal("100000")), anyString(), anyString());
                    verify(f.repository(), never()).createDepositRefundRequest(any(), any(), any(), any(), anyString(), anyString(), anyString(), anyString(), any());
                    return check("NON_REFUNDABLE".equals(actual.depositOutcome()),
                            "outcome=" + actual.depositOutcome());
                }),
            t("BKM-053", "BookingService.cancel", "Reason null, rỗng hoặc chỉ khoảng trắng",
                "Vui lòng bổ sung lý do hủy.; không thay đổi booking, phòng, cọc hoặc tồn quà combo", "Abnormal", () -> {
                    String[] reasons = {null, "", "   "};
                    List<TestResult> results = new ArrayList<>();
                    for (String reason : reasons) {
                        Fixture f = cancelFixture(futureMinutes(360), BigDecimal.ZERO);
                        TestResult result = expectFailure(() -> f.service().cancel(identity(), BOOKING_ID,
                                new BookingDtos.CancelBookingRequest(reason, null, null, null)), 400,
                                "Vui lòng bổ sung lý do hủy.");
                        verify(f.repository(), never()).releaseAllocation(any(), anyString());
                        verify(f.foodOrders(), never()).returnComboGiftStock(any(), any());
                        results.add(result);
                    }
                    return combine(results.toArray(TestResult[]::new));
                }),
            t("BKM-054", "BookingService.cancel", "Reason 1000 và 1001 ký tự",
                "Lý do hủy 1000 ký tự được nhận; 1001 ký tự trả: Lý do hủy chỉ được chứa tối đa 1000 ký tự.", "Boundary", () -> {
                    Fixture accepted = cancelFixture(futureMinutes(360), BigDecimal.ZERO);
                    BookingDtos.CancellationResult detail = accepted.service().cancel(identity(), BOOKING_ID,
                            new BookingDtos.CancelBookingRequest("R".repeat(1000), null, null, null));
                    TestResult a = check(detail != null, "1000 characters accepted");
                    Fixture rejected = cancelFixture(futureMinutes(360), BigDecimal.ZERO);
                    TestResult b = expectFailure(() -> rejected.service().cancel(identity(), BOOKING_ID,
                            new BookingDtos.CancelBookingRequest("R".repeat(1001), null, null, null)), 400,
                            "Lý do hủy chỉ được chứa tối đa 1000 ký tự.");
                    return combine(a, b);
                }),
            t("BKM-055", "BookingService.cancel", "Refund eligible nhưng thiếu từng trường ngân hàng",
                "Lần lượt trả: Vui lòng bổ sung chủ tài khoản.; Vui lòng bổ sung số tài khoản.; Vui lòng bổ sung ngân hàng.; không thay đổi dữ liệu", "Abnormal", () -> {
                    BookingDtos.CancelBookingRequest[] requests = {
                        new BookingDtos.CancelBookingRequest("Hủy sớm", null, "0123456789", "Demo Bank"),
                        new BookingDtos.CancelBookingRequest("Hủy sớm", "Khách Demo", null, "Demo Bank"),
                        new BookingDtos.CancelBookingRequest("Hủy sớm", "Khách Demo", "0123456789", null)
                    };
                    String[] messages = {
                        "Vui lòng bổ sung chủ tài khoản.",
                        "Vui lòng bổ sung số tài khoản.",
                        "Vui lòng bổ sung ngân hàng."
                    };
                    TestResult[] results = new TestResult[3];
                    for (int i = 0; i < requests.length; i++) {
                        Fixture f = cancelFixture(futureMinutes(360), new BigDecimal("100000"));
                        BookingDtos.CancelBookingRequest request = requests[i];
                        results[i] = expectFailure(() -> f.service().cancel(identity(), BOOKING_ID, request),
                                400, messages[i]);
                        verify(f.repository(), never()).createDepositRefundRequest(any(), any(), any(), any(),
                                anyString(), anyString(), anyString(), anyString(), any());
                        verify(f.foodOrders(), never()).returnComboGiftStock(any(), any());
                    }
                    return combine(results);
                }),
            t("BKM-056", "BookingService.cancel", "Account-holder name 200 và 201 ký tự",
                "Chủ tài khoản 200 ký tự được nhận; 201 ký tự trả: Chủ tài khoản chỉ được chứa tối đa 200 ký tự.", "Boundary", () -> {
                    Fixture accepted = cancelFixture(futureMinutes(360), new BigDecimal("100000"));
                    BookingDtos.CancellationResult actual = accepted.service().cancel(identity(), BOOKING_ID,
                            new BookingDtos.CancelBookingRequest("Hủy sớm", "N".repeat(200),
                                    "0123456789", "Demo Bank"));
                    TestResult a = check(actual != null, "200 characters accepted");
                    Fixture rejected = cancelFixture(futureMinutes(360), new BigDecimal("100000"));
                    TestResult b = expectFailure(() -> rejected.service().cancel(identity(), BOOKING_ID,
                            new BookingDtos.CancelBookingRequest("Hủy sớm", "N".repeat(201),
                                    "0123456789", "Demo Bank")), 400,
                            "Chủ tài khoản chỉ được chứa tối đa 200 ký tự.");
                    return combine(a, b);
                }),
            t("BKM-057", "BookingService.cancel", "Booking IN_HOUSE, COMPLETED hoặc CANCELLED",
                "IN_HOUSE/COMPLETED trả: Không thể hủy booking này vì khách đã nhận phòng hoặc lượt sử dụng đã kết thúc.; CANCELLED trả: Booking hiện tại không thể hủy; không release phòng/cọc", "Abnormal", () -> {
                    String[] statuses = {"IN_HOUSE", "COMPLETED", "CANCELLED"};
                    String[] messages = {
                        "Không thể hủy booking này vì khách đã nhận phòng hoặc lượt sử dụng đã kết thúc.",
                        "Không thể hủy booking này vì khách đã nhận phòng hoặc lượt sử dụng đã kết thúc.",
                        "Booking hiện tại không thể hủy"
                    };
                    TestResult[] results = new TestResult[statuses.length];
                    for (int i = 0; i < statuses.length; i++) {
                        Fixture f = fresh();
                        Instant start = futureMinutes(360);
                        when(f.repository().booking(BOOKING_ID, BRANCH_ID))
                                .thenReturn(booking(statuses[i], start, start.plusSeconds(7200)));
                        results[i] = expectFailure(() -> f.service().cancel(identity(), BOOKING_ID,
                                new BookingDtos.CancelBookingRequest("Lý do hợp lệ", null, null, null)), 409,
                                messages[i]);
                        verify(f.repository(), never()).releaseAllocation(any(), anyString());
                    }
                    return combine(results);
                }),
            t("BKM-058", "BookingService.cancel", "Booking đã đến hoặc qua giờ start",
                "Không thể hủy booking này vì khách đã nhận phòng hoặc lượt sử dụng đã kết thúc.; chuyển sang luồng no-show/check-in", "Abnormal", () -> {
                    Fixture f = fresh();
                    Instant start = Instant.now().minusSeconds(300);
                    when(f.repository().booking(BOOKING_ID, BRANCH_ID))
                            .thenReturn(booking("CONFIRMED", start, start.plusSeconds(7200)));
                    return expectFailure(() -> f.service().cancel(identity(), BOOKING_ID,
                            new BookingDtos.CancelBookingRequest("Khách không đến", null, null, null)), 409,
                            "Không thể hủy booking này vì khách đã nhận phòng hoặc lượt sử dụng đã kết thúc.");
                }),
            t("BKM-059", "BookingService.markNoShow", "Booking đã đến giờ, không có cọc",
                "NO_SHOW, release phòng, NO_DEPOSIT", "Normal", () -> {
                    Fixture f = noShowFixture(BigDecimal.ZERO);
                    BookingDtos.NoShowResult actual = f.service().markNoShow(identity(), BOOKING_ID,
                            new BookingDtos.NoShowRequest("Khách không đến nhận phòng"));
                    verify(f.repository()).releaseAllocation(ALLOCATION_ID, "Booking no-show");
                    verify(f.repository()).markNoShow(BOOKING_ID);
                    verify(f.repository(), never()).forfeitDeposit(any(), any(), any(), anyString(), anyString());
                    return check("NO_DEPOSIT".equals(actual.depositOutcome()),
                            "outcome=" + actual.depositOutcome());
                }),
            t("BKM-060", "BookingService.markNoShow", "Booking đã đến giờ, deposit=100000",
                "NO_SHOW, release phòng, NON_REFUNDABLE và forfeit", "Normal", () -> {
                    Fixture f = noShowFixture(new BigDecimal("100000"));
                    BookingDtos.NoShowResult actual = f.service().markNoShow(identity(), BOOKING_ID,
                            new BookingDtos.NoShowRequest("Khách không đến nhận phòng"));
                    verify(f.repository()).forfeitDeposit(eq(BOOKING_ID), eq(ACTOR_ID),
                            eq(new BigDecimal("100000")), anyString(), anyString());
                    return check("NON_REFUNDABLE".equals(actual.depositOutcome()),
                            "outcome=" + actual.depositOutcome());
                }),
            t("BKM-061", "BookingService.markNoShow", "Booking chưa đến start",
                "Từ chối; booking/allocation giữ nguyên", "Abnormal", () -> {
                    Fixture f = fresh();
                    Instant start = futureMinutes(120);
                    when(f.repository().booking(BOOKING_ID, BRANCH_ID))
                            .thenReturn(booking("CONFIRMED", start, start.plusSeconds(7200)));
                    TestResult result = expectFailureStatus(() -> f.service().markNoShow(identity(), BOOKING_ID,
                            new BookingDtos.NoShowRequest("Khách chưa đến")), 409);
                    verify(f.repository(), never()).releaseAllocation(any(), anyString());
                    return result;
                }),
            t("BKM-062", "BookingService.markNoShow", "Reason thiếu và reason 1001 ký tự",
                "Lần lượt trả: Vui lòng bổ sung lý do no-show.; Lý do no-show chỉ được chứa tối đa 1000 ký tự.; không thay booking", "Boundary", () -> {
                    Fixture missing = noShowFixture(BigDecimal.ZERO);
                    TestResult a = expectFailure(() -> missing.service().markNoShow(identity(), BOOKING_ID,
                            new BookingDtos.NoShowRequest("   ")), 400,
                            "Vui lòng bổ sung lý do no-show.");
                    Fixture longReason = noShowFixture(BigDecimal.ZERO);
                    TestResult b = expectFailure(() -> longReason.service().markNoShow(identity(), BOOKING_ID,
                            new BookingDtos.NoShowRequest("R".repeat(1001))), 400,
                            "Lý do no-show chỉ được chứa tối đa 1000 ký tự.");
                    return combine(a, b);
                }),
            t("BKM-063", "BookingService.cancel/markNoShow", "Receptionist không có active assignment",
                "Bạn không có quyền truy cập đối với booking.; không mutation", "Abnormal", () -> {
                    Fixture cancel = fresh();
                    when(cancel.repository().branchScope(ACTOR_ID)).thenReturn(null);
                    TestResult a = expectFailure(() -> cancel.service().cancel(identityWithoutBranch(), BOOKING_ID,
                            new BookingDtos.CancelBookingRequest("Lý do hợp lệ", null, null, null)), 403,
                            "Bạn không có quyền truy cập đối với booking.");
                    Fixture noShow = fresh();
                    when(noShow.repository().branchScope(ACTOR_ID)).thenReturn(null);
                    TestResult b = expectFailure(() -> noShow.service().markNoShow(identityWithoutBranch(), BOOKING_ID,
                            new BookingDtos.NoShowRequest("Khách không đến")), 403,
                            "Bạn không có quyền truy cập đối với booking.");
                    return combine(a, b);
                }),
            t("BKM-071", "BookingService.cancel", "Booking tồn tại nhưng cơ sở DEMO-UC3437 đang SUSPENDED",
                "Cơ sở đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi.; booking giữ nguyên", "Abnormal", () -> {
                    Fixture f = fresh();
                    when(f.repository().lockBookingId(BOOKING_ID, BRANCH_ID)).thenThrow(
                            new com.gcmb.backend.booking.exception.BookingFailure(403, "form",
                                    "Cơ sở đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi."));
                    TestResult result = expectFailure(() -> f.service().cancel(identity(), BOOKING_ID,
                            new BookingDtos.CancelBookingRequest("Khách đổi kế hoạch", null, null, null)), 403,
                            "Cơ sở đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi.");
                    verify(f.repository(), never()).cancelBooking(any(), any(), anyString());
                    return result;
                }),
            t("BKM-072", "BookingService.cancel", "Booking combo chưa sử dụng, chưa nhận quà và không có cọc",
                "Hoàn tồn quà combo rồi hủy booking và giải phóng phòng", "Normal", () -> {
                    Fixture f = cancelFixture(futureMinutes(360), BigDecimal.ZERO);
                    f.service().cancel(identity(), BOOKING_ID,
                            new BookingDtos.CancelBookingRequest("Khách đổi kế hoạch", null, null, null));
                    verify(f.foodOrders()).returnComboGiftStock(identity(), BOOKING_ID);
                    verify(f.repository()).cancelBooking(eq(BOOKING_ID), any(Instant.class), eq("Khách đổi kế hoạch"));
                    return check(true, "combo gift stock returned before cancellation completed");
                })
        );
    }

    private static Fixture cancelFixture(Instant start, BigDecimal deposit) {
        Fixture f = fresh();
        var initial = booking("CONFIRMED", start, start.plusSeconds(7200));
        var cancelled = booking("CANCELLED", start, start.plusSeconds(7200));
        var allocation = allocation(ROOM_ID, ROOM_TYPE_ID, start, start.plusSeconds(7200), "RESERVED");
        when(f.repository().currentAllocationForUpdate(BOOKING_ID)).thenReturn(allocation);
        when(f.repository().booking(BOOKING_ID, BRANCH_ID)).thenReturn(initial, cancelled);
        when(f.repository().allocations(BOOKING_ID)).thenReturn(List.of(allocation));
        when(f.repository().depositBalance(BOOKING_ID)).thenReturn(deposit);
        when(f.repository().depositReceivedAmount(BOOKING_ID)).thenReturn(deposit);
        when(f.repository().activeChargeTotal(BOOKING_ID)).thenReturn(new BigDecimal("360000"));
        return f;
    }

    private static Fixture noShowFixture(BigDecimal deposit) {
        Fixture f = fresh();
        Instant start = Instant.now().minusSeconds(300);
        var initial = booking("CONFIRMED", start, start.plusSeconds(7200));
        var noShow = booking("NO_SHOW", start, start.plusSeconds(7200));
        var allocation = allocation(ROOM_ID, ROOM_TYPE_ID, start, start.plusSeconds(7200), "RESERVED");
        when(f.repository().currentAllocationForUpdate(BOOKING_ID)).thenReturn(allocation);
        when(f.repository().booking(BOOKING_ID, BRANCH_ID)).thenReturn(initial, noShow);
        when(f.repository().allocations(BOOKING_ID)).thenReturn(List.of(allocation));
        when(f.repository().depositBalance(BOOKING_ID)).thenReturn(deposit);
        when(f.repository().depositReceivedAmount(BOOKING_ID)).thenReturn(deposit);
        when(f.repository().activeChargeTotal(BOOKING_ID)).thenReturn(new BigDecimal("360000"));
        return f;
    }

    private static ManualTest t(String id, String source, String input, String expected,
            String type, CheckedAction action) {
        return test(id, UC, source, PRE, input, expected, type,
                "Nguồn: UC-37_Cancel_Booking.docx; Production: " + source
                        + ". Phương pháp kiểm thử: White-box.", action);
    }
}
