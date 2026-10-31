/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 29/10/2026
 * Description: Manual unit test cases for UC-29 Update Room.
 */
package com.gcmb.backend.manual.roommanagement;

import static com.gcmb.backend.manual.roommanagement.RoomManagementManualRunner.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gcmb.backend.branch.dto.RoomCommand;
import com.gcmb.backend.branch.dto.RoomView;
import com.gcmb.backend.manual.roommanagement.RoomManagementManualRunner.Fixture;
import com.gcmb.backend.manual.roommanagement.RoomManagementManualRunner.ManualTest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;

public final class UpdateRoomManualCases {
    private static final String UC = "UC-29 Update Room";
    private static final String NOTE = "Nguồn: UC-29_Update_Room.docx; BR-111, BR-139, BR-173, BR-179; MSG02, MSG03, MSG24, MSG38, MSG44; Flyway V14. Phương pháp: White-box.";

    private UpdateRoomManualCases() {
    }

    public static List<ManualTest> cases() {
        List<ManualTest> cases = new ArrayList<>();
        cases.add(test("RM-011", "update(identity, roomId, validCommand)",
                "Trả RoomView và truyền đúng branch, roomId, command.", "Normal", () -> {
                    Fixture f = fixture();
                    RoomCommand command = validCommand();
                    RoomView expected = room("Phòng kiểm thử", "AVAILABLE");
                    when(f.repository().update(BRANCH_ID, ROOM_ID, command)).thenReturn(expected);
                    RoomView actual = f.service().update(f.identity(), ROOM_ID, command);
                    verify(f.repository()).update(BRANCH_ID, ROOM_ID, command);
                    return check(expected.equals(actual), "returned=" + actual);
                }));
        cases.add(test("RM-012", "update(identity, roomId, null)",
                "Từ chối 400 bằng MSG02 và không gọi repository.", "Abnormal", () -> {
                    Fixture f = fixture();
                    TestResult result = expectStatus(() -> f.service().update(f.identity(), ROOM_ID, null), 400,
                            "Vui lòng bổ sung Thông tin phòng.");
                    verify(f.repository(), never()).update(any(), any(), any());
                    return result;
                }));
        cases.add(test("RM-013", "code/name blank; roomTypeId/floorNumber null",
                "Mỗi biến thể bị từ chối 400 bằng MSG02; không gọi repository.", "Abnormal", () ->
                        combine(invalidRequired(command(" ", "Phòng", 1, ROOM_TYPE_ID, 1, null), "Mã phòng"),
                                invalidRequired(command("RM-UT-01", " ", 1, ROOM_TYPE_ID, 1, null), "Tên phòng"),
                                invalidRequired(command("RM-UT-01", "Phòng", 1, null, 1, null), "Loại phòng"),
                                invalidRequired(command("RM-UT-01", "Phòng", null, ROOM_TYPE_ID, 1, null), "Tầng"))));
        cases.add(test("RM-014", "capacity=0 và capacity=1",
                "Từ chối 0 bằng MSG44; chấp nhận biên 1.", "Boundary", () ->
                        combine(capacity(0, false), capacity(1, true))));
        cases.add(test("RM-015", "name dài 200 và 201 ký tự",
                "Chấp nhận 200; từ chối 201 bằng MSG03.", "Boundary", () ->
                        combine(nameLength(200, true), nameLength(201, false))));
        cases.add(test("RM-016", "repo.update trả null trong branch hiện tại",
                "Từ chối 404, không trả dữ liệu branch khác.", "Abnormal", () -> {
                    Fixture f = fixture();
                    when(f.repository().update(BRANCH_ID, ROOM_ID, validCommand())).thenReturn(null);
                    return expectStatus(() -> f.service().update(f.identity(), ROOM_ID, validCommand()), 404);
                }));
        cases.add(test("RM-017", "repo.update báo trùng code trong cơ sở",
                "Từ chối 409 và hiển thị đúng MSG38.", "Abnormal", () -> {
                    Fixture f = fixture();
                    RoomCommand command = validCommand();
                    when(f.repository().update(BRANCH_ID, ROOM_ID, command))
                            .thenThrow(new DataIntegrityViolationException("duplicate"));
                    return expectStatus(() -> f.service().update(f.identity(), ROOM_ID, command), 409,
                            "Mã phòng “RM-UT-01” đã tồn tại trong cơ sở Hoa Bằng. Vui lòng nhập mã khác.");
                }));
        cases.add(test("RM-018", "identity.branchId=null",
                "Từ chối 403 bằng MSG24 và không gọi repository.", "Abnormal", () -> {
                    Fixture f = fixture();
                    TestResult result = expectStatus(
                            () -> f.service().update(identityWithoutBranch(), ROOM_ID, validCommand()), 403,
                            "Bạn không có quyền cập nhật đối với phòng.");
                    verify(f.repository(), never()).update(any(), any(), any());
                    return result;
                }));
        return List.copyOf(cases);
    }

    private static ManualTest test(String id, String input, String expected, String type, CheckedAction action) {
        return new ManualTest(id, UC, "BranchRoomService.update", precondition(id), input, expected, type, NOTE, action);
    }

    private static String precondition(String id) {
        String validScope = "Branch Manager qlcs.hoabang đã đăng nhập, đang ACTIVE và được phân công quản lý cơ sở Hoa Bằng. ";
        String roomExists = "Phòng cần cập nhật tồn tại trong cơ sở Hoa Bằng. ";
        String activeType = "Loại phòng RT-QN tồn tại và đang hoạt động theo Flyway V14. ";
        String unit = "BranchRoomService thật; BranchRoomRepository mock; không ghi database.";
        return switch (id) {
            case "RM-011" -> validScope + roomExists + activeType
                    + "Mã cập nhật không trùng phòng khác trong cơ sở. " + unit;
            case "RM-012" -> validScope + roomExists + activeType
                    + "Request không cung cấp RoomCommand. " + unit;
            case "RM-013", "RM-014", "RM-015" -> validScope + roomExists + activeType
                    + "Các trường không được nêu trong Input đều hợp lệ. " + unit;
            case "RM-016" -> validScope
                    + "Không có phòng mang roomId trong phạm vi cơ sở Hoa Bằng. " + unit;
            case "RM-017" -> validScope + roomExists + activeType
                    + "Một phòng khác cùng cơ sở đã sử dụng mã RM-UT-01. " + unit;
            case "RM-018" -> "Branch Manager qlcs.hoabang đã đăng nhập và đang ACTIVE nhưng tài khoản chưa được gán cơ sở. "
                    + "Phòng không được truy cập trước khi xác định branch scope. " + unit;
            default -> throw new IllegalArgumentException("Unknown test case: " + id);
        };
    }

    private static TestResult invalidRequired(RoomCommand command, String field) {
        Fixture f = fixture();
        TestResult result = expectStatus(() -> f.service().update(f.identity(), ROOM_ID, command), 400,
                "Vui lòng bổ sung " + field + ".");
        verify(f.repository(), never()).update(any(), any(), any());
        return result;
    }

    private static TestResult capacity(int value, boolean valid) {
        Fixture f = fixture();
        RoomCommand command = command("RM-UT-01", "Phòng", 1, ROOM_TYPE_ID, value, null);
        if (!valid) {
            return expectStatus(() -> f.service().update(f.identity(), ROOM_ID, command), 400,
                    "Sức chứa phải là số nguyên lớn hơn 0.");
        }
        when(f.repository().update(BRANCH_ID, ROOM_ID, command)).thenReturn(room("Phòng", "AVAILABLE"));
        f.service().update(f.identity(), ROOM_ID, command);
        return check(true, "capacity=1 accepted");
    }

    private static TestResult nameLength(int length, boolean valid) {
        Fixture f = fixture();
        RoomCommand command = command("RM-UT-01", "N".repeat(length), 1, ROOM_TYPE_ID, 1, null);
        if (!valid) {
            return expectStatus(() -> f.service().update(f.identity(), ROOM_ID, command), 400,
                    "Tên phòng chỉ được chứa tối đa 200 ký tự.");
        }
        when(f.repository().update(BRANCH_ID, ROOM_ID, command)).thenReturn(room("N".repeat(length), "AVAILABLE"));
        f.service().update(f.identity(), ROOM_ID, command);
        return check(true, "acceptedLength=" + length);
    }
}
