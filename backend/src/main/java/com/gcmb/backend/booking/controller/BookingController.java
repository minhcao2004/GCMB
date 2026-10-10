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
}
