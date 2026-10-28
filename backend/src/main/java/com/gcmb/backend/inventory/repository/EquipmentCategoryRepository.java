/**
 * Copyright (c) 2026 SEP490-G12.
 * Author: TrongNV
 * Description: Truy vấn nhóm thiết bị và số asset tham chiếu trong phạm vi cơ sở.
 */
package com.gcmb.backend.inventory.repository;

import com.gcmb.backend.inventory.dto.EquipmentCategoryDtos.Command;
import com.gcmb.backend.inventory.dto.EquipmentCategoryDtos.View;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EquipmentCategoryRepository {
    private final JdbcTemplate db;
    // BR-111: Repository chỉ nhận truy vấn JDBC, quy tắc bắt buộc được xác thực ở service.
    public EquipmentCategoryRepository(JdbcTemplate db) { this.db = db; }

    // BR-111: Trả về cả số thiết bị đang tham chiếu để người dùng biết tác động khi xóa.
    private View map(ResultSet r, int row) throws SQLException {
        return new View(r.getObject("id", UUID.class), r.getObject("branch_id", UUID.class), r.getString("code"),
            r.getString("name"), r.getString("description"), r.getBoolean("active"), r.getLong("asset_count"),
            r.getTimestamp("created_at").toInstant().atOffset(java.time.ZoneOffset.UTC));
    }

    private static final String SELECT = "SELECT c.*, (SELECT count(*) FROM gcmb.asset a WHERE a.category_id=c.id) asset_count FROM gcmb.asset_category c ";

    // UC-61: Chỉ truy vấn danh mục thuộc cơ sở được truyền vào, hỗ trợ tìm kiếm và lọc trạng thái.
    public List<View> list(UUID branchId, String search, Boolean active) {
        return db.query(SELECT + "WHERE c.branch_id=? AND (CAST(? AS boolean) IS NULL OR c.active=?) AND (?='' OR c.code ILIKE '%'||?||'%' OR c.name ILIKE '%'||?||'%') ORDER BY c.code",
            this::map, branchId, active, active, search, search, search);
    }

    // UC-60: Ghi danh mục vào đúng cơ sở và để ràng buộc UNIQUE(branch_id, code) chặn trùng mã.
    public View create(UUID branchId, Command command) {
        UUID id = db.queryForObject("INSERT INTO gcmb.asset_category(branch_id,code,name,description,active) VALUES(?,?,?,?,true) RETURNING id",
            UUID.class, branchId, command.code().trim(), command.name().trim(), command.description().trim());
        return get(branchId, id);
    }

    // UC-61/62/63: Lấy một danh mục chỉ khi nó thuộc đúng phạm vi cơ sở.
    public View get(UUID branchId, UUID id) {
        List<View> rows = db.query(SELECT + "WHERE c.branch_id=? AND c.id=?", this::map, branchId, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // UC-62: Cập nhật tên/mã trong cùng cơ sở; tài sản đang tham chiếu không bị cập nhật.
    public View update(UUID branchId, UUID id, Command command) {
        int changed = db.update("UPDATE gcmb.asset_category SET code=?,name=?,description=? WHERE branch_id=? AND id=?",
            command.code().trim(), command.name().trim(), command.description().trim(), branchId, id);
        return changed == 0 ? null : get(branchId, id);
    }

    // UC-63: Chỉ đổi cờ hoạt động, giữ nguyên nhóm và toàn bộ asset liên quan.
    public View setActive(UUID branchId, UUID id, boolean active) {
        int changed = db.update("UPDATE gcmb.asset_category SET active=? WHERE branch_id=? AND id=?", active, branchId, id);
        return changed == 0 ? null : get(branchId, id);
    }

    // BR-118/BR-191: Từ chối xóa nếu có asset tham chiếu; không xóa hoặc đổi dữ liệu lịch sử.
    public boolean deleteUnused(UUID branchId, UUID id) {
        return db.update("DELETE FROM gcmb.asset_category c WHERE c.branch_id=? AND c.id=? AND NOT EXISTS (SELECT 1 FROM gcmb.asset a WHERE a.category_id=c.id)", branchId, id) > 0;
    }
}
