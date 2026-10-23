/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 13/10/2026
 * Description: DTO nhận yêu cầu thay đổi trạng thái vận hành phòng.
 */

package com.gcmb.backend.branch.dto;

import java.time.OffsetDateTime;

public record RoomStatusCommand(String status, String reason, OffsetDateTime expectedReadyAt) {}
