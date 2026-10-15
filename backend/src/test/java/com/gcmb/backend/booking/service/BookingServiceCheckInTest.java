package com.gcmb.backend.booking.service;

import com.gcmb.backend.administration.auth.dto.response.Identity;
import com.gcmb.backend.booking.exception.BookingFailure;
import com.gcmb.backend.booking.repository.BookingRepository;
import com.gcmb.backend.booking.repository.BookingRepository.AllocationRow;
import com.gcmb.backend.booking.repository.BookingRepository.BookingRow;
import com.gcmb.backend.booking.repository.BookingRepository.BranchScope;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingServiceCheckInTest {
    @Test
    void rejectsRegularCheckInWhenRoomIsStillOccupied() {
        BookingRepository repository = mock(BookingRepository.class);
        BookingService service = new BookingService(repository, mock(BookingPricingService.class), mock(ObjectMapper.class));
        UUID actorId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID allocationId = UUID.randomUUID();
        Instant start = Instant.now().minusSeconds(1800);
        Instant end = Instant.now().plusSeconds(3600);
        BookingRow booking = new BookingRow(bookingId, branchId, "BK-NEW", "Khách mới", null, 2,
            "PHONE", start, end, null, null, "CONFIRMED", null, null, 40, actorId, null, start);
        AllocationRow allocation = new AllocationRow(allocationId, bookingId, roomId, UUID.randomUUID(),
            "P.201", "Phòng 201", "Queen", "AVAILABLE", start, end, "RESERVED", null, start);
        when(repository.branchScope(actorId)).thenReturn(new BranchScope(branchId, "Chi nhánh", ZoneId.of("Asia/Ho_Chi_Minh")));
        when(repository.lockBookingId(bookingId, branchId)).thenReturn(bookingId);
        when(repository.booking(bookingId, branchId)).thenReturn(booking);
        when(repository.currentAllocationForUpdate(bookingId)).thenReturn(allocation);
        when(repository.hasRoomOverlap(eq(roomId), any(Instant.class), any(Instant.class), eq(bookingId))).thenReturn(true);

        BookingFailure failure = assertThrows(BookingFailure.class,
            () -> service.checkIn(new Identity(actorId, "le-tan", "RECEPTIONIST", "/le-tan"), bookingId));

        assertEquals(409, failure.status());
        verify(repository, never()).checkInBooking(any(), any(), any());
    }
}
