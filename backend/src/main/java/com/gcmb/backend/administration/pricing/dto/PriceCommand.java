package com.gcmb.backend.administration.pricing.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PriceCommand(UUID roomTypeId, String code, String name, String description,
                           Integer durationMinutes, Integer minuteFrom,
                           Integer minuteTo, BigDecimal price) {}
