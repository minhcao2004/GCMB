/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 29/10/2026
 * Description: Approved UC-27 DeleteComboPackage manual cases, retaining the spreadsheet CPM IDs.
 */
package com.gcmb.backend.manual.combopackagemanagement;

import java.util.List;
import static com.gcmb.backend.manual.combopackagemanagement.ComboPackageManagementManualRunner.*;

public final class DeleteComboPackageManualCases {
    private DeleteComboPackageManualCases() {}
    static List<ManualTest> cases() {
        return List.of(
                test("CPM-117", "UC-27 DeleteComboPackage",
                "Nguồn: UC-27_Delete_Combo_Package.docx; BR-118; Production: RoomCatalogService.deletePrice. Phương pháp kiểm thử: White-box.",
                "Recording JDBC chứa CIN_4H_QN active và không có tham chiếu nghiệp vụ.",
                                "Chọn xóa và xác nhận.",
                "RoomCatalogService.deletePrice xóa đúng combo được chọn và không xóa record không liên quan.",
                                () -> delete(false)),
                test("CPM-118", "UC-27 DeleteComboPackage",
                "Nguồn: UC-27_Delete_Combo_Package.docx; UC existing-record precondition; BR-118; Production: RoomCatalogService.deletePrice. Phương pháp kiểm thử: White-box.",
                "Recording JDBC chứa CIN_4H_QN inactive và không có tham chiếu nghiệp vụ.",
                                "Yêu cầu xóa I và xác nhận.",
                "RoomCatalogService.deletePrice xóa combo inactive khi không có tham chiếu nghiệp vụ.",
                                () -> delete(true)),
                test("CPM-125", "UC-27 DeleteComboPackage",
                                "Nguồn: UC-27_Delete_Combo_Package.docx; UC existing-record precondition; Flyway V3, V14; Production: RoomCatalogService.deletePrice; RoomCatalogService.price. Phương pháp kiểm thử: White-box.",
                                "Head Office Manager qlcc đã đăng nhập, phiên hợp lệ; CSRF hợp lệ khi ghi; hạng RT-QN/Queen và RT-KG/King active trong seed V3/V14; UUID 00000000-0000-0000-0000-000000000000 không tồn tại trong seed",
                                "Yêu cầu xóa combo không tồn tại.",
                                "Báo không tìm thấy; không xóa combo khác.",
                                () -> notFound(true))
        );
    }
}





