/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 27/10/2026
 * Description: Runs Personal Account manual unit tests and keeps their explicit spreadsheet IDs.
 */
package com.gcmb.backend.manual.personalaccount;

import com.gcmb.backend.auth.exception.AuthFailure;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;

public final class PersonalAccountManualRunner {
    private static final int EXPECTED_CASES = 102;

    private PersonalAccountManualRunner() {
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
            case "--summary" -> summary(cases);
            case "--case" -> {
                if (args.length < 2) {
                    System.out.println("Usage: --case <Test Case ID>");
                    return;
                }
                run(cases.stream().filter(test -> test.id().equalsIgnoreCase(args[1])).toList());
            }
            default -> System.out.println("Options: --all | --list | --summary | --case <ID>");
        }
    }

    private static List<ManualTest> allTests() {
        List<ManualTest> cases = new ArrayList<>();
        cases.addAll(LoginManualCases.cases());
        cases.addAll(ChangePasswordManualCases.cases());
        cases.addAll(ResetPasswordManualCases.cases());
        cases.addAll(UpdateAccountInformationManualCases.cases());
        cases.addAll(SecurityAndMailerManualCases.cases());
        cases.sort(Comparator.comparing(ManualTest::id));
        validate(cases, "PAM", EXPECTED_CASES);
        return List.copyOf(cases);
    }

    private static void menu(List<ManualTest> cases) {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.println("\nPERSONAL ACCOUNT MANAGEMENT - MANUAL UNIT TEST");
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
        cases.forEach(test -> System.out.printf("%-8s | %-28s | %s%n",
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

    private static void summary(List<ManualTest> cases) {
        int passed = 0;
        StringBuilder failedIds = new StringBuilder();
        for (ManualTest test : cases) {
            TestResult result = test.execute();
            if (result.passed()) {
                passed++;
            } else {
                if (!failedIds.isEmpty()) failedIds.append(",");
                failedIds.append(test.id());
            }
        }
        System.out.printf("SUMMARY: total=%d, passed=%d, failed=%d%n",
                cases.size(), passed, cases.size() - passed);
        System.out.println("FAILED_IDS: " + failedIds);
    }

    private static void validate(List<ManualTest> cases, String prefix, int expectedCount) {
        if (cases.size() != expectedCount) {
            throw new IllegalStateException("Expected " + expectedCount + " cases but found " + cases.size());
        }
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < cases.size(); index++) {
            ManualTest test = cases.get(index);
            String expectedId = "%s-%03d".formatted(prefix, index + 1);
            if (!expectedId.equals(test.id()) || !ids.add(test.id())) {
                throw new IllegalStateException("Invalid or duplicate Test Case ID: " + test.id());
            }
        }
    }

    public static TestResult check(boolean condition, String actual) {
        return new TestResult(condition, actual);
    }

    public static TestResult expectAuthFailure(
            ThrowingRunnable action, int expectedStatus, String expectedMessage) {
        try {
            action.run();
            return check(false, "No AuthFailure was thrown");
        } catch (AuthFailure failure) {
            boolean passed = failure.status() == expectedStatus
                    && Objects.equals(expectedMessage, failure.getMessage());
            return check(passed, "status=" + failure.status() + ", message=" + failure.getMessage());
        } catch (Throwable failure) {
            return check(false, "Unexpected " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    public static TestResult expectSuccess(ThrowingRunnable action, String actualWhenPassed) {
        try {
            action.run();
            return check(true, actualWhenPassed);
        } catch (Throwable failure) {
            return check(false, "Unexpected " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    @FunctionalInterface
    public interface CheckedAction {
        TestResult run() throws Exception;
    }

    public record TestResult(boolean passed, String actual) {
    }

    public record ManualTest(
            String id,
            String useCase,
            String sourceMethod,
            String preconditions,
            String input,
            String expected,
            String type,
            String note,
            CheckedAction action
    ) {
        public ManualTest {
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
