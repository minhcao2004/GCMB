/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 28/10
 * Description: DTO lệnh, danh sách và thông tin chi tiết tài sản thiết bị UC65–UC69.
 */
package com.gcmb.backend.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class EquipmentAssetDtos {
    // UC-65–UC-69: Chỉ dùng DTO dạng record lồng bên dưới, không cho tạo đối tượng tiện ích.
    private EquipmentAssetDtos() {}
    public record Command(String code, String name, UUID categoryId, UUID roomId, String locationNote,
                          String serialNumber, LocalDate purchaseDate, BigDecimal purchaseValue,
                          LocalDate warrantyExpiry, String status) {}
    public record StatusCommand(Boolean active) {}
    public record CategoryOption(UUID id, String code, String name, boolean active) {}
    public record RoomOption(UUID id, String code, String name, String operatingStatus) {}
    public record Options(List<CategoryOption> categories, List<RoomOption> rooms) {}
    public record View(UUID id, UUID branchId, String code, String name, UUID categoryId,
                       String categoryCode, String categoryName, UUID roomId, String roomCode, String roomName,
                       String locationNote, String serialNumber, LocalDate purchaseDate,
                       BigDecimal purchaseValue, LocalDate warrantyExpiry, String status,
                       boolean active, boolean hasImage, UUID latestIncidentId,
                       String latestIncident, OffsetDateTime createdAt) {}
    public record StoredImage(UUID id, String objectKey, String originalName, String mimeType,
                              long byteSize, String sha256) {}
    public record ImageContent(String originalName, String mimeType, byte[] bytes) {}
}
