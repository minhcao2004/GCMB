/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 09/11/2026
 * Description: Manual unit test cases for UC-18 Delete Room Type.
 */
package com.gcmb.backend.manual.roomtypemanagement;

import static com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.check;
import static com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.expectStatus;
import static com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.test;

import com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.ManualTest;
import com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.ScriptedJdbc;
import com.gcmb.backend.pricing.service.RoomCatalogService;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;

public final class DeleteRoomTypeManualCases {
    private static final String UC = "UC-18 Delete Room Type";
    private static final String PRE = "Head Office Manager đã đăng nhập; room type cần xóa tồn tại theo điều kiện test case.";
    private static final String HISTORY_WARNING = "Không thể thực hiện cách xóa này vì sẽ làm mất hoặc thay đổi dữ liệu liên quan. Lịch sử nghiệp vụ và các bản ghi liên quan phải được giữ nguyên.";

    private DeleteRoomTypeManualCases() {
    }

    public static List<ManualTest> cases() {
        return List.of(
                tc("RTM-087", "Xóa hạng phòng chưa được tham chiếu",
                        "Room type tồn tại và không được room, price rule hoặc combo tham chiếu.",
                        "Xóa đúng room type; không ảnh hưởng bản ghi khác.", () -> {
                            UUID id = UUID.fromString("50000000-0000-0000-0000-000000000005");
                            ScriptedJdbc db = new ScriptedJdbc();
                            db.typedRows.add(List.of(id));
                            db.objects.add(false);
                            new RoomCatalogService(db).deleteRoomType(id);
                            return check(db.updates.size() == 1
                                            && db.updates.get(0).sql().contains("DELETE FROM gcmb.room_type")
                                            && id.equals(db.updates.get(0).args()[0]),
                                    "deleteCalls=" + db.updates.size());
                        }, "RoomCatalogService.deleteRoomType"),
                tc("RTM-088", "Hạng phòng không tồn tại",
                        "UUID không thuộc room type nào.",
                        "Từ chối HTTP 404 với thông báo: Không tìm thấy hạng phòng.; không xóa dữ liệu.", () -> {
                            ScriptedJdbc db = new ScriptedJdbc();
                            db.typedRows.add(List.of());
                            UUID id = UUID.fromString("50000000-0000-0000-0000-000000000099");
                            return expectStatus(() -> new RoomCatalogService(db).deleteRoomType(id),
                                    404, "Không tìm thấy hạng phòng.");
                        }, "RoomCatalogService.deleteRoomType"),
                tc("RTM-089", "Hạng phòng đang được tham chiếu",
                        "Room type tồn tại và được ít nhất một room, price rule hoặc combo tham chiếu.",
                        "Từ chối HTTP 409 với thông báo: " + HISTORY_WARNING + "; không xóa room type hoặc lịch sử liên quan.", () -> {
                            UUID id = UUID.fromString("50000000-0000-0000-0000-000000000004");
                            ScriptedJdbc db = new ScriptedJdbc();
                            db.typedRows.add(List.of(id));
                            db.objects.add(true);
                            return expectStatus(() -> new RoomCatalogService(db).deleteRoomType(id),
                                    409, HISTORY_WARNING);
                        }, "RoomCatalogService.deleteRoomType"),
                tc("RTM-090", "Phát sinh tham chiếu đồng thời khi xóa",
                        "Kiểm tra ban đầu chưa thấy tham chiếu; DELETE phát sinh DataIntegrityViolationException do FK RESTRICT.",
                        "Từ chối HTTP 409 với thông báo: " + HISTORY_WARNING + "; dữ liệu lịch sử vẫn được giữ nguyên.", () -> {
                            UUID id = UUID.fromString("50000000-0000-0000-0000-000000000005");
                            ScriptedJdbc db = new ScriptedJdbc();
                            db.typedRows.add(List.of(id));
                            db.objects.add(false);
                            db.nextUpdateFailure = new DataIntegrityViolationException("concurrent reference");
                            return expectStatus(() -> new RoomCatalogService(db).deleteRoomType(id),
                                    409, HISTORY_WARNING);
                        }, "RoomCatalogService.deleteRoomType")
        );
    }

    private static ManualTest tc(String id, String title, String input, String expected,
            RoomTypeManagementManualRunner.CheckedAction action, String source) {
        return test(id, UC + " - " + title, source, PRE, input, expected, action);
    }
}
