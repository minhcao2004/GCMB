/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 28/10/2026
 * Description: Runs Room Type Management manual unit tests with spreadsheet test case IDs.
 */
package com.gcmb.backend.manual.roomtypemanagement;

import com.gcmb.backend.pricing.dto.RoomTypeCommand;
import com.gcmb.backend.pricing.service.RoomCatalogService;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.web.server.ResponseStatusException;

public final class RoomTypeManagementManualRunner {
    private static final int EXPECTED_CASES = 16;

    private RoomTypeManagementManualRunner() {
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
        cases.addAll(CreateRoomTypeManualCases.cases());
        cases.addAll(UpdateRoomTypeManualCases.cases());
        cases.sort(Comparator.comparing(ManualTest::id));
        validate(cases);
        return List.copyOf(cases);
    }

    private static void menu(List<ManualTest> cases) {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.println("\nROOM TYPE MANAGEMENT - MANUAL UNIT TEST");
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
        cases.forEach(test -> System.out.printf("%-8s | %-31s | %s%n",
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
            if (!test.id().matches("RTM-\\d{3}") || !ids.add(test.id())) {
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

    public static TestResult validateAccepted(RoomTypeCommand input) {
        try {
            invokeValidate(input);
            return check(true, "Production validation accepted the command");
        } catch (ResponseStatusException failure) {
            return check(false, "status=" + failure.getStatusCode().value()
                    + ", message=" + failure.getReason());
        } catch (Throwable failure) {
            return check(false, "Unexpected " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    public static TestResult validateRejectedStatus(RoomTypeCommand input) {
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

    public static TestResult validateRejectedMessage(RoomTypeCommand input, String... acceptedMessages) {
        try {
            invokeValidate(input);
            return check(false, "Production validation accepted invalid input");
        } catch (ResponseStatusException failure) {
            boolean messageMatched = List.of(acceptedMessages).contains(failure.getReason());
            return check(failure.getStatusCode().value() == 400 && messageMatched,
                    "status=" + failure.getStatusCode().value() + ", message=" + failure.getReason()
                            + ", expectedOneOf=" + List.of(acceptedMessages));
        } catch (Throwable failure) {
            return check(false, "Unexpected " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    private static void invokeValidate(RoomTypeCommand input) throws Throwable {
        try {
            Method method = RoomCatalogService.class.getDeclaredMethod("validateRoomType", RoomTypeCommand.class);
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

    @FunctionalInterface
    public interface CheckedAction {
        TestResult run() throws Exception;
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
