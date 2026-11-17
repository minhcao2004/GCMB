/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 13/11/2026
 * Description: Manual unit test cases cho UC-86 Update Shift Schedule.
 */
package com.gcmb.backend.manual.shiftmanagement;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gcmb.backend.accounts.repository.AccountRepository;
import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.exception.AuthFailure;
import com.gcmb.backend.manual.shiftmanagement.ShiftManagementManualRunner.ManualCase;
import com.gcmb.backend.manual.shiftmanagement.ShiftManagementManualRunner.Observation;
import com.gcmb.backend.workforce.dto.ShiftDtos.Replacement;
import com.gcmb.backend.workforce.repository.ShiftRepository;
import com.gcmb.backend.workforce.service.ShiftService;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class UpdateShiftScheduleManualCases {
    private static final UUID BRANCH = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID MANAGER = UUID.fromString("47570000-0000-4000-8000-000000000003");
    private static final UUID SHIFT = UUID.fromString("86000000-0000-4000-8000-000000000001");
    private static final UUID ASSIGNMENT = UUID.fromString("86000000-0000-4000-8000-000000000002");
    private static final UUID EMPLOYEE_A = UUID.fromString("47570000-0000-4000-8000-000000000011");
    private static final UUID EMPLOYEE_B = UUID.fromString("47570000-0000-4000-8000-000000000012");
    private static final String TITLE = "UC-86 Update Shift Schedule";
    private static final String SOURCE = "UC-86; BR-58, BR-63, BR-111, BR-177; MSG02, MSG24, MSG43, MSG86; V14, V28; ShiftService.assignEmpty, ShiftService.replace";
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private UpdateShiftScheduleManualCases() {
    }

    static List<ManualCase> cases() {
        return List.of(
            test("SHM-029",
                "Branch Manager quản lý cơ sở hoạt động; ca chưa có người và chưa bắt đầu; nhân viên hợp lệ.",
                "Phân công nhân viên vào ca trống.",
                "Tạo một phân công CONFIRMED, nguồn MANAGER; lịch tuần phản ánh nhân viên mới.",
                UpdateShiftScheduleManualCases::assignEmpty),
            test("SHM-030",
                "Ca có nhân viên A; nhân viên B hợp lệ và chưa có ca trùng.",
                "Thay A bằng B với lý do hợp lệ và version hiện tại.",
                "Phân công A chuyển CANCELLED; tạo phân công B CONFIRMED; lịch sử giữ lý do thay người.",
                UpdateShiftScheduleManualCases::replaceEmployee),
            test("SHM-031",
                "Branch Manager đã mở chức năng thay người.",
                "Bỏ trống nhân viên thay hoặc để lý do rỗng.",
                "Hiển thị đúng thông báo yêu cầu bổ sung; lịch không đổi.",
                UpdateShiftScheduleManualCases::requiredReplacementFields),
            test("SHM-032",
                "Ca đang được phân công cho nhân viên A.",
                "Chọn lại nhân viên A làm người thay.",
                "Hiển thị “Nhân viên làm thay phải khác người hiện tại.”; không hủy phân công cũ.",
                UpdateShiftScheduleManualCases::sameEmployee),
            test("SHM-033",
                "Phân công còn tồn tại nhưng ca đã kết thúc hoặc bị hủy.",
                "Thay nhân viên.",
                "Hiển thị đúng thông báo vòng đời ca; lịch không đổi.",
                UpdateShiftScheduleManualCases::endedOrCancelled),
            test("SHM-034",
                "Ca thuộc tháng đã chốt hoặc phân công đã có attendance.",
                "Phân công hoặc thay nhân viên.",
                "Hiển thị thông báo ca bị khóa; không thay đổi lịch.",
                UpdateShiftScheduleManualCases::closedOrAttended),
            test("SHM-035",
                "Nhân viên thay không hoạt động hoặc không thuộc cơ sở trong thời gian ca.",
                "Chọn nhân viên đó làm người thay.",
                "Hiển thị thông báo phải chọn lễ tân đang hoạt động tại cơ sở; không thay lịch.",
                UpdateShiftScheduleManualCases::ineligibleReplacement),
            test("SHM-036",
                "Nhân viên thay đã có ca trùng thời gian.",
                "Thay nhân viên.",
                "Hiển thị “Nhân viên làm thay đã có ca trùng thời gian.”; giữ phân công hiện tại.",
                UpdateShiftScheduleManualCases::overlap),
            test("SHM-037",
                "Nhân viên thay đã đạt giới hạn ca trong ngày.",
                "Phân công thêm ca.",
                "Hiển thị đúng thông báo giới hạn ca trong ngày.",
                UpdateShiftScheduleManualCases::dailyLimit),
            test("SHM-038",
                "Lịch đã thay đổi sau khi Branch Manager mở màn hình.",
                "Thay người bằng version cũ.",
                "Hiển thị thông báo lịch đã thay đổi; không ghi đè.",
                UpdateShiftScheduleManualCases::staleVersion),
            test("SHM-039",
                "Lần lượt kiểm tra người sai vai trò, quản lý ngoài phạm vi và cơ sở SUSPENDED.",
                "Cập nhật phân công hợp lệ.",
                "Người sai phạm vi bị từ chối; cơ sở tạm ngừng chỉ được xem dữ liệu.",
                UpdateShiftScheduleManualCases::authorization)
        );
    }

    private static Observation assignEmpty() {
        Scenario scenario = ready();
        scenario.service.assignEmpty(manager(), SHIFT, EMPLOYEE_B);
        boolean passed = scenario.assignCount == 1
            && scenario.auditCount == 1
            && "MANAGER".equals(scenario.lastSource)
            && "Phân công ca trống".equals(scenario.lastReason);
        return new Observation("assignWrites=" + scenario.assignCount + ", source=" + scenario.lastSource
            + ", reason=" + scenario.lastReason + ", audits=" + scenario.auditCount, passed);
    }

    private static Observation replaceEmployee() {
        Scenario scenario = ready();
        scenario.service.replace(manager(), ASSIGNMENT, new Replacement(EMPLOYEE_B, "Đổi lịch", 0L));
        boolean passed = scenario.cancelCount == 1 && scenario.assignCount == 1
            && scenario.auditCount == 1 && "Đổi lịch".equals(scenario.lastReason);
        return new Observation("cancelWrites=" + scenario.cancelCount + ", assignWrites="
            + scenario.assignCount + ", reason=" + scenario.lastReason, passed);
    }

    private static Observation requiredReplacementFields() {
        Scenario missingEmployee = ready();
        Scenario missingReason = ready();
        List<String> actual = List.of(
            replaceMessage(missingEmployee, new Replacement(null, "Đổi lịch", 0L)),
            replaceMessage(missingReason, new Replacement(EMPLOYEE_B, "   ", 0L))
        );
        List<String> expected = List.of(
            "Vui lòng bổ sung nhân viên làm thay.",
            "Vui lòng bổ sung lý do thay người."
        );
        return new Observation("messages=" + actual, expected.equals(actual));
    }

    private static Observation sameEmployee() {
        Scenario scenario = ready();
        String actual = replaceMessage(scenario, new Replacement(EMPLOYEE_A, "Đổi lịch", 0L));
        return new Observation("message=" + actual + ", cancelWrites=" + scenario.cancelCount,
            "Nhân viên làm thay phải khác người hiện tại.".equals(actual) && scenario.cancelCount == 0);
    }

    private static Observation endedOrCancelled() {
        Scenario ended = ready();
        ended.shift = shiftRow(LocalDate.now(ZONE).minusDays(1), "OPEN", 0L, false);
        Scenario cancelled = ready();
        cancelled.shift = shiftRow(futureDate(), "CANCELLED", 0L, true);
        List<String> actual = List.of(
            replaceMessage(ended, new Replacement(EMPLOYEE_B, "Đổi lịch", 0L)),
            replaceMessage(cancelled, new Replacement(EMPLOYEE_B, "Đổi lịch", 0L))
        );
        return new Observation("messages=" + actual, actual.equals(List.of(
            "Ca đã kết thúc, không thể thay nhân viên.",
            "Ca đã bị hủy."
        )));
    }

    private static Observation closedOrAttended() {
        String locked = "Ca thuộc kỳ công đã chốt hoặc đã có chấm công. Không thể thay đổi lịch này.";
        Scenario closed = ready();
        when(closed.repo.closedPeriod(eq(BRANCH), any())).thenReturn(true);
        Scenario attended = ready();
        when(attended.repo.attended(ASSIGNMENT)).thenReturn(true);
        List<String> actual = List.of(
            assignMessage(closed, EMPLOYEE_B),
            replaceMessage(attended, new Replacement(EMPLOYEE_B, "Đổi lịch", 0L))
        );
        return new Observation("messages=" + actual, actual.equals(List.of(locked, locked)));
    }

    private static Observation ineligibleReplacement() {
        Scenario scenario = ready();
        when(scenario.repo.belongs(eq(EMPLOYEE_B), eq(BRANCH), any(), any())).thenReturn(false);
        String actual = replaceMessage(scenario, new Replacement(EMPLOYEE_B, "Đổi lịch", 0L));
        return new Observation("message=" + actual,
            "Vui lòng chọn lễ tân đang hoạt động và được phân công tại cơ sở của ca.".equals(actual));
    }

    private static Observation overlap() {
        Scenario scenario = ready();
        when(scenario.repo.overlap(eq(EMPLOYEE_B), eq(SHIFT), any(), any())).thenReturn(true);
        String actual = replaceMessage(scenario, new Replacement(EMPLOYEE_B, "Đổi lịch", 0L));
        return new Observation("message=" + actual,
            "Nhân viên làm thay đã có ca trùng thời gian.".equals(actual));
    }

    private static Observation dailyLimit() {
        Scenario scenario = ready();
        when(scenario.repo.withinDailyLimit(EMPLOYEE_B, SHIFT)).thenReturn(false);
        String actual = replaceMessage(scenario, new Replacement(EMPLOYEE_B, "Đổi lịch", 0L));
        return new Observation("message=" + actual,
            "Mỗi ngày tối đa 3 ca. Nếu có ca 00:00–07:00 thì chỉ được làm tối đa 2 ca trong ngày.".equals(actual));
    }

    private static Observation staleVersion() {
        Scenario scenario = ready();
        String actual = replaceMessage(scenario, new Replacement(EMPLOYEE_B, "Đổi lịch", 99L));
        return new Observation("message=" + actual,
            "Lịch ca đã thay đổi. Vui lòng tải lại và kiểm tra thông tin trước khi xác nhận.".equals(actual));
    }

    private static Observation authorization() {
        Scenario denied = ready();
        Identity receptionist = new Identity(UUID.randomUUID(), "letan", "RECEPTIONIST", "/reception", BRANCH, "Hoa Bằng");
        String deniedMessage = assignMessage(denied, receptionist, EMPLOYEE_B);
        Scenario outside = ready();
        when(outside.accounts.manages(MANAGER, BRANCH, false)).thenReturn(false);
        String outsideMessage = assignMessage(outside, manager(), EMPLOYEE_B);
        Scenario suspended = ready();
        suspended.status = "SUSPENDED";
        String suspendedMessage = assignMessage(suspended, manager(), EMPLOYEE_B);
        String deniedExpected = "Bạn không có quyền thực hiện thao tác đối với lịch ca tại cơ sở này.";
        String suspendedExpected = "Cơ sở GenZ Cinema Hoa Bằng đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi.";
        return new Observation("messages=" + List.of(deniedMessage, outsideMessage, suspendedMessage),
            List.of(deniedMessage, outsideMessage, suspendedMessage)
                .equals(List.of(deniedExpected, deniedExpected, suspendedExpected)));
    }

    private static String assignMessage(Scenario scenario, UUID employee) {
        return assignMessage(scenario, manager(), employee);
    }

    private static String assignMessage(Scenario scenario, Identity actor, UUID employee) {
        try {
            scenario.service.assignEmpty(actor, SHIFT, employee);
            return "Không phát sinh lỗi";
        } catch (AuthFailure failure) {
            return failure.getMessage();
        }
    }

    private static String replaceMessage(Scenario scenario, Replacement input) {
        try {
            scenario.service.replace(manager(), ASSIGNMENT, input);
            return "Không phát sinh lỗi";
        } catch (AuthFailure failure) {
            return failure.getMessage();
        }
    }

    private static Scenario ready() {
        ShiftRepository repo = mock(ShiftRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        Scenario scenario = new Scenario(repo, accounts, new ShiftService(repo, accounts));
        scenario.shift = shiftRow(futureDate(), "OPEN", 0L, true);
        when(repo.actor(any(), anyString(), eq(BRANCH))).thenReturn(true);
        when(accounts.manages(MANAGER, BRANCH, false)).thenReturn(true);
        when(repo.branch(eq(BRANCH), anyBoolean())).thenAnswer(invocation -> branch(scenario.status));
        when(repo.shift(SHIFT, BRANCH)).thenAnswer(invocation -> scenario.shift);
        when(repo.assignment(ASSIGNMENT, BRANCH)).thenReturn(assignment());
        when(repo.registered(SHIFT)).thenReturn(0L);
        when(repo.closedPeriod(eq(BRANCH), any())).thenReturn(false);
        when(repo.hasAttendance(SHIFT)).thenReturn(false);
        when(repo.attended(ASSIGNMENT)).thenReturn(false);
        when(repo.belongs(eq(EMPLOYEE_B), eq(BRANCH), any(), any())).thenReturn(true);
        when(repo.overlap(eq(EMPLOYEE_B), eq(SHIFT), any(), any())).thenReturn(false);
        when(repo.withinDailyLimit(EMPLOYEE_B, SHIFT)).thenReturn(true);
        when(repo.templates()).thenReturn(List.of());
        when(repo.shifts(eq(BRANCH), any())).thenReturn(List.of());
        when(repo.assignments(eq(BRANCH), any(), isNull())).thenReturn(List.of());
        when(repo.history(eq(BRANCH), any())).thenReturn(List.of());
        when(repo.employees(BRANCH)).thenReturn(List.of());
        when(repo.closedMonths(eq(BRANCH), any())).thenReturn(Set.of());
        when(repo.assign(eq(SHIFT), any(), eq(MANAGER), eq("MANAGER"), anyString()))
            .thenAnswer(invocation -> {
                scenario.assignCount++;
                scenario.lastSource = invocation.getArgument(3);
                scenario.lastReason = invocation.getArgument(4);
                return UUID.randomUUID();
            });
        doAnswer(invocation -> {
            scenario.cancelCount++;
            scenario.lastReason = invocation.getArgument(1);
            return null;
        }).when(repo).cancel(eq(ASSIGNMENT), anyString());
        doAnswer(invocation -> {
            scenario.auditCount++;
            return null;
        }).when(repo).audit(any(), any(), any(), anyString(), anyMap());
        return scenario;
    }

    private static Map<String, Object> assignment() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", ASSIGNMENT);
        row.put("shift_id", SHIFT);
        row.put("employee_id", EMPLOYEE_A);
        row.put("status", "CONFIRMED");
        return row;
    }

    private static Map<String, Object> shiftRow(LocalDate date, String status, long version, boolean futureTime) {
        Instant start = futureTime
            ? Instant.now().plusSeconds(7200)
            : date.atTime(7, 0).atZone(ZONE).toInstant();
        Instant end = futureTime ? start.plusSeconds(5 * 3600) : start.plusSeconds(5 * 3600);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", SHIFT);
        row.put("work_date", Date.valueOf(date));
        row.put("starts_at", Timestamp.from(start));
        row.put("ends_at", Timestamp.from(end));
        row.put("capacity", 2);
        row.put("status", status);
        row.put("version", version);
        return row;
    }

    private static LocalDate futureDate() {
        return LocalDate.now(ZONE).plusDays(1);
    }

    private static Identity manager() {
        return new Identity(MANAGER, "qlcs.hoabang", "BRANCH_MANAGER", "/branch", BRANCH, "GenZ Cinema Hoa Bằng");
    }

    private static Map<String, Object> branch(String status) {
        return Map.of("name", "GenZ Cinema Hoa Bằng", "status", status, "timezone", ZONE.getId());
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
        int cancelCount;
        int auditCount;
        String lastSource;
        String lastReason;

        Scenario(ShiftRepository repo, AccountRepository accounts, ShiftService service) {
            this.repo = repo;
            this.accounts = accounts;
            this.service = service;
        }
    }
}
