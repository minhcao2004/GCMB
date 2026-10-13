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

    public BookingController(BookingService bookings) { this.bookings = bookings; }

    @GetMapping("/booking-options")
    public BookingDtos.BookingOptions options(@AuthenticationPrincipal Identity identity) {
        return bookings.options(identity);
    }

    @GetMapping("/availability")
    public BookingDtos.Availability availability(@AuthenticationPrincipal Identity identity,
            @RequestParam UUID roomTypeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime plannedStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime plannedEnd,
            @RequestParam(defaultValue = "HOURLY") String pricingMode,
            @RequestParam(required = false) UUID comboId) {
        return bookings.availability(identity, roomTypeId, plannedStart.toInstant(), plannedEnd.toInstant(), pricingMode, comboId);
    }

    @GetMapping("/bookings")
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
    public BookingDtos.BookingDetail detail(@AuthenticationPrincipal Identity identity, @PathVariable UUID bookingId) {
        return bookings.detail(identity, bookingId);
    }

    @GetMapping("/bookings/{bookingId}/edit-options")
    public BookingDtos.BookingEditOptions editOptions(@AuthenticationPrincipal Identity identity,
            @PathVariable UUID bookingId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime plannedStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime plannedEnd) {
        return bookings.editOptions(identity, bookingId, plannedStart.toInstant(), plannedEnd.toInstant());
    }

    @GetMapping("/bookings/{bookingId}/stay-options")
    public BookingDtos.StayOptions stayOptions(@AuthenticationPrincipal Identity identity, @PathVariable UUID bookingId) {
        return bookings.stayOptions(identity, bookingId);
    }

    @GetMapping("/bookings/{bookingId}/room-change-quote")
    public BookingDtos.RoomChangeQuote roomChangeQuote(@AuthenticationPrincipal Identity identity,
            @PathVariable UUID bookingId, @RequestParam UUID targetRoomId, @RequestParam String cause) {
        return bookings.roomChangeQuote(identity, bookingId, targetRoomId, cause);
    }

    @PostMapping("/bookings/{bookingId}/change-room")
    public BookingDtos.RoomChangeResult changeRoom(@AuthenticationPrincipal Identity identity,
            @PathVariable UUID bookingId, @RequestBody BookingDtos.RoomChangeRequest request) {
        return bookings.changeRoom(identity, bookingId, request);
    }

    @PostMapping("/bookings/{bookingId}/extension-quote")
    public BookingDtos.ExtensionQuote extensionQuote(@AuthenticationPrincipal Identity identity,
            @PathVariable UUID bookingId, @RequestBody BookingDtos.ExtendStayRequest request) {
        return bookings.extensionQuote(identity, bookingId, request);
    }

    @PostMapping("/bookings/{bookingId}/extend")
    public BookingDtos.ExtensionResult extendStay(@AuthenticationPrincipal Identity identity,
            @PathVariable UUID bookingId, @RequestBody BookingDtos.ExtendStayRequest request) {
        return bookings.extendStay(identity, bookingId, request);
    }

    @PostMapping("/bookings/{bookingId}/hourly-meter/start")
    public BookingDtos.BookingDetail startHourlyMeter(@AuthenticationPrincipal Identity identity, @PathVariable UUID bookingId) {
        return bookings.startHourlyMeter(identity, bookingId);
    }

    @PostMapping("/bookings/{bookingId}/hourly-meter/stop")
    public BookingDtos.BookingDetail stopHourlyMeter(@AuthenticationPrincipal Identity identity, @PathVariable UUID bookingId) {
        return bookings.stopHourlyMeter(identity, bookingId);
    }

    @PostMapping("/bookings/{bookingId}/check-in")
    public BookingDtos.BookingDetail checkIn(@AuthenticationPrincipal Identity identity, @PathVariable UUID bookingId) {
        return bookings.checkIn(identity, bookingId);
    }

    @PostMapping("/bookings")
    public BookingDtos.BookingDetail create(@AuthenticationPrincipal Identity identity,
            @RequestBody BookingDtos.CreateBookingRequest request) {
        return bookings.create(identity, request);
    }

    @PatchMapping("/bookings/{bookingId}")
    public BookingDtos.BookingDetail update(@AuthenticationPrincipal Identity identity, @PathVariable UUID bookingId,
            @RequestBody BookingDtos.UpdateBookingRequest request) {
        return bookings.update(identity, bookingId, request);
    }

    @PostMapping("/bookings/{bookingId}/cancel")
    public BookingDtos.CancellationResult cancel(@AuthenticationPrincipal Identity identity, @PathVariable UUID bookingId,
            @RequestBody(required = false) BookingDtos.CancelBookingRequest request) {
        return bookings.cancel(identity, bookingId, request);
    }

    @PostMapping("/bookings/{bookingId}/no-show")
    public BookingDtos.NoShowResult markNoShow(@AuthenticationPrincipal Identity identity, @PathVariable UUID bookingId,
            @RequestBody(required = false) BookingDtos.NoShowRequest request) {
        return bookings.markNoShow(identity, bookingId, request);
    }
}
