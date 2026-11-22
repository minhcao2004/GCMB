package com.gcmb.backend.dashboard;

import com.gcmb.backend.auth.dto.Identity;
import java.util.*;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/head-office/business-assistant")
public class BusinessAssistantController {
  private final BusinessAssistantService service;
  public BusinessAssistantController(BusinessAssistantService service) { this.service = service; }
  @GetMapping("/context")
  public ResponseEntity<Map<String, Object>> context(@AuthenticationPrincipal Identity actor,
      @RequestParam String month, @RequestParam(required = false) UUID branchId) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.overview(actor, month, branchId));
  }
  @PostMapping("/chat")
  public ResponseEntity<Map<String, Object>> chat(@AuthenticationPrincipal Identity actor,
      @RequestBody BusinessAssistantService.Question input) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.chat(actor, input));
  }
}
