/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 29/10/2026
 * Description: Manual unit test cases for UC-31 Delete Room.
 */
package com.gcmb.backend.manual.roommanagement;

import static com.gcmb.backend.manual.roommanagement.RoomManagementManualRunner.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gcmb.backend.manual.roommanagement.RoomManagementManualRunner.Fixture;
import com.gcmb.backend.manual.roommanagement.RoomManagementManualRunner.ManualTest;
import java.util.List;

public final class DeleteRoomManualCases {
    private static final String UC = "UC-31 Delete Room";
    private DeleteRoomManualCases() {
    }

    public static List<ManualTest> cases() {
        return List.of(
                test("RM-019", "Chọn xóa phòng không có dữ liệu nghiệp vụ tham chiếu và xác nhận",
                        "Hoàn tất không exception và repository nhận đúng branch/roomId.", "Normal", () -> {
                            Fixture f = fixture();
                            when(f.repository().delete(BRANCH_ID, ROOM_ID)).thenReturn(true);
                            f.service().delete(f.identity(), ROOM_ID);
                            verify(f.repository()).delete(BRANCH_ID, ROOM_ID);
                            return check(true, "delete completed once");
                        }),
                test("RM-020", "Chọn xóa phòng đang được booking, thiết bị hoặc lịch sử tham chiếu",
                        "Từ chối với thông báo “Không thể thực hiện cách xóa này vì sẽ làm mất hoặc thay đổi dữ liệu liên quan. Lịch sử nghiệp vụ và các bản ghi liên quan phải được giữ nguyên.”; phòng vẫn còn.", "Abnormal", () -> {
                            Fixture f = fixture();
                            when(f.repository().delete(BRANCH_ID, ROOM_ID)).thenReturn(false);
                            return expectStatus(() -> f.service().delete(f.identity(), ROOM_ID), 409,
                                    "Không thể thực hiện cách xóa này vì sẽ làm mất hoặc thay đổi dữ liệu liên quan. Lịch sử nghiệp vụ và các bản ghi liên quan phải được giữ nguyên.");
                        }),
                test("RM-021", "Tài khoản Branch Manager chưa được gán cơ sở",
                        "Từ chối với thông báo “Bạn không có quyền xóa đối với phòng.”; không gọi repository.", "Abnormal", () -> {
                            Fixture f = fixture();
                            TestResult result = expectStatus(
                                    () -> f.service().delete(identityWithoutBranch(), ROOM_ID), 403,
                                    "Bạn không có quyền xóa đối với phòng.");
                            verify(f.repository(), never()).delete(any(), any());
                            return result;
                        }),
                test("RM-039", "Xóa phòng tại cơ sở đang tạm ngừng hoạt động",
                        SUSPENDED_MESSAGE + "; không xóa phòng.", "Abnormal", () -> {
                            Fixture f = fixture();
                            suspended(f);
                            TestResult result = expectStatus(
                                    () -> f.service().delete(f.identity(), ROOM_ID), 403, SUSPENDED_MESSAGE);
                            verify(f.repository(), never()).delete(any(), any());
                            return result;
                        })
        );
    }

    private static ManualTest test(String id, String input, String expected, String type, CheckedAction action) {
        return new ManualTest(id, UC, "BranchRoomService.delete", precondition(id), input, expected, type, note(id), action);
    }

    private static String precondition(String id) {
        String validScope = "Branch Manager qlcs.hoabang đã đăng nhập, đang ACTIVE và được phân công quản lý cơ sở Hoa Bằng. ";
        String unit = "BranchRoomService thật; BranchRoomRepository mock; không xóa dữ liệu demo thật.";
        return switch (id) {
            case "RM-019" -> validScope
                    + "Phòng cần xóa tồn tại trong cơ sở và không có room allocation, thiết bị hoặc asset transfer tham chiếu. " + unit;
            case "RM-020" -> validScope
                    + "Phòng cần xóa tồn tại trong cơ sở và đang có booking, thiết bị hoặc lịch sử/bản ghi liên quan phải được bảo toàn. " + unit;
            case "RM-021" -> "Branch Manager qlcs.hoabang đã đăng nhập và đang ACTIVE nhưng tài khoản chưa được gán cơ sở. "
                    + "Không thực hiện truy vấn xóa trước khi xác định branch scope. " + unit;
            case "RM-039" -> "Branch Manager qlcs.hoabang đã đăng nhập và được phân công quản lý cơ sở Hoa Bằng; cơ sở đang tạm ngừng hoạt động. "
                    + "Phòng cần xóa tồn tại trong cơ sở. " + unit;
            default -> throw new IllegalArgumentException("Unknown test case: " + id);
        };
    }

    private static String note(String id) {
        String source = "Nguồn: UC-31_Delete_Room.docx; ";
        return source + switch (id) {
            case "RM-019" -> "BR-118; Production: BranchRoomService.delete. Phương pháp kiểm thử: White-box.";
            case "RM-020" -> "BR-118; MSG104; Production: BranchRoomService.delete. Phương pháp kiểm thử: White-box.";
            case "RM-021" -> "MSG24; Production: BranchRoomService.delete. Phương pháp kiểm thử: White-box.";
            case "RM-039" -> "BR-177; Production: BranchRoomService.delete; BranchRoomRepository.requireActive. Phương pháp kiểm thử: White-box.";
            default -> throw new IllegalArgumentException("Unknown test case: " + id);
        };
    }
}
