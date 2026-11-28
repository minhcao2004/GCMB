package com.gcmb.backend.booking.service;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.booking.exception.BookingFailure;
import com.gcmb.backend.booking.repository.BookingRepository;
import com.gcmb.backend.booking.repository.BookingRepository.AllocationRow;
import com.gcmb.backend.booking.repository.BookingRepository.BookingRow;
import com.gcmb.backend.booking.repository.BookingRepository.BranchScope;
import com.gcmb.backend.booking.repository.BookingRepository.ChargeRow;
import com.gcmb.backend.finance.service.FinancialPeriodService;
import com.gcmb.backend.inventory.service.FoodOrderService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingServiceCheckInTest {
    @Test
    void earlyCheckInPersistsTheActualStartBeforeTheShiftedEnd() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        BookingRepository repository = new BookingRepository(jdbcTemplate, mock(ObjectMapper.class));
        UUID bookingId = UUID.randomUUID();
        Instant arrival = Instant.parse("2026-11-28T07:53:00Z");
        Instant shiftedEnd = arrival.plus(Duration.ofHours(1));

        repository.checkInBooking(bookingId, arrival, shiftedEnd);

        verify(jdbcTemplate).update(eq("UPDATE gcmb.booking SET status='IN_HOUSE',actual_checkin=?,planned_start=LEAST(planned_start,?),planned_end=? WHERE id=? AND status='CONFIRMED'"),
            eq(Timestamp.from(arrival)), eq(Timestamp.from(arrival)), eq(Timestamp.from(shiftedEnd)), eq(bookingId));
    }

    @Test
    void lateCheckInRoomOptionsCoverTheShiftedStay() {
        BookingRepository repository = mock(BookingRepository.class);
        BookingService service = new BookingService(repository, mock(BookingPricingService.class), mock(ObjectMapper.class),
            mock(FoodOrderService.class), mock(FinancialPeriodService.class));
        UUID actorId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID roomTypeId = UUID.randomUUID();
        Instant start = Instant.now().minus(Duration.ofMinutes(50));
        Instant end = start.plus(Duration.ofHours(1));
        BookingRow booking = new BookingRow(bookingId, branchId, roomTypeId, "BK-LATE", "Khách đến muộn", null, 2,
            "PHONE", start, end, null, null, "CONFIRMED", null, null, 40, actorId, null, start);
        when(repository.branchScope(actorId)).thenReturn(new BranchScope(branchId, "Chi nhánh", ZoneId.of("Asia/Ho_Chi_Minh")));
        when(repository.booking(bookingId, branchId)).thenReturn(booking);
        when(repository.currentAllocation(bookingId)).thenReturn(new AllocationRow(UUID.randomUUID(), bookingId,
            UUID.randomUUID(), roomTypeId, "K03", "King 03", "King", "AVAILABLE", start, end, "RESERVED", null, start));
        when(repository.availableRoomsForEdit(eq(branchId), any(Instant.class), any(Instant.class), eq(bookingId), eq(true)))
            .thenReturn(List.of(new BookingRepository.RoomRow(UUID.randomUUID(), "K04", "King 04", 4, "AVAILABLE", roomTypeId, "King")));

        assertEquals(1, service.checkInRooms(new Identity(actorId, "le-tan", "RECEPTIONIST", "/le-tan", branchId, "Chi nhánh"), bookingId).size());

        ArgumentCaptor<Instant> shiftedStart = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> shiftedEnd = ArgumentCaptor.forClass(Instant.class);
        verify(repository).availableRoomsForEdit(eq(branchId), shiftedStart.capture(), shiftedEnd.capture(), eq(bookingId), eq(true));
        assertTrue(shiftedStart.getValue().isAfter(start));
        assertEquals(Duration.ofHours(1), Duration.between(shiftedStart.getValue(), shiftedEnd.getValue()));
    }

    @Test
    void lateCheckInShiftsCheckoutByTheBookedDuration() {
        BookingRepository repository = mock(BookingRepository.class);
        BookingService service = new BookingService(repository, mock(BookingPricingService.class), new ObjectMapper(),
            mock(FoodOrderService.class), mock(FinancialPeriodService.class));
        UUID actorId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID allocationId = UUID.randomUUID();
        Instant start = Instant.now().minus(Duration.ofMinutes(50));
        Instant end = start.plus(Duration.ofHours(1));
        BookingRow booking = new BookingRow(bookingId, branchId, "BK-LATE", "Khách đến muộn", null, 2,
            "PHONE", start, end, null, null, "CONFIRMED", null, null, 40, actorId, null, start);
        AllocationRow allocation = new AllocationRow(allocationId, bookingId, UUID.randomUUID(), UUID.randomUUID(),
            "P.201", "Phòng 201", "Queen", "AVAILABLE", start, end, "RESERVED", null, start);
        when(repository.branchScope(actorId)).thenReturn(new BranchScope(branchId, "Chi nhánh", ZoneId.of("Asia/Ho_Chi_Minh")));
        when(repository.lockBookingId(bookingId, branchId)).thenReturn(bookingId);
        when(repository.booking(bookingId, branchId)).thenReturn(booking);
        when(repository.currentAllocationForUpdate(bookingId)).thenReturn(allocation);
        when(repository.comboGiftRequirements(bookingId, branchId)).thenReturn(new BookingRepository.ComboGiftRequirements(0, 0));
        when(repository.checkInBooking(eq(bookingId), any(Instant.class), any(Instant.class))).thenReturn(1);
        when(repository.shiftAndOccupyAllocation(eq(allocationId), any(Instant.class), any(Instant.class), any(Instant.class))).thenReturn(1);
        when(repository.allocations(bookingId)).thenReturn(List.of(allocation));
        when(repository.charges(bookingId)).thenReturn(List.of());
        when(repository.depositBalance(bookingId)).thenReturn(BigDecimal.ZERO);
        when(repository.depositReceivedAmount(bookingId)).thenReturn(BigDecimal.ZERO);
        Identity actor = new Identity(actorId, "le-tan", "RECEPTIONIST", "/le-tan", branchId, "Chi nhánh");

        service.checkIn(actor, bookingId, new com.gcmb.backend.booking.dto.BookingDtos.CheckInRequest(List.of(), List.of()));

        ArgumentCaptor<Instant> actualStart = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> shiftedEnd = ArgumentCaptor.forClass(Instant.class);
        verify(repository).shiftAndOccupyAllocation(eq(allocationId), any(Instant.class), actualStart.capture(), shiftedEnd.capture());
        assertTrue(actualStart.getValue().isAfter(start));
        assertEquals(Duration.ofHours(1), Duration.between(actualStart.getValue(), shiftedEnd.getValue()));
        verify(repository).checkInBooking(eq(bookingId), eq(actualStart.getValue()), eq(shiftedEnd.getValue()));
    }

    @Test
    void lateCheckInDoesNotChargeTheTimeBeforeArrival() {
        BookingRepository repository = mock(BookingRepository.class);
        BookingService service = new BookingService(repository, mock(BookingPricingService.class), new ObjectMapper(),
            mock(FoodOrderService.class), mock(FinancialPeriodService.class));
        UUID actorId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID allocationId = UUID.randomUUID();
        UUID chargeId = UUID.randomUUID();
        Instant start = Instant.parse("2026-11-26T03:00:00Z"); // 10:00 ICT
        Instant checkIn = start.plusSeconds(50 * 60);
        Instant end = checkIn.plusSeconds(60 * 60); // 11:50 ICT sau khi dời
        BookingRow booking = new BookingRow(bookingId, branchId, "BK-LATE", "Khách đến muộn", null, 2,
            "PHONE", start, end, checkIn, null, "IN_HOUSE", null, null, 40, actorId, null, start);
        AllocationRow allocation = new AllocationRow(allocationId, bookingId, UUID.randomUUID(), UUID.randomUUID(),
            "P.201", "Phòng 201", "Queen", "AVAILABLE", checkIn, end, "OCCUPIED", null, start);
        ChargeRow charge = new ChargeRow(chargeId, allocationId, "ROOM", "Thuê phòng", BigDecimal.ONE,
            new BigDecimal("120000"), new BigDecimal("120000"), "ACTIVE", UUID.randomUUID(), null,
            "{\"pricingMode\":\"HOURLY\",\"from\":\"2026-11-26T03:00:00Z\",\"to\":\"2026-11-26T04:00:00Z\"}", start);
        when(repository.branchScope(actorId)).thenReturn(new BranchScope(branchId, "Chi nhánh", ZoneId.of("Asia/Ho_Chi_Minh")));
        when(repository.booking(bookingId, branchId)).thenReturn(booking);
        when(repository.charges(bookingId)).thenReturn(List.of(charge));
        when(repository.allocations(bookingId)).thenReturn(List.of(allocation));
        Identity actor = new Identity(actorId, "le-tan", "RECEPTIONIST", "/le-tan", branchId, "Chi nhánh");

        assertEquals(BigDecimal.ZERO, service.checkoutHourlyCharges(actor, bookingId, checkIn).get(chargeId).amount());
        assertEquals(new BigDecimal("20000"), service.checkoutHourlyCharges(actor, bookingId, checkIn.plusSeconds(10 * 60)).get(chargeId).amount());
        assertEquals(new BigDecimal("120000"), service.checkoutHourlyCharges(actor, bookingId, end).get(chargeId).amount());
    }

    @Test
    void rejectsRegularCheckInWhenRoomIsStillOccupied() {
        BookingRepository repository = mock(BookingRepository.class);
        BookingService service = new BookingService(repository, mock(BookingPricingService.class), mock(ObjectMapper.class),
            mock(FoodOrderService.class), mock(FinancialPeriodService.class));
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
        when(repository.comboGiftRequirements(bookingId, branchId)).thenReturn(new BookingRepository.ComboGiftRequirements(0, 0));
        when(repository.hasRoomOverlap(eq(roomId), any(Instant.class), any(Instant.class), eq(bookingId))).thenReturn(true);

        BookingFailure failure = assertThrows(BookingFailure.class,
            () -> service.checkIn(new Identity(
                actorId, "le-tan", "RECEPTIONIST", "/le-tan", branchId, "Chi nhánh"), bookingId,
                new com.gcmb.backend.booking.dto.BookingDtos.CheckInRequest(java.util.List.of(), java.util.List.of())));

        assertEquals(409, failure.status());
        verify(repository, never()).checkInBooking(any(), any(), any());
        ArgumentCaptor<Instant> shiftedEnd = ArgumentCaptor.forClass(Instant.class);
        verify(repository).hasRoomOverlap(eq(roomId), any(Instant.class), shiftedEnd.capture(), eq(bookingId));
        assertTrue(shiftedEnd.getValue().isAfter(end));
    }

    @Test
    void changesReservedRoomDuringCheckInAndKeepsAgreedCharges() {
        BookingRepository repository = mock(BookingRepository.class);
        BookingService service = new BookingService(repository, mock(BookingPricingService.class), new ObjectMapper(),
            mock(FoodOrderService.class), mock(FinancialPeriodService.class));
        UUID actorId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID roomTypeId = UUID.randomUUID();
        UUID oldRoomId = UUID.randomUUID();
        UUID newRoomId = UUID.randomUUID();
        UUID oldAllocationId = UUID.randomUUID();
        UUID newAllocationId = UUID.randomUUID();
        Instant start = Instant.now().plus(Duration.ofMinutes(5));
        Instant end = start.plus(Duration.ofHours(2));
        BookingRow booking = new BookingRow(bookingId, branchId, roomTypeId, "BK-ROOM", "Khách", null, 2,
            "FACEBOOK", start, end, null, null, "CONFIRMED", null, null, 40, actorId, null, start);
        AllocationRow oldAllocation = new AllocationRow(oldAllocationId, bookingId, oldRoomId, roomTypeId,
            "K03", "King 03", "King", "AVAILABLE", start, end, "RESERVED", null, start);
        AllocationRow newAllocation = new AllocationRow(newAllocationId, bookingId, newRoomId, roomTypeId,
            "K04", "King 04", "King", "AVAILABLE", start, end, "RESERVED", null, start);
        BookingRepository.RoomRow target = new BookingRepository.RoomRow(newRoomId, "K04", "King 04", 4,
            "AVAILABLE", roomTypeId, "King");
        when(repository.branchScope(actorId)).thenReturn(new BranchScope(branchId, "Chi nhánh", ZoneId.of("Asia/Ho_Chi_Minh")));
        when(repository.lockBookingId(bookingId, branchId)).thenReturn(bookingId);
        when(repository.booking(bookingId, branchId)).thenReturn(booking);
        when(repository.currentAllocationForUpdate(bookingId)).thenReturn(oldAllocation, newAllocation);
        when(repository.lockAvailableRoom(newRoomId, branchId)).thenReturn(target);
        when(repository.availableRoomsForEdit(eq(branchId), any(Instant.class), any(Instant.class), eq(bookingId), eq(true)))
            .thenReturn(List.of(target));
        when(repository.insertAllocation(eq(branchId), eq(bookingId), eq(newRoomId), any(Instant.class), any(Instant.class), eq(actorId)))
            .thenReturn(newAllocationId);
        when(repository.comboGiftRequirements(bookingId, branchId)).thenReturn(new BookingRepository.ComboGiftRequirements(0, 0));
        when(repository.checkInBooking(eq(bookingId), any(Instant.class), any(Instant.class))).thenReturn(1);
        when(repository.shiftAndOccupyAllocation(eq(newAllocationId), any(Instant.class), any(Instant.class), any(Instant.class))).thenReturn(1);
        when(repository.allocations(bookingId)).thenReturn(List.of(oldAllocation, newAllocation));
        when(repository.charges(bookingId)).thenReturn(List.of());
        when(repository.depositBalance(bookingId)).thenReturn(BigDecimal.ZERO);
        when(repository.depositReceivedAmount(bookingId)).thenReturn(BigDecimal.ZERO);

        service.checkIn(new Identity(actorId, "le-tan", "RECEPTIONIST", "/le-tan", branchId, "Chi nhánh"), bookingId,
            new com.gcmb.backend.booking.dto.BookingDtos.CheckInRequest(newRoomId, List.of(), List.of()));

        verify(repository).releaseAllocation(oldAllocationId, "Đổi phòng khi check-in");
        verify(repository).moveActiveCharges(oldAllocationId, newAllocationId);
        verify(repository).shiftAndOccupyAllocation(eq(newAllocationId), any(Instant.class), any(Instant.class), any(Instant.class));
        verify(repository, never()).shiftAndOccupyAllocation(eq(oldAllocationId), any(Instant.class), any(Instant.class), any(Instant.class));
    }

    @Test
    void releasedDemoRoomDoesNotHideAvailableRoomsDuringEdit() {
        BookingRepository repository = mock(BookingRepository.class);
        BookingService service = new BookingService(repository, mock(BookingPricingService.class), new ObjectMapper(),
            mock(FoodOrderService.class), mock(FinancialPeriodService.class));
        UUID actorId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID roomTypeId = UUID.randomUUID();
        UUID comboId = UUID.randomUUID();
        UUID k02 = UUID.randomUUID();
        UUID allocationId = UUID.randomUUID();
        Instant start = Instant.now().plus(Duration.ofHours(2));
        Instant end = start.plus(Duration.ofHours(2));
        BookingRow booking = new BookingRow(bookingId, branchId, roomTypeId, "BK-DEMO", "Khách", null, 2,
            "FACEBOOK", start, end, null, null, "CONFIRMED", null, null, 40, actorId, null, start);
        AllocationRow released = new AllocationRow(UUID.randomUUID(), bookingId, UUID.randomUUID(), roomTypeId,
            "K01", "King 01", "King", "AVAILABLE", start, end, "RELEASED", null, start.minusSeconds(60));
        AllocationRow reserved = new AllocationRow(allocationId, bookingId, k02, roomTypeId,
            "K02", "King 02", "King", "AVAILABLE", start, end, "RESERVED", null, start);
        BookingRepository.RoomRow room = new BookingRepository.RoomRow(k02, "K02", "King 02", 6,
            "AVAILABLE", roomTypeId, "King");
        ChargeRow charge = new ChargeRow(UUID.randomUUID(), null, "COMBO", "Gói King", BigDecimal.ONE,
            new BigDecimal("230000"), new BigDecimal("230000"), "ACTIVE", null, comboId,
            "{\"durationMinutes\":120,\"overtimeHourlyPrice\":100000}", start);
        Identity actor = new Identity(actorId, "le-tan", "RECEPTIONIST", "/le-tan", branchId, "Chi nhánh");
        when(repository.branchScope(actorId)).thenReturn(new BranchScope(branchId, "Chi nhánh", ZoneId.of("Asia/Ho_Chi_Minh")));
        when(repository.booking(bookingId, branchId)).thenReturn(booking);
        when(repository.allocations(bookingId)).thenReturn(List.of(released), List.of(released, reserved));
        when(repository.charges(bookingId)).thenReturn(List.of(charge));
        when(repository.activeChargeTotal(bookingId)).thenReturn(new BigDecimal("230000"));
        when(repository.availableRoomsForEdit(eq(branchId), eq(start), eq(end), eq(bookingId)))
            .thenReturn(List.of(room));
        when(repository.unassignedBookings(branchId, roomTypeId, start, end, bookingId)).thenReturn(0);
        when(repository.lockBookingId(bookingId, branchId)).thenReturn(bookingId);
        when(repository.lockRoomType(roomTypeId)).thenReturn(new BookingRepository.RoomTypeRow(roomTypeId, "KING", "King", "COMBO", 6));
        when(repository.availableRooms(branchId, roomTypeId, start, end)).thenReturn(List.of(room));
        when(repository.lockAvailableRoom(k02, branchId)).thenReturn(room);
        when(repository.insertAllocation(eq(branchId), eq(bookingId), eq(k02), eq(start), eq(end), eq(actorId)))
            .thenReturn(allocationId);

        assertNull(service.detail(actor, bookingId).booking().roomId());
        assertEquals(k02, service.editOptions(actor, bookingId, start, end, "COMBO", comboId).rooms().get(0).id());
        var updated = service.update(actor, bookingId, new com.gcmb.backend.booking.dto.BookingDtos.UpdateBookingRequest(
            k02, start, end, "COMBO", comboId, null, null, null));

        assertEquals(k02, updated.booking().roomId());
        verify(repository).insertCharge(eq(bookingId), eq(allocationId), any());
    }
}
