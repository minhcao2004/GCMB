/** Author: HuyDV. UC-91..97: endpoint kế toán; không gọi chuyển tiền ngân hàng. */
package com.gcmb.backend.workforce.controller;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.workforce.service.PayrollExport;
import com.gcmb.backend.workforce.service.PayrollService;
import com.gcmb.backend.workforce.service.PayrollService.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1")
public class PayrollController {

  private final PayrollService payrollService;

  @GetMapping("/branch-management/payroll/employees")
  public List<Map<String, Object>> branchEmployees(@AuthenticationPrincipal Identity actor) {
    return payrollService.branchEmployees(actor);
  }

  @PostMapping("/branch-management/payroll/hourly-rates")
  public void branchHourly(@AuthenticationPrincipal Identity actor, @RequestBody Hourly input) {
    payrollService.branchHourly(actor, input);
  }

  @GetMapping("/branch-management/payroll/{month}/employees")
  public List<Map<String, Object>> settlementEmployees(@AuthenticationPrincipal Identity actor,
      @PathVariable String month) {
    return payrollService.settlementEmployees(actor, month);
  }

  @GetMapping("/branch-management/payroll/{month}/history")
  public List<Map<String, Object>> branchHistory(@AuthenticationPrincipal Identity actor,
      @PathVariable String month) {
    return payrollService.branchHistory(actor, month);
  }

  @GetMapping("/branch-management/payroll/{month}/summary")
  public List<Map<String, Object>> branchSummary(@AuthenticationPrincipal Identity actor,
      @PathVariable String month) {
    return payrollService.branchSummary(actor, month);
  }

  @PostMapping("/branch-management/payroll/bank-details")
  public void branchBank(@AuthenticationPrincipal Identity actor, @RequestBody BankDetails input) {
    payrollService.branchBank(actor, input);
  }

  @PostMapping("/branch-management/payroll/{month}/{action:preview|submit}")
  public Map<String, Object> branchSettlement(@AuthenticationPrincipal Identity actor,
      @PathVariable String month, @PathVariable String action, @RequestBody BranchSettlement input) {
    return payrollService.branchSettlement(actor, month, input, "submit".equals(action));
  }

  public PayrollController(PayrollService payrollService) {
    this.payrollService = payrollService;
  }

  @PostMapping("/accounting/payroll/{month}/preview")
  public Map<String, Object> preview(
    @AuthenticationPrincipal Identity actor,
    @PathVariable String month,
    @RequestBody Calculate input
  ) {
    return payrollService.preview(actor, month, input);
  }

  @GetMapping("/accounting/payroll/options")
  public Map<String, Object> options(@AuthenticationPrincipal Identity actor) {
    return payrollService.options(actor);
  }

  @PostMapping("/accounting/payroll/settings")
  public void setting(@AuthenticationPrincipal Identity actor, @RequestBody Setting input) {
    payrollService.setting(actor, input);
  }

  @PostMapping("/accounting/payroll/hourly-rates")
  public void hourly(@AuthenticationPrincipal Identity actor, @RequestBody Hourly input) {
    payrollService.hourly(actor, input);
  }

  @PostMapping("/accounting/payroll/calendar")
  public void calendar(
    @AuthenticationPrincipal Identity actor,
    @RequestBody PayrollService.Calendar input
  ) {
    payrollService.calendar(actor, input);
  }

  @GetMapping("/accounting/payroll/{month}")
  public Map<String, Object> view(
    @AuthenticationPrincipal Identity actor,
    @PathVariable String month
  ) {
    return payrollService.view(actor, month);
  }

  @PostMapping("/accounting/payroll/{month}/calculate")
  public Map<String, Object> calculate(
    @AuthenticationPrincipal Identity actor,
    @PathVariable String month,
    @RequestBody Calculate input
  ) {
    return payrollService.calculate(actor, month, input);
  }

  @PostMapping("/accounting/payroll/{month}/adjust")
  public Map<String, Object> adjust(
    @AuthenticationPrincipal Identity actor,
    @PathVariable String month,
    @RequestBody Adjustment input
  ) {
    return payrollService.adjust(actor, month, input);
  }

  @PostMapping("/accounting/payroll/{month}/confirm")
  public Map<String, Object> confirm(
    @AuthenticationPrincipal Identity actor,
    @PathVariable String month,
    @RequestBody Confirm input
  ) {
    return payrollService.confirm(actor, month, input);
  }

  @GetMapping("/accounting/payroll/{month}/available/{employee}")
  public Map<String, Object> available(
    @AuthenticationPrincipal Identity actor,
    @PathVariable String month,
    @PathVariable UUID employee
  ) {
    return payrollService.available(actor, month, employee);
  }

  @PostMapping("/accounting/payroll/{month}/advance")
  public Map<String, Object> advance(
    @AuthenticationPrincipal Identity actor,
    @PathVariable String month,
    @RequestBody Pay input
  ) {
    return payrollService.advance(actor, month, input);
  }

  @PostMapping(value="/accounting/payroll/{month}/payments", consumes=MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> pay(
    @AuthenticationPrincipal Identity actor,
    @PathVariable String month,
    @RequestBody Payments input
  ) {
    return payrollService.pay(actor, month, input);
  }

  @PostMapping(value="/accounting/payroll/{month}/payments", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  public Map<String, Object> payWithEvidence(@AuthenticationPrincipal Identity actor,
      @PathVariable String month, @RequestPart Payments command, MultipartHttpServletRequest request) {
    return payrollService.pay(actor, month, command, request.getFileMap());
  }

  @GetMapping("/accounting/payroll/payments/{id}/evidence")
  public ResponseEntity<byte[]> paymentEvidence(@AuthenticationPrincipal Identity actor, @PathVariable UUID id) {
    var file = payrollService.paymentEvidence(actor, id);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
      .header("X-Content-Type-Options", "nosniff")
      .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
        .filename((String) file.get("original_name"), StandardCharsets.UTF_8).build().toString())
      .contentType(MediaType.parseMediaType((String) file.get("mime_type")))
      .body((byte[]) file.get("bytes"));
  }

  @GetMapping("/accounting/payroll/{month}/export")
  public ResponseEntity<byte[]> export(
    @AuthenticationPrincipal Identity actor,
    @PathVariable String month,
    @RequestParam(required = false) UUID branchId
  ) {
    return ResponseEntity.ok()
      .cacheControl(CacheControl.noStore())
      .header("Content-Disposition", "attachment; filename=payroll-" + month + ".xlsx")
      .contentType(
        MediaType.parseMediaType(
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
      )
      .body(PayrollExport.xlsx(payrollService.export(actor, month, branchId)));
  }
}
