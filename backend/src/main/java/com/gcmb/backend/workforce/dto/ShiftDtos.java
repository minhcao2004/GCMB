/**
 * Author: HuyDV
 * Created: 27/10/2026
 * Description: UC-83–86, dữ liệu công bố ca và thay người trong cùng một ca.
 */
package com.gcmb.backend.workforce.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ShiftDtos {

    private ShiftDtos() {}

    public record Slot(
        LocalDate date,
        UUID templateId,
        Integer capacity,
        boolean open,
        Long version
    ) {}

    public record Availability(List<Slot> slots) {}

    public record Replacement(UUID employeeId, String reason, Long version) {}
}
