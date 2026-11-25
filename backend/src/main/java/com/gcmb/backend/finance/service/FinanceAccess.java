/** Author: HuyDV. UC-91..101: quyền, kiểm tra tiền và nhật ký nghiệp vụ. */
package com.gcmb.backend.finance.service;

import com.gcmb.backend.accounts.repository.AccountRepository;
import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.exception.AuthFailure;
import java.math.BigDecimal;
import java.util.*;
import com.gcmb.backend.finance.repository.FinanceAccessRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class FinanceAccess {

  private final FinanceAccessRepository financeAccessRepository;
  private final AccountRepository accountRepository;
  private final ObjectMapper objectMapper;

  public FinanceAccess(FinanceAccessRepository financeAccessRepository, AccountRepository accountRepository, ObjectMapper objectMapper) {
    this.financeAccessRepository = financeAccessRepository;
    this.accountRepository = accountRepository;
    this.objectMapper = objectMapper;
  }

  public void accountant(Identity actor) {
    role(actor, "ACCOUNTANT");
  }

  public void role(Identity actor, String role) {
    if (
      actor == null ||
      !role.equals(actor.role()) ||
      !financeAccessRepository.hasActiveRole(actor.id(), role)
    ) throw new AuthFailure(403, "form", "Bạn không có quyền thực hiện thao tác này."); // MSG24.
  }

  public UUID branch(Identity actor, boolean write) {
    role(actor, "BRANCH_MANAGER");
    if (
      actor.branchId() == null || !accountRepository.manages(actor.id(), actor.branchId(), false)
    ) throw new AuthFailure(403, "form", "Bạn không có quyền xem đối với cơ sở này.");
    // HuyDV / BR-177: chỉ thao tác ghi cần giữ khóa cơ sở; FOR SHARE bị cấm trong transaction chỉ đọc.
    var branch = financeAccessRepository.findBranchStatus(actor.branchId(), write);
    if (write && !"ACTIVE".equals(branch.get("status"))) throw new AuthFailure(
      403,
      "form",
      "Cơ sở " +
        branch.get("name") +
        " đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi."
    ); // BR-177 / MSG43.
    return actor.branchId();
  }

  public void lock() {
    financeAccessRepository.lockAccountantFund();
  }

  public void audit(
    Identity actor,
    UUID branchId,
    UUID entityId,
    String action,
    Object before,
    Object after
  ) {
    financeAccessRepository.insertFinanceAudit(
      actor.id(),
      branchId,
      action,
      entityId,
      objectMapper.writeValueAsString(before),
      objectMapper.writeValueAsString(after)
    );
  }

  public static BigDecimal money(BigDecimal value, String field, boolean positive) {
    if (value == null) throw bad("Vui lòng bổ sung " + field + ".");
    if (
      (value.scale() > 0 && value.stripTrailingZeros().scale() > 0) ||
      value.signum() < 0 ||
      (positive && value.signum() == 0) ||
      value.compareTo(new BigDecimal("999999999999999")) > 0
    ) throw bad(
      field + " phải là số nguyên " + (positive ? "lớn hơn 0" : "không âm") + ", tối đa 15 chữ số."
    );
    return value.setScale(0);
  }

  public static String text(String value, String field, boolean required) {
    String result = value == null ? "" : value.trim();
    if (required && result.isEmpty()) throw bad("Vui lòng bổ sung " + field + "."); // MSG02.
    if (result.length() > 2000) throw bad(field + " chỉ được chứa tối đa 2000 ký tự."); // MSG03.
    return result;
  }

  public static AuthFailure bad(String message) {
    return new AuthFailure(400, "form", message);
  }

  public static AuthFailure conflict(String message) {
    return new AuthFailure(409, "form", message);
  }
}
