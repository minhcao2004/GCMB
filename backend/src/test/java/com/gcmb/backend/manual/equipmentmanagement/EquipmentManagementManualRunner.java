/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 11/11/2026
 * Description: Runs Equipment Management manual unit test cases.
 */
package com.gcmb.backend.manual.equipmentmanagement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.inventory.dto.EquipmentAssetDtos.CategoryOption;
import com.gcmb.backend.inventory.dto.EquipmentAssetDtos.Command;
import com.gcmb.backend.inventory.dto.EquipmentAssetDtos.Options;
import com.gcmb.backend.inventory.dto.EquipmentAssetDtos.RoomOption;
import com.gcmb.backend.inventory.dto.EquipmentAssetDtos.View;
import com.gcmb.backend.inventory.repository.EquipmentAssetRepository;
import com.gcmb.backend.inventory.service.EquipmentAssetFileStorageService;
import com.gcmb.backend.inventory.service.EquipmentAssetService;
import com.gcmb.backend.inventory.service.GoogleDriveFileStorageService;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.UUID;
import org.springframework.web.server.ResponseStatusException;

public final class EquipmentManagementManualRunner {
    private static final int EXPECTED_CASES = 40;

    static final UUID HEAD_OFFICE_ID = UUID.fromString("47570000-0000-4000-8000-000000000005");
    static final UUID BRANCH_MANAGER_ID = UUID.fromString("47570000-0000-4000-8000-000000000003");
    static final UUID BRANCH_ID = UUID.fromString("54590000-0000-4000-8000-000000000001");
    static final UUID OTHER_BRANCH_ID = UUID.fromString("54590000-0000-4000-8000-000000000009");
    static final UUID ASSET_ID = UUID.fromString("65690000-0000-4000-8000-000000000001");
    static final UUID NEW_ASSET_ID = UUID.fromString("65690000-0000-4000-8000-000000000002");
    static final UUID CATEGORY_ID = UUID.fromString("65690000-0000-4000-8000-000000000003");
    static final UUID OTHER_CATEGORY_ID = UUID.fromString("65690000-0000-4000-8000-000000000004");
    static final UUID ROOM_ID = UUID.fromString("65690000-0000-4000-8000-000000000005");
    static final UUID OTHER_ROOM_ID = UUID.fromString("65690000-0000-4000-8000-000000000006");
    static final UUID INCIDENT_ID = UUID.fromString("65690000-0000-4000-8000-000000000007");
    static final UUID ATTACHMENT_ID = UUID.fromString("65690000-0000-4000-8000-000000000008");
    static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-11-11T09:00:00+07:00");

    private EquipmentManagementManualRunner() {}

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
        cases.addAll(CreateEquipmentAssetManualCases.cases());
        cases.addAll(ViewEquipmentAssetsManualCases.cases());
        cases.addAll(UpdateEquipmentAssetManualCases.cases());
        cases.addAll(ChangeEquipmentAssetStatusManualCases.cases());
        cases.addAll(DeleteEquipmentAssetManualCases.cases());
        cases.sort(Comparator.comparing(ManualTest::id));
        if (cases.size() != EXPECTED_CASES) {
            throw new IllegalStateException("Expected " + EXPECTED_CASES + " cases but found " + cases.size());
        }
        if (cases.stream().map(ManualTest::id).distinct().count() != cases.size()) {
            throw new IllegalStateException("Duplicate Equipment Management test case ID");
        }
        return List.copyOf(cases);
    }

    private static void menu(List<ManualTest> cases) {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.println("\nEQUIPMENT MANAGEMENT MANUAL UNIT TEST");
                System.out.println("1. List cases\n2. Run all\n3. Run one case\n0. Exit");
                String choice = scanner.nextLine().trim();
                if ("0".equals(choice)) return;
                if ("1".equals(choice)) list(cases);
                else if ("2".equals(choice)) run(cases);
                else if ("3".equals(choice)) {
                    System.out.print("Test Case ID: ");
                    String id = scanner.nextLine().trim();
                    run(cases.stream().filter(test -> test.id().equalsIgnoreCase(id)).toList());
                }
            }
        }
    }

    private static void list(List<ManualTest> cases) {
        cases.forEach(test -> System.out.println(test.id() + " | " + test.useCase() + " | " + test.type()));
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
            System.out.printf("%n[%s] %s%nSource      : %s%nPrecondition: %s%nInput       : %s%nExpected    : %s%nActual      : %s%nVERDICT     : %s%n",
                    test.id(), test.useCase(), test.sourceMethod(), test.preconditions(), test.input(), test.expected(),
                    result.actual(), result.passed() ? "PASSED" : "FAILED");
        }
        System.out.printf("%nSUMMARY: total=%d, passed=%d, failed=%d%n", cases.size(), passed, cases.size() - passed);
    }

    static ManualTest test(String id, String useCase, String sourceMethod, String preconditions, String input,
            String expected, String type, String note, CheckedAction action) {
        return new ManualTest(id, useCase, sourceMethod, preconditions, input, expected, type, note, action);
    }

    static String note(String ucFile, String br, String msg, String production, String method) {
        StringBuilder value = new StringBuilder("Nguồn: ").append(ucFile).append(";");
        if (br != null && !br.isBlank()) value.append(" ").append(br).append(";");
        if (msg != null && !msg.isBlank()) value.append(" ").append(msg).append(";");
        value.append(" Flyway V14__seed_genz_branches_and_accounts.sql; Production: ")
                .append(production).append(". Phương pháp kiểm thử: ").append(method).append(".");
        return value.toString();
    }

    static Fixture fixture() {
        EquipmentAssetRepository repository = mock(EquipmentAssetRepository.class);
        EquipmentAssetFileStorageService fileStorage = mock(EquipmentAssetFileStorageService.class);
        EquipmentAssetService service = new EquipmentAssetService(repository, fileStorage);
        View existing = view(ASSET_ID, "ASSET-HN01-0001", "Máy chiếu", "IN_USE", true, false, null);
        View created = view(NEW_ASSET_ID, "ASSET-HN01-0002", "Loa sân khấu", "IN_USE", true, false, null);
        View updated = view(ASSET_ID, "ASSET-HN01-0001", "Máy chiếu laser", "IN_USE", true, false, INCIDENT_ID);
        when(repository.list(eq(BRANCH_ID), anyString(), nullable(UUID.class), anyString(), nullable(Boolean.class)))
                .thenReturn(List.of(existing));
        when(repository.options(eq(BRANCH_ID), nullable(UUID.class))).thenReturn(new Options(
                List.of(new CategoryOption(CATEGORY_ID, "EQ-AUDIO", "Thiết bị âm thanh", true)),
                List.of(new RoomOption(ROOM_ID, "P101", "Phòng 101", "AVAILABLE"))));
        when(repository.get(BRANCH_ID, ASSET_ID)).thenReturn(existing);
        when(repository.categoryAvailable(eq(BRANCH_ID), any(UUID.class), nullable(UUID.class))).thenReturn(true);
        when(repository.roomAvailable(eq(BRANCH_ID), nullable(UUID.class), nullable(UUID.class))).thenReturn(true);
        when(repository.create(eq(BRANCH_ID), any(Command.class))).thenReturn(created);
        when(repository.update(eq(BRANCH_ID), eq(ASSET_ID), any(Command.class))).thenReturn(updated);
        when(repository.setActive(eq(BRANCH_ID), eq(ASSET_ID), anyBoolean())).thenAnswer(invocation ->
                view(ASSET_ID, "ASSET-HN01-0001", "Máy chiếu", "IN_USE", invocation.getArgument(2), false, INCIDENT_ID));
        when(repository.deleteUnused(BRANCH_ID, ASSET_ID)).thenReturn(true);
        return new Fixture(repository, fileStorage, service);
    }

    static StorageFixture storageFixture() throws Exception {
        Path root = Files.createTempDirectory(Path.of("target"), "eqm-files-");
        EquipmentAssetFileStorageService storage = new EquipmentAssetFileStorageService(root.toString(),
                mock(GoogleDriveFileStorageService.class));
        return new StorageFixture(root, storage);
    }

    static void cleanup(StorageFixture fixture, String objectKey) throws Exception {
        if (objectKey != null) {
            fixture.storage().deleteQuietly(objectKey);
        }
        Files.deleteIfExists(fixture.root());
    }

    static Identity headOfficeManager() {
        return new Identity(HEAD_OFFICE_ID, "qlcc", "HEAD_OFFICE_MANAGER", "/head-office", null, null);
    }

    static Identity branchManager() {
        return new Identity(BRANCH_MANAGER_ID, "qlcs.hoabang", "BRANCH_MANAGER", "/manager", BRANCH_ID,
                "GenZ Cinema Hoa Bằng");
    }

    static Identity receptionist() {
        return new Identity(BRANCH_MANAGER_ID, "letan.hoabang", "RECEPTIONIST", "/reception", BRANCH_ID,
                "GenZ Cinema Hoa Bằng");
    }

    static Command command(String code, String name, UUID categoryId, UUID roomId, String location, String status) {
        return new Command(code, name, categoryId, roomId, location, "SN-0001", LocalDate.of(2026, 1, 1),
                new BigDecimal("15000000"), LocalDate.of(2027, 1, 1), status);
    }

    static View view(UUID id, String code, String name, String status, boolean active, boolean hasImage, UUID incidentId) {
        return new View(id, BRANCH_ID, code, name, CATEGORY_ID, "EQ-AUDIO", "Thiết bị âm thanh", ROOM_ID,
                "P101", "Phòng 101", "Sân khấu", "SN-0001", LocalDate.of(2026, 1, 1),
                new BigDecimal("15000000"), LocalDate.of(2027, 1, 1), status, active, hasImage,
                incidentId, incidentId == null ? null : "Lỗi nguồn", CREATED_AT);
    }

    static TestResult expectStatus(ThrowingRunnable action, int status, String message) {
        try {
            action.run();
            return check(false, "No exception; expected HTTP " + status + " and message: " + message);
        } catch (ResponseStatusException failure) {
            int actual = failure.getStatusCode().value();
            return check(actual == status && Objects.equals(message, failure.getReason()),
                    "HTTP " + actual + "; message=" + failure.getReason());
        } catch (Throwable failure) {
            return unexpected(failure);
        }
    }

    static TestResult check(boolean passed, String actual) { return new TestResult(passed, actual); }
    static TestResult unexpected(Throwable failure) {
        return new TestResult(false, "Unexpected " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
    }

    record Fixture(EquipmentAssetRepository repository, EquipmentAssetFileStorageService fileStorage,
                   EquipmentAssetService service) {}
    record StorageFixture(Path root, EquipmentAssetFileStorageService storage) {}
    record TestResult(boolean passed, String actual) {}
    record ManualTest(String id, String useCase, String sourceMethod, String preconditions, String input,
                      String expected, String type, String note, CheckedAction action) {
        ManualTest { Objects.requireNonNull(action, "action"); }
        TestResult execute() {
            try {
                TestResult result = action.run();
                return result == null ? check(false, "Test action returned no result") : result;
            } catch (Throwable failure) {
                return unexpected(failure);
            }
        }
    }

    @FunctionalInterface interface CheckedAction { TestResult run() throws Exception; }
    @FunctionalInterface interface ThrowingRunnable { void run() throws Exception; }
}
