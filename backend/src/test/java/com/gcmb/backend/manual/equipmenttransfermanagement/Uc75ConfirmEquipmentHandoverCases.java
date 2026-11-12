/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 12/11/2026
 * Description: Manual unit test cases cho UC-75 Confirm Equipment Handover.
 */
package com.gcmb.backend.manual.equipmenttransfermanagement;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.inventory.repository.EquipmentIncidentRepository;
import com.gcmb.backend.inventory.service.EquipmentIncidentService;
import com.gcmb.backend.manual.equipmenttransfermanagement.EquipmentTransferManagementManualRunner.ManualCase;
import com.gcmb.backend.manual.equipmenttransfermanagement.EquipmentTransferManagementManualRunner.Observation;
import java.util.List;
import java.util.UUID;
import org.springframework.web.server.ResponseStatusException;

final class Uc75ConfirmEquipmentHandoverCases {
    private static final UUID HOM_ID = UUID.fromString("47570000-0000-4000-8000-000000000005");
    private static final UUID SOURCE_MANAGER_ID = UUID.fromString("47570000-0000-4000-8000-000000000003");
    private static final UUID DESTINATION_MANAGER_ID = UUID.fromString("47570000-0000-4000-8000-000000000004");
    private static final UUID SOURCE_BRANCH = UUID.fromString("75000000-0000-4000-8000-000000000001");
    private static final UUID DESTINATION_BRANCH = UUID.fromString("75000000-0000-4000-8000-000000000002");
    private static final UUID TRANSFER_ID = UUID.fromString("75000000-0000-4000-8000-000000000020");
    private static final String TITLE = "UC-75 Confirm Equipment Handover";
    private static final String SOURCE = "UC-75; BR-49, BR-200; MSG24; V1, V14; EquipmentIncidentService.handover";

    private Uc75ConfirmEquipmentHandoverCases() {
    }

    static List<ManualCase> cases() {
        return List.of(
            test("ETM-012",
                "Branch Manager qlcs.hoabang đã đăng nhập. HN_HOA_BANG đang Active và là cơ sở nguồn được phân công. Yêu cầu điều chuyển tồn tại ở trạng thái REQUESTED.",
                "Xác nhận bàn giao yêu cầu điều chuyển tại cơ sở nguồn.",
                "Ghi nhận người và thời điểm bàn giao; chuyển yêu cầu và thiết bị sang IN_TRANSIT nhưng chưa đổi cơ sở hoặc vị trí thiết bị.",
                () -> {
                    RecordingRepository repository = repository("REQUESTED");
                    service(repository).handover(sourceManager(), TRANSFER_ID);
                    boolean passed = "IN_TRANSIT".equals(repository.transferStatus)
                        && "IN_TRANSIT".equals(repository.assetStatus)
                        && SOURCE_MANAGER_ID.equals(repository.handedOverBy)
                        && repository.handoverCount == 1
                        && SOURCE_BRANCH.equals(repository.assetBranch)
                        && "Q01".equals(repository.assetLocation);
                    return new Observation("transferStatus=" + repository.transferStatus
                        + ", assetStatus=" + repository.assetStatus
                        + ", handedOverBy=" + repository.handedOverBy
                        + ", handoverWrites=" + repository.handoverCount
                        + ", assetBranch=" + repository.assetBranch
                        + ", assetLocation=" + repository.assetLocation, passed);
                }),
            test("ETM-013",
                "Branch Manager đã đăng nhập nhưng chỉ được phân công cho cơ sở nhận, không phải cơ sở nguồn. Yêu cầu đang REQUESTED.",
                "Xác nhận bàn giao yêu cầu của cơ sở nguồn khác.",
                "Không ghi nhận bàn giao. Hiển thị: Bạn không có quyền xác nhận bàn giao đối với phiếu điều chuyển.",
                () -> permission(destinationManager())),
            test("ETM-014",
                "Head Office Manager đã đăng nhập. Yêu cầu điều chuyển đang REQUESTED.",
                "Head Office Manager xác nhận bàn giao thay Branch Manager nguồn.",
                "Không ghi nhận bàn giao. Hiển thị: Bạn không có quyền xác nhận bàn giao đối với phiếu điều chuyển.",
                () -> permission(headOffice())),
            test("ETM-015",
                "Branch Manager nguồn đã đăng nhập. Yêu cầu đã ở trạng thái IN_TRANSIT, COMPLETED hoặc CANCELLED.",
                "Xác nhận bàn giao lại yêu cầu không còn chờ bàn giao.",
                "Không ghi nhận lần bàn giao thứ hai; giữ nguyên người xác nhận, thời gian và trạng thái hiện có.",
                () -> {
                    RecordingRepository repository = repository("IN_TRANSIT");
                    repository.handedOverBy = SOURCE_MANAGER_ID;
                    repository.assetStatus = "IN_TRANSIT";
                    try {
                        service(repository).handover(sourceManager(), TRANSFER_ID);
                        return new Observation("Không phát sinh lỗi; handoverWrites=" + repository.handoverCount, false);
                    } catch (ResponseStatusException failure) {
                        boolean passed = "IN_TRANSIT".equals(repository.transferStatus)
                            && "IN_TRANSIT".equals(repository.assetStatus)
                            && SOURCE_MANAGER_ID.equals(repository.handedOverBy)
                            && repository.handoverCount == 0;
                        return new Observation("message=" + failure.getReason()
                            + ", transferStatus=" + repository.transferStatus
                            + ", handoverWrites=" + repository.handoverCount, passed);
                    }
                })
        );
    }

    private static Observation permission(Identity actor) {
        RecordingRepository repository = repository("REQUESTED");
        String expected = "Bạn không có quyền xác nhận bàn giao đối với phiếu điều chuyển.";
        try {
            service(repository).handover(actor, TRANSFER_ID);
            return new Observation("Không phát sinh lỗi; handoverWrites=" + repository.handoverCount, false);
        } catch (ResponseStatusException failure) {
            String actual = failure.getReason();
            boolean passed = expected.equals(actual)
                && "REQUESTED".equals(repository.transferStatus)
                && repository.handoverCount == 0;
            return new Observation("message=" + actual + ", transferStatus=" + repository.transferStatus
                + ", handoverWrites=" + repository.handoverCount, passed);
        }
    }

    private static ManualCase test(String id, String precondition, String input, String expected,
                                   EquipmentTransferManagementManualRunner.Execution execution) {
        return new ManualCase(id, TITLE, SOURCE, precondition, input, expected, execution);
    }

    private static EquipmentIncidentService service(RecordingRepository repository) {
        return new EquipmentIncidentService(repository, null);
    }

    private static RecordingRepository repository(String status) {
        RecordingRepository repository = new RecordingRepository();
        repository.transferStatus = status;
        repository.assetStatus = "IN_USE";
        repository.assetBranch = SOURCE_BRANCH;
        repository.assetLocation = "Q01";
        return repository;
    }

    private static Identity sourceManager() {
        return new Identity(SOURCE_MANAGER_ID, "qlcs.hoabang", "BRANCH_MANAGER", "/branch-management",
            SOURCE_BRANCH, "HN_HOA_BANG");
    }

    private static Identity destinationManager() {
        return new Identity(DESTINATION_MANAGER_ID, "qlcs.nguyenluongbang", "BRANCH_MANAGER", "/branch-management",
            DESTINATION_BRANCH, "HN_NGUYEN_LUONG_BANG_86");
    }

    private static Identity headOffice() {
        return new Identity(HOM_ID, "qlcc", "HEAD_OFFICE_MANAGER", "/head-office", null, null);
    }

    private static final class RecordingRepository extends EquipmentIncidentRepository {
        private String transferStatus;
        private String assetStatus;
        private UUID assetBranch;
        private String assetLocation;
        private UUID handedOverBy;
        private int handoverCount;

        private RecordingRepository() {
            super(null);
        }

        @Override
        public int handover(UUID id, UUID branch, UUID actor) {
            if (!TRANSFER_ID.equals(id) || !SOURCE_BRANCH.equals(branch) || !"REQUESTED".equals(transferStatus)) {
                return 0;
            }
            transferStatus = "IN_TRANSIT";
            assetStatus = "IN_TRANSIT";
            handedOverBy = actor;
            handoverCount++;
            return 1;
        }
    }
}
