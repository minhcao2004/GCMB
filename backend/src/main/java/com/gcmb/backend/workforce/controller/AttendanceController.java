/**
 * Author: HuyDV
 * Created: 27/10/2026
 * Description: UC-87..89, API chấm công và bảng công tháng của quản lý cơ sở.
 */
package com.gcmb.backend.workforce.controller;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.workforce.dto.AttendanceDtos.*;
import com.gcmb.backend.workforce.service.AttendanceService;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/branch-management/attendance")
public class AttendanceController {

    private final AttendanceService service;

    public AttendanceController(AttendanceService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> daily(
        @AuthenticationPrincipal Identity actor,
        @RequestParam(required = false) LocalDate date
    ) {
        return service.daily(actor, date);
    }

    @GetMapping("/month")
    public Map<String, Object> monthly(
        @AuthenticationPrincipal Identity actor,
        @RequestParam(required = false) String month
    ) {
        return service.monthly(actor, month);
    }

    @PostMapping("/{assignmentId}/record")
    public Map<String, Object> record(
        @AuthenticationPrincipal Identity actor,
        @PathVariable UUID assignmentId,
        @RequestBody RecordCommand input
    ) {
        return service.record(actor, assignmentId, input);
    }

    @PostMapping("/month/{month}/close")
    public Map<String, Object> close(
        @AuthenticationPrincipal Identity actor,
        @PathVariable String month,
        @RequestBody CloseCommand input
    ) {
        return service.close(actor, month, input);
    }
}
