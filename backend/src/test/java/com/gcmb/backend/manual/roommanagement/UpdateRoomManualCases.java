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
    private UpdateRoomManualCases() {
    }

    public static List<ManualTest> cases() {
        List<ManualTest> cases = new ArrayList<>();
        cases.add(test("RM-011", "Cập nhật phòng RM-UT-01 bằng thông tin hợp lệ",
                "Trả RoomView và truyền đúng branch, roomId, command.", "Normal", () -> {
                    Fixture f = fixture();
                    RoomCommand command = validCommand();
                    RoomView expected = room("Phòng kiểm thử", "AVAILABLE");
                    when(f.repository().update(BRANCH_ID, ROOM_ID, command)).thenReturn(expected);
                    RoomView actual = f.service().update(f.identity(), ROOM_ID, command);
                    verify(f.repository()).update(BRANCH_ID, ROOM_ID, command);
                    return check(expected.equals(actual), "returned=" + actual);
                }));
        cases.add(test("RM-012", "Xác nhận cập nhật nhưng không cung cấp thông tin phòng",
                "Từ chối với thông báo “Vui lòng bổ sung Thông tin phòng.”; không gọi repository.", "Abnormal", () -> {
                    Fixture f = fixture();
                    TestResult result = expectStatus(() -> f.service().update(f.identity(), ROOM_ID, null), 400,
                            "Vui lòng bổ sung Thông tin phòng.");
                    verify(f.repository(), never()).update(any(), any(), any());
                    return result;
                }));
        cases.add(test("RM-013", "Lần lượt để trống mã, tên, loại phòng và tầng",
                "Lần lượt hiển thị “Vui lòng bổ sung Mã phòng.”, “Vui lòng bổ sung Tên phòng.”, “Vui lòng bổ sung Loại phòng.” và “Vui lòng bổ sung Tầng.”; không gọi repository.", "Abnormal", () ->
                        combine(invalidRequired(command(" ", "Phòng", 1, ROOM_TYPE_ID, 1, null), "Mã phòng"),
                                invalidRequired(command("RM-UT-01", " ", 1, ROOM_TYPE_ID, 1, null), "Tên phòng"),
                                invalidRequired(command("RM-UT-01", "Phòng", 1, null, 1, null), "Loại phòng"),
                                invalidRequired(command("RM-UT-01", "Phòng", null, ROOM_TYPE_ID, 1, null), "Tầng"))));
        cases.add(test("RM-014", "V01 sức chứa = 0; V02 sức chứa = 1",
                "V01 hiển thị “Sức chứa phải là số nguyên lớn hơn 0.”; V02 cập nhật thành công.", "Boundary", () ->
                        combine(capacity(0, false), capacity(1, true))));
        cases.add(test("RM-015", "V01 tên phòng dài 200; V02 dài 201 ký tự",
                "V01 được chấp nhận; V02 hiển thị “Tên phòng chỉ được chứa tối đa 200 ký tự.”.", "Boundary", () ->
                        combine(nameLength(200, true), nameLength(201, false))));
        cases.add(test("RM-016", "Cập nhật roomId không tồn tại trong cơ sở Hoa Bằng",
                "Từ chối với thông báo “Không tìm thấy phòng”; không trả dữ liệu cơ sở khác.", "Abnormal", () -> {
                    Fixture f = fixture();
                    when(f.repository().update(BRANCH_ID, ROOM_ID, validCommand())).thenReturn(null);
                    return expectStatus(() -> f.service().update(f.identity(), ROOM_ID, validCommand()), 404);
                }));
        cases.add(test("RM-017", "Mã RM-UT-01 trùng do một giao dịch đồng thời vừa ghi trước khi cập nhật",
                "Từ chối với thông báo “Mã phòng “RM-UT-01” đã tồn tại trong cơ sở Hoa Bằng. Vui lòng nhập mã khác.”.", "Abnormal", () -> {
                    Fixture f = fixture();
                    RoomCommand command = validCommand();
                    when(f.repository().update(BRANCH_ID, ROOM_ID, command))
                            .thenThrow(new DataIntegrityViolationException("duplicate"));
                    return expectStatus(() -> f.service().update(f.identity(), ROOM_ID, command), 409,
                            "Mã phòng “RM-UT-01” đã tồn tại trong cơ sở Hoa Bằng. Vui lòng nhập mã khác.");
                }));
        cases.add(test("RM-018", "Tài khoản Branch Manager chưa được gán cơ sở",
                "Từ chối với thông báo “Bạn không có quyền cập nhật đối với phòng.”; không gọi repository.", "Abnormal", () -> {
                    Fixture f = fixture();
                    TestResult result = expectStatus(
                            () -> f.service().update(identityWithoutBranch(), ROOM_ID, validCommand()), 403,
                            "Bạn không có quyền cập nhật đối với phòng.");
                    verify(f.repository(), never()).update(any(), any(), any());
                    return result;
                }));
        cases.add(test("RM-037", "Cập nhật phòng tại cơ sở đang tạm ngừng hoạt động",
                SUSPENDED_MESSAGE + "; không kiểm tra trùng và không cập nhật phòng.", "Abnormal", () -> {
                    Fixture f = fixture();
                    suspended(f);
                    TestResult result = expectStatus(
                            () -> f.service().update(f.identity(), ROOM_ID, validCommand()), 403,
                            SUSPENDED_MESSAGE);
                    verify(f.repository(), never()).codeExists(any(), any(), any());
                    verify(f.repository(), never()).update(any(), any(), any());
                    return result;
                }));
        cases.add(test("RM-038", "Cập nhật phòng thành mã RM-UT-01 đã thuộc phòng khác trong cơ sở",
                "Từ chối với thông báo “Mã phòng “RM-UT-01” đã tồn tại trong cơ sở Hoa Bằng. Vui lòng nhập mã khác.”; không cập nhật.", "Abnormal", () -> {
                    Fixture f = fixture();
                    when(f.repository().codeExists(BRANCH_ID, "RM-UT-01", ROOM_ID)).thenReturn(true);
                    TestResult result = expectStatus(
                            () -> f.service().update(f.identity(), ROOM_ID, validCommand()), 409,
                            "Mã phòng “RM-UT-01” đã tồn tại trong cơ sở Hoa Bằng. Vui lòng nhập mã khác.");
                    verify(f.repository(), never()).update(any(), any(), any());
                    return result;
                }));
        return List.copyOf(cases);
    }

    private static ManualTest test(String id, String input, String expected, String type, CheckedAction action) {
        return new ManualTest(id, UC, "BranchRoomService.update", precondition(id), input, expected, type, note(id), action);
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
            case "RM-017", "RM-038" -> validScope + roomExists + activeType
                    + "Một phòng khác cùng cơ sở đã sử dụng mã RM-UT-01. " + unit;
            case "RM-018" -> "Branch Manager qlcs.hoabang đã đăng nhập và đang ACTIVE nhưng tài khoản chưa được gán cơ sở. "
                    + "Phòng không được truy cập trước khi xác định branch scope. " + unit;
            case "RM-037" -> "Branch Manager qlcs.hoabang đã đăng nhập và được phân công quản lý cơ sở Hoa Bằng; cơ sở đang tạm ngừng hoạt động. "
                    + roomExists + activeType + unit;
            default -> throw new IllegalArgumentException("Unknown test case: " + id);
        };
    }

    private static String note(String id) {
        String source = "Nguồn: UC-29_Update_Room.docx; ";
        return source + switch (id) {
            case "RM-011" -> "BR-111, BR-139, BR-173, BR-179; Flyway V14; Production: BranchRoomService.update. Phương pháp kiểm thử: White-box.";
            case "RM-012", "RM-013" -> "BR-111; MSG02; Production: BranchRoomService.update. Phương pháp kiểm thử: White-box.";
            case "RM-014" -> "BR-139; MSG44; Production: BranchRoomService.update. Phương pháp kiểm thử: White-box.";
            case "RM-015" -> "BR-179; MSG03; Production: BranchRoomService.update. Phương pháp kiểm thử: White-box.";
            case "RM-016" -> "Production: BranchRoomService.update. Phương pháp kiểm thử: White-box.";
            case "RM-017", "RM-038" -> "BR-173; MSG38; Production: BranchRoomService.update. Phương pháp kiểm thử: White-box.";
            case "RM-018" -> "MSG24; Production: BranchRoomService.update. Phương pháp kiểm thử: White-box.";
            case "RM-037" -> "BR-177; Production: BranchRoomService.update; BranchRoomRepository.requireActive. Phương pháp kiểm thử: White-box.";
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
