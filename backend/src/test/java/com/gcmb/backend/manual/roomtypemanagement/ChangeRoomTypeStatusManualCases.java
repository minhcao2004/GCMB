package com.gcmb.backend.manual.roomtypemanagement;

import java.util.List;
import com.gcmb.backend.manual.support.*;

/** Manual service UT. Results are reviewed by the tester. */
public final class ChangeRoomTypeStatusManualCases {
    private ChangeRoomTypeStatusManualCases() {}
    public static List<ManualCase> cases() {
        return List.of(
            new ManualCase("RTM-022", "roomtypemanagement", "UC-17 Change Room Type Status - Ngừng sử dụng thành công", "Quản lý cấp cao \"qlcc\" đã đăng nhập. Phạm vi toàn chuỗi.\nHạng phòng \"RT-QN\" tồn tại: đang sử dụng \"true\", sức chứa/số chỗ \"4\".\nHạng phòng \"RT-MUSIC\" tồn tại: đang sử dụng \"true\", sức chứa/số chỗ \"4\".\nRunner chuẩn bị các trạng thái đã nêu trước khi thực hiện nghiệp vụ; ghi nhận kết quả và trạng thái dữ liệu trước rollback. Sau case, toàn bộ thay đổi được rollback.", "Tìm kiếm/xem hạng phòng:\nmã định danh = \"RT-QN\".\nĐịnh danh đối chiếu: RT-QN có ID \"55e8cdc6-ed6b-407b-873b-c49841aa4730\".", "Chuyển trạng thái thành inactive; giữ nguyên dữ liệu hạng phòng và lịch sử liên quan.", List.of("UC-17"), List.of(new MethodTarget("com.gcmb.backend.pricing.service.RoomCatalogService", "deactivateRoomType", List.of("java.util.UUID"))), "UT", "backend/src/test/java/com/gcmb/backend/manual/roomtypemanagement/ChangeRoomTypeStatusManualCases.java"),
            new ManualCase("RTM-023", "roomtypemanagement", "UC-17 Change Room Type Status - Hạng phòng không tồn tại", "Quản lý cấp cao \"qlcc\" đã đăng nhập. Phạm vi toàn chuỗi.\nHạng phòng \"RT-QN\" tồn tại: đang sử dụng \"true\", sức chứa/số chỗ \"4\".\nHạng phòng \"RT-MUSIC\" tồn tại: đang sử dụng \"true\", sức chứa/số chỗ \"4\".\nRunner chuẩn bị các trạng thái đã nêu trước khi thực hiện nghiệp vụ; ghi nhận kết quả và trạng thái dữ liệu trước rollback. Sau case, toàn bộ thay đổi được rollback.", "Tìm kiếm/xem hạng phòng:\nmã định danh = \"49c99a3f-6c41-3476-9fce-8ccebe222a49\".", "Từ chối với thông báo: Không tìm thấy hạng phòng.; không thay đổi dữ liệu.", List.of("UC-17"), List.of(new MethodTarget("com.gcmb.backend.pricing.service.RoomCatalogService", "deactivateRoomType", List.of("java.util.UUID"))), "UT", "backend/src/test/java/com/gcmb/backend/manual/roomtypemanagement/ChangeRoomTypeStatusManualCases.java")
        );
    }
}
