/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 13/10/2026
 * Description: DTO thông tin phòng trả về cho màn hình quản lý.
 */

package com.gcmb.backend.branch.dto;

import java.util.UUID;

public record RoomView(UUID id, String code, String name, Integer floorNumber, UUID roomTypeId,
                       String roomTypeCode, String roomTypeName, String mode, Integer capacity,
                       String description, String operatingStatus, String statusReason,
                       java.time.OffsetDateTime expectedReadyAt, String bookingStatus) {}
