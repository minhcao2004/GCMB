/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 28/10/2026
 * Description: Manual unit test cases for UC-12 Set Branch Manager.
 */
package com.gcmb.backend.manual.branchmanagement;

import static com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.combine;
import static com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.sourceContains;
import static com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.test;

import com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.ManualTest;
import java.util.List;

public final class SetBranchManagerManualCases {
    private static final String UC = "UC-12 Set Branch Manager";
    private static final String SERVICE = "src/main/java/com/gcmb/backend/branch/service/BranchAdministrationService.java";
    private static final String CONTROLLER = "src/main/java/com/gcmb/backend/branch/controller/BranchManagerAssignmentController.java";
    private static final String SECURITY = "src/main/java/com/gcmb/backend/auth/security/AuthSecurity.java";
    private static final String PAGE = "../frontend/src/features/branch/pages/BranchManagementPage.js";
    private static final String V14 = "src/main/resources/db/migration/V14__seed_genz_branches_and_accounts.sql";
    private static final String V19 = "src/main/resources/db/migration/V19__managers_with_multiple_branches.sql";
    private static final String V20 = "src/main/resources/db/migration/V20__limit_two_branch_managers.sql";
    private static final String PRE = "Head Office Manager qlcc, branch và tài khoản demo Flyway V14 tồn tại.";
    private static final String MSG02 = "Vui lòng bổ sung Tài khoản quản lý cơ sở.";
    private static final String MSG40 = "Cơ sở GenZ Demo hiện không hoạt động. Vui lòng chọn cơ sở đang hoạt động để phân công.";
    private static final String MSG41 = "Tài khoản được chọn không đủ điều kiện làm Quản lý cơ sở. Vui lòng chọn tài khoản Quản lý cơ sở khả dụng.";

    private SetBranchManagerManualCases() {
    }

    public static List<ManualTest> cases() {
        return List.of(
                tc("BRM-069", "Danh sách manager khả dụng", "GET /managers",
                        "Chỉ ACTIVE BRANCH_MANAGER có employee ACTIVE/null, sắp xếp ổn định.",
                        () -> sourceContains(SERVICE, "u.status='ACTIVE' AND r.code='BRANCH_MANAGER'",
                                "e.id IS NULL OR e.status='ACTIVE'", "ORDER BY COALESCE(e.full_name,u.username),u.id"),
                        "BranchAdministrationService.managers"),
                tc("BRM-070", "Loại account không đủ điều kiện", "Sai role, REVOKED, employee INACTIVE",
                        "Không xuất hiện trong danh sách chọn.",
                        () -> sourceContains(SERVICE, "u.status='ACTIVE'", "r.code='BRANCH_MANAGER'",
                                "e.id IS NULL OR e.status='ACTIVE'"),
                        "BranchAdministrationService.managers"),
                tc("BRM-071", "Gán manager hợp lệ", "ACTIVE manager -> ACTIVE branch còn chỗ",
                        "Tạo đúng một assignment và trả detail branch.",
                        () -> combine(sourceContains(V14, "'BRANCH_MANAGER'", "'HEAD_OFFICE_MANAGER'"),
                                sourceContains(SERVICE, "INSERT INTO gcmb.branch_manager_assignment", "return get(branchId)")),
                        "BranchAdministrationService.assign"),
                tc("BRM-072", "Không có phiên assign", "PUT /{branchId}/manager",
                        "HTTP 401; không tạo assignment/audit.",
                        () -> sourceContains(SECURITY, "/api/v1/head-office/**", "response.setStatus(401)"),
                        "AuthSecurity/SessionFilter"),
                tc("BRM-073", "Sai vai trò assign", "BRANCH_MANAGER/RECEPTIONIST/ACCOUNTANT PUT assign",
                        "HTTP 403 và đúng MSG24.",
                        () -> sourceContains(SECURITY, "/api/v1/head-office/**",
                                "Bạn không có quyền phân công đối với quản lý cơ sở."),
                        "AuthSecurity"),
                tc("BRM-074", "Hủy xác nhận assign", "Chọn Hủy trước submit",
                        "Đóng modal, không gọi api.assign.",
                        () -> sourceContains(PAGE, "onClick={() => setModal(null)}",
                                "else if (modal === 'manager') result = await api.assign(selectedId, managerId)"),
                        "BranchManagementPage manager confirmation flow"),
                tc("BRM-075", "Thiếu manager", "body=null hoặc userId=null",
                        "HTTP 400 và đúng MSG02.",
                        () -> combine(sourceContains(CONTROLLER, "body == null ? null : body.userId()"),
                                sourceContains(SERVICE, MSG02)),
                        "BranchManagerAssignmentController.assign/BranchAdministrationService.assign"),
                tc("BRM-076", "Gán vào branch SUSPENDED", "Manager hợp lệ, branch không ACTIVE",
                        "Từ chối và đúng MSG40.",
                        () -> sourceContains(SERVICE, "if (!\"ACTIVE\".equals(get(branchId).get(\"status\")))", MSG40),
                        "BranchAdministrationService.assign"),
                tc("BRM-077", "Account không đủ điều kiện", "UUID lạ/REVOKED/sai role/employee INACTIVE",
                        "Từ chối từng trường hợp bằng đúng MSG41.",
                        () -> sourceContains(SERVICE, "u.status='ACTIVE' AND r.code='BRANCH_MANAGER'",
                                "e.status<>'ACTIVE'", MSG41),
                        "BranchAdministrationService.assign"),
                tc("BRM-078", "Branch không tồn tại", "Assign vào UUID branch không tồn tại",
                        "HTTP 404, không tạo assignment/audit.",
                        () -> sourceContains(SERVICE, "lock(branchId)", "HttpStatus.NOT_FOUND, \"Không tìm thấy cơ sở.\""),
                        "BranchAdministrationService.lock/assign"),
                tc("BRM-079", "Gán lặp cùng manager", "Cùng branchId + userId",
                        "Trả assignment hiện có, không tạo dòng trùng.",
                        () -> sourceContains(SERVICE, "if (current.contains(branchId)) return get(branchId)"),
                        "BranchAdministrationService.assign"),
                tc("BRM-080", "Manager thứ hai", "Branch có 1 manager, gán manager thứ 2",
                        "Thành công và managerCount=2.",
                        () -> combine(sourceContains(SERVICE, "count != null && count >= 2", "INSERT INTO gcmb.branch_manager_assignment"),
                                sourceContains(PAGE, "managerCount || 0}/2")),
                        "BranchAdministrationService.assign/SELECT"),
                tc("BRM-081", "Manager thứ ba", "Branch đã có 2 manager",
                        "Từ chối conflict; tổng không vượt 2.",
                        () -> combine(sourceContains(SERVICE, "count != null && count >= 2", "Cơ sở đã có đủ 2 quản lý"),
                                sourceContains(V20, "IF manager_count >= 2")),
                        "BranchAdministrationService.assign/enforce_branch_manager_limit"),
                tc("BRM-082", "Manager quản lý nhiều branch", "Manager ở A được thêm vào B",
                        "Giữ A, thêm B, không ghi đè user_account.branch_id đã có.",
                        () -> combine(sourceContains(V19, "DROP CONSTRAINT branch_manager_assignment_user_id_key"),
                                sourceContains(SERVICE, "Thêm phân công, giữ nguyên các cơ sở", "branch_id=COALESCE(branch_id,?)")),
                        "BranchAdministrationService.assign/Flyway V19"),
                tc("BRM-083", "Audit/history và message assign", "Assign thành công rồi xem detail/history",
                        "Một ASSIGN_BRANCH_MANAGER audit event và đúng MSG11.",
                        () -> combine(sourceContains(SERVICE, "'ASSIGN_BRANCH_MANAGER'", "jsonb_build_object('name',b.name,'managerId'"),
                                sourceContains(PAGE, "Đã cập nhật cơ sở.")),
                        "BranchAdministrationService.assign/history/BranchManagementPage.save"),
                tc("BRM-084", "Hai assign đồng thời", "Cạnh tranh manager thứ 2 và thứ 3",
                        "Advisory/row lock và DB trigger giữ tổng tối đa 2.",
                        () -> combine(sourceContains(SERVICE, "pg_advisory_xact_lock(1300261013)", "FOR UPDATE OF u", "lock(branchId)"),
                                sourceContains(V20, "pg_advisory_xact_lock(1300261013)", "IF manager_count >= 2")),
                        "BranchAdministrationService.assign/Flyway V20")
        );
    }

    private static ManualTest tc(String id, String title, String input, String expected,
            BranchManagementManualRunner.CheckedAction action, String source) {
        return test(id, UC + " - " + title, source, PRE, input, expected, action);
    }
}
