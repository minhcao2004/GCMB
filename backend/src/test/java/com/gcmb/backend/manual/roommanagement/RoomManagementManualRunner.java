/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 29/10/2026
 * Description: Runs Room Management manual unit tests with explicit spreadsheet test case IDs.
 */
package com.gcmb.backend.manual.roommanagement;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.branch.dto.RoomCommand;
import com.gcmb.backend.branch.dto.RoomView;
import com.gcmb.backend.branch.repository.BranchRoomRepository;
import com.gcmb.backend.branch.service.BranchRoomService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.server.ResponseStatusException;

public final class RoomManagementManualRunner {
    private static final int EXPECTED_CASES = 40;

    static final UUID ACTOR_ID = UUID.fromString("47570000-0000-4000-8000-000000000003");
    static final UUID BRANCH_ID = UUID.fromString("34370000-0000-4000-8000-000000000001");
    static final UUID ROOM_ID = UUID.fromString("34370000-0000-4000-8000-000000000101");
    static final UUID ROOM_TYPE_ID = UUID.fromString("34370000-0000-4000-8000-000000000201");
    static final String BRANCH_NAME = "Hoa Bằng";
    static final String SUSPENDED_MESSAGE = "Cơ sở đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi.";

    private RoomManagementManualRunner() {
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
            case "--case" -> {
                if (args.length < 2) {
                    System.out.println("Usage: --case <Test Case ID>");
                    return;
                }
                run(cases.stream().filter(test -> test.id().equalsIgnoreCase(args[1])).toList());
            }
            default -> System.out.println("Options: --all | --list | --case <ID>");
        }
    }

    private static List<ManualTest> allTests() {
        List<ManualTest> cases = new ArrayList<>();
        cases.addAll(CreateRoomManualCases.cases());
        cases.addAll(UpdateRoomManualCases.cases());
        cases.addAll(DeleteRoomManualCases.cases());
        cases.addAll(ViewRoomsManualCases.cases());
        cases.addAll(SetRoomStatusManualCases.cases());
        cases.sort(Comparator.comparing(ManualTest::id));
        validate(cases);
        return List.copyOf(cases);
    }

    private static void menu(List<ManualTest> cases) {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.println("\nROOM MANAGEMENT - MANUAL UNIT TEST");
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
        cases.forEach(test -> System.out.printf("%-8s | %-32s | %s%n",
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
            if (!test.id().matches("RM-\\d{3}") || !ids.add(test.id())) {
                throw new IllegalStateException("Invalid or duplicate Test Case ID: " + test.id());
            }
        }
    }

    static Fixture fixture() {
        BranchRoomRepository repository = mock(BranchRoomRepository.class);
        return new Fixture(repository, new BranchRoomService(repository), identity());
    }

    static Identity identity() {
        return new Identity(ACTOR_ID, "qlcs.hoabang", "BRANCH_MANAGER", "/branch", BRANCH_ID, BRANCH_NAME);
    }

    static Identity identityWithoutBranch() {
        return new Identity(ACTOR_ID, "qlcs.hoabang", "BRANCH_MANAGER", "/branch", null, null);
    }

    static RoomCommand validCommand() {
        return command("RM-UT-01", "Phòng kiểm thử", 1, ROOM_TYPE_ID, 1, "Phòng mẫu");
    }

    static RoomCommand command(String code, String name, Integer floor, UUID typeId,
            Integer capacity, String description) {
        return new RoomCommand(code, name, floor, typeId, capacity, description);
    }

    static RoomView room(String name, String status) {
        return new RoomView(ROOM_ID, "RM-UT-01", name, 1, ROOM_TYPE_ID,
                "RT-QN", "Phòng tiêu chuẩn", "CINEMA", 1, "Phòng mẫu",
                status, "", null, "READY", "MR-ROOM-UT-01");
    }

    static void suspended(Fixture fixture) {
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, SUSPENDED_MESSAGE))
                .when(fixture.repository()).requireActive(BRANCH_ID);
    }

    static TestResult expectStatus(ThrowingRunnable action, int status, String message) {
        try {
            action.run();
            return check(false, "No ResponseStatusException was thrown");
        } catch (ResponseStatusException failure) {
            boolean passed = failure.getStatusCode().value() == status
                    && Objects.equals(message, failure.getReason());
            return check(passed, "status=" + failure.getStatusCode().value()
                    + ", message=" + failure.getReason() + ", expected=" + message);
        } catch (Throwable failure) {
            return check(false, "Unexpected " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    static TestResult expectStatus(ThrowingRunnable action, int status) {
        try {
            action.run();
            return check(false, "No ResponseStatusException was thrown");
        } catch (ResponseStatusException failure) {
            return check(failure.getStatusCode().value() == status,
                    "status=" + failure.getStatusCode().value() + ", message=" + failure.getReason());
        } catch (Throwable failure) {
            return check(false, "Unexpected " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    static TestResult combine(TestResult... results) {
        boolean passed = true;
        List<String> actual = new ArrayList<>();
        for (TestResult result : results) {
            passed &= result.passed();
            actual.add(result.actual());
        }
        return check(passed, String.join("; ", actual));
    }

    static TestResult check(boolean condition, String actual) {
        return new TestResult(condition, actual);
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }

    @FunctionalInterface
    interface CheckedAction {
        TestResult run() throws Exception;
    }

    record Fixture(BranchRoomRepository repository, BranchRoomService service, Identity identity) {
    }

    record TestResult(boolean passed, String actual) {
    }

    record ManualTest(String id, String useCase, String sourceMethod, String preconditions,
                      String input, String expected, String type, String note, CheckedAction action) {
        ManualTest {
            Objects.requireNonNull(action, "action");
        }

        TestResult execute() {
            try {
                TestResult result = action.run();
                return result == null ? check(false, "Test action returned no result") : result;
            } catch (Throwable failure) {
                return check(false, "Unexpected " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
            }
        }
    }
}
