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

    private final JdbcTemplate db;

    public InventoryRepository(JdbcTemplate db) { this.db = db; }

    public List<InventoryItemView> list(UUID branchId) {
        return db.query(ITEM_SELECT + " WHERE i.branch_id=? ORDER BY i.active DESC,lower(i.name),i.sku",
            InventoryRepository::mapItem, branchId);
    }

    public InventoryItemView get(UUID branchId, UUID id) {
        List<InventoryItemView> rows = db.query(ITEM_SELECT + " WHERE i.branch_id=? AND i.id=?",
            InventoryRepository::mapItem, branchId, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<UnitOption> units() {
        return db.query("SELECT id,code,name FROM gcmb.unit ORDER BY lower(name)",
            (rs, row) -> new UnitOption(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name")));
    }

    public List<String> categories(UUID branchId) {
        return db.queryForList("""
            SELECT category FROM gcmb.inventory_item
            WHERE branch_id=? AND active=true GROUP BY category ORDER BY lower(category)
            """, String.class, branchId);
    }

    public boolean activeBranch(UUID branchId) {
        return Boolean.TRUE.equals(db.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM gcmb.branch WHERE id=? AND status='ACTIVE')", Boolean.class, branchId));
    }

    public UUID create(UUID branchId, String sku, String name, String category, UUID unitId,
                       String trackingMode, BigDecimal minimumQuantity) {
        UUID id = db.queryForObject("""
            INSERT INTO gcmb.inventory_item(branch_id,sku,name,category,unit_id,tracking_mode,active)
            VALUES (?,?,?,?,?,?,true) RETURNING id
            """, UUID.class, branchId, sku, name, category, unitId, trackingMode);
        db.update("""
            INSERT INTO gcmb.stock_balance(inventory_item_id,quantity,minimum_quantity,version)
            VALUES (?,0,?,0)
            """, id, minimumQuantity);
        return id;
    }

    public LockedItem lockItem(UUID branchId, UUID id) {
        List<LockedItem> rows = db.query("""
            SELECT i.id,i.unit_id,i.active,s.quantity,s.version
            FROM gcmb.inventory_item i JOIN gcmb.stock_balance s ON s.inventory_item_id=i.id
            WHERE i.branch_id=? AND i.id=? FOR UPDATE OF i,s
            """, (rs, row) -> new LockedItem(rs.getObject("id", UUID.class),
                rs.getObject("unit_id", UUID.class), rs.getBoolean("active"), rs.getBigDecimal("quantity"),
                rs.getLong("version")), branchId, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public boolean hasMovements(UUID inventoryItemId) {
        return Boolean.TRUE.equals(db.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM gcmb.stock_movement WHERE inventory_item_id=?)", Boolean.class, inventoryItemId));
    }

    public boolean hasActiveMenuLink(UUID inventoryItemId) {
        return Boolean.TRUE.equals(db.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM gcmb.menu_item WHERE inventory_item_id=? AND active=true AND deleted_at IS NULL)
            """, Boolean.class, inventoryItemId));
    }

    public void update(UUID branchId, UUID id, String sku, String name, String category, UUID unitId,
                       String trackingMode, BigDecimal minimumQuantity, boolean active) {
        db.update("""
            UPDATE gcmb.inventory_item SET sku=?,name=?,category=?,unit_id=?,tracking_mode=?,active=?
            WHERE id=? AND branch_id=?
            """, sku, name, category, unitId, trackingMode, active, id, branchId);
        db.update("UPDATE gcmb.stock_balance SET minimum_quantity=? WHERE inventory_item_id=?",
            minimumQuantity, id);
    }

    public boolean archive(UUID branchId, UUID id) {
        return db.update("UPDATE gcmb.inventory_item SET active=false WHERE id=? AND branch_id=? AND active=true",
            id, branchId) > 0;
    }

    public StockBalance lockBalance(UUID inventoryItemId) {
        List<StockBalance> rows = db.query("""
            SELECT quantity,version FROM gcmb.stock_balance WHERE inventory_item_id=? FOR UPDATE
            """, (rs, row) -> new StockBalance(rs.getBigDecimal("quantity"), rs.getLong("version")), inventoryItemId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public String nextReceiptCode() {
        db.query("SELECT pg_advisory_xact_lock(hashtext(?))", rs -> {}, "GCMB_STOCK_RECEIPT_CODE");
        Long next = db.queryForObject("""
            SELECT COALESCE(MAX(substring(code FROM 4)::bigint),0)+1
            FROM gcmb.stock_receipt WHERE code ~ '^NK-[0-9]+$'
            """, Long.class);
        return String.format(Locale.ROOT, "NK-%04d", next == null ? 1L : next);
    }

    public UUID insertReceipt(UUID branchId, String code, String supplierName, String invoice,
                              Instant receivedAt, UUID actorId, String evidenceKey,
                              String evidenceName, String evidenceMimeType) {
        return db.queryForObject("""
            INSERT INTO gcmb.stock_receipt(branch_id,code,supplier_name,supplier_invoice,received_at,status,
                evidence_object_key,evidence_original_name,evidence_mime_type)
            VALUES (?,?,?,?,?,'DRAFT',?,?,?) RETURNING id
            """, UUID.class, branchId, code, supplierName, invoice, Timestamp.from(receivedAt),
            evidenceKey, evidenceName, evidenceMimeType);
    }

    public UUID insertReceiptLine(UUID receiptId, UUID branchId, ReceiptLineCommand line) {
        return db.queryForObject("""
            INSERT INTO gcmb.stock_receipt_line(receipt_id,branch_id,inventory_item_id,quantity,unit_cost)
            VALUES (?,?,?,?,?) RETURNING id
            """, UUID.class, receiptId, branchId, line.inventoryItemId(), line.quantity(), line.unitCost());
    }

    public void postReceipt(UUID receiptId, UUID actorId) {
        db.update("""
            UPDATE gcmb.stock_receipt SET status='POSTED',posted_by=?,posted_at=CURRENT_TIMESTAMP
            WHERE id=? AND status='DRAFT'
            """, actorId, receiptId);
    }

    public void insertReceiptMovement(UUID itemId, UUID lineId, BigDecimal quantity, BigDecimal before,
                                      BigDecimal unitCost, UUID actorId, String reason) {
        db.update("""
            INSERT INTO gcmb.stock_movement(inventory_item_id,kind,quantity_delta,quantity_before,
                quantity_after,unit_cost,receipt_line_id,reason,actor_id,idempotency_key)
            VALUES (?,'RECEIPT',?,?,?,?,?,?,?,?)
            """, itemId, quantity, before, before.add(quantity), unitCost, lineId, reason, actorId,
            "RECEIPT-" + lineId);
    }

    public UUID insertAdjustment(UUID itemId, BigDecimal delta, BigDecimal before, Instant occurredAt, UUID actorId,
                                 String reason, String evidenceKey, String evidenceName, String evidenceMimeType) {
        return db.queryForObject("""
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
        return db.query(sql.toString(), InventoryRepository::mapMovement, args.toArray());
    }

    public StockMovementView movement(UUID branchId, UUID itemId, UUID movementId) {
        List<StockMovementView> rows = db.query(MOVEMENT_SELECT +
            " WHERE i.branch_id=? AND i.id=? AND m.id=?", InventoryRepository::mapMovement,
            branchId, itemId, movementId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public EvidenceRow evidence(UUID branchId, UUID itemId, UUID movementId) {
        List<EvidenceRow> rows = db.query("""
            SELECT COALESCE(m.evidence_object_key,r.evidence_object_key) AS object_key,
                   COALESCE(m.evidence_original_name,r.evidence_original_name) AS original_name,
                   COALESCE(m.evidence_mime_type,r.evidence_mime_type) AS mime_type
            FROM gcmb.stock_movement m
            JOIN gcmb.inventory_item i ON i.id=m.inventory_item_id
            LEFT JOIN gcmb.stock_receipt_line rl ON rl.id=m.receipt_line_id
            LEFT JOIN gcmb.stock_receipt r ON r.id=rl.receipt_id
            WHERE i.branch_id=? AND i.id=? AND m.id=?
              AND COALESCE(m.evidence_object_key,r.evidence_object_key) IS NOT NULL
            """, (rs, row) -> new EvidenceRow(rs.getString("object_key"),
                rs.getString("original_name"), rs.getString("mime_type")), branchId, itemId, movementId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static final String MOVEMENT_SELECT = """
        SELECT m.id,COALESCE(m.occurred_at,sr.received_at,m.created_at) AS created_at,m.kind,m.quantity_delta,m.quantity_after,m.unit_cost,m.reason,
               COALESCE(e.full_name,u.username,'Quản lý cơ sở') AS actor,
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

    private static InventoryItemView mapItem(ResultSet rs, int row) throws SQLException {
        return new InventoryItemView(rs.getObject("id", UUID.class), rs.getString("sku"), rs.getString("name"),
            rs.getString("category"), rs.getObject("unit_id", UUID.class), rs.getString("unit_name"),
            rs.getString("tracking_mode"), rs.getBigDecimal("quantity"), rs.getBigDecimal("minimum_quantity"),
            rs.getLong("stock_version"), rs.getBoolean("active"), rs.getBoolean("has_movements"),
            rs.getString("stock_status"));
    }

    private static StockMovementView mapMovement(ResultSet rs, int row) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new StockMovementView(rs.getObject("id", UUID.class), createdAt == null ? null : createdAt.toInstant(),
            rs.getString("kind"), rs.getBigDecimal("quantity_delta"), rs.getBigDecimal("quantity_after"),
            rs.getBigDecimal("unit_cost"), rs.getString("source"), rs.getString("actor"), rs.getString("reason"),
            rs.getString("evidence_name"), rs.getString("evidence_url"));
    }

    public record LockedItem(UUID id, UUID unitId, boolean active, BigDecimal quantity, long version) {}
    public record StockBalance(BigDecimal quantity, long version) {}
    public record EvidenceRow(String objectKey, String originalName, String mimeType) {}
}
