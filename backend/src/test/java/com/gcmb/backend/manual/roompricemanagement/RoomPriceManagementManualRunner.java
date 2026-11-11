/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 28/10/2026
 * Description: Runs Room Price Management manual unit tests with spreadsheet test case IDs.
 */
package com.gcmb.backend.manual.roompricemanagement;

import com.gcmb.backend.pricing.dto.PriceRuleCommand;
import com.gcmb.backend.pricing.dto.PriceRuleView;
import com.gcmb.backend.pricing.dto.RoomTypeView;
import com.gcmb.backend.pricing.service.RoomCatalogService;
import java.math.BigDecimal;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

public final class RoomPriceManagementManualRunner {
    private static final int EXPECTED_CASES = 47;

    private RoomPriceManagementManualRunner() {
    }

    public static void main(String[] args) {
        List<ManualTest> cases = allTests();
        if (args.length == 0) {
            menu(cases);
            return;
        }
        switch (args[0]) {
            case "--all" -> run(cases);
            case "--list" -> list(cases);
            case "--case" -> run(args.length < 2 ? List.of()
                    : cases.stream().filter(test -> test.id().equalsIgnoreCase(args[1])).toList());
            default -> System.out.println("Options: --all | --list | --case <ID>");
        }
    }

    private static List<ManualTest> allTests() {
        List<ManualTest> cases = new ArrayList<>();
        cases.addAll(CreateRoomPriceManualCases.cases());
        cases.addAll(ViewRoomPricesManualCases.cases());
        cases.addAll(UpdateRoomPriceManualCases.cases());
        cases.addAll(ChangeRoomPriceStatusManualCases.cases());
        cases.sort(Comparator.comparing(ManualTest::id));
        validate(cases);
        return List.copyOf(cases);
    }

    private static void menu(List<ManualTest> cases) {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.println("\nROOM PRICE MANAGEMENT - MANUAL UNIT TEST");
                System.out.println("1. List test cases");
                System.out.println("2. Run all test cases");
                System.out.println("3. Run one test case");
                System.out.println("0. Exit");
                System.out.print("Select: ");
                String option = scanner.nextLine().trim();
                if ("0".equals(option)) return;
                if ("1".equals(option)) list(cases);
                if ("2".equals(option)) run(cases);
                if ("3".equals(option)) {
                    System.out.print("Test Case ID: ");
                    String id = scanner.nextLine().trim();
                    run(cases.stream().filter(test -> test.id().equalsIgnoreCase(id)).toList());
                }
            }
        }
    }

    private static void list(List<ManualTest> cases) {
        cases.forEach(test -> System.out.printf("%-8s | %-35s | %s%n",
                test.id(), test.useCase(), test.sourceMethod()));
        System.out.println("Total: " + cases.size());
    }

    private static void run(List<ManualTest> cases) {
        if (cases.isEmpty()) {
            System.out.println("No matching test case.");
            return;
        }
        int passed = 0;
        for (ManualTest test : cases) {
            TestResult result = test.execute();
            if (result.passed()) passed++;
            System.out.println("\n[" + test.id() + "] " + test.useCase());
            System.out.println("Source      : " + test.sourceMethod());
            System.out.println("Precondition: " + test.preconditions());
            System.out.println("Input       : " + test.input());
            System.out.println("Expected    : " + test.expected());
            System.out.println("Actual      : " + result.actual());
            System.out.println("VERDICT     : " + (result.passed() ? "PASSED" : "FAILED"));
        }
        System.out.printf("%nSUMMARY: total=%d, passed=%d, failed=%d%n",
                cases.size(), passed, cases.size() - passed);
    }

    private static void validate(List<ManualTest> cases) {
        if (cases.size() != EXPECTED_CASES) {
            throw new IllegalStateException("Expected " + EXPECTED_CASES + " cases but found " + cases.size());
        }
        Set<String> ids = new HashSet<>();
        for (ManualTest test : cases) {
            if (!test.id().matches("RPM-\\d{3}") || !ids.add(test.id())) {
                throw new IllegalStateException("Invalid or duplicate Test Case ID: " + test.id());
            }
        }
    }

    public static ManualTest test(String id, String useCase, String sourceMethod,
            String preconditions, String input, String expected, CheckedAction action) {
        return new ManualTest(id, useCase, sourceMethod, preconditions, input, expected, action);
    }

    public static TestResult check(boolean condition, String actual) {
        return new TestResult(condition, actual);
    }

    public static TestResult validateAccepted(PriceRuleCommand input) {
        try {
            invokeValidate(input);
            return check(true, "Production validation accepted the command");
        } catch (ResponseStatusException failure) {
            return check(false, "status=" + failure.getStatusCode().value() + ", message=" + failure.getReason());
        } catch (Throwable failure) {
            return check(false, "Unexpected " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    public static TestResult validateRejectedStatus(PriceRuleCommand input) {
        try {
            invokeValidate(input);
            return check(false, "Production validation accepted invalid input");
        } catch (ResponseStatusException failure) {
            return check(failure.getStatusCode().value() == 400,
                    "status=" + failure.getStatusCode().value() + ", message=" + failure.getReason());
        } catch (Throwable failure) {
            return check(false, "Unexpected " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    public static TestResult validateRejectedMessage(PriceRuleCommand input, String expectedMessage) {
        try {
            invokeValidate(input);
            return check(false, "Production validation accepted invalid input");
        } catch (ResponseStatusException failure) {
            boolean passed = failure.getStatusCode().value() == 400
                    && Objects.equals(expectedMessage, failure.getReason());
            return check(passed, "status=" + failure.getStatusCode().value()
                    + ", message=" + failure.getReason() + ", expected=" + expectedMessage);
        } catch (Throwable failure) {
            return check(false, "Unexpected " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    private static void invokeValidate(PriceRuleCommand input) throws Throwable {
        try {
            Method method = RoomCatalogService.class.getDeclaredMethod("validatePriceRule", PriceRuleCommand.class);
            method.setAccessible(true);
            method.invoke(null, input);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    public static TestResult combine(TestResult... results) {
        boolean passed = true;
        List<String> actual = new ArrayList<>();
        for (TestResult result : results) {
            passed &= result.passed();
            actual.add(result.actual());
        }
        return check(passed, actual.stream().collect(Collectors.joining("; ")));
    }

    public static TestResult expectStatus(ThrowingRunnable action, int status, String message) {
        try {
            action.run();
            return check(false, "Không phát sinh ResponseStatusException");
        } catch (ResponseStatusException failure) {
            boolean passed = failure.getStatusCode().value() == status
                    && Objects.equals(message, failure.getReason());
            return check(passed, "status=" + failure.getStatusCode().value()
                    + ", message=" + failure.getReason());
        } catch (Throwable failure) {
            return check(false, "Unexpected " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    public static RoomTypeView roomType(UUID id, String code, String name, boolean active) {
        return new RoomTypeView(id, code, name, "CINEMA", 4, "Mô tả", active);
    }

    public static PriceRuleView priceRule(UUID id, String code, int version,
            String name, boolean active) {
        return new PriceRuleView(id, code, version,
                UUID.fromString("00000000-0000-0000-0000-000000000101"),
                "Queen", name, List.of(1, 2, 3, 4, 5),
                LocalDate.of(2026, 10, 28), null, 480, 1320,
                new BigDecimal("96000"), active);
    }

    @FunctionalInterface
    public interface CheckedAction {
        TestResult run() throws Exception;
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    public static final class ScriptedJdbc extends JdbcTemplate {
        public final Deque<Function<Object[], List<?>>> queryResults = new ArrayDeque<>();
        public final Deque<Object> objects = new ArrayDeque<>();
        public final Deque<Integer> updateResults = new ArrayDeque<>();
        public final List<SqlCall> reads = new ArrayList<>();
        public final List<SqlCall> updates = new ArrayList<>();
        public int executeCalls;

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper) {
            return nextQuery(sql, new Object[0]);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            return nextQuery(sql, args);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            reads.add(new SqlCall(sql, args));
            return (T) objects.removeFirst();
        }

        @Override
        public int update(String sql, Object... args) {
            updates.add(new SqlCall(sql, args));
            return updateResults.isEmpty() ? 1 : updateResults.removeFirst();
        }

        @Override
        public <T> T execute(ConnectionCallback<T> action) {
            executeCalls++;
            return null;
        }

        @SuppressWarnings("unchecked")
        private <T> List<T> nextQuery(String sql, Object[] args) {
            reads.add(new SqlCall(sql, args));
            return (List<T>) queryResults.removeFirst().apply(args);
        }
    }

    public record SqlCall(String sql, Object[] args) {
    }

    public record TestResult(boolean passed, String actual) {
    }

    public record ManualTest(String id, String useCase, String sourceMethod,
                             String preconditions, String input, String expected,
                             CheckedAction action) {
        public ManualTest {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(useCase, "useCase");
            Objects.requireNonNull(sourceMethod, "sourceMethod");
            Objects.requireNonNull(preconditions, "preconditions");
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(expected, "expected");
            Objects.requireNonNull(action, "action");
        }

        public TestResult execute() {
            try {
                TestResult result = action.run();
                return result == null ? check(false, "Test action returned no result") : result;
            } catch (Throwable failure) {
                return check(false, "Unexpected " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
            }
        }
    }
}
