package com.gcmb.backend.manual.foodservicemanagement;

import java.util.List;
import com.gcmb.backend.manual.support.*;

/** Manual service UT. Results are reviewed by the tester. */
public final class ViewBookingItemsManualCases {
    private ViewBookingItemsManualCases() {}
    public static List<ManualCase> cases() {
        return List.of(
            new ManualCase("FSM-045", "foodservicemanagement", "UC-52 View Booking Items", "Lễ tân \"builanhan\" đã đăng nhập. Phạm vi cơ sở \"HN_HOA_BANG\".\nMón \"dd862928-a379-3120-94c1-776b21994e8b\" tồn tại: đang sử dụng \"true\".\nTồn kho \"715635ef-f96b-3210-b91a-d02c433e9a9b\" tồn tại: số lượng \"98.0\", phiên bản \"2\".\nRunner chuẩn bị các trạng thái đã nêu trước khi thực hiện nghiệp vụ; ghi nhận kết quả và trạng thái dữ liệu trước rollback. Sau case, toàn bộ thay đổi được rollback.", "Tìm kiếm/xem món và đơn phục vụ:\ntrạng thái = \"ALL\".", "Trả đơn đã chuẩn bị ACCEPTED thuộc đúng cơ sở actor và giữ nguyên order/kho/charge.", List.of("UC-52"), List.of(new MethodTarget("com.gcmb.backend.inventory.service.FoodOrderService", "list", List.of("com.gcmb.backend.auth.dto.Identity", "java.lang.String"))), "UT", "backend/src/test/java/com/gcmb/backend/manual/foodservicemanagement/ViewBookingItemsManualCases.java"),
            new ManualCase("FSM-046", "foodservicemanagement", "UC-52 View Booking Items", "Lễ tân \"builanhan\" đã đăng nhập. Phạm vi cơ sở \"HN_HOA_BANG\".\nMón \"1e982497-b395-357a-9ce9-916fc0a10b03\" tồn tại: đang sử dụng \"true\".\nTồn kho \"70368744-c808-323f-99d2-835b8367a816\" tồn tại: số lượng \"100.0\", phiên bản \"1\".\nRunner chuẩn bị các trạng thái đã nêu trước khi thực hiện nghiệp vụ; ghi nhận kết quả và trạng thái dữ liệu trước rollback. Sau case, toàn bộ thay đổi được rollback.", "Tìm kiếm/xem món và đơn phục vụ:\ntrạng thái = \"ALL\".", "Trả danh sách rỗng khi chưa có đơn đã chuẩn bị.", List.of("UC-52"), List.of(new MethodTarget("com.gcmb.backend.inventory.service.FoodOrderService", "list", List.of("com.gcmb.backend.auth.dto.Identity", "java.lang.String"))), "UT", "backend/src/test/java/com/gcmb/backend/manual/foodservicemanagement/ViewBookingItemsManualCases.java"),
            new ManualCase("FSM-047", "foodservicemanagement", "UC-52 View Booking Items", "Lễ tân \"builanhan\" đã đăng nhập. Phạm vi cơ sở \"HN_HOA_BANG\".\nMón \"ccd1e4a0-e53c-3490-9911-7f7726498ea9\" tồn tại: đang sử dụng \"true\".\nTồn kho \"bb9b98da-6efe-3917-a707-20803f0701da\" tồn tại: số lượng \"98.0\", phiên bản \"2\".\nRunner chuẩn bị các trạng thái đã nêu trước khi thực hiện nghiệp vụ; ghi nhận kết quả và trạng thái dữ liệu trước rollback. Sau case, toàn bộ thay đổi được rollback.", "Tìm kiếm/xem món và đơn phục vụ:\ntrạng thái = \"ALL\".", "Trả đơn đã chuẩn bị ACCEPTED thuộc đúng cơ sở actor và giữ nguyên order/kho/charge.", List.of("UC-52"), List.of(new MethodTarget("com.gcmb.backend.inventory.service.FoodOrderService", "list", List.of("com.gcmb.backend.auth.dto.Identity", "java.lang.String"))), "UT", "backend/src/test/java/com/gcmb/backend/manual/foodservicemanagement/ViewBookingItemsManualCases.java")
        );
    }
}
