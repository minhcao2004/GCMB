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
    private final MenuItemRepository repository;
    private final RefundEvidenceStorageService imageStorage;

    public MenuItemService(MenuItemRepository repository, RefundEvidenceStorageService imageStorage) {
        this.repository = repository;
        this.imageStorage = imageStorage;
    }

    @Transactional(readOnly = true)
    public List<MenuItemView> list(Identity actor) {
        return repository.list(branch(actor));
    }

    @Transactional(readOnly = true)
    public List<String> categories(Identity actor) {
        return repository.categories(branch(actor));
    }

    @Transactional(readOnly = true)
    public List<InventoryOption> inventoryOptions(Identity actor) {
        return repository.inventoryOptions(branch(actor));
    }

    @Transactional(readOnly = true)
    public List<PublicMenuItemView> publicList(UUID branchId, String search, String category) {
        return repository.publicList(branchId, search, category);
    }

    @Transactional
    public MenuItemView create(Identity actor, MenuItemCommand input, MultipartFile image) {
        UUID branchId = writableBranch(actor);
        MenuItemCommand command = validate(branchId, input, null);
        StoredImage stored = store(image);
        try {
            MenuItemView created = repository.create(branchId, command, stored);
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
        UUID branchId = writableBranch(actor);
        MenuItemView existing = repository.get(branchId, id);
        if (existing == null) throw notFound();
        MenuItemCommand command = validate(branchId, input, existing.inventoryItemId());
        StoredImage stored = store(image);
        boolean removeImage = stored == null && Boolean.TRUE.equals(command.removeImage());
        ImageAsset oldImage = repository.image(branchId, id, false);
        try {
            MenuItemView updated = repository.update(branchId, id, command, stored, removeImage);
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
        MenuItemView updated = repository.changeStatus(writableBranch(actor), id, command.active());
        if (updated == null) throw notFound();
        return updated;
    }

    @Transactional
    public void delete(Identity actor, UUID id) {
        UUID branchId = writableBranch(actor);
        if (!repository.softDelete(branchId, id, actor.id())) throw notFound();
    }

    @Transactional(readOnly = true)
    public ImageContent image(Identity actor, UUID id) {
        return readImage(repository.image(branch(actor), id, false));
    }

    @Transactional(readOnly = true)
    public ImageContent publicImage(UUID branchId, UUID id) {
        return readImage(repository.publicImage(branchId, id));
    }

    private ImageContent readImage(ImageAsset image) {
        if (image == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh món.");
        try {
            return new ImageContent(image.originalName(), image.mimeType(), imageStorage.read(image.objectKey()));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tệp ảnh món không còn khả dụng.", exception);
        }
    }

    private MenuItemCommand validate(UUID branchId, MenuItemCommand input, UUID existingInventoryId) {
        if (input == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng bổ sung thông tin món.");
        String name = required(input.name(), "Tên món", 200);
        String category = required(input.category(), "Danh mục", 200);
        String kind = input.kind() == null ? "" : input.kind().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("PACKAGED", "PREPARED", "SERVICE").contains(kind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn loại món hợp lệ.");
        }
        if (input.salePrice() == null || input.salePrice().signum() < 0
                || input.salePrice().stripTrailingZeros().scale() > 0
                || input.salePrice().precision() - input.salePrice().scale() > 18) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giá bán phải là số nguyên VND không âm và tối đa 18 chữ số.");
        }
        if (input.active() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn tình trạng bán.");
        UUID inventoryId = input.inventoryItemId();
        // BR-39: món chế biến và dịch vụ không liên kết thẳng với tồn kho nguyên liệu; chỉ hàng đóng gói mới gắn mặt hàng kho.
        if ("PACKAGED".equals(kind)) {
            if (inventoryId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hàng hóa cần liên kết với một mặt hàng kho.");
            if (!repository.activeInventory(branchId, inventoryId) && !Objects.equals(existingInventoryId, inventoryId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mặt hàng kho không tồn tại, đã ngừng dùng hoặc ngoài cơ sở được phân quyền.");
            }
        } else if (inventoryId != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ hàng hóa đóng gói mới được liên kết trực tiếp với mặt hàng kho.");
        }
        String description = clean(input.description());
        if (description != null && description.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mô tả không được vượt quá 1.000 ký tự.");
        }
        return new MenuItemCommand(name, category, kind, inventoryId, input.salePrice(), input.active(),
            description, input.removeImage());
    }

    private String required(String value, String field, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " không được để trống.");
        if (normalized.length() > maxLength) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " không được vượt quá " + maxLength + " ký tự.");
        return normalized;
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private StoredImage store(MultipartFile image) {
        if (image == null) return null;
        StoredEvidence stored = imageStorage.store(image);
        return new StoredImage(stored.objectKey(), stored.originalName(), stored.mimeType());
    }

    private void cleanup(StoredImage image) {
        if (image != null) imageStorage.deleteQuietly(image.objectKey());
    }

    private void registerImageFinalizer(String newObjectKey, String oldObjectKey) {
        if (Objects.equals(newObjectKey, oldObjectKey)) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            if (oldObjectKey != null) imageStorage.deleteQuietly(oldObjectKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    if (oldObjectKey != null) imageStorage.deleteQuietly(oldObjectKey);
                } else if (newObjectKey != null) {
                    imageStorage.deleteQuietly(newObjectKey);
                }
            }
        });
    }

    private RuntimeException conflict(DataIntegrityViolationException cause) {
        return new ResponseStatusException(HttpStatus.CONFLICT,
            "Mã món đã tồn tại hoặc liên kết kho không còn hợp lệ. Vui lòng tải lại và thử lại.", cause);
    }

    private UUID writableBranch(Identity actor) {
        UUID branchId = branch(actor);
        if (!repository.activeBranch(branchId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cơ sở đang tạm ngưng; không thể thay đổi thực đơn.");
        }
        return branchId;
    }

    private UUID branch(Identity actor) {
        if (actor == null || !"BRANCH_MANAGER".equals(actor.role()) || actor.branchId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản chưa được phân quyền quản lý cơ sở.");
        }
        return actor.branchId();
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy món trong cơ sở được phân quyền.");
    }

    public record ImageContent(String originalName, String mimeType, byte[] bytes) {}
}
