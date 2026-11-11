/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 28/10/2026
 * Description: Manual unit test cases for UC-16 Update Room Type.
 */
package com.gcmb.backend.manual.roomtypemanagement;

import static com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.combine;
import static com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.check;
import static com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.expectStatus;
import static com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.roomType;
import static com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.test;
import static com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.validateAccepted;
import static com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.validateRejectedMessage;

import com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.ManualTest;
import com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.ScriptedJdbc;
import com.gcmb.backend.pricing.dto.RoomTypeCommand;
import com.gcmb.backend.pricing.dto.RoomTypeView;
import com.gcmb.backend.pricing.service.RoomCatalogService;
import java.util.List;
import java.util.UUID;

public final class UpdateRoomTypeManualCases {
    private static final String UC = "UC-16 Update Room Type";
    private static final String PRE = "Head Office Manager qlcc đã đăng nhập; room type cần sửa tồn tại theo điều kiện test case.";
    private static final String MSG02_NAME = "Vui lòng bổ sung Tên hạng phòng.";
    private static final String MSG03_NAME = "Tên hạng phòng chỉ được chứa tối đa 200 ký tự.";

    private UpdateRoomTypeManualCases() {
    }

    public static List<ManualTest> cases() {
        return List.of(
                tc("RTM-041", "Command null", "command=null",
                                        "Validation từ chối với thông báo bắt buộc tương ứng; dữ liệu/audit không đổi.",
                                        () -> validateRejectedMessage(null, "Vui lòng bổ sung Mã hạng phòng.", MSG02_NAME),
                                        "RoomCatalogService.validateRoomType"),
                tc("RTM-044", "Name bắt buộc khi update", "name=null, rỗng, khoảng trắng",
                                        "Từ chối với thông báo: " + MSG02_NAME + "; không update.",
                                        () -> combine(
                                                validateRejectedMessage(command("RT-QN", null, 4, null), MSG02_NAME),
                                                validateRejectedMessage(command("RT-QN", "", 4, null), MSG02_NAME),
                                                validateRejectedMessage(command("RT-QN", "   ", 4, null), MSG02_NAME)),
                                        "RoomCatalogService.validateRoomType"),
                tc("RTM-045", "Name đúng max khi update", "name 200 ký tự Unicode",
                                        "Chấp nhận và lưu đủ 200 ký tự sau trim.",
                                        () -> validateAccepted(command("RT-QN", "Ừ".repeat(200), 4, null)),
                                        "RoomCatalogService.validateRoomType"),
                tc("RTM-046", "Name vượt max khi update", "name 201 ký tự",
                                        "Từ chối với thông báo: " + MSG03_NAME + "; dữ liệu không đổi.",
                                        () -> validateRejectedMessage(command("RT-QN", "A".repeat(201), 4, null), MSG03_NAME),
                                        "RoomCatalogService.validateRoomType"),
                tc("RTM-048", "Capacity đúng min khi update", "capacity=1",
                                        "Chấp nhận và lưu capacity=1.",
                                        () -> validateAccepted(command("RT-QN", "Queen", 1, null)),
                                        "RoomCatalogService.validateRoomType"),
                tc("RTM-081", "Cập nhật thành công, giữ nguyên mã",
                                        "code='  rt-qn  ', name='  Queen mới  ', capacity=6, description='  Cập nhật  '",
                                        "Cập nhật name, capacity, description sau trim; giữ nguyên ID, code RT-QN và trạng thái active.", () -> {
                                            UUID id = UUID.fromString("50000000-0000-0000-0000-000000000002");
                                            ScriptedJdbc db = new ScriptedJdbc();
                                            RoomTypeView current = roomType(id, "RT-QN", "Queen", true);
                                            RoomTypeView expected = new RoomTypeView(id, "RT-QN", "Queen mới", "CINEMA", 6, "Cập nhật", true);
                                            db.queryRows.add(List.of(current));
                                            db.queryRows.add(List.of(expected));
                                            RoomTypeView actual = new RoomCatalogService(db).updateRoomType(
                                                    id, command("  rt-qn  ", "  Queen mới  ", 6, "  Cập nhật  "));
                                            Object[] args = db.updates.get(0).args();
                                            return check(actual.equals(expected) && "Queen mới".equals(args[0])
                                                            && Integer.valueOf(6).equals(args[2]) && "Cập nhật".equals(args[3])
                                                            && id.equals(args[4]),
                                                    "name=" + args[0] + ", capacity=" + args[2] + ", id=" + args[4]);
                                        }, "RoomCatalogService.updateRoomType"),
                tc("RTM-082", "Không cho thay đổi mã",
                                        "Room type hiện có code=RT-QN; command code=RT-QN-NEW.",
                                        "Từ chối HTTP 400 với thông báo: Không được thay đổi mã hạng phòng khi chỉnh sửa.; dữ liệu không đổi.", () -> {
                                            UUID id = UUID.fromString("50000000-0000-0000-0000-000000000002");
                                            ScriptedJdbc db = new ScriptedJdbc();
                                            db.queryRows.add(List.of(roomType(id, "RT-QN", "Queen", true)));
                                            return expectStatus(() -> new RoomCatalogService(db).updateRoomType(
                                                            id, command("RT-QN-NEW", "Queen mới", 6, "Cập nhật")),
                                                    400, "Không được thay đổi mã hạng phòng khi chỉnh sửa.");
                                        }, "RoomCatalogService.updateRoomType"),
                tc("RTM-083", "Room type không tồn tại",
                                        "UUID không thuộc room type nào; command hợp lệ.",
                                        "Từ chối HTTP 404 với thông báo: Không tìm thấy hạng phòng.; không tạo bản ghi thay thế.", () -> {
                                            ScriptedJdbc db = new ScriptedJdbc();
                                            db.queryRows.add(List.of());
                                            UUID id = UUID.fromString("50000000-0000-0000-0000-000000000099");
                                            return expectStatus(() -> new RoomCatalogService(db).updateRoomType(id, valid("RT-UNKNOWN")),
                                                    404, "Không tìm thấy hạng phòng.");
                                        }, "RoomCatalogService.updateRoomType")
        );
    }

    private static ManualTest tc(String id, String title, String input, String expected,
            RoomTypeManagementManualRunner.CheckedAction action, String source) {
        return test(id, UC + " - " + title, source, PRE, input, expected, action);
    }

    private static RoomTypeCommand valid(String code) {
        return command(code, "Hạng kiểm thử cập nhật", 6, "Đã cập nhật");
    }

    private static RoomTypeCommand command(String code, String name, Integer capacity, String description) {
        return new RoomTypeCommand(code, name, "CINEMA", capacity, description);
    }
}




