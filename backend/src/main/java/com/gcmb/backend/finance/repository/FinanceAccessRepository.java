package com.gcmb.backend.finance.repository;

import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
// Kiểm tra quyền tài khoản, trạng thái cơ sở và ghi nhật ký nghiệp vụ tài chính.
public class FinanceAccessRepository {

    private final JdbcTemplate jdbcTemplate;

    public FinanceAccessRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean hasActiveRole(UUID userId, String roleCode) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM gcmb.user_account u JOIN gcmb.role r ON r.id=u.role_id WHERE u.id=? AND u.status='ACTIVE' AND r.code=?)",
            Boolean.class, userId, roleCode));
    }

    public Map<String, Object> findBranchStatus(UUID branchId, boolean lockForShare) {
        return jdbcTemplate.queryForMap(
            "SELECT name,status FROM gcmb.branch WHERE id=?" + (lockForShare ? " FOR SHARE" : ""), branchId);
    }

    public void lockAccountantFund() {
        AccountantFund.lock(jdbcTemplate);
    }

    public void insertFinanceAudit(UUID actorId, UUID branchId, String action, UUID entityId, String beforeJson, String afterJson) {
        jdbcTemplate.update(
            "INSERT INTO gcmb.audit_event(actor_id,branch_id,action,entity_type,entity_id,before_data,after_data) VALUES (?,?,?,'FINANCE',?,CAST(? AS jsonb),CAST(? AS jsonb))",
            actorId, branchId, action, entityId, beforeJson, afterJson);
    }
}
