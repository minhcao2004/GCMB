package com.gcmb.backend.inventory.dto;

import com.gcmb.backend.inventory.dto.MenuItemDtos.PublicMenuItemView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class FoodOrderDtos {
    private FoodOrderDtos() {}

    public record ItemRequest(UUID menuItemId, Integer quantity) {}
    public record RoomRequest(List<ItemRequest> items, String idempotencyKey) {}
    public record StaffOrderRequest(UUID bookingId, UUID assignedEmployeeId,
                                    List<ItemRequest> items, String idempotencyKey) {}
    public record ConfirmRequest(UUID assignedEmployeeId, List<ItemRequest> items) {}
    public record ServiceStatusCommand(String status) {}

    public record RoomContext(String roomCode, String roomName, String branchName, Instant sessionExpiresAt) {}
    public record GuestSession(String sessionToken, RoomContext room, List<PublicMenuItemView> items) {}
    public record RoomMenu(RoomContext room, List<PublicMenuItemView> items) {}
    public record GuestOrderLine(String name, BigDecimal quantity, BigDecimal unitPrice,
                                 BigDecimal amount, boolean includedInCombo) {}
    public record GuestOrderView(String code, String roomCode, String status, Instant createdAt,
                                 BigDecimal total, List<GuestOrderLine> items) {}
    public record ActiveBooking(UUID id, String code, String roomCode, String roomName,
                                String customerName, Instant checkInAt) {}
    public record ServingEmployee(UUID id, String fullName) {}
    public record OrderItem(UUID id, UUID menuItemId, String name, BigDecimal quantity,
                            BigDecimal unitPrice, BigDecimal amount, boolean includedInCombo) {}
    public record OrderView(UUID id, String code, UUID bookingId, String bookingCode,
                            String roomCode, String roomName, String customerName,
                            String channel, String status, UUID assignedEmployeeId,
                            String assignedEmployeeName, Instant createdAt, Instant acceptedAt,
                            Instant servedAt, BigDecimal total, List<OrderItem> items) {}
}
