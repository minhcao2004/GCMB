/**
 * Author: HuyDV
 * Created: 27/10/2026
 * Description: UC-83–86, API lịch ca đúng vai trò và cơ sở trong phiên.
 */
package com.gcmb.backend.workforce.controller;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.workforce.dto.ShiftDtos.*;
import com.gcmb.backend.workforce.service.ShiftService;
import java.time.LocalDate;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class ShiftController {

    private final ShiftService service;

    public ShiftController(ShiftService service) {
        this.service = service;
    }

    @GetMapping({ "/api/v1/branch-management/shifts", "/api/v1/reception/shifts" })
    public Map<String, Object> weekly(
        @AuthenticationPrincipal Identity actor,
        @RequestParam(required = false) LocalDate week
    ) {
        return service.weekly(actor, week);
    }

    @PutMapping("/api/v1/branch-management/shifts/availability")
    public Map<String, Object> publish(
        @AuthenticationPrincipal Identity actor,
        @RequestBody Availability input
    ) {
        return service.publish(actor, input);
    }

    @PostMapping("/api/v1/reception/shifts/{id}/register")
    public Map<String, Object> register(
        @AuthenticationPrincipal Identity actor,
        @PathVariable UUID id
    ) {
        return service.register(actor, id);
    }

    @PostMapping("/api/v1/branch-management/shifts/assignments/{id}/replacement")
    public Map<String, Object> replace(
        @AuthenticationPrincipal Identity actor,
        @PathVariable UUID id,
        @RequestBody Replacement input
    ) {
        return service.replace(actor, id, input);
    }
}
