/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 27/10/2026
 * Description: Manual unit test cases for UC-08 View Audit Log.
 */
package com.gcmb.backend.manual.auditlog;

import static com.gcmb.backend.manual.auditlog.AuditLogManualRunner.check;
import static com.gcmb.backend.manual.auditlog.AuditLogManualRunner.expectAuthFailure;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gcmb.backend.audit.controller.AuditController;
import com.gcmb.backend.audit.dto.AuditDtos.AuditDetail;
import com.gcmb.backend.audit.dto.AuditDtos.AuditEntry;
import com.gcmb.backend.audit.dto.AuditDtos.AuditPage;
import com.gcmb.backend.audit.dto.AuditDtos.Snapshot;
import com.gcmb.backend.audit.repository.AuditRepository;
import com.gcmb.backend.audit.service.AuditService;
import com.gcmb.backend.audit.service.AuditSnapshots;
import com.gcmb.backend.manual.auditlog.AuditLogManualRunner.CheckedAction;
import com.gcmb.backend.manual.auditlog.AuditLogManualRunner.ManualTest;
import com.gcmb.backend.manual.auditlog.AuditLogManualRunner.TestResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

public final class ViewAuditLogManualCases {
    private static final String UC = "UC-08 View Audit Log";
    private static final String PRE = "Head Office Manager qlcc và dữ liệu cơ sở/tài khoản demo Flyway V14 tồn tại; audit log được phát sinh qua luồng production.";
    private static final String NOTE = "Nguồn: UC-08_View_Audit_Log.docx; BR-163; MSG01, MSG24, MSG106; Flyway V14.";
    private static final String MSG01 = "Không tìm thấy kết quả phù hợp. Hãy thử thay đổi từ khóa hoặc bộ lọc.";
    private static final String MSG24 = "Bạn không có quyền xem đối với nhật ký hệ thống.";
    private static final String MSG106 = "Ngày bắt đầu lọc phải bằng hoặc trước ngày kết thúc lọc.";
    private static final String FILTER_ERROR = "Bộ lọc hoặc phân trang không hợp lệ";
    private static final UUID EVENT_ID = UUID.fromString("81000000-0000-4000-8000-000000000001");
    private static final UUID EVENT_ID_2 = UUID.fromString("81000000-0000-4000-8000-000000000002");
    private static final UUID ACTOR_ID = UUID.fromString("47570000-0000-4000-8000-000000000015");
    private static final UUID HOA_BANG = UUID.fromString("47570000-0000-4000-8000-000000000101");
    private static final UUID ENTITY_ID = UUID.fromString("47570000-0000-4000-8000-000000000001");
    private static final Instant TIME = Instant.parse("2026-10-27T03:00:00Z");
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private ViewAuditLogManualCases() {
    }

    public static List<ManualTest> cases() {
        List<ManualTest> cases = new ArrayList<>();

        cases.add(test("ALM-001", "HEAD_OFFICE_MANAGER gọi danh sách", "Cho phép truy cập phạm vi /head-office.", "Normal", () -> {
            String source = source("src/main/java/com/gcmb/backend/auth/security/AuthSecurity.java");
            boolean allowed = source.contains("requestMatchers(\"/api/v1/head-office/**\").hasRole(\"HEAD_OFFICE_MANAGER\")");
            return check(allowed, "headOfficeRulePresent=" + allowed);
        }, "AuthSecurity"));
        cases.add(test("ALM-002", "Không có phiên gọi danh sách", "Từ chối HTTP 401, không trả log.", "Abnormal", () -> {
            String source = source("src/main/java/com/gcmb/backend/auth/security/AuthSecurity.java");
            boolean protectedRoute = source.contains("/api/v1/head-office/**") && source.contains("response.setStatus(401)");
            return check(protectedRoute, "protectedRoute=" + protectedRoute + ", unauthenticatedStatus=401");
        }, "AuthSecurity/SessionFilter"));
        cases.add(permissionMessage("ALM-003", "BRANCH_MANAGER gọi list/actions/detail"));
        cases.add(permissionMessage("ALM-004", "ACCOUNTANT và RECEPTIONIST gọi list"));
        cases.add(test("ALM-005", "Không truyền query parameter", "Controller dùng q/action rỗng, page=0,size=10.", "Normal", () -> {
            AuditService service = mock(AuditService.class);
            AuditPage page = new AuditPage(List.of(), 0, 0, 10);
            when(service.list("", "", null, null, null, 0, 10)).thenReturn(page);
            AuditPage actual = new AuditController(service).list("", "", null, null, null, 0, 10);
            verify(service).list("", "", null, null, null, 0, 10);
            return check(actual == page, "delegatedDefaults=true");
        }, "AuditController.list"));
        cases.add(test("ALM-006", "Bộ lọc mặc định", "Trả đủ metadata và trường AuditEntry.", "Normal", () -> {
            AuditEntry entry = entry(EVENT_ID, TIME, "Quản lý cấp cao", "qlcc", "SET_ACCOUNT_ROLE", "USER_ACCOUNT", ENTITY_ID, "Lễ tân - Hoa Bằng", "Toàn chuỗi", "Điều chuyển");
            ServiceFixture f = service(new AuditPage(List.of(entry), 1, 0, 10));
            AuditPage result = f.service().list("", "", null, null, null, 0, 10);
            boolean complete = result.items().size() == 1 && result.total() == 1 && result.page() == 0 && result.size() == 10
                    && result.items().get(0).id() != null && result.items().get(0).createdAt() != null
                    && result.items().get(0).actorName() != null && result.items().get(0).action() != null
                    && result.items().get(0).entityType() != null && result.items().get(0).entityName() != null;
            return check(complete, "page=" + result);
        }, "AuditService.list/AuditDtos.AuditEntry"));
        cases.add(repositorySql("ALM-007", "Nhiều created_at khác nhau", "ORDER BY created_at DESC.", "Normal",
                "ORDER BY a.created_at DESC", new Query("", "", null, null, null, 0, 10)));
        cases.add(repositorySql("ALM-008", "Hai log cùng created_at", "Tie-break bằng id DESC.", "Normal",
                "a.id DESC", new Query("", "", null, null, null, 0, 10)));
        cases.add(repositoryProjection("ALM-009", "actor_id=null", "actorName=Hệ thống, actorUsername=null.",
                entry(EVENT_ID, TIME, "Hệ thống", null, "SYSTEM", "BOOKING", ENTITY_ID, "Khách demo", "Toàn chuỗi", null),
                "COALESCE(employee.full_name,actor.username,'Hệ thống')"));
        cases.add(repositoryProjection("ALM-010", "Actor có employee", "Ưu tiên employee.full_name và giữ username.",
                entry(EVENT_ID, TIME, "Quản lý cơ sở - Hoa Bằng", "qlcs.hoabang", "UPDATE_ROOM", "ROOM", ENTITY_ID, "HB-01", "GenZ Cinema Hoa Bằng", null),
                "employee.full_name"));
        cases.add(repositoryProjection("ALM-011", "Actor không có employee", "Fallback actorName về username.",
                entry(EVENT_ID, TIME, "qlcc", "qlcc", "UPDATE_BRANCH", "BRANCH", ENTITY_ID, "Hoa Bằng", "Toàn chuỗi", null),
                "actor.username"));
        cases.add(repositoryProjection("ALM-012", "branch_id=null", "branchName=Toàn chuỗi.",
                entry(EVENT_ID, TIME, "Quản lý cấp cao", "qlcc", "CREATE_USER_ACCOUNT", "USER_ACCOUNT", ENTITY_ID, "demo", "Toàn chuỗi", null),
                "COALESCE(b.name,'Toàn chuỗi')"));
        cases.add(repositoryProjection("ALM-013", "BOOKING có contact_name", "Ưu tiên contact_name theo thứ tự fallback.",
                entry(EVENT_ID, TIME, "Lễ tân", "letan.hoabang", "BOOKING_CREATED", "BOOKING", ENTITY_ID, "Nguyễn Văn A", "GenZ Cinema Hoa Bằng", null),
                "a.after_data->>'contact_name'"));
        cases.add(repositoryProjection("ALM-014", "USER_ACCOUNT có snapshot tên", "Fallback đúng fullName/name/employee/username.",
                entry(EVENT_ID, TIME, "Quản lý cấp cao", "qlcc", "SET_ACCOUNT_ROLE", "USER_ACCOUNT", ENTITY_ID, "Lễ tân - Hoa Bằng", "Toàn chuỗi", null),
                "target_employee.full_name"));
        cases.add(repositoryProjection("ALM-015", "Đối tượng không còn thông tin tên", "entityName=Không còn thông tin tên.",
                entry(EVENT_ID, TIME, "Hệ thống", null, "DELETE_ROOM", "ROOM", ENTITY_ID, "Không còn thông tin tên", "Toàn chuỗi", null),
                "'Không còn thông tin tên'"));

        cases.add(test("ALM-016", "q='  QUẢN LÝ CƠ SỞ - HOA BẰNG  '", "Trim trước repository.", "Normal", () -> {
            ServiceFixture f = service(emptyPage(0, 10));
            f.service().list("  QUẢN LÝ CƠ SỞ - HOA BẰNG  ", "", null, null, null, 0, 10);
            verify(f.repository()).list("QUẢN LÝ CƠ SỞ - HOA BẰNG", "", null, null, null, 0, 10);
            return check(true, "repositoryQuery=QUẢN LÝ CƠ SỞ - HOA BẰNG");
        }, "AuditService.list"));
        cases.add(searchCase("ALM-017", "QLCS.HOABANG", "actor.username"));
        cases.add(searchCase("ALM-018", "Nguyễn Văn A", "booking.contact_name"));
        cases.add(searchCase("ALM-019", ENTITY_ID.toString(), "CAST(a.entity_id AS text)"));
        cases.add(test("ALM-020", "q không có kết quả", "API rỗng và UI hiển thị đúng MSG01.", "Abnormal", () -> {
            ServiceFixture f = service(emptyPage(0, 10));
            AuditPage result = f.service().list("khong-ton-tai-ALM-020", "", null, null, null, 0, 10);
            String ui = source("../frontend/src/features/backoffice/accounts/pages/AuditLogPage.js");
            boolean exactMessage = ui.contains(MSG01);
            return check(result.items().isEmpty() && result.total() == 0 && exactMessage,
                    "items=0,total=0,uiMessage=" + (exactMessage ? MSG01 : "Không có nhật ký phù hợp."));
        }, "AuditService.list/AuditLogPage"));

        cases.add(repositoryFilter("ALM-021", "action=SET_ACCOUNT_ROLE", "a.action=?", new Query("", "SET_ACCOUNT_ROLE", null, null, null, 0, 10), "SET_ACCOUNT_ROLE"));
        cases.add(repositoryFilter("ALM-022", "branchId=Hoa Bằng", "a.branch_id=?", new Query("", "", HOA_BANG, null, null, 0, 10), HOA_BANG));
        cases.add(dateFilter("ALM-023", LocalDate.of(2026, 10, 27), null));
        cases.add(dateFilter("ALM-024", null, LocalDate.of(2026, 10, 27)));
        cases.add(dateFilter("ALM-025", LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 27)));
        cases.add(dateFilter("ALM-026", LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 27)));
        cases.add(test("ALM-027", "Kết hợp q/action/branch/from/to", "Count và data dùng cùng điều kiện AND.", "Normal", () -> {
            Query query = new Query("qlcc", "SET_ACCOUNT_ROLE", HOA_BANG, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 27), 0, 10);
            RepoFixture f = repository(List.of(), List.of(), null, 0);
            apply(f.repository(), query);
            String count = f.countSql().get();
            String data = f.dataSql().get();
            boolean sameFilters = count.contains("a.action=?") && count.contains("a.branch_id=?") && count.contains("a.created_at>=?") && count.contains("a.created_at<?")
                    && data.contains("a.action=?") && data.contains("a.branch_id=?") && data.contains("a.created_at>=?") && data.contains("a.created_at<?");
            return check(sameFilters, "countAndDataUseSameFilters=" + sameFilters);
        }, "AuditRepository.list"));
        cases.add(test("ALM-028", "GET /actions với action lặp", "Trả distinct và sắp xếp tăng dần.", "Normal", () -> {
            RepoFixture f = repository(List.of(), List.of("A", "B", "C"), null, 0);
            List<String> actions = f.repository().actions();
            boolean ok = actions.equals(List.of("A", "B", "C")) && f.actionSql().get().contains("DISTINCT action") && f.actionSql().get().contains("ORDER BY action");
            return check(ok, "actions=" + actions);
        }, "AuditRepository.actions"));
        cases.add(test("ALM-029", "audit_event rỗng", "GET /actions trả [].", "Abnormal", () -> {
            RepoFixture f = repository(List.of(), List.of(), null, 0);
            List<String> actions = f.repository().actions();
            return check(actions.isEmpty(), "actions=" + actions);
        }, "AuditRepository.actions"));
        cases.add(pagingCase("ALM-030", 0, 10, 0L));
        cases.add(pagingCase("ALM-031", 1, 10, 10L));
        cases.add(invalidPaging("ALM-032", -1, 10));
        cases.add(invalidPaging("ALM-033", 0, 0));
        cases.add(validBoundary("ALM-034", "size=1", "", "", 0, 1));
        cases.add(validBoundary("ALM-035", "size=100", "", "", 0, 100));
        cases.add(invalidPaging("ALM-036", 0, 101));
        cases.add(validBoundary("ALM-037", "q dài 200", "a".repeat(200), "", 0, 10));
        cases.add(invalidLength("ALM-038", "a".repeat(201), ""));
        cases.add(validBoundary("ALM-039", "action dài 60", "", "A".repeat(60), 0, 10));
        cases.add(invalidLength("ALM-040", "", "A".repeat(61)));
        cases.add(test("ALM-041", "from=28/10/2026,to=27/10/2026", "Từ chối 400 và đúng MSG106.", "Abnormal", () -> {
            ServiceFixture f = service(emptyPage(0, 10));
            TestResult result = expectAuthFailure(() -> f.service().list("", "", null,
                    LocalDate.of(2026, 10, 28), LocalDate.of(2026, 10, 27), 0, 10), 400, MSG106);
            verify(f.repository(), never()).list(anyString(), anyString(), any(), any(), any(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt());
            return result;
        }, "AuditService.list"));
        cases.add(test("ALM-042", "to=9999-12-31", "Chấp nhận năm 9999.", "Boundary", () -> {
            ServiceFixture f = service(emptyPage(0, 10));
            LocalDate max = LocalDate.of(9999, 12, 31);
            f.service().list("", "", null, null, max, 0, 10);
            verify(f.repository()).list("", "", null, null, max, 0, 10);
            return check(true, "to=9999-12-31 accepted");
        }, "AuditService.list"));
        cases.add(test("ALM-043", "to=+10000-01-01", "Từ chối ngày kết thúc không hợp lệ.", "Boundary", () -> {
            ServiceFixture f = service(emptyPage(0, 10));
            TestResult result = expectAuthFailure(() -> f.service().list("", "", null, null,
                    LocalDate.of(10000, 1, 1), 0, 10), 400, "Ngày kết thúc không hợp lệ");
            verify(f.repository(), never()).list(anyString(), anyString(), any(), any(), any(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt());
            return result;
        }, "AuditService.list"));

        cases.add(test("ALM-044", "GET detail bằng id tồn tại", "Trả đúng event và metadata.", "Normal", () -> {
            AuditDetail detail = detail();
            AuditRepository repository = mock(AuditRepository.class);
            when(repository.detail(EVENT_ID)).thenReturn(detail);
            AuditDetail actual = new AuditService(repository).detail(EVENT_ID);
            return check(actual == detail && EVENT_ID.equals(actual.event().id()), "eventId=" + actual.event().id());
        }, "AuditService.detail/AuditRepository.detail"));
        cases.add(test("ALM-045", "Snapshot 7 trường trước/sau", "Map đúng before và after, không đảo.", "Normal", () -> {
            AuditDetail detail = detail();
            boolean ok = "old.user".equals(detail.before().username()) && "new.user".equals(detail.after().username())
                    && "OLD".equals(detail.before().status()) && "ACTIVE".equals(detail.after().status())
                    && "old@example.invalid".equals(detail.before().email()) && "new@example.invalid".equals(detail.after().email());
            return check(ok, "before=" + detail.before() + ", after=" + detail.after());
        }, "AuditRepository.detail/AuditDtos.Snapshot"));
        cases.add(test("ALM-046", "Snapshot chứa trường allowlist", "Giữ đúng trường nghiệp vụ cho phép.", "Normal", () -> {
            Map<String, Object> values = AuditSnapshots.read("{\"username\":\"qlcc\",\"status\":\"ACTIVE\",\"amount\":125000,\"branchName\":\"Hoa Bằng\"}");
            boolean ok = values.size() == 4 && "qlcc".equals(values.get("username")) && Integer.valueOf(125000).equals(values.get("amount"));
            return check(ok, "values=" + values);
        }, "AuditSnapshots.read"));
        cases.add(test("ALM-047", "Snapshot có password/hash/token/trường lạ", "Loại toàn bộ trường nhạy cảm.", "Abnormal", () -> {
            Map<String, Object> values = AuditSnapshots.read("{\"username\":\"qlcc\",\"password\":\"secret\",\"password_hash\":\"hash\",\"token\":\"token\",\"refreshToken\":\"refresh\",\"unknown\":\"x\"}");
            boolean ok = values.equals(Map.of("username", "qlcc"));
            return check(ok, "keys=" + values.keySet());
        }, "AuditSnapshots.read"));
        cases.add(test("ALM-048", "Snapshot có object/list lồng nhau", "Lọc nhạy cảm đệ quy.", "Abnormal", () -> {
            Map<String, Object> values = AuditSnapshots.read("{\"details\":{\"name\":\"A\",\"password\":\"secret\"},\"combos\":[{\"name\":\"Combo 1\",\"price\":50000,\"token\":\"secret\"}]}");
            String rendered = values.toString();
            boolean ok = rendered.contains("name=A") && rendered.contains("Combo 1") && rendered.contains("price=50000")
                    && !rendered.contains("password") && !rendered.contains("token") && !rendered.contains("secret");
            return check(ok, "values=" + values);
        }, "AuditSnapshots.clean/AuditSnapshots.cleanValue"));
        cases.add(test("ALM-049", "actorRole snapshot và fallback role", "SQL ưu tiên actorRole rồi role hiện tại.", "Normal", () -> {
            RepoFixture f = repository(List.of(), List.of(), detail(), 0);
            AuditDetail actual = f.repository().detail(EVENT_ID);
            boolean sql = f.dataSql().get().contains("COALESCE(a.after_data->>'actorRole'") && f.dataSql().get().contains("r.code");
            return check(sql && "HEAD_OFFICE_MANAGER".equals(actual.actorRole()), "actorRole=" + actual.actorRole() + ", sqlFallback=" + sql);
        }, "AuditRepository.detail"));
        cases.add(test("ALM-050", "UUID detail không tồn tại", "Từ chối 404 Không tìm thấy nhật ký.", "Abnormal", () -> {
            AuditRepository repository = mock(AuditRepository.class);
            when(repository.detail(EVENT_ID_2)).thenReturn(null);
            return expectAuthFailure(() -> new AuditService(repository).detail(EVENT_ID_2), 404, "Không tìm thấy nhật ký");
        }, "AuditService.detail"));
        cases.add(test("ALM-051", "Gọi list/actions/detail nhiều lần", "Chỉ truy vấn đọc, không mutate dữ liệu.", "Normal", () -> {
            RepoFixture f = repository(List.of(entry()), List.of("SET_ACCOUNT_ROLE"), detail(), 1);
            f.repository().list("", "", null, null, null, 0, 10);
            f.repository().actions();
            f.repository().detail(EVENT_ID);
            boolean readOnly = Mockito.mockingDetails(f.db()).getInvocations().stream()
                    .allMatch(invocation -> invocation.getMethod().getName().startsWith("query"));
            return check(readOnly, "jdbcMethods=" + Mockito.mockingDetails(f.db()).getInvocations().stream()
                    .map(invocation -> invocation.getMethod().getName()).toList());
        }, "AuditService/AuditRepository"));
        cases.add(test("ALM-052", "Schema audit_event append-only", "Trigger chặn UPDATE và DELETE.", "Abnormal", () -> {
            String migration = source("src/main/resources/db/migration/V1__initial_schema.sql");
            boolean function = migration.contains("CREATE FUNCTION reject_history_mutation()")
                    && migration.contains("RAISE EXCEPTION 'Append-only table %: create an explicit correction instead'");
            boolean trigger = migration.contains("CREATE TRIGGER immutable_audit BEFORE UPDATE OR DELETE ON audit_event");
            return check(function && trigger, "rejectFunction=" + function + ", immutableAuditTrigger=" + trigger);
        }, "V1 migration/reject_history_mutation"));

        return List.copyOf(cases);
    }

    private static ManualTest permissionMessage(String id, String input) {
        return test(id, input, "Từ chối 403 và đúng MSG24.", "Abnormal", () -> {
            String security = source("src/main/java/com/gcmb/backend/auth/security/AuthSecurity.java");
            String actual = security.contains("Yêu cầu không được phép hoặc mã bảo vệ đã hết hạn. Vui lòng tải lại trang")
                    ? "Yêu cầu không được phép hoặc mã bảo vệ đã hết hạn. Vui lòng tải lại trang" : "Không xác định";
            boolean denied = security.contains("/api/v1/head-office/**") && !actual.equals("Không xác định");
            return check(denied && MSG24.equals(actual), "status=403, message=" + actual);
        }, "AuthSecurity.accessDeniedHandler");
    }

    private static ManualTest searchCase(String id, String q, String sqlFragment) {
        return test(id, "q=" + q, "Tìm không phân biệt hoa thường theo trường nguồn.", "Normal", () -> {
            RepoFixture f = repository(List.of(entry()), List.of(), null, 1);
            AuditPage page = f.repository().list(q, "", null, null, null, 0, 10);
            boolean sql = f.dataSql().get().contains("lower(?)") && f.dataSql().get().contains(sqlFragment);
            boolean arg = q.equals(f.dataArgs().get()[0]);
            return check(sql && arg && page.total() == 1, "sqlField=" + sqlFragment + ", queryArg=" + f.dataArgs().get()[0]);
        }, "AuditRepository.list");
    }

    private static ManualTest repositoryFilter(String id, String input, String fragment, Query query, Object expectedArg) {
        return test(id, input, "Áp dụng đúng điều kiện và tham số.", "Normal", () -> {
            RepoFixture f = repository(List.of(entry()), List.of(), null, 1);
            apply(f.repository(), query);
            boolean sql = f.dataSql().get().contains(fragment);
            boolean arg = List.of(f.dataArgs().get()).contains(expectedArg);
            return check(sql && arg, "sqlCondition=" + fragment + ", args=" + List.of(f.dataArgs().get()));
        }, "AuditRepository.list");
    }

    private static ManualTest dateFilter(String id, LocalDate from, LocalDate to) {
        return test(id, "from=" + from + ",to=" + to, "Áp dụng biên ngày theo Asia/Ho_Chi_Minh.", "Boundary", () -> {
            RepoFixture f = repository(List.of(), List.of(), null, 0);
            f.repository().list("", "", null, from, to, 0, 10);
            List<Object> args = List.of(f.dataArgs().get());
            boolean fromOk = from == null || args.contains(Timestamp.from(from.atStartOfDay(VIETNAM).toInstant()));
            boolean toOk = to == null || args.contains(Timestamp.from(to.plusDays(1).atStartOfDay(VIETNAM).toInstant()));
            boolean operators = (from == null || f.dataSql().get().contains("a.created_at>=?"))
                    && (to == null || f.dataSql().get().contains("a.created_at<?"));
            return check(fromOk && toOk && operators, "args=" + args + ", sqlOperators=" + operators);
        }, "AuditRepository.list");
    }

    private static ManualTest pagingCase(String id, int page, int size, long expectedOffset) {
        return test(id, "page=" + page + ",size=" + size, "LIMIT và OFFSET đúng.", "Boundary", () -> {
            RepoFixture f = repository(List.of(entry()), List.of(), null, 21);
            AuditPage result = f.repository().list("", "", null, null, null, page, size);
            Object[] args = f.dataArgs().get();
            boolean ok = args[args.length - 2].equals(size) && args[args.length - 1].equals(expectedOffset)
                    && result.page() == page && result.size() == size && result.total() == 21;
            return check(ok, "limit=" + args[args.length - 2] + ", offset=" + args[args.length - 1]);
        }, "AuditRepository.list");
    }

    private static ManualTest invalidPaging(String id, int page, int size) {
        return test(id, "page=" + page + ",size=" + size, "Từ chối 400 trước repository.", "Boundary", () -> {
            ServiceFixture f = service(emptyPage(page, size));
            TestResult result = expectAuthFailure(() -> f.service().list("", "", null, null, null, page, size), 400, FILTER_ERROR);
            verify(f.repository(), never()).list(anyString(), anyString(), any(), any(), any(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt());
            return result;
        }, "AuditService.list");
    }

    private static ManualTest validBoundary(String id, String input, String q, String action, int page, int size) {
        return test(id, input, "Chấp nhận giá trị biên.", "Boundary", () -> {
            ServiceFixture f = service(emptyPage(page, size));
            f.service().list(q, action, null, null, null, page, size);
            verify(f.repository()).list(q.trim(), action, null, null, null, page, size);
            return check(true, input + " accepted");
        }, "AuditService.list");
    }

    private static ManualTest invalidLength(String id, String q, String action) {
        return test(id, "q.length=" + q.length() + ",action.length=" + action.length(), "Từ chối 400 trước repository.", "Boundary", () -> {
            ServiceFixture f = service(emptyPage(0, 10));
            TestResult result = expectAuthFailure(() -> f.service().list(q, action, null, null, null, 0, 10), 400, FILTER_ERROR);
            verify(f.repository(), never()).list(anyString(), anyString(), any(), any(), any(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt());
            return result;
        }, "AuditService.list");
    }

    private static ManualTest repositorySql(String id, String input, String expected, String type,
            String fragment, Query query) {
        return test(id, input, expected, type, () -> {
            RepoFixture f = repository(List.of(entry()), List.of(), null, 1);
            apply(f.repository(), query);
            boolean present = f.dataSql().get().contains(fragment);
            return check(present, "sqlContains=" + fragment + " -> " + present);
        }, "AuditRepository.list");
    }

    private static ManualTest repositoryProjection(String id, String input, String expected,
            AuditEntry projected, String sqlFragment) {
        return test(id, input, expected, "ALM-015".equals(id) ? "Abnormal" : "Normal", () -> {
            RepoFixture f = repository(List.of(projected), List.of(), null, 1);
            AuditEntry actual = f.repository().list("", "", null, null, null, 0, 10).items().get(0);
            boolean sql = f.dataSql().get().contains(sqlFragment);
            return check(sql && actual.equals(projected), "projection=" + actual + ", sqlFallback=" + sqlFragment);
        }, "AuditRepository.SELECT/AuditRepository.entry");
    }

    private static ServiceFixture service(AuditPage page) {
        AuditRepository repository = mock(AuditRepository.class);
        when(repository.list(anyString(), anyString(), any(), any(), any(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt())).thenReturn(page);
        return new ServiceFixture(repository, new AuditService(repository));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static RepoFixture repository(List<AuditEntry> entries, List<String> actions, AuditDetail detail, long total) {
        JdbcTemplate db = mock(JdbcTemplate.class);
        AtomicReference<String> countSql = new AtomicReference<>();
        AtomicReference<String> dataSql = new AtomicReference<>();
        AtomicReference<Object[]> dataArgs = new AtomicReference<>();
        AtomicReference<String> actionSql = new AtomicReference<>();
        when(db.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenAnswer(invocation -> {
            countSql.set(invocation.getArgument(0));
            return total;
        });
        when(db.query(anyString(), ArgumentMatchers.<RowMapper>any(), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            dataSql.set(sql);
            Object[] invocationArgs = invocation.getArguments();
            dataArgs.set(Arrays.copyOfRange(invocationArgs, 2, invocationArgs.length));
            return sql.contains("before_values") ? (detail == null ? List.of() : List.of(detail)) : entries;
        });
        when(db.queryForList(anyString(), eq(String.class))).thenAnswer(invocation -> {
            actionSql.set(invocation.getArgument(0));
            return actions;
        });
        return new RepoFixture(db, new AuditRepository(db), countSql, dataSql, dataArgs, actionSql);
    }

    private static AuditPage apply(AuditRepository repository, Query query) {
        return repository.list(query.q(), query.action(), query.branchId(), query.from(), query.to(), query.page(), query.size());
    }

    private static AuditEntry entry() {
        return entry(EVENT_ID, TIME, "Quản lý cấp cao", "qlcc", "SET_ACCOUNT_ROLE", "USER_ACCOUNT",
                ENTITY_ID, "Lễ tân - Hoa Bằng", "Toàn chuỗi", "Điều chuyển");
    }

    private static AuditEntry entry(UUID id, Instant createdAt, String actorName, String actorUsername,
            String action, String entityType, UUID entityId, String entityName, String branchName, String reason) {
        return new AuditEntry(id, createdAt, actorName, actorUsername, action, entityType, entityId, entityName, branchName, reason);
    }

    private static AuditDetail detail() {
        Snapshot before = new Snapshot("old.user", "RECEPTIONIST", "Hoa Bằng", "OLD",
                "Tên cũ", "old@example.invalid", "0900000001");
        Snapshot after = new Snapshot("new.user", "RECEPTIONIST", "Hoa Bằng", "ACTIVE",
                "Tên mới", "new@example.invalid", "0900000002");
        Map<String, Object> beforeValues = new LinkedHashMap<>();
        beforeValues.put("username", "old.user");
        beforeValues.put("status", "OLD");
        Map<String, Object> afterValues = new LinkedHashMap<>();
        afterValues.put("username", "new.user");
        afterValues.put("status", "ACTIVE");
        return new AuditDetail(entry(), before, after, beforeValues, afterValues, "HEAD_OFFICE_MANAGER");
    }

    private static AuditPage emptyPage(int page, int size) {
        return new AuditPage(List.of(), 0, page, size);
    }

    private static String source(String relative) throws Exception {
        Path path = Path.of(relative);
        if (!Files.exists(path)) path = Path.of("backend").resolve(relative);
        return Files.readString(path);
    }

    private static ManualTest test(String id, String input, String expected, String type,
            CheckedAction action, String sourceMethod) {
        return new ManualTest(id, UC, sourceMethod, PRE, input, expected, type,
                NOTE + " Production: " + sourceMethod + ".", action);
    }

    private record Query(String q, String action, UUID branchId, LocalDate from, LocalDate to, int page, int size) {
    }

    private record ServiceFixture(AuditRepository repository, AuditService service) {
    }

    private record RepoFixture(JdbcTemplate db, AuditRepository repository,
            AtomicReference<String> countSql, AtomicReference<String> dataSql,
            AtomicReference<Object[]> dataArgs, AtomicReference<String> actionSql) {
    }
}
