/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 09/11/2026
 * Description: Manual unit test cases for UC-15 View Room Types.
 */
package com.gcmb.backend.manual.roomtypemanagement;

import static com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.check;
import static com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.roomType;
import static com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.test;

import com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.ManualTest;
import com.gcmb.backend.manual.roomtypemanagement.RoomTypeManagementManualRunner.ScriptedJdbc;
import com.gcmb.backend.pricing.dto.RoomTypeView;
import com.gcmb.backend.pricing.service.RoomCatalogService;
import java.util.List;
import java.util.UUID;

public final class ViewRoomTypesManualCases {
    private static final String UC = "UC-15 View Room Types";

    private ViewRoomTypesManualCases() {
    }

    public static List<ManualTest> cases() {
        return List.of(
                test("RTM-079", UC + " - Danh sách có dữ liệu", "RoomCatalogService.roomTypes",
                        "Head Office Manager đã đăng nhập; danh mục có room type active và inactive.",
                        "Gọi roomTypes() khi truy vấn trả RT-KG và RT-QN.",
                        "Trả đủ mã, tên, loại dịch vụ, sức chứa, mô tả, trạng thái theo thứ tự code tăng dần; không thay đổi dữ liệu.", () -> {
                            ScriptedJdbc db = new ScriptedJdbc();
                            List<RoomTypeView> expected = List.of(
                                    roomType(UUID.fromString("50000000-0000-0000-0000-000000000003"), "RT-KG", "King", false),
                                    roomType(UUID.fromString("50000000-0000-0000-0000-000000000004"), "RT-QN", "Queen", true));
                            db.queryRows.add(expected);
                            List<RoomTypeView> actual = new RoomCatalogService(db).roomTypes();
                            return check(actual.equals(expected) && db.updates.isEmpty()
                                            && db.reads.get(0).sql().contains("ORDER BY code"),
                                    "rows=" + actual.size() + ", updates=" + db.updates.size());
                        }),
                test("RTM-080", UC + " - Danh sách rỗng", "RoomCatalogService.roomTypes",
                        "Head Office Manager đã đăng nhập; không có room type nào khớp yêu cầu xem.",
                        "Gọi roomTypes() khi truy vấn trả danh sách rỗng.",
                        "Trả danh sách rỗng; không thay đổi business record.", () -> {
                            ScriptedJdbc db = new ScriptedJdbc();
                            db.queryRows.add(List.of());
                            List<RoomTypeView> actual = new RoomCatalogService(db).roomTypes();
                            return check(actual.isEmpty() && db.updates.isEmpty(),
                                    "rows=" + actual.size() + ", updates=" + db.updates.size());
                        })
        );
    }
}
