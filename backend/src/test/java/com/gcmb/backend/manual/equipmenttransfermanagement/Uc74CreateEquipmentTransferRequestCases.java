/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 12/11/2026
 * Description: Manual unit test cases cho UC-74 Create Equipment Transfer Request.
 */
package com.gcmb.backend.manual.equipmenttransfermanagement;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.inventory.dto.EquipmentIncidentDtos.TransferCommand;
import com.gcmb.backend.inventory.repository.EquipmentIncidentRepository;
import com.gcmb.backend.inventory.service.EquipmentIncidentService;
import com.gcmb.backend.manual.equipmenttransfermanagement.EquipmentTransferManagementManualRunner.ManualCase;
import com.gcmb.backend.manual.equipmenttransfermanagement.EquipmentTransferManagementManualRunner.Observation;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

final class Uc74CreateEquipmentTransferRequestCases {
    private static final UUID HOM_ID = UUID.fromString("47570000-0000-4000-8000-000000000005");
    private static final UUID BRANCH_MANAGER_ID = UUID.fromString("47570000-0000-4000-8000-000000000003");
    private static final UUID RECEPTIONIST_ID = UUID.fromString("47570000-0000-4000-8000-000000000001");
    private static final UUID SOURCE_BRANCH = UUID.fromString("74000000-0000-4000-8000-000000000001");
    private static final UUID DESTINATION_BRANCH = UUID.fromString("74000000-0000-4000-8000-000000000002");
    private static final UUID ASSET_ID = UUID.fromString("74000000-0000-4000-8000-000000000010");
    private static final UUID UNKNOWN_ASSET_ID = UUID.fromString("74000000-0000-4000-8000-000000000099");
    private static final UUID TRANSFER_ID = UUID.fromString("74000000-0000-4000-8000-000000000020");
    private static final String TITLE = "UC-74 Create Equipment Transfer Request";
    private static final String SOURCE = "UC-74; BR-48, BR-111; MSG02, MSG24; V1, V14, V31; EquipmentIncidentService.transfer";

    private Uc74CreateEquipmentTransferRequestCases() {
    }

    static List<ManualCase> cases() {
        return List.of(
            test("ETM-001",
                "Tài khoản qlcc đăng nhập với vai trò Head Office Manager. Cơ sở nguồn HN_HOA_BANG và cơ sở nhận HN_NGUYEN_LUONG_BANG_86 tồn tại. Thiết bị EQ-ETM-001 đang hoạt động tại cơ sở nguồn và chưa có yêu cầu điều chuyển đang xử lý.",
                "Tạo yêu cầu điều chuyển EQ-ETM-001 sang HN_NGUYEN_LUONG_BANG_86, lý do Điều phối thiết bị theo nhu cầu cơ sở.",
                "Tạo một yêu cầu REQUESTED; giữ nguyên cơ sở và vị trí hiện tại của thiết bị.",
                () -> {
                    RecordingRepository repository = readyRepository();
                    UUID result = service(repository).transfer(headOffice(), command(ASSET_ID, DESTINATION_BRANCH, "Điều phối thiết bị theo nhu cầu cơ sở"));
                    boolean passed = TRANSFER_ID.equals(result)
                        && repository.createCount == 1
                        && ASSET_ID.equals(repository.recordedAsset)
                        && SOURCE_BRANCH.equals(repository.recordedFrom)
                        && DESTINATION_BRANCH.equals(repository.recordedTo)
                        && HOM_ID.equals(repository.recordedActor)
                        && "Điều phối thiết bị theo nhu cầu cơ sở".equals(repository.recordedReason)
                        && SOURCE_BRANCH.equals(repository.assetBranch)
                        && "Q01".equals(repository.assetLocation);
                    return new Observation(
                        "transferId=" + result + ", status=REQUESTED, transferWrites=" + repository.createCount
                            + ", assetBranch=" + repository.assetBranch + ", assetLocation=" + repository.assetLocation,
                        passed
                    );
                }),
            permission("ETM-002", branchManager(), "Branch Manager yêu cầu tạo điều chuyển thiết bị sang cơ sở khác."),
            permission("ETM-003", receptionist(), "Receptionist yêu cầu tạo điều chuyển thiết bị sang cơ sở khác."),
            required("ETM-004", command(null, DESTINATION_BRANCH, "Điều phối thiết bị"), "Vui lòng bổ sung thiết bị.", "Tạo yêu cầu với thiết bị để trống."),
            required("ETM-005", command(ASSET_ID, null, "Điều phối thiết bị"), "Vui lòng bổ sung cơ sở nhận.", "Tạo yêu cầu với cơ sở nhận để trống."),
            required("ETM-006", command(ASSET_ID, DESTINATION_BRANCH, null), "Vui lòng bổ sung lý do điều chuyển.", "Tạo yêu cầu với lý do null."),
            required("ETM-007", command(ASSET_ID, DESTINATION_BRANCH, "   "), "Vui lòng bổ sung lý do điều chuyển.", "Tạo yêu cầu với lý do chỉ chứa khoảng trắng."),
            test("ETM-008",
                "Head Office Manager đã đăng nhập. EQ-ETM-001 hiện thuộc HN_HOA_BANG.",
                "Chọn HN_HOA_BANG làm cả cơ sở nguồn và cơ sở nhận.",
                "Không tạo yêu cầu; thiết bị và lịch sử điều chuyển không thay đổi.",
                () -> rejectedWithoutWrite(readyRepository(), command(ASSET_ID, SOURCE_BRANCH, "Điều phối thiết bị"))),
            test("ETM-009",
                "Head Office Manager đã đăng nhập. Cơ sở nhận tồn tại. Không tồn tại thiết bị mang ID được cung cấp.",
                "Tạo yêu cầu cho thiết bị không tồn tại.",
                "Không tạo yêu cầu hoặc thay đổi thiết bị.",
                () -> {
                    RecordingRepository repository = readyRepository();
                    repository.assetBranch = null;
                    return rejectedWithoutWrite(repository, command(UNKNOWN_ASSET_ID, DESTINATION_BRANCH, "Điều phối thiết bị"));
                }),
            test("ETM-010",
                "Head Office Manager đã đăng nhập. Thiết bị tồn tại nhưng đang Inactive, Retired hoặc IN_TRANSIT.",
                "Tạo yêu cầu điều chuyển thiết bị không đủ điều kiện.",
                "Yêu cầu bị từ chối; không tạo bản ghi mới và không thay đổi thiết bị.",
                () -> repositoryConflict("asset is inactive or in transit")),
            test("ETM-011",
                "Head Office Manager đã đăng nhập. Thiết bị đã có yêu cầu REQUESTED hoặc IN_TRANSIT.",
                "Tạo thêm yêu cầu điều chuyển cho cùng thiết bị.",
                "Yêu cầu mới bị từ chối; chỉ giữ lại yêu cầu hiện có.",
                () -> repositoryConflict("uq_active_asset_transfer"))
        );
    }

    private static ManualCase permission(String id, Identity actor, String input) {
        return test(id,
            actor.role() + " đã đăng nhập; thiết bị hợp lệ tồn tại.",
            input,
            "Không tạo yêu cầu. Hiển thị: Bạn không có quyền tạo yêu cầu điều chuyển đối với thiết bị.",
            () -> {
                RecordingRepository repository = readyRepository();
                String expected = "Bạn không có quyền tạo yêu cầu điều chuyển đối với thiết bị.";
                try {
                    service(repository).transfer(actor, command(ASSET_ID, DESTINATION_BRANCH, "Điều phối thiết bị"));
                    return new Observation("Không phát sinh lỗi; transferWrites=" + repository.createCount, false);
                } catch (ResponseStatusException failure) {
                    String actual = failure.getReason();
                    return new Observation("message=" + actual + ", transferWrites=" + repository.createCount,
                        expected.equals(actual) && repository.createCount == 0);
                }
            });
    }

    private static ManualCase required(String id, TransferCommand command, String expectedMessage, String input) {
        return test(id,
            "Head Office Manager đã đăng nhập; các dữ liệu không bị để trống đều hợp lệ.",
            input,
            "Không tạo yêu cầu. Hiển thị: " + expectedMessage,
            () -> {
                RecordingRepository repository = readyRepository();
                try {
                    service(repository).transfer(headOffice(), command);
                    return new Observation("Không phát sinh lỗi; transferWrites=" + repository.createCount, false);
                } catch (ResponseStatusException failure) {
                    String actual = failure.getReason();
                    return new Observation("message=" + actual + ", transferWrites=" + repository.createCount,
                        expectedMessage.equals(actual) && repository.createCount == 0);
                }
            });
    }

    private static Observation rejectedWithoutWrite(RecordingRepository repository, TransferCommand command) {
        UUID branchBefore = repository.assetBranch;
        String locationBefore = repository.assetLocation;
        try {
            service(repository).transfer(headOffice(), command);
            return new Observation("Không phát sinh lỗi; transferWrites=" + repository.createCount, false);
        } catch (ResponseStatusException failure) {
            boolean passed = repository.createCount == 0
                && java.util.Objects.equals(branchBefore, repository.assetBranch)
                && java.util.Objects.equals(locationBefore, repository.assetLocation);
            return new Observation("message=" + failure.getReason() + ", transferWrites=" + repository.createCount
                + ", assetBranch=" + repository.assetBranch + ", assetLocation=" + repository.assetLocation, passed);
        }
    }

    private static Observation repositoryConflict(String databaseReason) {
        RecordingRepository repository = readyRepository();
        repository.databaseFailure = databaseReason;
        try {
            service(repository).transfer(headOffice(), command(ASSET_ID, DESTINATION_BRANCH, "Điều phối thiết bị"));
            return new Observation("Không phát sinh lỗi; transferWrites=" + repository.createCount, false);
        } catch (ResponseStatusException failure) {
            boolean passed = failure.getStatusCode().value() == 409
                && repository.createCount == 0
                && SOURCE_BRANCH.equals(repository.assetBranch)
                && "Q01".equals(repository.assetLocation);
            return new Observation("HTTP " + failure.getStatusCode().value() + ": " + failure.getReason()
                + ", transferWrites=" + repository.createCount + ", assetBranch=" + repository.assetBranch, passed);
        }
    }

    private static ManualCase test(String id, String precondition, String input, String expected,
                                   EquipmentTransferManagementManualRunner.Execution execution) {
        return new ManualCase(id, TITLE, SOURCE, precondition, input, expected, execution);
    }

    private static EquipmentIncidentService service(RecordingRepository repository) {
        return new EquipmentIncidentService(repository, null);
    }

    private static RecordingRepository readyRepository() {
        RecordingRepository repository = new RecordingRepository();
        repository.assetBranch = SOURCE_BRANCH;
        repository.assetLocation = "Q01";
        return repository;
    }

    private static TransferCommand command(UUID asset, UUID destination, String reason) {
        return new TransferCommand(asset, destination, reason);
    }

    private static Identity headOffice() {
        return new Identity(HOM_ID, "qlcc", "HEAD_OFFICE_MANAGER", "/head-office", null, null);
    }

    private static Identity branchManager() {
        return new Identity(BRANCH_MANAGER_ID, "qlcs.hoabang", "BRANCH_MANAGER", "/branch-management", SOURCE_BRANCH, "HN_HOA_BANG");
    }

    private static Identity receptionist() {
        return new Identity(RECEPTIONIST_ID, "letan.hoabang", "RECEPTIONIST", "/reception", SOURCE_BRANCH, "HN_HOA_BANG");
    }

    private static final class RecordingRepository extends EquipmentIncidentRepository {
        private UUID assetBranch;
        private String assetLocation;
        private int createCount;
        private UUID recordedAsset;
        private UUID recordedFrom;
        private UUID recordedTo;
        private UUID recordedActor;
        private String recordedReason;
        private String databaseFailure;

        private RecordingRepository() {
            super(null);
        }

        @Override
        public UUID assetBranch(UUID assetId) {
            return assetBranch;
        }

        @Override
        public UUID createTransfer(UUID asset, UUID from, UUID to, UUID actor, String reason) {
            if (databaseFailure != null) {
                throw new DataIntegrityViolationException(databaseFailure);
            }
            createCount++;
            recordedAsset = asset;
            recordedFrom = from;
            recordedTo = to;
            recordedActor = actor;
            recordedReason = reason;
            return TRANSFER_ID;
        }
    }
}
