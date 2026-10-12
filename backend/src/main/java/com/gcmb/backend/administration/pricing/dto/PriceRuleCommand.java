package com.gcmb.backend.administration.pricing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PriceRuleCommand(String code, String name, UUID roomTypeId,
                               List<Integer> weekdays, LocalDate validFrom,
                               LocalDate validTo, Integer minuteFrom,
                               Integer minuteTo, BigDecimal hourlyPrice) {}
