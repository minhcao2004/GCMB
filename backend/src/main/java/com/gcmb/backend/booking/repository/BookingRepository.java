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
// Lớp truy cập SQL; quy tắc cho phép thao tác và tính giá nằm ở service.
public class BookingRepository {
    private final JdbcTemplate db;
    private final ObjectMapper json;

    // JDBC lưu booking, lịch phòng, sổ cọc và snapshot JSON trong cùng database.
    public BookingRepository(JdbcTemplate db, ObjectMapper json) { this.db = db; this.json = json; }

    // Chuyển thời điểm Java sang tham số thời gian của JDBC.
    private static Timestamp ts(Instant value) { return Timestamp.from(value); }
    // Đọc timestamp có thể rỗng từ kết quả truy vấn.
    private static Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
    // Khôi phục giờ hẹn gốc của booking cũ đã check-in sớm.
    private static String scheduledStartExpression(String bookingAlias) {
        String checkInStart = "(SELECT NULLIF(checkin_event.details ->> 'plannedStartBeforeCheckIn', '')::timestamptz " +
            "FROM gcmb.booking_event checkin_event WHERE checkin_event.booking_id=" + bookingAlias + ".id " +
            "AND checkin_event.event_type='BOOKING_CHECKED_IN' " +
            "AND COALESCE((checkin_event.details ->> 'earlyCheckIn')::boolean,FALSE) " +
            "ORDER BY checkin_event.created_at DESC,checkin_event.id DESC LIMIT 1)";
        return "COALESCE(" + checkInStart + "," + bookingAlias + ".planned_start)";
    }
    // Khôi phục giờ check-in từ lịch sử hoặc allocation nếu cột cũ chưa có dữ liệu.
    private static String actualCheckinExpression(String bookingAlias) {
        String checkInEvent = "(SELECT NULLIF(checkin_event.details ->> 'actualCheckin', '')::timestamptz " +
            "FROM gcmb.booking_event checkin_event WHERE checkin_event.booking_id=" + bookingAlias + ".id " +
            "AND checkin_event.event_type='BOOKING_CHECKED_IN' " +
            "ORDER BY checkin_event.created_at DESC,checkin_event.id DESC LIMIT 1)";
        String firstAllocation = "(SELECT a.actual_start FROM gcmb.room_allocation a WHERE a.booking_id=" + bookingAlias + ".id " +
            "AND a.actual_start IS NOT NULL ORDER BY a.created_at,a.id LIMIT 1)";
        return "COALESCE(" + bookingAlias + ".actual_checkin," + checkInEvent + "," + firstAllocation + ")";
    }

    // Phạm vi chi nhánh và múi giờ gắn với nhân viên đang đăng nhập.
    public record BranchScope(UUID id, String name, ZoneId timezone) {}
    // Dữ liệu cấu hình giá/phòng được service dùng để xác thực và báo giá.
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
    // Ảnh chụp booking phục vụ các thao tác trong một giao dịch.
    public record BookingRow(UUID id, UUID branchId, String code, String contactName, String contactPhone, int guestCount,
                             String channel, Instant plannedStart, Instant plannedEnd, Instant actualCheckin,
                             Instant actualCheckout, String status, Instant cancelledAt, String cancellationReason,
                             int refundCutoffMinutes, UUID createdBy, String note, Instant createdAt) {}
    // Một chặng phòng riêng biệt để giữ được lịch sử đổi phòng và check-in.
    public record AllocationRow(UUID id, UUID bookingId, UUID roomId, UUID roomTypeId, String roomCode, String roomName, String roomTypeName,
                                String operatingStatus, Instant startsAt, Instant endsAt, String state, String changeReason,
                                Instant createdAt) {}
    // Dòng phí đã lưu với snapshot giá JSON cho đối soát.
    public record ChargeRow(UUID id, UUID allocationId, String kind, String description, BigDecimal quantity,
                            BigDecimal unitPrice, BigDecimal amount, String status,
                            UUID priceRuleId, UUID comboId, String snapshot, Instant createdAt) {}
    // Phiên đồng hồ giờ lẻ đã lưu trên máy chủ, không phụ thuộc trạng thái hộp thoại.
    public record MeterRow(UUID id, UUID allocationId, UUID priceRuleId, Instant startedAt, Instant billedFrom,
                           Instant originalAllocationEnd, Instant reservedUntil, BigDecimal hourlyPrice,
                           String status, Instant stoppedAt, Integer billedMinutes, BigDecimal amount) {}

    // Tìm chi nhánh đang hoạt động mà nhân viên được phép thao tác.
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

    // Lấy các hạng phòng đang hoạt động.
    public List<RoomTypeRow> roomTypes() {
        return db.query("SELECT id,code,name,mode,capacity FROM gcmb.room_type WHERE active=true ORDER BY name,code",
            (rs, rowNum) -> new RoomTypeRow(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"), rs.getString("mode"), rs.getInt("capacity")));
    }

    // Tìm một hạng phòng đang hoạt động theo ID.
    public RoomTypeRow roomType(UUID id) {
        List<RoomTypeRow> rows = db.query("SELECT id,code,name,mode,capacity FROM gcmb.room_type WHERE id=? AND active=true",
            (rs, rowNum) -> new RoomTypeRow(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"), rs.getString("mode"), rs.getInt("capacity")), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // Lấy combo hiện hành của hạng phòng.
    public List<ComboRow> combos(UUID roomTypeId) {
        return db.query("""
            SELECT id,code,version,room_type_id,name,duration_minutes,price,deposit_required,
                   overtime_hourly_price,valid_from,valid_to
            FROM gcmb.combo
            WHERE active=true AND room_type_id=? AND (valid_to IS NULL OR valid_to>CURRENT_TIMESTAMP)
            ORDER BY name,version DESC
            """, (rs, rowNum) -> combo(rs), roomTypeId);
    }

    // Tìm combo đang áp dụng để xác thực lựa chọn của client.
    public ComboRow combo(UUID id) {
        List<ComboRow> rows = db.query("""
            SELECT id,code,version,room_type_id,name,duration_minutes,price,deposit_required,
                   overtime_hourly_price,valid_from,valid_to
            FROM gcmb.combo WHERE id=? AND active=true
            """, (rs, rowNum) -> combo(rs), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // Ánh xạ hàng SQL sang dữ liệu combo.
    private static ComboRow combo(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ComboRow(rs.getObject("id", UUID.class), rs.getString("code"), rs.getInt("version"),
            rs.getObject("room_type_id", UUID.class), rs.getString("name"), rs.getInt("duration_minutes"),
            rs.getBigDecimal("price"), rs.getBigDecimal("deposit_required"), rs.getBigDecimal("overtime_hourly_price"),
            instant(rs, "valid_from"), instant(rs, "valid_to"));
    }

    // Lấy các rule giá có thể bao phủ khoảng ngày cần báo giá.
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

    // Tìm phòng không trùng lịch trong khoảng yêu cầu.
    public List<RoomRow> availableRooms(UUID branchId, UUID roomTypeId, Instant start, Instant end) {
        return db.query("""
            SELECT r.id,r.code,r.name,r.capacity,r.operating_status,r.room_type_id,rt.name AS room_type_name
            FROM gcmb.room r JOIN gcmb.room_type rt ON rt.id=r.room_type_id
            WHERE r.branch_id=? AND r.room_type_id=? AND rt.active=true AND r.operating_status='AVAILABLE'
              AND NOT EXISTS (
                SELECT 1 FROM gcmb.room_allocation a JOIN gcmb.booking b ON b.id=a.booking_id
                WHERE a.room_id=r.id AND a.state IN ('RESERVED','OCCUPIED')
                  AND b.status IN ('CONFIRMED','IN_HOUSE')
                  AND (a.state='OCCUPIED' AND b.status='IN_HOUSE'
                       OR (a.starts_at < ? AND a.ends_at > ?))
              )
            ORDER BY r.code
            """, (rs, rowNum) -> new RoomRow(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
                rs.getInt("capacity"), rs.getString("operating_status"), rs.getObject("room_type_id", UUID.class),
                rs.getString("room_type_name")), branchId, roomTypeId, ts(end), ts(start));
    }

    // Tìm phòng trống nhưng bỏ qua allocation của booking đang sửa.
    public List<RoomRow> availableRoomsForEdit(UUID branchId, Instant start, Instant end, UUID excludeBookingId) {
        return db.query("""
            SELECT r.id,r.code,r.name,r.capacity,r.operating_status,r.room_type_id,rt.name AS room_type_name
            FROM gcmb.room r JOIN gcmb.room_type rt ON rt.id=r.room_type_id
            WHERE r.branch_id=? AND rt.active=true AND r.operating_status='AVAILABLE'
              AND NOT EXISTS (
                SELECT 1 FROM gcmb.room_allocation a JOIN gcmb.booking b ON b.id=a.booking_id
                WHERE a.room_id=r.id AND a.state IN ('RESERVED','OCCUPIED')
                  AND b.status IN ('CONFIRMED','IN_HOUSE') AND b.id<>?
                  AND (a.state='OCCUPIED' AND b.status='IN_HOUSE'
                       OR (a.starts_at < ? AND a.ends_at > ?))
              )
            ORDER BY rt.name,r.code
            """, (rs, rowNum) -> new RoomRow(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
                rs.getInt("capacity"), rs.getString("operating_status"), rs.getObject("room_type_id", UUID.class),
                rs.getString("room_type_name")), branchId, excludeBookingId, ts(end), ts(start));
    }

    // Khóa hàng phòng trước khi đổi để giảm tranh chấp đồng thời.
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

    // Đọc phòng đích đang hoạt động trong phạm vi chi nhánh.
    public RoomRow availableRoom(UUID roomId, UUID branchId) {
        List<RoomRow> rows = db.query("""
            SELECT r.id,r.code,r.name,r.capacity,r.operating_status,r.room_type_id,rt.name AS room_type_name
            FROM gcmb.room r JOIN gcmb.room_type rt ON rt.id=r.room_type_id
            WHERE r.id=? AND r.branch_id=? AND r.operating_status='AVAILABLE' AND rt.active=true
            """, (rs, rowNum) -> new RoomRow(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
                rs.getInt("capacity"), rs.getString("operating_status"), rs.getObject("room_type_id", UUID.class),
                rs.getString("room_type_name")), roomId, branchId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // Phòng đang OCCUPIED vẫn bị giữ sau ends_at dự kiến cho tới khi kết thúc lượt sử dụng.
    public boolean hasRoomOverlap(UUID roomId, Instant start, Instant end, UUID excludeBookingId) {
        String sql = """
            SELECT EXISTS (
              SELECT 1 FROM gcmb.room_allocation a JOIN gcmb.booking b ON b.id=a.booking_id
              WHERE a.room_id=? AND a.state IN ('RESERVED','OCCUPIED') AND b.status IN ('CONFIRMED','IN_HOUSE')
                AND (a.state='OCCUPIED' AND b.status='IN_HOUSE'
                     OR (a.starts_at < ? AND a.ends_at > ?))
            """;
        List<Object> params = new ArrayList<>(List.of(roomId, ts(end), ts(start)));
        if (excludeBookingId != null) { sql += " AND a.booking_id<>?"; params.add(excludeBookingId); }
        sql += ")";
        return Boolean.TRUE.equals(db.queryForObject(sql, Boolean.class, params.toArray()));
    }

    // Tìm giờ bắt đầu booking kế tiếp để giới hạn thời gian giữ phòng.
    public Instant nextRoomBookingStart(UUID roomId, Instant after, UUID excludeBookingId) {
        List<Instant> rows = db.query("""
            SELECT a.starts_at FROM gcmb.room_allocation a
            JOIN gcmb.booking b ON b.id=a.booking_id
            WHERE a.room_id=? AND a.booking_id<>? AND a.state IN ('RESERVED','OCCUPIED')
              AND b.status IN ('CONFIRMED','IN_HOUSE') AND a.starts_at>=?
            ORDER BY a.starts_at LIMIT 1
            """, (rs, i) -> instant(rs, "starts_at"), roomId, excludeBookingId, ts(after));
        return rows.isEmpty() ? null : rows.get(0);
    }

    // Ghi booking và trả ID do database tạo.
    public UUID insertBooking(UUID branchId, String code, String contactName, String contactPhone, int guestCount, String channel,
                              Instant start, Instant end, int refundCutoffMinutes, UUID createdBy, String note) {
        return db.queryForObject("""
            INSERT INTO gcmb.booking(branch_id,code,contact_name,contact_phone,guest_count,channel,planned_start,planned_end,
                                     status,refund_cutoff_minutes,created_by,note)
            VALUES (?,?,?,?,?,?,?,?,'CONFIRMED',?,?,?) RETURNING id
            """, UUID.class, branchId, code, contactName, contactPhone, guestCount, channel, ts(start), ts(end), refundCutoffMinutes, createdBy, note);
    }

    // Cập nhật dữ liệu liên hệ và giờ hẹn của booking.
    public void updateBooking(UUID id, String contactName, String contactPhone, Instant start, Instant end, String note) {
        db.update("UPDATE gcmb.booking SET contact_name=?,contact_phone=?,planned_start=?,planned_end=?,note=? WHERE id=?",
            contactName, contactPhone, ts(start), ts(end), note, id);
    }

    // Tạo chặng giữ phòng ban đầu cho booking.
    public UUID insertAllocation(UUID branchId, UUID bookingId, UUID roomId, Instant start, Instant end, UUID actorId) {
        return db.queryForObject("""
            INSERT INTO gcmb.room_allocation(branch_id,booking_id,room_id,starts_at,ends_at,state,created_by)
            VALUES (?,?,?,?,?,'RESERVED',?) RETURNING id
            """, UUID.class, branchId, bookingId, roomId, ts(start), ts(end), actorId);
    }

    // Tạo chặng phòng mới và lưu nguyên nhân đổi phòng.
    public UUID insertChangedAllocation(UUID branchId, UUID bookingId, UUID roomId, Instant start, Instant end,
                                        UUID actorId, String cause, String reason) {
        return db.queryForObject("""
            INSERT INTO gcmb.room_allocation(branch_id,booking_id,room_id,starts_at,ends_at,state,created_by,change_cause,change_reason)
            VALUES (?,?,?,?,?,'RESERVED',?,?,?) RETURNING id
            """, UUID.class, branchId, bookingId, roomId, ts(start), ts(end), actorId, cause, reason);
    }

    // Khóa chặng phòng hiện tại trước khi thay đổi trạng thái hoặc thời gian.
    public AllocationRow currentAllocationForUpdate(UUID bookingId) {
        List<AllocationRow> rows = db.query("""
            SELECT a.id,a.booking_id,a.room_id,r.room_type_id,r.code AS room_code,r.name AS room_name,rt.name AS room_type_name,
                   r.operating_status,a.starts_at,a.ends_at,
                   a.state,a.change_reason,a.created_at
            FROM gcmb.room_allocation a JOIN gcmb.room r ON r.id=a.room_id JOIN gcmb.room_type rt ON rt.id=r.room_type_id
            WHERE a.booking_id=? AND a.state IN ('RESERVED','OCCUPIED')
            ORDER BY a.created_at DESC LIMIT 1 FOR UPDATE OF a,r
            """, BookingRepository::allocationRow, bookingId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // Đọc chặng phòng hiện tại để hiển thị hoặc báo giá.
    public AllocationRow currentAllocation(UUID bookingId) {
        List<AllocationRow> rows = db.query("""
            SELECT a.id,a.booking_id,a.room_id,r.room_type_id,r.code AS room_code,r.name AS room_name,rt.name AS room_type_name,
                   r.operating_status,a.starts_at,a.ends_at,a.state,a.change_reason,a.created_at
            FROM gcmb.room_allocation a JOIN gcmb.room r ON r.id=a.room_id JOIN gcmb.room_type rt ON rt.id=r.room_type_id
            WHERE a.booking_id=? AND a.state IN ('RESERVED','OCCUPIED')
            ORDER BY a.created_at DESC LIMIT 1
            """, BookingRepository::allocationRow, bookingId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // Chuyển booking sang đang sử dụng và lưu giờ check-in thực tế.
    public int checkInBooking(UUID bookingId, Instant now, Instant plannedEnd) {
        return db.update("UPDATE gcmb.booking SET status='IN_HOUSE',actual_checkin=?,planned_end=? WHERE id=? AND status='CONFIRMED'",
            ts(now), ts(plannedEnd), bookingId);
    }

    // Chuyển allocation đã giữ sang đang sử dụng.
    public int occupyAllocation(UUID allocationId, Instant now) {
        return db.update("UPDATE gcmb.room_allocation SET state='OCCUPIED',actual_start=? WHERE id=? AND state='RESERVED'",
            ts(now), allocationId);
    }

    // Đóng chặng phòng cũ tại thời điểm đổi phòng.
    public int finishOccupiedAllocation(UUID allocationId, Instant now, String cause, String reason) {
        return db.update("""
            UPDATE gcmb.room_allocation
            SET state='FINISHED',ends_at=?,actual_end=?,change_cause=?,change_reason=?
            WHERE id=? AND state='OCCUPIED' AND ends_at>?
            """, ts(now), ts(now), cause, reason, allocationId, ts(now));
    }

    // Mở rộng giờ kết thúc của chặng phòng nếu giá trị cũ còn khớp.
    public int updateOccupiedAllocationEnd(UUID allocationId, Instant oldEnd, Instant newEnd) {
        return db.update("UPDATE gcmb.room_allocation SET ends_at=? WHERE id=? AND state='OCCUPIED' AND ends_at=?",
            ts(newEnd), allocationId, ts(oldEnd));
    }

    // Mở rộng giờ trả dự kiến của booking nếu chưa bị phiên khác sửa.
    public int updateInHouseBookingEnd(UUID bookingId, Instant oldEnd, Instant newEnd) {
        return db.update("UPDATE gcmb.booking SET planned_end=? WHERE id=? AND status='IN_HOUSE' AND planned_end=?",
            ts(newEnd), bookingId, ts(oldEnd));
    }

    // Đọc phiên đồng hồ giờ lẻ đã lưu của booking.
    public MeterRow meter(UUID bookingId) {
        List<MeterRow> rows = db.query("""
            SELECT id,allocation_id,price_rule_id,started_at,billed_from,original_allocation_end,
                   reserved_until,hourly_price,status,stopped_at,billed_minutes,amount
            FROM gcmb.booking_hourly_meter WHERE booking_id=?
            """, (rs, i) -> new MeterRow(rs.getObject("id", UUID.class), rs.getObject("allocation_id", UUID.class),
                rs.getObject("price_rule_id", UUID.class), instant(rs, "started_at"), instant(rs, "billed_from"),
                instant(rs, "original_allocation_end"), instant(rs, "reserved_until"), rs.getBigDecimal("hourly_price"),
                rs.getString("status"), instant(rs, "stopped_at"), (Integer) rs.getObject("billed_minutes"),
                rs.getBigDecimal("amount")), bookingId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // Lưu mốc bắt đầu, đơn giá chốt và hạn giữ phòng của đồng hồ.
    public void startMeter(UUID bookingId, UUID allocationId, UUID priceRuleId, Instant startedAt,
                           Instant billedFrom, Instant originalEnd, Instant reservedUntil,
                           BigDecimal hourlyPrice, UUID actorId) {
        db.update("""
            INSERT INTO gcmb.booking_hourly_meter(booking_id,allocation_id,price_rule_id,started_at,billed_from,
                original_allocation_end,reserved_until,hourly_price,started_by)
            VALUES (?,?,?,?,?,?,?,?,?)
            """, bookingId, allocationId, priceRuleId, ts(startedAt), ts(billedFrom), ts(originalEnd),
            ts(reservedUntil), hourlyPrice, actorId);
    }

    // Chốt số phút, số tiền và người dừng đồng hồ.
    public void stopMeter(UUID meterId, Instant stoppedAt, int billedMinutes, BigDecimal amount, UUID actorId) {
        db.update("""
            UPDATE gcmb.booking_hourly_meter SET status='STOPPED',stopped_at=?,billed_minutes=?,amount=?,stopped_by=?
            WHERE id=? AND status='RUNNING'
            """, ts(stoppedAt), billedMinutes, amount, actorId, meterId);
    }

    // Dịch chặng phòng khi khách check-in sớm và kích hoạt phòng.
    public int shiftAndOccupyAllocation(UUID allocationId, Instant now, Instant startsAt, Instant endsAt) {
        return db.update("""
            UPDATE gcmb.room_allocation
            SET starts_at=?,ends_at=?,state='OCCUPIED',actual_start=?,
                change_reason='Khách nhận phòng sớm; khung giờ được dời để giữ nguyên thời lượng'
            WHERE id=? AND state='RESERVED'
            """, ts(startsAt), ts(endsAt), ts(now), allocationId);
    }

    // Giải phóng phòng đã giữ khi booking không còn sử dụng.
    public void releaseAllocation(UUID allocationId, String reason) {
        db.update("UPDATE gcmb.room_allocation SET state='RELEASED',change_reason=? WHERE id=? AND state='RESERVED'", reason, allocationId);
    }

    // Chuyển booking sang hủy và lưu lý do cùng thời điểm.
    public void cancelBooking(UUID id, Instant now, String reason) {
        db.update("UPDATE gcmb.booking SET status='CANCELLED',cancelled_at=?,cancellation_reason=? WHERE id=?", ts(now), reason, id);
    }

    // Chuyển booking đã đến giờ sang trạng thái khách vắng mặt.
    public int markNoShow(UUID id) {
        return db.update("UPDATE gcmb.booking SET status='NO_SHOW' WHERE id=? AND status='CONFIRMED'", id);
    }

    // Khóa tài khoản cọc để các bút toán không ghi đè nhau.
    public UUID lockDeposit(UUID bookingId) {
        List<UUID> rows = db.query("SELECT id FROM gcmb.deposit WHERE booking_id=? FOR UPDATE",
            (rs, rowNum) -> rs.getObject("id", UUID.class), bookingId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // Tạo yêu cầu hoàn cọc chờ xử lý, chưa ghi nhận đã thanh toán.
    public UUID createDepositRefundRequest(UUID branchId, UUID bookingId, UUID depositId, BigDecimal amount,
            String reason, String beneficiaryName, String bankName, String bankAccount, UUID actorId) {
        return db.queryForObject("""
            INSERT INTO gcmb.refund_request(branch_id,booking_id,deposit_id,kind,amount,reason,
                                            beneficiary_name,bank_name,bank_account,requested_by,status)
            VALUES (?,?,?,'DEPOSIT',?,?,?,?,?,?,'REQUESTED')
            RETURNING id
            """, UUID.class, branchId, bookingId, depositId, amount, reason, beneficiaryName, bankName, bankAccount, actorId);
    }

    // Khóa booking thuộc chi nhánh để serialize các cập nhật đồng thời.
    public UUID lockBookingId(UUID bookingId, UUID branchId) {
        List<UUID> rows = db.query("SELECT id FROM gcmb.booking WHERE id=? AND branch_id=? FOR UPDATE",
            (rs, rowNum) -> rs.getObject("id", UUID.class), bookingId, branchId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // Đọc booking theo ID và chi nhánh, kể cả dữ liệu giờ được khôi phục từ lịch sử.
    public BookingRow booking(UUID bookingId, UUID branchId) {
        String sql = "SELECT b.id,b.branch_id,b.code,b.contact_name,b.contact_phone,b.guest_count,b.channel," +
            scheduledStartExpression("b") + " AS planned_start,b.planned_end," + actualCheckinExpression("b") + " AS actual_checkin,b.actual_checkout,b.status," +
            "b.cancelled_at,b.cancellation_reason,b.refund_cutoff_minutes,b.created_by,b.note,b.created_at " +
            "FROM gcmb.booking b WHERE b.id=? AND b.branch_id=?";
        List<BookingRow> rows = db.query(sql, BookingRepository::bookingRow, bookingId, branchId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // Ánh xạ hàng SQL sang booking nội bộ.
    private static BookingRow bookingRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new BookingRow(rs.getObject("id", UUID.class), rs.getObject("branch_id", UUID.class), rs.getString("code"),
            rs.getString("contact_name"), rs.getString("contact_phone"), rs.getInt("guest_count"), rs.getString("channel"), instant(rs, "planned_start"),
            instant(rs, "planned_end"), instant(rs, "actual_checkin"), instant(rs, "actual_checkout"), rs.getString("status"),
            instant(rs, "cancelled_at"), rs.getString("cancellation_reason"), rs.getInt("refund_cutoff_minutes"),
            rs.getObject("created_by", UUID.class), rs.getString("note"), instant(rs, "created_at"));
    }

    // Lấy toàn bộ lịch sử các chặng phòng của booking.
    public List<AllocationRow> allocations(UUID bookingId) {
        return db.query("""
            SELECT a.id,a.booking_id,a.room_id,r.room_type_id,r.code AS room_code,r.name AS room_name,rt.name AS room_type_name,
                   r.operating_status,a.starts_at,a.ends_at,
                   a.state,a.change_reason,a.created_at
            FROM gcmb.room_allocation a JOIN gcmb.room r ON r.id=a.room_id JOIN gcmb.room_type rt ON rt.id=r.room_type_id
            WHERE a.booking_id=? ORDER BY a.created_at,a.id
            """, BookingRepository::allocationRow, bookingId);
    }

    // Ánh xạ hàng SQL sang chặng phòng nội bộ.
    private static AllocationRow allocationRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new AllocationRow(rs.getObject("id", UUID.class), rs.getObject("booking_id", UUID.class),
            rs.getObject("room_id", UUID.class), rs.getObject("room_type_id", UUID.class), rs.getString("room_code"), rs.getString("room_name"),
            rs.getString("room_type_name"), rs.getString("operating_status"),
            instant(rs, "starts_at"), instant(rs, "ends_at"), rs.getString("state"), rs.getString("change_reason"), instant(rs, "created_at"));
    }

    // Ghi dòng phí với snapshot giá để đối soát về sau.
    public void insertCharge(UUID bookingId, UUID allocationId, BookingDtos.ChargeLine line) {
        db.update("""
            INSERT INTO gcmb.booking_charge(booking_id,allocation_id,price_rule_id,combo_id,kind,description,
                                            quantity,unit_price,amount,pricing_snapshot,status)
            VALUES (?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),'ACTIVE')
            """, bookingId, allocationId, line.priceRuleId(), line.comboId(), line.kind(), line.description(),
            line.quantity(), line.unitPrice(), line.amount(), toJson(line.pricingSnapshot()));
    }

    // Lấy toàn bộ dòng phí, kể cả dòng đã bị thay thế.
    public List<ChargeRow> charges(UUID bookingId) {
        return db.query("""
            SELECT id,allocation_id,kind,description,quantity,unit_price,amount,status,price_rule_id,combo_id,
                   pricing_snapshot::text AS snapshot,created_at
            FROM gcmb.booking_charge WHERE booking_id=? ORDER BY created_at,id
            """, (rs, rowNum) -> new ChargeRow(rs.getObject("id", UUID.class), rs.getObject("allocation_id", UUID.class), rs.getString("kind"), rs.getString("description"),
                rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"), rs.getBigDecimal("amount"), rs.getString("status"),
                rs.getObject("price_rule_id", UUID.class), rs.getObject("combo_id", UUID.class), rs.getString("snapshot"), instant(rs, "created_at")), bookingId);
    }

    // Lấy các dòng phí còn hiệu lực của một chặng phòng.
    public List<ChargeRow> activeChargesForAllocation(UUID allocationId) {
        return db.query("""
            SELECT id,allocation_id,kind,description,quantity,unit_price,amount,status,price_rule_id,combo_id,
                   pricing_snapshot::text AS snapshot,created_at
            FROM gcmb.booking_charge WHERE allocation_id=? AND status='ACTIVE' ORDER BY created_at,id
            """, (rs, rowNum) -> new ChargeRow(rs.getObject("id", UUID.class), rs.getObject("allocation_id", UUID.class),
                rs.getString("kind"), rs.getString("description"), rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"),
                rs.getBigDecimal("amount"), rs.getString("status"), rs.getObject("price_rule_id", UUID.class),
                rs.getObject("combo_id", UUID.class), rs.getString("snapshot"), instant(rs, "created_at")), allocationId);
    }

    // Đánh dấu dòng phí cũ bị thay thế khi tách giá đổi phòng.
    public int supersedeCharge(UUID chargeId) {
        return db.update("UPDATE gcmb.booking_charge SET status='SUPERSEDED' WHERE id=? AND status='ACTIVE'", chargeId);
    }

    // Tạo tài khoản cọc cho booking mới.
    public void createDepositAccount(UUID bookingId) {
        db.update("INSERT INTO gcmb.deposit(booking_id) VALUES (?) ON CONFLICT (booking_id) DO NOTHING", bookingId);
    }

    // Ghi thanh toán và bút toán nhận cọc trong cùng giao dịch.
    public void receiveDeposit(UUID branchId, UUID bookingId, UUID actorId, BigDecimal amount,
            String method, String reference) {
        BigDecimal cashTendered = "CASH".equals(method) ? amount : null;
        BigDecimal cashChange = "CASH".equals(method) ? BigDecimal.ZERO : null;
        String paymentKey = "BOOKING-DEPOSIT-" + UUID.randomUUID();
        String entryKey = "BOOKING-DEPOSIT-" + UUID.randomUUID();
        UUID paymentId = db.queryForObject("""
            INSERT INTO gcmb.payment(branch_id,booking_id,purpose,method,amount,cash_tendered,cash_change,
                                     receipt_reference,received_at,received_by,idempotency_key)
            VALUES (?,?,'DEPOSIT',?,?,?,?,?,CURRENT_TIMESTAMP,?,?)
            RETURNING id
            """, UUID.class, branchId, bookingId, method, amount, cashTendered, cashChange, reference,
            actorId, paymentKey);
        int inserted = db.update("""
            INSERT INTO gcmb.deposit_entry(deposit_id,kind,amount,payment_id,actor_id,reason,idempotency_key)
            SELECT d.id,'RECEIVE',?,?,?,'Cọc đã thu khi tạo booking',?
            FROM gcmb.deposit d
            WHERE d.booking_id=?
            """, amount, paymentId, actorId, entryKey, bookingId);
        if (inserted != 1) throw new IllegalStateException("Booking deposit account was not created");
    }

    // Tính số dư cọc từ các bút toán thay vì trường tổng có thể cũ.
    public BigDecimal depositBalance(UUID bookingId) {
        List<BigDecimal> rows = db.query("""
            SELECT COALESCE(v.balance,0) AS balance
            FROM gcmb.deposit d LEFT JOIN gcmb.v_deposit_balance v ON v.id=d.id
            WHERE d.booking_id=?
            """, (rs, rowNum) -> rs.getBigDecimal("balance"), bookingId);
        return rows.isEmpty() ? BigDecimal.ZERO : rows.get(0);
    }

    // Tổng hợp khoản đã thu, vẫn hiển thị sau hoàn hoặc giữ cọc.
    public BigDecimal depositReceivedAmount(UUID bookingId) {
        return db.queryForObject("""
            SELECT GREATEST(
                COALESCE((
                    SELECT SUM(e.amount)
                    FROM gcmb.deposit d
                    JOIN gcmb.deposit_entry e ON e.deposit_id=d.id AND e.kind='RECEIVE'
                    WHERE d.booking_id=?
                ),0),
                COALESCE((
                    SELECT NULLIF(be.details ->> 'depositAmount','')::numeric
                    FROM gcmb.booking_event be
                    WHERE be.booking_id=? AND be.event_type='BOOKING_CANCELLED'
                    ORDER BY be.created_at DESC,be.id DESC
                    LIMIT 1
                ),0)
            )
            """, BigDecimal.class, bookingId, bookingId);
    }

    // Tổng hợp cọc bị giữ và dữ liệu lịch sử cần dự phòng.
    public BigDecimal depositForfeitedAmount(UUID bookingId) {
        return db.queryForObject("""
            SELECT GREATEST(
                COALESCE((
                    SELECT SUM(e.amount)
                    FROM gcmb.deposit d
                    JOIN gcmb.deposit_entry e ON e.deposit_id=d.id AND e.kind='FORFEIT'
                    WHERE d.booking_id=?
                ),0),
                COALESCE((
                    SELECT NULLIF(be.details ->> 'depositAmount','')::numeric
                    FROM gcmb.booking_event be
                    WHERE be.booking_id=? AND be.event_type='BOOKING_CANCELLED'
                      AND be.details ->> 'depositOutcome'='NON_REFUNDABLE'
                    ORDER BY be.created_at DESC,be.id DESC
                    LIMIT 1
                ),0)
            )
            """, BigDecimal.class, bookingId, bookingId);
    }

    // Suy ra kết quả xử lý cọc khi hủy từ sự kiện và sổ cọc.
    public String depositCancellationOutcome(UUID bookingId) {
        List<String> outcomes = db.query("""
            SELECT details ->> 'depositOutcome'
            FROM gcmb.booking_event
            WHERE booking_id=? AND event_type='BOOKING_CANCELLED'
            ORDER BY created_at DESC,id DESC
            LIMIT 1
            """, (rs, rowNum) -> rs.getString(1), bookingId);
        String outcome = outcomes.isEmpty() ? null : outcomes.get(0);
        if (outcome != null) return outcome;
        return db.queryForObject("""
            SELECT CASE
                WHEN EXISTS (
                    SELECT 1 FROM gcmb.deposit d
                    JOIN gcmb.deposit_entry e ON e.deposit_id=d.id
                    WHERE d.booking_id=? AND e.kind='FORFEIT'
                ) THEN 'NON_REFUNDABLE'
                WHEN EXISTS (
                    SELECT 1 FROM gcmb.refund_request r
                    WHERE r.booking_id=? AND r.kind='DEPOSIT'
                ) THEN 'REFUND_ELIGIBLE'
                WHEN NOT EXISTS (
                    SELECT 1 FROM gcmb.deposit d
                    JOIN gcmb.deposit_entry e ON e.deposit_id=d.id
                    WHERE d.booking_id=? AND e.kind='RECEIVE'
                ) THEN 'NO_DEPOSIT'
                ELSE NULL
            END
            """, String.class, bookingId, bookingId, bookingId);
    }

    // Xác định phương thức thu cọc, trả MULTIPLE nếu có nhiều nguồn.
    public String depositPaymentMethod(UUID bookingId) {
        List<String> methods = db.query("""
            SELECT DISTINCT p.method
            FROM gcmb.deposit d
            JOIN gcmb.deposit_entry e ON e.deposit_id=d.id AND e.kind='RECEIVE'
            JOIN gcmb.payment p ON p.id=e.payment_id
            WHERE d.booking_id=?
            ORDER BY p.method
            """, (rs, rowNum) -> rs.getString("method"), bookingId);
        if (methods.isEmpty()) return null;
        return methods.size() == 1 ? methods.get(0) : "MULTIPLE";
    }

    // Ghi bút toán giữ cọc với khóa chống ghi lặp.
    public void forfeitDeposit(UUID bookingId, UUID actorId, BigDecimal amount, String reason, String idempotencyKey) {
        db.update("""
            INSERT INTO gcmb.deposit_entry(deposit_id,kind,amount,actor_id,reason,idempotency_key)
            SELECT id,'FORFEIT',?,?,?,? FROM gcmb.deposit WHERE booking_id=?
            """, amount, actorId, reason, idempotencyKey, bookingId);
    }

    // Lưu sự kiện và chi tiết JSON để đối soát lịch sử booking.
    public void event(UUID bookingId, UUID actorId, String type, String reason, Map<String, Object> details) {
        db.update("INSERT INTO gcmb.booking_event(booking_id,actor_id,event_type,reason,details) VALUES (?,?,?, ?,CAST(? AS jsonb))",
            bookingId, actorId, type, reason, toJson(details));
    }

    // Truy vấn trang booking cùng bộ lọc và thông tin tổng hợp cho danh sách.
    public List<BookingDtos.BookingSummary> findBookings(UUID branchId, String status, LocalDate dateFrom,
            LocalDate dateTo, ZoneId timezone, String search, Boolean hasDeposit, int page, int size) {
        String plannedStart = scheduledStartExpression("b");
        StringBuilder where = new StringBuilder(" WHERE b.branch_id=?");
        List<Object> params = new ArrayList<>(); params.add(branchId);
        if (status != null && !status.isBlank()) { where.append(" AND b.status=?"); params.add(status); }
        if (dateFrom != null) {
            Instant rangeStart = dateFrom.atStartOfDay(timezone).toInstant();
            where.append(" AND (" + plannedStart + ">=? OR (b.status='IN_HOUSE' AND EXISTS (SELECT 1 FROM gcmb.room_allocation active_allocation WHERE active_allocation.booking_id=b.id AND active_allocation.state='OCCUPIED' AND active_allocation.ends_at>?)))");
            params.add(ts(rangeStart)); params.add(ts(rangeStart));
        }
        if (dateTo != null) { where.append(" AND " + plannedStart + "<?"); params.add(ts(dateTo.plusDays(1).atStartOfDay(timezone).toInstant())); }
        if (search != null && !search.isBlank()) {
            where.append(" AND (b.code ILIKE ? OR b.contact_name ILIKE ? OR COALESCE(b.contact_phone,'') ILIKE ? OR EXISTS (SELECT 1 FROM gcmb.room_allocation a JOIN gcmb.room sr ON sr.id=a.room_id WHERE a.booking_id=b.id AND sr.code ILIKE ?))");
            String pattern = "%" + search.trim() + "%";
            params.add(pattern); params.add(pattern); params.add(pattern); params.add(pattern);
        }
        if (Boolean.TRUE.equals(hasDeposit)) where.append(" AND COALESCE(received.amount,0)>0");
        if (Boolean.FALSE.equals(hasDeposit)) where.append(" AND COALESCE(received.amount,0)=0");
        String from = """
            FROM gcmb.booking b
            LEFT JOIN LATERAL (SELECT a.room_id FROM gcmb.room_allocation a WHERE a.booking_id=b.id ORDER BY a.created_at DESC,a.id DESC LIMIT 1) latest ON true
             LEFT JOIN gcmb.room r ON r.id=latest.room_id
             LEFT JOIN gcmb.room_type rt ON rt.id=r.room_type_id
            LEFT JOIN (SELECT booking_id,SUM(amount) AS amount FROM gcmb.booking_charge WHERE status='ACTIVE' GROUP BY booking_id) ch ON ch.booking_id=b.id
            LEFT JOIN gcmb.deposit d ON d.booking_id=b.id
            LEFT JOIN gcmb.v_deposit_balance dep ON dep.id=d.id
            LEFT JOIN LATERAL (
                SELECT GREATEST(
                    COALESCE((
                        SELECT SUM(e.amount)
                        FROM gcmb.deposit d1
                        JOIN gcmb.deposit_entry e ON e.deposit_id=d1.id AND e.kind='RECEIVE'
                        WHERE d1.booking_id=b.id
                    ),0),
                    COALESCE((
                        SELECT NULLIF(be.details ->> 'depositAmount','')::numeric
                        FROM gcmb.booking_event be
                        WHERE be.booking_id=b.id AND be.event_type='BOOKING_CANCELLED'
                        ORDER BY be.created_at DESC,be.id DESC
                        LIMIT 1
                    ),0)
                ) AS amount
            ) received ON true
            """;
        String sql = "SELECT b.id,b.code,b.contact_name,b.contact_phone,b.guest_count,b.channel," + plannedStart + " AS planned_start,b.planned_end," + actualCheckinExpression("b") + " AS actual_checkin,b.status,latest.room_id,r.code room_code,r.name room_name,rt.name room_type_name,r.operating_status room_operating_status,COALESCE(ch.amount,0) expected_amount,COALESCE(dep.balance,0) deposit_balance,COALESCE(received.amount,0) deposit_received_amount " + from + where + " ORDER BY " + plannedStart + " ASC,b.created_at DESC LIMIT ? OFFSET ?";
        params.add(size); params.add((long) page * size);
        return db.query(sql, (rs, rowNum) -> new BookingDtos.BookingSummary(rs.getObject("id", UUID.class), rs.getString("code"),
            rs.getString("contact_name"), rs.getString("contact_phone"), rs.getString("channel"), instant(rs, "planned_start"),
            instant(rs, "planned_end"), instant(rs, "actual_checkin"), rs.getString("status"), rs.getObject("room_id", UUID.class), rs.getString("room_code"),
            rs.getString("room_name"), rs.getString("room_type_name"), rs.getBigDecimal("expected_amount"), rs.getBigDecimal("deposit_balance"),
            rs.getBigDecimal("deposit_received_amount"),
            rs.getInt("guest_count"), rs.getString("room_operating_status")), params.toArray());
    }

    // Đếm booking theo cùng bộ lọc để tính tổng số trang.
    public long countBookings(UUID branchId, String status, LocalDate dateFrom, LocalDate dateTo, ZoneId timezone,
                              String search, Boolean hasDeposit) {
        String plannedStart = scheduledStartExpression("b");
        StringBuilder where = new StringBuilder(" WHERE b.branch_id=?");
        List<Object> params = new ArrayList<>(); params.add(branchId);
        if (status != null && !status.isBlank()) { where.append(" AND b.status=?"); params.add(status); }
        if (dateFrom != null) {
            Instant rangeStart = dateFrom.atStartOfDay(timezone).toInstant();
            where.append(" AND (" + plannedStart + ">=? OR (b.status='IN_HOUSE' AND EXISTS (SELECT 1 FROM gcmb.room_allocation active_allocation WHERE active_allocation.booking_id=b.id AND active_allocation.state='OCCUPIED' AND active_allocation.ends_at>?)))");
            params.add(ts(rangeStart)); params.add(ts(rangeStart));
        }
        if (dateTo != null) { where.append(" AND " + plannedStart + "<?"); params.add(ts(dateTo.plusDays(1).atStartOfDay(timezone).toInstant())); }
        if (search != null && !search.isBlank()) {
            where.append(" AND (b.code ILIKE ? OR b.contact_name ILIKE ? OR COALESCE(b.contact_phone,'') ILIKE ? OR EXISTS (SELECT 1 FROM gcmb.room_allocation a JOIN gcmb.room r ON r.id=a.room_id WHERE a.booking_id=b.id AND r.code ILIKE ?))");
            String pattern = "%" + search.trim() + "%"; params.add(pattern); params.add(pattern); params.add(pattern); params.add(pattern);
        }
        if (hasDeposit != null) {
            where.append(hasDeposit
                ? " AND (EXISTS (SELECT 1 FROM gcmb.deposit d JOIN gcmb.deposit_entry e ON e.deposit_id=d.id AND e.kind='RECEIVE' WHERE d.booking_id=b.id) OR EXISTS (SELECT 1 FROM gcmb.booking_event be WHERE be.booking_id=b.id AND be.event_type='BOOKING_CANCELLED' AND COALESCE(NULLIF(be.details ->> 'depositAmount','')::numeric,0)>0))"
                : " AND NOT (EXISTS (SELECT 1 FROM gcmb.deposit d JOIN gcmb.deposit_entry e ON e.deposit_id=d.id AND e.kind='RECEIVE' WHERE d.booking_id=b.id) OR EXISTS (SELECT 1 FROM gcmb.booking_event be WHERE be.booking_id=b.id AND be.event_type='BOOKING_CANCELLED' AND COALESCE(NULLIF(be.details ->> 'depositAmount','')::numeric,0)>0))");
        }
        return db.queryForObject("SELECT count(*) FROM gcmb.booking b" + where, Long.class, params.toArray());
    }

    // Cộng các dòng phí còn hiệu lực của booking.
    public BigDecimal activeChargeTotal(UUID bookingId) {
        return db.queryForObject("SELECT COALESCE(SUM(amount),0) FROM gcmb.booking_charge WHERE booking_id=? AND status='ACTIVE'", BigDecimal.class, bookingId);
    }

    // Mã hóa snapshot hoặc chi tiết sự kiện trước khi lưu JSONB.
    private String toJson(Map<String, Object> value) {
        try { return json.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("Cannot encode booking snapshot", e); }
    }
}
