/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 29/10/2026
 * Description: Manual unit test cases for UC-28 Create Room.
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

public final class CreateRoomManualCases {
    private static final String UC = "UC-28 Create Room";
    private static final String NOTE = "Nguồn: UC-28_Create_Room.docx; BR-111, BR-139, BR-173, BR-179; MSG02, MSG03, MSG24, MSG38, MSG44; Flyway V14. Phương pháp: White-box.";

    private CreateRoomManualCases() {
    }

    public static List<ManualTest> cases() {
        List<ManualTest> cases = new ArrayList<>();
        cases.add(test("RM-001", "create(identity, validCommand)",
                "Trả RoomView và gọi repository đúng một lần với branch từ identity.", "Normal", () -> {
                    Fixture f = fixture();
                    RoomCommand command = validCommand();
                    RoomView expected = room("Phòng kiểm thử", "AVAILABLE");
                    when(f.repository().create(BRANCH_ID, command)).thenReturn(expected);
                    RoomView actual = f.service().create(f.identity(), command);
                    verify(f.repository()).create(BRANCH_ID, command);
                    return check(expected.equals(actual), "returned=" + actual);
                }));
        cases.add(test("RM-002", "create(identity, null)",
                "Từ chối 400 bằng MSG02 và không gọi repository.", "Abnormal", () -> {
                    Fixture f = fixture();
                    var result = expectStatus(() -> f.service().create(f.identity(), null), 400,
                            "Vui lòng bổ sung Thông tin phòng.");
                    verify(f.repository(), never()).create(any(), any());
                    return result;
                }));
        cases.add(test("RM-003", "identity=null; identity.branchId=null",
                "Từ chối 403 bằng MSG24 trước khi gọi repository.", "Abnormal", () ->
                        combine(missingScope(null), missingScope(identityWithoutBranch()))));
        cases.add(test("RM-004", "code=null, rỗng hoặc chỉ khoảng trắng",
                "Từng biến thể bị từ chối 400 bằng MSG02; không gọi repository.", "Abnormal", () ->
                        combine(invalidRequired(command(null, "Phòng", 1, ROOM_TYPE_ID, 1, null), "Mã phòng"),
                                invalidRequired(command("", "Phòng", 1, ROOM_TYPE_ID, 1, null), "Mã phòng"),
                                invalidRequired(command("   ", "Phòng", 1, ROOM_TYPE_ID, 1, null), "Mã phòng"))));
        cases.add(test("RM-005", "name=null, rỗng hoặc chỉ khoảng trắng",
                "Từng biến thể bị từ chối 400 bằng MSG02; không gọi repository.", "Abnormal", () ->
                        combine(invalidRequired(command("RM-UT-01", null, 1, ROOM_TYPE_ID, 1, null), "Tên phòng"),
                                invalidRequired(command("RM-UT-01", "", 1, ROOM_TYPE_ID, 1, null), "Tên phòng"),
                                invalidRequired(command("RM-UT-01", "   ", 1, ROOM_TYPE_ID, 1, null), "Tên phòng"))));
        cases.add(test("RM-006", "roomTypeId=null",
                "Từ chối 400 bằng MSG02; không gọi repository.", "Abnormal", () ->
                        invalidRequired(command("RM-UT-01", "Phòng", 1, null, 1, null), "Loại phòng")));
        cases.add(test("RM-007", "floorNumber=null",
                "Từ chối 400 bằng MSG02; không gọi repository.", "Abnormal", () ->
                        invalidRequired(command("RM-UT-01", "Phòng", null, ROOM_TYPE_ID, 1, null), "Tầng")));
        cases.add(test("RM-008", "capacity=null, -1, 0 và 1",
                "Ba giá trị không hợp lệ bị từ chối; biên 1 được tạo thành công.", "Boundary", () ->
                        combine(invalidCapacity(null, true), invalidCapacity(-1, false), invalidCapacity(0, false),
                                validCapacityOne())));
        cases.add(test("RM-009", "name dài 200 và 201 ký tự",
                "Chấp nhận 200; từ chối 201 bằng MSG03.", "Boundary", () ->
                        combine(nameLength(200, true), nameLength(201, false))));
        cases.add(test("RM-010", "repo.create báo trùng code RM-UT-01 trong cơ sở Hoa Bằng",
                "Từ chối 409 và hiển thị đúng MSG38.", "Abnormal", () -> {
                    Fixture f = fixture();
                    RoomCommand command = validCommand();
                    when(f.repository().create(BRANCH_ID, command))
                            .thenThrow(new DataIntegrityViolationException("duplicate"));
                    return expectStatus(() -> f.service().create(f.identity(), command), 409,
                            "Mã phòng “RM-UT-01” đã tồn tại trong cơ sở Hoa Bằng. Vui lòng nhập mã khác.");
                }));
        return List.copyOf(cases);
    }

    private static ManualTest test(String id, String input, String expected, String type, CheckedAction action) {
        return new ManualTest(id, UC, "BranchRoomService.create", precondition(id), input, expected, type, NOTE, action);
    }

    private static String precondition(String id) {
        String validScope = "Branch Manager qlcs.hoabang đã đăng nhập, đang ACTIVE và được phân công quản lý cơ sở Hoa Bằng. ";
        String activeType = "Loại phòng RT-QN tồn tại và đang hoạt động theo dữ liệu Flyway V14. ";
        String unit = "BranchRoomService thật; BranchRoomRepository mock; không ghi database.";
        return switch (id) {
            case "RM-001" -> validScope + activeType
                    + "Mã RM-UT-01 chưa được sử dụng trong cơ sở. " + unit;
            case "RM-002" -> validScope + activeType
                    + "Request không cung cấp RoomCommand. " + unit;
            case "RM-003" -> "V01 request không có authenticated Identity; V02 Branch Manager đã đăng nhập nhưng tài khoản chưa được gán cơ sở. " + unit;
            case "RM-004", "RM-005", "RM-006", "RM-007", "RM-008", "RM-009" -> validScope
                    + activeType + "Các trường không được nêu trong Input đều hợp lệ. " + unit;
            case "RM-010" -> validScope + activeType
                    + "Một phòng khác trong cơ sở Hoa Bằng đã sử dụng mã RM-UT-01. " + unit;
            default -> throw new IllegalArgumentException("Unknown test case: " + id);
        };
    }

    private static TestResult missingScope(com.gcmb.backend.auth.dto.Identity identity) {
        Fixture f = fixture();
        TestResult result = expectStatus(() -> f.service().create(identity, validCommand()), 403,
                "Bạn không có quyền tạo đối với phòng.");
        verify(f.repository(), never()).create(any(), any());
        return result;
    }

    private static TestResult invalidRequired(RoomCommand command, String field) {
        Fixture f = fixture();
        TestResult result = expectStatus(() -> f.service().create(f.identity(), command), 400,
                "Vui lòng bổ sung " + field + ".");
        verify(f.repository(), never()).create(any(), any());
        return result;
    }

    private static TestResult invalidCapacity(Integer capacity, boolean missing) {
        Fixture f = fixture();
        RoomCommand command = command("RM-UT-01", "Phòng", 1, ROOM_TYPE_ID, capacity, null);
        String message = missing ? "Vui lòng bổ sung Sức chứa." : "Sức chứa phải là số nguyên lớn hơn 0.";
        TestResult result = expectStatus(() -> f.service().create(f.identity(), command), 400, message);
        verify(f.repository(), never()).create(any(), any());
        return result;
    }

    private static TestResult validCapacityOne() {
        Fixture f = fixture();
        RoomCommand command = command("RM-UT-01", "Phòng", 1, ROOM_TYPE_ID, 1, null);
        RoomView expected = room("Phòng", "AVAILABLE");
        when(f.repository().create(BRANCH_ID, command)).thenReturn(expected);
        return check(expected.equals(f.service().create(f.identity(), command)), "capacity=1 accepted");
    }

    private static TestResult nameLength(int length, boolean valid) {
        Fixture f = fixture();
        RoomCommand command = command("RM-UT-01", "N".repeat(length), 1, ROOM_TYPE_ID, 1, null);
        if (!valid) {
            return expectStatus(() -> f.service().create(f.identity(), command), 400,
                    "Tên phòng chỉ được chứa tối đa 200 ký tự.");
        }
        when(f.repository().create(BRANCH_ID, command)).thenReturn(room("N".repeat(length), "AVAILABLE"));
        f.service().create(f.identity(), command);
        return check(true, "acceptedLength=" + length);
    }
}
