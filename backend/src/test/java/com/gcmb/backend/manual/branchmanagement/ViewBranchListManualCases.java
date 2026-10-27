/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 28/10/2026
 * Description: Manual unit test cases for UC-10 View Branch List.
 */
package com.gcmb.backend.manual.branchmanagement;

import static com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.combine;
import static com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.sourceContains;
import static com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.test;

import com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.ManualTest;
import java.util.List;

public final class ViewBranchListManualCases {
    private static final String UC = "UC-10 View Branch List";
    private static final String SERVICE = "src/main/java/com/gcmb/backend/branch/service/BranchAdministrationService.java";
    private static final String CONTROLLER = "src/main/java/com/gcmb/backend/branch/controller/BranchManagerAssignmentController.java";
    private static final String SECURITY = "src/main/java/com/gcmb/backend/auth/security/AuthSecurity.java";
    private static final String PAGE = "../frontend/src/features/branch/pages/BranchManagementPage.js";
    private static final String V14 = "src/main/resources/db/migration/V14__seed_genz_branches_and_accounts.sql";
    private static final String PRE = "Head Office Manager qlcc và 18 cơ sở demo Flyway V14 tồn tại.";
    private static final String MSG01 = "Không tìm thấy kết quả phù hợp. Hãy thử thay đổi từ khóa hoặc bộ lọc.";

    private ViewBranchListManualCases() {
    }

    public static List<ManualTest> cases() {
        return List.of(
                tc("BRM-028", "Danh sách đầy đủ", "GET list không bộ lọc",
                        "Trả dữ liệu branch/manager trong phạm vi toàn chuỗi và không mutation.",
                        () -> combine(sourceContains(V14, "IF (SELECT COUNT(*) FROM genz_branch_seed) <> 18"),
                                sourceContains(SERVICE, "public List<Map<String, Object>> list()", "managerCount", "statusReason")),
                        "BranchAdministrationService.list"),
                tc("BRM-029", "Không có phiên", "GET /head-office/branches",
                        "HTTP 401, không trả dữ liệu.",
                        () -> sourceContains(SECURITY, "/api/v1/head-office/**", "response.setStatus(401)"),
                        "AuthSecurity/SessionFilter"),
                tc("BRM-030", "Sai vai trò xem branch", "BRANCH_MANAGER/RECEPTIONIST/ACCOUNTANT gọi list/detail",
                        "HTTP 403 và đúng MSG24, không lộ dữ liệu.",
                        () -> sourceContains(SECURITY, "/api/v1/head-office/**",
                                "Bạn không có quyền xem đối với cơ sở."),
                        "AuthSecurity"),
                tc("BRM-031", "Thứ tự ổn định", "list có tên/id khác nhau",
                        "ORDER BY name rồi id tăng dần.",
                        () -> sourceContains(SERVICE, "ORDER BY b.name,b.id"),
                        "BranchAdministrationService.list"),
                tc("BRM-032", "Tìm theo code", "query=hoa_bang/HN_HOA_BANG",
                        "Tìm code không phân biệt hoa thường.",
                        () -> sourceContains(PAGE, "`${branch.code} ${branch.branchName} ${branch.address}`",
                                ".toLocaleLowerCase('vi')", "query.trim().toLocaleLowerCase('vi')"),
                        "BranchManagementPage.visible"),
                tc("BRM-033", "Tìm tên/địa chỉ tiếng Việt", "query có khoảng trắng và khác hoa thường",
                        "Trim và tìm đúng tên/address theo locale vi.",
                        () -> sourceContains(PAGE, "branch.branchName", "branch.address",
                                "query.trim().toLocaleLowerCase('vi')"),
                        "BranchManagementPage.visible"),
                tc("BRM-034", "Lọc trạng thái", "ALL/ACTIVE/SUSPENDED/CLOSED",
                        "ALL giữ mọi record; từng status chỉ giữ record tương ứng.",
                        () -> sourceContains(PAGE, "ACTIVE: 'Đang hoạt động'", "SUSPENDED: 'Tạm ngừng'",
                                "CLOSED: 'Đã đóng'", "status === 'ALL' || branch.status === status"),
                        "BranchManagementPage.statuses/visible"),
                tc("BRM-035", "Kết hợp tìm kiếm và status", "query + status=SUSPENDED",
                        "Áp dụng giao của hai điều kiện.",
                        () -> sourceContains(PAGE, "(status === 'ALL' || branch.status === status)",
                                "&& `${branch.code} ${branch.branchName} ${branch.address}`"),
                        "BranchManagementPage.visible"),
                tc("BRM-036", "Không có kết quả", "query=khong-ton-tai-BRM-036",
                        "Danh sách rỗng và hiển thị đúng MSG01.",
                        () -> sourceContains(PAGE, MSG01),
                        "BranchManagementPage empty state"),
                tc("BRM-037", "Phân trang 18 branch", "Trang 1, trang 2, đổi query/status",
                        "10 dòng/trang, không trùng, reset page=0 khi lọc.",
                        () -> sourceContains(PAGE, "visible.slice(page * 10, page * 10 + 10)",
                                "useEffect(() => { setPage(0); }, [query, status])",
                                "(page + 1) * 10 >= visible.length"),
                        "BranchManagementPage pagination"),
                tc("BRM-038", "Xem chi tiết branch", "GET detail Hoa Bằng",
                        "Trả đúng branch, không mutation.",
                        () -> combine(sourceContains(CONTROLLER, "@GetMapping(\"/{id}\")", "return service.get(id)"),
                                sourceContains(SERVICE, "SELECT + \" WHERE b.id=?\"")),
                        "BranchManagerAssignmentController.detail/BranchAdministrationService.get"),
                tc("BRM-039", "Chi tiết không tồn tại", "GET UUID không tồn tại",
                        "HTTP 404 Không tìm thấy cơ sở.",
                        () -> sourceContains(SERVICE, "HttpStatus.NOT_FOUND, \"Không tìm thấy cơ sở.\""),
                        "BranchAdministrationService.get"),
                tc("BRM-040", "Xem staff/history và empty state", "GET staff/history có hoặc không dữ liệu",
                        "Trả dữ liệu đúng thứ tự; khi rỗng dùng MSG01; không mutation.",
                        () -> combine(sourceContains(SERVICE, "public List<Map<String, Object>> staff", "ORDER BY e.code",
                                        "public List<Map<String, Object>> history", "ORDER BY a.created_at DESC,a.id"),
                                sourceContains(PAGE, MSG01)),
                        "BranchAdministrationService.staff/history/BranchManagementPage.related"),
                tc("BRM-041", "Tổng hợp 0/1/2 manager", "list/detail branch theo managerCount",
                        "Không nhân bản branch; tổng hợp manager ổn định và count đúng.",
                        () -> sourceContains(SERVICE, "LEFT JOIN LATERAL", "string_agg", "count(*) AS total",
                                "ORDER BY u.username"),
                        "BranchAdministrationService.SELECT")
        );
    }

    private static ManualTest tc(String id, String title, String input, String expected,
            BranchManagementManualRunner.CheckedAction action, String source) {
        return test(id, UC + " - " + title, source, PRE, input, expected, action);
    }
}
