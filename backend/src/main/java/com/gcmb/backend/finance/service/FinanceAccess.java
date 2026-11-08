/** Author: HuyDV. UC-91..101: quyền, kiểm tra tiền và nhật ký nghiệp vụ. */
package com.gcmb.backend.finance.service;

import com.gcmb.backend.accounts.repository.AccountRepository;
import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.exception.AuthFailure;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class FinanceAccess {

  private final JdbcTemplate db;
  private final AccountRepository accounts;
  private final ObjectMapper json;

  public FinanceAccess(JdbcTemplate db, AccountRepository accounts, ObjectMapper json) {
    this.db = db;
    this.accounts = accounts;
    this.json = json;
  }

  public void accountant(Identity actor) {
    role(actor, "ACCOUNTANT");
  }

  public void role(Identity actor, String role) {
    if (
      actor == null ||
      !role.equals(actor.role()) ||
      !Boolean.TRUE.equals(
        db.queryForObject(
          "SELECT EXISTS(SELECT 1 FROM gcmb.user_account u JOIN gcmb.role r ON r.id=u.role_id WHERE u.id=? AND u.status='ACTIVE' AND r.code=?)",
          Boolean.class,
          actor.id(),
          role
        )
      )
    ) throw new AuthFailure(403, "form", "Bạn không có quyền thực hiện thao tác này."); // MSG24.
  }

  public UUID branch(Identity actor, boolean write) {
    role(actor, "BRANCH_MANAGER");
    if (
      actor.branchId() == null || !accounts.manages(actor.id(), actor.branchId(), false)
    ) throw new AuthFailure(403, "form", "Bạn không có quyền xem đối với cơ sở này.");
    var branch = db.queryForMap(
      "SELECT name,status FROM gcmb.branch WHERE id=? FOR SHARE",
      actor.branchId()
    );
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
    db.execute("SELECT pg_advisory_xact_lock(1300261103)");
  }

  public void audit(
    Identity actor,
    UUID branch,
    UUID id,
    String action,
    Object before,
    Object after
  ) {
    db.update(
      "INSERT INTO gcmb.audit_event(actor_id,branch_id,action,entity_type,entity_id,before_data,after_data) VALUES (?,?,?,'FINANCE',?,CAST(? AS jsonb),CAST(? AS jsonb))",
      actor.id(),
      branch,
      action,
      id,
      json.writeValueAsString(before),
      json.writeValueAsString(after)
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
