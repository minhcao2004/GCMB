package com.gcmb.backend.inventory.repository;

import com.gcmb.backend.inventory.dto.MenuItemDtos.MenuItemView;
import com.gcmb.backend.inventory.dto.MenuItemDtos.MenuItemCommand;
import com.gcmb.backend.inventory.dto.MenuItemDtos.InventoryOption;
import com.gcmb.backend.inventory.dto.MenuItemDtos.PublicMenuItemView;
import com.gcmb.backend.inventory.dto.MenuItemDtos.ImageAsset;
import com.gcmb.backend.inventory.dto.MenuItemDtos.StoredImage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MenuItemRepository {
    private static final String SELECT = """
        SELECT m.id,m.code,m.name,
               (SELECT c.category FROM gcmb.menu_item c
                WHERE c.branch_id=m.branch_id AND c.deleted_at IS NULL AND lower(c.category)=lower(m.category)
                ORDER BY (c.category <> lower(c.category)) DESC,c.category LIMIT 1) AS category,
               m.kind,m.inventory_item_id,
               i.sku AS inventory_sku,i.name AS inventory_name,
               sb.quantity AS stock_quantity,sb.minimum_quantity,u.name AS unit_name,
               m.sale_price,m.active,m.description,m.image_original_name,m.image_mime_type,
               CASE WHEN m.image_object_key IS NULL THEN NULL
                    ELSE '/branch-management/menu-items/' || m.id || '/image' END AS image_url,
               CASE WHEN m.inventory_item_id IS NULL THEN 'UNTRACKED'
                    WHEN sb.quantity IS NULL OR sb.quantity<=0 THEN 'OUT_OF_STOCK'
                    WHEN sb.quantity<=sb.minimum_quantity THEN 'LOW_STOCK'
                    ELSE 'AVAILABLE' END AS stock_status
        FROM gcmb.menu_item m
        LEFT JOIN gcmb.inventory_item i ON i.id=m.inventory_item_id AND i.branch_id=m.branch_id
        LEFT JOIN gcmb.stock_balance sb ON sb.inventory_item_id=i.id
        LEFT JOIN gcmb.unit u ON u.id=i.unit_id
        """;

    private final JdbcTemplate jdbcTemplate;

    public MenuItemRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public List<MenuItemView> list(UUID branchId) {
        return jdbcTemplate.query(SELECT + " WHERE m.branch_id=? AND m.deleted_at IS NULL ORDER BY lower(m.name),m.code",
            this::map, branchId);
    }

    public MenuItemView get(UUID branchId, UUID id) {
        List<MenuItemView> rows = jdbcTemplate.query(SELECT + " WHERE m.branch_id=? AND m.id=? AND m.deleted_at IS NULL",
            this::map, branchId, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<String> categories(UUID branchId) {
        return jdbcTemplate.queryForList("""
            SELECT DISTINCT ON (lower(category)) category FROM gcmb.menu_item
            WHERE branch_id=? AND deleted_at IS NULL
            ORDER BY lower(category), (category <> lower(category)) DESC, category
            """, String.class, branchId);
    }

    public List<InventoryOption> inventoryOptions(UUID branchId) {
        return jdbcTemplate.query("""
            SELECT i.id,i.sku,i.name,i.category,u.name AS unit_name,i.active
            FROM gcmb.inventory_item i JOIN gcmb.unit u ON u.id=i.unit_id
            WHERE i.branch_id=? ORDER BY i.active DESC,lower(i.name),i.sku
            """, (resultSet, row) -> new InventoryOption(resultSet.getObject("id", UUID.class), resultSet.getString("sku"),
                resultSet.getString("name"), resultSet.getString("category"), resultSet.getString("unit_name"), resultSet.getBoolean("active")), branchId);
    }

    public boolean activeInventory(UUID branchId, UUID inventoryItemId) {
        Boolean active = jdbcTemplate.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM gcmb.inventory_item WHERE id=? AND branch_id=? AND active=true)
            """, Boolean.class, inventoryItemId, branchId);
        return Boolean.TRUE.equals(active);
    }

    public boolean activeBranch(UUID branchId) {
        Boolean active = jdbcTemplate.queryForObject("SELECT EXISTS(SELECT 1 FROM gcmb.branch WHERE id=? AND status='ACTIVE')",
            Boolean.class, branchId);
        return Boolean.TRUE.equals(active);
    }

    public String branchName(UUID branchId) {
        return jdbcTemplate.queryForObject("SELECT name FROM gcmb.branch WHERE id=?", String.class, branchId);
    }

    public MenuItemView create(UUID branchId, MenuItemCommand command, StoredImage image) {
        String prefix = "SERVICE".equals(command.kind()) ? "DV-" : "FB-";
        String code = nextCode(branchId, prefix);
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO gcmb.menu_item(id,branch_id,code,name,category,kind,inventory_item_id,sale_price,active,
                description,image_object_key,image_original_name,image_mime_type)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, id, branchId, code, command.name(), command.category(), command.kind(), command.inventoryItemId(),
            command.salePrice(), command.active(), command.description(), image == null ? null : image.objectKey(),
            image == null ? null : image.originalName(), image == null ? null : image.mimeType());
        return get(branchId, id);
    }

    public MenuItemView update(UUID branchId, UUID id, MenuItemCommand command, StoredImage image, boolean removeImage) {
        boolean replaceImage = removeImage || image != null;
        int changed = jdbcTemplate.update("""
            UPDATE gcmb.menu_item SET name=?,category=?,kind=?,inventory_item_id=?,sale_price=?,active=?,description=?,
                image_object_key=CASE WHEN ? THEN ? ELSE image_object_key END,
                image_original_name=CASE WHEN ? THEN ? ELSE image_original_name END,
                image_mime_type=CASE WHEN ? THEN ? ELSE image_mime_type END
            WHERE id=? AND branch_id=? AND deleted_at IS NULL
            """, command.name(), command.category(), command.kind(), command.inventoryItemId(), command.salePrice(),
            command.active(), command.description(), replaceImage, image == null ? null : image.objectKey(),
            replaceImage, image == null ? null : image.originalName(), replaceImage,
            image == null ? null : image.mimeType(), id, branchId);
        return changed == 0 ? null : get(branchId, id);
    }

    public ImageAsset image(UUID branchId, UUID id, boolean activeOnly) {
        List<ImageAsset> rows = jdbcTemplate.query("""
            SELECT image_object_key,image_original_name,image_mime_type FROM gcmb.menu_item
            WHERE branch_id=? AND id=? AND deleted_at IS NULL AND image_object_key IS NOT NULL
                AND (?=false OR active=true)
            """, (resultSet, row) -> new ImageAsset(resultSet.getString("image_object_key"),
                resultSet.getString("image_original_name"), resultSet.getString("image_mime_type")), branchId, id, activeOnly);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<PublicMenuItemView> publicList(UUID branchId, String search, String category) {
        String term = search == null ? "" : search.trim();
        String categoryFilter = category == null ? "" : category.trim();
        return jdbcTemplate.query("""
            SELECT m.id,m.name,m.category,m.kind,m.sale_price,m.description,
                CASE WHEN m.image_object_key IS NULL THEN NULL
                    ELSE '/public/branches/' || m.branch_id || '/menu-items/' || m.id || '/image' END AS image_url,
                CASE WHEN m.inventory_item_id IS NULL THEN 'UNTRACKED'
                    WHEN sb.quantity IS NULL OR sb.quantity<=0 THEN 'OUT_OF_STOCK'
                    WHEN sb.quantity<=sb.minimum_quantity THEN 'LOW_STOCK'
                    ELSE 'AVAILABLE' END AS stock_status,
                CASE WHEN m.kind='PACKAGED' THEN COALESCE(sb.quantity,0)>0 ELSE true END AS available
            FROM gcmb.menu_item m
            JOIN gcmb.branch b ON b.id=m.branch_id AND b.status='ACTIVE'
            LEFT JOIN gcmb.stock_balance sb ON sb.inventory_item_id=m.inventory_item_id
            WHERE m.branch_id=? AND m.deleted_at IS NULL AND m.active=true
                AND (?='' OR strpos(lower(m.name || ' ' || m.category),lower(?))>0)
                AND (?='' OR lower(m.category)=lower(?))
            ORDER BY lower(m.category),lower(m.name),m.code
            """, (resultSet, row) -> new PublicMenuItemView(resultSet.getObject("id", UUID.class), resultSet.getString("name"),
                resultSet.getString("category"), resultSet.getString("kind"), resultSet.getBigDecimal("sale_price"),
                resultSet.getBoolean("available"), resultSet.getString("stock_status"), resultSet.getString("description"),
                resultSet.getString("image_url")), branchId, term, term, categoryFilter, categoryFilter);
    }

    public ImageAsset publicImage(UUID branchId, UUID id) {
        List<ImageAsset> rows = jdbcTemplate.query("""
            SELECT m.image_object_key,m.image_original_name,m.image_mime_type
            FROM gcmb.menu_item m JOIN gcmb.branch b ON b.id=m.branch_id AND b.status='ACTIVE'
            WHERE m.branch_id=? AND m.id=? AND m.active=true AND m.deleted_at IS NULL
                AND m.image_object_key IS NOT NULL
            """, (resultSet, row) -> new ImageAsset(resultSet.getString("image_object_key"),
                resultSet.getString("image_original_name"), resultSet.getString("image_mime_type")), branchId, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public MenuItemView changeStatus(UUID branchId, UUID id, boolean active) {
        int changed = jdbcTemplate.update("""
            UPDATE gcmb.menu_item SET active=?
            WHERE id=? AND branch_id=? AND deleted_at IS NULL
            """, active, id, branchId);
        return changed == 0 ? null : get(branchId, id);
    }

    public boolean softDelete(UUID branchId, UUID id, UUID actorId) {
        return jdbcTemplate.update("""
            UPDATE gcmb.menu_item
            SET active=false,deleted_at=CURRENT_TIMESTAMP,deleted_by=?
            WHERE id=? AND branch_id=? AND deleted_at IS NULL
            """, actorId, id, branchId) > 0;
    }

    private String nextCode(UUID branchId, String prefix) {
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(hashtext(?),hashtext(?))", resultSet -> {}, branchId.toString(), prefix);
        Long next = jdbcTemplate.queryForObject("""
            SELECT COALESCE(MAX(substring(code from '([0-9]+)$')::bigint),0)+1
            FROM gcmb.menu_item WHERE branch_id=? AND code ~ ?
            """, Long.class, branchId, "^" + prefix + "[0-9]+$");
        return prefix + String.format(java.util.Locale.ROOT, "%03d", next == null ? 1 : next);
    }

    private MenuItemView map(ResultSet r, int row) throws SQLException {
        return new MenuItemView(r.getObject("id", UUID.class), r.getString("code"), r.getString("name"),
            r.getString("category"), r.getString("kind"), r.getObject("inventory_item_id", UUID.class),
            r.getString("inventory_sku"), r.getString("inventory_name"), r.getBigDecimal("stock_quantity"),
            r.getBigDecimal("minimum_quantity"), r.getString("unit_name"), r.getBigDecimal("sale_price"),
            r.getBoolean("active"), r.getString("stock_status"), r.getString("description"), r.getString("image_url"),
            r.getString("image_original_name"), r.getString("image_mime_type"));
    }
}
