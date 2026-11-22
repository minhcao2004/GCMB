package com.gcmb.backend.inventory.service;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.booking.service.RefundEvidenceStorageService;
import com.gcmb.backend.inventory.dto.EquipmentAssetDtos;
import com.gcmb.backend.inventory.dto.EquipmentCategoryDtos;
import com.gcmb.backend.inventory.dto.EquipmentIncidentDtos.*;
import com.gcmb.backend.inventory.dto.InventoryDtos.*;
import com.gcmb.backend.inventory.dto.MenuItemDtos.MenuItemCommand;
import com.gcmb.backend.inventory.repository.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventorySystemMessagesTest {
    private static final UUID BRANCH = UUID.randomUUID(), ID = UUID.randomUUID(), CATEGORY = UUID.randomUUID();
    private static final Identity MANAGER = actor("BRANCH_MANAGER"), HQ = actor("HEAD_OFFICE_MANAGER");

    private static Identity actor(String role) {
        return new Identity(UUID.randomUUID(), "tester", role, "/", BRANCH, "Hoa Bằng");
    }

    private static void error(int status, String message, Runnable action) {
        var failure = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(status, failure.getStatusCode().value());
        assertEquals(message, failure.getReason());
    }

    static Stream<Arguments> menuInputs() {
        return Stream.of(
            Arguments.of(" ", BigDecimal.ZERO, "Vui lòng bổ sung tên món."),
            Arguments.of("Trà", null, "Vui lòng bổ sung giá bán."),
            Arguments.of("Trà", new BigDecimal("-1"), "Giá bán phải lớn hơn hoặc bằng 0."),
            Arguments.of("Trà", new BigDecimal("20000.5"), "Giá bán phải là số tiền nguyên đồng, không có phần thập phân."),
            Arguments.of("x".repeat(201), BigDecimal.ZERO, "Tên món chỉ được chứa tối đa 200 ký tự.")
        );
    }

    @ParameterizedTest @MethodSource("menuInputs")
    void invalidMenuInputReportsSpecificCauseWithoutWriting(String name, BigDecimal price, String message) {
        var repo = mock(MenuItemRepository.class);
        when(repo.activeBranch(BRANCH)).thenReturn(true);
        var service = new MenuItemService(repo, mock(RefundEvidenceStorageService.class));
        error(400, message, () -> service.create(MANAGER,
            new MenuItemCommand(name, "Đồ uống", "PREPARED", null, price, true, null, false), null));
        verify(repo, never()).create(any(), any(), any());
    }

    @Test void suspendedBranchNamesTheBranchWithoutWriting() {
        var repo = mock(MenuItemRepository.class);
        when(repo.branchName(BRANCH)).thenReturn("GenZ Cinema Hoa Bằng");
        var service = new MenuItemService(repo, mock(RefundEvidenceStorageService.class));
        error(403, "Cơ sở GenZ Cinema Hoa Bằng đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi.",
            () -> service.create(MANAGER, null, null));
        verify(repo, never()).create(any(), any(), any());
    }

    @Test void menuAndBookingPermissionsNameTheActionBeforeReadingData() {
        var menu = mock(MenuItemRepository.class);
        error(403, "Bạn không có quyền tạo món đối với thực đơn.",
            () -> new MenuItemService(menu, null).create(actor("ACCOUNTANT"), null, null));
        verifyNoInteractions(menu);
        var orders = mock(FoodOrderRepository.class);
        error(403, "Bạn không có quyền thêm món đối với booking.",
            () -> new FoodOrderService(orders, menu, null).addItems(actor("ACCOUNTANT"), null));
        verifyNoInteractions(orders);
    }

    @Test void stockAdjustmentRequiresReasonBeforeWriting() {
        var repo = mock(InventoryRepository.class);
        when(repo.activeBranch(BRANCH)).thenReturn(true);
        var service = new InventoryService(repo, null);
        for (String reason : new String[]{" ", "x".repeat(1001)}) {
            String message = reason.isBlank() ? "Vui lòng bổ sung lý do điều chỉnh."
                : "Lý do điều chỉnh chỉ được chứa tối đa 1000 ký tự.";
            error(400, message, () -> service.adjust(MANAGER,
                new StockAdjustmentCommand(ID, Instant.now(), 2L, BigDecimal.TEN, BigDecimal.ONE, reason), null));
        }
        verify(repo, never()).lockItem(any(), any());
    }

    @Test void invalidInventoryRoleDoesNotReadOrWrite() {
        var repo = mock(InventoryRepository.class);
        error(403, "Bạn không có quyền cập nhật đối với mặt hàng kho.",
            () -> new InventoryService(repo, null).update(actor("RECEPTIONIST"), ID, null));
        error(403, "Bạn không có quyền tạo đối với phiếu nhập kho.",
            () -> new InventoryService(repo, null).receive(actor("RECEPTIONIST"), null, null));
        verifyNoInteractions(repo);
    }

    static Stream<Arguments> categoryInputs() {
        return Stream.of(
            Arguments.of(null, "Vui lòng bổ sung thông tin nhóm thiết bị."),
            Arguments.of(new EquipmentCategoryDtos.Command(" ", "Loa", ""), "Vui lòng bổ sung mã nhóm thiết bị."),
            Arguments.of(new EquipmentCategoryDtos.Command("EQ-AUDIO", " ", ""), "Vui lòng bổ sung tên nhóm thiết bị.")
        );
    }

    @ParameterizedTest @MethodSource("categoryInputs")
    void categoryRequiredFieldsDoNotWrite(EquipmentCategoryDtos.Command input, String message) {
        var repo = mock(EquipmentCategoryRepository.class);
        error(400, message, () -> new EquipmentCategoryService(repo).create(HQ, null, input));
        verifyNoInteractions(repo);
    }

    @Test void duplicateCategoryIsDistinctFromOtherDatabaseConstraints() {
        var repo = mock(EquipmentCategoryRepository.class);
        var service = new EquipmentCategoryService(repo);
        var input = new EquipmentCategoryDtos.Command("EQ-AUDIO", "Loa", "");
        when(repo.create(any(), any())).thenThrow(new DuplicateKeyException("unique"));
        error(409, "Mã nhóm thiết bị “EQ-AUDIO” đã tồn tại trong danh mục toàn chuỗi. Vui lòng nhập mã khác.",
            () -> service.create(HQ, null, input));
        doThrow(new DataIntegrityViolationException("foreign key")).when(repo).create(any(), any());
        error(409, "Chưa thể xác nhận kết quả tạo nhóm thiết bị. Vui lòng kiểm tra lại trạng thái của nhóm thiết bị trước khi thực hiện lại.",
            () -> service.create(HQ, null, input));
    }

    @Test void categoryPermissionsRemainHeadOfficeOnly() {
        var repo = mock(EquipmentCategoryRepository.class);
        var service = new EquipmentCategoryService(repo);
        error(403, "Bạn không có quyền đổi trạng thái đối với nhóm thiết bị.", () -> service.setActive(MANAGER, null, ID, false));
        error(403, "Bạn không có quyền xóa đối với nhóm thiết bị.", () -> service.delete(MANAGER, null, ID));
        verifyNoInteractions(repo);
    }

    static Stream<Arguments> assetInputs() {
        return Stream.of(
            Arguments.of(" ", "Loa", CATEGORY, "Kho", "Vui lòng bổ sung mã thiết bị."),
            Arguments.of("ASSET-HN01-0001", " ", CATEGORY, "Kho", "Vui lòng bổ sung tên thiết bị."),
            Arguments.of("ASSET-HN01-0001", "Loa", null, "Kho", "Vui lòng bổ sung nhóm thiết bị."),
            Arguments.of("ASSET-HN01-0001", "Loa", CATEGORY, " ", "Vui lòng bổ sung vị trí thiết bị.")
        );
    }

    @ParameterizedTest @MethodSource("assetInputs")
    void assetRequiredFieldsDoNotWrite(String code, String name, UUID category, String location, String message) {
        var repo = mock(EquipmentAssetRepository.class);
        var service = new EquipmentAssetService(repo, null);
        error(400, message, () -> service.create(MANAGER, BRANCH,
            new EquipmentAssetDtos.Command(code, name, category, null, location, null, null, null, null, "IN_USE")));
        verifyNoInteractions(repo);
    }

    @Test void assetScopeNamesTheRequestedAction() {
        var repo = mock(EquipmentAssetRepository.class);
        error(403, "Bạn không có quyền cập nhật đối với thiết bị.",
            () -> new EquipmentAssetService(repo, null).update(MANAGER, UUID.randomUUID(), ID, null));
        verifyNoInteractions(repo);
    }

    static Stream<Arguments> incidentInputs() {
        return Stream.of(
            Arguments.of(null, "Vui lòng bổ sung thông tin báo cáo sự cố."),
            Arguments.of(new IncidentCommand(null, "Hỏng", false, "LOW", "NORMAL", LocalDate.now(), "REPAIR", "Vendor", null, null, null), "Vui lòng bổ sung thiết bị."),
            Arguments.of(new IncidentCommand(ID, " ", false, "LOW", "NORMAL", LocalDate.now(), "REPAIR", "Vendor", null, null, null), "Vui lòng bổ sung mô tả sự cố."),
            Arguments.of(new IncidentCommand(ID, "Hỏng", false, "LOW", "NORMAL", null, "REPAIR", "Vendor", null, null, null), "Vui lòng bổ sung ngày dự kiến hoàn thành."),
            Arguments.of(new IncidentCommand(ID, "Hỏng", false, "LOW", "NORMAL", LocalDate.now(), null, "Vendor", null, null, null), "Vui lòng bổ sung phương án xử lý."),
            Arguments.of(new IncidentCommand(ID, "Hỏng", false, "LOW", "NORMAL", LocalDate.now(), "REPAIR", " ", null, null, null), "Vui lòng bổ sung đơn vị sửa chữa/thay thế."),
            Arguments.of(new IncidentCommand(ID, "Hỏng", false, "LOW", "NORMAL", LocalDate.now(), "REPLACE", "Vendor", " ", null, null), "Vui lòng bổ sung mô tả thiết bị thay thế.")
        );
    }

    @ParameterizedTest @MethodSource("incidentInputs")
    void incidentRequiredFieldsDoNotWrite(IncidentCommand input, String message) {
        var repo = mock(EquipmentIncidentRepository.class);
        error(400, message, () -> new EquipmentIncidentService(repo, null).create(MANAGER, BRANCH, input, null));
        verifyNoInteractions(repo);
    }

    @Test void noActionAssessmentRemainsAllowed() {
        var repo = mock(EquipmentIncidentRepository.class);
        var input = new AssessmentCommand("NO_ACTION", "Không cần sửa", null);
        when(repo.assess(BRANCH, MANAGER.id(), ID, input)).thenReturn(1);
        new EquipmentIncidentService(repo, null).assess(MANAGER, BRANCH, ID, input);
        verify(repo).assess(BRANCH, MANAGER.id(), ID, input);
        verify(repo).updateAssetForPlan(BRANCH, ID, "NO_ACTION");
    }

    static Stream<Arguments> repairInputs() {
        return Stream.of(
            Arguments.of(null, "Vui lòng bổ sung thông tin kết quả sửa chữa."),
            Arguments.of(new RepairCommand("Vendor", " ", BigDecimal.ONE, LocalDate.now(), "IN_USE"), "Vui lòng bổ sung kết quả sửa chữa."),
            Arguments.of(new RepairCommand("Vendor", "Đạt", null, LocalDate.now(), "IN_USE"), "Vui lòng bổ sung chi phí sửa chữa thực tế."),
            Arguments.of(new RepairCommand("Vendor", "Đạt", BigDecimal.ONE, null, "IN_USE"), "Vui lòng bổ sung ngày hoàn tất sửa chữa."),
            Arguments.of(new RepairCommand("Vendor", "Đạt", BigDecimal.ONE, LocalDate.now(), "RETIRED"), "Vui lòng bổ sung tình trạng thiết bị sau sửa chữa hợp lệ.")
        );
    }

    @ParameterizedTest @MethodSource("repairInputs")
    void invalidRepairDoesNotWriteOrStoreEvidence(RepairCommand input, String message) {
        var repo = mock(EquipmentIncidentRepository.class);
        var storage = mock(EquipmentAssetFileStorageService.class);
        var proof = new MockMultipartFile("proof", new byte[]{1});
        error(400, message, () -> new EquipmentIncidentService(repo, storage).repair(MANAGER, BRANCH, ID, input, proof, proof));
        verifyNoInteractions(repo, storage);
    }

    @Test void wrongTransferBranchIsDeniedBeforeMutation() {
        var repo = mock(EquipmentIncidentRepository.class);
        when(repo.transferBranch(ID, true)).thenReturn(UUID.randomUUID());
        when(repo.transferBranch(ID, false)).thenReturn(UUID.randomUUID());
        var service = new EquipmentIncidentService(repo, null);
        error(403, "Bạn không có quyền xác nhận bàn giao đối với phiếu điều chuyển.", () -> service.handover(MANAGER, ID));
        error(403, "Bạn không có quyền xác nhận tiếp nhận đối với phiếu điều chuyển.",
            () -> service.receive(MANAGER, ID, new TransferReceiptCommand(UUID.randomUUID(), "")));
        verify(repo, never()).handover(any(), any(), any());
        verify(repo, never()).receive(any(), any(), any(), any());
    }

    @Test void headOfficeCannotHandoverOrReceiveForBranchManagers() {
        var repo = mock(EquipmentIncidentRepository.class);
        var service = new EquipmentIncidentService(repo, null);
        error(403, "Bạn không có quyền xác nhận bàn giao đối với phiếu điều chuyển.", () -> service.handover(HQ, ID));
        error(403, "Bạn không có quyền xác nhận tiếp nhận đối với phiếu điều chuyển.", () -> service.receive(HQ, ID, null));
        verifyNoInteractions(repo);
    }

    @Test void receiptBeforeHandoverStillReportsMsg82() {
        var repo = mock(EquipmentIncidentRepository.class);
        when(repo.transferBranch(ID, false)).thenReturn(BRANCH);
        when(repo.awaitingHandover(ID, BRANCH)).thenReturn(true);
        error(409, "Chưa thể xác nhận nhận thiết bị vì cơ sở gửi chưa xác nhận bàn giao.",
            () -> new EquipmentIncidentService(repo, null).receive(MANAGER, ID, new TransferReceiptCommand(UUID.randomUUID(), "")));
    }

    @Test void unauthorizedTransferListNamesTheTransferEntity() {
        var repo = mock(EquipmentIncidentRepository.class);
        error(403, "Bạn không có quyền xem đối với phiếu điều chuyển.",
            () -> new EquipmentIncidentService(repo, null).transfers(actor("ACCOUNTANT"), BRANCH));
        verifyNoInteractions(repo);
    }
}
