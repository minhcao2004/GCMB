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
    private static final String NOTE = "Nguồn: UC-31_Delete_Room.docx; BR-118; MSG24, MSG104. Phương pháp: White-box.";

    private DeleteRoomManualCases() {
    }

    public static List<ManualTest> cases() {
        return List.of(
                test("RM-019", "repo.delete(branchId, roomId)=true",
                        "Hoàn tất không exception và repository nhận đúng branch/roomId.", "Normal", () -> {
                            Fixture f = fixture();
                            when(f.repository().delete(BRANCH_ID, ROOM_ID)).thenReturn(true);
                            f.service().delete(f.identity(), ROOM_ID);
                            verify(f.repository()).delete(BRANCH_ID, ROOM_ID);
                            return check(true, "delete completed once");
                        }),
                test("RM-020", "repo.delete(branchId, roomId)=false do có dữ liệu liên quan",
                        "Từ chối 409 và hiển thị đúng MSG104.", "Abnormal", () -> {
                            Fixture f = fixture();
                            when(f.repository().delete(BRANCH_ID, ROOM_ID)).thenReturn(false);
                            return expectStatus(() -> f.service().delete(f.identity(), ROOM_ID), 409,
                                    "Không thể thực hiện cách xóa này vì sẽ làm mất hoặc thay đổi dữ liệu liên quan. Lịch sử nghiệp vụ và các bản ghi liên quan phải được giữ nguyên.");
                        }),
                test("RM-021", "identity.branchId=null",
                        "Từ chối 403 bằng MSG24 và không gọi repository.", "Abnormal", () -> {
                            Fixture f = fixture();
                            TestResult result = expectStatus(
                                    () -> f.service().delete(identityWithoutBranch(), ROOM_ID), 403,
                                    "Bạn không có quyền xóa đối với phòng.");
                            verify(f.repository(), never()).delete(any(), any());
                            return result;
                        })
        );
    }

    private static ManualTest test(String id, String input, String expected, String type, CheckedAction action) {
        return new ManualTest(id, UC, "BranchRoomService.delete", precondition(id), input, expected, type, NOTE, action);
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
            default -> throw new IllegalArgumentException("Unknown test case: " + id);
        };
    }
}
