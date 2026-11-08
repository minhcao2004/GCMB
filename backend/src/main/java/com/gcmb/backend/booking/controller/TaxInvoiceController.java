/**
 * Copyright (c) 2026 SEP490-G12.
 * Author: HuyDV
 * Created: 27/10/2026
 * Description: UC-127, lễ tân gửi yêu cầu; kế toán nhận và cập nhật tiến độ hóa đơn.
 */
package com.gcmb.backend.booking.controller;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.booking.service.TaxInvoiceService;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class TaxInvoiceController {

    private final TaxInvoiceService service;

    public TaxInvoiceController(TaxInvoiceService service) {
        this.service = service;
    }

    @GetMapping({ "/api/v1/reception/tax-invoices", "/api/v1/accounting/tax-invoices" })
    public List<Map<String, Object>> list(@AuthenticationPrincipal Identity actor) {
        return service.list(actor);
    }

    @GetMapping({ "/api/v1/reception/tax-invoices/{id}", "/api/v1/accounting/tax-invoices/{id}" })
    public Map<String, Object> detail(
        @AuthenticationPrincipal Identity actor,
        @PathVariable UUID id
    ) {
        return service.detail(actor, id);
    }

    @PostMapping("/api/v1/reception/tax-invoices/{id}")
    public Map<String, Object> create(
        @AuthenticationPrincipal Identity actor,
        @PathVariable UUID id,
        @RequestBody TaxInvoiceService.Request input
    ) {
        return service.create(actor, id, input);
    }

    @PutMapping("/api/v1/accounting/tax-invoices/{id}")
    public Map<String, Object> progress(
        @AuthenticationPrincipal Identity actor,
        @PathVariable UUID id,
        @RequestBody TaxInvoiceService.Progress input
    ) {
        return service.progress(actor, id, input);
    }

    @GetMapping("/api/v1/accounting/tax-invoices/unread")
    public Map<String, Object> unread(@AuthenticationPrincipal Identity actor) {
        return service.unread(actor);
    }

    @PostMapping("/api/v1/accounting/tax-invoices/{id}/read")
    public void read(@AuthenticationPrincipal Identity actor, @PathVariable UUID id) {
        service.read(actor, id);
    }
}
