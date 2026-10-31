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
            String stockStatus,
            String description,
            String imageUrl,
            String imageOriginalName,
            String imageMimeType) {}

    public record MenuItemStatusCommand(Boolean active) {}

    public record MenuItemCommand(String name, String category, String kind, UUID inventoryItemId,
                                  BigDecimal salePrice, Boolean active, String description,
                                  Boolean removeImage) {}

    public record InventoryOption(UUID id, String sku, String name, String unitName, boolean active) {}

    public record PublicMenuItemView(UUID id, String name, String category, String kind,
                                     BigDecimal salePrice, boolean available, String stockStatus,
                                     String description, String imageUrl) {}

    public record ImageAsset(String objectKey, String originalName, String mimeType) {}

    public record StoredImage(String objectKey, String originalName, String mimeType) {}
}
