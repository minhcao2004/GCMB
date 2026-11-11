/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 09/11/2026
 * Description: Manual unit test cases for UC-10 View Branch List.
 */
package com.gcmb.backend.manual.branchmanagement;

import static com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.branch;
import static com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.check;
import static com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.expectStatus;
import static com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.test;

import com.gcmb.backend.branch.service.BranchAdministrationService;
import com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.ManualTest;
import com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.ScriptedJdbc;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ViewBranchListManualCases {
    private static final String UC = "UC-10 View Branch List";
    private static final UUID BRANCH_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    private ViewBranchListManualCases() {
    }

    public static List<ManualTest> cases() {
        return List.of(
                test("BRM-111", UC + " - Danh sách có dữ liệu", "BranchAdministrationService.list",
                        "Head Office Manager đã đăng nhập; hệ thống có hai cơ sở hiện hữu; thao tác chỉ đọc.",
                        "Gọi list() khi truy vấn trả hai cơ sở.",
                        "Trả đủ hai cơ sở, sắp xếp theo tên rồi ID và không thay đổi dữ liệu nghiệp vụ.", () -> {
                            ScriptedJdbc db = new ScriptedJdbc();
                            List<Map<String, Object>> rows = List.of(
                                    branch(BRANCH_ID, "ACTIVE"),
                                    branch(UUID.fromString("10000000-0000-0000-0000-000000000002"), "SUSPENDED"));
                            db.mapRows.add(rows);
                            List<Map<String, Object>> actual = new BranchAdministrationService(db).list();
                            return check(actual.equals(rows) && db.updates.isEmpty()
                                            && db.reads.get(0).sql().contains("ORDER BY b.name,b.id"),
                                    "rows=" + actual.size() + ", updates=" + db.updates.size());
                        }),
                test("BRM-112", UC + " - Xem chi tiết cơ sở", "BranchAdministrationService.get",
                        "Head Office Manager đã đăng nhập; cơ sở Hoa Bằng tồn tại.",
                        "Gọi get(branchId) với ID cơ sở Hoa Bằng.",
                        "Trả đúng thông tin hiện tại của cơ sở và không thay đổi bản ghi.", () -> {
                            ScriptedJdbc db = new ScriptedJdbc();
                            Map<String, Object> row = branch(BRANCH_ID, "ACTIVE");
                            db.mapRows.add(List.of(row));
                            Map<String, Object> actual = new BranchAdministrationService(db).get(BRANCH_ID);
                            return check(actual.equals(row) && db.updates.isEmpty(),
                                    "branchId=" + actual.get("branchId") + ", updates=" + db.updates.size());
                        }),
                test("BRM-113", UC + " - Cơ sở không tồn tại", "BranchAdministrationService.get",
                        "Head Office Manager đã đăng nhập; UUID cần xem không thuộc cơ sở nào.",
                        "Gọi get(unknownBranchId).",
                        "Từ chối HTTP 404 với thông báo: Không tìm thấy cơ sở.", () -> {
                            ScriptedJdbc db = new ScriptedJdbc();
                            db.mapRows.add(List.of());
                            return expectStatus(() -> new BranchAdministrationService(db).get(BRANCH_ID),
                                    404, "Không tìm thấy cơ sở.");
                        }),
                test("BRM-114", UC + " - Xem nhân sự đang hiệu lực", "BranchAdministrationService.staff",
                        "Head Office Manager đã đăng nhập; cơ sở Hoa Bằng tồn tại và có một phân công nhân sự còn hiệu lực.",
                        "Gọi staff(branchId).",
                        "Trả nhân sự có phân công hiệu lực tại cơ sở, sắp xếp theo mã nhân viên; không thay đổi dữ liệu.", () -> {
                            ScriptedJdbc db = new ScriptedJdbc();
                            List<Map<String, Object>> staff = List.of(Map.of(
                                    "id", UUID.fromString("20000000-0000-0000-0000-000000000001"),
                                    "code", "NV001", "fullName", "Nguyễn An", "status", "ACTIVE"));
                            db.mapRows.add(List.of(branch(BRANCH_ID, "ACTIVE")));
                            db.mapRows.add(staff);
                            List<Map<String, Object>> actual = new BranchAdministrationService(db).staff(BRANCH_ID);
                            return check(actual.equals(staff) && db.updates.isEmpty()
                                            && db.reads.get(1).sql().contains("valid_from<=CURRENT_TIMESTAMP")
                                            && db.reads.get(1).sql().contains("ORDER BY e.code"),
                                    "staff=" + actual.size() + ", updates=" + db.updates.size());
                        }),
                test("BRM-115", UC + " - Xem lịch sử cơ sở", "BranchAdministrationService.history",
                        "Head Office Manager đã đăng nhập; cơ sở Hoa Bằng tồn tại và có lịch sử audit.",
                        "Gọi history(branchId).",
                        "Trả lịch sử của cơ sở theo thời gian giảm dần, giữ nguyên dữ liệu nghiệp vụ.", () -> {
                            ScriptedJdbc db = new ScriptedJdbc();
                            List<Map<String, Object>> history = List.of(Map.of(
                                    "action", "UPDATE_BRANCH", "actor", "qlcc", "reason", ""));
                            db.mapRows.add(List.of(branch(BRANCH_ID, "ACTIVE")));
                            db.mapRows.add(history);
                            List<Map<String, Object>> actual = new BranchAdministrationService(db).history(BRANCH_ID);
                            return check(actual.equals(history) && db.updates.isEmpty()
                                            && db.reads.get(1).sql().contains("ORDER BY a.created_at DESC,a.id"),
                                    "history=" + actual.size() + ", updates=" + db.updates.size());
                        })
        );
    }
}
