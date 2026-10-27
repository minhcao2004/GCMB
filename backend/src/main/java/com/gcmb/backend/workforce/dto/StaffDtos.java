/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 26/10/2026
 * Updated: 27/10/2026
 * Description: Dữ liệu hồ sơ, trạng thái và phân công nhân viên.
 */

package com.gcmb.backend.workforce.dto;

import java.time.LocalDate;
import java.util.UUID;

/** UC-77..82. Position is employment information, never a request to grant a login role. */
public final class StaffDtos {
    private StaffDtos() {}
    public record StaffCommand(String code, String fullName, String phone, String position,
            String contactEmail, String identityNumber, String address, String notes,
            LocalDate hiredOn, LocalDate resignedOn, Long version) {}
    public record StatusCommand(boolean active, String reason, LocalDate resignedOn, Long version) {}
    public record AssignmentCommand(UUID branchId, String reason, Long version) {}
}
