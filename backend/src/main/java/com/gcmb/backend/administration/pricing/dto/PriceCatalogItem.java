package com.gcmb.backend.administration.pricing.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PriceCatalogItem(UUID id, String kind, String code, UUID roomTypeId,
                               String roomTypeName, String name, String description,
                               Integer durationMinutes, Integer minuteFrom, Integer minuteTo,
                               BigDecimal price, boolean active) {}
