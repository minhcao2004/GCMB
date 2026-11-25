package com.gcmb.backend.dashboard.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
// Truy vấn số liệu vận hành cho dashboard trụ sở và dashboard quản lý cơ sở.
public class DashboardRepository {

    private final JdbcTemplate jdbcTemplate;

    public DashboardRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> findActiveBranchOptions() {
        return jdbcTemplate.queryForList("SELECT id, name, code FROM gcmb.branch WHERE status='ACTIVE' ORDER BY name, id");
    }

    public String findBranchTimezone(UUID branchId) {
        return jdbcTemplate.queryForObject("SELECT timezone FROM gcmb.branch WHERE id=?", String.class, branchId);
    }

    public boolean isBranchManagerOf(UUID userId, UUID branchId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM gcmb.branch_manager_assignment WHERE user_id=? AND branch_id=?)",
            Boolean.class, userId, branchId));
    }

    public Map<String, Object> branchOperations(UUID branchId) {
        Map<String, Object> operational = jdbcTemplate.queryForMap("""
            SELECT count(*) FILTER (WHERE r.operating_status <> 'INACTIVE') AS "roomCount",
                   count(*) FILTER (WHERE r.operating_status NOT IN ('INACTIVE','UNDER_MAINTENANCE') AND EXISTS (SELECT 1 FROM gcmb.room_allocation a WHERE a.room_id=r.id AND a.state='OCCUPIED')) AS "occupiedRooms",
                   count(*) FILTER (WHERE r.operating_status NOT IN ('INACTIVE','UNDER_MAINTENANCE')
                     AND NOT EXISTS (SELECT 1 FROM gcmb.room_allocation a WHERE a.room_id=r.id AND a.state='OCCUPIED')
                     AND EXISTS (SELECT 1 FROM gcmb.room_allocation a WHERE a.room_id=r.id AND a.state='RESERVED')) AS "bookedRooms",
                   count(*) FILTER (WHERE r.operating_status='UNDER_MAINTENANCE') AS "maintenanceRooms"
            FROM gcmb.room r WHERE r.branch_id=?
            """, branchId);
        operational.put("openIncidents", jdbcTemplate.queryForObject("SELECT count(*) FROM gcmb.asset_incident WHERE branch_id=? AND status NOT IN ('VERIFIED','CLOSED')", Long.class, branchId));
        operational.put("lowStockItems", jdbcTemplate.queryForObject("""
            SELECT count(*) FROM gcmb.inventory_item i JOIN gcmb.stock_balance s ON s.inventory_item_id=i.id
            WHERE i.branch_id=? AND i.active AND s.minimum_quantity>0 AND s.quantity<s.minimum_quantity
            """, Long.class, branchId));
        operational.put("staffPresent", jdbcTemplate.queryForObject("""
            SELECT count(DISTINCT a.employee_id) FROM gcmb.shift_assignment a JOIN gcmb.scheduled_shift s ON s.id=a.shift_id
            JOIN gcmb.attendance t ON t.assignment_id=a.id
            WHERE s.branch_id=? AND s.work_date=(CURRENT_TIMESTAMP AT TIME ZONE (SELECT timezone FROM gcmb.branch WHERE id=?))::date
              AND a.status='CONFIRMED' AND s.status<>'CANCELLED' AND t.actual_in IS NOT NULL
            """, Long.class, branchId, branchId));
        operational.put("staffTotal", jdbcTemplate.queryForObject("""
            SELECT count(DISTINCT a.employee_id) FROM gcmb.shift_assignment a JOIN gcmb.scheduled_shift s ON s.id=a.shift_id
            WHERE s.branch_id=? AND s.work_date=(CURRENT_TIMESTAMP AT TIME ZONE (SELECT timezone FROM gcmb.branch WHERE id=?))::date
              AND a.status='CONFIRMED' AND s.status<>'CANCELLED'
            """, Long.class, branchId, branchId));
        return operational;
    }

    public List<Map<String, Object>> customerSourceCounts(LocalDate from, LocalDate toExclusive, UUID branchId) {
        return jdbcTemplate.queryForList("""
            WITH channels(channel, sort_order) AS (
              VALUES ('FACEBOOK',1), ('ZALO',2), ('TIKTOK',3), ('INSTAGRAM',4)
            ), booking_counts AS (
              SELECT b.channel, count(*) AS count
              FROM gcmb.booking b
              JOIN gcmb.branch br ON br.id=b.branch_id
              WHERE b.channel IN (SELECT channel FROM channels) AND b.status<>'CANCELLED'
                AND b.planned_start >= (CAST(? AS timestamp) AT TIME ZONE br.timezone)
                AND b.planned_start < (CAST(? AS timestamp) AT TIME ZONE br.timezone)
                AND (CAST(? AS uuid) IS NULL OR b.branch_id=?)
              GROUP BY b.channel
            )
            SELECT channels.channel, COALESCE(booking_counts.count,0) AS count
            FROM channels LEFT JOIN booking_counts USING (channel)
            ORDER BY channels.sort_order
            """, from, toExclusive, branchId, branchId);
    }

    public List<Map<String,Object>> operationalWarnings(UUID branchId) {
        return jdbcTemplate.queryForList("""
            SELECT id,branch_id AS "branchId",branch,kind,detail,level FROM (
              SELECT 'branch-'||b.id::text AS id,b.id AS branch_id,b.name AS branch,'Cơ sở tạm ngừng' AS kind,
                COALESCE(NULLIF(b.status_reason,''),'Cơ sở đang ở trạng thái '||b.status||'.') AS detail,
                'critical' AS level,1 AS priority,b.updated_at AS created_at
              FROM gcmb.branch b WHERE b.status<>'ACTIVE' AND (CAST(? AS uuid) IS NULL OR b.id=?)
              UNION ALL
              SELECT i.id::text,i.branch_id,b.name,'Sự cố thiết bị',a.name||' · '||i.description,
                CASE WHEN i.severity IN ('HIGH','CRITICAL') OR i.priority IN ('HIGH','URGENT') THEN 'critical' ELSE 'warning' END,
                CASE WHEN i.severity IN ('HIGH','CRITICAL') OR i.priority IN ('HIGH','URGENT') THEN 2 ELSE 3 END,
                i.created_at
              FROM gcmb.asset_incident i JOIN gcmb.branch b ON b.id=i.branch_id
              JOIN gcmb.asset a ON a.id=i.asset_id
              WHERE i.status IN ('REPORTED','WAITING_APPROVAL','ASSESSED','IN_PROGRESS')
                AND (CAST(? AS uuid) IS NULL OR i.branch_id=?)
              UNION ALL
              SELECT 'stock-'||i.id::text,i.branch_id,b.name,'Tồn kho thấp',
                i.name||' còn '||s.quantity||' '||u.name||' (định mức '||s.minimum_quantity||' '||u.name||').',
                'warning',4,s.created_at
              FROM gcmb.inventory_item i JOIN gcmb.stock_balance s ON s.inventory_item_id=i.id
              JOIN gcmb.branch b ON b.id=i.branch_id JOIN gcmb.unit u ON u.id=i.unit_id
              WHERE i.active AND s.minimum_quantity>0 AND s.quantity<s.minimum_quantity
                AND (CAST(? AS uuid) IS NULL OR i.branch_id=?)
              UNION ALL
              SELECT 'room-'||r.id::text,r.branch_id,b.name,'Phòng đang bảo trì',
                COALESCE(NULLIF(r.code,''),r.name)||' đang tạm ngưng phục vụ.',
                'warning',5,r.created_at
              FROM gcmb.room r JOIN gcmb.branch b ON b.id=r.branch_id
              WHERE r.operating_status='UNDER_MAINTENANCE' AND (CAST(? AS uuid) IS NULL OR r.branch_id=?)
            ) alerts ORDER BY priority,created_at DESC LIMIT 24
            """,branchId,branchId,branchId,branchId,branchId,branchId,branchId,branchId);
    }

    public List<Map<String, Object>> revenueMix(LocalDate start, LocalDate endExclusive, UUID branchId) {
        return jdbcTemplate.queryForList("""
            WITH revenue_mix AS (
              SELECT CASE WHEN p.method='BANK_TRANSFER' THEN 'Chuyển khoản'
                          WHEN p.method='CASH' THEN 'Tiền mặt' ELSE p.method END AS label,
                SUM(p.amount) AS value
              FROM gcmb.bill b JOIN gcmb.branch br ON br.id=b.branch_id
              JOIN gcmb.payment p ON p.bill_id=b.id AND p.purpose='BILL'
              WHERE b.status='SETTLED' AND (b.completed_at AT TIME ZONE br.timezone)::date >= ?
                AND (b.completed_at AT TIME ZONE br.timezone)::date < ?
                AND (CAST(? AS uuid) IS NULL OR b.branch_id=?)
              GROUP BY label
              UNION ALL
              SELECT 'Cọc tịch thu', COALESCE(SUM(de.amount),0)
              FROM gcmb.deposit_entry de JOIN gcmb.deposit d ON d.id=de.deposit_id
              JOIN gcmb.booking bk ON bk.id=d.booking_id JOIN gcmb.branch br ON br.id=bk.branch_id
              WHERE de.kind='FORFEIT' AND (de.created_at AT TIME ZONE br.timezone)::date>=?
                AND (de.created_at AT TIME ZONE br.timezone)::date<? AND (CAST(? AS uuid) IS NULL OR bk.branch_id=?)
            ) SELECT label,value FROM revenue_mix ORDER BY label
            """, start, endExclusive, branchId, branchId,
                start, endExclusive, branchId, branchId);
    }

    public List<Map<String, Object>> occupancy(UUID branchId) {
        return jdbcTemplate.queryForList("""
            SELECT b.id,b.name,COUNT(r.id) FILTER (WHERE r.operating_status NOT IN ('INACTIVE','UNDER_MAINTENANCE')) AS total,
              COUNT(r.id) FILTER (WHERE r.operating_status NOT IN ('INACTIVE','UNDER_MAINTENANCE') AND EXISTS (
                SELECT 1 FROM gcmb.room_allocation a WHERE a.room_id=r.id AND a.state='OCCUPIED'
              )) AS occupied
            FROM gcmb.branch b LEFT JOIN gcmb.room r ON r.branch_id=b.id
            WHERE b.status='ACTIVE' AND (CAST(? AS uuid) IS NULL OR b.id=?)
            GROUP BY b.id,b.name ORDER BY b.name
            """, branchId, branchId);
    }

    public List<Map<String, Object>> roomTypePerformance(LocalDate start, LocalDate endExclusive, UUID branchId) {
        return jdbcTemplate.queryForList("""
            SELECT rt.name,COUNT(DISTINCT a.id) AS count,COALESCE(SUM(bl.amount),0) AS revenue,
              COALESCE(AVG(EXTRACT(EPOCH FROM (COALESCE(a.actual_end,a.ends_at)-COALESCE(a.actual_start,a.starts_at)))/60),0) AS minutes
            FROM gcmb.room_type rt JOIN gcmb.room r ON r.room_type_id=rt.id
            JOIN gcmb.room_allocation a ON a.room_id=r.id
            JOIN gcmb.branch br ON br.id=r.branch_id
            LEFT JOIN gcmb.bill b ON b.booking_id=a.booking_id AND b.status='SETTLED'
              AND (b.completed_at AT TIME ZONE br.timezone)::date >= ?
              AND (b.completed_at AT TIME ZONE br.timezone)::date < ?
            LEFT JOIN gcmb.bill_line bl ON bl.bill_id=b.id AND bl.order_line_id IS NULL
              AND EXISTS (SELECT 1 FROM gcmb.booking_charge bc WHERE bc.id=bl.booking_charge_id AND bc.allocation_id=a.id)
            WHERE r.branch_id=? AND a.state='FINISHED'
              AND (COALESCE(a.actual_end,a.ends_at) AT TIME ZONE br.timezone)::date >= ?
              AND (COALESCE(a.actual_end,a.ends_at) AT TIME ZONE br.timezone)::date < ?
            GROUP BY rt.id,rt.name ORDER BY revenue DESC,rt.name
            """, start, endExclusive, branchId, start, endExclusive);
    }

    public List<Map<String, Object>> hourlyOccupancy(LocalDate start, LocalDate endExclusive, UUID branchId) {
        return jdbcTemplate.queryForList("""
            SELECT EXTRACT(ISODOW FROM local_day)::int AS weekday, hour_slot AS hour,
              COUNT(*) AS occupied_room_hours
            FROM (
              SELECT d.local_day, h.hour_slot
              FROM generate_series(?::date, (?::date - 1), interval '1 day') d(local_day)
              CROSS JOIN generate_series(8,19) h(hour_slot)
            ) slots
            JOIN gcmb.branch br ON br.id=?
            JOIN gcmb.room_allocation a ON a.branch_id=br.id
              AND a.state IN ('OCCUPIED','FINISHED')
              AND a.starts_at < ((slots.local_day + slots.hour_slot * interval '1 hour' + interval '1 hour') AT TIME ZONE br.timezone)
              AND COALESCE(a.actual_end,a.ends_at) > ((slots.local_day + slots.hour_slot * interval '1 hour') AT TIME ZONE br.timezone)
            GROUP BY weekday,hour_slot ORDER BY weekday,hour_slot
            """, start, endExclusive, branchId);
    }

    public List<Map<String, Object>> openIncidents(UUID branchId) {
        return jdbcTemplate.queryForList("""
            SELECT i.id,i.asset_id AS "assetId",a.code AS "assetCode",a.name AS "assetName",
              COALESCE(r.code,r.name,a.location_note,'—') AS location,i.status,i.created_at AS "createdAt"
            FROM gcmb.asset_incident i JOIN gcmb.asset a ON a.id=i.asset_id
            LEFT JOIN gcmb.room r ON r.id=a.room_id
            WHERE i.branch_id=? AND i.status NOT IN ('VERIFIED','CLOSED')
            ORDER BY i.created_at DESC LIMIT 30
            """, branchId);
    }

    public List<Map<String, Object>> todayShifts(UUID branchId, LocalDate today) {
        return jdbcTemplate.queryForList("""
            SELECT s.work_date AS date,st.code AS shift,e.full_name AS employee,
              CASE WHEN att.id IS NULL THEN 'CHƯA CHẤM CÔNG'
                   WHEN att.status='ABSENT' THEN 'VẮNG'
                   WHEN att.actual_in IS NULL THEN 'CHƯA VÀO CA'
                   WHEN att.actual_out IS NULL THEN 'ĐANG LÀM'
                   ELSE 'ĐÃ CHẤM CÔNG' END AS attendance,
              s.starts_at AS "startsAt",s.ends_at AS "endsAt"
            FROM gcmb.scheduled_shift s JOIN gcmb.shift_template st ON st.id=s.template_id
            JOIN gcmb.shift_assignment sa ON sa.shift_id=s.id AND sa.status='CONFIRMED'
            JOIN gcmb.employee e ON e.id=sa.employee_id
            LEFT JOIN gcmb.attendance att ON att.assignment_id=sa.id
            WHERE s.branch_id=? AND s.work_date>=? AND s.work_date<? AND s.status<>'CANCELLED'
            ORDER BY s.work_date,s.starts_at,e.full_name LIMIT 100
            """, branchId, today, today.plusDays(2));
    }

    public List<Map<String, Object>> topItems(LocalDate start, LocalDate endExclusive, UUID branchId) {
        return jdbcTemplate.queryForList("""
            SELECT ol.name_snapshot AS name,COALESCE(SUM(ol.quantity),0) AS quantity,
              COALESCE(SUM(bl.amount),0) AS revenue
            FROM gcmb.bill b JOIN gcmb.branch br ON br.id=b.branch_id
            JOIN gcmb.bill_line bl ON bl.bill_id=b.id
            JOIN gcmb.order_line ol ON ol.id=bl.order_line_id
            WHERE b.status='SETTLED' AND (b.completed_at AT TIME ZONE br.timezone)::date >= ?
              AND (b.completed_at AT TIME ZONE br.timezone)::date < ? AND b.branch_id=?
            GROUP BY ol.name_snapshot ORDER BY quantity DESC,revenue DESC LIMIT 10
            """, start, endExclusive, branchId);
    }

    public List<Map<String, Object>> lowStock(UUID branchId) {
        return jdbcTemplate.queryForList("""
            SELECT i.name,b.quantity,b.minimum_quantity AS minimum,u.name AS unit
            FROM gcmb.inventory_item i JOIN gcmb.stock_balance b ON b.inventory_item_id=i.id
            JOIN gcmb.unit u ON u.id=i.unit_id
            WHERE i.branch_id=? AND i.active AND b.minimum_quantity>0 AND b.quantity<b.minimum_quantity
            ORDER BY b.quantity/b.minimum_quantity,i.name LIMIT 20
            """, branchId);
    }
}
