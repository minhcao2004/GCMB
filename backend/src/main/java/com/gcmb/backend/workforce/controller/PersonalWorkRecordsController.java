/**
 * Author: HuyDV
 * Created: 03/11/2026
 * Description: UC-90 / BR-60/61, lễ tân chỉ xem lịch và công của chính mình.
 */
package com.gcmb.backend.workforce.controller;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.workforce.service.AttendanceService;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PersonalWorkRecordsController {
    private final AttendanceService service;

    public PersonalWorkRecordsController(AttendanceService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/reception/work-records")
    public Map<String, Object> personal(
        @AuthenticationPrincipal Identity actor,
        @RequestParam(required = false) String month
    ) {
        return service.personal(actor, month);
    }
}
