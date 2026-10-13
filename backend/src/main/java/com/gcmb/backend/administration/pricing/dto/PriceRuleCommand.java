/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 12/10/2026
 * Description: DTO nhận dữ liệu tạo và cập nhật quy tắc giá.
 */

package com.gcmb.backend.administration.pricing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PriceRuleCommand(String code, String name, UUID roomTypeId,
                               List<Integer> weekdays, LocalDate validFrom,
                               LocalDate validTo, Integer minuteFrom,
                               Integer minuteTo, BigDecimal hourlyPrice) {}
