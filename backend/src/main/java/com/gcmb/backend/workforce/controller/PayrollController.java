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

@RestController
@RequestMapping("/api/v1")
public class PayrollController {

  private final PayrollService service;

  @GetMapping("/branch-management/payroll/employees")
  public List<Map<String, Object>> branchEmployees(@AuthenticationPrincipal Identity actor) {
    return service.branchEmployees(actor);
  }

  @PostMapping("/branch-management/payroll/hourly-rates")
  public void branchHourly(@AuthenticationPrincipal Identity actor, @RequestBody Hourly input) {
    service.branchHourly(actor, input);
  }

  @PostMapping("/branch-management/payroll/{month}/{action:preview|submit}")
  public Map<String, Object> branchSettlement(@AuthenticationPrincipal Identity actor,
      @PathVariable String month, @PathVariable String action, @RequestBody BranchSettlement input) {
    return service.branchSettlement(actor, month, input, "submit".equals(action));
  }

  public PayrollController(PayrollService service) {
    this.service = service;
  }

  @PostMapping("/accounting/payroll/{month}/preview")
  public Map<String, Object> preview(
    @AuthenticationPrincipal Identity actor,
    @PathVariable String month,
    @RequestBody Calculate input
  ) {
    return service.preview(actor, month, input);
  }

  @GetMapping("/accounting/payroll/options")
  public Map<String, Object> options(@AuthenticationPrincipal Identity actor) {
    return service.options(actor);
  }

  @PostMapping("/accounting/payroll/settings")
  public void setting(@AuthenticationPrincipal Identity actor, @RequestBody Setting input) {
    service.setting(actor, input);
  }

  @PostMapping("/accounting/payroll/hourly-rates")
  public void hourly(@AuthenticationPrincipal Identity actor, @RequestBody Hourly input) {
    service.hourly(actor, input);
  }

  @PostMapping("/accounting/payroll/calendar")
  public void calendar(
    @AuthenticationPrincipal Identity actor,
    @RequestBody PayrollService.Calendar input
  ) {
    service.calendar(actor, input);
  }

  @GetMapping("/accounting/payroll/{month}")
  public Map<String, Object> view(
    @AuthenticationPrincipal Identity actor,
    @PathVariable String month
  ) {
    return service.view(actor, month);
  }

  @PostMapping("/accounting/payroll/{month}/calculate")
  public Map<String, Object> calculate(
    @AuthenticationPrincipal Identity actor,
    @PathVariable String month,
    @RequestBody Calculate input
  ) {
    return service.calculate(actor, month, input);
  }

  @PostMapping("/accounting/payroll/{month}/adjust")
  public Map<String, Object> adjust(
    @AuthenticationPrincipal Identity actor,
    @PathVariable String month,
    @RequestBody Adjustment input
  ) {
    return service.adjust(actor, month, input);
  }

  @PostMapping("/accounting/payroll/{month}/confirm")
  public Map<String, Object> confirm(
    @AuthenticationPrincipal Identity actor,
    @PathVariable String month,
    @RequestBody Confirm input
  ) {
    return service.confirm(actor, month, input);
  }

  @GetMapping("/accounting/payroll/{month}/available/{employee}")
  public Map<String, Object> available(
    @AuthenticationPrincipal Identity actor,
    @PathVariable String month,
    @PathVariable UUID employee
  ) {
    return service.available(actor, month, employee);
  }

  @PostMapping("/accounting/payroll/{month}/advance")
  public Map<String, Object> advance(
    @AuthenticationPrincipal Identity actor,
    @PathVariable String month,
    @RequestBody Pay input
  ) {
    return service.advance(actor, month, input);
  }

  @PostMapping("/accounting/payroll/{month}/payments")
  public Map<String, Object> pay(
    @AuthenticationPrincipal Identity actor,
    @PathVariable String month,
    @RequestBody Payments input
  ) {
    return service.pay(actor, month, input);
  }

  @GetMapping("/accounting/payroll/{month}/export")
  public ResponseEntity<byte[]> export(
    @AuthenticationPrincipal Identity actor,
    @PathVariable String month
  ) {
    return ResponseEntity.ok()
      .cacheControl(CacheControl.noStore())
      .header("Content-Disposition", "attachment; filename=payroll-" + month + ".xlsx")
      .contentType(
        MediaType.parseMediaType(
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
      )
      .body(PayrollExport.xlsx(service.export(actor, month)));
  }
}
