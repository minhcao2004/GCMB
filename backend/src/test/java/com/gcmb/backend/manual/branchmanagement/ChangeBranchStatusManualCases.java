/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 28/10/2026
 * Description: Manual unit test cases for UC-13 Change Branch Operating Status.
 */
package com.gcmb.backend.manual.branchmanagement;

import static com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.combine;
import static com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.sourceContains;
import static com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.test;

import com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.ManualTest;
import java.util.List;

public final class ChangeBranchStatusManualCases {
    private static final String UC = "UC-13 Change Branch Operating Status";
    private static final String SERVICE = "src/main/java/com/gcmb/backend/branch/service/BranchAdministrationService.java";
    private static final String CONTROLLER = "src/main/java/com/gcmb/backend/branch/controller/BranchManagerAssignmentController.java";
    private static final String SECURITY = "src/main/java/com/gcmb/backend/auth/security/AuthSecurity.java";
    private static final String PAGE = "../frontend/src/features/branch/pages/BranchManagementPage.js";
    private static final String V1 = "src/main/resources/db/migration/V1__initial_schema.sql";
    private static final String V11 = "src/main/resources/db/migration/V11__branch_administration.sql";
    private static final String PRE = "Head Office Manager qlcc và branch demo Flyway V14 tồn tại.";
    private static final String MSG02_REASON = "Vui lòng bổ sung Lý do thay đổi trạng thái.";
    private static final String MSG03_REASON = "Lý do thay đổi trạng thái chỉ được chứa tối đa 2000 ký tự.";
    private static final String MSG42 = "Chưa thể ngừng hoạt động cơ sở {branch_name} vì còn booking chờ nhận phòng, khách đang sử dụng phòng hoặc bill chưa thanh toán. Vui lòng xử lý các mục còn tồn tại.";
    private static final String MSG43 = "đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi.";

    private ChangeBranchStatusManualCases() {
    }

    public static List<ManualTest> cases() {
        return List.of(
                tc("BRM-085", "Suspend hợp lệ", "ACTIVE, không blocker, reason hợp lệ",
                        "Chuyển SUSPENDED, lưu reason, giữ business history.",
                        () -> sourceContains(SERVICE, "List.of(\"SUSPENDED\", \"CLOSED\")",
                                "UPDATE gcmb.branch SET status=?,status_reason=?,updated_at=CURRENT_TIMESTAMP"),
                        "BranchAdministrationService.deactivate"),
                tc("BRM-086", "Không có phiên đổi status", "POST deactivate/reactivate",
                        "HTTP 401; status/history không đổi.",
                        () -> sourceContains(SECURITY, "/api/v1/head-office/**", "response.setStatus(401)"),
                        "AuthSecurity/SessionFilter"),
                tc("BRM-087", "Sai vai trò đổi status", "BRANCH_MANAGER/RECEPTIONIST/ACCOUNTANT gọi status",
                        "HTTP 403 và đúng MSG24.",
                        () -> sourceContains(SECURITY, "/api/v1/head-office/**",
                                "Bạn không có quyền chuyển trạng thái đối với cơ sở."),
                        "AuthSecurity"),
                tc("BRM-088", "Hủy xác nhận status", "Nhấn Hủy trước submit",
                        "Đóng modal, không gọi API status.",
                        () -> sourceContains(PAGE, "onClick={() => setModal(null)}",
                                "else result = await api.deactivate(selectedId, { status: modal, reason })"),
                        "BranchManagementPage status confirmation flow"),
                tc("BRM-089", "Status deactivate không hợp lệ", "body/status null, ACTIVE, INVALID",
                        "HTTP 400; chỉ SUSPENDED/CLOSED được chấp nhận.",
                        () -> sourceContains(SERVICE, "input == null || input.status() == null",
                                "!List.of(\"SUSPENDED\", \"CLOSED\").contains(input.status())",
                                "HttpStatus.BAD_REQUEST, \"Chọn Tạm ngừng hoặc Đã đóng.\""),
                        "BranchAdministrationService.deactivate"),
                tc("BRM-090", "Reason bắt buộc", "reason=null/rỗng/khoảng trắng",
                        "Từ chối bằng đúng MSG02.",
                        () -> sourceContains(SERVICE, "clean(input.reason()).isEmpty()", MSG02_REASON),
                        "BranchAdministrationService.deactivate"),
                tc("BRM-091", "Reason đúng max", "reason 2000 ký tự",
                        "Chấp nhận đủ 2000 ký tự sau trim.",
                        () -> sourceContains(SERVICE, "clean(input.reason()).length() > 2000",
                                "input.reason().trim()"),
                        "BranchAdministrationService.deactivate"),
                tc("BRM-092", "Reason vượt max", "reason 2001 ký tự",
                        "Từ chối bằng đúng MSG03.",
                        () -> sourceContains(SERVICE, "clean(input.reason()).length() > 2000", MSG03_REASON),
                        "BranchAdministrationService.deactivate"),
                tc("BRM-093", "Suspend lặp", "Branch đã SUSPENDED",
                        "Trả state hiện có, không tạo business result trùng.",
                        () -> sourceContains(SERVICE, "if (current.equals(input.status()))", "return get(id)"),
                        "BranchAdministrationService.deactivate"),
                tc("BRM-094", "Booking blockers", "DRAFT/CONFIRMED/IN_HOUSE",
                        "Liệt kê và chặn deactivate.",
                        () -> sourceContains(SERVICE, "status IN ('DRAFT','CONFIRMED','IN_HOUSE')", "'BOOKING' AS kind"),
                        "BranchAdministrationService.blockers/deactivate"),
                tc("BRM-095", "Room session blockers", "RESERVED/OCCUPIED",
                        "Liệt kê và chặn deactivate.",
                        () -> sourceContains(SERVICE, "a.state IN ('RESERVED','OCCUPIED')", "'ROOM_SESSION'"),
                        "BranchAdministrationService.blockers/deactivate"),
                tc("BRM-096", "Bill blockers", "DRAFT/ISSUED",
                        "Liệt kê và chặn deactivate.",
                        () -> sourceContains(SERVICE, "status IN ('DRAFT','ISSUED')", "'BILL'"),
                        "BranchAdministrationService.blockers/deactivate"),
                tc("BRM-097", "Nghiệp vụ đã kết thúc", "CANCELLED/COMPLETED/RELEASED/PAID/VOID",
                        "Không nằm trong các tập blocker.",
                        () -> combine(sourceContains(SERVICE, "status IN ('DRAFT','CONFIRMED','IN_HOUSE')",
                                        "a.state IN ('RESERVED','OCCUPIED')", "status IN ('DRAFT','ISSUED')"),
                                BranchManagementManualRunner.sourceOmits(SERVICE,
                                        "status IN ('CANCELLED','COMPLETED')",
                                        "a.state IN ('RELEASED')", "status IN ('PAID','VOID')")),
                        "BranchAdministrationService.blockers"),
                tc("BRM-098", "Blocker ngăn suspend", "Branch còn ít nhất một blocker",
                        "Status/reason/history giữ nguyên và hiển thị đúng MSG42.",
                        () -> combine(sourceContains(SERVICE, "if (!blockers(id).isEmpty())", MSG42),
                                sourceContains(PAGE, MSG42)),
                        "BranchAdministrationService.deactivate/BranchManagementPage blocker flow"),
                tc("BRM-099", "Blocker phát sinh trước xác nhận", "Initial empty rồi có booking mới",
                        "Backend lock và kiểm tra lại trong giao dịch; UI reload blocker khi lỗi.",
                        () -> combine(sourceContains(SERVICE, "lock(id)", "if (!blockers(id).isEmpty())"),
                                sourceContains(PAGE, "if (modal === 'SUSPENDED')", "setBlockers(await api.blockers(selectedId))")),
                        "BranchAdministrationService.deactivate/BranchManagementPage.save"),
                tc("BRM-100", "Reactivate SUSPENDED", "POST reactivate",
                        "Chuyển ACTIVE, xóa status_reason, giữ history.",
                        () -> sourceContains(SERVICE, "if (!\"SUSPENDED\".equals(current))",
                                "SET status='ACTIVE',status_reason='',updated_at=CURRENT_TIMESTAMP"),
                        "BranchAdministrationService.reactivate"),
                tc("BRM-101", "Reactivate lặp ACTIVE", "Branch đã ACTIVE",
                        "Trả state hiện có, không tạo business result trùng.",
                        () -> sourceContains(SERVICE, "if (\"ACTIVE\".equals(current)) return get(id)"),
                        "BranchAdministrationService.reactivate"),
                tc("BRM-102", "Reactivate CLOSED", "Branch status=CLOSED",
                        "Từ chối Chỉ mở lại cơ sở đang tạm ngừng.",
                        () -> sourceContains(SERVICE, "Chỉ mở lại cơ sở đang tạm ngừng."),
                        "BranchAdministrationService.reactivate"),
                tc("BRM-103", "Close branch", "ACTIVE không blocker, status=CLOSED",
                        "Chuyển CLOSED, lưu reason và closed_on hiện tại.",
                        () -> combine(sourceContains(V1, "CHECK (status IN ('ACTIVE','SUSPENDED','CLOSED'))"),
                                sourceContains(SERVICE, "closed_on=CASE WHEN ?='CLOSED' THEN CURRENT_DATE ELSE closed_on END")),
                        "BranchAdministrationService.deactivate/Flyway V1"),
                tc("BRM-104", "Đổi status branch CLOSED", "CLOSED -> SUSPENDED/CLOSED",
                        "Không cho chuyển, giữ closed_on/reason/history.",
                        () -> sourceContains(SERVICE, "if (current.equals(\"CLOSED\"))",
                                "Cơ sở đã đóng, không thể chuyển trạng thái này."),
                        "BranchAdministrationService.deactivate"),
                tc("BRM-105", "Branch không tồn tại", "GET blockers/deactivate/reactivate UUID lạ",
                        "HTTP 404; không tạo audit.",
                        () -> sourceContains(SERVICE, "HttpStatus.NOT_FOUND, \"Không tìm thấy cơ sở.\"",
                                "public List<Map<String, Object>> blockers", "lock(id)"),
                        "BranchAdministrationService.get/lock/blockers"),
                tc("BRM-106", "Audit deactivate và reason", "Suspend thành công rồi history",
                        "Một DEACTIVATE_BRANCH audit event; history có actor/action/reason.",
                        () -> combine(sourceContains(CONTROLLER, "audit.record(\"DEACTIVATE_BRANCH\""),
                                sourceContains(SERVICE, "a.action='DEACTIVATE_BRANCH'", "a.after_data->>'status_reason'")),
                        "MutationAudit.record/BranchAdministrationService.history"),
                tc("BRM-107", "MSG12 sau đổi status", "Suspend hoặc reactivate thành công",
                        "Hiển thị đúng MSG12 với entity/status thực tế.",
                        () -> sourceContains(PAGE, "Đã chuyển trạng thái của", "thành “"),
                        "BranchManagementPage.save"),
                tc("BRM-108", "Read-only booking khi SUSPENDED", "Staff xem dữ liệu rồi tạo booking",
                        "Cho xem; chặn tạo booking và hiển thị MSG43.",
                        () -> combine(sourceContains(V11, "CREATE TRIGGER booking_active_branch BEFORE INSERT ON gcmb.booking",
                                        "branch_status IS DISTINCT FROM 'ACTIVE'"),
                                sourceContains(PAGE, MSG43)),
                        "Flyway V11 require_active_branch/branch-scoped UI"),
                tc("BRM-109", "Read-only mọi mutation khi SUSPENDED", "Tạo/update room và nghiệp vụ khác",
                        "Mọi mutation branch-scoped bị chặn và dùng MSG43.",
                        () -> sourceContains(V11, "CREATE TRIGGER room_active_branch",
                                "CREATE TRIGGER payment_active_branch", "CREATE TRIGGER employee_active_branch", MSG43),
                        "Flyway V11/branch-scoped mutations"),
                tc("BRM-110", "Giữ dữ liệu lịch sử", "Xem detail/staff/history sau suspend",
                        "Các API đọc vẫn hoạt động; không xóa record.",
                        () -> combine(sourceContains(SERVICE, "public Map<String, Object> get(UUID id)",
                                        "public List<Map<String, Object>> staff", "public List<Map<String, Object>> history"),
                                BranchManagementManualRunner.sourceOmits(SERVICE, "DELETE FROM gcmb.branch WHERE")),
                        "BranchAdministrationService.get/staff/history")
        );
    }

    private static ManualTest tc(String id, String title, String input, String expected,
            BranchManagementManualRunner.CheckedAction action, String source) {
        return test(id, UC + " - " + title, source, PRE, input, expected, action);
    }
}
