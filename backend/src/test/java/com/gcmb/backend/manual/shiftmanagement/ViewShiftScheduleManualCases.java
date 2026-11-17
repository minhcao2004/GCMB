/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 13/11/2026
 * Description: Manual unit test cases cho UC-84 View Shift Schedule.
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class ViewShiftScheduleManualCases {
    private static final UUID BRANCH = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID MANAGER = UUID.fromString("47570000-0000-4000-8000-000000000003");
    private static final UUID RECEPTIONIST = UUID.fromString("47570000-0000-4000-8000-000000000001");
    private static final UUID EMPLOYEE = UUID.fromString("47570000-0000-4000-8000-000000000011");
    private static final String TITLE = "UC-84 View Shift Schedule";
    private static final String SOURCE = "UC-84; BR-53, BR-60, BR-177; MSG24, MSG43; V14; ShiftService.weekly";
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private ViewShiftScheduleManualCases() {
    }

    static List<ManualCase> cases() {
        return List.of(
            test("SHM-010",
                "Branch Manager đã đăng nhập và quản lý cơ sở Hoa Bằng.",
                "Xem lịch của tuần hiện tại.",
                "Trả về đúng tuần, tên cơ sở, bốn mẫu ca, các ca và phân công thuộc Hoa Bằng; không ghi dữ liệu.",
                ViewShiftScheduleManualCases::managerViewsWeek),
            test("SHM-011",
                "Cơ sở có lịch tại hai tuần khác nhau.",
                "Lần lượt chọn từng ngày thứ Hai bắt đầu tuần.",
                "Mỗi lần chỉ trả về ca thuộc đúng tuần được chọn.",
                ViewShiftScheduleManualCases::filtersByWeek),
            test("SHM-012",
                "Tuần được chọn có ca, phân công hiện tại và lịch sử thay người.",
                "Branch Manager xem chi tiết tuần.",
                "Trả về danh sách ca, phân công, lịch sử thay đổi và nhân viên của cơ sở; không thay đổi bản ghi.",
                ViewShiftScheduleManualCases::managerDetails),
            test("SHM-013",
                "Tuần được chọn không có ca hoặc phân công.",
                "Xem lịch tuần.",
                "Trả về danh sách ca, phân công và lịch sử rỗng; không phát sinh ghi dữ liệu.",
                ViewShiftScheduleManualCases::emptyWeek),
            test("SHM-014",
                "Receptionist đã đăng nhập và liên kết với hồ sơ nhân viên.",
                "Xem lịch tuần của home branch.",
                "Chỉ trả về phân công của chính Receptionist; không trả danh sách nhân viên hoặc lịch sử quản lý.",
                ViewShiftScheduleManualCases::receptionistScope),
            test("SHM-015",
                "Branch Manager đã đăng nhập.",
                "Chọn ngày không phải thứ Hai làm ngày bắt đầu tuần.",
                "Hiển thị “Vui lòng chọn tuần bắt đầu từ thứ Hai.”",
                ViewShiftScheduleManualCases::invalidWeek),
            test("SHM-016",
                "Lần lượt kiểm tra người không có quyền và Branch Manager tại cơ sở SUSPENDED.",
                "Xem lịch tuần.",
                "Người sai vai trò bị từ chối; quản lý cơ sở tạm ngừng vẫn xem được và kết quả có readOnly=true.",
                ViewShiftScheduleManualCases::authorizationAndReadOnly)
        );
    }

    private static Observation managerViewsWeek() {
        Scenario scenario = ready();
        LocalDate week = currentMonday();
        scenario.dataWeek = week;
        Map<String, Object> result = scenario.service.weekly(manager(), week);
        boolean passed = week.toString().equals(result.get("week"))
            && "GenZ Cinema Hoa Bằng".equals(result.get("branchName"))
            && size(result, "templates") == 4
            && size(result, "shifts") == 1
            && scenario.writeCount == 0;
        return new Observation("week=" + result.get("week") + ", templates=" + size(result, "templates")
            + ", shifts=" + size(result, "shifts") + ", writes=" + scenario.writeCount, passed);
    }

    private static Observation filtersByWeek() {
        Scenario scenario = ready();
        LocalDate current = currentMonday();
        LocalDate previous = current.minusWeeks(1);
        scenario.dataWeek = current;
        int currentCount = size(scenario.service.weekly(manager(), current), "shifts");
        int previousCount = size(scenario.service.weekly(manager(), previous), "shifts");
        return new Observation("currentWeekRows=" + currentCount + ", previousWeekRows=" + previousCount,
            currentCount == 1 && previousCount == 0 && scenario.writeCount == 0);
    }

    private static Observation managerDetails() {
        Scenario scenario = ready();
        scenario.dataWeek = currentMonday();
        scenario.withDetails = true;
        Map<String, Object> result = scenario.service.weekly(manager(), currentMonday());
        boolean passed = size(result, "assignments") == 1
            && size(result, "history") == 1
            && size(result, "employees") == 1
            && scenario.writeCount == 0;
        return new Observation("assignments=" + size(result, "assignments") + ", history="
            + size(result, "history") + ", employees=" + size(result, "employees"), passed);
    }

    private static Observation emptyWeek() {
        Scenario scenario = ready();
        scenario.dataWeek = currentMonday().minusWeeks(3);
        Map<String, Object> result = scenario.service.weekly(manager(), currentMonday());
        boolean passed = size(result, "shifts") == 0 && size(result, "assignments") == 0
            && size(result, "history") == 0 && scenario.writeCount == 0;
        return new Observation("shifts=" + size(result, "shifts") + ", assignments="
            + size(result, "assignments") + ", history=" + size(result, "history"), passed);
    }

    private static Observation receptionistScope() {
        Scenario scenario = ready();
        scenario.dataWeek = currentMonday();
        scenario.withDetails = true;
        Map<String, Object> result = scenario.service.weekly(receptionist(), currentMonday());
        boolean passed = size(result, "assignments") == 1
            && size(result, "history") == 0
            && size(result, "employees") == 0;
        return new Observation("ownAssignments=" + size(result, "assignments") + ", history="
            + size(result, "history") + ", employees=" + size(result, "employees"), passed);
    }

    private static Observation invalidWeek() {
        Scenario scenario = ready();
        String actual = message(() -> scenario.service.weekly(manager(), currentMonday().plusDays(1)));
        return new Observation("message=" + actual, "Vui lòng chọn tuần bắt đầu từ thứ Hai.".equals(actual));
    }

    private static Observation authorizationAndReadOnly() {
        Scenario denied = ready();
        Identity accountant = new Identity(UUID.randomUUID(), "ketoan", "ACCOUNTANT", "/accountant", null, null);
        String deniedMessage = message(() -> denied.service.weekly(accountant, currentMonday()));

        Scenario suspended = ready();
        suspended.status = "SUSPENDED";
        suspended.dataWeek = currentMonday();
        Map<String, Object> result = suspended.service.weekly(manager(), currentMonday());
        boolean passed = "Bạn không có quyền thực hiện thao tác đối với lịch ca tại cơ sở này.".equals(deniedMessage)
            && Boolean.TRUE.equals(result.get("readOnly"));
        return new Observation("deniedMessage=" + deniedMessage + ", suspendedReadOnly=" + result.get("readOnly"), passed);
    }

    private static String message(ShiftCall call) {
        try {
            call.run();
            return "Không phát sinh lỗi";
        } catch (AuthFailure failure) {
            return failure.getMessage();
        }
    }

    private static int size(Map<String, Object> result, String key) {
        return ((List<?>) result.get(key)).size();
    }

    private static Scenario ready() {
        ShiftRepository repo = mock(ShiftRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        Scenario scenario = new Scenario(repo, accounts, new ShiftService(repo, accounts));
        when(repo.actor(any(), anyString(), eq(BRANCH))).thenReturn(true);
        when(accounts.manages(MANAGER, BRANCH, false)).thenReturn(true);
        when(repo.branch(eq(BRANCH), anyBoolean())).thenAnswer(invocation -> branch(scenario.status));
        when(repo.templates()).thenReturn(templates());
        when(repo.linkedEmployee(RECEPTIONIST)).thenReturn(EMPLOYEE);
        when(repo.shifts(eq(BRANCH), any())).thenAnswer(invocation -> {
            LocalDate week = invocation.getArgument(1);
            return week.equals(scenario.dataWeek)
                ? new java.util.ArrayList<>(List.of(new LinkedHashMap<>(Map.of("date", week.toString()))))
                : new java.util.ArrayList<>();
        });
        when(repo.assignments(eq(BRANCH), any(), nullable(UUID.class))).thenAnswer(invocation ->
            scenario.withDetails
                ? new java.util.ArrayList<>(List.of(Map.of("employeeId", EMPLOYEE)))
                : new java.util.ArrayList<>());
        when(repo.history(eq(BRANCH), any())).thenAnswer(invocation ->
            scenario.withDetails ? List.of(Map.of("employeeId", EMPLOYEE)) : List.of());
        when(repo.employees(BRANCH)).thenAnswer(invocation ->
            scenario.withDetails ? List.of(Map.of("id", EMPLOYEE)) : List.of());
        when(repo.closedMonths(eq(BRANCH), any())).thenReturn(Set.of());
        when(repo.publish(any(), any(), any(), any(), any(), anyInt(), anyString(), any(), nullable(Map.class)))
            .thenAnswer(invocation -> {
                scenario.writeCount++;
                return UUID.randomUUID();
            });
        return scenario;
    }

    private static Identity manager() {
        return new Identity(MANAGER, "qlcs.hoabang", "BRANCH_MANAGER", "/branch", BRANCH, "GenZ Cinema Hoa Bằng");
    }

    private static Identity receptionist() {
        return new Identity(RECEPTIONIST, "letan.hoabang", "RECEPTIONIST", "/reception", BRANCH, "GenZ Cinema Hoa Bằng");
    }

    private static LocalDate currentMonday() {
        return LocalDate.now(ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static Map<String, Object> branch(String status) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", "GenZ Cinema Hoa Bằng");
        row.put("status", status);
        row.put("timezone", ZONE.getId());
        return row;
    }

    private static List<Map<String, Object>> templates() {
        return List.of(template(0, 420), template(420, 720), template(720, 1080), template(1080, 1440));
    }

    private static Map<String, Object> template(int start, int end) {
        return Map.of(
            "id", UUID.nameUUIDFromBytes((start + "/" + end).getBytes()),
            "startMinute", start,
            "endMinute", end
        );
    }

    private static ManualCase test(String id, String precondition, String input, String expected,
                                   ShiftManagementManualRunner.Execution execution) {
        return new ManualCase(id, TITLE, SOURCE, precondition, input, expected, execution);
    }

    @FunctionalInterface
    private interface ShiftCall {
        void run();
    }

    private static final class Scenario {
        final ShiftRepository repo;
        final AccountRepository accounts;
        final ShiftService service;
        String status = "ACTIVE";
        LocalDate dataWeek = currentMonday();
        boolean withDetails;
        int writeCount;

        Scenario(ShiftRepository repo, AccountRepository accounts, ShiftService service) {
            this.repo = repo;
            this.accounts = accounts;
            this.service = service;
        }
    }
}
