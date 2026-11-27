package com.gcmb.backend.manual.roompricemanagement;

import java.util.List;
import com.gcmb.backend.manual.support.*;

/** Manual service UT. Results are reviewed by the tester. */
public final class ViewRoomPricesManualCases {
    private ViewRoomPricesManualCases() {}
    public static List<ManualCase> cases() {
        return List.of(
            new ManualCase("RPM-025", "roompricemanagement", "UC-20 View Room Prices - Xem toàn bộ bảng giá", "Quản lý cấp cao \"qlcc\" đã đăng nhập. Phạm vi toàn chuỗi.\nHạng phòng \"RT-QN\" tồn tại: đang sử dụng \"true\", sức chứa/số chỗ \"4\".\nRunner chuẩn bị các trạng thái đã nêu trước khi thực hiện nghiệp vụ; ghi nhận kết quả và trạng thái dữ liệu trước rollback. Sau case, toàn bộ thay đổi được rollback.", "Tìm kiếm/xem quy tắc giá:\nhạng phòng = không truyền giá trị;\nactive only = \"false\".", "Trả cả version active và inactive theo room type, code và version giảm dần; không thay đổi dữ liệu.", List.of("UC-20"), List.of(new MethodTarget("com.gcmb.backend.pricing.service.RoomCatalogService", "priceRules", List.of("java.util.UUID", "boolean"))), "UT", "backend/src/test/java/com/gcmb/backend/manual/roompricemanagement/ViewRoomPricesManualCases.java"),
            new ManualCase("RPM-026", "roompricemanagement", "UC-20 View Room Prices - Lọc theo hạng phòng và trạng thái active", "Quản lý cấp cao \"qlcc\" đã đăng nhập. Phạm vi toàn chuỗi.\nHạng phòng \"RT-QN\" tồn tại: đang sử dụng \"true\", sức chứa/số chỗ \"4\".\nRunner chuẩn bị các trạng thái đã nêu trước khi thực hiện nghiệp vụ; ghi nhận kết quả và trạng thái dữ liệu trước rollback. Sau case, toàn bộ thay đổi được rollback.", "Tìm kiếm/xem quy tắc giá:\nhạng phòng = \"RT-QN\";\nactive only = \"true\".\nĐịnh danh đối chiếu: RT-QN có ID \"55e8cdc6-ed6b-407b-873b-c49841aa4730\".", "Chỉ trả quy tắc giá active của hạng phòng đã chuẩn bị đã chọn; không thay đổi dữ liệu.", List.of("UC-20"), List.of(new MethodTarget("com.gcmb.backend.pricing.service.RoomCatalogService", "priceRules", List.of("java.util.UUID", "boolean"))), "UT", "backend/src/test/java/com/gcmb/backend/manual/roompricemanagement/ViewRoomPricesManualCases.java"),
            new ManualCase("RPM-027", "roompricemanagement", "UC-20 View Room Prices - Không có dữ liệu phù hợp", "Quản lý cấp cao \"qlcc\" đã đăng nhập. Phạm vi toàn chuỗi.\nHạng phòng \"RT-QN\" tồn tại: đang sử dụng \"true\", sức chứa/số chỗ \"4\".\nRunner chuẩn bị các trạng thái đã nêu trước khi thực hiện nghiệp vụ; ghi nhận kết quả và trạng thái dữ liệu trước rollback. Sau case, toàn bộ thay đổi được rollback.", "Tìm kiếm/xem quy tắc giá:\nhạng phòng = \"RT-MUSIC\";\nactive only = \"true\".\nĐịnh danh đối chiếu: RT-MUSIC có ID \"67a13503-e5d5-43fc-80dc-8b6f917c1ef0\".", "Trả danh sách rỗng; không thay đổi business record.", List.of("UC-20"), List.of(new MethodTarget("com.gcmb.backend.pricing.service.RoomCatalogService", "priceRules", List.of("java.util.UUID", "boolean"))), "UT", "backend/src/test/java/com/gcmb/backend/manual/roompricemanagement/ViewRoomPricesManualCases.java")
        );
    }
}
