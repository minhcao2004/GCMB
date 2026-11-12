/** Author: HuyDV. Lễ tân ca trước giao, ca sau nhận; QLCS xử lý chênh lệch ở cả hai bước. */
package com.gcmb.backend.finance.controller;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.finance.service.ShiftCashHandoverService;
import java.time.LocalDate;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class ShiftCashHandoverController {
  private final ShiftCashHandoverService service;
  public ShiftCashHandoverController(ShiftCashHandoverService service) { this.service=service; }

  @GetMapping({"/api/v1/reception/cash-handovers","/api/v1/branch-management/cash-handovers"})
  public Map<String,Object> overview(@AuthenticationPrincipal Identity actor,
      @RequestParam(required=false) LocalDate date) {
    return service.overview(actor,date);
  }

  @PostMapping("/api/v1/reception/cash-handovers")
  public Map<String,Object> receive(@AuthenticationPrincipal Identity actor,
      @RequestBody ShiftCashHandoverService.Receive command) {
    return service.receive(actor,command);
  }

  @PostMapping("/api/v1/reception/cash-handovers/dispatch")
  public Map<String,Object> dispatch(@AuthenticationPrincipal Identity actor,
      @RequestBody ShiftCashHandoverService.Receive command) {
    return service.dispatch(actor,command);
  }

  @PostMapping("/api/v1/branch-management/cash-handovers/{id}/resolve")
  public Map<String,Object> resolve(@AuthenticationPrincipal Identity actor,@PathVariable UUID id,
      @RequestBody ShiftCashHandoverService.Resolution command) {
    return service.resolve(actor,id,command);
  }
}
