package com.gcmb.backend.inventory.service;

import com.gcmb.backend.audit.service.MutationAudit;
import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.booking.service.RefundEvidenceStorageService;
import com.gcmb.backend.booking.service.RefundEvidenceStorageService.StoredEvidence;
import com.gcmb.backend.inventory.dto.InventoryDtos.EvidenceContent;
import com.gcmb.backend.inventory.dto.InventoryDtos.InventoryItemCommand;
import com.gcmb.backend.inventory.dto.InventoryDtos.InventoryItemView;
import com.gcmb.backend.inventory.dto.InventoryDtos.InventoryItemWithMenuCommand;
import com.gcmb.backend.inventory.dto.InventoryDtos.InventoryMetadata;
import com.gcmb.backend.inventory.dto.InventoryDtos.ReceiptLineCommand;
import com.gcmb.backend.inventory.dto.InventoryDtos.ReceiptLineResult;
import com.gcmb.backend.inventory.dto.InventoryDtos.StockAdjustmentCommand;
import com.gcmb.backend.inventory.dto.InventoryDtos.StockMovementView;
import com.gcmb.backend.inventory.dto.InventoryDtos.StockReceiptCommand;
import com.gcmb.backend.inventory.dto.InventoryDtos.StockReceiptResult;
import com.gcmb.backend.inventory.dto.MenuItemDtos.MenuItemCommand;
import com.gcmb.backend.inventory.dto.MenuItemDtos.MenuItemView;
import com.gcmb.backend.inventory.repository.InventoryRepository;
import com.gcmb.backend.inventory.repository.InventoryRepository.EvidenceRow;
import com.gcmb.backend.inventory.repository.InventoryRepository.LockedItem;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InventoryService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("999999999999999.999");
    private static final BigDecimal MAX_UNIT_COST = new BigDecimal("999999999999999999");
    private static final Set<String> TRACKING_MODES = Set.of("AUTO_ON_SALE", "MANUAL");
    private static final Set<String> MOVEMENT_KINDS = Set.of("OPENING", "RECEIPT", "SALE", "ADJUSTMENT", "RETURN");

    private final InventoryRepository inventoryRepository;
    private final RefundEvidenceStorageService refundEvidenceStorageService;
    private final MenuItemService menuItemService;
    private final MutationAudit mutationAudit;

    public InventoryService(InventoryRepository inventoryRepository, RefundEvidenceStorageService refundEvidenceStorageService,
                            MenuItemService menuItemService, MutationAudit mutationAudit) {
        this.inventoryRepository = inventoryRepository;
        this.refundEvidenceStorageService = refundEvidenceStorageService;
        this.menuItemService = menuItemService;
        this.mutationAudit = mutationAudit;
    }

    @Transactional(readOnly = true)
    public List<InventoryItemView> list(Identity actor) {
        return inventoryRepository.list(branch(actor));
    }

    @Transactional(readOnly = true)
    public InventoryMetadata metadata(Identity actor) {
        UUID branchId = branch(actor);
        return new InventoryMetadata(inventoryRepository.units(), inventoryRepository.categories(branchId));
    }

    @Transactional(readOnly = true)
    public InventoryItemView item(Identity actor, UUID id) {
        InventoryItemView item = inventoryRepository.get(branch(actor), id);
        if (item == null) throw notFound();
        return item;
    }

    @Transactional
    public InventoryItemView create(Identity actor, InventoryItemCommand input) {
        UUID branchId = writableBranch(actor, "tạo", "mặt hàng kho");
        if (input == null) throw bad("Vui lòng bổ sung thông tin mặt hàng.");
        // Mã mặt hàng được cấp tự động trong phạm vi từng cơ sở.
        InventoryItemCommand command = validate(new InventoryItemCommand(inventoryRepository.nextInventorySku(branchId),
            input.name(), input.category(), input.unitId(), input.trackingMode(), input.minimumQuantity(), input.active()), branchId);
        try {
            UUID id = inventoryRepository.create(branchId, command.sku(), command.name(), command.category(), command.unitId(),
                command.trackingMode(), command.minimumQuantity());
            return required(branchId, id);
        } catch (DataIntegrityViolationException exception) {
            throw constraintFailure(exception, "tạo mặt hàng");
        }
    }

    @Transactional
    public InventoryItemView createWithMenu(Identity actor, InventoryItemWithMenuCommand input) {
        // Hai bản ghi và hai sự kiện audit cùng giao dịch; lỗi tạo món sẽ hoàn tác mặt hàng kho.
        InventoryItemView item = create(actor, input == null ? null : input.item());
        mutationAudit.record("CREATE_MENU_ITEM", "MENU_ITEM", null,
            () -> menuItemService.create(actor, new MenuItemCommand(item.name(), item.category(), "PACKAGED", item.id(),
                input.salePrice(), true, null, false), null), MenuItemView::id);
        return item;
    }

    @Transactional
    public InventoryItemView update(Identity actor, UUID id, InventoryItemCommand input) {
        UUID branchId = writableBranch(actor, "cập nhật", "mặt hàng kho");
        LockedItem existing = inventoryRepository.lockItem(branchId, id);
        if (existing == null) throw notFound();
        InventoryItemCommand command = validate(input, branchId);
        if (existing.active() && !Boolean.TRUE.equals(command.active()) && inventoryRepository.hasActiveMenuLink(id)) {
            throw conflict("Mặt hàng đang được gắn với món F&B đang bán. Hãy ngừng bán hoặc bỏ liên kết trước khi ngừng sử dụng.");
        }
        if (!existing.unitId().equals(command.unitId()) && inventoryRepository.hasMovements(id)) {
            throw conflict("Không thể đổi đơn vị vì mặt hàng đã có lịch sử kho. Hãy tạo mặt hàng mới nếu cần dùng đơn vị khác.");
        }
        try {
            inventoryRepository.update(branchId, id, command.sku(), command.name(), command.category(), command.unitId(),
                command.trackingMode(), command.minimumQuantity(), Boolean.TRUE.equals(command.active()));
            return required(branchId, id);
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã mặt hàng “" + command.sku()
                + "” đã tồn tại tại cơ sở này. Vui lòng nhập mã khác.", exception);
        } catch (DataIntegrityViolationException exception) {
            throw constraintFailure(exception, "cập nhật mặt hàng");
        }
    }

    @Transactional
    public void delete(Identity actor, UUID id) {
        UUID branchId = writableBranch(actor, "xóa", "mặt hàng kho");
        LockedItem existing = inventoryRepository.lockItem(branchId, id);
        if (existing == null) throw notFound();
        if (!existing.active()) return;
        if (inventoryRepository.hasActiveMenuLink(id)) {
            throw conflict("Mặt hàng đang được gắn với món F&B đang bán. Hãy ngừng bán hoặc bỏ liên kết trước khi xóa.");
        }
        inventoryRepository.archive(branchId, id);
    }

    @Transactional(readOnly = true)
    public List<StockMovementView> history(Identity actor, UUID itemId, java.time.Instant from,
                                          java.time.Instant to, String requestedKind, String search) {
        UUID branchId = branch(actor);
        if (inventoryRepository.get(branchId, itemId) == null) throw notFound();
        if (from != null && to != null && to.isBefore(from)) {
            throw bad("Thời điểm kết thúc phải bằng hoặc sau thời điểm bắt đầu.");
        }
        String kind = requestedKind == null || requestedKind.isBlank() || "ALL".equalsIgnoreCase(requestedKind)
            ? null : requestedKind.trim().toUpperCase(Locale.ROOT);
        if (kind != null && !MOVEMENT_KINDS.contains(kind)) throw bad("Loại biến động kho không hợp lệ.");
        String normalizedSearch = clean(search);
        if (normalizedSearch != null && normalizedSearch.length() > 100) {
            throw bad("Từ khóa tìm kiếm chỉ được chứa tối đa 100 ký tự.");
        }
        return inventoryRepository.history(branchId, itemId, from, to, kind, normalizedSearch);
    }

    @Transactional
    // BR-31, BR-32, BR-33, BR-40, BR-197: xác nhận phiếu nhập cộng kho theo số lượng nhận, không ghi nhận thanh toán nhà cung cấp.
    public StockReceiptResult receive(Identity actor, StockReceiptCommand input, MultipartFile evidence) {
        UUID branchId = writableBranch(actor, "tạo", "phiếu nhập kho");
        StockReceiptCommand command = validateReceipt(input, branchId);
        StoredEvidence stored = evidence == null ? null : refundEvidenceStorageService.store(evidence, "kho-hang");
        if (stored != null) cleanupIfRolledBack(stored.objectKey());

        String code = inventoryRepository.nextReceiptCode();
        UUID receiptId = inventoryRepository.insertReceipt(branchId, code, required(command.supplierName(), "Nhà cung cấp", 200),
            optional(command.supplierInvoice(), 200, "Tham chiếu mua hàng"), command.receivedAt(), actor.id(),
            stored == null ? null : stored.objectKey(), stored == null ? null : stored.originalName(),
            stored == null ? null : stored.mimeType());

        List<UUID> lineIds = new ArrayList<>();
        for (ReceiptLineCommand line : command.items()) {
            lineIds.add(inventoryRepository.insertReceiptLine(receiptId, branchId, line));
        }
        List<UUID> itemIds = command.items().stream().map(ReceiptLineCommand::inventoryItemId)
            .sorted(Comparator.comparing(UUID::toString)).toList();
        Map<UUID, LockedItem> lockedItems = new HashMap<>();
        for (UUID itemId : itemIds) {
            LockedItem item = inventoryRepository.lockItem(branchId, itemId);
            if (item == null || !item.active()) {
                throw conflict("Một mặt hàng đã ngừng dùng hoặc không thuộc cơ sở này. Hãy tải lại danh sách.");
            }
            lockedItems.put(itemId, item);
        }
        inventoryRepository.postReceipt(receiptId, actor.id());

        List<ReceiptLineResult> results = new ArrayList<>();
        BigDecimal totalQuantity = ZERO;
        BigDecimal totalValue = ZERO;
        for (int index = 0; index < command.items().size(); index++) {
            ReceiptLineCommand line = command.items().get(index);
            InventoryItemView item = required(branchId, line.inventoryItemId());
            LockedItem lockedItem = lockedItems.get(line.inventoryItemId());
            inventoryRepository.insertReceiptMovement(line.inventoryItemId(), lineIds.get(index), line.quantity(),
                lockedItem.quantity(), line.unitCost(), actor.id(), "Nhập hàng theo phiếu " + code);
            BigDecimal amount = line.quantity().multiply(line.unitCost()).setScale(0, RoundingMode.HALF_UP);
            results.add(new ReceiptLineResult(item.id(), item.sku(), item.name(), item.unitName(), line.quantity(),
                line.unitCost(), amount, lockedItem.quantity().add(line.quantity())));
            totalQuantity = totalQuantity.add(line.quantity());
            totalValue = totalValue.add(amount);
        }
        return new StockReceiptResult(code, command.receivedAt(), results.size(), totalQuantity, totalValue, results);
    }

    @Transactional
    // BR-34, BR-35, BR-40, BR-189, BR-190: lưu lý do và chênh lệch kiểm kê; yêu cầu rà soát khi số tồn đã đổi.
    public StockMovementView adjust(Identity actor, StockAdjustmentCommand input, MultipartFile evidence) {
        UUID branchId = writableBranch(actor, "điều chỉnh", "tồn kho");
        if (input == null || input.inventoryItemId() == null) throw bad("Vui lòng chọn mặt hàng cần kiểm kê.");
        if (input.occurredAt() == null) throw bad("Vui lòng nhập thời điểm kiểm kê.");
        if (input.expectedStockVersion() == null || input.expectedQuantity() == null) {
            throw bad("Không tìm thấy số tồn ban đầu. Hãy tải lại mặt hàng và kiểm tra lại.");
        }
        BigDecimal actual = quantity(input.actualQuantity(), "Tồn thực tế", true);
        String reason = required(input.reason(), "Lý do điều chỉnh", 1000);
        LockedItem item = inventoryRepository.lockItem(branchId, input.inventoryItemId());
        if (item == null) throw notFound();
        if (!item.active()) throw conflict("Mặt hàng đã ngừng sử dụng nên không thể điều chỉnh tồn.");
        if (item.version() != input.expectedStockVersion() || item.quantity().compareTo(input.expectedQuantity()) != 0) {
            InventoryItemView view = required(branchId, item.id());
            throw conflict("Tồn kho đã thay đổi từ " + quantityText(input.expectedQuantity()) + " thành "
                + quantityText(item.quantity()) + " " + view.unitName() + ". Vui lòng kiểm tra lại trước khi xác nhận điều chỉnh.");
        }
        BigDecimal delta = actual.subtract(item.quantity());
        if (delta.signum() == 0) throw bad("Tồn thực tế không thay đổi. Không cần ghi nhận điều chỉnh.");
        StoredEvidence stored = evidence == null ? null : refundEvidenceStorageService.store(evidence, "kho-hang");
        if (stored != null) cleanupIfRolledBack(stored.objectKey());
        UUID movementId = inventoryRepository.insertAdjustment(item.id(), delta, item.quantity(), input.occurredAt(), actor.id(), reason,
            stored == null ? null : stored.objectKey(), stored == null ? null : stored.originalName(),
            stored == null ? null : stored.mimeType());
        return inventoryRepository.movement(branchId, item.id(), movementId);
    }

    @Transactional(readOnly = true)
    public EvidenceContent evidence(Identity actor, UUID itemId, UUID movementId) {
        UUID branchId = branch(actor);
        EvidenceRow row = inventoryRepository.evidence(branchId, itemId, movementId);
        if (row == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy chứng từ kho.");
        try {
            return new EvidenceContent(row.originalName(), row.mimeType(), refundEvidenceStorageService.read(row.objectKey()));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tệp chứng từ kho không còn khả dụng.", exception);
        }
    }

    // BR-31, BR-33, BR-197: số lượng nhận phải dương; chi phí dòng là số lượng nhân đơn giá.
    private StockReceiptCommand validateReceipt(StockReceiptCommand input, UUID branchId) {
        if (input == null) throw bad("Vui lòng bổ sung thông tin phiếu nhập.");
        if (input.receivedAt() == null) throw bad("Vui lòng nhập ngày nhận hàng.");
        String supplier = required(input.supplierName(), "Nhà cung cấp", 200);
        String invoice = optional(input.supplierInvoice(), 200, "Tham chiếu mua hàng");
        if (input.items() == null || input.items().isEmpty()) throw bad("Thêm ít nhất một mặt hàng vào phiếu nhập.");
        Set<UUID> unique = new HashSet<>();
        List<ReceiptLineCommand> lines = new ArrayList<>();
        for (ReceiptLineCommand line : input.items()) {
            if (line == null || line.inventoryItemId() == null) throw bad("Vui lòng chọn mặt hàng cho từng dòng nhập.");
            if (!unique.add(line.inventoryItemId())) throw bad("Mỗi mặt hàng chỉ xuất hiện một lần trong phiếu nhập.");
            InventoryItemView item = inventoryRepository.get(branchId, line.inventoryItemId());
            if (item == null || !item.active()) throw conflict("Một mặt hàng đã ngừng dùng hoặc không thuộc cơ sở này. Hãy tải lại danh sách.");
            BigDecimal received = quantity(line.quantity(), "Số lượng nhận", false);
            BigDecimal unitCost = money(line.unitCost(), "Đơn giá");
            LocalDate expiryDate = line.expiryDate();
            if (expiryDate == null) throw bad("Vui lòng bổ sung hạn sử dụng.");
            if (expiryDate.isBefore(input.receivedAt().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate())) {
                throw bad("Hạn sử dụng không được trước ngày nhận hàng.");
            }
            lines.add(new ReceiptLineCommand(line.inventoryItemId(), received, unitCost, expiryDate));
        }
        return new StockReceiptCommand(input.receivedAt(), supplier, invoice, lines);
    }

    private InventoryItemCommand validate(InventoryItemCommand input, UUID branchId) {
        if (input == null) throw bad("Vui lòng bổ sung thông tin mặt hàng.");
        String sku = required(input.sku(), "Mã mặt hàng", 60).toUpperCase(Locale.ROOT);
        if (!sku.matches("[A-Z0-9_-]+")) throw bad("Mã mặt hàng chỉ dùng chữ không dấu, số, dấu gạch ngang hoặc gạch dưới.");
        String name = required(input.name(), "Tên mặt hàng", 200);
        String requestedCategory = required(input.category(), "Nhóm hàng", 100);
        String category = inventoryRepository.categories(branchId).stream().filter(option -> option.equalsIgnoreCase(requestedCategory))
            .findFirst().orElseThrow(() -> bad("Vui lòng chọn nhóm hàng trong danh sách."));
        if (input.unitId() == null || inventoryRepository.units().stream().noneMatch(unit -> unit.id().equals(input.unitId()))) {
            throw bad("Vui lòng chọn đơn vị tính hợp lệ.");
        }
        String tracking = input.trackingMode() == null ? "" : input.trackingMode().trim().toUpperCase(Locale.ROOT);
        if (!TRACKING_MODES.contains(tracking)) throw bad("Vui lòng chọn cách theo dõi tồn hợp lệ.");
        BigDecimal minimum = quantity(input.minimumQuantity(), "Ngưỡng tồn thấp", true);
        return new InventoryItemCommand(sku, name, category, input.unitId(), tracking, minimum,
            input.active() == null || input.active());
    }

    private BigDecimal quantity(BigDecimal value, String field, boolean allowZero) {
        if (value == null || value.signum() < 0 || (!allowZero && value.signum() == 0)
                || value.compareTo(MAX_QUANTITY) > 0 || value.stripTrailingZeros().scale() > 3) {
            throw bad(field + " phải " + (allowZero ? "lớn hơn hoặc bằng 0" : "lớn hơn 0")
                + " và tối đa 3 chữ số thập phân.");
        }
        return value.stripTrailingZeros();
    }

    private BigDecimal money(BigDecimal value, String field) {
        if (value == null || value.signum() < 0 || value.compareTo(MAX_UNIT_COST) > 0
                || value.stripTrailingZeros().scale() > 0) {
            throw bad(field + " phải là số nguyên VND không âm, tối đa 18 chữ số.");
        }
        return value.stripTrailingZeros();
    }

    private String required(String value, String field, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw bad("Vui lòng bổ sung " + field.toLowerCase(Locale.ROOT) + ".");
        if (normalized.length() > maxLength) throw bad(field + " chỉ được chứa tối đa " + maxLength + " ký tự.");
        return normalized;
    }

    private String optional(String value, int maxLength, String field) {
        String normalized = clean(value);
        if (normalized != null && normalized.length() > maxLength) {
            throw bad(field + " chỉ được chứa tối đa " + maxLength + " ký tự.");
        }
        return normalized;
    }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private InventoryItemView required(UUID branchId, UUID id) {
        InventoryItemView item = inventoryRepository.get(branchId, id);
        if (item == null) throw notFound();
        return item;
    }

    // BR-177: cơ sở tạm ngừng chỉ được xem dữ liệu; thao tác ghi tồn kho bị từ chối.
    private UUID writableBranch(Identity actor, String action, String entity) {
        UUID branchId = branch(actor, action, entity);
        if (!inventoryRepository.activeBranch(branchId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "Cơ sở " + inventoryRepository.branchName(branchId)
                + " đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi.");
        return branchId;
    }

    private UUID branch(Identity actor) {
        return branch(actor, "xem", "mặt hàng kho");
    }

    private UUID branch(Identity actor, String action, String entity) {
        if (actor == null || !"BRANCH_MANAGER".equals(actor.role()) || actor.branchId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền " + action + " đối với " + entity + ".");
        }
        return actor.branchId();
    }

    private void cleanupIfRolledBack(String objectKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) refundEvidenceStorageService.deleteQuietly(objectKey);
            }
        });
    }

    private ResponseStatusException constraintFailure(DataIntegrityViolationException cause, String action) {
        return new ResponseStatusException(HttpStatus.CONFLICT,
            "Chưa thể xác nhận kết quả " + action + ". Vui lòng kiểm tra lại trạng thái của mặt hàng kho trước khi thực hiện lại.", cause);
    }

    private static String quantityText(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy mặt hàng kho tại cơ sở này.");
    }
}
