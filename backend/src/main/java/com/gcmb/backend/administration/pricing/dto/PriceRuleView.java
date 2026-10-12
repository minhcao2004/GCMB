package com.gcmb.backend.administration.pricing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PriceRuleView(UUID id, String code, int version, UUID roomTypeId,
                            String roomTypeName, String name, List<Integer> weekdays,
                            LocalDate validFrom, LocalDate validTo, Integer minuteFrom,
                            Integer minuteTo, BigDecimal hourlyPrice, boolean active) {}
