/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 13/11/2026
 * Description: Manual unit test cases cho UC-87 Record Daily Attendance.
 */
package com.gcmb.backend.manual.shiftmanagement;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gcmb.backend.accounts.repository.AccountRepository;
import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.exception.AuthFailure;
import com.gcmb.backend.manual.shiftmanagement.ShiftManagementManualRunner.ManualCase;
import com.gcmb.backend.manual.shiftmanagement.ShiftManagementManualRunner.Observation;
import com.gcmb.backend.workforce.dto.AttendanceDtos.RecordCommand;
import com.gcmb.backend.workforce.repository.AttendanceRepository;
import com.gcmb.backend.workforce.repository.ShiftRepository;
import com.gcmb.backend.workforce.service.AttendanceService;
import com.gcmb.backend.workforce.service.ShiftService;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class RecordDailyAttendanceManualCases {
    private static final UUID BRANCH = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID MANAGER = UUID.fromString("47570000-0000-4000-8000-000000000003");
    private static final UUID ASSIGNMENT = UUID.fromString("87000000-0000-4000-8000-000000000001");
    private static final UUID PERIOD = UUID.fromString("87000000-0000-4000-8000-000000000002");
    private static final UUID ATTENDANCE = UUID.fromString("87000000-0000-4000-8000-000000000003");
    private static final UUID ISSUE = UUID.fromString("87000000-0000-4000-8000-000000000004");
    private static final String TITLE = "UC-87 Record Daily Attendance";
    private static final String SOURCE = "UC-87; BR-59, BR-61, BR-63, BR-111, BR-193, BR-194, BR-204, BR-177; MSG02, MSG03, MSG24, MSG43, MSG86; V14, V29; AttendanceService.record";
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private RecordDailyAttendanceManualCases() {
    }

    static List<ManualCase> cases() {
        return List.of(
            test("SHM-040",
                "Branch Manager đã đăng nhập; cơ sở hoạt động; phân công tồn tại; ca đã bắt đầu; tháng công OPEN.",
                "Chấm trạng thái WORKED.",
                "Lưu WORKED, giờ vào/ra bằng toàn bộ thời gian ca và workedMinutes bằng thời lượng ca.",
                RecordDailyAttendanceManualCases::workedShift),
            test("SHM-041",
                "Ca 18:00–00:00 ngày kế tiếp đã bắt đầu.",
                "Chấm WORKED.",
                "Giờ ra thuộc ngày kế tiếp; thời lượng được tính bằng sáu giờ.",
                RecordDailyAttendanceManualCases::overnightShift),
            test("SHM-042",
                "Phân công hợp lệ; tháng công OPEN.",
                "Chấm ABSENT và ghi chú.",
                "Lưu ABSENT, workedMinutes=0, không lưu giờ vào/ra; ghi chú được giữ.",
                RecordDailyAttendanceManualCases::absent),
            test("SHM-043",
                "Attendance hợp lệ và tháng OPEN.",
                "Ghi chú có khoảng trắng ở đầu và cuối.",
                "Ghi chú được trim trước khi lưu.",
                RecordDailyAttendanceManualCases::trimNote),
            test("SHM-044",
                "Các trường khác hợp lệ.",
                "Ghi chú lần lượt đúng 2.000 và 2.001 ký tự.",
                "2.000 ký tự được chấp nhận; 2.001 ký tự hiển thị đúng thông báo giới hạn.",
                RecordDailyAttendanceManualCases::noteBoundary),
            test("SHM-045",
                "Phân công hợp lệ; tháng OPEN.",
                "Status null, rỗng hoặc khác WORKED/ABSENT.",
                "Hiển thị “Vui lòng bổ sung tình trạng chấm công.”; không lưu attendance.",
                RecordDailyAttendanceManualCases::invalidStatus),
            test("SHM-046",
                "Một ca chưa bắt đầu và một phân công thuộc ngày tương lai.",
                "Chấm attendance.",
                "Hiển thị đúng thông báo chưa đến giờ hoặc chưa thể chấm ngày tương lai.",
                RecordDailyAttendanceManualCases::invalidTiming),
            test("SHM-047",
                "Tháng của phân công đã CLOSED.",
                "Ghi hoặc sửa attendance.",
                "Hiển thị thông báo công tháng đã chốt; attendance cũ được giữ nguyên.",
                RecordDailyAttendanceManualCases::closedMonth),
            test("SHM-048",
                "Attendance được mở ở version cũ và vừa được người khác sửa.",
                "Lưu lại version cũ.",
                "Hiển thị thông báo bảng công đã thay đổi; không ghi đè.",
                RecordDailyAttendanceManualCases::staleVersion),
            test("SHM-049",
                "Phân công có phản ánh OPEN thuộc đúng attendance.",
                "Chấm attendance, chọn phản ánh và nhập kết quả xử lý hợp lệ.",
                "Attendance được lưu; phản ánh được xử lý và liên kết đúng attendance.",
                RecordDailyAttendanceManualCases::resolveIssue),
            test("SHM-050",
                "Phân công có phản ánh OPEN.",
                "Bỏ trống kết quả xử lý hoặc gửi ID phản ánh không thuộc phân công.",
                "Hiển thị đúng thông báo yêu cầu hoặc dữ liệu đã thay đổi; không xử lý sai phản ánh.",
                RecordDailyAttendanceManualCases::invalidIssueResolution),
            test("SHM-051",
                "Assignment không tồn tại hoặc thuộc cơ sở khác.",
                "Ghi attendance cho assignment đó.",
                "Hiển thị “Không tìm thấy phân ca tại cơ sở đang quản lý.”; không ghi attendance.",
                RecordDailyAttendanceManualCases::missingAssignment),
            test("SHM-052",
                "Lần lượt kiểm tra Receptionist, Accountant và Branch Manager tại cơ sở SUSPENDED.",
                "Ghi attendance hợp lệ.",
                "Người sai vai trò bị từ chối; cơ sở tạm ngừng chỉ được xem dữ liệu.",
                RecordDailyAttendanceManualCases::authorization)
        );
    }

    private static Observation workedShift() {
        Scenario scenario = ready();
        Instant start = Instant.now().minusSeconds(3600);
        Instant end = start.plusSeconds(5 * 3600);
        scenario.row = assignmentRow(LocalDate.now(ZONE), start, end, 0L);
        scenario.service.record(manager(), ASSIGNMENT, command("WORKED", "", 0L, List.of(), ""));
        boolean passed = "WORKED".equals(scenario.savedStatus)
            && start.equals(scenario.savedIn)
            && end.equals(scenario.savedOut)
            && scenario.savedMinutes == 300
            && scenario.saveCount == 1;
        return new Observation("status=" + scenario.savedStatus + ", actualIn=" + scenario.savedIn
            + ", actualOut=" + scenario.savedOut + ", workedMinutes=" + scenario.savedMinutes, passed);
    }

    private static Observation overnightShift() {
        Scenario scenario = ready();
        LocalDate date = LocalDate.now(ZONE).minusDays(1);
        Instant start = date.atTime(18, 0).atZone(ZONE).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(ZONE).toInstant();
        scenario.row = assignmentRow(date, start, end, 0L);
        scenario.service.record(manager(), ASSIGNMENT, command("WORKED", "", 0L, List.of(), ""));
        return new Observation("actualIn=" + scenario.savedIn + ", actualOut=" + scenario.savedOut
            + ", workedMinutes=" + scenario.savedMinutes,
            end.equals(scenario.savedOut) && scenario.savedMinutes == 360);
    }

    private static Observation absent() {
        Scenario scenario = ready();
        scenario.service.record(manager(), ASSIGNMENT, command("ABSENT", "Nghỉ phép", 0L, List.of(), ""));
        boolean passed = "ABSENT".equals(scenario.savedStatus) && scenario.savedIn == null
            && scenario.savedOut == null && scenario.savedMinutes == 0
            && "Nghỉ phép".equals(scenario.savedNote);
        return new Observation("status=" + scenario.savedStatus + ", actualIn=" + scenario.savedIn
            + ", actualOut=" + scenario.savedOut + ", workedMinutes=" + scenario.savedMinutes
            + ", note=" + scenario.savedNote, passed);
    }

    private static Observation trimNote() {
        Scenario scenario = ready();
        scenario.service.record(manager(), ASSIGNMENT, command("ABSENT", "  Có phép  ", 0L, List.of(), ""));
        return new Observation("savedNote=“" + scenario.savedNote + "”", "Có phép".equals(scenario.savedNote));
    }

    private static Observation noteBoundary() {
        Scenario accepted = ready();
        accepted.service.record(manager(), ASSIGNMENT, command("ABSENT", "A".repeat(2000), 0L, List.of(), ""));
        Scenario rejected = ready();
        String actual = recordMessage(rejected, command("ABSENT", "A".repeat(2001), 0L, List.of(), ""));
        return new Observation("acceptedLength=" + accepted.savedNote.length() + ", rejectedMessage=" + actual,
            accepted.savedNote.length() == 2000
                && "Ghi chú chỉ được chứa tối đa 2000 ký tự.".equals(actual)
                && rejected.saveCount == 0);
    }

    private static Observation invalidStatus() {
        List<String> actual = List.of(
            recordMessage(ready(), command(null, "", 0L, List.of(), "")),
            recordMessage(ready(), command("", "", 0L, List.of(), "")),
            recordMessage(ready(), command("LATE", "", 0L, List.of(), ""))
        );
        String expected = "Vui lòng bổ sung tình trạng chấm công.";
        return new Observation("messages=" + actual, actual.equals(List.of(expected, expected, expected)));
    }

    private static Observation invalidTiming() {
        Scenario notStarted = ready();
        Instant future = Instant.now().plusSeconds(3600);
        notStarted.row = assignmentRow(LocalDate.now(ZONE), future, future.plusSeconds(3600), 0L);
        String first = recordMessage(notStarted, command("WORKED", "", 0L, List.of(), ""));

        Scenario futureDate = ready();
        futureDate.row = assignmentRow(LocalDate.now(ZONE).plusDays(1),
            Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600), 0L);
        String second = recordMessage(futureDate, command("WORKED", "", 0L, List.of(), ""));
        return new Observation("messages=" + List.of(first, second), List.of(first, second).equals(List.of(
            "Chưa đến giờ bắt đầu ca, chưa thể chấm công.",
            "Chưa thể chấm công cho ngày làm việc trong tương lai."
        )));
    }

    private static Observation closedMonth() {
        Scenario scenario = ready();
        scenario.periodStatus = "CLOSED";
        LocalDate date = LocalDate.now(ZONE);
        scenario.row = assignmentRow(date, Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600), 0L);
        String actual = recordMessage(scenario, command("WORKED", "", 0L, List.of(), ""));
        String expected = "Công tháng %02d/%04d đã được chốt nên không thể sửa trực tiếp bản ghi chấm công này."
            .formatted(date.getMonthValue(), date.getYear());
        return new Observation("message=" + actual, expected.equals(actual) && scenario.saveCount == 0);
    }

    private static Observation staleVersion() {
        Scenario scenario = ready();
        String actual = recordMessage(scenario, command("WORKED", "", 99L, List.of(), ""));
        return new Observation("message=" + actual,
            "Bảng công đã thay đổi. Vui lòng tải lại và kiểm tra trước khi xác nhận.".equals(actual));
    }

    private static Observation resolveIssue() {
        Scenario scenario = ready();
        scenario.issues = List.of(issue(ISSUE));
        scenario.service.record(manager(), ASSIGNMENT,
            command("WORKED", "", 0L, List.of(ISSUE), "Đã đối chiếu"));
        return new Observation("saveWrites=" + scenario.saveCount + ", resolveWrites="
            + scenario.resolveCount + ", resolution=" + scenario.savedResolution,
            scenario.saveCount == 1 && scenario.resolveCount == 1
                && "Đã đối chiếu".equals(scenario.savedResolution));
    }

    private static Observation invalidIssueResolution() {
        Scenario missing = ready();
        missing.issues = List.of(issue(ISSUE));
        String first = recordMessage(missing,
            command("WORKED", "", 0L, List.of(ISSUE), "   "));

        Scenario foreign = ready();
        foreign.issues = List.of(issue(ISSUE));
        String second = recordMessage(foreign,
            command("WORKED", "", 0L, List.of(UUID.randomUUID()), "Đã xử lý"));
        return new Observation("messages=" + List.of(first, second), List.of(first, second).equals(List.of(
            "Vui lòng bổ sung kết quả xử lý phản ánh.",
            "Bảng công đã thay đổi. Vui lòng tải lại và kiểm tra trước khi xác nhận."
        )));
    }

    private static Observation missingAssignment() {
        Scenario scenario = ready();
        scenario.row = null;
        String actual = recordMessage(scenario, command("WORKED", "", 0L, List.of(), ""));
        return new Observation("message=" + actual,
            "Không tìm thấy phân ca tại cơ sở đang quản lý.".equals(actual) && scenario.saveCount == 0);
    }

    private static Observation authorization() {
        Scenario denied = ready();
        Identity receptionist = new Identity(UUID.randomUUID(), "letan", "RECEPTIONIST", "/reception", BRANCH, "Hoa Bằng");
        String deniedMessage = recordMessage(denied, receptionist, command("WORKED", "", 0L, List.of(), ""));
        Scenario suspended = ready();
        suspended.branchStatus = "SUSPENDED";
        String suspendedMessage = recordMessage(suspended, manager(), command("WORKED", "", 0L, List.of(), ""));
        String deniedExpected = "Bạn không có quyền thực hiện thao tác đối với lịch ca tại cơ sở này.";
        String suspendedExpected = "Cơ sở GenZ Cinema Hoa Bằng đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi.";
        return new Observation("messages=" + List.of(deniedMessage, suspendedMessage),
            List.of(deniedMessage, suspendedMessage).equals(List.of(deniedExpected, suspendedExpected)));
    }

    private static String recordMessage(Scenario scenario, RecordCommand input) {
        return recordMessage(scenario, manager(), input);
    }

    private static String recordMessage(Scenario scenario, Identity actor, RecordCommand input) {
        try {
            scenario.service.record(actor, ASSIGNMENT, input);
            return "Không phát sinh lỗi";
        } catch (AuthFailure failure) {
            return failure.getMessage();
        }
    }

    private static RecordCommand command(String status, String note, Long version,
                                         List<UUID> issueIds, String resolution) {
        return new RecordCommand(status, null, null, note, version, issueIds, resolution);
    }

    private static Scenario ready() {
        AttendanceRepository attendance = mock(AttendanceRepository.class);
        ShiftRepository shifts = mock(ShiftRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        ShiftService shiftService = new ShiftService(shifts, accounts);
        Scenario scenario = new Scenario(attendance, shifts, accounts,
            new AttendanceService(attendance, shifts, shiftService));
        Instant start = Instant.now().minusSeconds(3600);
        scenario.row = assignmentRow(LocalDate.now(ZONE), start, start.plusSeconds(5 * 3600), 0L);

        when(shifts.actor(any(), anyString(), eq(BRANCH))).thenReturn(true);
        when(accounts.manages(MANAGER, BRANCH, false)).thenReturn(true);
        when(shifts.branch(eq(BRANCH), anyBoolean())).thenAnswer(invocation -> branch(scenario.branchStatus));
        when(shifts.templates()).thenReturn(List.of());
        when(attendance.row(BRANCH, ASSIGNMENT)).thenAnswer(invocation -> scenario.row);
        when(attendance.ensurePeriod(eq(BRANCH), any())).thenAnswer(invocation -> period(scenario.periodStatus));
        when(attendance.period(eq(BRANCH), any(), anyBoolean())).thenAnswer(invocation -> period(scenario.periodStatus));
        when(attendance.rows(eq(BRANCH), any(), any())).thenReturn(List.of());
        when(attendance.issues(eq(BRANCH), any(), any())).thenAnswer(invocation -> scenario.issues);
        when(attendance.save(eq(ASSIGNMENT), eq(PERIOD), anyString(), nullable(Instant.class),
            nullable(Instant.class), anyInt(), anyString(), eq(MANAGER))).thenAnswer(invocation -> {
                scenario.saveCount++;
                scenario.savedStatus = invocation.getArgument(2);
                scenario.savedIn = invocation.getArgument(3);
                scenario.savedOut = invocation.getArgument(4);
                scenario.savedMinutes = invocation.getArgument(5);
                scenario.savedNote = invocation.getArgument(6);
                return ATTENDANCE;
            });
        when(attendance.resolve(any(), eq(ATTENDANCE), eq(MANAGER), anyString())).thenAnswer(invocation -> {
            scenario.resolveCount++;
            scenario.savedResolution = invocation.getArgument(3);
            return true;
        });
        doAnswer(invocation -> {
            scenario.changedCount++;
            return null;
        }).when(attendance).changed(PERIOD);
        doAnswer(invocation -> {
            scenario.auditCount++;
            return null;
        }).when(attendance).audit(any(), any(), any(), anyString(), any(), any());
        return scenario;
    }

    private static Map<String, Object> assignmentRow(LocalDate date, Instant start, Instant end, long version) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("assignmentId", ASSIGNMENT);
        row.put("workDate", date.toString());
        row.put("startsAt", Timestamp.from(start));
        row.put("endsAt", Timestamp.from(end));
        row.put("version", version);
        return row;
    }

    private static Map<String, Object> issue(UUID id) {
        return Map.of("id", id, "assignmentId", ASSIGNMENT, "status", "OPEN");
    }

    private static Map<String, Object> period(String status) {
        return Map.of("id", PERIOD, "status", status, "version", 0L);
    }

    private static Map<String, Object> branch(String status) {
        return Map.of("name", "GenZ Cinema Hoa Bằng", "status", status, "timezone", ZONE.getId());
    }

    private static Identity manager() {
        return new Identity(MANAGER, "qlcs.hoabang", "BRANCH_MANAGER", "/branch", BRANCH, "GenZ Cinema Hoa Bằng");
    }

    private static ManualCase test(String id, String precondition, String input, String expected,
                                   ShiftManagementManualRunner.Execution execution) {
        return new ManualCase(id, TITLE, SOURCE, precondition, input, expected, execution);
    }

    private static final class Scenario {
        final AttendanceRepository attendance;
        final ShiftRepository shifts;
        final AccountRepository accounts;
        final AttendanceService service;
        String branchStatus = "ACTIVE";
        String periodStatus = "OPEN";
        Map<String, Object> row;
        List<Map<String, Object>> issues = List.of();
        int saveCount;
        int resolveCount;
        int changedCount;
        int auditCount;
        String savedStatus;
        Instant savedIn;
        Instant savedOut;
        int savedMinutes;
        String savedNote;
        String savedResolution;

        Scenario(AttendanceRepository attendance, ShiftRepository shifts,
                 AccountRepository accounts, AttendanceService service) {
            this.attendance = attendance;
            this.shifts = shifts;
            this.accounts = accounts;
            this.service = service;
        }
    }
}
