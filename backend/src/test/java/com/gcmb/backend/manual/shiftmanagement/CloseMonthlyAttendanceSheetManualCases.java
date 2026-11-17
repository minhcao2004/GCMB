/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 13/11/2026
 * Description: Manual unit test cases cho UC-89 Close Monthly Attendance Sheet.
 */
package com.gcmb.backend.manual.shiftmanagement;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gcmb.backend.accounts.repository.AccountRepository;
import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.exception.AuthFailure;
import com.gcmb.backend.manual.shiftmanagement.ShiftManagementManualRunner.ManualCase;
import com.gcmb.backend.manual.shiftmanagement.ShiftManagementManualRunner.Observation;
import com.gcmb.backend.workforce.dto.AttendanceDtos.CloseCommand;
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

final class CloseMonthlyAttendanceSheetManualCases {
    private static final UUID MANAGER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID BRANCH = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID EMPLOYEE = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID PERIOD = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private CloseMonthlyAttendanceSheetManualCases() {
    }

    static List<ManualCase> cases() {
        return List.of(
            test("SHM-058", "Branch Manager; cơ sở ACTIVE; tháng đã kết thúc; mọi bản ghi hợp lệ; không có phản ánh OPEN.",
                "Xác nhận chốt với revision mới nhất.",
                "Tháng chuyển CLOSED, lưu người chốt và không còn cho phép sửa trực tiếp.",
                CloseMonthlyAttendanceSheetManualCases::closeValidMonth),
            test("SHM-059", "Tháng đã kết thúc nhưng không có phân ca.",
                "Xác nhận chốt tháng.",
                "Hiển thị thông báo chưa thể chốt và không cập nhật kỳ công.",
                CloseMonthlyAttendanceSheetManualCases::emptyMonth),
            test("SHM-060", "Tháng lần lượt có bản ghi thiếu, bản ghi không hợp lệ hoặc phản ánh OPEN.",
                "Xác nhận chốt tháng.",
                "Hiển thị đúng thông báo chưa thể chốt cho từng trường hợp.",
                CloseMonthlyAttendanceSheetManualCases::incompleteMonth),
            test("SHM-061", "Tháng hiện tại chưa kết thúc hoặc ca vẫn chưa kết thúc.",
                "Xác nhận chốt tháng.",
                "Không cho chốt trước thời điểm hợp lệ.",
                CloseMonthlyAttendanceSheetManualCases::monthNotEnded),
            test("SHM-062", "Tháng đủ điều kiện chốt.",
                "confirmedCorrect=false hoặc command null.",
                "Hiển thị “Vui lòng xác nhận không còn bản ghi chấm công chưa đúng.”.",
                CloseMonthlyAttendanceSheetManualCases::confirmationRequired),
            test("SHM-063", "Bảng công đã thay đổi sau khi người dùng mở trang.",
                "Gửi revision cũ.",
                "Hiển thị thông báo bảng công đã thay đổi; không chốt.",
                CloseMonthlyAttendanceSheetManualCases::staleRevision),
            test("SHM-064", "Tháng công đã CLOSED.",
                "Chốt lại tháng.",
                "Hiển thị thông báo tháng đã chốt; không chốt lần hai.",
                CloseMonthlyAttendanceSheetManualCases::alreadyClosed),
            test("SHM-065", "Người dùng sai vai trò hoặc cơ sở SUSPENDED.",
                "Chốt bảng công tháng.",
                "Từ chối thao tác và không cập nhật kỳ công.",
                CloseMonthlyAttendanceSheetManualCases::authorization)
        );
    }

    private static Observation closeValidMonth() {
        Scenario scenario = ready("ACTIVE");
        String revision = (String) scenario.service.monthly(manager(), scenario.month.toString()).get("revision");
        Map<String, Object> result = scenario.service.close(manager(), scenario.month.toString(),
            new CloseCommand(revision, true));
        return new Observation("status=" + ((Map<?, ?>) result.get("period")).get("status")
            + ", closeWrites=" + scenario.closeCount + ", canClose=" + result.get("canClose"),
            scenario.closeCount == 1 && "CLOSED".equals(((Map<?, ?>) result.get("period")).get("status"))
                && Boolean.FALSE.equals(result.get("canClose")));
    }

    private static Observation emptyMonth() {
        Scenario scenario = ready("ACTIVE");
        scenario.rows = List.of();
        String actual = closeMessage(scenario, new CloseCommand("anything", true));
        return result(actual, scenario, "Chưa thể chốt công tháng");
    }

    private static Observation incompleteMonth() {
        Scenario missing = ready("ACTIVE");
        missing.rows = List.of(row(missing.month.atDay(5), null, "WORKED", true));
        String first = closeMessage(missing, new CloseCommand("x", true));

        Scenario invalid = ready("ACTIVE");
        invalid.rows = List.of(row(invalid.month.atDay(5), UUID.randomUUID(), "WORKED", false));
        String second = closeMessage(invalid, new CloseCommand("x", true));

        Scenario issue = ready("ACTIVE");
        issue.issues = List.of(new LinkedHashMap<>(Map.of("status", "OPEN")));
        String third = closeMessage(issue, new CloseCommand("x", true));
        boolean passed = List.of(first, second, third).stream().allMatch(value -> value.startsWith("Chưa thể chốt công tháng"))
            && missing.closeCount + invalid.closeCount + issue.closeCount == 0;
        return new Observation("messages=" + List.of(first, second, third), passed);
    }

    private static Observation monthNotEnded() {
        Scenario scenario = ready("ACTIVE");
        scenario.month = YearMonth.now(ZONE);
        scenario.rows = List.of(row(scenario.month.atDay(1), UUID.randomUUID(), "WORKED", true));
        String actual = closeMessage(scenario, new CloseCommand("x", true));
        return result(actual, scenario, "Chưa thể chốt công tháng");
    }

    private static Observation confirmationRequired() {
        Scenario firstScenario = ready("ACTIVE");
        String revision = (String) firstScenario.service.monthly(manager(), firstScenario.month.toString()).get("revision");
        String first = closeMessage(firstScenario, new CloseCommand(revision, false));
        Scenario secondScenario = ready("ACTIVE");
        String second = closeMessage(secondScenario, null);
        String expected = "Vui lòng xác nhận không còn bản ghi chấm công chưa đúng.";
        return new Observation("messages=" + List.of(first, second), first.equals(expected) && second.equals(expected)
            && firstScenario.closeCount + secondScenario.closeCount == 0);
    }

    private static Observation staleRevision() {
        Scenario scenario = ready("ACTIVE");
        String actual = closeMessage(scenario, new CloseCommand("revision-cu", true));
        return result(actual, scenario, "Bảng công đã thay đổi. Vui lòng tải lại và kiểm tra trước khi xác nhận.");
    }

    private static Observation alreadyClosed() {
        Scenario scenario = ready("ACTIVE");
        scenario.periodStatus = "CLOSED";
        String actual = closeMessage(scenario, new CloseCommand("x", true));
        String expected = "Công tháng %02d/%04d đã được chốt nên không thể sửa trực tiếp bản ghi chấm công này."
            .formatted(scenario.month.getMonthValue(), scenario.month.getYear());
        return result(actual, scenario, expected);
    }

    private static Observation authorization() {
        Scenario wrongRole = ready("ACTIVE");
        String first = message(() -> wrongRole.service.close(identity("RECEPTIONIST"), wrongRole.month.toString(),
            new CloseCommand("x", true)));
        Scenario suspended = ready("SUSPENDED");
        String second = closeMessage(suspended, new CloseCommand("x", true));
        boolean passed = first.contains("không có quyền") && second.contains("đang tạm ngừng hoạt động")
            && wrongRole.closeCount + suspended.closeCount == 0;
        return new Observation("messages=" + List.of(first, second), passed);
    }

    private static Observation result(String actual, Scenario scenario, String expected) {
        return new Observation("message=" + actual + ", closeWrites=" + scenario.closeCount,
            (actual.equals(expected) || actual.startsWith(expected)) && scenario.closeCount == 0);
    }

    private static String closeMessage(Scenario scenario, CloseCommand command) {
        return message(() -> scenario.service.close(manager(), scenario.month.toString(), command));
    }

    private static ManualCase test(String id, String precondition, String input, String expected,
                                   ShiftManagementManualRunner.Execution execution) {
        return new ManualCase(id, "UC-89 Close Monthly Attendance Sheet",
            "UC-89; BR-62, BR-63, BR-64, BR-177; MSG20, MSG43, MSG86", precondition, input, expected, execution);
    }

    private static Scenario ready(String branchStatus) {
        AttendanceRepository attendance = mock(AttendanceRepository.class);
        ShiftRepository shifts = mock(ShiftRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        Scenario scenario = new Scenario(attendance, shifts, accounts);
        when(shifts.actor(any(), anyString(), eq(BRANCH))).thenReturn(true);
        when(shifts.branch(eq(BRANCH), anyBoolean())).thenReturn(Map.of(
            "name", "Chi nhánh Quận 1", "status", branchStatus, "timezone", ZONE.getId()));
        when(accounts.manages(MANAGER, BRANCH, false)).thenReturn(true);
        when(attendance.period(eq(BRANCH), any(LocalDate.class), anyBoolean())).thenAnswer(invocation ->
            new LinkedHashMap<>(Map.of("id", PERIOD, "status", scenario.periodStatus, "version", 0)));
        when(attendance.ensurePeriod(eq(BRANCH), any(LocalDate.class))).thenAnswer(invocation ->
            new LinkedHashMap<>(Map.of("id", PERIOD, "status", scenario.periodStatus, "version", 0)));
        when(attendance.rows(eq(BRANCH), any(LocalDate.class), any(LocalDate.class)))
            .thenAnswer(invocation -> new ArrayList<>(scenario.rows));
        when(attendance.issues(eq(BRANCH), any(LocalDate.class), any(LocalDate.class)))
            .thenAnswer(invocation -> new ArrayList<>(scenario.issues));
        doAnswer(invocation -> {
            scenario.closeCount++;
            scenario.periodStatus = "CLOSED";
            return null;
        }).when(attendance).close(eq(PERIOD), eq(MANAGER));
        return scenario;
    }

    private static Map<String, Object> row(LocalDate day, UUID attendanceId, String status, boolean valid) {
        Instant start = day.atTime(8, 0).atZone(ZONE).toInstant();
        Instant end = start.plusSeconds(5 * 3600L);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("employeeId", EMPLOYEE);
        row.put("code", "NV001");
        row.put("fullName", "Nguyễn Văn A");
        row.put("startsAt", Timestamp.from(start));
        row.put("endsAt", Timestamp.from(end));
        row.put("attendanceId", attendanceId);
        row.put("status", status);
        row.put("actualIn", valid ? Timestamp.from(start) : Timestamp.from(start));
        row.put("actualOut", valid ? Timestamp.from(end) : Timestamp.from(end.minusSeconds(60)));
        row.put("workedMinutes", valid ? 300 : 300);
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
        final AttendanceService service;
        YearMonth month = YearMonth.now(ZONE).minusMonths(1);
        String periodStatus = "OPEN";
        List<Map<String, Object>> rows;
        List<Map<String, Object>> issues = List.of();
        int closeCount;

        Scenario(AttendanceRepository attendance, ShiftRepository shifts, AccountRepository accounts) {
            this.service = new AttendanceService(attendance, shifts, new ShiftService(shifts, accounts));
            this.rows = List.of(row(month.atDay(5), UUID.randomUUID(), "WORKED", true));
        }
    }
}
