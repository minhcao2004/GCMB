/**
 * Author: HuyDV
 * Created: 27/10/2026
 * Description: UC-87..89, đọc công theo phân ca và khóa bảng công trong giao dịch.
 */
package com.gcmb.backend.workforce.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class AttendanceRepository {

    private final JdbcTemplate db;
    private final ObjectMapper json;

    public AttendanceRepository(JdbcTemplate db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    public Map<String, Object> period(UUID branch, LocalDate month, boolean lock) {
        var rows = db.queryForList(
            """
            SELECT p.id,p.status,p.version,p.closed_at AS "closedAt",u.username AS "closedBy"
            FROM gcmb.attendance_period p LEFT JOIN gcmb.user_account u ON u.id=p.closed_by
            WHERE p.branch_id=? AND p.month=?
            """ + (lock ? " FOR UPDATE OF p" : ""),
            branch,
            month
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, Object> ensurePeriod(UUID branch, LocalDate month) {
        db.update(
            """
            INSERT INTO gcmb.attendance_period(branch_id,month,status) VALUES (?,?,'OPEN')
            ON CONFLICT(branch_id,month) DO NOTHING
            """,
            branch,
            month
        );
        return period(branch, month, true);
    }

    public List<Map<String, Object>> rows(UUID branch, LocalDate from, LocalDate until) {
        // UC-88: giữ cả công cũ của người đã điều chuyển/ngừng; không lọc theo hồ sơ hiện tại.
        return db.queryForList(
            """
            SELECT sa.id AS "assignmentId",sa.employee_id AS "employeeId",e.code,
              e.full_name AS "fullName",s.id AS "shiftId",s.work_date::text AS "workDate",
              s.template_id AS "templateId",s.starts_at AS "startsAt",s.ends_at AS "endsAt",
              a.id AS "attendanceId",a.status,COALESCE(a.version,0) AS version,
              a.actual_in AS "actualIn",a.actual_out AS "actualOut",
              COALESCE(a.worked_minutes,0) AS "workedMinutes",COALESCE(a.break_minutes,0) AS "breakMinutes",
              COALESCE(a.exception_note,'') AS note,
              (SELECT count(*) FROM gcmb.attendance_issue i WHERE i.attendance_id=a.id AND i.status='OPEN') AS "openIssues"
            FROM gcmb.shift_assignment sa JOIN gcmb.scheduled_shift s ON s.id=sa.shift_id
            JOIN gcmb.employee e ON e.id=sa.employee_id
            LEFT JOIN gcmb.attendance a ON a.assignment_id=sa.id
            WHERE s.branch_id=? AND s.work_date>=? AND s.work_date<?
              AND ((sa.status='CONFIRMED' AND s.status<>'CANCELLED') OR a.id IS NOT NULL)
            ORDER BY s.work_date,s.starts_at,e.full_name,sa.id
            """,
            branch,
            from,
            until
        );
    }

    public Map<String, Object> row(UUID branch, UUID assignment) {
        var keys = db.queryForList(
            """
            SELECT s.work_date FROM gcmb.shift_assignment sa
            JOIN gcmb.scheduled_shift s ON s.id=sa.shift_id
            WHERE sa.id=? AND s.branch_id=? AND sa.status='CONFIRMED' AND s.status<>'CANCELLED'
            FOR UPDATE OF sa
            """,
            assignment,
            branch
        );
        if (keys.isEmpty()) return null;
        LocalDate date = ((java.sql.Date) keys.get(0).get("work_date")).toLocalDate();
        return rows(branch, date, date.plusDays(1))
            .stream()
            .filter(r -> assignment.equals(r.get("assignmentId")))
            .findFirst()
            .orElse(null);
    }

    public List<Map<String, Object>> issues(UUID branch, LocalDate from, LocalDate until) {
        return db.queryForList(
            """
            SELECT i.id,a.assignment_id AS "assignmentId",i.description,i.status,i.resolution
            FROM gcmb.attendance_issue i JOIN gcmb.attendance a ON a.id=i.attendance_id
            JOIN gcmb.shift_assignment sa ON sa.id=a.assignment_id
            JOIN gcmb.scheduled_shift s ON s.id=sa.shift_id
            WHERE s.branch_id=? AND s.work_date>=? AND s.work_date<?
            ORDER BY i.created_at,i.id
            """,
            branch,
            from,
            until
        );
    }

    public UUID save(
        UUID assignment,
        UUID period,
        String status,
        Instant in,
        Instant out,
        int minutes,
        String note,
        UUID actor
    ) {
        return db.queryForObject(
            """
            INSERT INTO gcmb.attendance(assignment_id,period_id,status,actual_in,actual_out,
              break_minutes,worked_minutes,exception_note,recorded_by)
            VALUES (?,?,?,?,?,0,?,?,?)
            ON CONFLICT(assignment_id) DO UPDATE SET status=EXCLUDED.status,
              actual_in=EXCLUDED.actual_in,actual_out=EXCLUDED.actual_out,break_minutes=0,
              worked_minutes=EXCLUDED.worked_minutes,exception_note=EXCLUDED.exception_note,
              recorded_by=EXCLUDED.recorded_by,version=gcmb.attendance.version+1
            RETURNING id
            """,
            UUID.class,
            assignment,
            period,
            status,
            timestamp(in),
            timestamp(out),
            minutes,
            note,
            actor
        );
    }

    public boolean resolve(UUID issue, UUID attendance, UUID actor, String resolution) {
        return (
            db.update(
                """
                UPDATE gcmb.attendance_issue SET status='RESOLVED',resolved_by=?,resolved_at=CURRENT_TIMESTAMP,resolution=?
                WHERE id=? AND attendance_id=? AND status='OPEN'
                """,
                actor,
                resolution,
                issue,
                attendance
            ) == 1
        );
    }

    public void changed(UUID period) {
        db.update("UPDATE gcmb.attendance_period SET version=version+1 WHERE id=?", period);
    }

    public void close(UUID period, UUID actor) {
        db.update(
            """
            UPDATE gcmb.attendance_period SET status='CLOSED',closed_by=?,closed_at=CURRENT_TIMESTAMP,version=version+1
            WHERE id=? AND status='OPEN'
            """,
            actor,
            period
        );
    }

    public void audit(
        UUID actor,
        UUID branch,
        UUID entity,
        String action,
        Object before,
        Object after
    ) {
        db.update(
            """
            INSERT INTO gcmb.audit_event(actor_id,branch_id,action,entity_type,entity_id,before_data,after_data)
            VALUES (?,?,?,'ATTENDANCE',?,CAST(? AS jsonb),CAST(? AS jsonb))
            """,
            actor,
            branch,
            action,
            entity,
            json.writeValueAsString(before),
            json.writeValueAsString(after)
        );
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
