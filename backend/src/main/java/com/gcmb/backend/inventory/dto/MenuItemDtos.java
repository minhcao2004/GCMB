package com.gcmb.backend.inventory.dto;

import java.math.BigDecimal;
import java.util.UUID;

public final class MenuItemDtos {
    private MenuItemDtos() {}

    public record MenuItemView(
            UUID id,
            String code,
            String name,
            String category,
            String kind,
            UUID inventoryItemId,
            String inventorySku,
            String inventoryName,
            BigDecimal stockQuantity,
            BigDecimal minimumQuantity,
            String unitName,
            BigDecimal salePrice,
            boolean active,
            String stockStatus) {}

    public record MenuItemStatusCommand(Boolean active) {}
}
