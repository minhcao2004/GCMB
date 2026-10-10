package com.gcmb.backend.booking.repository;

import com.gcmb.backend.booking.dto.BookingDtos;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BookingRepository {
    private final JdbcTemplate db;
    private final ObjectMapper json;

    public BookingRepository(JdbcTemplate db, ObjectMapper json) { this.db = db; this.json = json; }

    private static Timestamp ts(Instant value) { return Timestamp.from(value); }
    private static Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    public record BranchScope(UUID id, String name, ZoneId timezone) {}
    public record RoomTypeRow(UUID id, String code, String name, String mode, int capacity) {}
    public record ComboRow(UUID id, String code, int version, UUID roomTypeId, String name,
                           int durationMinutes, BigDecimal price, BigDecimal depositRequired,
                           BigDecimal overtimeHourlyPrice, Instant validFrom, Instant validTo) {}
    public record RoomRow(UUID id, String code, String name, int capacity, String operatingStatus,
                          UUID roomTypeId, String roomTypeName) {}
    public record PricingRule(UUID id, String name, UUID branchId, LocalDate validFrom, LocalDate validTo,
                              String dayKind, LocalDate specificDate, int minuteFrom, int minuteTo,
                              BigDecimal hourlyPrice, BigDecimal overtimeHourlyPrice,
                              int billingStepMinutes, int priority) {}
    public record BookingRow(UUID id, UUID branchId, String code, String contactName, String contactPhone,
                             String channel, Instant plannedStart, Instant plannedEnd, Instant actualCheckin,
                             Instant actualCheckout, String status, Instant cancelledAt, String cancellationReason,
                             int refundCutoffMinutes, UUID createdBy, String note, Instant createdAt) {}
    public record AllocationRow(UUID id, UUID bookingId, UUID roomId, String roomCode, String roomName,
                                Instant startsAt, Instant endsAt, String state, String changeReason,
                                Instant createdAt) {}
    public record ChargeRow(UUID id, String kind, String description, BigDecimal quantity,
                            BigDecimal unitPrice, BigDecimal amount, String status,
                            UUID priceRuleId, UUID comboId, String snapshot, Instant createdAt) {}

    public BranchScope branchScope(UUID userId) {
        List<BranchScope> rows = db.query("""
            SELECT b.id, b.name, b.timezone
            FROM gcmb.employee e
            JOIN gcmb.employee_assignment ea ON ea.employee_id=e.id
            JOIN gcmb.branch b ON b.id=ea.branch_id
            WHERE e.user_id=? AND e.status='ACTIVE'
              AND ea.valid_from <= CURRENT_TIMESTAMP
              AND (ea.valid_to IS NULL OR ea.valid_to > CURRENT_TIMESTAMP)
              AND b.status='ACTIVE'
            ORDER BY ea.valid_from DESC
            LIMIT 1
            """, (rs, rowNum) -> new BranchScope(rs.getObject("id", UUID.class), rs.getString("name"), ZoneId.of(rs.getString("timezone"))), userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<RoomTypeRow> roomTypes() {
        return db.query("SELECT id,code,name,mode,capacity FROM gcmb.room_type WHERE active=true ORDER BY name,code",
            (rs, rowNum) -> new RoomTypeRow(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"), rs.getString("mode"), rs.getInt("capacity")));
    }

    public RoomTypeRow roomType(UUID id) {
        List<RoomTypeRow> rows = db.query("SELECT id,code,name,mode,capacity FROM gcmb.room_type WHERE id=? AND active=true",
            (rs, rowNum) -> new RoomTypeRow(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"), rs.getString("mode"), rs.getInt("capacity")), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<ComboRow> combos(UUID roomTypeId) {
        return db.query("""
            SELECT id,code,version,room_type_id,name,duration_minutes,price,deposit_required,
                   overtime_hourly_price,valid_from,valid_to
            FROM gcmb.combo
            WHERE active=true AND room_type_id=? AND (valid_to IS NULL OR valid_to>CURRENT_TIMESTAMP)
            ORDER BY name,version DESC
            """, (rs, rowNum) -> combo(rs), roomTypeId);
    }

    public ComboRow combo(UUID id) {
        List<ComboRow> rows = db.query("""
            SELECT id,code,version,room_type_id,name,duration_minutes,price,deposit_required,
                   overtime_hourly_price,valid_from,valid_to
            FROM gcmb.combo WHERE id=? AND active=true
            """, (rs, rowNum) -> combo(rs), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static ComboRow combo(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ComboRow(rs.getObject("id", UUID.class), rs.getString("code"), rs.getInt("version"),
            rs.getObject("room_type_id", UUID.class), rs.getString("name"), rs.getInt("duration_minutes"),
            rs.getBigDecimal("price"), rs.getBigDecimal("deposit_required"), rs.getBigDecimal("overtime_hourly_price"),
            instant(rs, "valid_from"), instant(rs, "valid_to"));
    }

    public List<PricingRule> pricingRules(UUID roomTypeId, UUID branchId, LocalDate startDate, LocalDate endDate) {
        return db.query("""
            SELECT id,name,branch_id,valid_from,valid_to,day_kind,specific_date,minute_from,minute_to,
                   hourly_price,overtime_hourly_price,billing_step_minutes,priority
            FROM gcmb.price_rule
            WHERE room_type_id=? AND active=true AND (branch_id=? OR branch_id IS NULL)
              AND valid_from<=? AND (valid_to IS NULL OR valid_to>?)
              AND (day_kind<>'SPECIFIC' OR specific_date BETWEEN ? AND ?)
            ORDER BY (branch_id IS NOT NULL) DESC,priority DESC,
                     CASE day_kind WHEN 'SPECIFIC' THEN 4 WHEN 'HOLIDAY' THEN 3 WHEN 'WEEKEND' THEN 2 WHEN 'WEEKDAY' THEN 2 ELSE 1 END DESC,
                     id
            """, (rs, rowNum) -> new PricingRule(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getObject("branch_id", UUID.class), rs.getObject("valid_from", LocalDate.class),
                rs.getObject("valid_to", LocalDate.class), rs.getString("day_kind"),
                rs.getObject("specific_date", LocalDate.class), rs.getInt("minute_from"), rs.getInt("minute_to"),
                rs.getBigDecimal("hourly_price"), rs.getBigDecimal("overtime_hourly_price"),
                rs.getInt("billing_step_minutes"), rs.getInt("priority")), roomTypeId, branchId,
                endDate, startDate, startDate, endDate);
    }

    public List<RoomRow> availableRooms(UUID branchId, UUID roomTypeId, Instant start, Instant end) {
        return db.query("""
            SELECT r.id,r.code,r.name,r.capacity,r.operating_status,r.room_type_id,rt.name AS room_type_name
            FROM gcmb.room r JOIN gcmb.room_type rt ON rt.id=r.room_type_id
            WHERE r.branch_id=? AND r.room_type_id=? AND rt.active=true AND r.operating_status='AVAILABLE'
              AND NOT EXISTS (
                SELECT 1 FROM gcmb.room_allocation a JOIN gcmb.booking b ON b.id=a.booking_id
                WHERE a.room_id=r.id AND a.state IN ('RESERVED','OCCUPIED')
                  AND b.status IN ('CONFIRMED','IN_HOUSE')
                  AND a.starts_at < ? AND a.ends_at > ?
              )
            ORDER BY r.code
            """, (rs, rowNum) -> new RoomRow(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
                rs.getInt("capacity"), rs.getString("operating_status"), rs.getObject("room_type_id", UUID.class),
                rs.getString("room_type_name")), branchId, roomTypeId, ts(end), ts(start));
    }

    public RoomRow lockAvailableRoom(UUID roomId, UUID branchId) {
        List<RoomRow> rows = db.query("""
            SELECT r.id,r.code,r.name,r.capacity,r.operating_status,r.room_type_id,rt.name AS room_type_name
            FROM gcmb.room r JOIN gcmb.room_type rt ON rt.id=r.room_type_id
            WHERE r.id=? AND r.branch_id=? AND r.operating_status='AVAILABLE' AND rt.active=true
            FOR UPDATE OF r
            """, (rs, rowNum) -> new RoomRow(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
                rs.getInt("capacity"), rs.getString("operating_status"), rs.getObject("room_type_id", UUID.class),
                rs.getString("room_type_name")), roomId, branchId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public boolean hasRoomOverlap(UUID roomId, Instant start, Instant end, UUID excludeBookingId) {
        String sql = """
            SELECT EXISTS (
              SELECT 1 FROM gcmb.room_allocation a JOIN gcmb.booking b ON b.id=a.booking_id
              WHERE a.room_id=? AND a.state IN ('RESERVED','OCCUPIED') AND b.status IN ('CONFIRMED','IN_HOUSE')
                AND a.starts_at < ? AND a.ends_at > ?
            """;
        List<Object> params = new ArrayList<>(List.of(roomId, ts(end), ts(start)));
        if (excludeBookingId != null) { sql += " AND a.booking_id<>?"; params.add(excludeBookingId); }
        sql += ")";
        return Boolean.TRUE.equals(db.queryForObject(sql, Boolean.class, params.toArray()));
    }

    public UUID insertBooking(UUID branchId, String code, String contactName, String contactPhone, String channel,
                              Instant start, Instant end, int refundCutoffMinutes, UUID createdBy, String note) {
        return db.queryForObject("""
            INSERT INTO gcmb.booking(branch_id,code,contact_name,contact_phone,channel,planned_start,planned_end,
                                     status,refund_cutoff_minutes,created_by,note)
            VALUES (?,?,?,?,?,?,?,'CONFIRMED',?,?,?) RETURNING id
            """, UUID.class, branchId, code, contactName, contactPhone, channel, ts(start), ts(end), refundCutoffMinutes, createdBy, note);
    }

    public void updateBooking(UUID id, String contactName, String contactPhone, Instant start, Instant end, String note) {
        db.update("UPDATE gcmb.booking SET contact_name=?,contact_phone=?,planned_start=?,planned_end=?,note=? WHERE id=?",
            contactName, contactPhone, ts(start), ts(end), note, id);
    }

    public UUID insertAllocation(UUID branchId, UUID bookingId, UUID roomId, Instant start, Instant end, UUID actorId) {
        return db.queryForObject("""
            INSERT INTO gcmb.room_allocation(branch_id,booking_id,room_id,starts_at,ends_at,state,created_by)
            VALUES (?,?,?,?,?,'RESERVED',?) RETURNING id
            """, UUID.class, branchId, bookingId, roomId, ts(start), ts(end), actorId);
    }

    public AllocationRow currentAllocationForUpdate(UUID bookingId) {
        List<AllocationRow> rows = db.query("""
            SELECT a.id,a.booking_id,a.room_id,r.code AS room_code,r.name AS room_name,a.starts_at,a.ends_at,
                   a.state,a.change_reason,a.created_at
            FROM gcmb.room_allocation a JOIN gcmb.room r ON r.id=a.room_id
            WHERE a.booking_id=? AND a.state IN ('RESERVED','OCCUPIED')
            ORDER BY a.created_at DESC LIMIT 1 FOR UPDATE OF a
            """, BookingRepository::allocationRow, bookingId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void releaseAllocation(UUID allocationId, String reason) {
        db.update("UPDATE gcmb.room_allocation SET state='RELEASED',change_reason=? WHERE id=? AND state='RESERVED'", reason, allocationId);
    }

    public void cancelBooking(UUID id, Instant now, String reason) {
        db.update("UPDATE gcmb.booking SET status='CANCELLED',cancelled_at=?,cancellation_reason=? WHERE id=?", ts(now), reason, id);
    }

    public UUID lockBookingId(UUID bookingId, UUID branchId) {
        List<UUID> rows = db.query("SELECT id FROM gcmb.booking WHERE id=? AND branch_id=? FOR UPDATE",
            (rs, rowNum) -> rs.getObject("id", UUID.class), bookingId, branchId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public BookingRow booking(UUID bookingId, UUID branchId) {
        List<BookingRow> rows = db.query("""
            SELECT id,branch_id,code,contact_name,contact_phone,channel,planned_start,planned_end,actual_checkin,
                   actual_checkout,status,cancelled_at,cancellation_reason,refund_cutoff_minutes,created_by,note,created_at
            FROM gcmb.booking WHERE id=? AND branch_id=?
            """, BookingRepository::bookingRow, bookingId, branchId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static BookingRow bookingRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new BookingRow(rs.getObject("id", UUID.class), rs.getObject("branch_id", UUID.class), rs.getString("code"),
            rs.getString("contact_name"), rs.getString("contact_phone"), rs.getString("channel"), instant(rs, "planned_start"),
            instant(rs, "planned_end"), instant(rs, "actual_checkin"), instant(rs, "actual_checkout"), rs.getString("status"),
            instant(rs, "cancelled_at"), rs.getString("cancellation_reason"), rs.getInt("refund_cutoff_minutes"),
            rs.getObject("created_by", UUID.class), rs.getString("note"), instant(rs, "created_at"));
    }

    public List<AllocationRow> allocations(UUID bookingId) {
        return db.query("""
            SELECT a.id,a.booking_id,a.room_id,r.code AS room_code,r.name AS room_name,a.starts_at,a.ends_at,
                   a.state,a.change_reason,a.created_at
            FROM gcmb.room_allocation a JOIN gcmb.room r ON r.id=a.room_id
            WHERE a.booking_id=? ORDER BY a.created_at,a.id
            """, BookingRepository::allocationRow, bookingId);
    }

    private static AllocationRow allocationRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new AllocationRow(rs.getObject("id", UUID.class), rs.getObject("booking_id", UUID.class),
            rs.getObject("room_id", UUID.class), rs.getString("room_code"), rs.getString("room_name"),
            instant(rs, "starts_at"), instant(rs, "ends_at"), rs.getString("state"), rs.getString("change_reason"), instant(rs, "created_at"));
    }

    public void insertCharge(UUID bookingId, UUID allocationId, BookingDtos.ChargeLine line) {
        db.update("""
            INSERT INTO gcmb.booking_charge(booking_id,allocation_id,price_rule_id,combo_id,kind,description,
                                            quantity,unit_price,amount,pricing_snapshot,status)
            VALUES (?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),'ACTIVE')
            """, bookingId, allocationId, line.priceRuleId(), line.comboId(), line.kind(), line.description(),
            line.quantity(), line.unitPrice(), line.amount(), toJson(line.pricingSnapshot()));
    }

    public List<ChargeRow> charges(UUID bookingId) {
        return db.query("""
            SELECT id,kind,description,quantity,unit_price,amount,status,price_rule_id,combo_id,
                   pricing_snapshot::text AS snapshot,created_at
            FROM gcmb.booking_charge WHERE booking_id=? ORDER BY created_at,id
            """, (rs, rowNum) -> new ChargeRow(rs.getObject("id", UUID.class), rs.getString("kind"), rs.getString("description"),
                rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"), rs.getBigDecimal("amount"), rs.getString("status"),
                rs.getObject("price_rule_id", UUID.class), rs.getObject("combo_id", UUID.class), rs.getString("snapshot"), instant(rs, "created_at")), bookingId);
    }

    public void createDepositAccount(UUID bookingId) {
        db.update("INSERT INTO gcmb.deposit(booking_id) VALUES (?) ON CONFLICT (booking_id) DO NOTHING", bookingId);
    }

    public BigDecimal depositBalance(UUID bookingId) {
        List<BigDecimal> rows = db.query("""
            SELECT COALESCE(v.balance,0) AS balance
            FROM gcmb.deposit d LEFT JOIN gcmb.v_deposit_balance v ON v.id=d.id
            WHERE d.booking_id=?
            """, (rs, rowNum) -> rs.getBigDecimal("balance"), bookingId);
        return rows.isEmpty() ? BigDecimal.ZERO : rows.get(0);
    }

    public void forfeitDeposit(UUID bookingId, UUID actorId, BigDecimal amount, String reason, String idempotencyKey) {
        db.update("""
            INSERT INTO gcmb.deposit_entry(deposit_id,kind,amount,actor_id,reason,idempotency_key)
            SELECT id,'FORFEIT',?,?,?,? FROM gcmb.deposit WHERE booking_id=?
            """, amount, actorId, reason, idempotencyKey, bookingId);
    }

    public void event(UUID bookingId, UUID actorId, String type, String reason, Map<String, Object> details) {
        db.update("INSERT INTO gcmb.booking_event(booking_id,actor_id,event_type,reason,details) VALUES (?,?,?, ?,CAST(? AS jsonb))",
            bookingId, actorId, type, reason, toJson(details));
    }

    public List<BookingDtos.BookingSummary> findBookings(UUID branchId, String status, LocalDate dateFrom,
            LocalDate dateTo, ZoneId timezone, String search, Boolean hasDeposit, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE b.branch_id=?");
        List<Object> params = new ArrayList<>(); params.add(branchId);
        if (status != null && !status.isBlank()) { where.append(" AND b.status=?"); params.add(status); }
        if (dateFrom != null) { where.append(" AND b.planned_start>=?"); params.add(ts(dateFrom.atStartOfDay(timezone).toInstant())); }
        if (dateTo != null) { where.append(" AND b.planned_start<?"); params.add(ts(dateTo.plusDays(1).atStartOfDay(timezone).toInstant())); }
        if (search != null && !search.isBlank()) {
            where.append(" AND (b.code ILIKE ? OR b.contact_name ILIKE ? OR COALESCE(b.contact_phone,'') ILIKE ? OR EXISTS (SELECT 1 FROM gcmb.room_allocation a JOIN gcmb.room sr ON sr.id=a.room_id WHERE a.booking_id=b.id AND sr.code ILIKE ?))");
            String pattern = "%" + search.trim() + "%";
            params.add(pattern); params.add(pattern); params.add(pattern); params.add(pattern);
        }
        if (Boolean.TRUE.equals(hasDeposit)) where.append(" AND COALESCE(dep.balance,0)>0");
        if (Boolean.FALSE.equals(hasDeposit)) where.append(" AND COALESCE(dep.balance,0)=0");
        String from = """
            FROM gcmb.booking b
            LEFT JOIN LATERAL (SELECT a.room_id FROM gcmb.room_allocation a WHERE a.booking_id=b.id ORDER BY a.created_at DESC,a.id DESC LIMIT 1) latest ON true
            LEFT JOIN gcmb.room r ON r.id=latest.room_id
            LEFT JOIN (SELECT booking_id,SUM(amount) AS amount FROM gcmb.booking_charge WHERE status='ACTIVE' GROUP BY booking_id) ch ON ch.booking_id=b.id
            LEFT JOIN gcmb.deposit d ON d.booking_id=b.id
            LEFT JOIN gcmb.v_deposit_balance dep ON dep.id=d.id
            """;
        String sql = "SELECT b.id,b.code,b.contact_name,b.contact_phone,b.channel,b.planned_start,b.planned_end,b.status,latest.room_id,r.code room_code,r.name room_name,COALESCE(ch.amount,0) expected_amount,COALESCE(dep.balance,0) deposit_balance " + from + where + " ORDER BY b.planned_start DESC,b.created_at DESC LIMIT ? OFFSET ?";
        params.add(size); params.add((long) page * size);
        return db.query(sql, (rs, rowNum) -> new BookingDtos.BookingSummary(rs.getObject("id", UUID.class), rs.getString("code"),
            rs.getString("contact_name"), rs.getString("contact_phone"), rs.getString("channel"), instant(rs, "planned_start"),
            instant(rs, "planned_end"), rs.getString("status"), rs.getObject("room_id", UUID.class), rs.getString("room_code"),
            rs.getString("room_name"), rs.getBigDecimal("expected_amount"), rs.getBigDecimal("deposit_balance")), params.toArray());
    }

    public long countBookings(UUID branchId, String status, LocalDate dateFrom, LocalDate dateTo, ZoneId timezone,
                              String search, Boolean hasDeposit) {
        StringBuilder where = new StringBuilder(" WHERE b.branch_id=?");
        List<Object> params = new ArrayList<>(); params.add(branchId);
        if (status != null && !status.isBlank()) { where.append(" AND b.status=?"); params.add(status); }
        if (dateFrom != null) { where.append(" AND b.planned_start>=?"); params.add(ts(dateFrom.atStartOfDay(timezone).toInstant())); }
        if (dateTo != null) { where.append(" AND b.planned_start<?"); params.add(ts(dateTo.plusDays(1).atStartOfDay(timezone).toInstant())); }
        if (search != null && !search.isBlank()) {
            where.append(" AND (b.code ILIKE ? OR b.contact_name ILIKE ? OR COALESCE(b.contact_phone,'') ILIKE ? OR EXISTS (SELECT 1 FROM gcmb.room_allocation a JOIN gcmb.room r ON r.id=a.room_id WHERE a.booking_id=b.id AND r.code ILIKE ?))");
            String pattern = "%" + search.trim() + "%"; params.add(pattern); params.add(pattern); params.add(pattern); params.add(pattern);
        }
        if (hasDeposit != null) {
            where.append(hasDeposit ? " AND EXISTS (SELECT 1 FROM gcmb.deposit d JOIN gcmb.v_deposit_balance v ON v.id=d.id WHERE d.booking_id=b.id AND v.balance>0)" : " AND NOT EXISTS (SELECT 1 FROM gcmb.deposit d JOIN gcmb.v_deposit_balance v ON v.id=d.id WHERE d.booking_id=b.id AND v.balance>0)");
        }
        return db.queryForObject("SELECT count(*) FROM gcmb.booking b" + where, Long.class, params.toArray());
    }

    public BigDecimal activeChargeTotal(UUID bookingId) {
        return db.queryForObject("SELECT COALESCE(SUM(amount),0) FROM gcmb.booking_charge WHERE booking_id=? AND status='ACTIVE'", BigDecimal.class, bookingId);
    }

    private String toJson(Map<String, Object> value) {
        try { return json.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("Cannot encode booking snapshot", e); }
    }
}
