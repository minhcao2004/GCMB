/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 09/11/2026
 * Description: Manual unit test cases for UC-13 Change Branch Operating Status.
 */
package com.gcmb.backend.manual.branchmanagement;

import static com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.branch;
import static com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.check;
import static com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.combine;
import static com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.expectStatus;
import static com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.test;

import com.gcmb.backend.branch.dto.BranchDtos.StatusCommand;
import com.gcmb.backend.branch.service.BranchAdministrationService;
import com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.ManualTest;
import com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.ScriptedJdbc;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ChangeBranchStatusManualCases {
    private static final String UC = "UC-13 Change Branch Operating Status";
    private static final UUID BRANCH_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final String REASON = "Tạm ngừng để bảo trì";

    private ChangeBranchStatusManualCases() {
    }

    public static List<ManualTest> cases() {
        return List.of(
                test("BRM-123", UC + " - Trạng thái không hợp lệ", "BranchAdministrationService.deactivate",
                        "Head Office Manager đã đăng nhập; cơ sở Hoa Bằng ACTIVE và không có blocker.",
                        "Lần lượt body=null, status=null, ACTIVE và INVALID.",
                        "Từng yêu cầu bị từ chối HTTP 400 với thông báo: Chọn Tạm ngừng hoặc Đã đóng.; trạng thái không đổi.", () -> combine(
                                invalidStatus(null),
                                invalidStatus(new StatusCommand(null, REASON)),
                                invalidStatus(new StatusCommand("ACTIVE", REASON)),
                                invalidStatus(new StatusCommand("INVALID", REASON)))),
                test("BRM-124", UC + " - Lý do bắt buộc và giới hạn", "BranchAdministrationService.deactivate",
                        "Head Office Manager đã đăng nhập; cơ sở Hoa Bằng ACTIVE và không có blocker.",
                        "status=SUSPENDED; reason lần lượt null, rỗng, chỉ khoảng trắng và 2.001 ký tự.",
                        "Giá trị thiếu bị từ chối bằng 'Vui lòng bổ sung lý do thay đổi trạng thái.'; 2.001 ký tự bị từ chối bằng 'Lý do thay đổi trạng thái chỉ được chứa tối đa 2000 ký tự.'; trạng thái không đổi.", () -> combine(
                                invalidReason(null, "Vui lòng bổ sung lý do thay đổi trạng thái."),
                                invalidReason("", "Vui lòng bổ sung lý do thay đổi trạng thái."),
                                invalidReason("   ", "Vui lòng bổ sung lý do thay đổi trạng thái."),
                                invalidReason("R".repeat(2001), "Lý do thay đổi trạng thái chỉ được chứa tối đa 2000 ký tự."))),
                test("BRM-125", UC + " - Gửi lặp cùng trạng thái", "BranchAdministrationService.deactivate",
                        "Head Office Manager đã đăng nhập; cơ sở Hoa Bằng đã SUSPENDED với dữ liệu lịch sử được giữ nguyên.",
                        "Gọi deactivate với status=SUSPENDED và lý do hợp lệ.",
                        "Trả trạng thái hiện có, không update hoặc tạo kết quả nghiệp vụ trùng.", () -> {
                            ScriptedJdbc db = locked("SUSPENDED");
                            db.mapRows.add(List.of(branch(BRANCH_ID, "SUSPENDED")));
                            Map<String, Object> actual = new BranchAdministrationService(db)
                                    .deactivate(BRANCH_ID, new StatusCommand("SUSPENDED", REASON));
                            return check("SUSPENDED".equals(actual.get("status")) && db.updates.isEmpty(),
                                    "status=" + actual.get("status") + ", updates=" + db.updates.size());
                        }),
                test("BRM-126", UC + " - Liệt kê blocker", "BranchAdministrationService.blockers",
                        "Head Office Manager đã đăng nhập; cơ sở Hoa Bằng ACTIVE có booking chờ nhận phòng, phòng đang sử dụng và bill chưa thanh toán.",
                        "Gọi blockers(branchId).",
                        "Trả đủ blocker BOOKING, ROOM_SESSION và BILL theo loại/mã; không thay đổi dữ liệu.", () -> {
                            ScriptedJdbc db = new ScriptedJdbc();
                            List<Map<String, Object>> blockers = List.of(
                                    Map.of("kind", "BOOKING", "code", "BK001", "status", "CONFIRMED"),
                                    Map.of("kind", "ROOM_SESSION", "code", "BK002", "status", "OCCUPIED"),
                                    Map.of("kind", "BILL", "code", "BL001", "status", "ISSUED"));
                            db.mapRows.add(List.of(branch(BRANCH_ID, "ACTIVE")));
                            db.mapRows.add(blockers);
                            List<Map<String, Object>> actual = new BranchAdministrationService(db).blockers(BRANCH_ID);
                            return check(actual.equals(blockers) && db.updates.isEmpty()
                                            && db.reads.get(1).sql().contains("ORDER BY kind,code"),
                                    "blockers=" + actual.size() + ", updates=" + db.updates.size());
                        }),
                test("BRM-127", UC + " - Blocker ngăn tạm ngừng", "BranchAdministrationService.deactivate",
                        "Head Office Manager đã đăng nhập; cơ sở Hoa Bằng ACTIVE có ít nhất một blocker BR-149.",
                        "Gọi deactivate status=SUSPENDED với lý do hợp lệ.",
                        "Từ chối HTTP 409 với thông báo: Chưa thể ngừng hoạt động cơ sở GenZ Cinema Hoa Bằng vì còn booking chờ nhận phòng, khách đang sử dụng phòng hoặc bill chưa thanh toán. Vui lòng xử lý các mục còn tồn tại.; trạng thái không đổi.", () -> {
                            ScriptedJdbc db = locked("ACTIVE");
                            db.mapRows.add(List.of(branch(BRANCH_ID, "ACTIVE")));
                            db.mapRows.add(List.of(Map.of("kind", "BOOKING", "code", "BK001", "status", "CONFIRMED")));
                            db.mapRows.add(List.of(branch(BRANCH_ID, "ACTIVE")));
                            return expectStatus(() -> new BranchAdministrationService(db).deactivate(
                                            BRANCH_ID, new StatusCommand("SUSPENDED", REASON)), 409,
                                    "Chưa thể ngừng hoạt động cơ sở GenZ Cinema Hoa Bằng vì còn booking chờ nhận phòng, khách đang sử dụng phòng hoặc bill chưa thanh toán. Vui lòng xử lý các mục còn tồn tại.");
                        }),
                test("BRM-128", UC + " - Tạm ngừng thành công", "BranchAdministrationService.deactivate",
                        "Head Office Manager đã đăng nhập; cơ sở Hoa Bằng ACTIVE không có booking chờ nhận phòng, khách đang sử dụng phòng hoặc bill chưa thanh toán.",
                        "status=SUSPENDED, reason=Tạm ngừng để bảo trì.",
                        "Chuyển cơ sở sang SUSPENDED, lưu đúng lý do và giữ nguyên dữ liệu lịch sử.", () -> successfulDeactivate("SUSPENDED")),
                test("BRM-129", UC + " - Đóng cơ sở thành công", "BranchAdministrationService.deactivate",
                        "Head Office Manager đã đăng nhập; cơ sở Hoa Bằng ACTIVE không có blocker BR-149.",
                        "status=CLOSED, reason=Tạm ngừng để bảo trì.",
                        "Chuyển cơ sở sang CLOSED, lưu lý do, đặt closed_on theo ngày hiện tại và giữ nguyên lịch sử.", () -> successfulDeactivate("CLOSED")),
                test("BRM-130", UC + " - Mở lại cơ sở", "BranchAdministrationService.reactivate",
                        "Head Office Manager đã đăng nhập; cơ sở Hoa Bằng đang SUSPENDED.",
                        "Gọi reactivate(branchId).",
                        "Chuyển trạng thái sang ACTIVE, xóa status_reason và giữ nguyên lịch sử nghiệp vụ.", () -> {
                            ScriptedJdbc db = locked("SUSPENDED");
                            db.mapRows.add(List.of(branch(BRANCH_ID, "ACTIVE")));
                            Map<String, Object> actual = new BranchAdministrationService(db).reactivate(BRANCH_ID);
                            boolean update = db.updates.size() == 1
                                    && db.updates.get(0).sql().contains("status='ACTIVE',status_reason=''" );
                            return check("ACTIVE".equals(actual.get("status")) && update,
                                    "status=" + actual.get("status") + ", updates=" + db.updates.size());
                        }),
                test("BRM-131", UC + " - Mở lại cơ sở đang ACTIVE", "BranchAdministrationService.reactivate",
                        "Head Office Manager đã đăng nhập; cơ sở Hoa Bằng đã ACTIVE.",
                        "Gọi reactivate(branchId).",
                        "Trả trạng thái ACTIVE hiện có và không update trùng.", () -> {
                            ScriptedJdbc db = locked("ACTIVE");
                            db.mapRows.add(List.of(branch(BRANCH_ID, "ACTIVE")));
                            Map<String, Object> actual = new BranchAdministrationService(db).reactivate(BRANCH_ID);
                            return check("ACTIVE".equals(actual.get("status")) && db.updates.isEmpty(),
                                    "status=" + actual.get("status") + ", updates=" + db.updates.size());
                        }),
                test("BRM-132", UC + " - Không mở lại cơ sở CLOSED", "BranchAdministrationService.reactivate",
                        "Head Office Manager đã đăng nhập; cơ sở Hoa Bằng đang CLOSED.",
                        "Gọi reactivate(branchId).",
                        "Từ chối HTTP 409 với thông báo: Chỉ mở lại cơ sở đang tạm ngừng.; trạng thái và lịch sử không đổi.", () -> {
                            ScriptedJdbc db = locked("CLOSED");
                            return expectStatus(() -> new BranchAdministrationService(db).reactivate(BRANCH_ID),
                                    409, "Chỉ mở lại cơ sở đang tạm ngừng.");
                        }),
                test("BRM-133", UC + " - Cơ sở không tồn tại", "BranchAdministrationService.deactivate/reactivate",
                        "Head Office Manager đã đăng nhập; UUID không thuộc cơ sở nào.",
                        "Gọi deactivate với dữ liệu hợp lệ rồi gọi reactivate trên UUID không tồn tại.",
                        "Mỗi thao tác bị từ chối HTTP 404 với thông báo: Không tìm thấy cơ sở.; không tạo cập nhật.", () -> combine(
                                missingBranch(true), missingBranch(false)))
        );
    }

    private static BranchManagementManualRunner.TestResult invalidStatus(StatusCommand command) {
        ScriptedJdbc db = new ScriptedJdbc();
        return expectStatus(() -> new BranchAdministrationService(db).deactivate(BRANCH_ID, command),
                400, "Chọn Tạm ngừng hoặc Đã đóng.");
    }

    private static BranchManagementManualRunner.TestResult invalidReason(String reason, String expectedMessage) {
        ScriptedJdbc db = new ScriptedJdbc();
        return expectStatus(() -> new BranchAdministrationService(db).deactivate(
                        BRANCH_ID, new StatusCommand("SUSPENDED", reason)),
                400, expectedMessage);
    }

    private static BranchManagementManualRunner.TestResult successfulDeactivate(String status) {
        ScriptedJdbc db = locked("ACTIVE");
        db.mapRows.add(List.of(branch(BRANCH_ID, "ACTIVE")));
        db.mapRows.add(List.of());
        db.mapRows.add(List.of(branch(BRANCH_ID, status)));
        Map<String, Object> actual = new BranchAdministrationService(db)
                .deactivate(BRANCH_ID, new StatusCommand(status, "  " + REASON + "  "));
        Object[] args = db.updates.get(0).args();
        boolean saved = db.updates.size() == 1 && status.equals(args[0]) && REASON.equals(args[1]);
        boolean closedOn = !"CLOSED".equals(status) || db.updates.get(0).sql().contains("closed_on=CASE");
        return check(status.equals(actual.get("status")) && saved && closedOn,
                "status=" + actual.get("status") + ", savedReason=" + args[1]);
    }

    private static BranchManagementManualRunner.TestResult missingBranch(boolean deactivate) {
        ScriptedJdbc db = new ScriptedJdbc();
        db.typedRows.add(List.of());
        BranchAdministrationService service = new BranchAdministrationService(db);
        return expectStatus(() -> {
            if (deactivate) service.deactivate(BRANCH_ID, new StatusCommand("SUSPENDED", REASON));
            else service.reactivate(BRANCH_ID);
        }, 404, "Không tìm thấy cơ sở.");
    }

    private static ScriptedJdbc locked(String status) {
        ScriptedJdbc db = new ScriptedJdbc();
        db.typedRows.add(List.of(BRANCH_ID));
        db.mapRows.add(List.of(branch(BRANCH_ID, status)));
        return db;
    }
}
