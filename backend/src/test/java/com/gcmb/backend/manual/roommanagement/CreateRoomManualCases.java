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
    private CreateRoomManualCases() {
    }

    public static List<ManualTest> cases() {
        List<ManualTest> cases = new ArrayList<>();
        cases.add(test("RM-001", "Tạo phòng RM-UT-01 với thông tin hợp lệ",
                "Trả RoomView và gọi repository đúng một lần với branch từ identity.", "Normal", () -> {
                    Fixture f = fixture();
                    RoomCommand command = validCommand();
                    RoomView expected = room("Phòng kiểm thử", "AVAILABLE");
                    when(f.repository().create(BRANCH_ID, command)).thenReturn(expected);
                    RoomView actual = f.service().create(f.identity(), command);
                    verify(f.repository()).create(BRANCH_ID, command);
                    return check(expected.equals(actual), "returned=" + actual);
                }));
        cases.add(test("RM-002", "Xác nhận tạo phòng nhưng không cung cấp thông tin phòng",
                "Từ chối với thông báo “Vui lòng bổ sung Thông tin phòng.”; không gọi repository.", "Abnormal", () -> {
                    Fixture f = fixture();
                    var result = expectStatus(() -> f.service().create(f.identity(), null), 400,
                            "Vui lòng bổ sung Thông tin phòng.");
                    verify(f.repository(), never()).create(any(), any());
                    return result;
                }));
        cases.add(test("RM-003", "V01 chưa đăng nhập; V02 tài khoản chưa được gán cơ sở",
                "Từ chối với thông báo “Bạn không có quyền tạo đối với phòng.”; không gọi repository.", "Abnormal", () ->
                        combine(missingScope(null), missingScope(identityWithoutBranch()))));
        cases.add(test("RM-004", "V01 mã phòng null; V02 rỗng; V03 chỉ có khoảng trắng",
                "Mỗi biến thể bị từ chối với thông báo “Vui lòng bổ sung Mã phòng.”; không gọi repository.", "Abnormal", () ->
                        combine(invalidRequired(command(null, "Phòng", 1, ROOM_TYPE_ID, 1, null), "Mã phòng"),
                                invalidRequired(command("", "Phòng", 1, ROOM_TYPE_ID, 1, null), "Mã phòng"),
                                invalidRequired(command("   ", "Phòng", 1, ROOM_TYPE_ID, 1, null), "Mã phòng"))));
        cases.add(test("RM-005", "V01 tên phòng null; V02 rỗng; V03 chỉ có khoảng trắng",
                "Mỗi biến thể bị từ chối với thông báo “Vui lòng bổ sung Tên phòng.”; không gọi repository.", "Abnormal", () ->
                        combine(invalidRequired(command("RM-UT-01", null, 1, ROOM_TYPE_ID, 1, null), "Tên phòng"),
                                invalidRequired(command("RM-UT-01", "", 1, ROOM_TYPE_ID, 1, null), "Tên phòng"),
                                invalidRequired(command("RM-UT-01", "   ", 1, ROOM_TYPE_ID, 1, null), "Tên phòng"))));
        cases.add(test("RM-006", "Không chọn loại phòng",
                "Từ chối với thông báo “Vui lòng bổ sung Loại phòng.”; không gọi repository.", "Abnormal", () ->
                        invalidRequired(command("RM-UT-01", "Phòng", 1, null, 1, null), "Loại phòng")));
        cases.add(test("RM-007", "Không nhập tầng",
                "Từ chối với thông báo “Vui lòng bổ sung Tầng.”; không gọi repository.", "Abnormal", () ->
                        invalidRequired(command("RM-UT-01", "Phòng", null, ROOM_TYPE_ID, 1, null), "Tầng")));
        cases.add(test("RM-008", "V01 sức chứa null; V02 -1; V03 0; V04 1",
                "V01 hiển thị “Vui lòng bổ sung Sức chứa.”; V02–V03 hiển thị “Sức chứa phải là số nguyên lớn hơn 0.”; V04 tạo thành công.", "Boundary", () ->
                        combine(invalidCapacity(null, true), invalidCapacity(-1, false), invalidCapacity(0, false),
                                validCapacityOne())));
        cases.add(test("RM-009", "V01 tên phòng dài 200; V02 dài 201 ký tự",
                "V01 được chấp nhận; V02 bị từ chối với thông báo “Tên phòng chỉ được chứa tối đa 200 ký tự.”.", "Boundary", () ->
                        combine(nameLength(200, true), nameLength(201, false))));
        cases.add(test("RM-010", "Mã RM-UT-01 trùng do một giao dịch đồng thời vừa ghi trước khi lưu",
                "Từ chối với thông báo “Mã phòng “RM-UT-01” đã tồn tại trong cơ sở Hoa Bằng. Vui lòng nhập mã khác.”.", "Abnormal", () -> {
                    Fixture f = fixture();
                    RoomCommand command = validCommand();
                    when(f.repository().create(BRANCH_ID, command))
                            .thenThrow(new DataIntegrityViolationException("duplicate"));
                    return expectStatus(() -> f.service().create(f.identity(), command), 409,
                            "Mã phòng “RM-UT-01” đã tồn tại trong cơ sở Hoa Bằng. Vui lòng nhập mã khác.");
                }));
        cases.add(test("RM-035", "Tạo phòng tại cơ sở đang tạm ngừng hoạt động",
                SUSPENDED_MESSAGE + "; không kiểm tra trùng và không tạo phòng.", "Abnormal", () -> {
                    Fixture f = fixture();
                    suspended(f);
                    TestResult result = expectStatus(() -> f.service().create(f.identity(), validCommand()), 403,
                            SUSPENDED_MESSAGE);
                    verify(f.repository(), never()).codeExists(any(), any(), any());
                    verify(f.repository(), never()).create(any(), any());
                    return result;
                }));
        cases.add(test("RM-036", "Tạo phòng với mã RM-UT-01 đã tồn tại trong cơ sở Hoa Bằng",
                "Từ chối với thông báo “Mã phòng “RM-UT-01” đã tồn tại trong cơ sở Hoa Bằng. Vui lòng nhập mã khác.”; không tạo phòng.", "Abnormal", () -> {
                    Fixture f = fixture();
                    when(f.repository().codeExists(BRANCH_ID, "RM-UT-01", null)).thenReturn(true);
                    TestResult result = expectStatus(() -> f.service().create(f.identity(), validCommand()), 409,
                            "Mã phòng “RM-UT-01” đã tồn tại trong cơ sở Hoa Bằng. Vui lòng nhập mã khác.");
                    verify(f.repository(), never()).create(any(), any());
                    return result;
                }));
        return List.copyOf(cases);
    }

    private static ManualTest test(String id, String input, String expected, String type, CheckedAction action) {
        return new ManualTest(id, UC, "BranchRoomService.create", precondition(id), input, expected, type, note(id), action);
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
            case "RM-010", "RM-036" -> validScope + activeType
                    + "Một phòng khác trong cơ sở Hoa Bằng đã sử dụng mã RM-UT-01. " + unit;
            case "RM-035" -> "Branch Manager qlcs.hoabang đã đăng nhập và được phân công quản lý cơ sở Hoa Bằng; cơ sở đang tạm ngừng hoạt động. "
                    + activeType + unit;
            default -> throw new IllegalArgumentException("Unknown test case: " + id);
        };
    }

    private static String note(String id) {
        String source = "Nguồn: UC-28_Create_Room.docx; ";
        return source + switch (id) {
            case "RM-001" -> "BR-111, BR-139, BR-164, BR-173, BR-179; Flyway V14; Production: BranchRoomService.create. Phương pháp kiểm thử: White-box.";
            case "RM-002", "RM-004", "RM-005", "RM-006", "RM-007" -> "BR-111; MSG02; Production: BranchRoomService.create. Phương pháp kiểm thử: White-box.";
            case "RM-003" -> "MSG24; Production: BranchRoomService.create. Phương pháp kiểm thử: White-box.";
            case "RM-008" -> "BR-111, BR-139; MSG02, MSG44; Production: BranchRoomService.create. Phương pháp kiểm thử: White-box.";
            case "RM-009" -> "BR-179; MSG03; Production: BranchRoomService.create. Phương pháp kiểm thử: White-box.";
            case "RM-010", "RM-036" -> "BR-173; MSG38; Production: BranchRoomService.create. Phương pháp kiểm thử: White-box.";
            case "RM-035" -> "BR-177; Production: BranchRoomService.create; BranchRoomRepository.requireActive. Phương pháp kiểm thử: White-box.";
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
