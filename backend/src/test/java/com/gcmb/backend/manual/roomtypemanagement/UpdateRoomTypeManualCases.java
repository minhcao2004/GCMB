/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 28/10/2026
 * Description: Manual unit test cases for UC-16 Update Room Type.
 */
package com.gcmb.backend.manual.roomtypemanagement;

import static com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.combine;
import static com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.test;
import static com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.validateAccepted;
import static com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.validateRejectedMessage;

import com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.ManualTest;
import com.gcmb.backend.pricing.dto.RoomTypeCommand;
import java.util.List;

public final class UpdateRoomTypeManualCases {
    private static final String UC = "UC-16 Update Room Type";
    private static final String SERVICE = "src/main/java/com/gcmb/backend/pricing/service/RoomCatalogService.java";
    private static final String CONTROLLER = "src/main/java/com/gcmb/backend/pricing/controller/RoomCatalogController.java";
    private static final String SECURITY = "src/main/java/com/gcmb/backend/auth/security/AuthSecurity.java";
    private static final String PAGE = "../frontend/src/features/pricing/pages/RoomCatalogPage.js";
    private static final String API = "../frontend/src/features/pricing/services/roomCatalogApi.js";
    private static final String PRE = "Head Office Manager qlcc đã đăng nhập; room type cần sửa tồn tại theo điều kiện test case.";
    private static final String MSG02_NAME = "Vui lòng bổ sung Tên hạng phòng.";
    private static final String MSG03_NAME = "Tên hạng phòng chỉ được chứa tối đa 200 ký tự.";
    private static final String MSG44 = "Sức chứa phải là số nguyên lớn hơn 0.";

    private UpdateRoomTypeManualCases() {
    }

    public static List<ManualTest> cases() {
        return List.of(
                tc("RTM-041", "Command null", "command=null",
                                        "Validation từ chối bằng MSG02; dữ liệu/audit không đổi.",
                                        () -> validateRejectedMessage(null, "Vui lòng bổ sung Mã hạng phòng.", MSG02_NAME),
                                        "RoomCatalogService.validateRoomType"),
                tc("RTM-044", "Name bắt buộc khi update", "name=null, rỗng, khoảng trắng",
                                        "Từ chối bằng MSG02; không update.",
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
                                        "Từ chối bằng MSG03; dữ liệu không đổi.",
                                        () -> validateRejectedMessage(command("RT-QN", "A".repeat(201), 4, null), MSG03_NAME),
                                        "RoomCatalogService.validateRoomType"),
                tc("RTM-048", "Capacity đúng min khi update", "capacity=1",
                                        "Chấp nhận và lưu capacity=1.",
                                        () -> validateAccepted(command("RT-QN", "Queen", 1, null)),
                                        "RoomCatalogService.validateRoomType")
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




