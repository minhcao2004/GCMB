/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 29/10/2026
 * Description: Runs executable Combo Package Management manual unit tests.
 */
package com.gcmb.backend.manual.combopackagemanagement;

import com.gcmb.backend.pricing.dto.PriceCatalogItem;
import com.gcmb.backend.pricing.dto.PriceCommand;
import com.gcmb.backend.pricing.service.RoomCatalogService;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

public final class ComboPackageManagementManualRunner {
    static final UUID QUEEN = UUID.nameUUIDFromBytes("RT-QN".getBytes(StandardCharsets.UTF_8));
    static final UUID KING = UUID.nameUUIDFromBytes("RT-KG".getBytes(StandardCharsets.UTF_8));
    static final UUID COMBO = UUID.nameUUIDFromBytes("CIN_C1_QN".getBytes(StandardCharsets.UTF_8));
    static final UUID ABSENT = new UUID(0, 0);
    private static final int EXPECTED_CASES = 81;

    private ComboPackageManagementManualRunner() {}

    public static void main(String[] args) {
        List<ManualTest> tests = new ArrayList<>();
        tests.addAll(CreateComboPackageManualCases.cases());
        tests.addAll(ViewComboPackagesManualCases.cases());
        tests.addAll(UpdateComboPackageManualCases.cases());
        tests.addAll(DeleteComboPackageManualCases.cases());
        tests.sort(Comparator.comparing(ManualTest::id));
        validateCatalog(tests);
        if (args.length == 0) { menu(tests); return; }
        switch (args[0]) {
            case "--all" -> run(tests);
            case "--list" -> list(tests);
            case "--case" -> {
                if (args.length != 2) throw new IllegalArgumentException("--case <ID>");
                List<ManualTest> selected = tests.stream()
                    .filter(test -> test.id().equalsIgnoreCase(args[1])).toList();
                if (selected.isEmpty()) throw new IllegalArgumentException("Unknown case: " + args[1]);
                run(selected);
            }
            default -> throw new IllegalArgumentException("Options: --all | --list | --case <ID>");
        }
    }

    private static void validateCatalog(List<ManualTest> tests) {
        if (tests.size() != EXPECTED_CASES)
            throw new IllegalStateException("Expected " + EXPECTED_CASES + " cases, got " + tests.size());
        Set<String> ids = new HashSet<>();
        for (ManualTest test : tests)
            if (!test.id().matches("CPM-\\d{3}") || !ids.add(test.id()))
                throw new IllegalStateException("Invalid/duplicate ID: " + test.id());
    }

    private static void menu(List<ManualTest> tests) {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.println("\nCOMBO PACKAGE MANAGEMENT\n1. List test cases\n2. Run all test cases\n3. Run one test case theo ID\n0. Exit");
                if (!scanner.hasNextLine()) return;
                switch (scanner.nextLine().trim()) {
                    case "0" -> { return; }
                    case "1" -> list(tests);
                    case "2" -> run(tests);
                    case "3" -> {
                        System.out.print("Test Case ID: ");
                        if (!scanner.hasNextLine()) return;
                        String id = scanner.nextLine().trim();
                        run(tests.stream().filter(test -> test.id().equalsIgnoreCase(id)).toList());
                    }
                    default -> System.out.println("Select 0, 1, 2 or 3.");
                }
            }
        }
    }

    private static void list(List<ManualTest> tests) {
        tests.forEach(test -> System.out.println(test.id() + " | " + test.uc() + " | " + test.input()));
        System.out.println("Total: " + tests.size());
    }

    private static void run(List<ManualTest> tests) {
        System.out.println("MODE: production calls + recording JDBC; NO LIVE DATABASE.");
        if (tests.isEmpty()) { System.out.println("No matching case."); return; }
        int passed = 0;
        for (ManualTest test : tests) {
            TestResult result;
            try { result = test.action().run(); }
            catch (Throwable failure) { result = check(false, "Unexpected test/environment exception: " + failure); }
            if (result.passed()) passed++;
            System.out.println("\n[" + test.id() + "] " + test.uc());
            System.out.println("Source      : " + test.source());
            System.out.println("Precondition: " + test.pre());
            System.out.println("Input       : " + test.input());
            System.out.println("Expected    : " + test.expected());
            System.out.println("Actual      : " + result.actual());
            System.out.println("VERDICT     : " + (result.passed() ? "PASSED" : "FAILED"));
        }
        System.out.printf("%nSUMMARY: total=%d, passed=%d, failed=%d%n", tests.size(), passed, tests.size() - passed);
    }

    static ManualTest test(String id, String uc, String source, String pre, String input,
                           String expected, Action action) {
        return new ManualTest(id, uc, source, pre, input, expected, action);
    }
    @FunctionalInterface interface Action { TestResult run() throws Exception; }
    record ManualTest(String id, String uc, String source, String pre, String input,
                      String expected, Action action) {}
    record TestResult(boolean passed, String actual) {}
    static TestResult check(boolean passed, String actual) { return new TestResult(passed, actual); }

    static PriceCommand command(String code, String name, UUID room, Integer duration,
                                Integer from, Integer to, String price) {
        return command(code, name, room, duration, from, to, price, 0, 0);
    }

    static PriceCommand command(String code, String name, UUID room, Integer duration,
                                Integer from, Integer to, String price,
                                Integer includedFnbQuantity, Integer includedFoodQuantity) {
        return new PriceCommand(room, code, name, "2 giờ xem phim; 1 đồ ăn; 2 đồ uống tự chọn",
            duration, from, to, price == null ? null : new BigDecimal(price),
            includedFnbQuantity, includedFoodQuantity);
    }

    static PriceCommand valid() {
        return command("CPM_NEW_QN", "Combo Queen mới", QUEEN, 120, 0, 1440, "236000", 2, 1);
    }
    static PriceCommand change(String field, Object value) { return changeFrom(valid(), field, value); }

    static PriceCommand edited(String field, Object value) {
        Map<String, Object> row = new RecordingJdbc().records.get(COMBO);
        PriceCommand current = new PriceCommand(QUEEN, "CIN_C1_QN", (String) row.get("name"),
            (String) row.get("description"), (Integer) row.get("duration_minutes"),
            (Integer) row.get("minute_from"), (Integer) row.get("minute_to"),
            (BigDecimal) row.get("price"), (Integer) row.get("included_fnb_quantity"),
            (Integer) row.get("included_food_quantity"));
        return changeFrom(current, field, value);
    }

    private static PriceCommand changeFrom(PriceCommand command, String field, Object value) {
        return new PriceCommand(
            field.equals("room") ? (UUID) value : command.roomTypeId(),
            field.equals("code") ? (String) value : command.code(),
            field.equals("name") ? (String) value : command.name(),
            field.equals("description") ? (String) value : command.description(),
            field.equals("duration") ? (Integer) value : command.durationMinutes(),
            field.equals("from") ? (Integer) value : command.minuteFrom(),
            field.equals("to") ? (Integer) value : command.minuteTo(),
            field.equals("price") ? (value == null ? null : new BigDecimal(value.toString())) : command.price(),
            field.equals("fnb") ? (Integer) value : command.includedFnbQuantity(),
            field.equals("food") ? (Integer) value : command.includedFoodQuantity());
    }

    static void validate(PriceCommand command) throws Exception {
        Method method = RoomCatalogService.class.getDeclaredMethod("validatePrice", String.class, PriceCommand.class);
        method.setAccessible(true);
        try { method.invoke(null, "COMBO", command); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException cause) throw cause;
            throw failure;
        }
    }

    static TestResult reject(PriceCommand command, String expectedMessage) throws Exception {
        try {
            validate(command);
            return check(false, "Production validator ACCEPTED invalid input; expected=" + expectedMessage);
        } catch (ResponseStatusException failure) {
            boolean passed = failure.getStatusCode().value() == 400
                && Objects.equals(expectedMessage, failure.getReason());
            return check(passed, "status=" + failure.getStatusCode().value()
                + ", message=" + failure.getReason() + ", expected=" + expectedMessage);
        }
    }

    static TestResult invalidTime(PriceCommand command) throws Exception {
        try { validate(command); return check(false, "Production validator accepted an out-of-day minute"); }
        catch (ResponseStatusException failure) {
            return check(failure.getStatusCode().value() == 400,
                "status=" + failure.getStatusCode().value() + ", message=" + failure.getReason());
        }
    }

    static TestResult accept(PriceCommand command, boolean update) {
        RecordingJdbc jdbc = new RecordingJdbc();
        RoomCatalogService service = new RoomCatalogService(jdbc);
        try {
            PriceCatalogItem actual = update
                ? service.updatePrice("COMBO", COMBO, command)
                : service.createPrice("COMBO", command.roomTypeId(), command);
            String expectedCode = update ? "CIN_C1_QN" : command.code().trim().toUpperCase(Locale.ROOT);
            UUID expectedRoom = update && command.roomTypeId() == null ? QUEEN : command.roomTypeId();
            int expectedFrom = command.minuteFrom().equals(command.minuteTo()) ? 0 : command.minuteFrom();
            int expectedTo = command.minuteFrom().equals(command.minuteTo()) ? 1440
                : command.minuteTo() == 0 ? 1440 : command.minuteTo();
            int expectedFnb = command.includedFnbQuantity() == null ? 0 : command.includedFnbQuantity();
            int expectedFood = command.includedFoodQuantity() == null ? 0 : command.includedFoodQuantity();
            boolean passed = actual.code().equals(expectedCode)
                && actual.roomTypeId().equals(expectedRoom)
                && actual.name().equals(command.name().trim())
                && actual.description().equals(command.description() == null ? "" : command.description().trim())
                && actual.durationMinutes().equals(command.durationMinutes())
                && actual.minuteFrom().equals(expectedFrom) && actual.minuteTo().equals(expectedTo)
                && actual.price().compareTo(command.price()) == 0
                && Objects.equals(actual.includedFnbQuantity(), expectedFnb)
                && Objects.equals(actual.includedFoodQuantity(), expectedFood)
                && actual.active() && jdbc.insertCount == 1
                && (!update || !Boolean.TRUE.equals(jdbc.records.get(COMBO).get("active")));
            return check(passed, "returned=" + actual + "; insertCount=" + jdbc.insertCount
                + "; previousActive=" + jdbc.records.get(COMBO).get("active"));
        } catch (ResponseStatusException failure) {
            return check(false, "status=" + failure.getStatusCode().value() + ", message=" + failure.getReason());
        }
    }

    static TestResult missingRoom(boolean nonexistent) {
        RecordingJdbc jdbc = new RecordingJdbc();
        PriceCommand command = nonexistent ? change("room", ABSENT) : change("room", null);
        try {
            new RoomCatalogService(jdbc).createPrice("COMBO", command.roomTypeId(), command);
            return check(false, "Missing room accepted; insertCount=" + jdbc.insertCount);
        } catch (ResponseStatusException failure) {
            boolean passed = nonexistent ? failure.getStatusCode().value() == 404
                : failure.getStatusCode().value() == 400
                    && "Vui lòng bổ sung Hạng áp dụng.".equals(failure.getReason());
            return check(passed && jdbc.insertCount == 0,
                "status=" + failure.getStatusCode().value() + ", message=" + failure.getReason()
                    + ", insertCount=" + jdbc.insertCount);
        }
    }

    static TestResult duplicate() {
        RecordingJdbc jdbc = new RecordingJdbc();
        PriceCommand command = change("code", "CIN_C1_QN");
        try {
            new RoomCatalogService(jdbc).createPrice("COMBO", QUEEN, command);
            return check(false, "Duplicate was accepted");
        } catch (ResponseStatusException failure) {
            String expected = "Mã combo “CIN_C1_QN” đã tồn tại trong danh sách combo đang hoạt động. Vui lòng nhập mã khác.";
            return check(failure.getStatusCode().value() == 409
                    && expected.equals(failure.getReason()) && jdbc.insertCount == 0,
                "status=" + failure.getStatusCode().value() + ", message=" + failure.getReason()
                    + ", insertCount=" + jdbc.insertCount);
        }
    }

    static TestResult updateRoomRequired() {
        RecordingJdbc jdbc = new RecordingJdbc();
        try {
            new RoomCatalogService(jdbc).updatePrice("COMBO", COMBO, edited("room", null));
            return check(false, "Production updatePrice accepted missing room; insertCount=" + jdbc.insertCount);
        } catch (ResponseStatusException failure) {
            return check(failure.getStatusCode().value() == 400
                    && "Vui lòng bổ sung Hạng áp dụng.".equals(failure.getReason()) && jdbc.insertCount == 0,
                "status=" + failure.getStatusCode().value() + ", message=" + failure.getReason()
                    + ", insertCount=" + jdbc.insertCount);
        }
    }

    static TestResult notFound(boolean deletion) {
        RecordingJdbc jdbc = new RecordingJdbc();
        try {
            RoomCatalogService service = new RoomCatalogService(jdbc);
            if (deletion) service.deletePrice("COMBO", ABSENT);
            else service.updatePrice("COMBO", ABSENT, valid());
            return check(false, "Nonexistent combo was accepted");
        } catch (ResponseStatusException failure) {
            return check(failure.getStatusCode().value() == 404
                    && jdbc.insertCount == 0 && jdbc.deleteCount == 0,
                "status=" + failure.getStatusCode().value() + ", message=" + failure.getReason()
                    + ", insertCount=" + jdbc.insertCount + ", deleteCount=" + jdbc.deleteCount);
        }
    }

    static TestResult delete(boolean inactive) {
        RecordingJdbc jdbc = new RecordingJdbc("CIN_4H_QN");
        if (inactive) jdbc.records.get(COMBO).put("active", false);
        new RoomCatalogService(jdbc).deletePrice("COMBO", COMBO);
        return check(jdbc.deleteCount == 1 && !jdbc.records.containsKey(COMBO),
            "deleteCount=" + jdbc.deleteCount + ", selectedStillExists=" + jdbc.records.containsKey(COMBO));
    }

    static TestResult deleteReferenced() {
        RecordingJdbc jdbc = new RecordingJdbc("CIN_4H_QN");
        jdbc.blockDelete = true;
        try {
            new RoomCatalogService(jdbc).deletePrice("COMBO", COMBO);
            return check(false, "Referenced combo was deleted");
        } catch (ResponseStatusException failure) {
            String expected = "Không thể thực hiện cách xóa này vì sẽ làm mất hoặc thay đổi dữ liệu liên quan. Lịch sử nghiệp vụ và các bản ghi liên quan phải được giữ nguyên.";
            return check(failure.getStatusCode().value() == 409
                    && expected.equals(failure.getReason()) && jdbc.records.containsKey(COMBO),
                "status=" + failure.getStatusCode().value() + ", message=" + failure.getReason()
                    + ", selectedStillExists=" + jdbc.records.containsKey(COMBO));
        }
    }

    static TestResult listWithQuantities() {
        RecordingJdbc jdbc = new RecordingJdbc();
        List<PriceCatalogItem> result = new RoomCatalogService(jdbc).prices("COMBO", null, false);
        boolean passed = result.size() == 1 && result.get(0).code().equals("CIN_C1_QN")
            && Objects.equals(result.get(0).includedFnbQuantity(), 2)
            && Objects.equals(result.get(0).includedFoodQuantity(), 1);
        return check(passed, "result=" + result);
    }

    static TestResult listActiveByRoom() {
        RecordingJdbc jdbc = new RecordingJdbc();
        UUID inactiveId = UUID.nameUUIDFromBytes("INACTIVE_KING".getBytes(StandardCharsets.UTF_8));
        jdbc.records.put(inactiveId, RecordingJdbc.row(inactiveId, "CIN_INACTIVE_KG", 1L, KING,
            "Combo King inactive", "", 120, 0, 1440, new BigDecimal("246000"), false, 0, 0));
        List<PriceCatalogItem> result = new RoomCatalogService(jdbc).prices("COMBO", QUEEN, true);
        boolean passed = result.size() == 1 && result.get(0).roomTypeId().equals(QUEEN)
            && result.get(0).active();
        return check(passed, "result=" + result);
    }

    static TestResult listEmpty() {
        RecordingJdbc jdbc = new RecordingJdbc();
        jdbc.records.clear();
        List<PriceCatalogItem> result = new RoomCatalogService(jdbc).prices("COMBO", null, false);
        return check(result.isEmpty(), "resultCount=" + result.size());
    }

    static final class RecordingJdbc extends JdbcTemplate {
        final Map<UUID, Map<String, Object>> records = new LinkedHashMap<>();
        int insertCount;
        int deleteCount;
        boolean blockDelete;

        RecordingJdbc() { this("CIN_C1_QN"); }
        RecordingJdbc(String seedCode) {
            if ("CIN_C1_QN".equals(seedCode)) {
                records.put(COMBO, row(COMBO, seedCode, 1L, QUEEN, "Combo 1 · 2 giờ",
                    "2 giờ xem phim; 1 đồ ăn; 2 đồ uống tự chọn", 120, 0, 1440,
                    new BigDecimal("236000"), true, 2, 1));
            } else if ("CIN_4H_QN".equals(seedCode)) {
                records.put(COMBO, row(COMBO, seedCode, 1L, QUEEN, "Combo Queen · 4 giờ",
                    "4 giờ xem phim", 240, 0, 1440, new BigDecimal("430000"), true, 0, 0));
            } else throw new IllegalArgumentException("Unsupported demo combo: " + seedCode);
        }

        static Map<String, Object> row(UUID id, String code, Object version, UUID room, String name,
                                       String description, Integer duration, Integer from, Integer to,
                                       BigDecimal price, boolean active, Integer includedFnbQuantity,
                                       Integer includedFoodQuantity) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", id); row.put("kind", "COMBO"); row.put("code", code); row.put("version", version);
            row.put("room_type_id", room); row.put("room_type_name", KING.equals(room) ? "King" : "Queen");
            row.put("name", name); row.put("description", description); row.put("duration_minutes", duration);
            row.put("minute_from", from); row.put("minute_to", to); row.put("price", price);
            row.put("active", active); row.put("branch_id", null);
            row.put("included_fnb_quantity", includedFnbQuantity);
            row.put("included_food_quantity", includedFoodQuantity);
            return row;
        }

        @Override public <T> T queryForObject(String sql, Class<T> type, Object... args) {
            long value;
            if (sql.contains("COUNT(*)") && sql.contains("gcmb.combo")) {
                value = records.values().stream()
                    .filter(row -> Objects.equals(row.get("code"), args[0])
                        && Boolean.TRUE.equals(row.get("active"))).count();
            } else if (sql.contains("MAX(version)")) {
                value = records.values().stream().filter(row -> Objects.equals(row.get("code"), args[0]))
                    .mapToLong(row -> ((Number) row.get("version")).longValue()).max().orElse(0) + 1;
            } else throw new IllegalStateException("Unexpected production SQL: " + sql);
            if (type.equals(Integer.class)) return type.cast(Integer.valueOf((int) value));
            return type.cast(Long.valueOf(value));
        }

        @Override public int update(String sql, Object... args) {
            if (sql.contains("INSERT INTO gcmb.combo")) {
                UUID id = (UUID) args[0];
                records.put(id, row(id, (String) args[1], args[2], (UUID) args[3],
                    (String) args[5], (String) args[12], (Integer) args[6], (Integer) args[8],
                    (Integer) args[9], (BigDecimal) args[7], true, (Integer) args[10], (Integer) args[11]));
                insertCount++;
                return 1;
            }
            if (sql.startsWith("UPDATE gcmb.combo SET active=false")) {
                Map<String, Object> row = records.get((UUID) args[0]);
                if (row == null) return 0;
                row.put("active", false);
                return 1;
            }
            if (sql.startsWith("DELETE FROM gcmb.combo_item")) return 0;
            if (sql.startsWith("DELETE FROM gcmb.combo")) {
                if (blockDelete) throw new DataIntegrityViolationException("referenced combo");
                if (records.remove((UUID) args[0]) == null) return 0;
                deleteCount++;
                return 1;
            }
            throw new IllegalStateException("Unexpected production DML: " + sql);
        }

        @Override public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
            List<Map<String, Object>> rows = new ArrayList<>();
            if (sql.startsWith("SELECT id,code,name,mode,capacity,description,active FROM gcmb.room_type")) {
                UUID room = (UUID) args[0];
                if (QUEEN.equals(room) || KING.equals(room)) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", room); row.put("code", QUEEN.equals(room) ? "RT-QN" : "RT-KG");
                    row.put("name", QUEEN.equals(room) ? "Queen" : "King"); row.put("mode", "CINEMA");
                    row.put("capacity", QUEEN.equals(room) ? 4 : 6); row.put("description", "Demo room type");
                    row.put("active", true); rows.add(row);
                }
            } else if (sql.contains(") catalogue WHERE")) {
                UUID roomFilter = args.length == 3 ? (UUID) args[2] : null;
                boolean activeOnly = sql.contains("AND active=true");
                records.values().stream()
                    .filter(row -> roomFilter == null || roomFilter.equals(row.get("room_type_id")))
                    .filter(row -> !activeOnly || Boolean.TRUE.equals(row.get("active")))
                    .sorted(Comparator.comparing(row -> row.get("room_type_name") + ":" + row.get("code")))
                    .forEach(rows::add);
            } else if (sql.contains("WHERE c.id=?")) {
                Map<String, Object> row = records.get((UUID) args[0]);
                if (row != null) rows.add(row);
            } else throw new IllegalStateException("Unexpected production query: " + sql);
            List<T> result = new ArrayList<>();
            try {
                for (int index = 0; index < rows.size(); index++)
                    result.add(mapper.mapRow(resultSet(rows.get(index)), index));
            } catch (java.sql.SQLException failure) { throw new IllegalStateException(failure); }
            return result;
        }

        private static ResultSet resultSet(Map<String, Object> row) {
            return (ResultSet) Proxy.newProxyInstance(ComboPackageManagementManualRunner.class.getClassLoader(),
                new Class<?>[]{ResultSet.class}, (proxy, method, args) -> {
                    if (method.getName().equals("toString")) return "RecordingResultSet" + row;
                    Object value = args != null && args.length > 0 ? row.get(String.valueOf(args[0])) : null;
                    return switch (method.getName()) {
                        case "getObject", "getBigDecimal" -> value;
                        case "getString" -> value == null ? null : value.toString();
                        case "getInt" -> value == null ? 0 : ((Number) value).intValue();
                        case "getBoolean" -> Boolean.TRUE.equals(value);
                        case "wasNull", "isClosed" -> false;
                        case "close" -> null;
                        default -> throw new UnsupportedOperationException("ResultSet adapter: " + method);
                    };
                });
        }
    }
}
