/** Author: HuyDV. QLCS requests drawer cash; only accountant can confirm payment. */
package com.gcmb.backend.finance.controller;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.finance.service.DrawerFundingService;
import com.gcmb.backend.finance.service.DrawerFundingService.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class DrawerFundingController {
  private final DrawerFundingService service;
  public DrawerFundingController(DrawerFundingService service) { this.service=service; }

  @GetMapping({"/api/v1/branch-management/cash-closing/funding","/api/v1/accounting/cash-closing/funding"})
  public Map<String,Object> overview(@AuthenticationPrincipal Identity actor,@RequestParam(required=false) UUID branchId) {
    return service.overview(actor,branchId);
  }
  @PostMapping("/api/v1/branch-management/cash-closing/funding")
  public Map<String,Object> request(@AuthenticationPrincipal Identity actor,@RequestBody Request input) {
    return service.request(actor,input);
  }
  @PostMapping(value="/api/v1/accounting/cash-closing/funding/{id}/pay",consumes=MediaType.APPLICATION_JSON_VALUE)
  public Map<String,Object> pay(@AuthenticationPrincipal Identity actor,@PathVariable UUID id,@RequestBody Payment input) {
    return service.pay(actor,id,input);
  }
  @PostMapping(value="/api/v1/accounting/cash-closing/funding/{id}/pay",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  public Map<String,Object> payWithEvidence(@AuthenticationPrincipal Identity actor,@PathVariable UUID id,
      @RequestPart Payment command,@RequestPart(required=false) MultipartFile proof) {
    return service.pay(actor,id,command,proof);
  }
  @PostMapping({"/api/v1/branch-management/cash-closing/funding/{id}/recipient",
      "/api/v1/accounting/cash-closing/funding/{id}/recipient"})
  public Map<String,Object> completeRecipient(@AuthenticationPrincipal Identity actor,@PathVariable UUID id) {
    return service.completeRecipient(actor,id);
  }
  @GetMapping({"/api/v1/branch-management/cash-closing/funding/{id}/evidence",
      "/api/v1/accounting/cash-closing/funding/{id}/evidence"})
  public ResponseEntity<byte[]> evidence(@AuthenticationPrincipal Identity actor,@PathVariable UUID id) {
    var file=service.evidence(actor,id);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
      .header("X-Content-Type-Options","nosniff")
      .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment()
        .filename((String)file.get("original_name"),StandardCharsets.UTF_8).build().toString())
      .contentType(MediaType.parseMediaType((String)file.get("mime_type")))
      .body((byte[])file.get("bytes"));
  }
  @PostMapping("/api/v1/accounting/cash-closing/funding/{id}/reject")
  public Map<String,Object> reject(@AuthenticationPrincipal Identity actor,@PathVariable UUID id,@RequestBody Payment input) {
    return service.reject(actor,id,input);
  }
}
