/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 11/11/2026
 * Description: Runs Equipment Maintenance manual unit test cases.
 */
package com.gcmb.backend.manual.equipmentmaintenance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.inventory.dto.EquipmentIncidentDtos.AssetOption;
import com.gcmb.backend.inventory.dto.EquipmentIncidentDtos.IncidentCommand;
import com.gcmb.backend.inventory.dto.EquipmentIncidentDtos.IncidentView;
import com.gcmb.backend.inventory.dto.EquipmentIncidentDtos.ProgressView;
import com.gcmb.backend.inventory.repository.EquipmentIncidentRepository;
import com.gcmb.backend.inventory.service.EquipmentAssetFileStorageService;
import com.gcmb.backend.inventory.service.EquipmentAssetFileStorageService.StoredFile;
import com.gcmb.backend.inventory.service.EquipmentIncidentService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.UUID;
import org.springframework.web.server.ResponseStatusException;

public final class EquipmentMaintenanceManualRunner {
    private static final int EXPECTED_CASES = 52;

    static final UUID HEAD_OFFICE_ID = UUID.fromString("47570000-0000-4000-8000-000000000005");
    static final UUID BRANCH_MANAGER_ID = UUID.fromString("47570000-0000-4000-8000-000000000003");
    static final UUID RECEPTIONIST_ID = UUID.fromString("47570000-0000-4000-8000-000000000001");
    static final UUID BRANCH_ID = UUID.fromString("54590000-0000-4000-8000-000000000001");
    static final UUID OTHER_BRANCH_ID = UUID.fromString("54590000-0000-4000-8000-000000000009");
    static final UUID ASSET_ID = UUID.fromString("65690000-0000-4000-8000-000000000001");
    static final UUID REPLACEMENT_ASSET_ID = UUID.fromString("65690000-0000-4000-8000-000000000002");
    static final UUID ROOM_ID = UUID.fromString("65690000-0000-4000-8000-000000000005");
    static final UUID INCIDENT_ID = UUID.fromString("67690000-0000-4000-8000-000000000001");
    static final UUID PROGRESS_ID = UUID.fromString("67690000-0000-4000-8000-000000000002");
    static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-11-11T09:00:00+07:00");

    private EquipmentMaintenanceManualRunner() {}

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
        cases.addAll(CreateEquipmentIncidentReportManualCases.cases());
        cases.addAll(RecordRepairOutcomeManualCases.cases());
        cases.addAll(ViewEquipmentIncidentsManualCases.cases());
        cases.addAll(AssessEquipmentIncidentManualCases.cases());
        cases.sort(Comparator.comparing(ManualTest::id));
        if (cases.size() != EXPECTED_CASES) {
            throw new IllegalStateException("Expected " + EXPECTED_CASES + " cases but found " + cases.size());
        }
        if (cases.stream().map(ManualTest::id).distinct().count() != cases.size()) {
            throw new IllegalStateException("Duplicate Equipment Maintenance test case ID");
        }
        return List.copyOf(cases);
    }

    private static void menu(List<ManualTest> cases) {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.println("\nEQUIPMENT MAINTENANCE MANUAL UNIT TEST");
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

    static String note(String source, String br, String msg, String production, String method) {
        StringBuilder value = new StringBuilder("Nguồn: ").append(source).append(";");
        if (br != null && !br.isBlank()) value.append(" ").append(br).append(";");
        if (msg != null && !msg.isBlank()) value.append(" ").append(msg).append(";");
        value.append(" Flyway V14__seed_genz_branches_and_accounts.sql; Production: ")
                .append(production).append(". Phương pháp kiểm thử: ").append(method).append(".");
        return value.toString();
    }

    static Fixture fixture() throws Exception {
        EquipmentIncidentRepository repository = mock(EquipmentIncidentRepository.class);
        EquipmentAssetFileStorageService fileStorage = mock(EquipmentAssetFileStorageService.class);
        EquipmentIncidentService service = new EquipmentIncidentService(repository, fileStorage);
        when(repository.assets(BRANCH_ID)).thenReturn(List.of(assetOption()));
        when(repository.incidents(eq(BRANCH_ID), anyString(), anyString())).thenReturn(List.of(incidentView()));
        when(repository.progress(BRANCH_ID, INCIDENT_ID)).thenReturn(List.of(progressView()));
        when(repository.createIncident(eq(BRANCH_ID), any(UUID.class), anyString(), any(IncidentCommand.class)))
                .thenReturn(INCIDENT_ID);
        when(repository.setIncidentEvidence(eq(BRANCH_ID), eq(INCIDENT_ID), any(UUID.class))).thenReturn(true);
        when(repository.review(eq(BRANCH_ID), eq(BRANCH_MANAGER_ID), eq(INCIDENT_ID), any())).thenReturn(true);
        when(repository.incidentPlan(BRANCH_ID, INCIDENT_ID)).thenReturn("REPAIR");
        when(repository.progress(eq(BRANCH_ID), eq(BRANCH_MANAGER_ID), eq(INCIDENT_ID), any())).thenReturn(true);
        when(repository.close(BRANCH_ID, BRANCH_MANAGER_ID, INCIDENT_ID, "Đã nghiệm thu")).thenReturn(true);
        when(repository.assess(eq(BRANCH_ID), eq(BRANCH_MANAGER_ID), eq(INCIDENT_ID), any())).thenReturn(1);
        when(repository.repair(eq(BRANCH_ID), eq(INCIDENT_ID), eq(BRANCH_MANAGER_ID), any())).thenReturn(1);
        when(repository.setRepairEvidence(eq(BRANCH_ID), eq(INCIDENT_ID), any(UUID.class), any(UUID.class))).thenReturn(true);
        when(fileStorage.store(any())).thenReturn(evidenceFile());
        when(fileStorage.storeInDrive(any())).thenReturn(invoiceFile(), transferFile());
        when(repository.repairAttachment(nullable(UUID.class), eq(INCIDENT_ID), anyString()))
                .thenReturn(new EquipmentIncidentRepository.AttachmentView("hoa-don.pdf", "application/pdf", "gdrive:invoice"));
        when(fileStorage.read("gdrive:invoice")).thenReturn("%PDF-1.7".getBytes());
        return new Fixture(repository, fileStorage, service);
    }

    static Identity headOfficeManager() {
        return new Identity(HEAD_OFFICE_ID, "qlcc", "HEAD_OFFICE_MANAGER", "/head-office", null, null);
    }

    static Identity branchManager() {
        return new Identity(BRANCH_MANAGER_ID, "qlcs.hoabang", "BRANCH_MANAGER", "/manager", BRANCH_ID,
                "GenZ Cinema Hoa Bằng");
    }

    static Identity receptionist() {
        return new Identity(RECEPTIONIST_ID, "letan.hoabang", "RECEPTIONIST", "/reception", BRANCH_ID,
                "GenZ Cinema Hoa Bằng");
    }

    static IncidentCommand incidentCommand(String description, LocalDate expectedOn, String plan,
            String provider, String replacement, BigDecimal estimatedCost) {
        return new IncidentCommand(ASSET_ID, description, true, "HIGH", "URGENT", expectedOn, plan, provider,
                replacement, estimatedCost, "Kiểm tra nguồn điện");
    }

    static IncidentCommand validIncident() {
        return incidentCommand("Máy chiếu mất nguồn", LocalDate.of(2026, 11, 15), "REPAIR", "Nhà cung cấp A", null,
                new BigDecimal("2500000"));
    }

    static AssetOption assetOption() {
        return new AssetOption(ASSET_ID, "ASSET-HN01-0001", "Máy chiếu", "P101", "Phòng 101");
    }

    static IncidentView incidentView() {
        return new IncidentView(INCIDENT_ID, ASSET_ID, "ASSET-HN01-0001", "Máy chiếu", BRANCH_ID,
                "GenZ Cinema Hoa Bằng", ROOM_ID, "P101", "Phòng 101", "Máy chiếu mất nguồn", true,
                "IN_PROGRESS", "REPAIR", null, null, null, null, null, CREATED_AT, "HIGH", "URGENT",
                LocalDate.of(2026, 11, 15), "Kiểm tra nguồn điện", RECEPTIONIST_ID, null, null,
                "Nhà cung cấp A", null, new BigDecimal("2500000"));
    }

    static ProgressView progressView() {
        return new ProgressView(PROGRESS_ID, BRANCH_MANAGER_ID, "IN_PROGRESS", "Đã gửi sửa",
                LocalDate.of(2026, 11, 15), CREATED_AT);
    }

    static StoredFile evidenceFile() {
        return stored("evidence.png", "image/png", "evidence");
    }

    static StoredFile invoiceFile() {
        return stored("hoa-don.pdf", "application/pdf", "invoice");
    }

    static StoredFile transferFile() {
        return stored("chuyen-khoan.pdf", "application/pdf", "transfer");
    }

    private static StoredFile stored(String name, String mime, String key) {
        byte[] bytes = "%PDF-1.7".getBytes();
        return new StoredFile(UUID.nameUUIDFromBytes(key.getBytes()), "gdrive:" + key, name, mime, bytes.length,
                "sha256", bytes);
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

    record Fixture(EquipmentIncidentRepository repository, EquipmentAssetFileStorageService fileStorage,
                   EquipmentIncidentService service) {}
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
