package com.gcmb.backend.finance.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OperatingPolicyRepository {
    private final JdbcTemplate jdbc;

    public OperatingPolicyRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String,Object>> history() {
        return jdbc.queryForList("""
            SELECT p.id,p.policy_key AS "policyKey",p.amount,p.effective_from::text AS "effectiveFrom",
              p.changed_at AS "changedAt",p.reason,u.username AS "changedBy"
            FROM gcmb.operating_policy p LEFT JOIN gcmb.user_account u ON u.id=p.changed_by
            ORDER BY p.policy_key,p.changed_at DESC,p.id DESC
            """);
    }

    public BigDecimal amountAt(String key, LocalDate date) {
        return jdbc.queryForObject("""
            SELECT amount FROM gcmb.operating_policy WHERE policy_key=? AND effective_from<=?
            ORDER BY effective_from DESC,changed_at DESC,id DESC LIMIT 1
            """, BigDecimal.class, key, date);
    }

    public UUID latestId(String key) {
        return jdbc.queryForObject("""
            SELECT id FROM gcmb.operating_policy WHERE policy_key=?
            ORDER BY changed_at DESC,id DESC LIMIT 1
            """, UUID.class, key);
    }

    public void lock(String key) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))", Object.class, "gcmb.operating_policy." + key);
    }

    public void insert(String key, BigDecimal amount, LocalDate effectiveFrom, UUID actor, String reason) {
        jdbc.update("""
            INSERT INTO gcmb.operating_policy(policy_key,amount,effective_from,changed_by,reason)
            VALUES(?,?,?,?,?)
            """, key, amount, effectiveFrom, actor, reason);
        jdbc.update("""
            INSERT INTO gcmb.audit_event(actor_id,action,entity_type,before_data,after_data,reason)
            VALUES(?,'UPDATE_OPERATING_POLICY','OPERATING_POLICY',
              jsonb_build_object('key',CAST(? AS text)),
              jsonb_build_object('key',CAST(? AS text),'amount',CAST(? AS numeric),'effectiveFrom',CAST(? AS date)),?)
            """, actor, key, key, amount, effectiveFrom, reason);
    }
}
