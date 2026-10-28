/**
 * Author: HuyDV
 * Created: 27/10/2026
 * Description: UC-87..89, dữ liệu xác nhận chấm công và chốt bảng công tháng.
 */
package com.gcmb.backend.workforce.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class AttendanceDtos {

    private AttendanceDtos() {}

    public record RecordCommand(
        String status,
        LocalDateTime actualIn,
        LocalDateTime actualOut,
        String note,
        Long version,
        List<UUID> resolvedIssueIds,
        String resolution
    ) {}

    public record CloseCommand(String revision, boolean confirmedCorrect) {}
}
