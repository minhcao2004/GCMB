package com.gcmb.backend.inventory.repository;

import com.gcmb.backend.inventory.dto.FoodOrderDtos.ActiveBooking;
import com.gcmb.backend.inventory.dto.FoodOrderDtos.OrderItem;
import com.gcmb.backend.inventory.dto.FoodOrderDtos.OrderView;
import com.gcmb.backend.inventory.dto.FoodOrderDtos.ServingEmployee;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FoodOrderRepository {
    private final JdbcTemplate db;

    public FoodOrderRepository(JdbcTemplate db) { this.db = db; }

    public ActiveRoom activeRoomByMrCode(String mrCode) {
        List<ActiveRoom> rows = db.query("""
            SELECT r.id AS room_id,r.code AS room_code,r.name AS room_name,
                   b.id AS branch_id,b.name AS branch_name,
                   bk.id AS booking_id,bk.code AS booking_code,a.id AS allocation_id
            FROM gcmb.room r
            JOIN gcmb.branch b ON b.id=r.branch_id AND b.status='ACTIVE'
            JOIN gcmb.room_allocation a ON a.room_id=r.id AND a.state='OCCUPIED'
            JOIN gcmb.booking bk ON bk.id=a.booking_id AND bk.status='IN_HOUSE'
            WHERE r.mr_code=?
            ORDER BY a.created_at DESC
            LIMIT 2
            """, (rs, row) -> new ActiveRoom(rs.getObject("room_id", UUID.class), rs.getString("room_code"),
                rs.getString("room_name"), rs.getObject("branch_id", UUID.class), rs.getString("branch_name"),
                rs.getObject("booking_id", UUID.class), rs.getString("booking_code"),
                rs.getObject("allocation_id", UUID.class)), mrCode);
        if (rows.isEmpty()) return null;
        if (rows.size() > 1) throw new IllegalStateException("Phòng có nhiều lượt sử dụng đang hoạt động.");
        return rows.get(0);
    }

    public ActiveRoom lockActiveBooking(UUID bookingId, UUID branchId) {
        List<ActiveRoom> rows = db.query("""
            SELECT r.id AS room_id,r.code AS room_code,r.name AS room_name,
                   b.id AS branch_id,b.name AS branch_name,
                   bk.id AS booking_id,bk.code AS booking_code,a.id AS allocation_id
            FROM gcmb.booking bk
            JOIN gcmb.branch b ON b.id=bk.branch_id
            JOIN gcmb.room_allocation a ON a.booking_id=bk.id AND a.state='OCCUPIED'
            JOIN gcmb.room r ON r.id=a.room_id
            WHERE bk.id=? AND bk.branch_id=? AND bk.status='IN_HOUSE' AND b.status='ACTIVE'
            ORDER BY a.created_at DESC
            LIMIT 2
            FOR UPDATE OF bk,a,r
            """, (rs, row) -> new ActiveRoom(rs.getObject("room_id", UUID.class), rs.getString("room_code"),
                rs.getString("room_name"), rs.getObject("branch_id", UUID.class), rs.getString("branch_name"),
                rs.getObject("booking_id", UUID.class), rs.getString("booking_code"),
                rs.getObject("allocation_id", UUID.class)), bookingId, branchId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public RoomQrSession validSession(String mrCode, String tokenHash) {
        List<RoomQrSession> rows = db.query("""
            SELECT s.id AS session_id,s.booking_id,s.allocation_id,b.branch_id,b.code AS booking_code,
                   r.id AS room_id,r.code AS room_code,r.name AS room_name,br.name AS branch_name,s.expires_at
            FROM gcmb.room_qr_session s
            JOIN gcmb.booking b ON b.id=s.booking_id AND b.status='IN_HOUSE'
            JOIN gcmb.room_allocation a ON a.id=s.allocation_id AND a.booking_id=b.id AND a.state='OCCUPIED'
            JOIN gcmb.room r ON r.id=a.room_id
            JOIN gcmb.branch br ON br.id=b.branch_id AND br.status='ACTIVE'
            WHERE r.mr_code=? AND s.token_hash=? AND s.revoked_at IS NULL AND s.expires_at>CURRENT_TIMESTAMP
            ORDER BY s.created_at DESC LIMIT 1
            """, (rs, row) -> new RoomQrSession(rs.getObject("session_id", UUID.class),
                rs.getObject("booking_id", UUID.class), rs.getObject("allocation_id", UUID.class),
                rs.getObject("branch_id", UUID.class), rs.getString("booking_code"),
                rs.getObject("room_id", UUID.class), rs.getString("room_code"), rs.getString("room_name"),
                rs.getString("branch_name"), instant(rs, "expires_at")), mrCode, tokenHash);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public UUID createSession(UUID bookingId, UUID allocationId, String tokenHash) {
        return db.queryForObject("""
            INSERT INTO gcmb.room_qr_session(booking_id,allocation_id,token_hash,expires_at)
            VALUES (?,?,?,CURRENT_TIMESTAMP+interval '12 hours') RETURNING id
            """, UUID.class, bookingId, allocationId, tokenHash);
    }

    public List<ActiveBooking> activeBookings(UUID branchId) {
        return db.query("""
            SELECT bk.id,bk.code,r.code AS room_code,r.name AS room_name,bk.contact_name,
                   bk.actual_checkin
            FROM gcmb.booking bk
            JOIN gcmb.room_allocation a ON a.booking_id=bk.id AND a.state='OCCUPIED'
            JOIN gcmb.room r ON r.id=a.room_id
            WHERE bk.branch_id=? AND bk.status='IN_HOUSE'
            ORDER BY bk.actual_checkin DESC,bk.code
            """, (rs, row) -> new ActiveBooking(rs.getObject("id", UUID.class), rs.getString("code"),
                rs.getString("room_code"), rs.getString("room_name"), rs.getString("contact_name"),
                instant(rs, "actual_checkin")), branchId);
    }

    public List<ServingEmployee> onShiftEmployees(UUID branchId) {
        return db.query("""
            SELECT e.id,e.full_name
            FROM gcmb.scheduled_shift s
            JOIN gcmb.shift_assignment sa ON sa.shift_id=s.id AND sa.status='CONFIRMED'
            JOIN gcmb.employee e ON e.id=sa.employee_id AND e.status='ACTIVE' AND e.user_id IS NOT NULL
            JOIN gcmb.user_account ua ON ua.id=e.user_id AND ua.status='ACTIVE'
            JOIN gcmb.role ro ON ro.id=ua.role_id AND ro.code='RECEPTIONIST'
            JOIN gcmb.employee_assignment ea ON ea.employee_id=e.id AND ea.branch_id=s.branch_id
                AND ea.is_branch_manager=false AND ea.valid_from<=CURRENT_TIMESTAMP
                AND (ea.valid_to IS NULL OR ea.valid_to>CURRENT_TIMESTAMP)
            WHERE s.branch_id=? AND s.status='OPEN'
                AND s.starts_at<=CURRENT_TIMESTAMP AND s.ends_at>CURRENT_TIMESTAMP
            GROUP BY e.id,e.full_name
            ORDER BY lower(e.full_name),e.id
            """, (rs, row) -> new ServingEmployee(rs.getObject("id", UUID.class), rs.getString("full_name")), branchId);
    }

    public boolean eligibleOnShift(UUID employeeId, UUID branchId) {
        Boolean eligible = db.queryForObject("""
            SELECT EXISTS(
                SELECT 1
                FROM gcmb.scheduled_shift s
                JOIN gcmb.shift_assignment sa ON sa.shift_id=s.id AND sa.status='CONFIRMED'
                JOIN gcmb.employee e ON e.id=sa.employee_id AND e.status='ACTIVE' AND e.user_id IS NOT NULL
                JOIN gcmb.user_account ua ON ua.id=e.user_id AND ua.status='ACTIVE'
                JOIN gcmb.role ro ON ro.id=ua.role_id AND ro.code='RECEPTIONIST'
                JOIN gcmb.employee_assignment ea ON ea.employee_id=e.id AND ea.branch_id=s.branch_id
                    AND ea.is_branch_manager=false AND ea.valid_from<=CURRENT_TIMESTAMP
                    AND (ea.valid_to IS NULL OR ea.valid_to>CURRENT_TIMESTAMP)
                WHERE e.id=? AND s.branch_id=? AND s.status='OPEN'
                    AND s.starts_at<=CURRENT_TIMESTAMP AND s.ends_at>CURRENT_TIMESTAMP
            )
            """, Boolean.class, employeeId, branchId);
        return Boolean.TRUE.equals(eligible);
    }

    public UUID servingUser(UUID employeeId) {
        List<UUID> rows = db.query("SELECT user_id FROM gcmb.employee WHERE id=?", (rs, row) -> rs.getObject(1, UUID.class), employeeId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public UUID createOrder(UUID branchId, UUID bookingId, UUID allocationId, String channel,
                            UUID createdBy, UUID qrSessionId, String status, UUID acceptedBy,
                            UUID assignedEmployeeId, String idempotencyKey) {
        List<UUID> rows = db.query("""
            INSERT INTO gcmb.fb_order(branch_id,booking_id,allocation_id,channel,created_by,qr_session_id,
                status,accepted_by,accepted_at,assigned_employee_id,idempotency_key)
            VALUES (?,?,?,?,?,?,?,?,CASE WHEN ?::uuid IS NULL THEN NULL ELSE CURRENT_TIMESTAMP END,?,?)
            ON CONFLICT (idempotency_key) DO NOTHING
            RETURNING id
            """, (rs, row) -> rs.getObject("id", UUID.class), branchId, bookingId, allocationId, channel,
            createdBy, qrSessionId, status, acceptedBy, acceptedBy, assignedEmployeeId, idempotencyKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public ExistingOrder existingByKey(String key) {
        List<ExistingOrder> rows = db.query("""
            SELECT id,branch_id,booking_id,channel,qr_session_id
            FROM gcmb.fb_order WHERE idempotency_key=?
            """, (rs, row) -> new ExistingOrder(rs.getObject("id", UUID.class),
                rs.getObject("branch_id", UUID.class), rs.getObject("booking_id", UUID.class),
                rs.getString("channel"), rs.getObject("qr_session_id", UUID.class)), key);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public UUID insertLine(UUID orderId, UUID branchId, UUID bookingId, UUID menuItemId, UUID comboItemId,
                           String name, BigDecimal quantity, BigDecimal unitPrice) {
        UUID id = UUID.randomUUID();
        db.update("""
            INSERT INTO gcmb.order_line(id,order_id,branch_id,booking_id,menu_item_id,booking_combo_item_id,
                name_snapshot,quantity,unit_price)
            VALUES (?,?,?,?,?,?,?,?,?)
            """, id, orderId, branchId, bookingId, menuItemId, comboItemId,
            name, quantity, unitPrice);
        return id;
    }

    public List<PendingLine> pendingLines(UUID orderId) {
        return db.query("""
            SELECT id,menu_item_id,name_snapshot,quantity,unit_price
            FROM gcmb.order_line WHERE order_id=? ORDER BY created_at,id
            """, (rs, row) -> new PendingLine(rs.getObject("id", UUID.class),
                rs.getObject("menu_item_id", UUID.class), rs.getString("name_snapshot"),
                rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price")), orderId);
    }

    public ComboAllowance comboAllowance(UUID bookingId, UUID menuItemId, UUID excludingOrderId) {
        List<ComboAllowance> rows = db.query("""
            SELECT c.id,c.included_quantity,
                COALESCE(SUM(CASE WHEN o.id IS NOT NULL AND o.id<>? AND o.status IN ('ACCEPTED','SERVED')
                    THEN l.quantity ELSE 0 END),0) AS used_quantity
            FROM gcmb.booking_combo_item c
            LEFT JOIN gcmb.order_line l ON l.booking_combo_item_id=c.id
            LEFT JOIN gcmb.fb_order o ON o.id=l.order_id
            WHERE c.booking_id=? AND c.menu_item_id=?
            GROUP BY c.id,c.included_quantity
            """, (rs, row) -> new ComboAllowance(rs.getObject("id", UUID.class),
                rs.getBigDecimal("included_quantity"), rs.getBigDecimal("used_quantity")),
            excludingOrderId, bookingId, menuItemId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void updateLine(UUID lineId, UUID comboItemId, BigDecimal quantity, BigDecimal unitPrice) {
        db.update("UPDATE gcmb.order_line SET booking_combo_item_id=?,quantity=?,unit_price=? WHERE id=?",
            comboItemId, quantity, unitPrice, lineId);
    }

    public void deleteLine(UUID lineId) { db.update("DELETE FROM gcmb.order_line WHERE id=?", lineId); }

    public List<SellableItem> sellableItems(UUID branchId, List<UUID> itemIds) {
        if (itemIds.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(itemIds.size(), "?"));
        return db.query("""
            SELECT m.id,m.name,m.kind,m.inventory_item_id,m.sale_price,i.tracking_mode,
                   COALESCE(sb.quantity,0) AS stock_quantity
            FROM gcmb.menu_item m
            LEFT JOIN gcmb.inventory_item i ON i.id=m.inventory_item_id
            LEFT JOIN gcmb.stock_balance sb ON sb.inventory_item_id=m.inventory_item_id
            WHERE m.branch_id=? AND m.deleted_at IS NULL AND m.active=true
                AND m.id IN (""" + placeholders + ") ORDER BY m.id",
            (rs, row) -> new SellableItem(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getString("kind"), rs.getObject("inventory_item_id", UUID.class),
                rs.getBigDecimal("sale_price"), rs.getString("tracking_mode"),
                rs.getBigDecimal("stock_quantity")), bind(branchId, itemIds));
    }

    public BigDecimal lockStock(UUID inventoryItemId) {
        List<BigDecimal> rows = db.query("""
            SELECT quantity FROM gcmb.stock_balance WHERE inventory_item_id=? FOR UPDATE
            """, (rs, row) -> rs.getBigDecimal("quantity"), inventoryItemId);
        return rows.isEmpty() ? BigDecimal.ZERO : rows.get(0);
    }

    public void insertSale(UUID inventoryItemId, UUID orderLineId, BigDecimal before,
                           BigDecimal quantity, UUID actorId, String reason) {
        BigDecimal after = before.subtract(quantity);
        db.update("""
            INSERT INTO gcmb.stock_movement(inventory_item_id,kind,quantity_delta,quantity_before,
                quantity_after,order_line_id,reason,actor_id,idempotency_key)
            VALUES (?,'SALE',?,?,?,?,?,?,?)
            """, inventoryItemId, quantity.negate(), before, after, orderLineId, reason, actorId,
            "SALE-" + orderLineId);
    }

    public OrderLock lockOrder(UUID orderId, UUID branchId) {
        List<OrderLock> rows = db.query("""
            SELECT o.id,o.branch_id,o.booking_id,o.allocation_id,o.channel,o.qr_session_id,o.status,
                   o.assigned_employee_id,e.user_id AS assigned_user_id
            FROM gcmb.fb_order o
            LEFT JOIN gcmb.employee e ON e.id=o.assigned_employee_id
            WHERE o.id=? AND o.branch_id=?
            FOR UPDATE OF o
            """, (rs, row) -> new OrderLock(rs.getObject("id", UUID.class),
                rs.getObject("branch_id", UUID.class), rs.getObject("booking_id", UUID.class),
                rs.getObject("allocation_id", UUID.class), rs.getString("channel"),
                rs.getObject("qr_session_id", UUID.class), rs.getString("status"),
                rs.getObject("assigned_employee_id", UUID.class), rs.getObject("assigned_user_id", UUID.class)),
            orderId, branchId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public int accept(UUID orderId, UUID branchId, UUID acceptedBy, UUID employeeId) {
        return db.update("""
            UPDATE gcmb.fb_order SET status='ACCEPTED',accepted_by=?,accepted_at=CURRENT_TIMESTAMP,
                assigned_employee_id=?
            WHERE id=? AND branch_id=? AND status='PENDING'
            """, acceptedBy, employeeId, orderId, branchId);
    }

    public int reject(UUID orderId, UUID branchId) {
        return db.update("""
            UPDATE gcmb.fb_order SET status='REJECTED'
            WHERE id=? AND branch_id=? AND status='PENDING'
            """, orderId, branchId);
    }

    public int serve(UUID orderId, UUID branchId) {
        return db.update("""
            UPDATE gcmb.fb_order SET status='SERVED',served_at=CURRENT_TIMESTAMP
            WHERE id=? AND branch_id=? AND status='ACCEPTED'
            """, orderId, branchId);
    }

    public List<OrderView> orders(UUID branchId, String status) {
        return loadOrders("o.branch_id=?", new Object[]{branchId}, status, 200);
    }

    public List<OrderView> qrOrders(UUID sessionId) {
        return loadOrders("o.qr_session_id=?", new Object[]{sessionId}, null, 100);
    }

    public OrderView order(UUID orderId, UUID branchId) {
        List<OrderView> rows = loadOrders("o.id=? AND o.branch_id=?", new Object[]{orderId, branchId}, null, 1);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public OrderView qrOrder(UUID orderId, UUID sessionId) {
        List<OrderView> rows = loadOrders("o.id=? AND o.qr_session_id=?", new Object[]{orderId, sessionId}, null, 1);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<OrderView> loadOrders(String predicate, Object[] predicateArgs, String status, int limit) {
        String statusClause = status == null ? "" : " AND o.status=?";
        Object[] args = status == null ? predicateArgs : append(predicateArgs, status);
        args = append(args, limit);
        List<OrderHeader> headers = db.query("""
            SELECT o.id,o.booking_id,b.code AS booking_code,r.code AS room_code,r.name AS room_name,
                   b.contact_name,o.channel,o.status,o.assigned_employee_id,e.full_name AS assigned_employee_name,
                   o.created_at,o.accepted_at,o.served_at
            FROM gcmb.fb_order o
            JOIN gcmb.booking b ON b.id=o.booking_id
            JOIN gcmb.room_allocation a ON a.id=o.allocation_id
            JOIN gcmb.room r ON r.id=a.room_id
            LEFT JOIN gcmb.employee e ON e.id=o.assigned_employee_id
            """ + " WHERE " + predicate + statusClause + " ORDER BY o.created_at DESC,o.id DESC LIMIT ?",
            (rs, row) -> new OrderHeader(rs.getObject("id", UUID.class), rs.getObject("booking_id", UUID.class),
                rs.getString("booking_code"), rs.getString("room_code"), rs.getString("room_name"),
                rs.getString("contact_name"), rs.getString("channel"), rs.getString("status"),
                rs.getObject("assigned_employee_id", UUID.class), rs.getString("assigned_employee_name"),
                instant(rs, "created_at"), instant(rs, "accepted_at"), instant(rs, "served_at")), args);
        if (headers.isEmpty()) return List.of();

        List<UUID> ids = headers.stream().map(OrderHeader::id).toList();
        String linePlaceholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        Map<UUID, List<OrderItem>> linesByOrder = new HashMap<>();
        db.query("""
            SELECT id,order_id,menu_item_id,name_snapshot,quantity,unit_price,booking_combo_item_id
            FROM gcmb.order_line WHERE order_id IN (""" + linePlaceholders + ") ORDER BY created_at,id",
            rs -> {
                UUID orderId = rs.getObject("order_id", UUID.class);
                BigDecimal quantity = rs.getBigDecimal("quantity");
                BigDecimal unitPrice = rs.getBigDecimal("unit_price");
                linesByOrder.computeIfAbsent(orderId, ignored -> new ArrayList<>()).add(
                    new OrderItem(rs.getObject("id", UUID.class), rs.getObject("menu_item_id", UUID.class),
                        rs.getString("name_snapshot"), quantity, unitPrice, unitPrice.multiply(quantity),
                        rs.getObject("booking_combo_item_id", UUID.class) != null));
            }, ids.toArray());

        List<OrderView> result = new ArrayList<>(headers.size());
        for (OrderHeader header : headers) {
            List<OrderItem> items = linesByOrder.getOrDefault(header.id(), List.of());
            BigDecimal total = items.stream().map(OrderItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            result.add(new OrderView(header.id(), "OD-" + header.id().toString().substring(0, 8).toUpperCase(),
                header.bookingId(), header.bookingCode(), header.roomCode(), header.roomName(), header.customerName(),
                header.channel(), header.status(), header.assignedEmployeeId(), header.assignedEmployeeName(),
                header.createdAt(), header.acceptedAt(), header.servedAt(), total, items));
        }
        return result;
    }

    private static Object[] bind(UUID branchId, List<UUID> ids) {
        Object[] result = new Object[ids.size() + 1];
        result[0] = branchId;
        for (int i = 0; i < ids.size(); i++) result[i + 1] = ids.get(i);
        return result;
    }

    private static Object[] append(Object[] values, Object extra) {
        Object[] result = java.util.Arrays.copyOf(values, values.length + 1);
        result[values.length] = extra;
        return result;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    public record ActiveRoom(UUID roomId, String roomCode, String roomName, UUID branchId,
                             String branchName, UUID bookingId, String bookingCode, UUID allocationId) {}
    public record RoomQrSession(UUID id, UUID bookingId, UUID allocationId, UUID branchId,
                                String bookingCode, UUID roomId, String roomCode, String roomName,
                                String branchName, Instant expiresAt) {}
    public record ExistingOrder(UUID id, UUID branchId, UUID bookingId, String channel, UUID qrSessionId) {}
    public record PendingLine(UUID id, UUID menuItemId, String name, BigDecimal quantity, BigDecimal unitPrice) {}
    public record ComboAllowance(UUID id, BigDecimal includedQuantity, BigDecimal usedQuantity) {}
    public record SellableItem(UUID id, String name, String kind, UUID inventoryItemId,
                               BigDecimal salePrice, String trackingMode, BigDecimal stockQuantity) {}
    public record OrderLock(UUID id, UUID branchId, UUID bookingId, UUID allocationId, String channel,
                            UUID qrSessionId, String status, UUID assignedEmployeeId, UUID assignedUserId) {}
    private record OrderHeader(UUID id, UUID bookingId, String bookingCode, String roomCode, String roomName,
                               String customerName, String channel, String status, UUID assignedEmployeeId,
                               String assignedEmployeeName, Instant createdAt, Instant acceptedAt, Instant servedAt) {}
}
