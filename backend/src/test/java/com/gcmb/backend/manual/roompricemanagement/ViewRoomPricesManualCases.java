/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 09/11/2026
 * Description: Manual unit test cases for UC-20 View Room Prices.
 */
package com.gcmb.backend.manual.roompricemanagement;

import static com.gcmb.backend.manual.roompricemanagement.RoomPriceManagementManualRunner.check;
import static com.gcmb.backend.manual.roompricemanagement.RoomPriceManagementManualRunner.priceRule;
import static com.gcmb.backend.manual.roompricemanagement.RoomPriceManagementManualRunner.test;

import com.gcmb.backend.manual.roompricemanagement.RoomPriceManagementManualRunner.ManualTest;
import com.gcmb.backend.manual.roompricemanagement.RoomPriceManagementManualRunner.ScriptedJdbc;
import com.gcmb.backend.pricing.dto.PriceRuleView;
import com.gcmb.backend.pricing.service.RoomCatalogService;
import java.util.List;
import java.util.UUID;

public final class ViewRoomPricesManualCases {
    private static final String UC = "UC-20 View Room Prices";
    private static final String PRE = "Head Office Manager đã đăng nhập.";
    private static final UUID ROOM = UUID.fromString("00000000-0000-0000-0000-000000000101");

    private ViewRoomPricesManualCases() {
    }

    public static List<ManualTest> cases() {
        return List.of(
                tc("RPM-074", "Xem toàn bộ bảng giá",
                        "Yêu cầu danh sách không truyền roomTypeId và activeOnly=false.",
                        "Trả cả version active và inactive theo room type, code và version giảm dần; không thay đổi dữ liệu.", () -> {
                            ScriptedJdbc db = new ScriptedJdbc();
                            List<PriceRuleView> expected = List.of(
                                    priceRule(UUID.fromString("70000000-0000-0000-0000-000000000002"), "BG-QN", 2, "Giá mới", true),
                                    priceRule(UUID.fromString("70000000-0000-0000-0000-000000000001"), "BG-QN", 1, "Giá cũ", false));
                            db.queryResults.add(args -> expected);
                            List<PriceRuleView> actual = new RoomCatalogService(db).priceRules(null, false);
                            return check(actual.equals(expected) && db.updates.isEmpty()
                                            && db.reads.get(0).sql().contains("ORDER BY rt.name,p.code,p.version DESC"),
                                    "rows=" + actual.size() + ", updates=" + db.updates.size());
                        }),
                tc("RPM-075", "Lọc theo hạng phòng và trạng thái active",
                        "roomTypeId=RT-QN UUID; activeOnly=true.",
                        "Chỉ truy vấn rule active của RT-QN; không thay đổi dữ liệu.", () -> {
                            ScriptedJdbc db = new ScriptedJdbc();
                            db.queryResults.add(args -> List.of(priceRule(
                                    UUID.fromString("70000000-0000-0000-0000-000000000002"), "BG-QN", 2, "Giá mới", true)));
                            List<PriceRuleView> actual = new RoomCatalogService(db).priceRules(ROOM, true);
                            String sql = db.reads.get(0).sql();
                            return check(actual.size() == 1 && sql.contains("p.room_type_id=?")
                                            && sql.contains("p.active=true") && ROOM.equals(db.reads.get(0).args()[0])
                                            && db.updates.isEmpty(),
                                    "rows=" + actual.size() + ", filterArg=" + db.reads.get(0).args()[0]);
                        }),
                tc("RPM-076", "Không có dữ liệu phù hợp",
                        "Bộ lọc hợp lệ nhưng không có price rule khớp.",
                        "Trả danh sách rỗng; không thay đổi business record.", () -> {
                            ScriptedJdbc db = new ScriptedJdbc();
                            db.queryResults.add(args -> List.of());
                            List<PriceRuleView> actual = new RoomCatalogService(db).priceRules(ROOM, true);
                            return check(actual.isEmpty() && db.updates.isEmpty(),
                                    "rows=" + actual.size() + ", updates=" + db.updates.size());
                        })
        );
    }

    private static ManualTest tc(String id, String title, String input, String expected,
            RoomPriceManagementManualRunner.CheckedAction action) {
        return test(id, UC + " - " + title, "RoomCatalogService.priceRules",
                PRE, input, expected, action);
    }
}
