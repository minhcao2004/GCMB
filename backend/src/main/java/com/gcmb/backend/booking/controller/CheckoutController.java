/**
 * Copyright (c) 2026 SEP490-G12.
 * Author: HuyDV
 * Created: 14/10/2026
 * Description: API checkout/lập bill của lễ tân và webhook có chứng thực từ SePay.
 */
package com.gcmb.backend.booking.controller;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.booking.dto.CheckoutDtos.*;
import com.gcmb.backend.booking.service.CheckoutService;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class CheckoutController {

    private final CheckoutService checkoutService;
    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @GetMapping("/api/v1/reception/bills")
    public java.util.List<Map<String, Object>> list(@AuthenticationPrincipal Identity actor) {
        return checkoutService.list(actor);
    }

    @GetMapping("/api/v1/reception/bookings/{id}/checkout")
    public Map<String, Object> preview(
        @AuthenticationPrincipal Identity actor,
        @PathVariable UUID id
    ) {
        return checkoutService.preview(actor, id);
    }

    @PostMapping("/api/v1/reception/bookings/{id}/checkout")
    public Map<String, Object> issue(
        @AuthenticationPrincipal Identity actor,
        @PathVariable UUID id,
        @RequestBody Issue input
    ) {
        return checkoutService.issue(actor, id, input);
    }

    @GetMapping("/api/v1/reception/bills/{id}")
    public Map<String, Object> bill(
        @AuthenticationPrincipal Identity actor,
        @PathVariable UUID id
    ) {
        return checkoutService.bill(actor, id);
    }

    @PostMapping("/api/v1/reception/bills/{id}/payment-plan")
    public Map<String, Object> plan(
        @AuthenticationPrincipal Identity actor,
        @PathVariable UUID id,
        @RequestBody PaymentPlan input
    ) {
        return checkoutService.plan(actor, id, input);
    }

    @PostMapping("/api/v1/reception/bills/{id}/complete")
    public Map<String, Object> complete(
        @AuthenticationPrincipal Identity actor,
        @PathVariable UUID id
    ) {
        return checkoutService.complete(actor, id);
    }

    @PostMapping("/api/v1/reception/bills/{id}/renew-qr")
    public Map<String, Object> renew(
        @AuthenticationPrincipal Identity actor,
        @PathVariable UUID id
    ) {
        return checkoutService.renew(actor, id);
    }

    /** Đặt lại phân bổ tạm khi chưa có khoản thanh toán thực tế. */
    @PostMapping("/api/v1/reception/bills/{id}/change-payment-method")
    public Map<String, Object> changeMethod(
        @AuthenticationPrincipal Identity actor,
        @PathVariable UUID id
    ) {
        return checkoutService.changeMethod(actor, id);
    }

    @PostMapping("/api/v1/payments/sepay/webhook")
    public Map<String, Boolean> webhook(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestBody Webhook payload
    ) {
        checkoutService.webhook(authorization, payload);
        return Map.of("success", true);
    }
}
