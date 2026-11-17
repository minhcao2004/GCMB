/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 13/11/2026
 * Description: Manual unit test cases cho UC-83 Register for Shift.
 */
package com.gcmb.backend.manual.shiftmanagement;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gcmb.backend.accounts.repository.AccountRepository;
import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.exception.AuthFailure;
import com.gcmb.backend.manual.shiftmanagement.ShiftManagementManualRunner.ManualCase;
import com.gcmb.backend.manual.shiftmanagement.ShiftManagementManualRunner.Observation;
import com.gcmb.backend.workforce.repository.ShiftRepository;
import com.gcmb.backend.workforce.service.ShiftService;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class RegisterForShiftManualCases {
    private static final UUID BRANCH = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID RECEPTIONIST = UUID.fromString("47570000-0000-4000-8000-000000000001");
    private static final UUID EMPLOYEE = UUID.fromString("47570000-0000-4000-8000-000000000011");
    private static final UUID SHIFT = UUID.fromString("83000000-0000-4000-8000-000000000001");
    private static final UUID ASSIGNMENT = UUID.fromString("83000000-0000-4000-8000-000000000002");
    private static final String TITLE = "UC-83 Register for Shift";
    private static final String SOURCE = "UC-83; BR-54, BR-55, BR-56, BR-177; MSG19, MSG24, MSG43, MSG84; V14; ShiftService.register";
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private RegisterForShiftManualCases() {
    }

    static List<ManualCase> cases() {
        return List.of(
            test("SHM-001",
                "Receptionist letan.hoabang đã đăng nhập, có home branch Hoa Bằng đang hoạt động; ca tuần tới đang OPEN và còn chỗ.",
                "Đăng ký ca 07:00–12:00 tại Hoa Bằng.",
                "Đăng ký được tự động xác nhận; tạo đúng một phân công CONFIRMED; số chỗ còn lại giảm một.",
                RegisterForShiftManualCases::validRegistration),
            test("SHM-002",
                "Receptionist đã có phân công được xác nhận trong ca được chọn.",
                "Gửi lại yêu cầu đăng ký cùng ca.",
                "Không tạo phân công trùng; trả về lịch hiện tại với đăng ký cũ được giữ nguyên.",
                RegisterForShiftManualCases::idempotentRegistration),
            test("SHM-003",
                "Receptionist đã đăng nhập; một ca đã đóng và một ca thuộc cơ sở khác.",
                "Lần lượt đăng ký ca đã đóng và ca ngoài home branch.",
                "Hiển thị “Chỉ có thể đăng ký ca đang mở tại cơ sở được phân công. Vui lòng chọn ca phù hợp.”; không tạo phân công.",
                RegisterForShiftManualCases::closedOrForeignShift),
            test("SHM-004",
                "Ca OPEN có capacity bằng số người đã đăng ký.",
                "Đăng ký thêm một Receptionist.",
                "Hiển thị thông báo ca đã đủ người đăng ký; không vượt capacity.",
                RegisterForShiftManualCases::fullShift),
            test("SHM-005",
                "Receptionist và ca đều hợp lệ nhưng ca không thuộc tuần tới.",
                "Đăng ký ca.",
                "Hiển thị “Chỉ đăng ký ca tuần tới trước 22:00 Chủ nhật.”; không tạo phân công.",
                RegisterForShiftManualCases::outsideRegistrationWindow),
            test("SHM-006",
                "Tài khoản Receptionist không liên kết nhân viên hoặc nhân viên không còn thuộc cơ sở trong thời gian ca.",
                "Đăng ký ca OPEN còn chỗ.",
                "Hiển thị “Chỉ có thể đăng ký ca đang mở tại cơ sở được phân công. Vui lòng chọn ca phù hợp.”; không tạo phân công.",
                RegisterForShiftManualCases::ineligibleEmployee),
            test("SHM-007",
                "Receptionist đã có ca trùng thời gian hoặc đã đạt giới hạn ca trong ngày.",
                "Đăng ký thêm ca.",
                "Hiển thị đúng thông báo trùng ca hoặc giới hạn ca; không tạo phân công.",
                RegisterForShiftManualCases::scheduleConflicts),
            test("SHM-008",
                "Branch Manager hoặc Accountant đã đăng nhập.",
                "Thực hiện đăng ký ca như Receptionist.",
                "Hiển thị “Bạn không có quyền thực hiện thao tác đối với lịch ca tại cơ sở này.”; không tạo phân công.",
                RegisterForShiftManualCases::unauthorized),
            test("SHM-009",
                "Receptionist thuộc cơ sở đang SUSPENDED.",
                "Đăng ký ca.",
                "Hiển thị thông báo cơ sở tạm ngừng và chỉ được xem dữ liệu.",
                RegisterForShiftManualCases::suspendedBranch)
        );
    }

    private static Observation validRegistration() {
        Scenario scenario = ready();
        scenario.shift = shiftRow(nextMonday(), "OPEN", 2);
        Map<String, Object> result = scenario.service.register(receptionist(), SHIFT);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assignments = (List<Map<String, Object>>) result.get("assignments");
        boolean passed = scenario.assignCount == 1
            && scenario.auditCount == 1
            && assignments.size() == 1
            && ((Number) assignments.get(0).get("remaining")).intValue() == 1;
        return new Observation("assignWrites=" + scenario.assignCount + ", auditWrites="
            + scenario.auditCount + ", remaining=" + assignments.get(0).get("remaining"), passed);
    }

    private static Observation idempotentRegistration() {
        Scenario scenario = ready();
        scenario.shift = shiftRow(nextMonday(), "OPEN", 2);
        when(scenario.repo.already(EMPLOYEE, SHIFT)).thenReturn(true);
        scenario.existingAssignment = true;
        Map<String, Object> result = scenario.service.register(receptionist(), SHIFT);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assignments = (List<Map<String, Object>>) result.get("assignments");
        return new Observation("assignWrites=" + scenario.assignCount + ", assignments=" + assignments.size(),
            scenario.assignCount == 0 && assignments.size() == 1);
    }

    private static Observation closedOrForeignShift() {
        String expected = "Chỉ có thể đăng ký ca đang mở tại cơ sở được phân công. Vui lòng chọn ca phù hợp.";
        Scenario closed = ready();
        closed.shift = shiftRow(nextMonday(), "CLOSED", 2);
        Scenario foreign = ready();
        foreign.shift = null;
        List<String> actual = List.of(message(closed, receptionist()), message(foreign, receptionist()));
        return new Observation("messages=" + actual,
            actual.equals(List.of(expected, expected)) && closed.assignCount == 0 && foreign.assignCount == 0);
    }

    private static Observation fullShift() {
        Scenario scenario = ready();
        LocalDate date = nextMonday();
        scenario.shift = shiftRow(date, "OPEN", 2);
        when(scenario.repo.registered(SHIFT)).thenReturn(2L);
        String actual = message(scenario, receptionist());
        String expected = "Ca 07:00 ngày " + date + " đã đủ người đăng ký. Vui lòng chọn ca khác.";
        return new Observation("message=" + actual + ", assignWrites=" + scenario.assignCount,
            expected.equals(actual) && scenario.assignCount == 0);
    }

    private static Observation outsideRegistrationWindow() {
        Scenario scenario = ready();
        LocalDate currentMonday = LocalDate.now(ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        scenario.shift = shiftRow(currentMonday.plusDays(6), "OPEN", 2);
        String actual = message(scenario, receptionist());
        return new Observation("message=" + actual,
            "Chỉ đăng ký ca tuần tới trước 22:00 Chủ nhật.".equals(actual) && scenario.assignCount == 0);
    }

    private static Observation ineligibleEmployee() {
        String expected = "Chỉ có thể đăng ký ca đang mở tại cơ sở được phân công. Vui lòng chọn ca phù hợp.";
        Scenario unlinked = ready();
        unlinked.shift = shiftRow(nextMonday(), "OPEN", 2);
        when(unlinked.repo.linkedEmployee(RECEPTIONIST)).thenReturn(null);
        Scenario inactive = ready();
        inactive.shift = shiftRow(nextMonday(), "OPEN", 2);
        when(inactive.repo.belongs(eq(EMPLOYEE), eq(BRANCH), any(), any())).thenReturn(false);
        List<String> actual = List.of(message(unlinked, receptionist()), message(inactive, receptionist()));
        return new Observation("messages=" + actual, actual.equals(List.of(expected, expected)));
    }

    private static Observation scheduleConflicts() {
        Scenario overlap = ready();
        overlap.shift = shiftRow(nextMonday(), "OPEN", 2);
        when(overlap.repo.overlap(eq(EMPLOYEE), eq(SHIFT), any(), any())).thenReturn(true);
        Scenario limit = ready();
        limit.shift = shiftRow(nextMonday(), "OPEN", 2);
        when(limit.repo.withinDailyLimit(EMPLOYEE, SHIFT)).thenReturn(false);
        List<String> actual = List.of(message(overlap, receptionist()), message(limit, receptionist()));
        List<String> expected = List.of(
            "Nhân viên đã có ca trùng thời gian.",
            "Mỗi ngày tối đa 3 ca. Nếu có ca 00:00–07:00 thì chỉ được làm tối đa 2 ca trong ngày."
        );
        return new Observation("messages=" + actual, expected.equals(actual));
    }

    private static Observation unauthorized() {
        Scenario scenario = ready();
        scenario.shift = shiftRow(nextMonday(), "OPEN", 2);
        Identity accountant = new Identity(UUID.randomUUID(), "ketoan", "ACCOUNTANT", "/accountant", null, null);
        String actual = message(scenario, accountant);
        return new Observation("message=" + actual,
            "Bạn không có quyền thực hiện thao tác đối với lịch ca tại cơ sở này.".equals(actual));
    }

    private static Observation suspendedBranch() {
        Scenario scenario = ready();
        scenario.status = "SUSPENDED";
        scenario.shift = shiftRow(nextMonday(), "OPEN", 2);
        String actual = message(scenario, receptionist());
        String expected = "Cơ sở GenZ Cinema Hoa Bằng đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi.";
        return new Observation("message=" + actual, expected.equals(actual));
    }

    private static String message(Scenario scenario, Identity actor) {
        try {
            scenario.service.register(actor, SHIFT);
            return "Không phát sinh lỗi";
        } catch (AuthFailure failure) {
            return failure.getMessage();
        }
    }

    private static Scenario ready() {
        ShiftRepository repo = mock(ShiftRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        Scenario scenario = new Scenario(repo, accounts, new ShiftService(repo, accounts));
        when(repo.actor(any(), anyString(), eq(BRANCH))).thenReturn(true);
        when(accounts.manages(any(), eq(BRANCH), anyBoolean())).thenReturn(true);
        when(repo.branch(eq(BRANCH), anyBoolean())).thenAnswer(invocation -> branch(scenario.status));
        when(repo.templates()).thenReturn(templates());
        when(repo.shift(SHIFT, BRANCH)).thenAnswer(invocation -> scenario.shift);
        when(repo.linkedEmployee(RECEPTIONIST)).thenReturn(EMPLOYEE);
        when(repo.belongs(eq(EMPLOYEE), eq(BRANCH), any(), any())).thenReturn(true);
        when(repo.already(EMPLOYEE, SHIFT)).thenReturn(false);
        when(repo.registered(SHIFT)).thenReturn(0L);
        when(repo.closedPeriod(eq(BRANCH), any())).thenReturn(false);
        when(repo.overlap(eq(EMPLOYEE), eq(SHIFT), any(), any())).thenReturn(false);
        when(repo.withinDailyLimit(EMPLOYEE, SHIFT)).thenReturn(true);
        when(repo.shifts(eq(BRANCH), any())).thenAnswer(invocation -> List.of(scenario.shiftView()));
        when(repo.assignments(eq(BRANCH), any(), eq(EMPLOYEE))).thenAnswer(invocation -> scenario.assignmentViews());
        when(repo.history(eq(BRANCH), any())).thenReturn(List.of());
        when(repo.employees(BRANCH)).thenReturn(List.of());
        when(repo.closedMonths(eq(BRANCH), any())).thenReturn(Set.of());
        when(repo.assign(eq(SHIFT), eq(EMPLOYEE), eq(RECEPTIONIST), eq("SELF"), isNull()))
            .thenAnswer(invocation -> {
                scenario.assignCount++;
                return ASSIGNMENT;
            });
        doAnswer(invocation -> {
            scenario.auditCount++;
            return null;
        }).when(repo).audit(any(), any(), any(), anyString(), anyMap());
        return scenario;
    }

    private static Identity receptionist() {
        return new Identity(RECEPTIONIST, "letan.hoabang", "RECEPTIONIST", "/reception", BRANCH, "GenZ Cinema Hoa Bằng");
    }

    private static LocalDate nextMonday() {
        return LocalDate.now(ZONE).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    private static Map<String, Object> branch(String status) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", BRANCH);
        row.put("name", "GenZ Cinema Hoa Bằng");
        row.put("status", status);
        row.put("timezone", ZONE.getId());
        return row;
    }

    private static List<Map<String, Object>> templates() {
        return List.of(template(0, 420), template(420, 720), template(720, 1080), template(1080, 1440));
    }

    private static Map<String, Object> template(int start, int end) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", UUID.nameUUIDFromBytes((start + "/" + end).getBytes()));
        row.put("startMinute", start);
        row.put("endMinute", end);
        return row;
    }

    private static Map<String, Object> shiftRow(LocalDate date, String status, int capacity) {
        Instant start = date.atTime(7, 0).atZone(ZONE).toInstant();
        Instant end = date.atTime(12, 0).atZone(ZONE).toInstant();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", SHIFT);
        row.put("work_date", Date.valueOf(date));
        row.put("starts_at", Timestamp.from(start));
        row.put("ends_at", Timestamp.from(end));
        row.put("capacity", capacity);
        row.put("status", status);
        row.put("version", 0L);
        return row;
    }

    private static ManualCase test(String id, String precondition, String input, String expected,
                                   ShiftManagementManualRunner.Execution execution) {
        return new ManualCase(id, TITLE, SOURCE, precondition, input, expected, execution);
    }

    private static final class Scenario {
        final ShiftRepository repo;
        final AccountRepository accounts;
        final ShiftService service;
        String status = "ACTIVE";
        Map<String, Object> shift;
        int assignCount;
        int auditCount;
        boolean existingAssignment;

        Scenario(ShiftRepository repo, AccountRepository accounts, ShiftService service) {
            this.repo = repo;
            this.accounts = accounts;
            this.service = service;
        }

        Map<String, Object> shiftView() {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("id", SHIFT);
            view.put("remaining", Math.max(0, 2 - assignCount - (existingAssignment ? 1 : 0)));
            return view;
        }

        List<Map<String, Object>> assignmentViews() {
            if (assignCount == 0 && !existingAssignment) {
                return List.of();
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", ASSIGNMENT);
            row.put("status", "CONFIRMED");
            row.put("remaining", Math.max(0, 2 - assignCount - (existingAssignment ? 1 : 0)));
            return new ArrayList<>(List.of(row));
        }
    }
}
