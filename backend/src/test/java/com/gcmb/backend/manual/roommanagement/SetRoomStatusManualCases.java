/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 29/10/2026
 * Description: Manual unit test cases for UC-33 Set Room Operating Status.
 */
package com.gcmb.backend.manual.roommanagement;

import static com.gcmb.backend.manual.roommanagement.RoomManagementManualRunner.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gcmb.backend.branch.dto.RoomStatusCommand;
import com.gcmb.backend.branch.dto.RoomView;
import com.gcmb.backend.branch.repository.BranchRoomRepository;
import com.gcmb.backend.manual.roommanagement.RoomManagementManualRunner.Fixture;
import com.gcmb.backend.manual.roommanagement.RoomManagementManualRunner.ManualTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

public final class SetRoomStatusManualCases {
    private static final String UC = "UC-33 Set Room Operating Status";
    private static final String NOTE = "Nguồn: UC-33_Set_Room_Operating_Status.docx; BR-05, BR-43, BR-111, BR-165; MSG02, MSG24. Phương pháp: White-box.";

    private SetRoomStatusManualCases() {
    }

    public static List<ManualTest> cases() {
        List<ManualTest> cases = new ArrayList<>();
        cases.add(test("RM-026", "status AVAILABLE, UNDER_MAINTENANCE và INACTIVE; reason hợp lệ",
                "Chấp nhận đúng ba trạng thái và chuyển đầy đủ command/actor/branch vào repository.", "Normal", () ->
                        combine(validStatus("AVAILABLE"), validStatus("UNDER_MAINTENANCE"), validStatus("INACTIVE")),
                "BranchRoomService.status"));
        cases.add(test("RM-027", "status(identity, roomId, null)",
                "Từ chối 400 bằng MSG02 và không gọi repository.", "Abnormal", () -> {
                    Fixture f = fixture();
                    TestResult result = expectStatus(() -> f.service().status(f.identity(), ROOM_ID, null), 400,
                            "Vui lòng bổ sung Trạng thái phòng.");
                    verify(f.repository(), never()).changeStatus(any(), any(), any(), any());
                    return result;
                }, "BranchRoomService.status"));
        cases.add(test("RM-028", "status=null, rỗng, UNKNOWN hoặc CLEANING",
                "Mỗi trạng thái bị từ chối 400 có kiểm soát; không gọi repository.", "Abnormal", () ->
                        combine(invalidStatus(null), invalidStatus(""), invalidStatus("UNKNOWN"), invalidStatus("CLEANING")),
                "BranchRoomService.status"));
        cases.add(test("RM-029", "reason=null, rỗng hoặc chỉ khoảng trắng",
                "Mỗi biến thể bị từ chối 400 bằng MSG02; không gọi repository.", "Abnormal", () ->
                        combine(invalidReason(null), invalidReason(""), invalidReason("   ")),
                "BranchRoomService.status"));
        cases.add(test("RM-030", "identity.branchId=null",
                "Từ chối 403 bằng MSG24 và không gọi repository.", "Abnormal", () -> {
                    Fixture f = fixture();
                    RoomStatusCommand command = status("INACTIVE", "Ngừng sử dụng", null);
                    TestResult result = expectStatus(
                            () -> f.service().status(identityWithoutBranch(), ROOM_ID, command), 403,
                            "Bạn không có quyền thay đổi trạng thái đối với phòng.");
                    verify(f.repository(), never()).changeStatus(any(), any(), any(), any());
                    return result;
                }, "BranchRoomService.status"));
        cases.add(test("RM-031", "repo.changeStatus trả null",
                "Từ chối 404 khi phòng không tồn tại trong branch được cấp.", "Abnormal", () -> {
                    Fixture f = fixture();
                    RoomStatusCommand command = status("INACTIVE", "Ngừng sử dụng", null);
                    when(f.repository().changeStatus(BRANCH_ID, ROOM_ID, ACTOR_ID, command)).thenReturn(null);
                    return expectStatus(() -> f.service().status(f.identity(), ROOM_ID, command), 404);
                }, "BranchRoomService.status"));
        cases.add(test("RM-032", "UNDER_MAINTENANCE có reason và expectedReadyAt",
                "Truyền nguyên trạng thái, lý do, thời gian dự kiến và actorId vào repository.", "Normal", () -> {
                    Fixture f = fixture();
                    OffsetDateTime ready = OffsetDateTime.of(2026, 10, 30, 9, 0, 0, 0, ZoneOffset.ofHours(7));
                    RoomStatusCommand command = status("UNDER_MAINTENANCE", "Sửa máy chiếu", ready);
                    RoomView expected = room("Phòng kiểm thử", "UNDER_MAINTENANCE");
                    when(f.repository().changeStatus(BRANCH_ID, ROOM_ID, ACTOR_ID, command)).thenReturn(expected);
                    RoomView actual = f.service().status(f.identity(), ROOM_ID, command);
                    verify(f.repository()).changeStatus(BRANCH_ID, ROOM_ID, ACTOR_ID, command);
                    return check(expected.equals(actual), "expectedReadyAt=" + ready);
                }, "BranchRoomService.status"));
        cases.add(test("RM-033", "BranchRoomRepository.changeStatus với phòng tồn tại",
                "Thực hiện một UPDATE room, một INSERT history và trả trạng thái sau cập nhật.", "Normal", () -> {
                    RoomView before = room("Phòng kiểm thử", "AVAILABLE");
                    RoomView after = room("Phòng kiểm thử", "UNDER_MAINTENANCE");
                    RecordingJdbc db = new RecordingJdbc(before, after);
                    BranchRoomRepository repository = new BranchRoomRepository(db);
                    OffsetDateTime ready = OffsetDateTime.of(2026, 10, 30, 9, 0, 0, 0, ZoneOffset.UTC);
                    RoomStatusCommand command = status("UNDER_MAINTENANCE", " Sửa máy chiếu ", ready);
                    RoomView actual = repository.changeStatus(BRANCH_ID, ROOM_ID, ACTOR_ID, command);
                    boolean updateRoom = db.sql().size() == 2 && db.sql().get(0).startsWith("UPDATE gcmb.room");
                    boolean insertHistory = db.sql().size() == 2
                            && db.sql().get(1).startsWith("INSERT INTO gcmb.room_operating_status_history");
                    Object[] history = db.arguments().size() < 2 ? new Object[0] : db.arguments().get(1);
                    boolean values = history.length == 7
                            && Objects.equals(history[0], ROOM_ID)
                            && Objects.equals(history[1], BRANCH_ID)
                            && Objects.equals(history[2], "AVAILABLE")
                            && Objects.equals(history[3], "UNDER_MAINTENANCE")
                            && Objects.equals(history[4], "Sửa máy chiếu")
                            && Objects.equals(history[6], ACTOR_ID);
                    return check(after.equals(actual) && updateRoom && insertHistory && values,
                            "sql=" + db.sql() + ", historyArgs=" + Arrays.toString(history));
                }, "BranchRoomRepository.changeStatus"));
        return List.copyOf(cases);
    }

    private static ManualTest test(String id, String input, String expected, String type,
            CheckedAction action, String source) {
        return new ManualTest(id, UC, source, precondition(id), input, expected, type, NOTE, action);
    }

    private static String precondition(String id) {
        String validScope = "Branch Manager qlcs.hoabang đã đăng nhập, đang ACTIVE và được phân công quản lý cơ sở Hoa Bằng. ";
        String roomExists = "Phòng cần đổi trạng thái tồn tại trong chính cơ sở Hoa Bằng. ";
        String serviceUnit = "BranchRoomService thật; BranchRoomRepository mock; không kết nối database ngoài.";
        return switch (id) {
            case "RM-026" -> validScope + roomExists
                    + "Mỗi biến thể có lý do thay đổi hợp lệ. " + serviceUnit;
            case "RM-027" -> validScope + roomExists
                    + "Request không cung cấp RoomStatusCommand. " + serviceUnit;
            case "RM-028" -> validScope + roomExists
                    + "Reason hợp lệ; chỉ status thay đổi theo từng biến thể. " + serviceUnit;
            case "RM-029" -> validScope + roomExists
                    + "Status INACTIVE hợp lệ; reason thiếu hoặc chỉ chứa khoảng trắng. " + serviceUnit;
            case "RM-030" -> "Branch Manager qlcs.hoabang đã đăng nhập và đang ACTIVE nhưng tài khoản chưa được gán cơ sở. "
                    + "Không truy cập phòng trước khi xác định branch scope. " + serviceUnit;
            case "RM-031" -> validScope
                    + "Không có phòng mang roomId trong phạm vi cơ sở Hoa Bằng. " + serviceUnit;
            case "RM-032" -> validScope + roomExists
                    + "Phòng có thể được chuyển sang UNDER_MAINTENANCE; reason và expectedReadyAt hợp lệ. " + serviceUnit;
            case "RM-033" -> validScope
                    + "Phòng tồn tại trong cơ sở, trạng thái ban đầu AVAILABLE; actorId hợp lệ. "
                    + "BranchRoomRepository thật; RecordingJdbc trả snapshot trước/sau và không kết nối database ngoài.";
            default -> throw new IllegalArgumentException("Unknown test case: " + id);
        };
    }

    private static RoomStatusCommand status(String status, String reason, OffsetDateTime ready) {
        return new RoomStatusCommand(status, reason, ready);
    }

    private static TestResult validStatus(String value) {
        Fixture f = fixture();
        RoomStatusCommand command = status(value, "Kiểm tra phòng", null);
        RoomView expected = room("Phòng kiểm thử", value);
        when(f.repository().changeStatus(BRANCH_ID, ROOM_ID, ACTOR_ID, command)).thenReturn(expected);
        RoomView actual = f.service().status(f.identity(), ROOM_ID, command);
        verify(f.repository()).changeStatus(BRANCH_ID, ROOM_ID, ACTOR_ID, command);
        return check(expected.equals(actual), "acceptedStatus=" + value);
    }

    private static TestResult invalidStatus(String value) {
        Fixture f = fixture();
        RoomStatusCommand command = status(value, "Kiểm tra phòng", null);
        TestResult result = expectStatus(() -> f.service().status(f.identity(), ROOM_ID, command), 400);
        verify(f.repository(), never()).changeStatus(any(), any(), any(), any());
        return result;
    }

    private static TestResult invalidReason(String value) {
        Fixture f = fixture();
        RoomStatusCommand command = status("INACTIVE", value, null);
        TestResult result = expectStatus(() -> f.service().status(f.identity(), ROOM_ID, command), 400,
                "Vui lòng bổ sung Lý do thay đổi.");
        verify(f.repository(), never()).changeStatus(any(), any(), any(), any());
        return result;
    }

    private static final class RecordingJdbc extends JdbcTemplate {
        private final RoomView before;
        private final RoomView after;
        private final List<String> sql = new ArrayList<>();
        private final List<Object[]> arguments = new ArrayList<>();
        private int queryCount;

        private RecordingJdbc(RoomView before, RoomView after) {
            this.before = before;
            this.after = after;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String query, RowMapper<T> rowMapper, Object... args) {
            queryCount++;
            return (List<T>) List.of(queryCount == 1 ? before : after);
        }

        @Override
        public int update(String query, Object... args) {
            sql.add(query);
            arguments.add(args);
            return 1;
        }

        private List<String> sql() {
            return sql;
        }

        private List<Object[]> arguments() {
            return arguments;
        }
    }
}
