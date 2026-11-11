/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 09/11/2026
 * Description: UC-24 ViewComboPackages manual cases for the updated combo quantities.
 */
package com.gcmb.backend.manual.combopackagemanagement;

import java.util.List;
import static com.gcmb.backend.manual.combopackagemanagement.ComboPackageManagementManualRunner.*;

public final class ViewComboPackagesManualCases {
    private ViewComboPackagesManualCases() {}

    static List<ManualTest> cases() {
        return List.of(
                test("CPM-136", "UC-24 ViewComboPackages",
                        "Nguồn: UC-24_View_Combo_Packages.docx; Flyway V3, V36, V37; Production: RoomCatalogService.prices. Phương pháp kiểm thử: White-box.",
                        "Head Office Manager qlcc đã đăng nhập, phiên hợp lệ; seed có CIN_C1_QN active với 2 đồ uống và 1 đồ ăn tặng kèm.",
                        "Mở danh sách combo, không lọc hạng phòng và không giới hạn trạng thái active.",
                        "Danh sách trả về CIN_C1_QN cùng số lượng đồ uống = 2 và đồ ăn = 1.",
                        ComboPackageManagementManualRunner::listWithQuantities),
                test("CPM-137", "UC-24 ViewComboPackages",
                        "Nguồn: UC-24_View_Combo_Packages.docx; Flyway V3, V36, V37; Production: RoomCatalogService.prices. Phương pháp kiểm thử: White-box.",
                        "Head Office Manager qlcc đã đăng nhập, phiên hợp lệ; có combo active của RT-QN và combo inactive của RT-KG.",
                        "Lọc danh sách theo RT-QN/Queen và chỉ lấy combo active.",
                        "Chỉ trả về combo active thuộc RT-QN; không lẫn combo inactive hoặc hạng khác.",
                        ComboPackageManagementManualRunner::listActiveByRoom),
                test("CPM-138", "UC-24 ViewComboPackages",
                        "Nguồn: UC-24_View_Combo_Packages.docx; MSG01; Production: RoomCatalogService.prices. Phương pháp kiểm thử: White-box.",
                        "Head Office Manager qlcc đã đăng nhập, phiên hợp lệ; không có combo phù hợp trong nguồn dữ liệu kiểm thử.",
                        "Mở danh sách combo với điều kiện không có bản ghi phù hợp.",
                        "Trả về danh sách rỗng để giao diện hiển thị “Không tìm thấy kết quả phù hợp. Hãy thử thay đổi từ khóa hoặc bộ lọc.”",
                        ComboPackageManagementManualRunner::listEmpty)
        );
    }
}
