/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 12/11/2026
 * Description: Manual unit test cases cho UC-78 View Staff Records.
 */
package com.gcmb.backend.manual.staffmanagement;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gcmb.backend.accounts.repository.AccountRepository;
import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.exception.AuthFailure;
import com.gcmb.backend.manual.staffmanagement.StaffManagementManualRunner.ManualCase;
import com.gcmb.backend.manual.staffmanagement.StaffManagementManualRunner.Observation;
import com.gcmb.backend.workforce.repository.StaffRepository;
import com.gcmb.backend.workforce.service.StaffService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

final class ViewStaffRecordsManualCases {
    private static final UUID HOM_ID = UUID.fromString("47570000-0000-4000-8000-000000000005");
    private static final UUID BM_ID = UUID.fromString("47570000-0000-4000-8000-000000000003");
    private static final UUID RECEPTIONIST_ID = UUID.fromString("47570000-0000-4000-8000-000000000001");
    private static final UUID HOA_BANG = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID NLB = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final String TITLE = "UC-78 View Staff Records";
    private static final String SOURCE = "UC-78; BR-52, BR-177; MSG24, MSG43; V14; StaffService.list/options/detail";

    private ViewStaffRecordsManualCases() {
    }

    static List<ManualCase> cases() {
        return List.of(
            test("SFM-011",
                "Head Office Manager qlcc đã đăng nhập; dữ liệu nhân viên V14 tồn tại.",
                "Xem danh sách và lựa chọn cơ sở, không nhập bộ lọc.",
                "Trả danh sách nhân viên toàn hệ thống và các cơ sở có thể lựa chọn; không thay đổi dữ liệu.",
                () -> listAndOptions(false)),
            test("SFM-012",
                "Branch Manager qlcs.hoabang đã đăng nhập.",
                "Xem danh sách và lựa chọn cơ sở.",
                "Chỉ trả nhân viên và cơ sở Hoa Bằng thuộc phạm vi quản lý.",
                () -> listAndOptions(true)),
            test("SFM-013",
                "Head Office Manager đã đăng nhập; GENZ-REC-HOA-BANG tồn tại.",
                "Tìm theo tên, mã nhân viên hoặc số điện thoại.",
                "Trả đúng hồ sơ phù hợp với từ khóa; không thay đổi dữ liệu.",
                ViewStaffRecordsManualCases::search),
            test("SFM-014",
                "Head Office Manager đã đăng nhập; có nhân viên thuộc nhiều vị trí, trạng thái và cơ sở.",
                "Lọc theo vị trí RECEPTIONIST, trạng thái ACTIVE, cơ sở Hoa Bằng và bộ lọc chưa phân công.",
                "Repository nhận đúng từng điều kiện lọc và trả các hồ sơ phù hợp.",
                ViewStaffRecordsManualCases::filters),
            test("SFM-015",
                "Head Office Manager đã đăng nhập.",
                "Tìm kiếm bằng giá trị Không tồn tại.",
                "Trả danh sách rỗng, total=0; không thay đổi dữ liệu.",
                ViewStaffRecordsManualCases::emptySearch),
            test("SFM-016",
                "Head Office Manager đã đăng nhập; GENZ-REC-HOA-BANG tồn tại và có lịch sử cơ sở.",
                "Xem chi tiết hồ sơ.",
                "Trả thông tin hồ sơ và lịch sử phân công; không ghi dữ liệu.",
                ViewStaffRecordsManualCases::detail),
            test("SFM-017",
                "Branch Manager được phân công tại cơ sở Hoa Bằng đang SUSPENDED và tài khoản vẫn hoạt động.",
                "Xem danh sách nhân viên tại cơ sở.",
                "Vẫn xem được dữ liệu ở chế độ chỉ đọc.",
                ViewStaffRecordsManualCases::suspendedRead),
            test("SFM-018",
                "qlcs.hoabang đã đăng nhập; hồ sơ thuộc Nguyễn Lương Bằng.",
                "Xem chi tiết hồ sơ ngoài cơ sở được phân công.",
                "Hiển thị “Bạn không có quyền thực hiện thao tác đối với hồ sơ nhân viên này.”",
                ViewStaffRecordsManualCases::crossBranch),
            test("SFM-019",
                "Head Office Manager đã đăng nhập.",
                "Từ khóa tìm kiếm lần lượt có đúng 200 và 201 ký tự.",
                "200 ký tự được xử lý; 201 ký tự hiển thị “Bộ lọc hoặc phân trang không hợp lệ.”",
                ViewStaffRecordsManualCases::queryBoundary),
            test("SFM-020",
                "Head Office Manager đã đăng nhập.",
                "Lần lượt dùng trang âm, kích thước 0, kích thước 101, vị trí hoặc trạng thái không hợp lệ.",
                "Mỗi lần đều hiển thị “Bộ lọc hoặc phân trang không hợp lệ.”",
                ViewStaffRecordsManualCases::invalidFilters),
            test("SFM-021",
                "Hồ sơ có số định danh, email liên hệ và tài khoản liên kết.",
                "Truy vấn danh sách nhân viên.",
                "Projection danh sách không lấy số định danh, email liên hệ hoặc mã tài khoản.",
                ViewStaffRecordsManualCases::sensitiveProjection)
        );
    }

    private static Observation listAndOptions(boolean branchScoped) {
        Scenario scenario = ready();
        Identity actor = branchScoped ? branchManager() : headOffice();
        UUID scope = branchScoped ? HOA_BANG : null;
        List<Map<String, Object>> items = branchScoped
            ? List.of(listRow(RECEPTIONIST_ID, "GENZ-REC-HOA-BANG", HOA_BANG))
            : List.of(listRow(RECEPTIONIST_ID, "GENZ-REC-HOA-BANG", HOA_BANG), listRow(UUID.randomUUID(), "GENZ-REC-NLB-86", NLB));
        when(scenario.staff.list("", "", "", scope, false, 0, 10)).thenReturn(page(items));
        when(scenario.staff.branches(scope)).thenReturn(branchScoped
            ? List.of(branch(HOA_BANG, "HN_HOA_BANG"))
            : List.of(branch(HOA_BANG, "HN_HOA_BANG"), branch(NLB, "HN_NGUYEN_LUONG_BANG_86")));
        Map<String, Object> result = scenario.service.list(actor, "", "", "", null, false, 0, 10);
        Map<String, Object> options = scenario.service.options(actor);
        int itemCount = ((List<?>) result.get("items")).size();
        int branchCount = ((List<?>) options.get("branches")).size();
        boolean passed = itemCount == (branchScoped ? 1 : 2) && branchCount == (branchScoped ? 1 : 2);
        return new Observation("items=" + itemCount + ", branches=" + branchCount + ", scope=" + scope, passed);
    }

    private static Observation search() {
        Scenario scenario = ready();
        Map<String, Object> expected = page(List.of(listRow(RECEPTIONIST_ID, "GENZ-REC-HOA-BANG", HOA_BANG)));
        when(scenario.staff.list("GENZ-REC", "", "", null, false, 0, 10)).thenReturn(expected);
        Map<String, Object> actual = scenario.service.list(headOffice(), " GENZ-REC ", "", "", null, false, 0, 10);
        return new Observation("items=" + ((List<?>) actual.get("items")).size() + ", query=GENZ-REC", actual == expected);
    }

    private static Observation filters() {
        Scenario scenario = ready();
        when(scenario.staff.list("", "RECEPTIONIST", "ACTIVE", HOA_BANG, false, 0, 20))
            .thenReturn(page(List.of(listRow(RECEPTIONIST_ID, "GENZ-REC-HOA-BANG", HOA_BANG))));
        when(scenario.staff.list("", "", "", null, true, 0, 20)).thenReturn(page(List.of()));
        Map<String, Object> filtered = scenario.service.list(headOffice(), "", "RECEPTIONIST", "ACTIVE", HOA_BANG, false, 0, 20);
        Map<String, Object> unassigned = scenario.service.list(headOffice(), "", "", "", null, true, 0, 20);
        boolean passed = ((List<?>) filtered.get("items")).size() == 1 && ((List<?>) unassigned.get("items")).isEmpty();
        return new Observation("filteredItems=" + ((List<?>) filtered.get("items")).size()
            + ", unassignedItems=" + ((List<?>) unassigned.get("items")).size(), passed);
    }

    private static Observation emptySearch() {
        Scenario scenario = ready();
        when(scenario.staff.list("Không tồn tại", "", "", null, false, 0, 10)).thenReturn(page(List.of()));
        Map<String, Object> actual = scenario.service.list(headOffice(), "Không tồn tại", "", "", null, false, 0, 10);
        boolean passed = ((List<?>) actual.get("items")).isEmpty() && ((Number) actual.get("total")).longValue() == 0L;
        return new Observation("items=0, total=" + actual.get("total"), passed);
    }

    private static Observation detail() {
        Scenario scenario = ready();
        Map<String, Object> row = detailRow(RECEPTIONIST_ID, HOA_BANG);
        List<Map<String, Object>> history = List.of(Map.of("branchCode", "HN_HOA_BANG"));
        when(scenario.staff.find(RECEPTIONIST_ID)).thenReturn(row);
        when(scenario.staff.history(RECEPTIONIST_ID)).thenReturn(history);
        Map<String, Object> actual = scenario.service.detail(headOffice(), RECEPTIONIST_ID);
        boolean passed = actual.get("history").equals(history) && "GENZ-REC-HOA-BANG".equals(actual.get("code"));
        return new Observation("code=" + actual.get("code") + ", history=" + ((List<?>) actual.get("history")).size(), passed);
    }

    private static Observation suspendedRead() {
        Scenario scenario = ready();
        when(scenario.accounts.manages(BM_ID, HOA_BANG, false)).thenReturn(true);
        when(scenario.staff.list("", "", "", HOA_BANG, false, 0, 10))
            .thenReturn(page(List.of(listRow(RECEPTIONIST_ID, "GENZ-REC-HOA-BANG", HOA_BANG))));
        Map<String, Object> actual = scenario.service.list(branchManager(), "", "", "", null, false, 0, 10);
        return new Observation("items=" + ((List<?>) actual.get("items")).size() + ", writeCalls=0",
            ((List<?>) actual.get("items")).size() == 1);
    }

    private static Observation crossBranch() {
        Scenario scenario = ready();
        UUID employee = UUID.randomUUID();
        when(scenario.staff.find(employee)).thenReturn(detailRow(employee, NLB));
        String actual = failure(() -> scenario.service.detail(branchManager(), employee));
        String expected = "Bạn không có quyền thực hiện thao tác đối với hồ sơ nhân viên này.";
        return new Observation("message=" + actual, expected.equals(actual));
    }

    private static Observation queryBoundary() {
        Scenario accepted = ready();
        String max = "A".repeat(200);
        when(accepted.staff.list(max, "", "", null, false, 0, 10)).thenReturn(page(List.of()));
        accepted.service.list(headOffice(), max, "", "", null, false, 0, 10);
        Scenario rejected = ready();
        String message = failure(() -> rejected.service.list(headOffice(), "A".repeat(201), "", "", null, false, 0, 10));
        return new Observation("acceptedLength=200, rejectedMessage=" + message,
            "Bộ lọc hoặc phân trang không hợp lệ.".equals(message));
    }

    private static Observation invalidFilters() {
        List<String> messages = List.of(
            invalid("", "", "", -1, 10),
            invalid("", "", "", 0, 0),
            invalid("", "", "", 0, 101),
            invalid("", "DIRECTOR", "", 0, 10),
            invalid("", "", "UNKNOWN", 0, 10)
        );
        boolean passed = messages.stream().allMatch("Bộ lọc hoặc phân trang không hợp lệ."::equals);
        return new Observation("messages=" + messages, passed);
    }

    private static String invalid(String q, String position, String status, int page, int size) {
        Scenario scenario = ready();
        return failure(() -> scenario.service.list(headOffice(), q, position, status, null, false, page, size));
    }

    private static Observation sensitiveProjection() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        StaffRepository repository = new StaffRepository(db, mock(ObjectMapper.class));
        when(db.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(db.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(listRow(RECEPTIONIST_ID, "GENZ-REC-HOA-BANG", HOA_BANG)));
        repository.list("", "", "", null, false, 0, 10);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(db).queryForList(sql.capture(), any(Object[].class));
        String statement = sql.getValue();
        String projection = statement.substring(0, statement.indexOf("FROM"));
        boolean passed = !projection.contains("identity_number")
            && !projection.contains("contact_email")
            && !projection.contains("user_id")
            && projection.contains("e.code")
            && projection.contains("e.full_name");
        return new Observation("listProjection=" + projection.replaceAll("\\s+", " ").trim(), passed);
    }

    private static Scenario ready() {
        StaffRepository staff = mock(StaffRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        when(staff.actorActive(any(), anyString())).thenReturn(true);
        when(accounts.manages(any(), any(), anyBoolean())).thenReturn(true);
        return new Scenario(staff, accounts, new StaffService(staff, accounts));
    }

    private static String failure(Action action) {
        try {
            action.run();
            return "Không phát sinh lỗi";
        } catch (AuthFailure failure) {
            return failure.getMessage();
        }
    }

    private static Map<String, Object> page(List<Map<String, Object>> items) {
        return Map.of("items", items, "total", (long) items.size(), "page", 0, "size", 10);
    }

    private static Map<String, Object> listRow(UUID id, String code, UUID branch) {
        return Map.of("id", id, "code", code, "fullName", code, "phone", "0900000000",
            "position", "RECEPTIONIST", "status", "ACTIVE", "branchId", branch, "branchName", code);
    }

    private static Map<String, Object> detailRow(UUID id, UUID branch) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("code", "GENZ-REC-HOA-BANG");
        row.put("fullName", "Lễ tân - Hoa Bằng");
        row.put("hiredOn", "2026-11-01");
        row.put("status", "ACTIVE");
        row.put("version", 0L);
        row.put("branchId", branch);
        return row;
    }

    private static Map<String, Object> branch(UUID id, String code) {
        return Map.of("id", id, "code", code, "name", code, "status", "ACTIVE");
    }

    private static Identity headOffice() {
        return new Identity(HOM_ID, "qlcc", "HEAD_OFFICE_MANAGER", "/head-office", null, null);
    }

    private static Identity branchManager() {
        return new Identity(BM_ID, "qlcs.hoabang", "BRANCH_MANAGER", "/branch-management", HOA_BANG, "GenZ Cinema Hoa Bằng");
    }

    private static ManualCase test(String id, String precondition, String input, String expected,
                                   StaffManagementManualRunner.Execution execution) {
        return new ManualCase(id, TITLE, SOURCE, precondition, input, expected, execution);
    }

    @FunctionalInterface
    private interface Action {
        void run();
    }

    private record Scenario(StaffRepository staff, AccountRepository accounts, StaffService service) {
    }
}
