/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 28/10/2026
 * Description: Manual unit test cases for UC-21 Update Room Price.
 */
package com.gcmb.backend.manual.roompricemanagement;

import static com.gcmb.backend.manual.roompricemanagement.RoomPriceManagementManualRunner.combine;
import static com.gcmb.backend.manual.roompricemanagement.RoomPriceManagementManualRunner.check;
import static com.gcmb.backend.manual.roompricemanagement.RoomPriceManagementManualRunner.expectStatus;
import static com.gcmb.backend.manual.roompricemanagement.RoomPriceManagementManualRunner.priceRule;
import static com.gcmb.backend.manual.roompricemanagement.RoomPriceManagementManualRunner.test;
import static com.gcmb.backend.manual.roompricemanagement.RoomPriceManagementManualRunner.validateAccepted;
import static com.gcmb.backend.manual.roompricemanagement.RoomPriceManagementManualRunner.validateRejectedMessage;
import static com.gcmb.backend.manual.roompricemanagement.RoomPriceManagementManualRunner.validateRejectedStatus;

import com.gcmb.backend.manual.roompricemanagement.RoomPriceManagementManualRunner.ManualTest;
import com.gcmb.backend.manual.roompricemanagement.RoomPriceManagementManualRunner.ScriptedJdbc;
import com.gcmb.backend.pricing.dto.PriceRuleCommand;
import com.gcmb.backend.pricing.dto.PriceRuleView;
import com.gcmb.backend.pricing.service.RoomCatalogService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class UpdateRoomPriceManualCases {
    private static final String UC = "UC-21 Update Room Price";
    private static final String PRE = "Head Office Manager qlcc đã đăng nhập; BG-QN-HOURLY version 1 tồn tại và active theo Flyway V3/V4.";
    private static final UUID ROOM = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final String MSG49 = "Ngày kết thúc hiệu lực phải bằng hoặc sau ngày bắt đầu hiệu lực.";
    private static final String MSG50 = "Vui lòng chọn ít nhất một ngày trong tuần áp dụng mức giá này.";
    private static final String MSG51 = "Cấu hình giá bị trùng với Giá giờ lẻ Queen về hạng phòng, ngày áp dụng và khung giờ. Vui lòng điều chỉnh thời gian áp dụng.";

    private UpdateRoomPriceManualCases() {
    }

    public static List<ManualTest> cases() {
        return List.of(
                tc("RPM-055", "Thiếu trường bắt buộc", "Lần lượt name=null và hourlyPrice=null",
                                        "Từ chối name với thông báo: Vui lòng bổ sung Tên quy tắc giá.; từ chối price với thông báo: Vui lòng bổ sung Đơn giá.",
                                        () -> combine(validateRejectedMessage(command("BG-QN-HOURLY", null, ROOM, List.of(1), date(), null, 480, 1320, "96000"), "Vui lòng bổ sung Tên quy tắc giá."), validateRejectedMessage(command("BG-QN-HOURLY", "Giá", ROOM, List.of(1), date(), null, 480, 1320, null), "Vui lòng bổ sung Đơn giá.")), "RoomCatalogService.validatePriceRule"),
                tc("RPM-056", "Name đúng max", "Name dài 200 ký tự",
                                        "Validation chấp nhận.",
                                        () -> validateAccepted(command("BG-QN-HOURLY", "N".repeat(200), ROOM, List.of(1), date(), null, 480, 1320, "96000")), "RoomCatalogService.validatePriceRule"),
                tc("RPM-057", "Name vượt max", "Name dài 201 ký tự",
                                        "Từ chối với thông báo: Tên quy tắc giá chỉ được chứa tối đa 200 ký tự.",
                                        () -> validateRejectedMessage(command("BG-QN-HOURLY", "N".repeat(201), ROOM, List.of(1), date(), null, 480, 1320, "96000"), "Tên quy tắc giá chỉ được chứa tối đa 200 ký tự."), "RoomCatalogService.validatePriceRule"),
                tc("RPM-058", "Weekdays không hợp lệ", "[], [0], [8], [null]",
                                        "Từ chối; danh sách rỗng hiển thị: " + MSG50,
                                        () -> combine(validateRejectedMessage(withDays(List.of()), MSG50), validateRejectedStatus(withDays(List.of(0))), validateRejectedStatus(withDays(List.of(8))), validateRejectedStatus(withDays(Arrays.asList((Integer) null)))), "RoomCatalogService.validatePriceRule"),
                tc("RPM-059", "Thiếu validFrom", "validFrom=null",
                                        "Từ chối với thông báo: Vui lòng bổ sung Ngày bắt đầu.",
                                        () -> validateRejectedMessage(command("BG-QN-HOURLY", "Giá", ROOM, List.of(1), null, null, 480, 1320, "96000"), "Vui lòng bổ sung Ngày bắt đầu."), "RoomCatalogService.validatePriceRule"),
                tc("RPM-060", "validTo tùy chọn", "validTo=null",
                                        "Validation chấp nhận hiệu lực không giới hạn.",
                                        () -> validateAccepted(valid()), "RoomCatalogService.validatePriceRule"),
                tc("RPM-061", "Hiệu lực một ngày", "validTo=validFrom",
                                        "BR-146 cho phép update một ngày.",
                                        () -> validateAccepted(command("BG-QN-HOURLY", "Giá", ROOM, List.of(1), date(), date(), 480, 1320, "96000")), "RoomCatalogService.validatePriceRule"),
                tc("RPM-062", "Ngày kết thúc trước ngày bắt đầu", "validTo=validFrom-1",
                                        "Từ chối với thông báo: " + MSG49,
                                        () -> validateRejectedMessage(command("BG-QN-HOURLY", "Giá", ROOM, List.of(1), date(), date().minusDays(1), 480, 1320, "96000"), MSG49), "RoomCatalogService.validatePriceRule"),
                tc("RPM-063", "Biên thời gian hợp lệ", "00:00-00:01 và 23:59-24:00",
                                        "Validation chấp nhận.",
                                        () -> combine(validateAccepted(withTime(0, 1)), validateAccepted(withTime(1439, 1440))), "RoomCatalogService.validatePriceRule"),
                tc("RPM-064", "Khung giờ 24 giờ", "minuteFrom=minuteTo=480",
                                        "BR-148 cho phép và hiểu là 24 giờ.",
                                        () -> validateAccepted(withTime(480, 480)), "RoomCatalogService.validatePriceRule"),
                tc("RPM-066", "Thời gian ngoài miền", "from=-1/1440; to=-1/1441",
                                        "Từ chối từng giá trị với thông báo: Khung giờ áp dụng không hợp lệ.",
                                        () -> combine(validateRejectedStatus(withTime(-1, 1)), validateRejectedStatus(withTime(1440, 1)), validateRejectedStatus(withTime(0, -1)), validateRejectedStatus(withTime(0, 1441))), "RoomCatalogService.validatePriceRule"),
                tc("RPM-067", "Giá bằng 0", "hourlyPrice=0",
                                        "Validation chấp nhận.",
                                        () -> validateAccepted(withPrice("0")), "RoomCatalogService.validatePriceRule"),
                tc("RPM-068", "Giá âm", "hourlyPrice=-1",
                                        "Từ chối với thông báo: Đơn giá phải lớn hơn hoặc bằng 0.",
                                        () -> validateRejectedMessage(withPrice("-1"), "Đơn giá phải lớn hơn hoặc bằng 0."), "RoomCatalogService.validatePriceRule"),
                tc("RPM-069", "Giá thập phân", "hourlyPrice=96000.5",
                                        "Từ chối với thông báo: Đơn giá phải là số tiền nguyên đồng, không có phần thập phân.",
                                        () -> validateRejectedMessage(withPrice("96000.5"), "Đơn giá phải là số tiền nguyên đồng, không có phần thập phân."), "RoomCatalogService.validatePriceRule"),
                tc("RPM-077", "Tạo version mới và giữ lịch sử",
                                        "Rule BG-QN-HOURLY version 1 active; cập nhật name=Giá cập nhật.",
                                        "Version 1 chuyển inactive; tạo version 2 active với cùng code và dữ liệu mới.", () -> {
                                            UUID oldId = UUID.fromString("70000000-0000-0000-0000-000000000001");
                                            ScriptedJdbc db = new ScriptedJdbc();
                                            db.queryResults.add(args -> List.of(priceRule(oldId, "BG-QN-HOURLY", 1, "Giá cũ", true)));
                                            db.objects.add(2L);
                                            db.queryResults.add(args -> List.of(priceRule((UUID) args[0], "BG-QN-HOURLY", 2, "Giá cập nhật", true)));
                                            PriceRuleView actual = new RoomCatalogService(db).updatePriceRule(oldId, valid());
                                            return check(actual.version() == 2 && actual.active() && db.executeCalls == 1
                                                            && db.updates.size() == 1
                                                            && db.updates.get(0).sql().contains("SET active=false"),
                                                    "version=" + actual.version() + ", active=" + actual.active()
                                                            + ", oldUpdates=" + db.updates.size());
                                        }, "RoomCatalogService.updatePriceRule"),
                tc("RPM-078", "Không cho đổi mã", "Rule hiện có code=BG-QN-HOURLY; command code=BG-QN-NEW.",
                                        "Từ chối HTTP 400 với thông báo: Không thể đổi mã quy tắc khi đang sửa.; không tạo version mới.", () -> {
                                            UUID id = UUID.fromString("70000000-0000-0000-0000-000000000001");
                                            ScriptedJdbc db = new ScriptedJdbc();
                                            db.queryResults.add(args -> List.of(priceRule(id, "BG-QN-HOURLY", 1, "Giá cũ", true)));
                                            return expectStatus(() -> new RoomCatalogService(db).updatePriceRule(
                                                            id, command("BG-QN-NEW", "Giá", ROOM, List.of(1), date(), null, 480, 1320, "96000")),
                                                    400, "Không thể đổi mã quy tắc khi đang sửa.");
                                        }, "RoomCatalogService.updatePriceRule"),
                tc("RPM-079", "Rule không tồn tại", "UUID không thuộc price rule nào; command hợp lệ.",
                                        "Từ chối HTTP 404 với thông báo: Không tìm thấy quy tắc giá.; không tạo version thay thế.", () -> {
                                            ScriptedJdbc db = new ScriptedJdbc();
                                            db.queryResults.add(args -> List.of());
                                            UUID id = UUID.fromString("70000000-0000-0000-0000-000000000099");
                                            return expectStatus(() -> new RoomCatalogService(db).updatePriceRule(id, valid()),
                                                    404, "Không tìm thấy quy tắc giá.");
                                        }, "RoomCatalogService.updatePriceRule"),
                tc("RPM-080", "Version mới bị overlap",
                                        "Rule version 1 tồn tại; dữ liệu cập nhật trùng room type, ngày và khung giờ với rule active khác.",
                                        "Từ chối HTTP 409 với thông báo: " + MSG51 + "; version cũ vẫn active và không tạo version mới.", () -> {
                                            UUID oldId = UUID.fromString("70000000-0000-0000-0000-000000000001");
                                            ScriptedJdbc db = new ScriptedJdbc();
                                            db.queryResults.add(args -> List.of(priceRule(oldId, "BG-QN-HOURLY", 1, "Giá cũ", true)));
                                            db.objects.add(2L);
                                            db.queryResults.add(args -> List.of(priceRule((UUID) args[0], "BG-QN-HOURLY", 2, "Giá cập nhật", true)));
                                            return expectStatus(() -> new RoomCatalogService(db).updatePriceRule(oldId, valid()),
                                                    409, MSG51);
                                        }, "RoomCatalogService.updatePriceRule")
        );
    }

    private static ManualTest tc(String id, String title, String input, String expected,
            RoomPriceManagementManualRunner.CheckedAction action, String source) {
        return test(id, UC + " - " + title, source, PRE, input, expected, action);
    }

    private static LocalDate date() {
        return LocalDate.of(2026, 10, 28);
    }

    private static PriceRuleCommand valid() {
        return command("BG-QN-HOURLY", "Giá cập nhật", ROOM, List.of(1, 2, 3, 4, 5), date(), null, 480, 1320, "96000");
    }

    private static PriceRuleCommand withDays(List<Integer> days) {
        return command("BG-QN-HOURLY", "Giá", ROOM, days, date(), null, 480, 1320, "96000");
    }

    private static PriceRuleCommand withTime(Integer from, Integer to) {
        return command("BG-QN-HOURLY", "Giá", ROOM, List.of(1), date(), null, from, to, "96000");
    }

    private static PriceRuleCommand withPrice(String price) {
        return command("BG-QN-HOURLY", "Giá", ROOM, List.of(1), date(), null, 480, 1320, price);
    }

    private static PriceRuleCommand command(String code, String name, UUID roomTypeId,
            List<Integer> weekdays, LocalDate from, LocalDate to, Integer minuteFrom,
            Integer minuteTo, String price) {
        return new PriceRuleCommand(code, name, roomTypeId, weekdays, from, to,
                minuteFrom, minuteTo, price == null ? null : new BigDecimal(price));
    }
}




