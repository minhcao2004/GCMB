package com.gcmb.backend.inventory.repository;

import com.gcmb.backend.inventory.dto.InventoryDtos.InventoryItemView;
import com.gcmb.backend.inventory.dto.InventoryDtos.ReceiptLineCommand;
import com.gcmb.backend.inventory.dto.InventoryDtos.ReceiptLineResult;
import com.gcmb.backend.inventory.dto.InventoryDtos.StockMovementView;
import com.gcmb.backend.inventory.dto.InventoryDtos.UnitOption;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryRepository {
    private static final String ITEM_SELECT = """
        SELECT i.id,i.sku,i.name,i.category,i.unit_id,u.name AS unit_name,i.tracking_mode,
               COALESCE(sb.quantity,0) AS quantity,COALESCE(sb.minimum_quantity,0) AS minimum_quantity,
               COALESCE(sb.version,0) AS stock_version,i.active,
               EXISTS(SELECT 1 FROM gcmb.stock_movement sm WHERE sm.inventory_item_id=i.id) AS has_movements,
               CASE WHEN COALESCE(sb.quantity,0)<=0 THEN 'OUT_OF_STOCK'
                    WHEN sb.quantity<=sb.minimum_quantity THEN 'LOW_STOCK' ELSE 'AVAILABLE' END AS stock_status
        FROM gcmb.inventory_item i
        JOIN gcmb.unit u ON u.id=i.unit_id
        LEFT JOIN gcmb.stock_balance sb ON sb.inventory_item_id=i.id
        """;

    private final JdbcTemplate jdbcTemplate;

    public InventoryRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public List<InventoryItemView> list(UUID branchId) {
        return jdbcTemplate.query(ITEM_SELECT + " WHERE i.branch_id=? ORDER BY i.active DESC,lower(i.name),i.sku",
            InventoryRepository::mapItem, branchId);
    }

    public InventoryItemView get(UUID branchId, UUID id) {
        List<InventoryItemView> rows = jdbcTemplate.query(ITEM_SELECT + " WHERE i.branch_id=? AND i.id=?",
            InventoryRepository::mapItem, branchId, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<UnitOption> units() {
        return jdbcTemplate.query("SELECT id,code,name FROM gcmb.unit ORDER BY lower(name)",
            (resultSet, row) -> new UnitOption(resultSet.getObject("id", UUID.class), resultSet.getString("code"), resultSet.getString("name")));
    }

    public List<String> categories(UUID branchId) {
        List<String> categories = new ArrayList<>(List.of("Đồ uống", "Đồ ăn", "Nguyên liệu", "Vật tư", "Khác"));
        for (String category : jdbcTemplate.queryForList("""
                SELECT category FROM gcmb.inventory_item
                WHERE branch_id=? GROUP BY category ORDER BY lower(category)
                """, String.class, branchId)) {
            if (categories.stream().noneMatch(existing -> existing.equalsIgnoreCase(category))) categories.add(category);
        }
        return categories;
    }

    public boolean activeBranch(UUID branchId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM gcmb.branch WHERE id=? AND status='ACTIVE')", Boolean.class, branchId));
    }

    public String branchName(UUID branchId) {
        return jdbcTemplate.queryForObject("SELECT name FROM gcmb.branch WHERE id=?", String.class, branchId);
    }

    public String nextInventorySku(UUID branchId) {
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(hashtext(?))", resultSet -> {}, "GCMB_INVENTORY_ITEM_SKU:" + branchId);
        Long next = jdbcTemplate.queryForObject("""
            SELECT COALESCE(MAX(substring(sku FROM 4)::bigint),0)+1
            FROM gcmb.inventory_item WHERE branch_id=? AND sku ~ '^MH-[0-9]+$'
            """, Long.class, branchId);
        return String.format(Locale.ROOT, "MH-%04d", next == null ? 1L : next);
    }

    public UUID create(UUID branchId, String sku, String name, String category, UUID unitId,
                       String trackingMode, BigDecimal minimumQuantity) {
        UUID id = jdbcTemplate.queryForObject("""
            INSERT INTO gcmb.inventory_item(branch_id,sku,name,category,unit_id,tracking_mode,active)
            VALUES (?,?,?,?,?,?,true) RETURNING id
            """, UUID.class, branchId, sku, name, category, unitId, trackingMode);
        jdbcTemplate.update("""
            INSERT INTO gcmb.stock_balance(inventory_item_id,quantity,minimum_quantity,version)
            VALUES (?,0,?,0)
            """, id, minimumQuantity);
        return id;
    }

    public LockedItem lockItem(UUID branchId, UUID id) {
        List<LockedItem> rows = jdbcTemplate.query("""
            SELECT i.id,i.unit_id,i.active,s.quantity,s.version
            FROM gcmb.inventory_item i JOIN gcmb.stock_balance s ON s.inventory_item_id=i.id
            WHERE i.branch_id=? AND i.id=? FOR UPDATE OF i,s
            """, (resultSet, row) -> new LockedItem(resultSet.getObject("id", UUID.class),
                resultSet.getObject("unit_id", UUID.class), resultSet.getBoolean("active"), resultSet.getBigDecimal("quantity"),
                resultSet.getLong("version")), branchId, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public boolean hasMovements(UUID inventoryItemId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM gcmb.stock_movement WHERE inventory_item_id=?)", Boolean.class, inventoryItemId));
    }

    public boolean hasActiveMenuLink(UUID inventoryItemId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM gcmb.menu_item WHERE inventory_item_id=? AND active=true AND deleted_at IS NULL)
            """, Boolean.class, inventoryItemId));
    }

    public void update(UUID branchId, UUID id, String sku, String name, String category, UUID unitId,
                       String trackingMode, BigDecimal minimumQuantity, boolean active) {
        jdbcTemplate.update("""
            UPDATE gcmb.inventory_item SET sku=?,name=?,category=?,unit_id=?,tracking_mode=?,active=?
            WHERE id=? AND branch_id=?
            """, sku, name, category, unitId, trackingMode, active, id, branchId);
        jdbcTemplate.update("UPDATE gcmb.stock_balance SET minimum_quantity=? WHERE inventory_item_id=?",
            minimumQuantity, id);
    }

    public boolean archive(UUID branchId, UUID id) {
        return jdbcTemplate.update("UPDATE gcmb.inventory_item SET active=false WHERE id=? AND branch_id=? AND active=true",
            id, branchId) > 0;
    }

    public StockBalance lockBalance(UUID inventoryItemId) {
        List<StockBalance> rows = jdbcTemplate.query("""
            SELECT quantity,version FROM gcmb.stock_balance WHERE inventory_item_id=? FOR UPDATE
            """, (resultSet, row) -> new StockBalance(resultSet.getBigDecimal("quantity"), resultSet.getLong("version")), inventoryItemId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public String nextReceiptCode() {
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(hashtext(?))", resultSet -> {}, "GCMB_STOCK_RECEIPT_CODE");
        Long next = jdbcTemplate.queryForObject("""
            SELECT COALESCE(MAX(substring(code FROM 4)::bigint),0)+1
            FROM gcmb.stock_receipt WHERE code ~ '^NK-[0-9]+$'
            """, Long.class);
        return String.format(Locale.ROOT, "NK-%04d", next == null ? 1L : next);
    }

    public UUID insertReceipt(UUID branchId, String code, String supplierName, String invoice,
                              Instant receivedAt, UUID actorId, String evidenceKey,
                              String evidenceName, String evidenceMimeType) {
        return jdbcTemplate.queryForObject("""
            INSERT INTO gcmb.stock_receipt(branch_id,code,supplier_name,supplier_invoice,received_at,status,
                evidence_object_key,evidence_original_name,evidence_mime_type)
            VALUES (?,?,?,?,?,'DRAFT',?,?,?) RETURNING id
            """, UUID.class, branchId, code, supplierName, invoice, Timestamp.from(receivedAt),
            evidenceKey, evidenceName, evidenceMimeType);
    }

    public UUID insertReceiptLine(UUID receiptId, UUID branchId, ReceiptLineCommand line) {
        return jdbcTemplate.queryForObject("""
            INSERT INTO gcmb.stock_receipt_line(receipt_id,branch_id,inventory_item_id,quantity,unit_cost,expiry_date)
            VALUES (?,?,?,?,?,?) RETURNING id
            """, UUID.class, receiptId, branchId, line.inventoryItemId(), line.quantity(), line.unitCost(), line.expiryDate());
    }

    public void postReceipt(UUID receiptId, UUID actorId) {
        jdbcTemplate.update("""
            UPDATE gcmb.stock_receipt SET status='POSTED',posted_by=?,posted_at=CURRENT_TIMESTAMP
            WHERE id=? AND status='DRAFT'
            """, actorId, receiptId);
    }

    public void insertReceiptMovement(UUID itemId, UUID lineId, BigDecimal quantity, BigDecimal before,
                                      BigDecimal unitCost, UUID actorId, String reason) {
        jdbcTemplate.update("""
            INSERT INTO gcmb.stock_movement(inventory_item_id,kind,quantity_delta,quantity_before,
                quantity_after,unit_cost,receipt_line_id,reason,actor_id,idempotency_key)
            VALUES (?,'RECEIPT',?,?,?,?,?,?,?,?)
            """, itemId, quantity, before, before.add(quantity), unitCost, lineId, reason, actorId,
            "RECEIPT-" + lineId);
    }

    public UUID insertAdjustment(UUID itemId, BigDecimal delta, BigDecimal before, Instant occurredAt, UUID actorId,
                                 String reason, String evidenceKey, String evidenceName, String evidenceMimeType) {
        return jdbcTemplate.queryForObject("""
            INSERT INTO gcmb.stock_movement(inventory_item_id,kind,quantity_delta,quantity_before,
                quantity_after,occurred_at,reason,actor_id,idempotency_key,evidence_object_key,evidence_original_name,evidence_mime_type)
            VALUES (?,'ADJUSTMENT',?,?,?,?,?,?,?,?,?,?) RETURNING id
            """, UUID.class, itemId, delta, before, before.add(delta), Timestamp.from(occurredAt), reason, actorId,
            "ADJUSTMENT-" + UUID.randomUUID(), evidenceKey, evidenceName, evidenceMimeType);
    }

    public List<StockMovementView> history(UUID branchId, UUID itemId, Instant from, Instant to,
                                           String kind, String search) {
        StringBuilder sql = new StringBuilder(MOVEMENT_SELECT)
            .append(" WHERE i.branch_id=? AND i.id=?");
        List<Object> args = new ArrayList<>(List.of(branchId, itemId));
        if (from != null) { sql.append(" AND COALESCE(m.occurred_at,sr.received_at,m.created_at)>=?"); args.add(Timestamp.from(from)); }
        if (to != null) { sql.append(" AND COALESCE(m.occurred_at,sr.received_at,m.created_at)<=?"); args.add(Timestamp.from(to)); }
        if (kind != null) { sql.append(" AND m.kind=?"); args.add(kind); }
        if (search != null && !search.isBlank()) {
            sql.append(" AND (m.reason ILIKE ? OR COALESCE(sr.code,'') ILIKE ? OR COALESCE(u.username,'') ILIKE ? OR COALESCE(e.full_name,'') ILIKE ?)");
            String term = "%" + search.trim() + "%";
            args.add(term); args.add(term); args.add(term); args.add(term);
        }
        sql.append(" ORDER BY COALESCE(m.occurred_at,sr.received_at,m.created_at) DESC,m.created_at DESC,m.id DESC LIMIT 500");
        return jdbcTemplate.query(sql.toString(), InventoryRepository::mapMovement, args.toArray());
    }

    public StockMovementView movement(UUID branchId, UUID itemId, UUID movementId) {
        List<StockMovementView> rows = jdbcTemplate.query(MOVEMENT_SELECT +
            " WHERE i.branch_id=? AND i.id=? AND m.id=?", InventoryRepository::mapMovement,
            branchId, itemId, movementId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public EvidenceRow evidence(UUID branchId, UUID itemId, UUID movementId) {
        List<EvidenceRow> rows = jdbcTemplate.query("""
            SELECT COALESCE(m.evidence_object_key,r.evidence_object_key) AS object_key,
                   COALESCE(m.evidence_original_name,r.evidence_original_name) AS original_name,
                   COALESCE(m.evidence_mime_type,r.evidence_mime_type) AS mime_type
            FROM gcmb.stock_movement m
            JOIN gcmb.inventory_item i ON i.id=m.inventory_item_id
            LEFT JOIN gcmb.stock_receipt_line rl ON rl.id=m.receipt_line_id
            LEFT JOIN gcmb.stock_receipt r ON r.id=rl.receipt_id
            WHERE i.branch_id=? AND i.id=? AND m.id=?
              AND COALESCE(m.evidence_object_key,r.evidence_object_key) IS NOT NULL
            """, (resultSet, row) -> new EvidenceRow(resultSet.getString("object_key"),
                resultSet.getString("original_name"), resultSet.getString("mime_type")), branchId, itemId, movementId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static final String MOVEMENT_SELECT = """
        SELECT m.id,COALESCE(m.occurred_at,sr.received_at,m.created_at) AS created_at,m.kind,m.quantity_delta,m.quantity_after,m.unit_cost,m.reason,
                COALESCE(e.full_name,u.username,'Quản lý cơ sở') AS actor,
                sr.supplier_name,rl.expiry_date,
               CASE WHEN m.kind='RECEIPT' THEN sr.code || ' · Nhập'
                    WHEN m.kind='SALE' THEN 'OD-' || upper(left(replace(ol.order_id::text,'-',''),6)) || ' · Bán'
                    WHEN m.kind='ADJUSTMENT' THEN 'DC-' || upper(left(replace(m.id::text,'-',''),6)) || ' · Kiểm kê'
                    WHEN m.kind='RETURN' THEN 'HT-' || upper(left(replace(m.id::text,'-',''),6)) || ' · Hoàn hàng'
                    ELSE 'Tồn đầu kỳ' END AS source,
               COALESCE(m.evidence_original_name,sr.evidence_original_name) AS evidence_name,
               CASE WHEN COALESCE(m.evidence_object_key,sr.evidence_object_key) IS NULL THEN NULL
                    ELSE '/branch-management/inventory/items/' || i.id || '/movements/' || m.id || '/evidence' END AS evidence_url
        FROM gcmb.stock_movement m
        JOIN gcmb.inventory_item i ON i.id=m.inventory_item_id
        LEFT JOIN gcmb.stock_receipt_line rl ON rl.id=m.receipt_line_id
        LEFT JOIN gcmb.stock_receipt sr ON sr.id=rl.receipt_id
        LEFT JOIN gcmb.order_line ol ON ol.id=m.order_line_id
        LEFT JOIN gcmb.user_account u ON u.id=m.actor_id
        LEFT JOIN gcmb.employee e ON e.user_id=u.id
        """;

    private static InventoryItemView mapItem(ResultSet resultSet, int row) throws SQLException {
        return new InventoryItemView(resultSet.getObject("id", UUID.class), resultSet.getString("sku"), resultSet.getString("name"),
            resultSet.getString("category"), resultSet.getObject("unit_id", UUID.class), resultSet.getString("unit_name"),
            resultSet.getString("tracking_mode"), resultSet.getBigDecimal("quantity"), resultSet.getBigDecimal("minimum_quantity"),
            resultSet.getLong("stock_version"), resultSet.getBoolean("active"), resultSet.getBoolean("has_movements"),
            resultSet.getString("stock_status"));
    }

    private static StockMovementView mapMovement(ResultSet resultSet, int row) throws SQLException {
        Timestamp createdAt = resultSet.getTimestamp("created_at");
        return new StockMovementView(resultSet.getObject("id", UUID.class), createdAt == null ? null : createdAt.toInstant(),
            resultSet.getString("kind"), resultSet.getBigDecimal("quantity_delta"), resultSet.getBigDecimal("quantity_after"),
            resultSet.getBigDecimal("unit_cost"), resultSet.getString("source"), resultSet.getString("actor"), resultSet.getString("reason"),
            resultSet.getString("evidence_name"), resultSet.getString("evidence_url"),
            resultSet.getString("supplier_name"), resultSet.getObject("expiry_date", LocalDate.class));
    }

    public record LockedItem(UUID id, UUID unitId, boolean active, BigDecimal quantity, long version) {}
    public record StockBalance(BigDecimal quantity, long version) {}
    public record EvidenceRow(String objectKey, String originalName, String mimeType) {}
}
