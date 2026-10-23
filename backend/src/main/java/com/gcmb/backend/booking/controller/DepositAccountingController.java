package com.gcmb.backend.booking.controller;

import com.gcmb.backend.administration.auth.dto.response.Identity;
import com.gcmb.backend.booking.dto.DepositDtos;
import com.gcmb.backend.booking.service.DepositManagementService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping(value = "/refund-requests/{requestId}/complete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DepositDtos.RefundCompletionResult completeRefund(@AuthenticationPrincipal Identity identity,
            @PathVariable UUID requestId, @RequestParam("externalReference") String externalReference,
            @RequestPart("image") MultipartFile image) {
        return deposits.completeRefund(identity, requestId, externalReference, image);
    }

    @GetMapping("/refund-requests/{requestId}/evidence")
    public ResponseEntity<byte[]> refundEvidence(@AuthenticationPrincipal Identity identity,
            @PathVariable UUID requestId) {
        DepositDtos.RefundEvidenceContent evidence = deposits.refundEvidence(identity, requestId);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(evidence.mimeType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                .filename(evidence.originalName(), StandardCharsets.UTF_8).build().toString())
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .header("X-Content-Type-Options", "nosniff")
            .body(evidence.bytes());
    }
}
