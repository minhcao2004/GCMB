package com.gcmb.backend.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BookingDtos {
    private BookingDtos() {}

    public record BookingOptions(UUID branchId, String branchName, String timezone, List<RoomTypeOption> roomTypes) {}
    public record RoomTypeOption(UUID id, String code, String name, String mode, int capacity, List<ComboOption> combos) {}
    public record ComboOption(UUID id, String code, int version, String name, int durationMinutes,
                              BigDecimal price, BigDecimal depositRequired, Instant validFrom, Instant validTo) {}
    public record AvailableRoom(UUID id, String code, String name, int capacity, String operatingStatus) {}
    public record ChargeLine(String kind, String description, BigDecimal quantity, BigDecimal unitPrice,
                             BigDecimal amount, UUID priceRuleId, UUID comboId, Map<String, Object> pricingSnapshot) {}
    public record PriceQuote(String pricingMode, BigDecimal expectedAmount, BigDecimal depositRequired,
                             List<ChargeLine> charges) {}
    public record Availability(UUID branchId, UUID roomTypeId, Instant plannedStart, Instant plannedEnd,
                               List<AvailableRoom> rooms, PriceQuote quote) {}
    public record BookingEditOptions(List<AvailableRoom> rooms) {}

    public record StayOptions(UUID currentRoomId, String currentRoomCode, UUID currentRoomTypeId, String currentRoomTypeName,
                              Instant extensionStartsAt, Instant plannedEnd, String currentComboName,
                              long currentDurationMinutes,
                              List<AvailableRoom> availableRooms, List<ComboOption> extensionCombos) {}
    public record RoomChangeRequest(UUID targetRoomId, String cause, String reason) {}
    public record RoomChangeQuote(BigDecimal currentAmount, BigDecimal projectedAmount,
                                  BigDecimal adjustmentAmount, BigDecimal usedSegmentAmount,
                                  BigDecimal nextSegmentAmount) {}
    public record RoomChangeResult(BookingDetail booking, BigDecimal previousAmount,
                                   BigDecimal updatedAmount, BigDecimal adjustmentAmount) {}
    public record ExtendStayRequest(String pricingMode, UUID comboId, Integer hours) {}
    public record ExtensionQuote(Instant startsAt, Instant endsAt, PriceQuote quote) {}
    public record ExtensionResult(BookingDetail booking, Instant startsAt, Instant endsAt, PriceQuote quote) {}
    public record MeterView(String status, Instant startedAt, Instant billedFrom, Instant reservedUntil,
                            Instant stoppedAt, Integer billedMinutes, BigDecimal hourlyPrice, BigDecimal amount) {}

    public record CreateBookingRequest(UUID roomId, Instant plannedStart, Instant plannedEnd,
                                       String pricingMode, UUID comboId, String contactName,
                                       String contactPhone, String channel, String note, Integer guestCount,
                                       BigDecimal depositAmount, String paymentMethod, String paymentReference) {}
    public record UpdateBookingRequest(UUID roomId, Instant plannedStart, Instant plannedEnd,
                                       String contactName, String contactPhone, String note) {}
    public record CancelBookingRequest(String reason, String beneficiaryName, String bankAccount, String bankName) {}
    public record NoShowRequest(String reason) {}

    public record BookingSummary(UUID id, String code, String contactName, String contactPhone,
                                 String channel, Instant plannedStart, Instant plannedEnd, Instant actualCheckin, String status,
                                 UUID roomId, String roomCode, String roomName, String roomTypeName,
                                 BigDecimal expectedAmount, BigDecimal depositBalance, BigDecimal depositReceivedAmount, int guestCount,
                                 String roomOperatingStatus) {}
    public record BookingPage(List<BookingSummary> items, int page, int size, long totalElements, int totalPages) {}
    public record AllocationView(UUID id, UUID roomId, String roomCode, String roomName,
                                 Instant startsAt, Instant endsAt, String state,
                                 String changeReason, Instant createdAt) {}
    public record ChargeView(UUID id, String kind, String description, BigDecimal quantity,
                             BigDecimal unitPrice, BigDecimal amount, String status,
                             UUID priceRuleId, UUID comboId, Map<String, Object> pricingSnapshot,
                             Instant createdAt) {}
    public record DepositView(BigDecimal balance, BigDecimal receivedAmount, BigDecimal forfeitedAmount,
                              String cancellationOutcome,
                              int refundCutoffMinutes, String paymentMethod) {}
    public record BookingDetail(BookingSummary booking, UUID branchId, String note,
                                Instant createdAt, List<AllocationView> allocations,
                                List<ChargeView> charges, DepositView deposit,
                                MeterView meter) {}
    public record CancellationResult(BookingDetail booking, String depositOutcome,
                                     BigDecimal depositAmount, Instant cancelledAt, UUID refundRequestId) {}
    public record NoShowResult(BookingDetail booking, String depositOutcome,
                               BigDecimal depositAmount, Instant markedAt, UUID releasedRoomId) {}
}
