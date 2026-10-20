package com.gcmb.backend.booking.controller;

import com.gcmb.backend.administration.auth.dto.response.Identity;
import com.gcmb.backend.booking.dto.DepositDtos;
import com.gcmb.backend.booking.service.DepositManagementService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounting/deposits")
public class DepositAccountingController {
    private final DepositManagementService deposits;

    public DepositAccountingController(DepositManagementService deposits) {
        this.deposits = deposits;
    }

    @GetMapping("/refund-requests")
    public DepositDtos.RefundRequestQueue refundRequests(@AuthenticationPrincipal Identity identity) {
        return deposits.refundRequests(identity);
    }

    @PostMapping("/refund-requests/{requestId}/complete")
    public DepositDtos.RefundCompletionResult completeRefund(@AuthenticationPrincipal Identity identity,
            @PathVariable UUID requestId, @RequestBody DepositDtos.CompleteRefundRequest request) {
        return deposits.completeRefund(identity, requestId, request);
    }
}
