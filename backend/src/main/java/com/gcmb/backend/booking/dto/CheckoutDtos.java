/**
 * Copyright (c) 2026 SEP490-G12.
 * Author: HuyDV
 * Created: 14/10/2026
 * Description: Hợp đồng kiểm tra phòng, chốt bill và thanh toán không áp dụng cọc.
 */
package com.gcmb.backend.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CheckoutDtos {

    private CheckoutDtos() {}

    public record Surcharge(String reason, BigDecimal amount) {}

    public record Issue(
        boolean inspected,
        String inspectionNote,
        List<Surcharge> surcharges,
        boolean overtimeConfirmed,
        BigDecimal overtimeAmount,
        boolean unservedConfirmed,
        Instant quotedAt,
        String quoteToken
    ) {}

    public record Overtime(
        Instant from,
        Instant to,
        long seconds,
        BigDecimal hourlyPrice,
        BigDecimal amount,
        UUID priceRuleId
    ) {}

    public record PaymentPlan(String method, BigDecimal cashAmount, BigDecimal cashTendered) {}

    public record Webhook(
        Long id,
        String gateway,
        String transactionDate,
        String accountNumber,
        String code,
        String content,
        String transferType,
        BigDecimal transferAmount,
        String referenceCode
    ) {}
}
