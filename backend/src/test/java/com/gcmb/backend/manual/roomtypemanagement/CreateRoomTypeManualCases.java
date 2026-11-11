/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 28/10/2026
 * Description: Manual unit test cases for UC-14 Create Room Type.
 */
package com.gcmb.backend.manual.roomtypemanagement;

import static com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.combine;
import static com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.check;
import static com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.expectStatus;
import static com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.roomType;
import static com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.test;
import static com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.validateAccepted;
import static com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.validateRejectedMessage;
import static com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.validateRejectedStatus;

import com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.ManualTest;
import com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.ScriptedJdbc;
import com.gcmb.backend.pricing.dto.RoomTypeCommand;
import com.gcmb.backend.pricing.dto.RoomTypeView;
import com.gcmb.backend.pricing.service.RoomCatalogService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;

public final class CreateRoomTypeManualCases {
    private static final String UC = "UC-14 Create Room Type";
    private static final String PRE = "Head Office Manager qlcc và dữ liệu room type demo Flyway V3/V14 tồn tại theo điều kiện test case.";
    private static final String MSG02_CODE = "Vui lòng bổ sung Mã hạng phòng.";
    private static final String MSG02_NAME = "Vui lòng bổ sung Tên hạng phòng.";
    private static final String MSG03_NAME = "Tên hạng phòng chỉ được chứa tối đa 200 ký tự.";
    private static final String MSG37 = "Mã hạng phòng phải có từ 2 đến 60 ký tự và chỉ được dùng chữ cái không dấu, chữ số, dấu gạch dưới (_) hoặc dấu gạch nối (-).";

    private CreateRoomTypeManualCases() {
    }

    public static List<ManualTest> cases() {
        List<ManualTest> cases = new ArrayList<>();
                cases.add(tc("RTM-007", "Command null", "command=null",
                "Validation từ chối với thông báo: " + MSG02_CODE + "; không ghi dữ liệu.",
                () -> validateRejectedMessage(null, MSG02_CODE), "RoomCatalogService.validateRoomType"));
        cases.add(tc("RTM-008", "Code bắt buộc", "code=null, rỗng, khoảng trắng",
                "Từ chối từng giá trị với thông báo: " + MSG02_CODE + "; không ghi dữ liệu.",
                () -> combine(
                        validateRejectedMessage(command(null, "Hạng", "CINEMA", 4, ""), MSG02_CODE),
                        validateRejectedMessage(command("", "Hạng", "CINEMA", 4, ""), MSG02_CODE),
                        validateRejectedMessage(command("   ", "Hạng", "CINEMA", 4, ""), MSG02_CODE)),
                "RoomCatalogService.validateRoomType"));
        cases.add(tc("RTM-009", "Code dưới min", "code=A",
                "Từ chối với thông báo: " + MSG37,
                () -> validateRejectedMessage(valid("A"), MSG37), "RoomCatalogService.validateRoomType"));
        cases.add(tc("RTM-010", "Code đúng min", "code=A1",
                "Chấp nhận code 2 ký tự.",
                () -> validateAccepted(valid("A1")), "RoomCatalogService.validateRoomType"));
        cases.add(tc("RTM-012", "Code vượt max", "code dài 61 ký tự",
                "Từ chối với thông báo: " + MSG37,
                () -> validateRejectedMessage(valid("A".repeat(61)), MSG37), "RoomCatalogService.validateRoomType"));
        cases.add(tc("RTM-013", "Code sai ký tự", "RT 01, RT.01, RT@01, HẠNG_01",
                "Từ chối tất cả với thông báo: " + MSG37,
                () -> combine(
                        validateRejectedMessage(valid("RT 01"), MSG37),
                        validateRejectedMessage(valid("RT.01"), MSG37),
                        validateRejectedMessage(valid("RT@01"), MSG37),
                        validateRejectedMessage(valid("HẠNG_01"), MSG37)),
                "RoomCatalogService.validateRoomType"));
        cases.add(tc("RTM-015", "Name bắt buộc", "name=null, rỗng, khoảng trắng",
                "Từ chối với thông báo: " + MSG02_NAME,
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
                "Từ chối với thông báo: " + MSG03_NAME,
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
        cases.add(tc("RTM-076", "Tạo thành công và chuẩn hóa dữ liệu",
                "code='  rtm-new  ', name='  Hạng mới  ', description='  Mô tả  '",
                "Tạo room type active; code=RTM-NEW, name=Hạng mới và description=Mô tả được lưu sau trim.", () -> {
                    ScriptedJdbc db = new ScriptedJdbc();
                    UUID id = UUID.fromString("50000000-0000-0000-0000-000000000001");
                    RoomTypeView expected = roomType(id, "RTM-NEW", "Hạng mới", true);
                    db.objects.add(0L);
                    db.queryRows.add(List.of(expected));
                    RoomTypeView actual = new RoomCatalogService(db).createRoomType(
                            command("  rtm-new  ", "  Hạng mới  ", "CINEMA", 4, "  Mô tả  "));
                    Object[] args = db.updates.get(0).args();
                    return check(actual.equals(expected) && db.updates.size() == 1
                                    && "RTM-NEW".equals(args[1]) && "Hạng mới".equals(args[2])
                                    && "Mô tả".equals(args[5]),
                            "code=" + args[1] + ", name=" + args[2] + ", description=" + args[5]);
                }, "RoomCatalogService.createRoomType"));
        cases.add(tc("RTM-077", "Mã đã tồn tại",
                "code=RT-QN; duplicate count=1",
                "Từ chối HTTP 409 với thông báo: Mã hạng phòng “RT-QN” đã tồn tại trong hệ thống. Vui lòng nhập mã khác.; không tạo record.", () -> {
                    ScriptedJdbc db = new ScriptedJdbc();
                    db.objects.add(1L);
                    return expectStatus(() -> new RoomCatalogService(db).createRoomType(valid("RT-QN")),
                            409, "Mã hạng phòng “RT-QN” đã tồn tại trong hệ thống. Vui lòng nhập mã khác.");
                }, "RoomCatalogService.createRoomType"));
        cases.add(tc("RTM-078", "Trùng mã phát sinh đồng thời",
                "Pre-check chưa thấy RTM-RACE; INSERT phát sinh DataIntegrityViolationException.",
                "Từ chối HTTP 409 với thông báo: Mã hạng phòng “RTM-RACE” đã tồn tại trong hệ thống. Vui lòng nhập mã khác.; không trả thành công giả.", () -> {
                    ScriptedJdbc db = new ScriptedJdbc();
                    db.objects.add(0L);
                    db.nextUpdateFailure = new DataIntegrityViolationException("duplicate race");
                    return expectStatus(() -> new RoomCatalogService(db).createRoomType(valid("RTM-RACE")),
                            409, "Mã hạng phòng “RTM-RACE” đã tồn tại trong hệ thống. Vui lòng nhập mã khác.");
                }, "RoomCatalogService.createRoomType"));
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


