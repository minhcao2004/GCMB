/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 13/11/2026
 * Description: Manual unit test cases cho UC-88 View Attendance Records.
 */
package com.gcmb.backend.manual.shiftmanagement;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gcmb.backend.accounts.repository.AccountRepository;
import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.exception.AuthFailure;
import com.gcmb.backend.manual.shiftmanagement.ShiftManagementManualRunner.ManualCase;
import com.gcmb.backend.manual.shiftmanagement.ShiftManagementManualRunner.Observation;
import com.gcmb.backend.workforce.repository.AttendanceRepository;
import com.gcmb.backend.workforce.repository.ShiftRepository;
import com.gcmb.backend.workforce.service.AttendanceService;
import com.gcmb.backend.workforce.service.ShiftService;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ViewAttendanceRecordsManualCases {
    private static final UUID MANAGER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID BRANCH = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID EMPLOYEE = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private ViewAttendanceRecordsManualCases() {
    }

    static List<ManualCase> cases() {
        return List.of(
            test("SHM-053", "Branch Manager đã đăng nhập; cơ sở ACTIVE; có dữ liệu công trong ngày.",
                "Xem bảng công theo ngày.",
                "Trả đúng ngày, danh sách công, phản ánh, ca mẫu và cho phép chỉnh sửa.",
                ViewAttendanceRecordsManualCases::dailyRecords),
            test("SHM-054", "Tháng có bản ghi WORKED và ABSENT hợp lệ.",
                "Xem bảng tổng hợp công theo tháng.",
                "Tổng hợp đúng số ca, phút công, vắng mặt và dữ liệu theo nhân viên.",
                ViewAttendanceRecordsManualCases::monthlySummary),
            test("SHM-055", "Ngày và tháng được chọn không có phân ca.",
                "Xem bảng công ngày và tháng.",
                "Trả danh sách rỗng, các bộ đếm bằng 0 và không phát sinh lỗi.",
                ViewAttendanceRecordsManualCases::emptyRecords),
            test("SHM-056", "Branch Manager thuộc cơ sở SUSPENDED.",
                "Xem dữ liệu công ngày và tháng.",
                "Vẫn xem được dữ liệu nhưng kết quả ở chế độ chỉ đọc.",
                ViewAttendanceRecordsManualCases::suspendedReadOnly),
            test("SHM-057", "Người dùng không phải Branch Manager hoặc không quản lý cơ sở.",
                "Xem bảng công.",
                "Hiển thị thông báo không có quyền và không phát sinh thao tác ghi.",
                ViewAttendanceRecordsManualCases::authorization)
        );
    }

    private static Observation dailyRecords() {
        Scenario scenario = ready("ACTIVE");
        LocalDate day = LocalDate.now(ZONE).minusDays(1);
        scenario.rows = List.of(worked(day, 300));
        scenario.issues = List.of(new LinkedHashMap<>(Map.of("status", "OPEN")));
        Map<String, Object> result = scenario.service.daily(manager(), day);
        int rows = ((List<?>) result.get("rows")).size();
        int issues = ((List<?>) result.get("issues")).size();
        int templates = ((List<?>) result.get("templates")).size();
        return new Observation("date=" + result.get("date") + ", rows=" + rows + ", issues=" + issues
            + ", templates=" + templates + ", readOnly=" + result.get("readOnly"),
            day.toString().equals(result.get("date")) && rows == 1 && issues == 1 && templates == 1
                && Boolean.FALSE.equals(result.get("readOnly")));
    }

    private static Observation monthlySummary() {
        Scenario scenario = ready("ACTIVE");
        YearMonth month = YearMonth.now(ZONE).minusMonths(1);
        scenario.rows = List.of(worked(month.atDay(5), 300), absent(month.atDay(6)));
        Map<String, Object> result = scenario.service.monthly(manager(), month.toString());
        @SuppressWarnings("unchecked")
        Map<String, Object> employee = (Map<String, Object>) ((java.util.Collection<?>) result.get("employees"))
            .iterator().next();
        boolean passed = ((Number) result.get("assignmentCount")).longValue() == 2
            && ((Number) result.get("workedMinutes")).longValue() == 300
            && ((Number) employee.get("absences")).longValue() == 1
            && ((Number) employee.get("shiftCount")).longValue() == 2;
        return new Observation("assignmentCount=" + result.get("assignmentCount") + ", workedMinutes="
            + result.get("workedMinutes") + ", absences=" + employee.get("absences"), passed);
    }

    private static Observation emptyRecords() {
        Scenario scenario = ready("ACTIVE");
        LocalDate day = LocalDate.now(ZONE).minusDays(1);
        Map<String, Object> daily = scenario.service.daily(manager(), day);
        Map<String, Object> monthly = scenario.service.monthly(manager(), YearMonth.from(day).toString());
        boolean passed = ((List<?>) daily.get("rows")).isEmpty()
            && ((Number) monthly.get("assignmentCount")).longValue() == 0
            && ((Number) monthly.get("workedMinutes")).longValue() == 0;
        return new Observation("dailyRows=" + ((List<?>) daily.get("rows")).size()
            + ", monthlyAssignments=" + monthly.get("assignmentCount")
            + ", workedMinutes=" + monthly.get("workedMinutes"), passed);
    }

    private static Observation suspendedReadOnly() {
        Scenario scenario = ready("SUSPENDED");
        boolean daily = Boolean.TRUE.equals(scenario.service.daily(manager(), LocalDate.now(ZONE)).get("readOnly"));
        boolean monthly = Boolean.TRUE.equals(scenario.service.monthly(manager(), null).get("readOnly"));
        return new Observation("dailyReadOnly=" + daily + ", monthlyReadOnly=" + monthly, daily && monthly);
    }

    private static Observation authorization() {
        Scenario receptionist = ready("ACTIVE");
        String first = message(() -> receptionist.service.daily(identity("RECEPTIONIST"), null));
        Scenario unmanaged = ready("ACTIVE");
        when(unmanaged.accounts.manages(MANAGER, BRANCH, false)).thenReturn(false);
        String second = message(() -> unmanaged.service.monthly(manager(), null));
        boolean noWrites = receptionist.writeCount == 0 && unmanaged.writeCount == 0;
        return new Observation("messages=" + List.of(first, second) + ", writes=0", first.contains("không có quyền")
            && second.contains("không có quyền") && noWrites);
    }

    private static ManualCase test(String id, String precondition, String input, String expected,
                                   ShiftManagementManualRunner.Execution execution) {
        return new ManualCase(id, "UC-88 View Attendance Records", "UC-88; BR-59, BR-61, BR-62, BR-177; MSG24",
            precondition, input, expected, execution);
    }

    private static Scenario ready(String status) {
        AttendanceRepository attendance = mock(AttendanceRepository.class);
        ShiftRepository shifts = mock(ShiftRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        Scenario scenario = new Scenario(attendance, shifts, accounts);
        when(shifts.actor(any(), anyString(), eq(BRANCH))).thenReturn(true);
        when(shifts.branch(eq(BRANCH), anyBoolean())).thenReturn(Map.of(
            "name", "Chi nhánh Quận 1", "status", status, "timezone", ZONE.getId()));
        when(shifts.templates()).thenReturn(List.of(Map.of("code", "MORNING")));
        when(accounts.manages(MANAGER, BRANCH, false)).thenReturn(true);
        when(attendance.period(eq(BRANCH), any(LocalDate.class), anyBoolean())).thenReturn(
            new LinkedHashMap<>(Map.of("status", "OPEN", "version", 0)));
        when(attendance.rows(eq(BRANCH), any(LocalDate.class), any(LocalDate.class)))
            .thenAnswer(invocation -> new ArrayList<>(scenario.rows));
        when(attendance.issues(eq(BRANCH), any(LocalDate.class), any(LocalDate.class)))
            .thenAnswer(invocation -> new ArrayList<>(scenario.issues));
        return scenario;
    }

    private static Map<String, Object> worked(LocalDate day, int minutes) {
        Instant start = day.atTime(8, 0).atZone(ZONE).toInstant();
        return row(day, "WORKED", start, start.plusSeconds(minutes * 60L), minutes, true);
    }

    private static Map<String, Object> absent(LocalDate day) {
        Instant start = day.atTime(8, 0).atZone(ZONE).toInstant();
        return row(day, "ABSENT", start, start.plusSeconds(5 * 3600L), 0, false);
    }

    private static Map<String, Object> row(LocalDate day, String status, Instant start, Instant end,
                                           int minutes, boolean withTimes) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("assignmentId", UUID.randomUUID());
        row.put("employeeId", EMPLOYEE);
        row.put("code", "NV001");
        row.put("fullName", "Nguyễn Văn A");
        row.put("workDate", day.toString());
        row.put("startsAt", Timestamp.from(start));
        row.put("endsAt", Timestamp.from(end));
        row.put("attendanceId", UUID.randomUUID());
        row.put("status", status);
        row.put("actualIn", withTimes ? Timestamp.from(start) : null);
        row.put("actualOut", withTimes ? Timestamp.from(end) : null);
        row.put("workedMinutes", minutes);
        row.put("breakMinutes", 0);
        row.put("openIssues", 0L);
        return row;
    }

    private static Identity manager() {
        return identity("BRANCH_MANAGER");
    }

    private static Identity identity(String role) {
        return new Identity(MANAGER, "manager", role, "/", BRANCH, "Chi nhánh Quận 1");
    }

    private static String message(Throwing action) {
        try {
            action.run();
            return "NO_ERROR";
        } catch (AuthFailure failure) {
            return failure.getMessage();
        }
    }

    @FunctionalInterface
    private interface Throwing {
        void run();
    }

    private static final class Scenario {
        final AttendanceRepository attendance;
        final AccountRepository accounts;
        final AttendanceService service;
        List<Map<String, Object>> rows = List.of();
        List<Map<String, Object>> issues = List.of();
        int writeCount;

        Scenario(AttendanceRepository attendance, ShiftRepository shifts, AccountRepository accounts) {
            this.attendance = attendance;
            this.accounts = accounts;
            this.service = new AttendanceService(attendance, shifts, new ShiftService(shifts, accounts));
        }
    }
}
