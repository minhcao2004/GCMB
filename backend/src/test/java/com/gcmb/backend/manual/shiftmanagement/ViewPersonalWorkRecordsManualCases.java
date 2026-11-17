/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 13/11/2026
 * Description: Manual unit test cases cho UC-90 View Personal Work Records.
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

final class ViewPersonalWorkRecordsManualCases {
    private static final UUID USER = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID BRANCH = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID EMPLOYEE = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private ViewPersonalWorkRecordsManualCases() {
    }

    static List<ManualCase> cases() {
        return List.of(
            test("SHM-066", "Receptionist đã đăng nhập và tài khoản liên kết đúng hồ sơ nhân viên.",
                "Xem công cá nhân của tháng có dữ liệu.",
                "Chỉ trả dữ liệu của hồ sơ liên kết, đúng tổng phút công và ở chế độ chỉ đọc.",
                ViewPersonalWorkRecordsManualCases::personalRecords),
            test("SHM-067", "Tài khoản có hồ sơ liên kết nhưng tháng được chọn không có dữ liệu.",
                "Xem công cá nhân.",
                "Trả danh sách rỗng, tổng phút và số phân ca bằng 0.",
                ViewPersonalWorkRecordsManualCases::emptyMonth),
            test("SHM-068", "Receptionist có hồ sơ liên kết.",
                "Không truyền tham số month.",
                "Mặc định sử dụng tháng hiện tại theo múi giờ cơ sở.",
                ViewPersonalWorkRecordsManualCases::defaultMonth),
            test("SHM-069", "Nhân viên đã điều chuyển; repository trả lịch sử công ở cơ sở cũ.",
                "Xem công cá nhân.",
                "Giữ lịch sử cơ sở cũ, không cho chỉnh sửa và không nhận employeeId từ input.",
                ViewPersonalWorkRecordsManualCases::historyReadOnly),
            test("SHM-070", "Receptionist có hồ sơ liên kết.",
                "month sai định dạng.",
                "Hiển thị “Tháng phải có định dạng YYYY-MM.”.",
                ViewPersonalWorkRecordsManualCases::invalidMonth),
            test("SHM-071", "Tài khoản không liên kết hồ sơ hoặc người dùng không phải Receptionist.",
                "Xem công cá nhân.",
                "Hiển thị “Bạn không có quyền xem đối với công cá nhân.”.",
                ViewPersonalWorkRecordsManualCases::authorization),
            test("SHM-072", "Receptionist thuộc cơ sở SUSPENDED và có hồ sơ liên kết.",
                "Xem công cá nhân.",
                "Vẫn xem được lịch sử ở chế độ chỉ đọc.",
                ViewPersonalWorkRecordsManualCases::suspendedReadOnly)
        );
    }

    private static Observation personalRecords() {
        Scenario scenario = ready("ACTIVE", EMPLOYEE);
        YearMonth month = YearMonth.now(ZONE).minusMonths(1);
        scenario.rows = List.of(worked(month.atDay(5), "Chi nhánh cũ", 300));
        Map<String, Object> result = scenario.service.personal(receptionist(), month.toString());
        boolean passed = ((List<?>) result.get("rows")).size() == 1
            && ((Number) result.get("workedMinutes")).longValue() == 300
            && ((Number) result.get("assignmentCount")).longValue() == 1
            && Boolean.TRUE.equals(result.get("readOnly")) && EMPLOYEE.equals(scenario.requestedEmployee);
        return new Observation("rows=" + ((List<?>) result.get("rows")).size() + ", workedMinutes="
            + result.get("workedMinutes") + ", requestedEmployee=" + scenario.requestedEmployee
            + ", readOnly=" + result.get("readOnly"), passed);
    }

    private static Observation emptyMonth() {
        Scenario scenario = ready("ACTIVE", EMPLOYEE);
        Map<String, Object> result = scenario.service.personal(receptionist(), "2025-01");
        boolean passed = ((List<?>) result.get("rows")).isEmpty()
            && ((Number) result.get("workedMinutes")).longValue() == 0
            && ((Number) result.get("assignmentCount")).longValue() == 0;
        return new Observation("rows=0, workedMinutes=" + result.get("workedMinutes")
            + ", assignmentCount=" + result.get("assignmentCount"), passed);
    }

    private static Observation defaultMonth() {
        Scenario scenario = ready("ACTIVE", EMPLOYEE);
        Map<String, Object> result = scenario.service.personal(receptionist(), null);
        String expected = YearMonth.now(ZONE).toString();
        return new Observation("month=" + result.get("month"), expected.equals(result.get("month")));
    }

    private static Observation historyReadOnly() {
        Scenario scenario = ready("ACTIVE", EMPLOYEE);
        YearMonth month = YearMonth.now(ZONE).minusMonths(2);
        scenario.rows = List.of(worked(month.atDay(5), "Chi nhánh cũ", 300));
        Map<String, Object> result = scenario.service.personal(receptionist(), month.toString());
        @SuppressWarnings("unchecked")
        Map<String, Object> row = (Map<String, Object>) ((List<?>) result.get("rows")).get(0);
        return new Observation("branchName=" + row.get("branchName") + ", requestedEmployee="
            + scenario.requestedEmployee + ", readOnly=" + result.get("readOnly"),
            "Chi nhánh cũ".equals(row.get("branchName")) && EMPLOYEE.equals(scenario.requestedEmployee)
                && Boolean.TRUE.equals(result.get("readOnly")));
    }

    private static Observation invalidMonth() {
        Scenario scenario = ready("ACTIVE", EMPLOYEE);
        String actual = message(() -> scenario.service.personal(receptionist(), "13/2026"));
        return new Observation("message=" + actual, "Tháng phải có định dạng YYYY-MM.".equals(actual));
    }

    private static Observation authorization() {
        Scenario unlinked = ready("ACTIVE", null);
        String first = message(() -> unlinked.service.personal(receptionist(), null));
        Scenario wrongRole = ready("ACTIVE", EMPLOYEE);
        String second = message(() -> wrongRole.service.personal(identity("BRANCH_MANAGER"), null));
        String expected = "Bạn không có quyền xem đối với công cá nhân.";
        return new Observation("messages=" + List.of(first, second),
            first.equals(expected) && second.equals(expected));
    }

    private static Observation suspendedReadOnly() {
        Scenario scenario = ready("SUSPENDED", EMPLOYEE);
        Map<String, Object> result = scenario.service.personal(receptionist(), null);
        return new Observation("readOnly=" + result.get("readOnly"), Boolean.TRUE.equals(result.get("readOnly")));
    }

    private static ManualCase test(String id, String precondition, String input, String expected,
                                   ShiftManagementManualRunner.Execution execution) {
        return new ManualCase(id, "UC-90 View Personal Work Records", "UC-90; BR-60, BR-61, BR-177; MSG24",
            precondition, input, expected, execution);
    }

    private static Scenario ready(String branchStatus, UUID linkedEmployee) {
        AttendanceRepository attendance = mock(AttendanceRepository.class);
        ShiftRepository shifts = mock(ShiftRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        Scenario scenario = new Scenario(attendance, shifts, accounts);
        when(shifts.actor(any(), anyString(), eq(BRANCH))).thenReturn(true);
        when(shifts.branch(eq(BRANCH), anyBoolean())).thenReturn(Map.of(
            "name", "Chi nhánh hiện tại", "status", branchStatus, "timezone", ZONE.getId()));
        when(shifts.linkedEmployee(USER)).thenReturn(linkedEmployee);
        when(attendance.personalRows(eq(EMPLOYEE), any(LocalDate.class), any(LocalDate.class)))
            .thenAnswer(invocation -> {
                scenario.requestedEmployee = invocation.getArgument(0);
                return new ArrayList<>(scenario.rows);
            });
        return scenario;
    }

    private static Map<String, Object> worked(LocalDate day, String branchName, int minutes) {
        Instant start = day.atTime(8, 0).atZone(ZONE).toInstant();
        Instant end = start.plusSeconds(minutes * 60L);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("employeeId", EMPLOYEE);
        row.put("code", "NV001");
        row.put("fullName", "Nguyễn Văn A");
        row.put("branchName", branchName);
        row.put("startsAt", Timestamp.from(start));
        row.put("endsAt", Timestamp.from(end));
        row.put("attendanceId", UUID.randomUUID());
        row.put("status", "WORKED");
        row.put("actualIn", Timestamp.from(start));
        row.put("actualOut", Timestamp.from(end));
        row.put("workedMinutes", minutes);
        row.put("breakMinutes", 0);
        row.put("openIssues", 0L);
        return row;
    }

    private static Identity receptionist() {
        return identity("RECEPTIONIST");
    }

    private static Identity identity(String role) {
        return new Identity(USER, "receptionist", role, "/", BRANCH, "Chi nhánh hiện tại");
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
        List<Map<String, Object>> rows = List.of();
        UUID requestedEmployee;

        Scenario(AttendanceRepository attendance, ShiftRepository shifts, AccountRepository accounts) {
            this.service = new AttendanceService(attendance, shifts, new ShiftService(shifts, accounts));
        }
    }
}
