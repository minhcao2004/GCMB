package com.gcmb.backend.manual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public final class ManualTestRunner {
    private static final String LINE = "====================================";

    private ManualTestRunner() {
    }

    public static void main(String[] args) {
        List<ManualTestSuite> suites = ManualTestRegistry.suites();
        if (args.length == 1 && "--list".equals(args[0])) {
            printAll(suites);
            return;
        }
        if (args.length == 2 && "--case".equals(args[0])) {
            findByKey(suites, args[1]);
            return;
        }
        menu(suites, new Scanner(System.in));
    }

    private static void menu(List<ManualTestSuite> suites, Scanner scanner) {
        Map<String, List<ManualTestSuite>> modules = new LinkedHashMap<>();
        for (ManualTestSuite suite : suites) {
            modules.computeIfAbsent(suite.module(), ignored -> new ArrayList<>()).add(suite);
        }
        while (true) {
            System.out.println();
            System.out.println(LINE);
            System.out.println("       GCMB MANUAL UNIT TEST");
            System.out.println(LINE);
            int option = 1;
            List<String> moduleNames = new ArrayList<>(modules.keySet());
            for (String module : moduleNames) {
                System.out.println(option++ + ". " + module);
            }
            System.out.println(option + ". View All Test Cases");
            System.out.println("0. Exit");
            int selected = readChoice(scanner, "Select option: ", 0, option);
            if (selected == 0) {
                return;
            }
            if (selected == option) {
                printAll(suites);
                continue;
            }
            suiteMenu(moduleNames.get(selected - 1), modules.get(moduleNames.get(selected - 1)), scanner);
        }
    }

    private static void suiteMenu(String module, List<ManualTestSuite> suites, Scanner scanner) {
        while (true) {
            System.out.println();
            System.out.println(LINE);
            System.out.println("       " + module.toUpperCase() + " TESTS");
            System.out.println(LINE);
            for (int index = 0; index < suites.size(); index++) {
                System.out.println((index + 1) + ". " + suites.get(index).name());
            }
            System.out.println("0. Back");
            int selected = readChoice(scanner, "Select option: ", 0, suites.size());
            if (selected == 0) {
                return;
            }
            caseMenu(suites.get(selected - 1), scanner);
        }
    }

    private static void caseMenu(ManualTestSuite suite, Scanner scanner) {
        while (true) {
            System.out.println();
            System.out.println(LINE);
            System.out.println("       " + suite.name().toUpperCase() + " MANUAL TEST");
            System.out.println(LINE);
            for (int index = 0; index < suite.cases().size(); index++) {
                ManualTestCase testCase = suite.cases().get(index);
                System.out.println((index + 1) + ". " + testCase.displayId()
                        + " — " + testCase.sourceMethod());
            }
            System.out.println("0. Back");
            int selected = readChoice(scanner, "Select Test Case: ", 0, suite.cases().size());
            if (selected == 0) {
                return;
            }
            execute(suite.cases().get(selected - 1));
        }
    }

    private static int readChoice(Scanner scanner, String prompt, int minimum, int maximum) {
        while (true) {
            System.out.print(prompt);
            if (!scanner.hasNextLine()) {
                return 0;
            }
            String text = scanner.nextLine().trim();
            try {
                int value = Integer.parseInt(text);
                if (value >= minimum && value <= maximum) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // Print the same concise prompt for malformed menu input.
            }
            System.out.println("Please enter a number from " + minimum + " to " + maximum + ".");
        }
    }

    private static void execute(ManualTestCase testCase) {
        System.out.println();
        System.out.println(LINE);
        System.out.println("TEST CASE ID: " + testCase.displayId());
        System.out.println("TEST KEY: " + testCase.key());
        System.out.println("TEST CLASS: " + testCase.sourceClass());
        System.out.println("TEST METHOD: " + testCase.sourceMethod());
        System.out.println("TEST TYPE: " + testCase.testType());
        System.out.println("TRACEABILITY: " + testCase.traceability());
        System.out.println(LINE);
        section("PRECONDITIONS", testCase.preconditions());
        section("INPUT", testCase.input());
        section("EXPECTED RESULT", testCase.expectedResult());
        System.out.println("EXECUTING...");
        System.out.println();
        try {
            ManualTestResult result = testCase.executor().execute();
            section("ACTUAL RESULT", result.actualResult());
            section("EXCEPTION", result.exception());
            section("SIDE EFFECTS", result.sideEffects());
        } catch (Throwable setupFailure) {
            section("SETUP ERROR",
                    setupFailure.getClass().getName() + ": " + String.valueOf(setupFailure.getMessage()));
            section("ACTUAL RESULT", "Business method was not observed because setup failed");
            section("EXCEPTION", "Not available");
            section("SIDE EFFECTS", "Not available");
        }
        System.out.println(LINE);
        System.out.println("MANUAL VERIFICATION REQUIRED");
        System.out.println(LINE);
        System.out.println("Compare Actual Result with Expected Result");
        System.out.println("and record Pass/Fail in Unit Test Excel.");
    }

    private static void section(String heading, String value) {
        System.out.println(heading + ":");
        System.out.println(value == null || value.isBlank() ? "N/A" : value);
        System.out.println();
    }

    private static void printAll(List<ManualTestSuite> suites) {
        int total = 0;
        for (ManualTestSuite suite : suites) {
            System.out.println();
            System.out.println("[" + suite.module() + " / " + suite.name() + "]");
            for (ManualTestCase testCase : suite.cases()) {
                total++;
                System.out.println(testCase.key() + " | " + testCase.displayId()
                        + " | " + testCase.sourceClass() + "." + testCase.sourceMethod()
                        + " | " + testCase.traceability());
            }
        }
        System.out.println();
        System.out.println("Total selectable manual test cases: " + total);
        System.out.println("No test case was executed.");
    }

    private static void findByKey(List<ManualTestSuite> suites, String key) {
        for (ManualTestSuite suite : suites) {
            for (ManualTestCase testCase : suite.cases()) {
                if (testCase.key().equals(key)) {
                    execute(testCase);
                    return;
                }
            }
        }
        System.out.println("Unknown manual test key: " + key);
        System.out.println("Use --list to view available keys. No test case was executed.");
    }
}
