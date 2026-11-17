/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 13/11/2026
 * Description: Chạy thủ công các unit test cho Shift Management (UC-83 đến UC-90).
 */
package com.gcmb.backend.manual.shiftmanagement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Pattern;

public final class ShiftManagementManualRunner {
    private static final Pattern ID_PATTERN = Pattern.compile("SHM-\\d{3}");

    private ShiftManagementManualRunner() {
    }

    @FunctionalInterface
    public interface Execution {
        Observation run() throws Exception;
    }

    public record Observation(String actual, boolean passed) {
    }

    public record ManualCase(
        String id,
        String title,
        String source,
        String precondition,
        String input,
        String expected,
        Execution execution
    ) {
    }

    public static void main(String[] args) {
        List<ManualCase> cases = allCases();
        validate(cases);
        if (args.length == 0) {
            menu(cases);
        } else if (args.length == 1 && "--list".equals(args[0])) {
            list(cases);
        } else if (args.length == 1 && "--all".equals(args[0])) {
            run(cases);
        } else if (args.length == 2 && "--case".equals(args[0])) {
            run(List.of(find(cases, args[1])));
        } else {
            usage();
            System.exit(2);
        }
    }

    private static List<ManualCase> allCases() {
        List<ManualCase> cases = new ArrayList<>();
        cases.addAll(RegisterForShiftManualCases.cases());
        cases.addAll(ViewShiftScheduleManualCases.cases());
        cases.addAll(SetWeeklyShiftAvailabilityManualCases.cases());
        cases.addAll(UpdateShiftScheduleManualCases.cases());
        cases.addAll(RecordDailyAttendanceManualCases.cases());
        cases.addAll(ViewAttendanceRecordsManualCases.cases());
        cases.addAll(CloseMonthlyAttendanceSheetManualCases.cases());
        cases.addAll(ViewPersonalWorkRecordsManualCases.cases());
        return List.copyOf(cases);
    }

    private static void validate(List<ManualCase> cases) {
        Set<String> ids = new HashSet<>();
        for (ManualCase testCase : cases) {
            if (!ID_PATTERN.matcher(testCase.id()).matches()) {
                throw new IllegalStateException("Invalid Test Case ID: " + testCase.id());
            }
            if (!ids.add(testCase.id())) {
                throw new IllegalStateException("Duplicate Test Case ID: " + testCase.id());
            }
        }
    }

    private static void menu(List<ManualCase> cases) {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.println("\nSHIFT MANAGEMENT MANUAL RUNNER");
                System.out.println("1. List cases");
                System.out.println("2. Run all");
                System.out.println("3. Run one case");
                System.out.println("0. Exit");
                System.out.print("Select: ");
                switch (scanner.nextLine().trim()) {
                    case "1" -> list(cases);
                    case "2" -> run(cases);
                    case "3" -> {
                        System.out.print("Test Case ID: ");
                        run(List.of(find(cases, scanner.nextLine().trim())));
                    }
                    case "0" -> {
                        return;
                    }
                    default -> System.out.println("Invalid selection.");
                }
            }
        }
    }

    private static ManualCase find(List<ManualCase> cases, String id) {
        return cases.stream()
            .filter(testCase -> testCase.id().equalsIgnoreCase(id))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown Test Case ID: " + id));
    }

    private static void list(List<ManualCase> cases) {
        cases.forEach(testCase -> System.out.println(testCase.id() + " | " + testCase.title()));
        System.out.println("TOTAL: " + cases.size());
    }

    private static void run(List<ManualCase> cases) {
        int passed = 0;
        for (ManualCase testCase : cases) {
            Observation observation;
            try {
                observation = testCase.execution().run();
            } catch (Throwable failure) {
                observation = new Observation(
                    failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage()),
                    false
                );
            }
            if (observation.passed()) {
                passed++;
            }
            print(testCase, observation);
        }
        System.out.printf(
            "SUMMARY: total=%d, passed=%d, failed=%d%n",
            cases.size(),
            passed,
            cases.size() - passed
        );
    }

    private static void print(ManualCase testCase, Observation observation) {
        System.out.println("\n------------------------------------------------------------");
        System.out.println("ID           : " + testCase.id());
        System.out.println("UC / Title   : " + testCase.title());
        System.out.println("Source       : " + testCase.source());
        System.out.println("Precondition : " + testCase.precondition());
        System.out.println("Input        : " + testCase.input());
        System.out.println("Expected     : " + testCase.expected());
        System.out.println("Actual       : " + observation.actual());
        System.out.println("Verdict      : " + (observation.passed() ? "Passed" : "Failed"));
    }

    private static void usage() {
        System.out.println("Usage:");
        System.out.println("  --list");
        System.out.println("  --all");
        System.out.println("  --case SHM-001");
    }
}
