/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 12/11/2026
 * Description: Manual unit test cases cho UC-76 Confirm Equipment Receipt.
 */
package com.gcmb.backend.manual.equipmenttransfermanagement;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.inventory.dto.EquipmentIncidentDtos.TransferReceiptCommand;
import com.gcmb.backend.inventory.repository.EquipmentIncidentRepository;
import com.gcmb.backend.inventory.service.EquipmentIncidentService;
import com.gcmb.backend.manual.equipmenttransfermanagement.EquipmentTransferManagementManualRunner.ManualCase;
import com.gcmb.backend.manual.equipmenttransfermanagement.EquipmentTransferManagementManualRunner.Observation;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.server.ResponseStatusException;

final class Uc76ConfirmEquipmentReceiptCases {
    private static final UUID HOM_ID = UUID.fromString("47570000-0000-4000-8000-000000000005");
    private static final UUID SOURCE_MANAGER_ID = UUID.fromString("47570000-0000-4000-8000-000000000003");
    private static final UUID DESTINATION_MANAGER_ID = UUID.fromString("47570000-0000-4000-8000-000000000004");
    private static final UUID SOURCE_BRANCH = UUID.fromString("76000000-0000-4000-8000-000000000001");
    private static final UUID DESTINATION_BRANCH = UUID.fromString("76000000-0000-4000-8000-000000000002");
    private static final UUID TRANSFER_ID = UUID.fromString("76000000-0000-4000-8000-000000000020");
    private static final UUID DESTINATION_ROOM = UUID.fromString("76000000-0000-4000-8000-000000000030");
    private static final UUID OTHER_BRANCH_ROOM = UUID.fromString("76000000-0000-4000-8000-000000000031");
    private static final UUID INACTIVE_ROOM = UUID.fromString("76000000-0000-4000-8000-000000000032");
    private static final String TITLE = "UC-76 Confirm Equipment Receipt";
    private static final String SOURCE = "UC-76; BR-49, BR-50, BR-51, BR-111, BR-200; MSG02, MSG24, MSG82; V1, V14; EquipmentIncidentService.receive";

    private Uc76ConfirmEquipmentReceiptCases() {
    }

    static List<ManualCase> cases() {
        return List.of(
            test("ETM-016",
                "Branch Manager qlcs.nguyenluongbang đã đăng nhập và được phân công cơ sở nhận HN_NGUYEN_LUONG_BANG_86. Yêu cầu đang IN_TRANSIT. Phòng Q01 Active thuộc cơ sở nhận.",
                "Xác nhận tiếp nhận thiết bị tại phòng Q01.",
                "Ghi nhận tiếp nhận; chuyển yêu cầu sang COMPLETED; cập nhật thiết bị sang cơ sở nhận, phòng Q01 và giữ lịch sử chuyển.",
                () -> {
                    RecordingRepository repository = repository("IN_TRANSIT");
                    repository.activeDestinationRooms.add(DESTINATION_ROOM);
                    service(repository).receive(destinationManager(), TRANSFER_ID, command(DESTINATION_ROOM));
                    boolean passed = "COMPLETED".equals(repository.transferStatus)
                        && DESTINATION_MANAGER_ID.equals(repository.receivedBy)
                        && DESTINATION_BRANCH.equals(repository.assetBranch)
                        && DESTINATION_ROOM.equals(repository.assetRoom)
                        && "IN_USE".equals(repository.assetStatus)
                        && SOURCE_MANAGER_ID.equals(repository.handedOverBy)
                        && repository.receiveCount == 1;
                    return new Observation("transferStatus=" + repository.transferStatus
                        + ", receivedBy=" + repository.receivedBy
                        + ", assetBranch=" + repository.assetBranch
                        + ", assetRoom=" + repository.assetRoom
                        + ", assetStatus=" + repository.assetStatus
                        + ", handoverHistory=" + repository.handedOverBy
                        + ", receiptWrites=" + repository.receiveCount, passed);
                }),
            test("ETM-017",
                "Branch Manager cơ sở nhận đã đăng nhập. Yêu cầu vẫn REQUESTED; cơ sở nguồn chưa xác nhận bàn giao.",
                "Xác nhận tiếp nhận thiết bị.",
                "Không tiếp nhận. Hiển thị: Chưa thể xác nhận nhận thiết bị vì cơ sở gửi chưa xác nhận bàn giao.",
                () -> {
                    RecordingRepository repository = repository("REQUESTED");
                    repository.activeDestinationRooms.add(DESTINATION_ROOM);
                    String expected = "Chưa thể xác nhận nhận thiết bị vì cơ sở gửi chưa xác nhận bàn giao.";
                    try {
                        service(repository).receive(destinationManager(), TRANSFER_ID, command(DESTINATION_ROOM));
                        return new Observation("Không phát sinh lỗi; receiptWrites=" + repository.receiveCount, false);
                    } catch (ResponseStatusException failure) {
                        String actual = failure.getReason();
                        boolean passed = expected.equals(actual)
                            && "REQUESTED".equals(repository.transferStatus)
                            && SOURCE_BRANCH.equals(repository.assetBranch)
                            && repository.receiveCount == 0;
                        return new Observation("message=" + actual + ", transferStatus=" + repository.transferStatus
                            + ", assetBranch=" + repository.assetBranch
                            + ", receiptWrites=" + repository.receiveCount, passed);
                    }
                }),
            test("ETM-018",
                "Branch Manager cơ sở nguồn đã đăng nhập. Yêu cầu đang IN_TRANSIT đến một cơ sở khác.",
                "Branch Manager nguồn xác nhận tiếp nhận.",
                "Không tiếp nhận. Hiển thị: Bạn không có quyền xác nhận tiếp nhận đối với phiếu điều chuyển.",
                () -> permission(sourceManager())),
            test("ETM-019",
                "Head Office Manager đã đăng nhập. Yêu cầu đang IN_TRANSIT.",
                "Head Office Manager xác nhận tiếp nhận thay Branch Manager đích.",
                "Không tiếp nhận. Hiển thị: Bạn không có quyền xác nhận tiếp nhận đối với phiếu điều chuyển.",
                () -> permission(headOffice())),
            test("ETM-020",
                "Branch Manager cơ sở nhận đã đăng nhập. Yêu cầu đang IN_TRANSIT.",
                "Xác nhận tiếp nhận nhưng không chọn phòng nhận.",
                "Không tiếp nhận. Hiển thị: Vui lòng bổ sung phòng nhận thiết bị.",
                () -> {
                    RecordingRepository repository = repository("IN_TRANSIT");
                    String expected = "Vui lòng bổ sung phòng nhận thiết bị.";
                    try {
                        service(repository).receive(destinationManager(), TRANSFER_ID, command(null));
                        return new Observation("Không phát sinh lỗi; receiptWrites=" + repository.receiveCount, false);
                    } catch (ResponseStatusException failure) {
                        String actual = failure.getReason();
                        return new Observation("message=" + actual + ", receiptWrites=" + repository.receiveCount,
                            expected.equals(actual) && repository.receiveCount == 0);
                    }
                }),
            test("ETM-021",
                "Branch Manager cơ sở nhận đã đăng nhập. Yêu cầu đang IN_TRANSIT. Phòng được chọn thuộc cơ sở khác.",
                "Xác nhận tiếp nhận vào phòng ngoài cơ sở nhận.",
                "Từ chối; giữ yêu cầu IN_TRANSIT và không thay đổi thiết bị.",
                () -> invalidRoom(OTHER_BRANCH_ROOM)),
            test("ETM-022",
                "Branch Manager cơ sở nhận đã đăng nhập. Yêu cầu đang IN_TRANSIT. Phòng được chọn thuộc cơ sở nhận nhưng Inactive.",
                "Xác nhận tiếp nhận vào phòng Inactive.",
                "Từ chối; giữ yêu cầu IN_TRANSIT và không thay đổi thiết bị.",
                () -> invalidRoom(INACTIVE_ROOM)),
            test("ETM-023",
                "Yêu cầu đã COMPLETED và thiết bị đã được gán cho cơ sở, phòng nhận. Branch Manager cơ sở nhận đã đăng nhập.",
                "Xác nhận tiếp nhận lại cùng yêu cầu.",
                "Không tạo lần tiếp nhận thứ hai; giữ nguyên người nhận, thời điểm nhận, cơ sở, phòng và lịch sử.",
                () -> {
                    RecordingRepository repository = repository("COMPLETED");
                    repository.assetBranch = DESTINATION_BRANCH;
                    repository.assetRoom = DESTINATION_ROOM;
                    repository.receivedBy = DESTINATION_MANAGER_ID;
                    repository.activeDestinationRooms.add(DESTINATION_ROOM);
                    try {
                        service(repository).receive(destinationManager(), TRANSFER_ID, command(DESTINATION_ROOM));
                        return new Observation("Không phát sinh lỗi; receiptWrites=" + repository.receiveCount, false);
                    } catch (ResponseStatusException failure) {
                        boolean passed = "COMPLETED".equals(repository.transferStatus)
                            && DESTINATION_BRANCH.equals(repository.assetBranch)
                            && DESTINATION_ROOM.equals(repository.assetRoom)
                            && DESTINATION_MANAGER_ID.equals(repository.receivedBy)
                            && repository.receiveCount == 0;
                        return new Observation("message=" + failure.getReason()
                            + ", transferStatus=" + repository.transferStatus
                            + ", assetBranch=" + repository.assetBranch
                            + ", assetRoom=" + repository.assetRoom
                            + ", receiptWrites=" + repository.receiveCount, passed);
                    }
                })
        );
    }

    private static Observation permission(Identity actor) {
        RecordingRepository repository = repository("IN_TRANSIT");
        repository.activeDestinationRooms.add(DESTINATION_ROOM);
        String expected = "Bạn không có quyền xác nhận tiếp nhận đối với phiếu điều chuyển.";
        try {
            service(repository).receive(actor, TRANSFER_ID, command(DESTINATION_ROOM));
            return new Observation("Không phát sinh lỗi; receiptWrites=" + repository.receiveCount, false);
        } catch (ResponseStatusException failure) {
            String actual = failure.getReason();
            boolean passed = expected.equals(actual)
                && "IN_TRANSIT".equals(repository.transferStatus)
                && SOURCE_BRANCH.equals(repository.assetBranch)
                && repository.receiveCount == 0;
            return new Observation("message=" + actual + ", transferStatus=" + repository.transferStatus
                + ", assetBranch=" + repository.assetBranch
                + ", receiptWrites=" + repository.receiveCount, passed);
        }
    }

    private static Observation invalidRoom(UUID room) {
        RecordingRepository repository = repository("IN_TRANSIT");
        UUID branchBefore = repository.assetBranch;
        UUID roomBefore = repository.assetRoom;
        try {
            service(repository).receive(destinationManager(), TRANSFER_ID, command(room));
            return new Observation("Không phát sinh lỗi; receiptWrites=" + repository.receiveCount, false);
        } catch (ResponseStatusException failure) {
            boolean passed = "IN_TRANSIT".equals(repository.transferStatus)
                && branchBefore.equals(repository.assetBranch)
                && java.util.Objects.equals(roomBefore, repository.assetRoom)
                && repository.receiveCount == 0;
            return new Observation("message=" + failure.getReason()
                + ", transferStatus=" + repository.transferStatus
                + ", assetBranch=" + repository.assetBranch
                + ", assetRoom=" + repository.assetRoom
                + ", receiptWrites=" + repository.receiveCount, passed);
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
        repository.assetBranch = SOURCE_BRANCH;
        repository.assetStatus = "IN_TRANSIT";
        repository.handedOverBy = SOURCE_MANAGER_ID;
        return repository;
    }

    private static TransferReceiptCommand command(UUID room) {
        return new TransferReceiptCommand(room, "Đã nhận đủ thiết bị");
    }

    private static Identity destinationManager() {
        return new Identity(DESTINATION_MANAGER_ID, "qlcs.nguyenluongbang", "BRANCH_MANAGER", "/branch-management",
            DESTINATION_BRANCH, "HN_NGUYEN_LUONG_BANG_86");
    }

    private static Identity sourceManager() {
        return new Identity(SOURCE_MANAGER_ID, "qlcs.hoabang", "BRANCH_MANAGER", "/branch-management",
            SOURCE_BRANCH, "HN_HOA_BANG");
    }

    private static Identity headOffice() {
        return new Identity(HOM_ID, "qlcc", "HEAD_OFFICE_MANAGER", "/head-office", null, null);
    }

    private static final class RecordingRepository extends EquipmentIncidentRepository {
        private String transferStatus;
        private UUID assetBranch;
        private UUID assetRoom;
        private String assetStatus;
        private UUID handedOverBy;
        private UUID receivedBy;
        private int receiveCount;
        private final Set<UUID> activeDestinationRooms = new HashSet<>();

        private RecordingRepository() {
            super(null);
        }

        @Override
        public int receive(UUID id, UUID branch, UUID actor, UUID room) {
            if (!TRANSFER_ID.equals(id)
                || !DESTINATION_BRANCH.equals(branch)
                || !"IN_TRANSIT".equals(transferStatus)
                || !activeDestinationRooms.contains(room)) {
                return 0;
            }
            transferStatus = "COMPLETED";
            receivedBy = actor;
            assetBranch = DESTINATION_BRANCH;
            assetRoom = room;
            assetStatus = "IN_USE";
            receiveCount++;
            return 1;
        }

        @Override
        public boolean awaitingHandover(UUID id, UUID destination) {
            return TRANSFER_ID.equals(id)
                && DESTINATION_BRANCH.equals(destination)
                && "REQUESTED".equals(transferStatus);
        }
    }
}
