/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: Dữ liệu nhật ký chỉ đọc, phân trang và chi tiết thay đổi an toàn.
 */
package com.gcmb.backend.audit.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AuditDtos {

    private AuditDtos() {}

    public record AuditEntry(
        UUID id,
        Instant createdAt,
        String actorName,
        String actorUsername,
        String action,
        String entityType,
        UUID entityId,
        String entityName,
        String branchName,
        String reason
    ) {}

    public record AuditPage(List<AuditEntry> items, long total, int page, int size) {}

    public record Snapshot(
        String username,
        String role,
        String branchName,
        String status,
        String fullName,
        String email,
        String phone
    ) {}

    public record AuditDetail(
        AuditEntry event,
        Snapshot before,
        Snapshot after,
        Map<String, Object> beforeValues,
        Map<String, Object> afterValues,
        String actorRole
    ) {
        public AuditDetail(AuditEntry event, Snapshot before, Snapshot after) {
            this(event, before, after, Map.of(), Map.of(), null);
        }
    }
}
