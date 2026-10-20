package com.gcmb.backend.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Hợp đồng đọc sổ tiền cọc và gửi yêu cầu hoàn cho kế toán xử lý.
public final class DepositDtos {
    private DepositDtos() {}

    public record DepositPage(String branchName, DepositSummary summary, List<DepositItem> items,
                              int page, int size, long total, int totalPages) {}

    public record DepositSummary(BigDecimal heldAmount, long heldBookings,
                                 BigDecimal pendingRefundAmount, long pendingRequests,
                                 BigDecimal refundedAmount, long refundTransactions,
                                 BigDecimal forfeitedAmount, long forfeitedBookings) {}

    public record DepositItem(UUID bookingId, String bookingCode, String customerName, String phone,
                              String roomCode, String roomName, Instant checkInAt, BigDecimal amount,
                              BigDecimal receivedAmount, BigDecimal balance, BigDecimal pendingRefundAmount,
                              BigDecimal refundedAmount, BigDecimal forfeitedAmount, String paymentMethod,
                              String status, String refundRequestStatus, Instant activityAt,
                              boolean canRequestRefund) {}

    public record DepositDetail(DepositItem deposit, List<DepositEvent> history) {}

    public record DepositEvent(Instant happenedAt, String type, String label, String details) {}

    public record RefundableBooking(UUID bookingId, String bookingCode, String customerName, String phone,
                                    Instant cancelledAt, BigDecimal amount) {}

    public record CreateRefundRequest(UUID bookingId, String beneficiaryName, String bankName,
                                      String bankAccount, String reason) {}

    public record RefundRequestResult(UUID id, UUID bookingId, BigDecimal amount, String status) {}

    public record RefundRequestQueue(String branchName, List<RefundRequestItem> items) {}

    public record RefundRequestItem(UUID id, UUID bookingId, String bookingCode, String customerName, String phone,
                                    BigDecimal amount, String reason, String beneficiaryName, String bankName,
                                    String bankAccount, String status, Instant requestedAt, Instant paidAt,
                                    String externalReference) {}

    public record CompleteRefundRequest(String externalReference) {}

    public record RefundCompletionResult(UUID id, UUID bookingId, BigDecimal amount, String status) {}
}
