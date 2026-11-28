package com.gcmb.backend.finance.controller;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.finance.service.OperatingPolicyService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/head-office/operating-policies")
public class OperatingPolicyController {
    private final OperatingPolicyService service;

    public OperatingPolicyController(OperatingPolicyService service) { this.service = service; }

    @GetMapping
    public List<Map<String,Object>> history(@AuthenticationPrincipal Identity actor) {
        return service.history(actor);
    }

    @PutMapping("/{key}")
    public List<Map<String,Object>> update(@AuthenticationPrincipal Identity actor, @PathVariable String key,
                                            @RequestBody Change body) {
        return service.update(actor, key, body.amount(), body.effectiveFrom(), body.reason(), body.expectedLatestId());
    }

    public record Change(BigDecimal amount, LocalDate effectiveFrom, String reason, UUID expectedLatestId) {}
}
