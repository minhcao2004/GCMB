package com.gcmb.backend.inventory.service;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.booking.service.RefundEvidenceStorageService;
import com.gcmb.backend.booking.service.RefundEvidenceStorageService.StoredEvidence;
import com.gcmb.backend.inventory.dto.MenuItemDtos.ImageAsset;
import com.gcmb.backend.inventory.dto.MenuItemDtos.InventoryOption;
import com.gcmb.backend.inventory.dto.MenuItemDtos.MenuItemCommand;
import com.gcmb.backend.inventory.dto.MenuItemDtos.MenuItemStatusCommand;
import com.gcmb.backend.inventory.dto.MenuItemDtos.MenuItemView;
import com.gcmb.backend.inventory.dto.MenuItemDtos.PublicMenuItemView;
import com.gcmb.backend.inventory.dto.MenuItemDtos.StoredImage;
import com.gcmb.backend.inventory.repository.MenuItemRepository;
import java.io.IOException;
import java.util.Locale;
import java.util.List;
import java.util.Objects;
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
public class MenuItemService {
    private final MenuItemRepository menuItemRepository;
    private final RefundEvidenceStorageService refundEvidenceStorageService;

    public MenuItemService(MenuItemRepository menuItemRepository, RefundEvidenceStorageService refundEvidenceStorageService) {
        this.menuItemRepository = menuItemRepository;
        this.refundEvidenceStorageService = refundEvidenceStorageService;
    }

    @Transactional(readOnly = true)
    public List<MenuItemView> list(Identity actor) {
        return menuItemRepository.list(branch(actor));
    }

    @Transactional(readOnly = true)
    public List<String> categories(Identity actor) {
        return menuItemRepository.categories(branch(actor));
    }

    @Transactional(readOnly = true)
    public List<InventoryOption> inventoryOptions(Identity actor) {
        return menuItemRepository.inventoryOptions(branch(actor));
    }

    @Transactional(readOnly = true)
    public List<PublicMenuItemView> publicList(UUID branchId, String search, String category) {
        return menuItemRepository.publicList(branchId, search, category);
    }

    @Transactional
    public MenuItemView create(Identity actor, MenuItemCommand input, MultipartFile image) {
        UUID branchId = writableBranch(actor, "tạo món");
        MenuItemCommand command = validate(branchId, input, null);
        StoredImage stored = store(image);
        try {
            MenuItemView created = menuItemRepository.create(branchId, command, stored);
            registerImageFinalizer(stored == null ? null : stored.objectKey(), null);
            return created;
        } catch (DataIntegrityViolationException exception) {
            cleanup(stored);
            throw conflict(exception);
        } catch (RuntimeException exception) {
            cleanup(stored);
            throw exception;
        }
    }

    @Transactional
    public MenuItemView update(Identity actor, UUID id, MenuItemCommand input, MultipartFile image) {
        UUID branchId = writableBranch(actor, "cập nhật món");
        MenuItemView existing = menuItemRepository.get(branchId, id);
        if (existing == null) throw notFound();
        MenuItemCommand command = validate(branchId, input, existing.inventoryItemId());
        StoredImage stored = store(image);
        boolean removeImage = stored == null && Boolean.TRUE.equals(command.removeImage());
        ImageAsset oldImage = menuItemRepository.image(branchId, id, false);
        try {
            MenuItemView updated = menuItemRepository.update(branchId, id, command, stored, removeImage);
            if (updated == null) throw notFound();
            if (stored != null || removeImage) {
                registerImageFinalizer(stored == null ? null : stored.objectKey(), oldImage == null ? null : oldImage.objectKey());
            }
            return updated;
        } catch (DataIntegrityViolationException exception) {
            cleanup(stored);
            throw conflict(exception);
        } catch (RuntimeException exception) {
            cleanup(stored);
            throw exception;
        }
    }

    @Transactional
    public MenuItemView changeStatus(Identity actor, UUID id, MenuItemStatusCommand command) {
        if (command == null || command.active() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn trạng thái bán hợp lệ.");
        }
        MenuItemView updated = menuItemRepository.changeStatus(writableBranch(actor, "đổi trạng thái món"), id, command.active());
        if (updated == null) throw notFound();
        return updated;
    }

    @Transactional
    public void delete(Identity actor, UUID id) {
        UUID branchId = writableBranch(actor, "xóa món");
        if (!menuItemRepository.softDelete(branchId, id, actor.id())) throw notFound();
    }

    @Transactional(readOnly = true)
    public ImageContent image(Identity actor, UUID id) {
        return readImage(menuItemRepository.image(branch(actor), id, false));
    }

    @Transactional(readOnly = true)
    public ImageContent publicImage(UUID branchId, UUID id) {
        return readImage(menuItemRepository.publicImage(branchId, id));
    }

    private ImageContent readImage(ImageAsset image) {
        if (image == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh món.");
        try {
            return new ImageContent(image.originalName(), image.mimeType(), refundEvidenceStorageService.read(image.objectKey()));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tệp ảnh món không còn khả dụng.", exception);
        }
    }

    // BR-201: món trong thực đơn phải có tên và giá bán.
    private MenuItemCommand validate(UUID branchId, MenuItemCommand input, UUID existingInventoryId) {
        if (input == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng bổ sung thông tin món.");
        String name = required(input.name(), "Tên món", 200);
        String requestedCategory = required(input.category(), "Danh mục", 200);
        // Dùng lại cách viết đang có tại cơ sở để tránh tạo nhóm chỉ khác chữ hoa/thường.
        String category = menuItemRepository.categories(branchId).stream()
            .filter(existing -> existing.equalsIgnoreCase(requestedCategory))
            .findFirst().orElse(requestedCategory);
        String kind = input.kind() == null ? "" : input.kind().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("PACKAGED", "PREPARED", "SERVICE").contains(kind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn loại món hợp lệ.");
        }
        if (input.salePrice() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng bổ sung giá bán.");
        if (input.salePrice().signum() < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giá bán phải lớn hơn hoặc bằng 0.");
        if (input.salePrice().stripTrailingZeros().scale() > 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "Giá bán phải là số tiền nguyên đồng, không có phần thập phân.");
        if (input.salePrice().precision() - input.salePrice().scale() > 18) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giá bán phải là số nguyên VND không âm và tối đa 18 chữ số.");
        }
        if (input.active() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn tình trạng bán.");
        UUID inventoryId = input.inventoryItemId();
        // BR-39: món chế biến không tự trừ tồn kho nguyên liệu.
        if ("PACKAGED".equals(kind)) {
            // BR-111, MSG02: hàng hóa phải có mặt hàng kho liên kết.
            if (inventoryId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng bổ sung mặt hàng kho.");
            if (!menuItemRepository.activeInventory(branchId, inventoryId) && !Objects.equals(existingInventoryId, inventoryId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mặt hàng kho không tồn tại, đã ngừng dùng hoặc ngoài cơ sở được phân quyền.");
            }
        } else if (inventoryId != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ hàng hóa đóng gói mới được liên kết trực tiếp với mặt hàng kho.");
        }
        String description = clean(input.description());
        if (description != null && description.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mô tả chỉ được chứa tối đa 1000 ký tự.");
        }
        return new MenuItemCommand(name, category, kind, inventoryId, input.salePrice(), input.active(),
            description, input.removeImage());
    }

    private String required(String value, String field, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng bổ sung " + field.toLowerCase(Locale.ROOT) + ".");
        if (normalized.length() > maxLength) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " chỉ được chứa tối đa " + maxLength + " ký tự.");
        return normalized;
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private StoredImage store(MultipartFile image) {
        if (image == null) return null;
        StoredEvidence stored = refundEvidenceStorageService.storeMenuImage(image);
        return new StoredImage(stored.objectKey(), stored.originalName(), stored.mimeType());
    }

    private void cleanup(StoredImage image) {
        if (image != null) refundEvidenceStorageService.deleteQuietly(image.objectKey());
    }

    private void registerImageFinalizer(String newObjectKey, String oldObjectKey) {
        if (Objects.equals(newObjectKey, oldObjectKey)) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            if (oldObjectKey != null) refundEvidenceStorageService.deleteQuietly(oldObjectKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    if (oldObjectKey != null) refundEvidenceStorageService.deleteQuietly(oldObjectKey);
                } else if (newObjectKey != null) {
                    refundEvidenceStorageService.deleteQuietly(newObjectKey);
                }
            }
        });
    }

    private RuntimeException conflict(DataIntegrityViolationException cause) {
        return new ResponseStatusException(HttpStatus.CONFLICT,
            "Mã món đã tồn tại hoặc liên kết kho không còn hợp lệ. Vui lòng tải lại và thử lại.", cause);
    }

    private UUID writableBranch(Identity actor, String action) {
        UUID branchId = branch(actor, action);
        if (!menuItemRepository.activeBranch(branchId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cơ sở " + menuItemRepository.branchName(branchId)
                + " đang tạm ngừng hoạt động. Bạn có thể xem dữ liệu nhưng không thể thực hiện thay đổi.");
        }
        return branchId;
    }

    private UUID branch(Identity actor) {
        return branch(actor, "xem");
    }

    private UUID branch(Identity actor, String action) {
        if (actor == null || !"BRANCH_MANAGER".equals(actor.role()) || actor.branchId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền " + action + " đối với thực đơn.");
        }
        return actor.branchId();
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy món trong cơ sở được phân quyền.");
    }

    public record ImageContent(String originalName, String mimeType, byte[] bytes) {}
}
