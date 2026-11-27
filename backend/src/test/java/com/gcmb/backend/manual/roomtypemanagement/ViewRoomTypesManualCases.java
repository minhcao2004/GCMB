package com.gcmb.backend.manual.roomtypemanagement;

import java.util.List;
import com.gcmb.backend.manual.support.*;

/** Manual service UT. Results are reviewed by the tester. */
public final class ViewRoomTypesManualCases {
    private ViewRoomTypesManualCases() {}
    public static List<ManualCase> cases() {
        return List.of(
            new ManualCase("RTM-018", "roomtypemanagement", "UC-15 View Room Types - Danh sách có dữ liệu", "Quản lý cấp cao \"qlcc\" đã đăng nhập. Phạm vi toàn chuỗi.\nHạng phòng \"RT-QN\" tồn tại: đang sử dụng \"true\", sức chứa/số chỗ \"4\".\nHạng phòng \"RT-MUSIC\" tồn tại: đang sử dụng \"true\", sức chứa/số chỗ \"4\".\nRunner chuẩn bị các trạng thái đã nêu trước khi thực hiện nghiệp vụ; ghi nhận kết quả và trạng thái dữ liệu trước rollback. Sau case, toàn bộ thay đổi được rollback.", "Tìm kiếm/xem hạng phòng trong phạm vi quyền của tài khoản đã đăng nhập.", "Trả đủ mã, tên, loại dịch vụ, sức chứa, mô tả và trạng thái theo code tăng dần; không thay đổi dữ liệu.", List.of("UC-15"), List.of(new MethodTarget("com.gcmb.backend.pricing.service.RoomCatalogService", "roomTypes", List.of())), "UT", "backend/src/test/java/com/gcmb/backend/manual/roomtypemanagement/ViewRoomTypesManualCases.java")
        );
    }
}
