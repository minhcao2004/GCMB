/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 29/10/2026
 * Description: Manual unit test cases for UC-32 View Rooms.
 */
package com.gcmb.backend.manual.roommanagement;

import static com.gcmb.backend.manual.roommanagement.RoomManagementManualRunner.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gcmb.backend.branch.dto.RoomView;
import com.gcmb.backend.manual.roommanagement.RoomManagementManualRunner.Fixture;
import com.gcmb.backend.manual.roommanagement.RoomManagementManualRunner.ManualTest;
import java.util.List;

public final class ViewRoomsManualCases {
    private static final String UC = "UC-32 View Rooms";
    private static final String NOTE = "Nguồn: UC-32_View_Rooms.docx; BR-122; MSG01, MSG24. MSG01 và cảnh báo BR-122 không được khẳng định tại service UT. Phương pháp: White-box.";

    private ViewRoomsManualCases() {
    }

    public static List<ManualTest> cases() {
        return List.of(
                test("RM-022", "list(identity) với repository trả hai phòng",
                        "Trả nguyên danh sách và chỉ đọc đúng branch từ identity.", "Normal", () -> {
                            Fixture f = fixture();
                            List<RoomView> expected = List.of(room("Phòng 01", "AVAILABLE"),
                                    new RoomView(java.util.UUID.fromString("34370000-0000-4000-8000-000000000102"),
                                            "RM-UT-02", "Phòng 02", 2, ROOM_TYPE_ID, "RT-QN",
                                            "Phòng tiêu chuẩn", "CINEMA", 2, "", "INACTIVE", "Bảo trì",
                                            null, "READY"));
                            when(f.repository().list(BRANCH_ID)).thenReturn(expected);
                            List<RoomView> actual = f.service().list(f.identity());
                            verify(f.repository()).list(BRANCH_ID);
                            return check(expected.equals(actual), "returnedCount=" + actual.size());
                        }),
                test("RM-023", "list(identity) với repository trả []",
                        "Trả danh sách rỗng, không exception và không ghi dữ liệu.", "Normal", () -> {
                            Fixture f = fixture();
                            when(f.repository().list(BRANCH_ID)).thenReturn(List.of());
                            List<RoomView> actual = f.service().list(f.identity());
                            return check(actual.isEmpty(), "returned=[]");
                        }),
                test("RM-024", "identity=null hoặc identity.branchId=null",
                        "Từ chối 403 bằng MSG24 trước khi gọi repository.", "Abnormal", () ->
                                combine(missingScope(null), missingScope(identityWithoutBranch()))),
                test("RM-025", "types(identity) trả danh mục; identity thiếu branch bị từ chối",
                        "Identity hợp lệ nhận danh mục và không ghi; thiếu branch nhận 403.", "Normal", () -> {
                            Fixture f = fixture();
                            List<RoomView> expected = List.of(new RoomView(null, "RT-QN", "Phòng tiêu chuẩn",
                                    null, ROOM_TYPE_ID, "RT-QN", "Phòng tiêu chuẩn", "CINEMA",
                                    null, null, null, null, null, null));
                            when(f.repository().types()).thenReturn(expected);
                            boolean valid = expected.equals(f.service().types(f.identity()));
                            verify(f.repository()).types();
                            Fixture missing = fixture();
                            TestResult denied = expectStatus(
                                    () -> missing.service().types(identityWithoutBranch()), 403,
                                    "Bạn không có quyền xem đối với danh mục loại phòng.");
                            verify(missing.repository(), never()).types();
                            return combine(check(valid, "types=" + expected.size()), denied);
                        })
        );
    }

    private static ManualTest test(String id, String input, String expected, String type, CheckedAction action) {
        return new ManualTest(id, UC, "BranchRoomService.list/types", precondition(id), input, expected, type, NOTE, action);
    }

    private static String precondition(String id) {
        String validScope = "Người dùng đã đăng nhập bằng vai trò được UC-32 cho phép; fixture sử dụng Branch Manager qlcs.hoabang đang ACTIVE và có phạm vi cơ sở Hoa Bằng. ";
        String unit = "BranchRoomService thật; BranchRoomRepository mock; thao tác chỉ đọc.";
        return switch (id) {
            case "RM-022" -> validScope + "Trong phạm vi cơ sở có hai phòng. " + unit;
            case "RM-023" -> validScope + "Không có phòng nào trong phạm vi cơ sở. " + unit;
            case "RM-024" -> "V01 request không có authenticated Identity; V02 người dùng đã đăng nhập nhưng không có branch scope. " + unit;
            case "RM-025" -> "V01 " + validScope
                    + "Có ít nhất một loại phòng đang hoạt động; V02 người dùng đã đăng nhập nhưng không có branch scope. " + unit;
            default -> throw new IllegalArgumentException("Unknown test case: " + id);
        };
    }

    private static TestResult missingScope(com.gcmb.backend.auth.dto.Identity identity) {
        Fixture f = fixture();
        TestResult result = expectStatus(() -> f.service().list(identity), 403,
                "Bạn không có quyền xem đối với phòng.");
        verify(f.repository(), never()).list(any());
        return result;
    }
}
