package com.gcmb.backend.booking.controller;

import com.gcmb.backend.administration.audit.service.MutationAudit;
import com.gcmb.backend.administration.auth.dto.response.Identity;
import com.gcmb.backend.booking.dto.BookingDtos;
import com.gcmb.backend.booking.dto.DepositDtos;
import com.gcmb.backend.booking.service.DepositManagementService;
import com.gcmb.backend.booking.service.BookingService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reception")
public class BookingController {
    private final BookingService bookings;
    private final DepositManagementService deposits;

    // Service chịu trách nhiệm quy tắc nghiệp vụ và phạm vi chi nhánh.
    private final MutationAudit audit;
    public BookingController(BookingService bookings, DepositManagementService deposits, MutationAudit audit) {
        this.bookings = bookings;
        this.deposits = deposits;
        this.audit = audit;
    }
    @GetMapping("/deposits")
    // GET: danh sách tiền cọc, tổng hợp và bộ lọc trong phạm vi chi nhánh lễ tân.
    public DepositDtos.DepositPage deposits(@AuthenticationPrincipal Identity identity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return deposits.list(identity, dateFrom, dateTo, status, method, search, page, size);
    }

    @GetMapping("/deposits/eligible-refund-bookings")
    // GET: các booking đã hủy còn cọc và chưa có yêu cầu hoàn đang xử lý.
    public List<DepositDtos.RefundableBooking> eligibleRefundBookings(@AuthenticationPrincipal Identity identity) {
        return deposits.eligibleRefundBookings(identity);
    }

    @GetMapping("/deposits/{bookingId}")
    // GET: chi tiết và lịch sử tiền cọc của một booking trong chi nhánh hiện tại.
    public DepositDtos.DepositDetail depositDetail(@AuthenticationPrincipal Identity identity, @PathVariable UUID bookingId) {
        return deposits.detail(identity, bookingId);
    }

    @PostMapping("/deposits/refund-requests")
    // POST: tạo yêu cầu chờ kế toán xử lý; lễ tân không ghi nhận đã hoàn tiền.
    public DepositDtos.RefundRequestResult createDepositRefundRequest(@AuthenticationPrincipal Identity identity,
            @RequestBody DepositDtos.CreateRefundRequest request) {
        return audit.record("REQUEST_DEPOSIT_REFUND", "BOOKING", request == null ? null : request.bookingId(),
            () -> deposits.createRefundRequest(identity, request), null);
    }

    @GetMapping("/booking-options")
    // GET: trả lựa chọn hạng phòng và combo cho lễ tân.
    public BookingDtos.BookingOptions options(@AuthenticationPrincipal Identity identity) {
        return bookings.options(identity);
    }

    @GetMapping("/rooms")
    public List<BookingDtos.RoomSummary> rooms(@AuthenticationPrincipal Identity identity) {
        return bookings.rooms(identity);
    }

    @PostMapping("/rooms/{roomId}/finish-cleaning")
    public void finishCleaning(@AuthenticationPrincipal Identity identity, @PathVariable UUID roomId) {
        audit.record("ROOM_CLEANING_COMPLETED", "ROOM", roomId, () -> {
            bookings.finishCleaning(identity, roomId);
            return null;
        }, null);
    }

    @GetMapping("/availability")
    // GET: báo phòng trống và giá cho khoảng thời gian yêu cầu.
    public BookingDtos.Availability availability(@AuthenticationPrincipal Identity identity,
            @RequestParam UUID roomTypeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime plannedStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime plannedEnd,
            @RequestParam(defaultValue = "HOURLY") String pricingMode,
            @RequestParam(required = false) UUID comboId) {
        return bookings.availability(identity, roomTypeId, plannedStart.toInstant(), plannedEnd.toInstant(), pricingMode, comboId);
    }

    @GetMapping("/bookings")
    // GET: trả danh sách booking có lọc và phân trang.
    public BookingDtos.BookingPage list(@AuthenticationPrincipal Identity identity,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean hasDeposit,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return bookings.list(identity, status, dateFrom, dateTo, search, hasDeposit, page, size);
    }

    @GetMapping("/bookings/{bookingId}")
    // GET: trả lịch phòng, phí và cọc của một booking.
    public BookingDtos.BookingDetail detail(@AuthenticationPrincipal Identity identity, @PathVariable UUID bookingId) {
        return bookings.detail(identity, bookingId);
    }

    @GetMapping("/bookings/{bookingId}/edit-options")
    // GET: trả phòng có thể chọn khi sửa booking.
    public BookingDtos.BookingEditOptions editOptions(@AuthenticationPrincipal Identity identity,
            @PathVariable UUID bookingId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime plannedStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime plannedEnd,
            @RequestParam(required = false) String pricingMode,
            @RequestParam(required = false) UUID comboId) {
        return bookings.editOptions(identity, bookingId, plannedStart.toInstant(), plannedEnd.toInstant(), pricingMode, comboId);
    }

    @GetMapping("/bookings/{bookingId}/stay-options")
    // GET: trả dữ kiện đổi phòng và gia hạn cho booking đang sử dụng.
    public BookingDtos.StayOptions stayOptions(@AuthenticationPrincipal Identity identity, @PathVariable UUID bookingId) {
        return bookings.stayOptions(identity, bookingId);
    }

    @GetMapping("/bookings/{bookingId}/room-change-quote")
    // GET: báo khoản điều chỉnh dự kiến khi đổi phòng.
    public BookingDtos.RoomChangeQuote roomChangeQuote(@AuthenticationPrincipal Identity identity,
            @PathVariable UUID bookingId, @RequestParam UUID targetRoomId, @RequestParam String cause) {
        return bookings.roomChangeQuote(identity, bookingId, targetRoomId, cause);
    }

    @PostMapping("/bookings/{bookingId}/change-room")
    // POST: ghi đổi phòng và trả booking đã cập nhật.
    public BookingDtos.RoomChangeResult changeRoom(@AuthenticationPrincipal Identity identity,
            @PathVariable UUID bookingId, @RequestBody BookingDtos.RoomChangeRequest request) {
        return audit.record("BOOKING_ROOM_CHANGED","BOOKING",bookingId,()->bookings.changeRoom(identity, bookingId, request),null);
    }

    @PostMapping("/bookings/{bookingId}/extension-quote")
    // POST: báo giá gia hạn mà chưa lưu thay đổi.
    public BookingDtos.ExtensionQuote extensionQuote(@AuthenticationPrincipal Identity identity,
            @PathVariable UUID bookingId, @RequestBody BookingDtos.ExtendStayRequest request) {
        return bookings.extensionQuote(identity, bookingId, request);
    }

    @PostMapping("/bookings/{bookingId}/extend")
    // POST: xác nhận gia hạn và khoản phí mới.
    public BookingDtos.ExtensionResult extendStay(@AuthenticationPrincipal Identity identity,
            @PathVariable UUID bookingId, @RequestBody BookingDtos.ExtendStayRequest request) {
        return audit.record("BOOKING_EXTENDED","BOOKING",bookingId,()->bookings.extendStay(identity, bookingId, request),null);
    }

    @PostMapping("/bookings/{bookingId}/hourly-meter/start")
    // POST: bắt đầu phiên tính giờ lẻ trên máy chủ.
    public BookingDtos.BookingDetail startHourlyMeter(@AuthenticationPrincipal Identity identity, @PathVariable UUID bookingId) {
        return audit.record("HOURLY_METER_STARTED","BOOKING",bookingId,()->bookings.startHourlyMeter(identity, bookingId),null);
    }

    @PostMapping("/bookings/{bookingId}/hourly-meter/stop")
    // POST: chốt phiên giờ lẻ theo thời gian thực dùng.
    public BookingDtos.BookingDetail stopHourlyMeter(@AuthenticationPrincipal Identity identity, @PathVariable UUID bookingId) {
        return audit.record("HOURLY_METER_STOPPED","BOOKING",bookingId,()->bookings.stopHourlyMeter(identity, bookingId),null);
    }

    @PostMapping("/bookings/{bookingId}/check-in")
    // POST: ghi nhận khách vào phòng.
    public BookingDtos.BookingDetail checkIn(@AuthenticationPrincipal Identity identity, @PathVariable UUID bookingId) {
        return audit.record("BOOKING_CHECKED_IN","BOOKING",bookingId,()->bookings.checkIn(identity, bookingId),null);
    }

    @PostMapping("/bookings")
    // POST: tạo booking và các khoản thu đã xác nhận.
    public BookingDtos.BookingDetail create(@AuthenticationPrincipal Identity identity,
            @RequestBody BookingDtos.CreateBookingRequest request) {
        return audit.record("BOOKING_CREATED","BOOKING",null,()->bookings.create(identity, request),result->result.booking().id());
    }

    @PatchMapping("/bookings/{bookingId}")
    // PATCH: sửa booking chưa bắt đầu.
    public BookingDtos.BookingDetail update(@AuthenticationPrincipal Identity identity, @PathVariable UUID bookingId,
            @RequestBody BookingDtos.UpdateBookingRequest request) {
        return audit.record("BOOKING_UPDATED","BOOKING",bookingId,()->bookings.update(identity, bookingId, request),null);
    }

    @PostMapping("/bookings/{bookingId}/cancel")
    // POST: hủy booking và xử lý số dư cọc.
    public BookingDtos.CancellationResult cancel(@AuthenticationPrincipal Identity identity, @PathVariable UUID bookingId,
            @RequestBody(required = false) BookingDtos.CancelBookingRequest request) {
        return audit.record("BOOKING_CANCELLED","BOOKING",bookingId,()->bookings.cancel(identity, bookingId, request),null);
    }

    @PostMapping("/bookings/{bookingId}/no-show")
    // POST: đánh dấu khách không đến nhận phòng.
    public BookingDtos.NoShowResult markNoShow(@AuthenticationPrincipal Identity identity, @PathVariable UUID bookingId,
            @RequestBody(required = false) BookingDtos.NoShowRequest request) {
        return audit.record("BOOKING_NO_SHOW","BOOKING",bookingId,()->bookings.markNoShow(identity, bookingId, request),null);
    }
}
