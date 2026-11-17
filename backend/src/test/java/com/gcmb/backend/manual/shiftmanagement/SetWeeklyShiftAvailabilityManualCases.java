/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 13/11/2026
 * Description: Manual unit test cases cho UC-85 Set Weekly Shift Availability.
 */
package com.gcmb.backend.manual.shiftmanagement;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gcmb.backend.accounts.repository.AccountRepository;
import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.exception.AuthFailure;
import com.gcmb.backend.manual.shiftmanagement.ShiftManagementManualRunner.ManualCase;
import com.gcmb.backend.manual.shiftmanagement.ShiftManagementManualRunner.Observation;
import com.gcmb.backend.workforce.dto.ShiftDtos.Availability;
import com.gcmb.backend.workforce.dto.ShiftDtos.Slot;
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

final class SetWeeklyShiftAvailabilityManualCases {
    private static final UUID BRANCH = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID MANAGER = UUID.fromString("47570000-0000-4000-8000-000000000003");
    private static final UUID NIGHT = UUID.fromString("85000000-0000-4000-8000-000000000001");
    private static final UUID MORNING = UUID.fromString("85000000-0000-4000-8000-000000000002");
    private static final UUID AFTERNOON = UUID.fromString("85000000-0000-4000-8000-000000000003");
    private static final UUID EVENING = UUID.fromString("85000000-0000-4000-8000-000000000004");
    private static final UUID EXISTING_SHIFT = UUID.fromString("85000000-0000-4000-8000-000000000005");
    private static final String TITLE = "UC-85 Set Weekly Shift Availability";
    private static final String SOURCE = "UC-85; BR-53, BR-111, BR-192, BR-203, BR-177; MSG02, MSG24, MSG43, MSG44, MSG85, MSG86; V14, V28; ShiftService.publish";
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private SetWeeklyShiftAvailabilityManualCases() {
    }

    static List<ManualCase> cases() {
        return List.of(
            test("SHM-017",
                "Branch Manager đã đăng nhập; cơ sở hoạt động; tuần tới chưa có lịch được công bố.",
                "Công bố capacity cho các ca trong tuần tới.",
                "Các ca tuần tới được lưu với đúng ngày, ca và capacity, sau đó mở cho đăng ký.",
                SetWeeklyShiftAvailabilityManualCases::publishUpcomingWeek),
            test("SHM-018",
                "Branch Manager đang cấu hình một tuần hợp lệ.",
                "Công bố bốn ca cố định trong cùng ngày.",
                "Chỉ bốn khung giờ cố định được lưu; ca cuối kết thúc vào 00:00 ngày kế tiếp.",
                SetWeeklyShiftAvailabilityManualCases::fourFixedShifts),
            test("SHM-019",
                "Ngày và mẫu ca hợp lệ; chưa có người đăng ký.",
                "Đặt capacity bằng 1.",
                "Capacity 1 được chấp nhận và ca được công bố.",
                SetWeeklyShiftAvailabilityManualCases::capacityMinimum),
            test("SHM-020",
                "Branch Manager đã đăng nhập tại cơ sở hoạt động.",
                "Availability null, danh sách null hoặc danh sách rỗng.",
                "Hiển thị “Vui lòng bổ sung các ca cần công bố.”; không ghi dữ liệu.",
                SetWeeklyShiftAvailabilityManualCases::emptySlots),
            test("SHM-021",
                "Danh sách availability có phần tử.",
                "Bỏ trống ngày hoặc template ca.",
                "Hiển thị “Vui lòng bổ sung ngày và ca làm việc hợp lệ.”; không ghi dữ liệu.",
                SetWeeklyShiftAvailabilityManualCases::missingDateOrTemplate),
            test("SHM-022",
                "Ngày và ca hợp lệ.",
                "Capacity bằng 0 hoặc số âm.",
                "Hiển thị “Số chỗ phải là số nguyên lớn hơn 0.”; không công bố ca.",
                SetWeeklyShiftAvailabilityManualCases::invalidCapacity),
            test("SHM-023",
                "Danh sách lưu có cùng ngày và template hai lần.",
                "Gửi hai slot trùng nhau.",
                "Hiển thị “Một ngày/ca không được xuất hiện hai lần.”; không tạo bản ghi trùng.",
                SetWeeklyShiftAvailabilityManualCases::duplicateSlot),
            test("SHM-024",
                "Các slot riêng lẻ đều hợp lệ.",
                "Gửi các slot thuộc hai tuần khác nhau trong một lần lưu.",
                "Hiển thị “Các ca trong một lần lưu phải thuộc cùng một tuần.”; không cập nhật lịch.",
                SetWeeklyShiftAvailabilityManualCases::mixedWeeks),
            test("SHM-025",
                "Ca đã có hai người đăng ký.",
                "Giảm capacity từ 3 xuống 1.",
                "Hiển thị “Ca này đã có 2 người đăng ký. Số chỗ không được nhỏ hơn 2.”; capacity cũ được giữ nguyên.",
                SetWeeklyShiftAvailabilityManualCases::capacityBelowRegistered),
            test("SHM-026",
                "Người dùng mở lịch ở version cũ; dữ liệu đã được người khác cập nhật.",
                "Lưu slot với version cũ.",
                "Hiển thị “Lịch ca đã thay đổi. Vui lòng tải lại và kiểm tra thông tin trước khi xác nhận.”; không ghi đè.",
                SetWeeklyShiftAvailabilityManualCases::staleVersion),
            test("SHM-027",
                "Ca thuộc tháng đã chốt, đã có chấm công hoặc đã kết thúc.",
                "Thay đổi trạng thái hoặc capacity ca.",
                "Hiển thị đúng thông báo khóa ca; không thay đổi lịch.",
                SetWeeklyShiftAvailabilityManualCases::lockedShift),
            test("SHM-028",
                "Lần lượt kiểm tra Receptionist, Accountant và Branch Manager tại cơ sở SUSPENDED.",
                "Công bố availability hợp lệ.",
                "Người sai vai trò bị từ chối; cơ sở tạm ngừng chỉ được xem dữ liệu.",
                SetWeeklyShiftAvailabilityManualCases::authorization)
        );
    }

    private static Observation publishUpcomingWeek() {
        Scenario scenario = ready();
        Slot slot = new Slot(currentMonday().plusWeeks(1), MORNING, 2, true, null);
        try {
            scenario.service.publish(manager(), new Availability(List.of(slot)));
            return new Observation("publishWrites=" + scenario.publishCount, scenario.publishCount == 1);
        } catch (AuthFailure failure) {
            return new Observation("message=" + failure.getMessage() + ", publishWrites=" + scenario.publishCount, false);
        }
    }

    private static Observation fourFixedShifts() {
        Scenario scenario = ready();
        LocalDate date = editableDate();
        Availability input = new Availability(List.of(
            new Slot(date, NIGHT, 2, true, null),
            new Slot(date, MORNING, 2, true, null),
            new Slot(date, AFTERNOON, 2, true, null),
            new Slot(date, EVENING, 2, true, null)
        ));
        scenario.service.publish(manager(), input);
        return new Observation("publishWrites=" + scenario.publishCount + ", templates=" + scenario.publishedTemplates,
            scenario.publishCount == 4 && scenario.publishedTemplates.containsAll(List.of(NIGHT, MORNING, AFTERNOON, EVENING)));
    }

    private static Observation capacityMinimum() {
        Scenario scenario = ready();
        scenario.service.publish(manager(), availability(1, true, null));
        return new Observation("publishWrites=" + scenario.publishCount + ", capacity=" + scenario.lastCapacity,
            scenario.publishCount == 1 && scenario.lastCapacity == 1);
    }

    private static Observation emptySlots() {
        List<String> actual = List.of(
            message(ready(), null),
            message(ready(), new Availability(null)),
            message(ready(), new Availability(List.of()))
        );
        String expected = "Vui lòng bổ sung các ca cần công bố.";
        return new Observation("messages=" + actual, actual.equals(List.of(expected, expected, expected)));
    }

    private static Observation missingDateOrTemplate() {
        List<String> actual = List.of(
            message(ready(), new Availability(List.of(new Slot(null, MORNING, 2, true, null)))),
            message(ready(), new Availability(List.of(new Slot(editableDate(), null, 2, true, null))))
        );
        String expected = "Vui lòng bổ sung ngày và ca làm việc hợp lệ.";
        return new Observation("messages=" + actual, actual.equals(List.of(expected, expected)));
    }

    private static Observation invalidCapacity() {
        List<String> actual = List.of(
            message(ready(), availability(0, true, null)),
            message(ready(), availability(-1, true, null))
        );
        String expected = "Số chỗ phải là số nguyên lớn hơn 0.";
        return new Observation("messages=" + actual, actual.equals(List.of(expected, expected)));
    }

    private static Observation duplicateSlot() {
        Scenario scenario = ready();
        LocalDate date = editableDate();
        Slot slot = new Slot(date, MORNING, 2, true, null);
        String actual = message(scenario, new Availability(List.of(slot, slot)));
        return new Observation("message=" + actual + ", publishWrites=" + scenario.publishCount,
            "Một ngày/ca không được xuất hiện hai lần.".equals(actual) && scenario.publishCount == 1);
    }

    private static Observation mixedWeeks() {
        Scenario scenario = ready();
        Availability input = new Availability(List.of(
            new Slot(editableDate(), MORNING, 2, true, null),
            new Slot(currentMonday().plusWeeks(1), AFTERNOON, 2, true, null)
        ));
        String actual = message(scenario, input);
        return new Observation("message=" + actual,
            "Các ca trong một lần lưu phải thuộc cùng một tuần.".equals(actual));
    }

    private static Observation capacityBelowRegistered() {
        Scenario scenario = ready();
        scenario.old = existing(3, 0L, "OPEN");
        when(scenario.repo.registered(EXISTING_SHIFT)).thenReturn(2L);
        String actual = message(scenario, new Availability(List.of(
            new Slot(editableDate(), MORNING, 1, true, 0L)
        )));
        String expected = "Ca này đã có 2 người đăng ký. Số chỗ không được nhỏ hơn 2.";
        return new Observation("message=" + actual + ", publishWrites=" + scenario.publishCount,
            expected.equals(actual) && scenario.publishCount == 0);
    }

    private static Observation staleVersion() {
        Scenario scenario = ready();
        scenario.old = existing(3, 2L, "OPEN");
        String actual = message(scenario, new Availability(List.of(
            new Slot(editableDate(), MORNING, 3, true, 1L)
        )));
        return new Observation("message=" + actual,
            "Lịch ca đã thay đổi. Vui lòng tải lại và kiểm tra thông tin trước khi xác nhận.".equals(actual));
    }

    private static Observation lockedShift() {
        Scenario closed = ready();
        when(closed.repo.closedPeriod(BRANCH, editableDate())).thenReturn(true);
        String closedMessage = message(closed, availability(2, true, null));

        Scenario attended = ready();
        attended.old = existing(2, 0L, "OPEN");
        when(attended.repo.hasAttendance(EXISTING_SHIFT)).thenReturn(true);
        String attendedMessage = message(attended, new Availability(List.of(
            new Slot(editableDate(), MORNING, 2, false, 0L)
        )));
        String locked = "Ca thuộc kỳ công đã chốt hoặc đã có chấm công. Không thể thay đổi lịch này.";
        return new Observation("messages=" + List.of(closedMessage, attendedMessage),
            List.of(closedMessage, attendedMessage).equals(List.of(locked, locked)));
    }

    private static Observation authorization() {
        Scenario denied = ready();
        Identity receptionist = new Identity(UUID.randomUUID(), "letan", "RECEPTIONIST", "/reception", BRANCH, "Hoa Bằng");
        String deniedMessage = message(denied, receptionist, availability(2, true, null));
        Scenario suspended = ready();
        suspended.status = "SUSPENDED";
        String suspendedMessage = message(suspended, manager(), availability(2, true, null));
        String expectedDenied = "Bạn không có quyền thực hiện thao tác đối với lịch ca tại cơ sở này.";
        String expectedSuspended = "Cơ sở GenZ Cinema Hoa Bằng đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi.";
        return new Observation("messages=" + List.of(deniedMessage, suspendedMessage),
            List.of(deniedMessage, suspendedMessage).equals(List.of(expectedDenied, expectedSuspended)));
    }

    private static String message(Scenario scenario, Availability input) {
        return message(scenario, manager(), input);
    }

    private static String message(Scenario scenario, Identity actor, Availability input) {
        try {
            scenario.service.publish(actor, input);
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
        when(accounts.manages(MANAGER, BRANCH, false)).thenReturn(true);
        when(repo.branch(eq(BRANCH), anyBoolean())).thenAnswer(invocation -> branch(scenario.status));
        when(repo.templates()).thenReturn(templates());
        when(repo.slot(eq(BRANCH), any(), any())).thenAnswer(invocation -> scenario.old);
        when(repo.registered(any())).thenReturn(0L);
        when(repo.closedPeriod(eq(BRANCH), any())).thenReturn(false);
        when(repo.hasAttendance(any())).thenReturn(false);
        when(repo.shifts(eq(BRANCH), any())).thenReturn(List.of());
        when(repo.assignments(eq(BRANCH), any(), isNull())).thenReturn(List.of());
        when(repo.history(eq(BRANCH), any())).thenReturn(List.of());
        when(repo.employees(BRANCH)).thenReturn(List.of());
        when(repo.closedMonths(eq(BRANCH), any())).thenReturn(Set.of());
        when(repo.publish(any(), any(), any(), any(), any(), anyInt(), anyString(), any(), nullable(Map.class)))
            .thenAnswer(invocation -> {
                scenario.publishCount++;
                scenario.publishedTemplates.add(invocation.getArgument(1));
                scenario.lastCapacity = invocation.getArgument(5);
                return scenario.old == null ? UUID.randomUUID() : EXISTING_SHIFT;
            });
        return scenario;
    }

    private static Availability availability(int capacity, boolean open, Long version) {
        return new Availability(List.of(new Slot(editableDate(), MORNING, capacity, open, version)));
    }

    private static Map<String, Object> existing(int capacity, long version, String status) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", EXISTING_SHIFT);
        row.put("capacity", capacity);
        row.put("version", version);
        row.put("status", status);
        return row;
    }

    private static LocalDate currentMonday() {
        return LocalDate.now(ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static LocalDate editableDate() {
        return currentMonday().plusDays(6);
    }

    private static Identity manager() {
        return new Identity(MANAGER, "qlcs.hoabang", "BRANCH_MANAGER", "/branch", BRANCH, "GenZ Cinema Hoa Bằng");
    }

    private static Map<String, Object> branch(String status) {
        return Map.of(
            "id", BRANCH,
            "name", "GenZ Cinema Hoa Bằng",
            "status", status,
            "timezone", ZONE.getId()
        );
    }

    private static List<Map<String, Object>> templates() {
        return List.of(
            template(NIGHT, 0, 420),
            template(MORNING, 420, 720),
            template(AFTERNOON, 720, 1080),
            template(EVENING, 1080, 1440)
        );
    }

    private static Map<String, Object> template(UUID id, int start, int end) {
        return Map.of("id", id, "startMinute", start, "endMinute", end);
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
        Map<String, Object> old;
        int publishCount;
        int lastCapacity;
        final java.util.ArrayList<UUID> publishedTemplates = new java.util.ArrayList<>();

        Scenario(ShiftRepository repo, AccountRepository accounts, ShiftService service) {
            this.repo = repo;
            this.accounts = accounts;
            this.service = service;
        }
    }
}
