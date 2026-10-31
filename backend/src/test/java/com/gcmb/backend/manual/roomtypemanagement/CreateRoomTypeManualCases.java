/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 28/10/2026
 * Description: Manual unit test cases for UC-14 Create Room Type.
 */
package com.gcmb.backend.manual.roomtypemanagement;

import static com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.combine;
import static com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.test;
import static com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.validateAccepted;
import static com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.validateRejectedMessage;
import static com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.validateRejectedStatus;

import com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.ManualTest;
import com.gcmb.backend.pricing.dto.RoomTypeCommand;
import java.util.ArrayList;
import java.util.List;

public final class CreateRoomTypeManualCases {
    private static final String UC = "UC-14 Create Room Type";
    private static final String SERVICE = "src/main/java/com/gcmb/backend/pricing/service/RoomCatalogService.java";
    private static final String CONTROLLER = "src/main/java/com/gcmb/backend/pricing/controller/RoomCatalogController.java";
    private static final String SECURITY = "src/main/java/com/gcmb/backend/auth/security/AuthSecurity.java";
    private static final String PAGE = "../frontend/src/features/pricing/pages/RoomCatalogPage.js";
    private static final String API = "../frontend/src/features/pricing/services/roomCatalogApi.js";
    private static final String V3 = "src/main/resources/db/migration/V3__room_price_catalog.sql";
    private static final String PRE = "Head Office Manager qlcc và dữ liệu room type demo Flyway V3/V14 tồn tại theo điều kiện test case.";
    private static final String MSG02_CODE = "Vui lòng bổ sung Mã hạng phòng.";
    private static final String MSG02_NAME = "Vui lòng bổ sung Tên hạng phòng.";
    private static final String MSG03_NAME = "Tên hạng phòng chỉ được chứa tối đa 200 ký tự.";
    private static final String MSG37 = "Mã hạng phòng phải có từ 2 đến 60 ký tự và chỉ được dùng chữ cái không dấu, chữ số, dấu gạch dưới (_) hoặc dấu gạch nối (-).";
    private static final String MSG44 = "Sức chứa phải là số nguyên lớn hơn 0.";

    private CreateRoomTypeManualCases() {
    }

    public static List<ManualTest> cases() {
        List<ManualTest> cases = new ArrayList<>();
                cases.add(tc("RTM-007", "Command null", "command=null",
                "Validation từ chối bằng MSG02; không ghi dữ liệu.",
                () -> validateRejectedMessage(null, MSG02_CODE), "RoomCatalogService.validateRoomType"));
        cases.add(tc("RTM-008", "Code bắt buộc", "code=null, rỗng, khoảng trắng",
                "Từ chối bằng MSG02/MSG37 phù hợp.",
                () -> combine(
                        validateRejectedMessage(command(null, "Hạng", "CINEMA", 4, ""), MSG02_CODE),
                        validateRejectedMessage(command("", "Hạng", "CINEMA", 4, ""), MSG37),
                        validateRejectedMessage(command("   ", "Hạng", "CINEMA", 4, ""), MSG37)),
                "RoomCatalogService.validateRoomType"));
        cases.add(tc("RTM-009", "Code dưới min", "code=A",
                "Từ chối bằng đúng MSG37.",
                () -> validateRejectedMessage(valid("A"), MSG37), "RoomCatalogService.validateRoomType"));
        cases.add(tc("RTM-010", "Code đúng min", "code=A1",
                "Chấp nhận code 2 ký tự.",
                () -> validateAccepted(valid("A1")), "RoomCatalogService.validateRoomType"));
        cases.add(tc("RTM-012", "Code vượt max", "code dài 61 ký tự",
                "Từ chối bằng đúng MSG37.",
                () -> validateRejectedMessage(valid("A".repeat(61)), MSG37), "RoomCatalogService.validateRoomType"));
        cases.add(tc("RTM-013", "Code sai ký tự", "RT 01, RT.01, RT@01, HẠNG_01",
                "Từ chối tất cả bằng MSG37.",
                () -> combine(
                        validateRejectedMessage(valid("RT 01"), MSG37),
                        validateRejectedMessage(valid("RT.01"), MSG37),
                        validateRejectedMessage(valid("RT@01"), MSG37),
                        validateRejectedMessage(valid("HẠNG_01"), MSG37)),
                "RoomCatalogService.validateRoomType"));
        cases.add(tc("RTM-015", "Name bắt buộc", "name=null, rỗng, khoảng trắng",
                "Từ chối bằng MSG02.",
                () -> combine(
                        validateRejectedMessage(command("RT01", null, "CINEMA", 4, ""), MSG02_NAME),
                        validateRejectedMessage(command("RT01", "", "CINEMA", 4, ""), MSG02_NAME),
                        validateRejectedMessage(command("RT01", "   ", "CINEMA", 4, ""), MSG02_NAME)),
                "RoomCatalogService.validateRoomType"));
        cases.add(tc("RTM-016", "Name đúng max", "name dài 200 ký tự Unicode",
                "Chấp nhận đủ 200 ký tự sau trim.",
                () -> validateAccepted(command("RT01", "Ừ".repeat(200), "CINEMA", 4, "")),
                "RoomCatalogService.validateRoomType"));
        cases.add(tc("RTM-017", "Name vượt max", "name dài 201 ký tự",
                "Từ chối bằng MSG03 max_length=200.",
                () -> validateRejectedMessage(command("RT01", "A".repeat(201), "CINEMA", 4, ""), MSG03_NAME),
                "RoomCatalogService.validateRoomType"));
        cases.add(tc("RTM-019", "Capacity đúng min", "capacity=1",
                "Chấp nhận và lưu capacity=1.",
                () -> validateAccepted(command("RT01", "Hạng", "CINEMA", 1, "")),
                "RoomCatalogService.validateRoomType"));
        cases.add(tc("RTM-020", "Service type không hợp lệ", "null, KARAOKE",
                "Validation từ chối; không tạo type ngoài CINEMA/MUSIC_BOX.",
                () -> combine(
                        validateRejectedStatus(command("RT01", "Hạng", null, 4, "")),
                        validateRejectedStatus(command("RT02", "Hạng", "KARAOKE", 4, ""))),
                "RoomCatalogService.validateRoomType"));
return List.copyOf(cases);
    }

    private static ManualTest tc(String id, String title, String input, String expected,
            RoomTypeManagementManualRunner.CheckedAction action, String source) {
        return test(id, UC + " - " + title, source, PRE, input, expected, action);
    }

    private static RoomTypeCommand valid(String code) {
        return command(code, "Hạng kiểm thử", "CINEMA", 4, "Dùng cho kiểm thử vòng đời");
    }

    private static RoomTypeCommand command(String code, String name, String serviceType,
            Integer capacity, String description) {
        return new RoomTypeCommand(code, name, serviceType, capacity, description);
    }
}


