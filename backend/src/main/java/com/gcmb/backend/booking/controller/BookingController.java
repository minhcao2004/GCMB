package com.gcmb.backend.booking.controller;

import com.gcmb.backend.administration.auth.dto.response.Identity;
import com.gcmb.backend.booking.dto.BookingDtos;
import com.gcmb.backend.booking.service.BookingService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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

    // Service chịu trách nhiệm quy tắc nghiệp vụ và phạm vi chi nhánh.
    public BookingController(BookingService bookings) { this.bookings = bookings; }

    @GetMapping("/booking-options")
    // GET: trả lựa chọn hạng phòng và combo cho lễ tân.
    public BookingDtos.BookingOptions options(@AuthenticationPrincipal Identity identity) {
        return bookings.options(identity);
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
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime plannedEnd) {
        return bookings.editOptions(identity, bookingId, plannedStart.toInstant(), plannedEnd.toInstant());
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
        return bookings.changeRoom(identity, bookingId, request);
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
        return bookings.extendStay(identity, bookingId, request);
    }

    @PostMapping("/bookings/{bookingId}/hourly-meter/start")
    // POST: bắt đầu phiên tính giờ lẻ trên máy chủ.
    public BookingDtos.BookingDetail startHourlyMeter(@AuthenticationPrincipal Identity identity, @PathVariable UUID bookingId) {
        return bookings.startHourlyMeter(identity, bookingId);
    }

    @PostMapping("/bookings/{bookingId}/hourly-meter/stop")
    // POST: chốt phiên giờ lẻ theo thời gian thực dùng.
    public BookingDtos.BookingDetail stopHourlyMeter(@AuthenticationPrincipal Identity identity, @PathVariable UUID bookingId) {
        return bookings.stopHourlyMeter(identity, bookingId);
    }

    @PostMapping("/bookings/{bookingId}/check-in")
    // POST: ghi nhận khách vào phòng.
    public BookingDtos.BookingDetail checkIn(@AuthenticationPrincipal Identity identity, @PathVariable UUID bookingId) {
        return bookings.checkIn(identity, bookingId);
    }

    @PostMapping("/bookings")
    // POST: tạo booking và các khoản thu đã xác nhận.
    public BookingDtos.BookingDetail create(@AuthenticationPrincipal Identity identity,
            @RequestBody BookingDtos.CreateBookingRequest request) {
        return bookings.create(identity, request);
    }

    @PatchMapping("/bookings/{bookingId}")
    // PATCH: sửa booking chưa bắt đầu.
    public BookingDtos.BookingDetail update(@AuthenticationPrincipal Identity identity, @PathVariable UUID bookingId,
            @RequestBody BookingDtos.UpdateBookingRequest request) {
        return bookings.update(identity, bookingId, request);
    }

    @PostMapping("/bookings/{bookingId}/cancel")
    // POST: hủy booking và xử lý số dư cọc.
    public BookingDtos.CancellationResult cancel(@AuthenticationPrincipal Identity identity, @PathVariable UUID bookingId,
            @RequestBody(required = false) BookingDtos.CancelBookingRequest request) {
        return bookings.cancel(identity, bookingId, request);
    }

    @PostMapping("/bookings/{bookingId}/no-show")
    // POST: đánh dấu khách không đến nhận phòng.
    public BookingDtos.NoShowResult markNoShow(@AuthenticationPrincipal Identity identity, @PathVariable UUID bookingId,
            @RequestBody(required = false) BookingDtos.NoShowRequest request) {
        return bookings.markNoShow(identity, bookingId, request);
    }
}
