/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 12/10/2026
 * Description: DTO nhận dữ liệu cấu hình giá phòng.
 */

package com.gcmb.backend.pricing.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PriceCommand(UUID roomTypeId, String code, String name, String description,
                           Integer durationMinutes, Integer minuteFrom,
                           Integer minuteTo, BigDecimal price) {}
