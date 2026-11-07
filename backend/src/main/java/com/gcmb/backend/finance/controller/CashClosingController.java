/** Author: HuyDV. UC-98..101: API chốt quỹ, xem minh chứng và đối soát. */
package com.gcmb.backend.finance.controller;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.finance.service.CashClosingService;
import com.gcmb.backend.finance.service.CashClosingService.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class CashClosingController {

  private final CashClosingService service;

  public CashClosingController(CashClosingService service) {
    this.service = service;
  }

  @GetMapping("/api/v1/branch-management/cash-closing/preview")
  public Map<String, Object> preview(
    @AuthenticationPrincipal Identity actor,
    @RequestParam(required = false) LocalDate date
  ) {
    return service.preview(actor, date);
  }

  @PostMapping(
    value = "/api/v1/branch-management/cash-closing",
    consumes = MediaType.MULTIPART_FORM_DATA_VALUE
  )
  public Map<String, Object> submit(
    @AuthenticationPrincipal Identity actor,
    @RequestPart Submit command,
    @RequestPart(required = false) MultipartFile evidence
  ) {
    return service.submit(actor, command, evidence);
  }

  @GetMapping({ "/api/v1/branch-management/cash-closing", "/api/v1/accounting/cash-closing" })
  public List<Map<String, Object>> list(
    @AuthenticationPrincipal Identity actor,
    @RequestParam(required = false) LocalDate from,
    @RequestParam(required = false) LocalDate to,
    @RequestParam(required = false) UUID branchId,
    @RequestParam(required = false) String status,
    @RequestParam(defaultValue = "false") boolean alerts
  ) {
    return service.list(actor, from, to, branchId, status, alerts);
  }

  @GetMapping({
    "/api/v1/branch-management/cash-closing/{id}",
    "/api/v1/accounting/cash-closing/{id}",
  })
  public Map<String, Object> detail(
    @AuthenticationPrincipal Identity actor,
    @PathVariable UUID id
  ) {
    return service.detail(actor, id);
  }

  @PostMapping("/api/v1/accounting/cash-closing/{id}/reconcile")
  public Map<String, Object> reconcile(
    @AuthenticationPrincipal Identity actor,
    @PathVariable UUID id,
    @RequestBody Review body
  ) {
    return service.reconcile(actor, id, body);
  }

  @GetMapping({
    "/api/v1/branch-management/cash-closing/{id}/evidence",
    "/api/v1/accounting/cash-closing/{id}/evidence",
  })
  public ResponseEntity<byte[]> evidence(
    @AuthenticationPrincipal Identity actor,
    @PathVariable UUID id
  ) {
    var value = service.evidence(actor, id);
    return ResponseEntity.ok()
      .cacheControl(CacheControl.noStore())
      .contentType(MediaType.parseMediaType((String) value.get("mime_type")))
      .header("X-Content-Type-Options", "nosniff")
      .header(
        "Content-Disposition",
        ContentDisposition.attachment()
          .filename((String) value.get("original_name"), java.nio.charset.StandardCharsets.UTF_8)
          .build()
          .toString()
      )
      .body((byte[]) value.get("bytes"));
  }
}
