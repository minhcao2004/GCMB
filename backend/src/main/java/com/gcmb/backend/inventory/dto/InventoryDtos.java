package com.gcmb.backend.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class InventoryDtos {
    private InventoryDtos() {}

    public record InventoryItemCommand(String sku, String name, String category, UUID unitId,
                                       String trackingMode, BigDecimal minimumQuantity, Boolean active) {}

    public record InventoryItemView(UUID id, String sku, String name, String category, UUID unitId,
                                    String unitName, String trackingMode, BigDecimal quantity,
                                    BigDecimal minimumQuantity, long stockVersion, boolean active,
                                    boolean hasMovements, String stockStatus) {}

    public record UnitOption(UUID id, String code, String name) {}

    public record InventoryMetadata(List<UnitOption> units, List<String> categories) {}

    public record ReceiptLineCommand(UUID inventoryItemId, BigDecimal quantity, BigDecimal unitCost) {}

    public record StockReceiptCommand(Instant receivedAt, String supplierName, String supplierInvoice,
                                      List<ReceiptLineCommand> items) {}

    public record ReceiptLineResult(UUID inventoryItemId, String sku, String name, String unitName,
                                   BigDecimal quantity, BigDecimal unitCost, BigDecimal amount,
                                   BigDecimal quantityAfter) {}

    public record StockReceiptResult(String code, Instant receivedAt, int itemCount,
                                     BigDecimal totalQuantity, BigDecimal totalValue,
                                     List<ReceiptLineResult> items) {}

    public record StockAdjustmentCommand(UUID inventoryItemId, Instant occurredAt, Long expectedStockVersion,
                                         BigDecimal expectedQuantity, BigDecimal actualQuantity,
                                         String reason) {}

    public record StockMovementView(UUID id, Instant occurredAt, String kind,
                                    BigDecimal quantityDelta, BigDecimal quantityAfter,
                                    BigDecimal unitCost, String source, String actor,
                                    String reason, String evidenceName, String evidenceUrl) {}

    public record EvidenceContent(String originalName, String mimeType, byte[] bytes) {}
}
