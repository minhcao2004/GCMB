/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 09/11/2026
 * Description: Manual unit test cases for UC-17 Change Room Type Status.
 */
package com.gcmb.backend.manual.roomtypemanagement;

import static com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.check;
import static com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.expectStatus;
import static com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.roomType;
import static com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.test;

import com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.ManualTest;
import com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.ScriptedJdbc;
import com.gcmb.backend.pricing.dto.RoomTypeView;
import com.gcmb.backend.pricing.service.RoomCatalogService;
import java.util.List;
import java.util.UUID;

public final class ChangeRoomTypeStatusManualCases {
    private static final String UC = "UC-17 Change Room Type Status";
    private static final String PRE = "Head Office Manager đã đăng nhập; room type cần đổi trạng thái tồn tại theo điều kiện test case.";

    private ChangeRoomTypeStatusManualCases() {
    }

    public static List<ManualTest> cases() {
        return List.of(
                tc("RTM-084", "Ngừng sử dụng thành công",
                        "Room type RT-QN đang active; chọn ngừng sử dụng.",
                        "Trạng thái chuyển thành inactive; dữ liệu hạng phòng và lịch sử liên quan vẫn được giữ nguyên.", () -> {
                            UUID id = UUID.fromString("50000000-0000-0000-0000-000000000004");
                            ScriptedJdbc db = new ScriptedJdbc();
                            db.queryRows.add(List.of(roomType(id, "RT-QN", "Queen", false)));
                            RoomTypeView actual = new RoomCatalogService(db).deactivateRoomType(id);
                            return check(!actual.active() && db.updates.size() == 1
                                            && db.updates.get(0).sql().contains("SET active=false")
                                            && id.equals(db.updates.get(0).args()[0]),
                                    "active=" + actual.active() + ", updates=" + db.updates.size());
                        }, "RoomCatalogService.deactivateRoomType"),
                tc("RTM-085", "Hạng phòng không tồn tại",
                        "UUID không thuộc room type nào; chọn ngừng sử dụng.",
                        "Từ chối HTTP 404 với thông báo: Không tìm thấy hạng phòng.; không thay đổi dữ liệu.", () -> {
                            ScriptedJdbc db = new ScriptedJdbc();
                            db.updateResults.add(0);
                            UUID id = UUID.fromString("50000000-0000-0000-0000-000000000099");
                            return expectStatus(() -> new RoomCatalogService(db).deactivateRoomType(id),
                                    404, "Không tìm thấy hạng phòng.");
                        }, "RoomCatalogService.deactivateRoomType"),
                tc("RTM-086", "Kích hoạt lại hạng phòng",
                        "Room type RT-KG đang inactive và lịch sử liên quan còn tồn tại; chọn kích hoạt lại.",
                        "Có thao tác production chuyển inactive thành active mà không tạo room type mới hoặc xóa lịch sử liên quan.", () -> {
                            try {
                                RoomCatalogService.class.getDeclaredMethod("reactivateRoomType", UUID.class);
                                return check(true, "Production có phương thức reactivateRoomType(UUID)");
                            } catch (NoSuchMethodException failure) {
                                return check(false, "Production chưa có phương thức reactivateRoomType(UUID)");
                            }
                        }, "RoomCatalogService status contract")
        );
    }

    private static ManualTest tc(String id, String title, String input, String expected,
            RoomTypeManagementManualRunner.CheckedAction action, String source) {
        return test(id, UC + " - " + title, source, PRE, input, expected, action);
    }
}
