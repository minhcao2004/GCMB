/**
 * Copyright (c) 2026 SEP490-G12.
 * Author: TrongNV
 * Description: DTO lệnh và kết quả cho quản lý nhóm thiết bị UC60–UC64.
 */
package com.gcmb.backend.inventory.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class EquipmentCategoryDtos {
    private EquipmentCategoryDtos() {}
    public record Command(String code, String name, String description) {}
    public record StatusCommand(Boolean active) {}
    public record View(UUID id, UUID branchId, String code, String name, String description, boolean active,
                       long assetCount, OffsetDateTime createdAt) {}
}
