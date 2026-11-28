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

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AttendanceRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> period(UUID branch, LocalDate month, boolean lock) {
        var rows = jdbcTemplate.queryForList(
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
        jdbcTemplate.update(
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
        return rows(branch, from, until, null);
    }

    public List<Map<String, Object>> workforceActivity(UUID branch, LocalDate from, LocalDate until) {
        return jdbcTemplate.queryForList("""
            WITH shift_records AS (
              SELECT s.branch_id, s.id, s.capacity, s.status <> 'CANCELLED' scheduled,
                s.ends_at <= CURRENT_TIMESTAMP finished,
                sa.status = 'CONFIRMED' confirmed, sa.employee_id, s.work_date,
                a.id attendance_id, a.worked_minutes,
                CASE WHEN a.status='WORKED' THEN a.actual_out>a.actual_in
                    AND a.break_minutes=0 AND a.actual_out-a.actual_in=a.worked_minutes*interval '1 minute'
                    AND (a.actual_out<=CURRENT_TIMESTAMP OR (s.starts_at<=CURRENT_TIMESTAMP
                        AND a.actual_in=s.starts_at AND a.actual_out=s.ends_at))
                  ELSE a.status='ABSENT' AND a.actual_in IS NULL AND a.actual_out IS NULL AND a.worked_minutes=0 END valid,
                a.status='ABSENT' absent
              FROM gcmb.scheduled_shift s
              LEFT JOIN gcmb.shift_assignment sa ON sa.shift_id=s.id
              LEFT JOIN gcmb.attendance a ON a.assignment_id=sa.id
              WHERE (CAST(? AS uuid) IS NULL OR s.branch_id=?) AND s.work_date>=? AND s.work_date<?
                AND (s.status<>'CANCELLED' OR a.id IS NOT NULL)
            ), per_shift AS (
              SELECT branch_id, id, capacity, scheduled,
                count(*) FILTER (WHERE scheduled AND confirmed) assigned,
                count(*) FILTER (WHERE scheduled AND confirmed AND finished AND attendance_id IS NULL) missing,
                count(attendance_id) recorded,
                count(*) FILTER (WHERE attendance_id IS NOT NULL AND valid IS NOT TRUE) invalid,
                count(*) FILTER (WHERE NOT absent AND valid) worked,
                count(*) FILTER (WHERE absent AND valid) absent,
                sum(worked_minutes) FILTER (WHERE valid) minutes,
                count(*) FILTER (WHERE attendance_id IS NOT NULL AND gcmb.employee_month_closed(branch_id,employee_id,work_date)) closed,
                sum((SELECT count(*) FROM gcmb.attendance_issue i WHERE i.attendance_id=r.attendance_id AND i.status='OPEN')) issues
              FROM shift_records r GROUP BY branch_id, id, capacity, scheduled
            )
            SELECT branch_id AS "branchId", count(*) FILTER (WHERE scheduled) AS "scheduledShifts",
              count(*) FILTER (WHERE scheduled AND assigned<capacity) AS "shiftsBelowCapacity",
              COALESCE(sum(greatest(capacity-assigned,0)) FILTER (WHERE scheduled),0) AS "vacantShiftSlots",
              COALESCE(sum(assigned),0) AS "confirmedAssignments", COALESCE(sum(recorded),0) AS "attendanceRecords",
              COALESCE(sum(missing),0) AS "missingAttendance", COALESCE(sum(invalid),0) AS "invalidAttendance",
              COALESCE(sum(worked),0) AS "workedAttendances", COALESCE(sum(absent),0) AS "absences",
              COALESCE(sum(minutes),0) AS "workedMinutes",
              COALESCE(sum(closed),0) AS "closedAttendance", COALESCE(sum(issues),0) AS "openAttendanceIssues"
            FROM per_shift GROUP BY GROUPING SETS ((branch_id), ())
            ORDER BY branch_id NULLS FIRST
            """, branch, branch, from, until);
    }

    public List<Map<String, Object>> personalRows(UUID employee, LocalDate from, LocalDate until) {
        // BR-60: UUID này do service lấy từ phiên, không nhận mã nhân viên từ request.
        java.util.Objects.requireNonNull(employee);
        return rows(null, from, until, employee);
    }

    private List<Map<String, Object>> rows(UUID branch, LocalDate from, LocalDate until, UUID employee) {
        // UC-88: giữ cả công cũ của người đã điều chuyển/ngừng; không lọc theo hồ sơ hiện tại.
        return jdbcTemplate.queryForList(
            """
            SELECT sa.id AS "assignmentId",sa.employee_id AS "employeeId",e.code,
              e.full_name AS "fullName",s.id AS "shiftId",s.work_date::text AS "workDate",
              s.template_id AS "templateId",s.starts_at AS "startsAt",s.ends_at AS "endsAt",
              br.name AS "branchName",CASE WHEN gcmb.employee_month_closed(s.branch_id,e.id,s.work_date)
                THEN 'CLOSED' ELSE p.status END AS "periodStatus",
              COALESCE((SELECT r.hourly_rate FROM gcmb.branch_employee_pay_rate r
                WHERE r.employee_id=e.id AND r.branch_id=s.branch_id AND r.effective_from<=s.work_date
                ORDER BY r.effective_from DESC LIMIT 1),
              (SELECT r.hourly_rate FROM gcmb.employee_pay_rate r
                WHERE r.employee_id=e.id AND r.valid_from<=s.work_date
                  AND (r.valid_to IS NULL OR r.valid_to>s.work_date)
                ORDER BY r.valid_from DESC LIMIT 1)) AS "hourlyRate",
              CASE WHEN st.start_minute=0 AND st.end_minute=420 THEN
                (SELECT policy.amount FROM gcmb.operating_policy policy
                 WHERE policy.policy_key='NIGHT_SHIFT_PREMIUM' AND policy.effective_from<=s.work_date
                 ORDER BY policy.effective_from DESC,policy.changed_at DESC,policy.id DESC LIMIT 1)
                ELSE 0 END AS "nightPremium",
              a.id AS "attendanceId",a.status,COALESCE(a.version,0) AS version,
              a.actual_in AS "actualIn",a.actual_out AS "actualOut",
              COALESCE(a.worked_minutes,0) AS "workedMinutes",COALESCE(a.break_minutes,0) AS "breakMinutes",
              COALESCE(a.exception_note,'') AS note,
              (SELECT count(*) FROM gcmb.attendance_issue i WHERE i.attendance_id=a.id AND i.status='OPEN') AS "openIssues"
            FROM gcmb.shift_assignment sa JOIN gcmb.scheduled_shift s ON s.id=sa.shift_id
            JOIN gcmb.employee e ON e.id=sa.employee_id
            JOIN gcmb.shift_template st ON st.id=s.template_id
            LEFT JOIN gcmb.attendance a ON a.assignment_id=sa.id
            JOIN gcmb.branch br ON br.id=s.branch_id
            LEFT JOIN gcmb.attendance_period p ON p.id=a.period_id
            WHERE (CAST(? AS uuid) IS NULL OR s.branch_id=?) AND s.work_date>=? AND s.work_date<?
              AND (CAST(? AS uuid) IS NULL OR sa.employee_id=?)
              AND ((sa.status='CONFIRMED' AND s.status<>'CANCELLED') OR a.id IS NOT NULL)
            ORDER BY s.work_date,s.starts_at,e.full_name,sa.id
            """,
            branch,
            branch,
            from,
            until,
            employee,
            employee
        );
    }

    public Map<String, Object> row(UUID branch, UUID assignment) {
        var keys = jdbcTemplate.queryForList(
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
        return jdbcTemplate.queryForList(
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
        return jdbcTemplate.queryForObject(
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
            jdbcTemplate.update(
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
        jdbcTemplate.update("UPDATE gcmb.attendance_period SET version=version+1 WHERE id=?", period);
    }

    public List<Map<String,Object>> employeeClosures(UUID branch,LocalDate month) {
        return jdbcTemplate.queryForList("""
            SELECT c.employee_id AS "employeeId",c.closed_at AS "closedAt",u.username AS "closedBy"
            FROM gcmb.employee_attendance_closing c JOIN gcmb.attendance_period p ON p.id=c.period_id
            JOIN gcmb.user_account u ON u.id=c.closed_by WHERE p.branch_id=? AND p.month=?
            """,branch,month);
    }

    public void closeEmployee(UUID period,UUID employee,UUID actor,Object snapshot) {
        jdbcTemplate.update("INSERT INTO gcmb.employee_attendance_closing(period_id,employee_id,closed_by,source_snapshot) VALUES (?,?,?,?::jsonb)",
            period,employee,actor,objectMapper.writeValueAsString(snapshot));
    }

    public void close(UUID period, UUID actor) {
        jdbcTemplate.update(
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
        jdbcTemplate.update(
            """
            INSERT INTO gcmb.audit_event(actor_id,branch_id,action,entity_type,entity_id,before_data,after_data)
            VALUES (?,?,?,'ATTENDANCE',?,CAST(? AS jsonb),CAST(? AS jsonb))
            """,
            actor,
            branch,
            action,
            entity,
            objectMapper.writeValueAsString(before),
            objectMapper.writeValueAsString(after)
        );
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
