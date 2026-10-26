package com.gcmb.backend.inventory.repository;

import com.gcmb.backend.inventory.dto.MenuItemDtos.MenuItemView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MenuItemRepository {
    private static final String SELECT = """
        SELECT m.id,m.code,m.name,m.category,m.kind,m.inventory_item_id,
               i.sku AS inventory_sku,i.name AS inventory_name,
               sb.quantity AS stock_quantity,sb.minimum_quantity,u.name AS unit_name,
               m.sale_price,m.active,
               CASE WHEN m.inventory_item_id IS NULL THEN 'UNTRACKED'
                    WHEN sb.quantity IS NULL OR sb.quantity<=0 THEN 'OUT_OF_STOCK'
                    WHEN sb.quantity<=sb.minimum_quantity THEN 'LOW_STOCK'
                    ELSE 'AVAILABLE' END AS stock_status
        FROM gcmb.menu_item m
        LEFT JOIN gcmb.inventory_item i ON i.id=m.inventory_item_id AND i.branch_id=m.branch_id
        LEFT JOIN gcmb.stock_balance sb ON sb.inventory_item_id=i.id
        LEFT JOIN gcmb.unit u ON u.id=i.unit_id
        """;

    private final JdbcTemplate db;

    public MenuItemRepository(JdbcTemplate db) { this.db = db; }

    public List<MenuItemView> list(UUID branchId) {
        return db.query(SELECT + " WHERE m.branch_id=? AND m.deleted_at IS NULL ORDER BY lower(m.name),m.code",
            this::map, branchId);
    }

    public List<String> categories(UUID branchId) {
        return db.queryForList("""
            SELECT category FROM gcmb.menu_item
            WHERE branch_id=? AND deleted_at IS NULL
            GROUP BY category ORDER BY lower(category)
            """, String.class, branchId);
    }

    public MenuItemView changeStatus(UUID branchId, UUID id, boolean active) {
        int changed = db.update("""
            UPDATE gcmb.menu_item SET active=?
            WHERE id=? AND branch_id=? AND deleted_at IS NULL
            """, active, id, branchId);
        return changed == 0 ? null : get(branchId, id);
    }

    public boolean softDelete(UUID branchId, UUID id, UUID actorId) {
        return db.update("""
            UPDATE gcmb.menu_item
            SET active=false,deleted_at=CURRENT_TIMESTAMP,deleted_by=?
            WHERE id=? AND branch_id=? AND deleted_at IS NULL
            """, actorId, id, branchId) > 0;
    }

    private MenuItemView get(UUID branchId, UUID id) {
        List<MenuItemView> rows = db.query(SELECT + " WHERE m.branch_id=? AND m.id=? AND m.deleted_at IS NULL",
            this::map, branchId, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private MenuItemView map(ResultSet r, int row) throws SQLException {
        return new MenuItemView(r.getObject("id", UUID.class), r.getString("code"), r.getString("name"),
            r.getString("category"), r.getString("kind"), r.getObject("inventory_item_id", UUID.class),
            r.getString("inventory_sku"), r.getString("inventory_name"), r.getBigDecimal("stock_quantity"),
            r.getBigDecimal("minimum_quantity"), r.getString("unit_name"), r.getBigDecimal("sale_price"),
            r.getBoolean("active"), r.getString("stock_status"));
    }
}
