/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 09/11/2026
 * Description: Manual unit test cases for UC-12 Set Branch Manager.
 */
package com.gcmb.backend.manual.branchmanagement;

import static com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.branch;
import static com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.check;
import static com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.expectStatus;
import static com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.test;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.branch.service.BranchAdministrationService;
import com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.ManualTest;
import com.gcmb.backend.manual.branchmanagement.BranchManagementManualRunner.ScriptedJdbc;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SetBranchManagerManualCases {
    private static final String UC = "UC-12 Set Branch Manager";
    private static final UUID BRANCH_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID MANAGER_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final Identity ACTOR = new Identity(
            ACTOR_ID, "qlcc", "HEAD_OFFICE_MANAGER", "/head-office", null, null);

    private SetBranchManagerManualCases() {
    }

    public static List<ManualTest> cases() {
        return List.of(
                test("BRM-116", UC + " - Danh sách quản lý khả dụng", "BranchAdministrationService.managers",
                        "Head Office Manager đã đăng nhập; có tài khoản Branch Manager ACTIVE và các tài khoản không đủ điều kiện.",
                        "Gọi managers().",
                        "Chỉ trả tài khoản ACTIVE có vai trò BRANCH_MANAGER và không gắn employee INACTIVE; sắp xếp theo họ tên/username rồi ID.", () -> {
                            ScriptedJdbc db = new ScriptedJdbc();
                            List<Map<String, Object>> managers = List.of(Map.of(
                                    "userId", MANAGER_ID, "username", "qlcs.hoabang", "fullName", "Quản lý Hoa Bằng"));
                            db.mapRows.add(managers);
                            List<Map<String, Object>> actual = new BranchAdministrationService(db).managers();
                            String sql = db.reads.get(0).sql();
                            return check(actual.equals(managers)
                                            && sql.contains("u.status='ACTIVE'")
                                            && sql.contains("r.code='BRANCH_MANAGER'")
                                            && sql.contains("e.status='ACTIVE'")
                                            && sql.contains("ORDER BY COALESCE(e.full_name,u.username),u.id"),
                                    "managers=" + actual.size());
                        }),
                test("BRM-117", UC + " - Thiếu tài khoản quản lý", "BranchAdministrationService.assign",
                        "Head Office Manager đã đăng nhập; cơ sở Hoa Bằng đang ACTIVE.",
                        "Gọi assign(branchId, null, actor).",
                        "Từ chối HTTP 400 với thông báo: Vui lòng bổ sung tài khoản quản lý cơ sở.; không tạo phân công.", () -> {
                            ScriptedJdbc db = new ScriptedJdbc();
                            return expectStatus(() -> new BranchAdministrationService(db).assign(BRANCH_ID, null, ACTOR),
                                    400, "Vui lòng bổ sung tài khoản quản lý cơ sở.");
                        }),
                test("BRM-118", UC + " - Tài khoản không đủ điều kiện", "BranchAdministrationService.assign",
                        "Head Office Manager đã đăng nhập; cơ sở Hoa Bằng đang ACTIVE; tài khoản được chọn không phải Branch Manager ACTIVE khả dụng.",
                        "Gọi assign(branchId, ineligibleUserId, actor).",
                        "Từ chối HTTP 409 với thông báo: Tài khoản được chọn không đủ điều kiện làm Quản lý cơ sở. Vui lòng chọn tài khoản Quản lý cơ sở khả dụng.; không tạo phân công.", () -> {
                            ScriptedJdbc db = new ScriptedJdbc();
                            db.typedRows.add(List.of());
                            return expectStatus(() -> new BranchAdministrationService(db).assign(BRANCH_ID, MANAGER_ID, ACTOR),
                                    409, "Tài khoản được chọn không đủ điều kiện làm Quản lý cơ sở. Vui lòng chọn tài khoản Quản lý cơ sở khả dụng.");
                        }),
                test("BRM-119", UC + " - Cơ sở không hoạt động", "BranchAdministrationService.assign",
                        "Head Office Manager đã đăng nhập; tài khoản Branch Manager ACTIVE khả dụng; cơ sở Hoa Bằng đang SUSPENDED.",
                        "Gọi assign(branchId, managerId, actor).",
                        "Từ chối HTTP 409 với thông báo: Cơ sở GenZ Cinema Hoa Bằng hiện không hoạt động. Vui lòng chọn cơ sở đang hoạt động để phân công.; giữ nguyên phân công.", () -> {
                            ScriptedJdbc db = eligibleManagerAndLockedBranch("SUSPENDED");
                            return expectStatus(() -> new BranchAdministrationService(db).assign(BRANCH_ID, MANAGER_ID, ACTOR),
                                    409, "Cơ sở GenZ Cinema Hoa Bằng hiện không hoạt động. Vui lòng chọn cơ sở đang hoạt động để phân công.");
                        }),
                test("BRM-120", UC + " - Gán lặp cùng quản lý", "BranchAdministrationService.assign",
                        "Head Office Manager đã đăng nhập; cơ sở Hoa Bằng ACTIVE; quản lý đã được phân công tại cơ sở này.",
                        "Gọi lại assign với cùng branchId và managerId.",
                        "Trả phân công hiện có, không tạo dòng hoặc audit trùng và không làm mất phân công khác.", () -> {
                            ScriptedJdbc db = eligibleManagerAndLockedBranch("ACTIVE");
                            db.typedRows.add(List.of(BRANCH_ID));
                            db.mapRows.add(List.of(branch(BRANCH_ID, "ACTIVE")));
                            Map<String, Object> actual = new BranchAdministrationService(db).assign(BRANCH_ID, MANAGER_ID, ACTOR);
                            return check(BRANCH_ID.equals(actual.get("branchId")) && db.updates.isEmpty(),
                                    "branchId=" + actual.get("branchId") + ", updates=" + db.updates.size());
                        }),
                test("BRM-121", UC + " - Đủ hai quản lý", "BranchAdministrationService.assign",
                        "Head Office Manager đã đăng nhập; cơ sở Hoa Bằng ACTIVE đã có hai quản lý; tài khoản thứ ba đủ điều kiện.",
                        "Gọi assign với manager thứ ba.",
                        "Từ chối HTTP 409 với thông báo: Cơ sở đã có đủ 2 quản lý. Hãy chuyển hoặc thu hồi phân công một quản lý trước.; tổng số quản lý không vượt 2.", () -> {
                            ScriptedJdbc db = eligibleManagerAndLockedBranch("ACTIVE");
                            db.typedRows.add(List.of());
                            db.objects.add(2);
                            return expectStatus(() -> new BranchAdministrationService(db).assign(BRANCH_ID, MANAGER_ID, ACTOR),
                                    409, "Cơ sở đã có đủ 2 quản lý. Hãy chuyển hoặc thu hồi phân công một quản lý trước.");
                        }),
                test("BRM-122", UC + " - Gán quản lý thành công", "BranchAdministrationService.assign",
                        "Head Office Manager qlcc đã đăng nhập; cơ sở Hoa Bằng ACTIVE còn chỗ; tài khoản Branch Manager ACTIVE khả dụng và chưa được gán tại cơ sở.",
                        "Gọi assign(branchId, managerId, actor).",
                        "Tạo đúng một phân công, giữ các phân công khác, đồng bộ branch mặc định khi trống và ghi audit ASSIGN_BRANCH_MANAGER.", () -> {
                            ScriptedJdbc db = eligibleManagerAndLockedBranch("ACTIVE");
                            db.typedRows.add(List.of());
                            db.objects.add(1);
                            db.mapRows.add(List.of(branch(BRANCH_ID, "ACTIVE")));
                            Map<String, Object> actual = new BranchAdministrationService(db).assign(BRANCH_ID, MANAGER_ID, ACTOR);
                            boolean assignment = db.updates.stream().anyMatch(call -> call.sql().contains("INSERT INTO gcmb.branch_manager_assignment"));
                            boolean defaultBranch = db.updates.stream().anyMatch(call -> call.sql().contains("branch_id=COALESCE"));
                            boolean audit = db.updates.stream().anyMatch(call -> call.sql().contains("ASSIGN_BRANCH_MANAGER"));
                            return check(BRANCH_ID.equals(actual.get("branchId")) && db.updates.size() == 4
                                            && assignment && defaultBranch && audit,
                                    "updates=" + db.updates.size() + ", assignment=" + assignment + ", audit=" + audit);
                        })
        );
    }

    private static ScriptedJdbc eligibleManagerAndLockedBranch(String status) {
        ScriptedJdbc db = new ScriptedJdbc();
        db.typedRows.add(List.of(MANAGER_ID));
        db.typedRows.add(List.of(BRANCH_ID));
        db.mapRows.add(List.of(branch(BRANCH_ID, status)));
        return db;
    }
}
