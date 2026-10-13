/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 12/10/2026
 * Description: DTO trả thông tin quy tắc giá.
 */

package com.gcmb.backend.administration.pricing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PriceRuleView(UUID id, String code, int version, UUID roomTypeId,
                            String roomTypeName, String name, List<Integer> weekdays,
                            LocalDate validFrom, LocalDate validTo, Integer minuteFrom,
                            Integer minuteTo, BigDecimal hourlyPrice, boolean active) {}
