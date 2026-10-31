/**
 * Author: HuyDV
 * Created: 27/10/2026
 * Description: Lưu ca tuần, đăng ký, lịch sử thay người và kiểm tra phạm vi nhân viên.
 */
package com.gcmb.backend.workforce.repository;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class ShiftRepository {

    private final JdbcTemplate db;
    private final ObjectMapper json;

    public ShiftRepository(JdbcTemplate db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    public void serialize() {
        db.execute("SELECT pg_advisory_xact_lock(1300261013)");
    }

    public boolean actor(UUID user, String role, UUID branch) {
        return Boolean.TRUE.equals(
            db.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM gcmb.user_account u JOIN gcmb.role r ON r.id=u.role_id WHERE u.id=? AND u.status='ACTIVE' AND r.code=? AND (r.code='BRANCH_MANAGER' OR u.branch_id=?))",
                Boolean.class,
                user,
                role,
                branch
            )
        );
    }

    public Map<String, Object> branch(UUID id, boolean lock) {
        var rows = db.queryForList(
            "SELECT id,name,status,timezone FROM gcmb.branch WHERE id=?" +
                (lock ? " FOR SHARE" : ""),
            id
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> templates() {
        return db.queryForList(
            "SELECT id,code,start_minute AS \"startMinute\",end_minute AS \"endMinute\" FROM gcmb.shift_template ORDER BY start_minute"
        );
    }

    public List<Map<String, Object>> shifts(UUID branch, LocalDate week) {
        return db.queryForList(
            """
            SELECT s.id,s.template_id AS "templateId",s.work_date::text AS date,s.capacity,s.status,s.version,
              s.starts_at AS "startsAt",s.ends_at AS "endsAt",
              (SELECT count(*) FROM gcmb.shift_assignment a WHERE a.shift_id=s.id AND a.status='CONFIRMED') AS registered,
              EXISTS(SELECT 1 FROM gcmb.shift_assignment a JOIN gcmb.attendance t ON t.assignment_id=a.id WHERE a.shift_id=s.id) AS "hasAttendance"
            FROM gcmb.scheduled_shift s WHERE s.branch_id=? AND s.work_date>=? AND s.work_date<? ORDER BY s.work_date,s.starts_at
            """,
            branch,
            week,
            week.plusDays(7)
        );
    }

    public Set<LocalDate> closedMonths(UUID branch, LocalDate week) {
        return new HashSet<>(
            db.queryForList(
                "SELECT month FROM gcmb.attendance_period WHERE branch_id=? AND status='CLOSED' AND month>=? AND month<=?",
                LocalDate.class,
                branch,
                week.withDayOfMonth(1),
                week.plusDays(6).withDayOfMonth(1)
            )
        );
    }

    public boolean hasAttendance(UUID shift) {
        return Boolean.TRUE.equals(
            db.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM gcmb.shift_assignment a JOIN gcmb.attendance t ON t.assignment_id=a.id WHERE a.shift_id=?)",
                Boolean.class,
                shift
            )
        );
    }

    public List<Map<String, Object>> assignments(UUID branch, LocalDate week, UUID employee) {
        return db.queryForList(
            """
            SELECT a.id,a.shift_id AS "shiftId",e.id AS "employeeId",e.code,e.full_name AS "fullName",e.status AS "employeeStatus",a.source
            FROM gcmb.shift_assignment a JOIN gcmb.employee e ON e.id=a.employee_id JOIN gcmb.scheduled_shift s ON s.id=a.shift_id
            WHERE s.branch_id=? AND s.work_date>=? AND s.work_date<? AND a.status='CONFIRMED'
              AND (CAST(? AS uuid) IS NULL OR e.id=?) ORDER BY s.work_date,s.starts_at,e.full_name,e.id
            """,
            branch,
            week,
            week.plusDays(7),
            employee,
            employee
        );
    }

    public List<Map<String, Object>> history(UUID branch, LocalDate week) {
        return db.queryForList(
            """
            SELECT a.id,a.shift_id AS "shiftId",e.full_name AS "fullName",e.code,a.reason,a.cancelled_at AS "cancelledAt"
            FROM gcmb.shift_assignment a JOIN gcmb.employee e ON e.id=a.employee_id JOIN gcmb.scheduled_shift s ON s.id=a.shift_id
            WHERE s.branch_id=? AND s.work_date>=? AND s.work_date<? AND a.status='CANCELLED'
            ORDER BY a.cancelled_at DESC,a.id
            """,
            branch,
            week,
            week.plusDays(7)
        );
    }

    public Map<String, Object> shift(UUID id, UUID branch) {
        var rows = db.queryForList(
            "SELECT * FROM gcmb.scheduled_shift WHERE id=? AND branch_id=? FOR UPDATE",
            id,
            branch
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, Object> slot(UUID branch, UUID template, LocalDate date) {
        var rows = db.queryForList(
            "SELECT * FROM gcmb.scheduled_shift WHERE branch_id=? AND template_id=? AND work_date=? FOR UPDATE",
            branch,
            template,
            date
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    public long registered(UUID shift) {
        return db.queryForObject(
            "SELECT count(*) FROM gcmb.shift_assignment WHERE shift_id=? AND status='CONFIRMED'",
            Long.class,
            shift
        );
    }

    public UUID linkedEmployee(UUID user) {
        var ids = db.queryForList(
            "SELECT id FROM gcmb.employee WHERE user_id=? AND status='ACTIVE'",
            UUID.class,
            user
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    public boolean belongs(UUID employee, UUID branch, Instant at, Instant end) {
        // UC-83/86: ca lễ tân chỉ nhận hồ sơ có vị trí RECEPTIONIST, kể cả khi gửi trực tiếp API.
        return Boolean.TRUE.equals(
            db.queryForObject(
                """
                SELECT EXISTS(SELECT 1 FROM gcmb.employee e WHERE e.id=? AND e.status='ACTIVE' AND e.position='RECEPTIONIST'
                  AND EXISTS(SELECT 1 FROM gcmb.employee_assignment a WHERE a.employee_id=e.id AND a.branch_id=?
                    AND a.valid_from<=clock_timestamp() AND (a.valid_to IS NULL OR a.valid_to>clock_timestamp()))
                  AND EXISTS(SELECT 1 FROM gcmb.employee_assignment a WHERE a.employee_id=e.id AND a.branch_id=?
                    AND a.valid_from<=? AND (a.valid_to IS NULL OR a.valid_to>=?)))
                """,
                Boolean.class,
                employee,
                branch,
                branch,
                Timestamp.from(at),
                Timestamp.from(end)
            )
        );
    }

    public List<Map<String, Object>> employees(UUID branch) {
        // UC-86 / BR-58: nguồn người làm thay là hồ sơ lễ tân đang hoạt động tại cơ sở.
        return db.queryForList(
            """
            SELECT e.id,e.code,e.full_name AS "fullName" FROM gcmb.employee e WHERE e.status='ACTIVE' AND e.position='RECEPTIONIST'
              AND EXISTS(SELECT 1 FROM gcmb.employee_assignment a WHERE a.employee_id=e.id AND a.branch_id=?
                AND a.valid_from<=clock_timestamp() AND (a.valid_to IS NULL OR a.valid_to>clock_timestamp())) ORDER BY e.full_name,e.id
            """,
            branch
        );
    }

    public boolean overlap(UUID employee, UUID shift, Instant start, Instant end) {
        return Boolean.TRUE.equals(
            db.queryForObject(
                """
                SELECT EXISTS(SELECT 1 FROM gcmb.shift_assignment a JOIN gcmb.scheduled_shift s ON s.id=a.shift_id
                  WHERE a.employee_id=? AND a.status='CONFIRMED' AND (s.id=? OR (s.starts_at<? AND s.ends_at>?)))
                """,
                Boolean.class,
                employee,
                shift,
                Timestamp.from(end),
                Timestamp.from(start)
            )
        );
    }

    public boolean already(UUID employee, UUID shift) {
        return Boolean.TRUE.equals(
            db.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM gcmb.shift_assignment WHERE employee_id=? AND shift_id=? AND status='CONFIRMED')",
                Boolean.class,
                employee,
                shift
            )
        );
    }

    public boolean closedPeriod(UUID branch, LocalDate date) {
        return !db
            .queryForList(
                "SELECT id FROM gcmb.attendance_period WHERE branch_id=? AND month=? AND status='CLOSED' FOR SHARE",
                UUID.class,
                branch,
                date.withDayOfMonth(1)
            )
            .isEmpty();
    }

    public Map<String, Object> assignment(UUID id, UUID branch) {
        var rows = db.queryForList(
            "SELECT a.* FROM gcmb.shift_assignment a JOIN gcmb.scheduled_shift s ON s.id=a.shift_id WHERE a.id=? AND s.branch_id=? FOR UPDATE OF a",
            id,
            branch
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    public boolean attended(UUID assignment) {
        return Boolean.TRUE.equals(
            db.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM gcmb.attendance WHERE assignment_id=?)",
                Boolean.class,
                assignment
            )
        );
    }

    public UUID publish(
        UUID branch,
        UUID template,
        LocalDate date,
        Instant start,
        Instant end,
        int capacity,
        String status,
        UUID actor,
        Map<String, Object> old
    ) {
        if (old == null) {
            return db.queryForObject(
                "INSERT INTO gcmb.scheduled_shift(branch_id,template_id,work_date,starts_at,ends_at,capacity,status,created_by) VALUES (?,?,?,?,?,?,?,?) RETURNING id",
                UUID.class,
                branch,
                template,
                date,
                Timestamp.from(start),
                Timestamp.from(end),
                capacity,
                status,
                actor
            );
        }
        UUID id = (UUID) old.get("id");
        db.update(
            "UPDATE gcmb.scheduled_shift SET capacity=?,status=?,version=version+1 WHERE id=?",
            capacity,
            status,
            id
        );
        return id;
    }

    public UUID assign(UUID shift, UUID employee, UUID actor, String source, String reason) {
        UUID id = db.queryForObject(
            "INSERT INTO gcmb.shift_assignment(shift_id,employee_id,assigned_by,source,status,reason) VALUES (?,?,?,?,'CONFIRMED',?) RETURNING id",
            UUID.class,
            shift,
            employee,
            actor,
            source,
            reason
        );
        db.update("UPDATE gcmb.scheduled_shift SET version=version+1 WHERE id=?", shift);
        return id;
    }

    public void cancel(UUID assignment, String reason) {
        db.update(
            "UPDATE gcmb.shift_assignment SET status='CANCELLED',cancelled_at=CURRENT_TIMESTAMP,reason=? WHERE id=?",
            reason,
            assignment
        );
    }

    public void audit(
        UUID actor,
        UUID branch,
        UUID shift,
        String action,
        Map<String, Object> data
    ) {
        db.update(
            "INSERT INTO gcmb.audit_event(actor_id,branch_id,action,entity_type,entity_id,after_data) VALUES (?,?,?,'SCHEDULED_SHIFT',?,CAST(? AS jsonb))",
            actor,
            branch,
            action,
            shift,
            json.writeValueAsString(data)
        );
    }
}
