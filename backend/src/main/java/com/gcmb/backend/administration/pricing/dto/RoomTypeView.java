/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 12/10/2026
 * Description: DTO trả thông tin hạng phòng.
 */

package com.gcmb.backend.administration.pricing.dto;

import java.util.UUID;

public record RoomTypeView(UUID id, String code, String name, String serviceType,
                           Integer capacity, String description, boolean active) {}
