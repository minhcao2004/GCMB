/**
 * Copyright (c) 2026 SEP490-G12.
 * Author: TrongNV
 * Created: 28/10
 * Description: DTO yêu cầu và phản hồi cho báo cáo, đánh giá, sửa chữa và điều chuyển thiết bị UC70–UC76.
 */
package com.gcmb.backend.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class EquipmentIncidentDtos {
    private EquipmentIncidentDtos() {}
    public record AssetOption(UUID id, String code, String name, String roomCode, String location) {}
    public record IncidentCommand(UUID assetId, String description, boolean roomUnusable, String severity, String priority, java.time.LocalDate expectedCompletionOn, String handlingPlan, String serviceProvider, String replacementDetails, BigDecimal estimatedCost, String proposedSolution) {}
    public record ReviewCommand(boolean approved, String note) {}
    public record ProgressCommand(String status, String note, LocalDate expectedCompletionOn) {}
    public record ProgressView(UUID id, UUID loggedBy, String status, String note, LocalDate expectedCompletionOn, OffsetDateTime createdAt) {}
    public record AssessmentCommand(String plan, String note, UUID replacementAssetId) {}
    public record RepairCommand(String vendorName, String outcome, BigDecimal actualCost, LocalDate completedOn, String assetCondition) {}
    public record IncidentView(UUID id, UUID assetId, String assetCode, String assetName, UUID branchId,
        String branchName, UUID roomId, String roomCode, String location, String description,
        boolean roomUnusable, String status, String handlingPlan, String assessmentNote,
        String vendorName, BigDecimal actualCost, LocalDate completedOn, String outcome,
        OffsetDateTime createdAt, String severity, String priority, LocalDate expectedCompletionOn,
        String proposedSolution, UUID reportedBy, UUID closedBy, OffsetDateTime closedAt,
        String serviceProvider, String replacementDetails, BigDecimal estimatedCost) {}
    public record TransferCommand(UUID assetId, UUID toBranchId, String reason) {}
    public record TransferReceiptCommand(UUID toRoomId, String note) {}
    public record TransferView(UUID id, UUID assetId, String assetCode, String assetName,
        UUID fromBranchId, String fromBranchName, UUID toBranchId, String toBranchName,
        UUID toRoomId, String toRoomCode, String reason, String status, OffsetDateTime createdAt) {}
}
