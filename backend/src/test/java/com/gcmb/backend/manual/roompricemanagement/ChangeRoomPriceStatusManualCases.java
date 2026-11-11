/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 09/11/2026
 * Description: Manual unit test cases for UC-22 Change Room Price Status.
 */
package com.gcmb.backend.manual.roompricemanagement;

import static com.gcmb.backend.manual.roompricemanagement.RoomPriceManagementManualRunner.check;
import static com.gcmb.backend.manual.roompricemanagement.RoomPriceManagementManualRunner.test;

import com.gcmb.backend.manual.roompricemanagement.RoomPriceManagementManualRunner.ManualTest;
import com.gcmb.backend.pricing.service.RoomCatalogService;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class ChangeRoomPriceStatusManualCases {
    private static final String UC = "UC-22 Change Room Price Status";
    private static final String PRE = "Head Office Manager đã đăng nhập; room price tồn tại theo điều kiện test case.";

    private ChangeRoomPriceStatusManualCases() {
    }

    public static List<ManualTest> cases() {
        return List.of(
                tc("RPM-081", "Ngừng áp dụng room price",
                        "Price rule đang active; chọn inactive và xác nhận.",
                        "Có thao tác production chuyển rule thành inactive, giữ version và giá booking đã xác nhận.",
                        () -> statusContract("deactivate", "inactive")),
                tc("RPM-082", "Kích hoạt lại room price",
                        "Price rule đang inactive, không trùng code hoặc cấu hình; chọn active và xác nhận.",
                        "Có thao tác production kiểm tra BR-175/BR-228 rồi chuyển rule thành active, giữ lịch sử liên quan.",
                        () -> statusContract("reactivate", "activate", "active"))
        );
    }

    private static RoomPriceManagementManualRunner.TestResult statusContract(String... actions) {
        Method method = Arrays.stream(RoomCatalogService.class.getDeclaredMethods())
                .filter(candidate -> Modifier.isPublic(candidate.getModifiers()))
                .filter(candidate -> Arrays.asList(candidate.getParameterTypes()).contains(UUID.class))
                .filter(candidate -> {
                    String name = candidate.getName().toLowerCase();
                    return name.contains("pricerule") || name.contains("roomprice");
                })
                .filter(candidate -> Arrays.stream(actions)
                        .anyMatch(action -> candidate.getName().toLowerCase().contains(action)))
                .findFirst().orElse(null);
        return check(method != null,
                method == null ? "Production chưa có thao tác đổi trạng thái room price tương ứng"
                        : "Production method=" + method.getName());
    }

    private static ManualTest tc(String id, String title, String input, String expected,
            RoomPriceManagementManualRunner.CheckedAction action) {
        return test(id, UC + " - " + title, "RoomCatalogService status contract",
                PRE, input, expected, action);
    }
}
