/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 28/10/2026
 * Description: Manual unit test cases for UC-19 Create Room Price.
 */
package com.gcmb.backend.manual.roompricemanagement;

import static com.gcmb.backend.manual.roompricemanagement.RoomPriceManagementManualRunner.combine;
import static com.gcmb.backend.manual.roompricemanagement.RoomPriceManagementManualRunner.test;
import static com.gcmb.backend.manual.roompricemanagement.RoomPriceManagementManualRunner.validateAccepted;
import static com.gcmb.backend.manual.roompricemanagement.RoomPriceManagementManualRunner.validateRejectedMessage;
import static com.gcmb.backend.manual.roompricemanagement.RoomPriceManagementManualRunner.validateRejectedStatus;

import com.gcmb.backend.manual.roompricemanagement.RoomPriceManagementManualRunner.ManualTest;
import com.gcmb.backend.pricing.dto.PriceRuleCommand;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class CreateRoomPriceManualCases {
    private static final String UC = "UC-19 Create Room Price";
    private static final String SERVICE = "src/main/java/com/gcmb/backend/pricing/service/RoomCatalogService.java";
    private static final String CONTROLLER = "src/main/java/com/gcmb/backend/pricing/controller/RoomCatalogController.java";
    private static final String SECURITY = "src/main/java/com/gcmb/backend/auth/security/AuthSecurity.java";
    private static final String PAGE = "../frontend/src/features/pricing/pages/RoomCatalogPage.js";
    private static final String V1 = "src/main/resources/db/migration/V1__initial_schema.sql";
    private static final String PRE = "Head Office Manager qlcc đã đăng nhập; RT-QN và dữ liệu Flyway V3/V4 tồn tại theo điều kiện test case.";
    private static final UUID ROOM = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final String MSG02_NAME = "Vui lòng bổ sung Tên quy tắc giá.";
    private static final String MSG03_NAME = "Tên quy tắc giá chỉ được chứa tối đa 200 ký tự.";
    private static final String MSG37 = "Mã quy tắc giá phải có từ 2 đến 60 ký tự và chỉ được dùng chữ cái không dấu, chữ số, dấu gạch dưới (_) hoặc dấu gạch nối (-).";
    private static final String MSG45 = "Đơn giá phải lớn hơn hoặc bằng 0.";
    private static final String MSG47 = "Đơn giá phải là số nguyên VND.";
    private static final String MSG49 = "Ngày kết thúc phải bằng hoặc sau ngày bắt đầu.";
    private static final String MSG50 = "Vui lòng chọn ít nhất một ngày áp dụng.";

    private CreateRoomPriceManualCases() {
    }

    public static List<ManualTest> cases() {
        return List.of(
                tc("RPM-005", "Biên code hợp lệ", "Code dài 2 và 60 ký tự",
                                        "Chấp nhận cả hai giá trị biên.",
                                        () -> combine(validateAccepted(valid("A1")), validateAccepted(valid("A".repeat(60)))), "RoomCatalogService.validatePriceRule"),
                tc("RPM-006", "Biên code không hợp lệ", "Code dài 1 và 61 ký tự",
                                        "Từ chối từng giá trị bằng MSG37.",
                                        () -> combine(validateRejectedMessage(valid("A"), MSG37), validateRejectedMessage(valid("A".repeat(61)), MSG37)), "RoomCatalogService.validatePriceRule"),
                tc("RPM-007", "Code sai ký tự", "A A; HẠNG; A@A",
                                        "Từ chối từng code bằng MSG37.",
                                        () -> combine(validateRejectedMessage(valid("A A"), MSG37), validateRejectedMessage(valid("HẠNG"), MSG37), validateRejectedMessage(valid("A@A"), MSG37)), "RoomCatalogService.validatePriceRule"),
                tc("RPM-008", "Name bắt buộc", "Name null, rỗng, khoảng trắng",
                                        "Từ chối bằng MSG02.",
                                        () -> combine(validateRejectedMessage(command("QN01", null, ROOM, List.of(1), date(), null, 480, 1320, "96000"), MSG02_NAME),
                                                validateRejectedMessage(command("QN01", "", ROOM, List.of(1), date(), null, 480, 1320, "96000"), MSG02_NAME),
                                                validateRejectedMessage(command("QN01", "   ", ROOM, List.of(1), date(), null, 480, 1320, "96000"), MSG02_NAME)), "RoomCatalogService.validatePriceRule"),
                tc("RPM-009", "Name đúng max", "Name dài 200 ký tự",
                                        "Chấp nhận đủ 200 ký tự.",
                                        () -> validateAccepted(command("QN01", "N".repeat(200), ROOM, List.of(1), date(), null, 480, 1320, "96000")), "RoomCatalogService.validatePriceRule"),
                tc("RPM-010", "Name vượt max", "Name dài 201 ký tự",
                                        "Từ chối bằng MSG03.",
                                        () -> validateRejectedMessage(command("QN01", "N".repeat(201), ROOM, List.of(1), date(), null, 480, 1320, "96000"), MSG03_NAME), "RoomCatalogService.validatePriceRule"),
                tc("RPM-013", "Weekdays hợp lệ", "[1] và [1,2,3,4,5,6,7]",
                                        "Chấp nhận một ngày và đủ bảy ngày.",
                                        () -> combine(validateAccepted(withDays(List.of(1))), validateAccepted(withDays(List.of(1, 2, 3, 4, 5, 6, 7)))), "RoomCatalogService.validatePriceRule"),
                tc("RPM-014", "Weekdays bắt buộc", "null và []",
                                        "Từ chối bằng MSG50.",
                                        () -> combine(validateRejectedMessage(withDays(null), MSG50), validateRejectedMessage(withDays(List.of()), MSG50)), "RoomCatalogService.validatePriceRule"),
                tc("RPM-015", "Weekdays ngoài miền", "[0], [8], [null]",
                                        "Từ chối từng dữ liệu.",
                                        () -> combine(validateRejectedStatus(withDays(List.of(0))), validateRejectedStatus(withDays(List.of(8))), validateRejectedStatus(withDays(java.util.Arrays.asList((Integer) null)))), "RoomCatalogService.validatePriceRule"),
                tc("RPM-016", "validFrom bắt buộc", "validFrom=null",
                                        "Từ chối bằng MSG02.",
                                        () -> validateRejectedMessage(command("QN01", "Giá", ROOM, List.of(1), null, null, 480, 1320, "96000"), "Vui lòng bổ sung Ngày bắt đầu."), "RoomCatalogService.validatePriceRule"),
                tc("RPM-017", "Không giới hạn ngày kết thúc", "validTo=null",
                                        "Validation chấp nhận.",
                                        () -> validateAccepted(valid("QN01")), "RoomCatalogService.validatePriceRule"),
                tc("RPM-018", "Hiệu lực một ngày", "validTo=validFrom",
                                        "BR-146 cho phép cấu hình một ngày.",
                                        () -> validateAccepted(command("QN01", "Giá", ROOM, List.of(1), date(), date(), 480, 1320, "96000")), "RoomCatalogService.validatePriceRule"),
                tc("RPM-019", "Ngày kết thúc trước ngày bắt đầu", "validTo=validFrom-1",
                                        "Từ chối bằng MSG49.",
                                        () -> validateRejectedMessage(command("QN01", "Giá", ROOM, List.of(1), date(), date().minusDays(1), 480, 1320, "96000"), MSG49), "RoomCatalogService.validatePriceRule"),
                tc("RPM-020", "Biên thời gian hợp lệ", "00:00-00:01 và 23:59-24:00",
                                        "Chấp nhận cả hai cửa sổ.",
                                        () -> combine(validateAccepted(withTime(0, 1)), validateAccepted(withTime(1439, 1440))), "RoomCatalogService.validatePriceRule"),
                tc("RPM-021", "Thời gian ngoài miền", "minuteFrom=-1/1440; minuteTo=0/1441",
                                        "Từ chối từng giá trị.",
                                        () -> combine(validateRejectedStatus(withTime(-1, 1)), validateRejectedStatus(withTime(1440, 1)), validateRejectedStatus(withTime(0, 0)), validateRejectedStatus(withTime(0, 1441))), "RoomCatalogService.validatePriceRule"),
                tc("RPM-022", "Khung giờ 24 giờ", "minuteFrom=minuteTo=480",
                                        "BR-148 hiểu là 24 giờ và chấp nhận.",
                                        () -> validateAccepted(withTime(480, 480)), "RoomCatalogService.validatePriceRule"),
                tc("RPM-024", "Giá bằng 0", "hourlyPrice=0",
                                        "Chấp nhận price=0.",
                                        () -> validateAccepted(withPrice("0")), "RoomCatalogService.validatePriceRule"),
                tc("RPM-025", "Giá âm", "hourlyPrice=-1",
                                        "Từ chối bằng MSG45.",
                                        () -> validateRejectedMessage(withPrice("-1"), MSG45), "RoomCatalogService.validatePriceRule"),
                tc("RPM-026", "Giá có phần thập phân", "hourlyPrice=96000.5",
                                        "Từ chối bằng MSG47.",
                                        () -> validateRejectedMessage(withPrice("96000.5"), MSG47), "RoomCatalogService.validatePriceRule"),
                tc("RPM-030", "Không overlap", "Không giao ít nhất một chiều ngày/weekday/time",
                                        "Validation chấp nhận command hợp lệ.",
                                        () -> validateAccepted(valid("QN-NON-OVERLAP")), "RoomCatalogService.validatePriceRule")
        );
    }

    private static ManualTest tc(String id, String title, String input, String expected,
            RoomPriceManagementManualRunner.CheckedAction action, String source) {
        return test(id, UC + " - " + title, source, PRE, input, expected, action);
    }

    private static LocalDate date() {
        return LocalDate.of(2026, 10, 28);
    }

    private static PriceRuleCommand valid(String code) {
        return command(code, "Giá kiểm thử", ROOM, List.of(1, 2, 3, 4, 5), date(), null, 480, 1320, "96000");
    }

    private static PriceRuleCommand withDays(List<Integer> days) {
        return command("QN01", "Giá", ROOM, days, date(), null, 480, 1320, "96000");
    }

    private static PriceRuleCommand withTime(Integer from, Integer to) {
        return command("QN01", "Giá", ROOM, List.of(1), date(), null, from, to, "96000");
    }

    private static PriceRuleCommand withPrice(String price) {
        return command("QN01", "Giá", ROOM, List.of(1), date(), null, 480, 1320, price);
    }

    private static PriceRuleCommand command(String code, String name, UUID roomTypeId,
            List<Integer> weekdays, LocalDate from, LocalDate to, Integer minuteFrom,
            Integer minuteTo, String price) {
        return new PriceRuleCommand(code, name, roomTypeId, weekdays, from, to,
                minuteFrom, minuteTo, price == null ? null : new BigDecimal(price));
    }
}




