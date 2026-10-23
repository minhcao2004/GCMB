/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 12/10/2026
 * Description: DTO biểu diễn mục trong danh mục giá phòng.
 */

package com.gcmb.backend.pricing.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PriceCatalogItem(UUID id, String kind, String code, UUID roomTypeId,
                               String roomTypeName, String name, String description,
                               Integer durationMinutes, Integer minuteFrom, Integer minuteTo,
                               BigDecimal price, boolean active) {}
