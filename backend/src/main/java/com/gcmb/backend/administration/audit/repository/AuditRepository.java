/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: Truy vấn nhật ký theo ngày, người thực hiện, đối tượng, hành động và cơ sở.
 */
package com.gcmb.backend.administration.audit.repository;

import com.gcmb.backend.administration.audit.dto.AuditDtos.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditRepository {
    private final JdbcTemplate db;
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String FROM = """
            FROM gcmb.audit_event a
            LEFT JOIN gcmb.user_account actor ON actor.id=a.actor_id
            LEFT JOIN gcmb.employee employee ON employee.user_id=actor.id
            LEFT JOIN gcmb.branch b ON b.id=a.branch_id
            LEFT JOIN gcmb.user_account target ON target.id=a.entity_id AND a.entity_type='USER_ACCOUNT'
            """;
    private static final String SELECT = """
            SELECT a.id,a.created_at,a.action,a.entity_type,a.entity_id,a.reason,
                COALESCE(employee.full_name,actor.username,'Hệ thống') AS actor_name,
                actor.username AS actor_username,
                COALESCE(a.after_data->>'username',target.username,CAST(a.entity_id AS text),'—') AS entity_name,
                COALESCE(b.name,'Toàn chuỗi') AS branch_name
            """;

    public AuditRepository(JdbcTemplate db) {
        this.db = db;
    }

    private static AuditEntry entry(ResultSet rs, int row) throws SQLException {
        return new AuditEntry(
                rs.getObject("id", UUID.class),
                rs.getTimestamp("created_at").toInstant(),
                rs.getString("actor_name"),
                rs.getString("actor_username"),
                rs.getString("action"),
                rs.getString("entity_type"),
                rs.getObject("entity_id", UUID.class),
                rs.getString("entity_name"),
                rs.getString("branch_name"),
                rs.getString("reason")
        );
    }

    public AuditPage list(
            String q,
            String action,
            UUID branchId,
            LocalDate from,
            LocalDate to,
            int page,
            int size) {
        StringBuilder where = new StringBuilder("""
                WHERE strpos(lower(concat_ws(' ',employee.full_name,actor.username,
                    target.username,CAST(a.entity_id AS text),a.after_data->>'username')),lower(?))>0
                """);
        List<Object> args = new ArrayList<>();
        args.add(q);
        if (!action.isEmpty()) {
            where.append(" AND a.action=?");
            args.add(action);
        }
        if (branchId != null) {
            where.append(" AND a.branch_id=?");
            args.add(branchId);
        }
        // Khoảng ngày theo giờ Việt Nam, gồm toàn bộ ngày kết thúc.
        if (from != null) {
            where.append(" AND a.created_at>=?");
            args.add(Timestamp.from(from.atStartOfDay(ZONE).toInstant()));
        }
        if (to != null) {
            where.append(" AND a.created_at<?");
            args.add(Timestamp.from(to.plusDays(1).atStartOfDay(ZONE).toInstant()));
        }
        long total = db.queryForObject(
                "SELECT count(*) " + FROM + where,
                Long.class,
                args.toArray()
        );
        args.add(size);
        args.add((long) page * size);
        List<AuditEntry> items = db.query(
                SELECT + FROM + where + " ORDER BY a.created_at DESC,a.id DESC LIMIT ? OFFSET ?",
                AuditRepository::entry,
                args.toArray()
        );
        return new AuditPage(items, total, page, size);
    }

    public List<String> actions() {
        return db.queryForList("SELECT DISTINCT action FROM gcmb.audit_event ORDER BY action", String.class);
    }

    public AuditDetail detail(UUID id) {
        // Không trả nguyên JSON lịch sử: chỉ chiếu các trường tài khoản được phép hiển thị.
        String snapshots = """
                ,a.before_data->>'username' AS before_username,
                a.before_data->>'role' AS before_role,
                a.before_data->>'branchName' AS before_branch,
                a.before_data->>'status' AS before_status,
                a.after_data->>'username' AS after_username,
                a.after_data->>'role' AS after_role,
                a.after_data->>'branchName' AS after_branch,
                a.after_data->>'status' AS after_status,
                a.before_data->>'fullName' AS before_name,
                a.before_data->>'email' AS before_email,
                a.before_data->>'phone' AS before_phone,
                a.after_data->>'fullName' AS after_name,
                a.after_data->>'email' AS after_email,
                a.after_data->>'phone' AS after_phone
                """;
        List<AuditDetail> result = db.query(
                SELECT + snapshots + FROM + " WHERE a.id=?",
                (rs, row) -> new AuditDetail(
                        entry(rs, row),
                        new Snapshot(rs.getString("before_username"), rs.getString("before_role"),
                                rs.getString("before_branch"), rs.getString("before_status"),
                                rs.getString("before_name"), rs.getString("before_email"), rs.getString("before_phone")),
                        new Snapshot(rs.getString("after_username"), rs.getString("after_role"),
                                rs.getString("after_branch"), rs.getString("after_status"),
                                rs.getString("after_name"), rs.getString("after_email"), rs.getString("after_phone"))
                ),
                id
        );
        return result.isEmpty() ? null : result.get(0);
    }
}
