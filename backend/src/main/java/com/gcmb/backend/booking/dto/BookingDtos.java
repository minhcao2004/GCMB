package com.gcmb.backend.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Hợp đồng request/response của API booking; dữ liệu tính giá do backend quyết định.
public final class BookingDtos {
    // Lớp chỉ gom các record hợp đồng, không tạo instance.
    private BookingDtos() {}

    // Lựa chọn ban đầu theo chi nhánh cho biểu mẫu tạo booking.
    public record BookingOptions(UUID branchId, String branchName, String timezone, List<RoomTypeOption> roomTypes) {}
    // Hạng phòng cùng sức chứa và combo được phép chọn.
    public record RoomTypeOption(UUID id, String code, String name, String mode, int capacity, List<ComboOption> combos) {}
    // Phiên bản và thời hạn của một combo dùng để báo giá.
    public record ComboOption(UUID id, String code, int version, String name, int durationMinutes,
                              BigDecimal price, BigDecimal depositRequired, Instant validFrom, Instant validTo) {}
    // Phòng còn phù hợp với khoảng giờ và sức chứa yêu cầu.
    public record AvailableRoom(UUID id, String code, String name, int capacity, String operatingStatus) {}
    // Dòng phí chuẩn bị lưu kèm snapshot giải thích giá.
    public record ChargeLine(String kind, String description, BigDecimal quantity, BigDecimal unitPrice,
                             BigDecimal amount, UUID priceRuleId, UUID comboId, Map<String, Object> pricingSnapshot) {}
    // Tổng tiền dự kiến, cọc yêu cầu và các dòng phí cấu thành.
    public record PriceQuote(String pricingMode, BigDecimal expectedAmount, BigDecimal depositRequired,
                             List<ChargeLine> charges) {}
    // Kết quả kiểm tra lịch phòng và giá cho một khoảng thời gian.
    public record Availability(UUID branchId, UUID roomTypeId, Instant plannedStart, Instant plannedEnd,
                               List<AvailableRoom> rooms, PriceQuote quote) {}
    // Danh sách phòng hợp lệ khi sửa booking.
    public record BookingEditOptions(List<AvailableRoom> rooms) {}

    // Dữ liệu phòng hiện tại, phòng có thể đổi và combo có thể gia hạn.
    public record StayOptions(UUID currentRoomId, String currentRoomCode, UUID currentRoomTypeId, String currentRoomTypeName,
                              Instant extensionStartsAt, Instant plannedEnd, String currentComboName,
                              long currentDurationMinutes,
                              List<AvailableRoom> availableRooms, List<ComboOption> extensionCombos) {}
    // Phòng đích, nguyên nhân áp giá và ghi chú đổi phòng.
    public record RoomChangeRequest(UUID targetRoomId, String cause, String reason) {}
    // So sánh tổng phí trước/sau đổi phòng và từng phần sử dụng.
    public record RoomChangeQuote(BigDecimal currentAmount, BigDecimal projectedAmount,
                                  BigDecimal adjustmentAmount, BigDecimal usedSegmentAmount,
                                  BigDecimal nextSegmentAmount) {}
    // Booking sau đổi phòng cùng phần điều chỉnh tiền.
    public record RoomChangeResult(BookingDetail booking, BigDecimal previousAmount,
                                   BigDecimal updatedAmount, BigDecimal adjustmentAmount) {}
    // Yêu cầu gia hạn bằng combo hoặc số giờ cho client cũ.
    public record ExtendStayRequest(String pricingMode, UUID comboId, Integer hours) {}
    // Mốc gia hạn và báo giá trước khi ghi dữ liệu.
    public record ExtensionQuote(Instant startsAt, Instant endsAt, PriceQuote quote) {}
    // Booking sau khi gia hạn và giá đã ghi nhận.
    public record ExtensionResult(BookingDetail booking, Instant startsAt, Instant endsAt, PriceQuote quote) {}
    // Trạng thái đồng hồ giờ lẻ, đơn giá chốt và số phút/tiền đã tính.
    public record MeterView(String status, Instant startedAt, Instant billedFrom, Instant reservedUntil,
                            Instant stoppedAt, Integer billedMinutes, BigDecimal hourlyPrice, BigDecimal amount) {}

    // Dữ liệu tạo booking; khoản cọc chỉ ghi khi lễ tân xác nhận đã thu.
    public record CreateBookingRequest(UUID roomId, Instant plannedStart, Instant plannedEnd,
                                       String pricingMode, UUID comboId, String contactName,
                                       String contactPhone, String channel, String note, Integer guestCount,
                                       BigDecimal depositAmount, String paymentMethod, String paymentReference) {}
    // Các trường có thể sửa trước giờ bắt đầu booking.
    public record UpdateBookingRequest(UUID roomId, Instant plannedStart, Instant plannedEnd,
                                       String contactName, String contactPhone, String note) {}
    // Lý do hủy và thông tin người nhận nếu cần hoàn cọc.
    public record CancelBookingRequest(String reason, String beneficiaryName, String bankAccount, String bankName) {}
    // Lý do đánh dấu khách không đến nhận phòng.
    public record NoShowRequest(String reason) {}

    // Dữ liệu tóm tắt cho danh sách, gồm số đã thu và số dư cọc.
    public record BookingSummary(UUID id, String code, String contactName, String contactPhone,
                                 String channel, Instant plannedStart, Instant plannedEnd, Instant actualCheckin, String status,
                                 UUID roomId, String roomCode, String roomName, String roomTypeName,
                                 BigDecimal expectedAmount, BigDecimal depositBalance, BigDecimal depositReceivedAmount, int guestCount,
                                 String roomOperatingStatus) {}
    // Trang kết quả và tổng số booking sau khi lọc.
    public record BookingPage(List<BookingSummary> items, int page, int size, long totalElements, int totalPages) {}
    // Một chặng trong lịch sử giữ/sử dụng phòng.
    public record AllocationView(UUID id, UUID roomId, String roomCode, String roomName,
                                 Instant startsAt, Instant endsAt, String state,
                                 String changeReason, Instant createdAt) {}
    // Dòng phí đã lưu, kể cả trạng thái bị thay thế.
    public record ChargeView(UUID id, String kind, String description, BigDecimal quantity,
                             BigDecimal unitPrice, BigDecimal amount, String status,
                             UUID priceRuleId, UUID comboId, Map<String, Object> pricingSnapshot,
                             Instant createdAt) {}
    // Số dư, tổng đã nhận, cọc bị giữ và kết quả hủy.
    public record DepositView(BigDecimal balance, BigDecimal receivedAmount, BigDecimal forfeitedAmount,
                              String cancellationOutcome,
                              int refundCutoffMinutes, String paymentMethod) {}
    // Tập hợp booking, các chặng phòng, phí, cọc và đồng hồ.
    public record BookingDetail(BookingSummary booking, UUID branchId, String note,
                                Instant createdAt, List<AllocationView> allocations,
                                List<ChargeView> charges, DepositView deposit,
                                MeterView meter) {}
    // Kết quả hủy và ID yêu cầu hoàn cọc nếu được tạo.
    public record CancellationResult(BookingDetail booking, String depositOutcome,
                                     BigDecimal depositAmount, Instant cancelledAt, UUID refundRequestId) {}
    // Kết quả no-show, cọc bị giữ và phòng được giải phóng.
    public record NoShowResult(BookingDetail booking, String depositOutcome,
                               BigDecimal depositAmount, Instant markedAt, UUID releasedRoomId) {}
}
