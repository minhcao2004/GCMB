/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 29/10/2026
 * Description: Manual unit test cases for UC-32 View Rooms.
 */
package com.gcmb.backend.manual.roommanagement;

import static com.gcmb.backend.manual.roommanagement.RoomManagementManualRunner.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gcmb.backend.branch.dto.RoomView;
import com.gcmb.backend.branch.repository.BranchRoomRepository;
import com.gcmb.backend.manual.roommanagement.RoomManagementManualRunner.Fixture;
import com.gcmb.backend.manual.roommanagement.RoomManagementManualRunner.ManualTest;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

public final class ViewRoomsManualCases {
    private static final String UC = "UC-32 View Rooms";
    private ViewRoomsManualCases() {
    }

    public static List<ManualTest> cases() {
        return List.of(
                test("RM-022", "Xem danh sách hai phòng trong phạm vi cơ sở Hoa Bằng",
                        "Trả nguyên hai phòng, gồm mã MR ổn định của từng phòng; chỉ đọc đúng branch từ identity.", "Normal", () -> {
                            Fixture f = fixture();
                            List<RoomView> expected = List.of(room("Phòng 01", "AVAILABLE"),
                                    new RoomView(java.util.UUID.fromString("34370000-0000-4000-8000-000000000102"),
                                            "RM-UT-02", "Phòng 02", 2, ROOM_TYPE_ID, "RT-QN",
                                            "Phòng tiêu chuẩn", "CINEMA", 2, "", "INACTIVE", "Bảo trì",
                                            null, "READY", "MR-ROOM-UT-02"));
                            when(f.repository().list(BRANCH_ID)).thenReturn(expected);
                            List<RoomView> actual = f.service().list(f.identity());
                            verify(f.repository()).list(BRANCH_ID);
                            return check(expected.equals(actual), "returnedCount=" + actual.size());
                        }),
                test("RM-023", "Xem danh sách khi cơ sở không có phòng phù hợp",
                        "Trả danh sách rỗng, không exception và không ghi dữ liệu.", "Normal", () -> {
                            Fixture f = fixture();
                            when(f.repository().list(BRANCH_ID)).thenReturn(List.of());
                            List<RoomView> actual = f.service().list(f.identity());
                            return check(actual.isEmpty(), "returned=[]");
                        }),
                test("RM-024", "V01 chưa đăng nhập; V02 tài khoản không có phạm vi cơ sở",
                        "Từ chối với thông báo “Bạn không có quyền xem đối với phòng.”; không gọi repository.", "Abnormal", () ->
                                combine(missingScope(null), missingScope(identityWithoutBranch()))),
                test("RM-025", "V01 xem danh mục loại phòng; V02 tài khoản không có phạm vi cơ sở",
                        "V01 nhận danh mục và không ghi dữ liệu; V02 hiển thị “Bạn không có quyền xem đối với danh mục loại phòng.”.", "Normal", () -> {
                            Fixture f = fixture();
                            List<RoomView> expected = List.of(new RoomView(null, "RT-QN", "Phòng tiêu chuẩn",
                                    null, ROOM_TYPE_ID, "RT-QN", "Phòng tiêu chuẩn", "CINEMA",
                                    null, null, null, null, null, null, null));
                            when(f.repository().types()).thenReturn(expected);
                            boolean valid = expected.equals(f.service().types(f.identity()));
                            verify(f.repository()).types();
                            Fixture missing = fixture();
                            TestResult denied = expectStatus(
                                    () -> missing.service().types(identityWithoutBranch()), 403,
                                    "Bạn không có quyền xem đối với danh mục loại phòng.");
                            verify(missing.repository(), never()).types();
                            return combine(check(valid, "types=" + expected.size()), denied);
                        }),
                test("RM-034", "Đọc phòng RM-UT-01 có mã MR-ROOM-UT-01 từ danh sách",
                        "RoomView trả về đúng mrCode = MR-ROOM-UT-01 cùng các thông tin phòng; không ghi dữ liệu.", "Normal", () -> {
                            BranchRoomRepository repository = new BranchRoomRepository(new MappingJdbc());
                            List<RoomView> actual = repository.list(BRANCH_ID);
                            boolean passed = actual.size() == 1
                                    && "RM-UT-01".equals(actual.get(0).code())
                                    && "MR-ROOM-UT-01".equals(actual.get(0).mrCode());
                            return check(passed, "returned=" + actual);
                        })
        );
    }

    private static ManualTest test(String id, String input, String expected, String type, CheckedAction action) {
        String source = "RM-034".equals(id) ? "BranchRoomRepository.list" : "BranchRoomService.list/types";
        return new ManualTest(id, UC, source, precondition(id), input, expected, type, note(id), action);
    }

    private static String precondition(String id) {
        String validScope = "Người dùng đã đăng nhập bằng vai trò được UC-32 cho phép; fixture sử dụng Branch Manager qlcs.hoabang đang ACTIVE và có phạm vi cơ sở Hoa Bằng. ";
        String unit = "BranchRoomService thật; BranchRoomRepository mock; thao tác chỉ đọc.";
        return switch (id) {
            case "RM-022" -> validScope + "Trong phạm vi cơ sở có hai phòng. " + unit;
            case "RM-023" -> validScope + "Không có phòng nào trong phạm vi cơ sở. " + unit;
            case "RM-024" -> "V01 request không có authenticated Identity; V02 người dùng đã đăng nhập nhưng không có branch scope. " + unit;
            case "RM-025" -> "V01 " + validScope
                    + "Có ít nhất một loại phòng đang hoạt động; V02 người dùng đã đăng nhập nhưng không có branch scope. " + unit;
            case "RM-034" -> validScope
                    + "Flyway V23 quy định mỗi phòng có mr_code ổn định; nguồn dữ liệu ghi nhận RM-UT-01 với MR-ROOM-UT-01. "
                    + "BranchRoomRepository thật; Recording JDBC không kết nối database ngoài.";
            default -> throw new IllegalArgumentException("Unknown test case: " + id);
        };
    }

    private static String note(String id) {
        String source = "Nguồn: UC-32_View_Rooms.docx; ";
        return source + switch (id) {
            case "RM-022" -> "Flyway V23; Production: BranchRoomService.list. Phương pháp kiểm thử: White-box.";
            case "RM-023" -> "Production: BranchRoomService.list. Phương pháp kiểm thử: White-box.";
            case "RM-024" -> "MSG24; Production: BranchRoomService.list. Phương pháp kiểm thử: White-box.";
            case "RM-025" -> "MSG24; Flyway V14; Production: BranchRoomService.types. Phương pháp kiểm thử: White-box.";
            case "RM-034" -> "Flyway V23; Production: BranchRoomRepository.list. Phương pháp kiểm thử: White-box.";
            default -> throw new IllegalArgumentException("Unknown test case: " + id);
        };
    }

    private static TestResult missingScope(com.gcmb.backend.auth.dto.Identity identity) {
        Fixture f = fixture();
        TestResult result = expectStatus(() -> f.service().list(identity), 403,
                "Bạn không có quyền xem đối với phòng.");
        verify(f.repository(), never()).list(any());
        return result;
    }

    private static final class MappingJdbc extends JdbcTemplate {
        @Override
        public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", ROOM_ID);
            row.put("code", "RM-UT-01");
            row.put("name", "Phòng kiểm thử");
            row.put("floor_number", 1);
            row.put("room_type_id", ROOM_TYPE_ID);
            row.put("type_code", "RT-QN");
            row.put("type_name", "Phòng tiêu chuẩn");
            row.put("mode", "CINEMA");
            row.put("capacity", 1);
            row.put("description", "Phòng mẫu");
            row.put("operating_status", "AVAILABLE");
            row.put("status_reason", "");
            row.put("expected_ready_at", null);
            row.put("booking_status", "READY");
            row.put("mr_code", "MR-ROOM-UT-01");
            try {
                return List.of(mapper.mapRow(resultSet(row), 0));
            } catch (java.sql.SQLException failure) {
                throw new IllegalStateException(failure);
            }
        }

        private ResultSet resultSet(Map<String, Object> row) {
            return (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{ResultSet.class}, (proxy, method, args) -> {
                        Object value = args == null || args.length == 0 ? null : row.get(String.valueOf(args[0]));
                        return switch (method.getName()) {
                            case "getObject" -> value;
                            case "getString" -> value == null ? null : value.toString();
                            case "getInt" -> value == null ? 0 : ((Number) value).intValue();
                            case "getTimestamp" -> (Timestamp) value;
                            case "wasNull", "isClosed" -> false;
                            case "close" -> null;
                            default -> throw new UnsupportedOperationException("ResultSet adapter: " + method.getName());
                        };
                    });
        }
    }
}
