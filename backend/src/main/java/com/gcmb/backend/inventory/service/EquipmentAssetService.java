/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 28/10
 * Description: Thực thi quy tắc nghiệp vụ và phạm vi truy cập tài sản thiết bị UC65–UC69.
 */
package com.gcmb.backend.inventory.service;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.inventory.dto.EquipmentAssetDtos.Command;
import com.gcmb.backend.inventory.dto.EquipmentAssetDtos.ImageContent;
import com.gcmb.backend.inventory.dto.EquipmentAssetDtos.Options;
import com.gcmb.backend.inventory.dto.EquipmentAssetDtos.StoredImage;
import com.gcmb.backend.inventory.dto.EquipmentAssetDtos.View;
import com.gcmb.backend.inventory.repository.EquipmentAssetRepository;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EquipmentAssetService {
    private static final Set<String> ASSET_STATUSES = Set.of("IN_USE", "BROKEN", "REPAIRING", "IN_TRANSIT", "RETIRED");
    private final EquipmentAssetRepository equipmentAssetRepository;
    private final EquipmentAssetFileStorageService equipmentAssetFileStorageService;

    // BR-202: Dùng chung repository và file storage cho mọi luồng tài sản.
    public EquipmentAssetService(EquipmentAssetRepository equipmentAssetRepository, EquipmentAssetFileStorageService equipmentAssetFileStorageService) {
        this.equipmentAssetRepository = equipmentAssetRepository;
        this.equipmentAssetFileStorageService = equipmentAssetFileStorageService;
    }

    // BR-202: Quản lý cơ sở bị giới hạn theo branch trong phiên; quản lý cấp cao phải chỉ định branch.
    private UUID scope(Identity actor, UUID requestedBranch) {
        return scope(actor, requestedBranch, "xem");
    }

    private UUID scope(Identity actor, UUID requestedBranch, String action) {
        if (actor == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập lại");
        if ("BRANCH_MANAGER".equals(actor.role())) {
            if (actor.branchId() == null || (requestedBranch != null && !actor.branchId().equals(requestedBranch)))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền " + action + " đối với thiết bị.");
            return actor.branchId();
        }
        if ("HEAD_OFFICE_MANAGER".equals(actor.role()) && requestedBranch != null) return requestedBranch;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền " + action + " đối với thiết bị.");
    }

    // UC-66: Trả danh sách trong phạm vi cơ sở cùng bộ lọc tìm kiếm, vị trí, tình trạng và hoạt động.
    public List<View> list(Identity actor, UUID branchId, String search, UUID roomId, String status, Boolean active) {
        return equipmentAssetRepository.list(scope(actor, branchId), search == null ? "" : search.trim(), roomId,
            status == null ? "" : status, active);
    }

    // UC-65/67: Tải lựa chọn nhóm và phòng phù hợp với cơ sở và thiết bị đang sửa.
    public Options options(Identity actor, UUID branchId, UUID assetId) {
        UUID authorizedBranch = scope(actor, branchId);
        if (assetId != null && equipmentAssetRepository.get(authorizedBranch, assetId) == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thiết bị trong phạm vi cơ sở");
        return equipmentAssetRepository.options(authorizedBranch, assetId);
    }

    // BR-111/BR-202: Bắt buộc mã, tên, nhóm và vị trí; kiểm tra giới hạn, giá mua và tình trạng hợp lệ.
    private Command validate(Command input, UUID branchId, UUID assetId) {
        if (input == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng bổ sung thông tin thiết bị.");
        if (input.code() == null || input.code().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng bổ sung mã thiết bị.");
        if (input.name() == null || input.name().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng bổ sung tên thiết bị.");
        if (input.categoryId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng bổ sung nhóm thiết bị.");
        if (input.roomId() == null && (input.locationNote() == null || input.locationNote().isBlank()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng bổ sung vị trí thiết bị.");
        if (input.code().trim().length() > 60) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã thiết bị chỉ được chứa tối đa 60 ký tự.");
        if (input.name().trim().length() > 200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên thiết bị chỉ được chứa tối đa 200 ký tự.");
        if (input.locationNote() != null && input.locationNote().trim().length() > 200)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vị trí thiết bị chỉ được chứa tối đa 200 ký tự.");
        if (input.serialNumber() != null && input.serialNumber().trim().length() > 200)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Serial chỉ được chứa tối đa 200 ký tự.");
        if (input.purchaseValue() != null && input.purchaseValue().signum() < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giá mua phải lớn hơn hoặc bằng 0.");
        if (input.purchaseDate() != null && input.warrantyExpiry() != null && input.warrantyExpiry().isBefore(input.purchaseDate()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hạn bảo hành không được trước ngày mua");
        if (input.status() == null || !ASSET_STATUSES.contains(input.status()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái thiết bị không hợp lệ");
        if (!equipmentAssetRepository.categoryAvailable(branchId, input.categoryId(), assetId))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chọn nhóm đang hoạt động trong cơ sở");
        if (!equipmentAssetRepository.roomAvailable(branchId, input.roomId(), assetId))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phòng/vị trí phải thuộc cơ sở đang quản lý và còn sử dụng");
        return new Command(input.code().trim(), input.name().trim(), input.categoryId(), input.roomId(), clean(input.locationNote()),
            clean(input.serialNumber()), input.purchaseDate(), input.purchaseValue(), input.warrantyExpiry(), input.status());
    }

    // UC-65/BR-202: Tạo tài sản trong branch đang được quản lý; trạng thái chuyển cơ sở do UC74–UC76 kiểm soát.
    public View create(Identity actor, UUID branchId, Command input) {
        UUID authorizedBranch = scope(actor, branchId, "tạo");
        Command command = validate(input, authorizedBranch, null);
        if (!command.code().matches("ASSET-[A-Z0-9]{2,10}-[0-9]{4,}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã thiết bị theo mẫu ASSET-{MÃ_CƠ_SỞ}-{SỐ_TĂNG_DẦN 4 CHỮ SỐ}, ví dụ ASSET-HN01-0001");
        if ("IN_TRANSIT".equals(command.status()) || "RETIRED".equals(command.status()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiết bị mới phải được tạo trước khi chuyển hoặc ngừng sử dụng");
        try { return equipmentAssetRepository.create(authorizedBranch, command); }
        catch (org.springframework.dao.DuplicateKeyException e) { throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Mã thiết bị “" + command.code() + "” đã tồn tại trong toàn chuỗi. Vui lòng nhập mã khác.", e); }
        catch (DataIntegrityViolationException e) { throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Chưa thể xác nhận kết quả tạo thiết bị. Vui lòng kiểm tra lại trạng thái của thiết bị trước khi thực hiện lại.", e); }
    }

    // UC-67/BR-110: Sửa hồ sơ nhưng không đổi branch; trạng thái IN_TRANSIT không thể bị đổi để bỏ qua xác nhận bàn giao.
    public View update(Identity actor, UUID branchId, UUID id, Command input) {
        UUID authorizedBranch = scope(actor, branchId, "cập nhật");
        View before = equipmentAssetRepository.get(authorizedBranch, id);
        if (before == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thiết bị trong phạm vi cơ sở");
        if (input == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng bổ sung thông tin thiết bị.");
        if ("IN_TRANSIT".equals(before.status()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Thiết bị đang chuyển cơ sở; chỉ UC75–UC76 được cập nhật sau xác nhận bàn giao");
        if ("IN_TRANSIT".equals(input.status()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ quy trình điều chuyển UC74–UC76 được chuyển thiết bị sang trạng thái đang chuyển");
        Command command = validate(input, authorizedBranch, id);
        try { return equipmentAssetRepository.update(authorizedBranch, id, command); }
        catch (org.springframework.dao.DuplicateKeyException e) { throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Mã thiết bị “" + command.code() + "” đã tồn tại trong toàn chuỗi. Vui lòng nhập mã khác.", e); }
        catch (DataIntegrityViolationException e) { throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Chưa thể xác nhận kết quả cập nhật thiết bị. Vui lòng kiểm tra lại trạng thái của thiết bị trước khi thực hiện lại.", e); }
    }

    // UC-68: Đổi hoạt động/ngừng hoạt động tách biệt với tình trạng vận hành và giữ nguyên lịch sử.
    public View setActive(Identity actor, UUID branchId, UUID id, Boolean active) {
        if (active == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng bổ sung trạng thái sử dụng thiết bị.");
        UUID authorizedBranch = scope(actor, branchId, "đổi trạng thái");
        View current = equipmentAssetRepository.get(authorizedBranch, id);
        if (current != null && "IN_TRANSIT".equals(current.status()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không đổi trạng thái sử dụng khi thiết bị đang điều chuyển");
        View result = equipmentAssetRepository.setActive(authorizedBranch, id, active);
        if (result == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thiết bị trong phạm vi cơ sở");
        return result;
    }

    // UC-69/BR-118: Chặn xóa mọi tài sản đã có sự cố, điều chuyển hoặc lịch sử tài sản thay thế.
    public void delete(Identity actor, UUID branchId, UUID id) {
        try {
            if (!equipmentAssetRepository.deleteUnused(scope(actor, branchId, "xóa"), id))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể thực hiện cách xóa này vì sẽ làm mất hoặc thay đổi dữ liệu liên quan. Lịch sử nghiệp vụ và các bản ghi liên quan phải được giữ nguyên.");
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể thực hiện cách xóa này vì sẽ làm mất hoặc thay đổi dữ liệu liên quan. Lịch sử nghiệp vụ và các bản ghi liên quan phải được giữ nguyên.");
        }
    }

    // BR-115/BR-116: Kiểm tra định dạng và dung lượng, lưu file cùng metadata, rồi liên kết vào tài sản.
    @Transactional
    public View uploadImage(Identity actor, UUID branchId, UUID id, MultipartFile image) {
        UUID authorizedBranch = scope(actor, branchId);
        if (equipmentAssetRepository.get(authorizedBranch, id) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thiết bị trong phạm vi cơ sở");
        EquipmentAssetFileStorageService.StoredFile stored = equipmentAssetFileStorageService.store(image, "thiet-bi");
        try {
            equipmentAssetRepository.insertImage(stored, actor.id());
            if (!equipmentAssetRepository.setImage(authorizedBranch, id, stored.id())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thiết bị trong phạm vi cơ sở");
            return equipmentAssetRepository.get(authorizedBranch, id);
        } catch (RuntimeException error) {
            equipmentAssetFileStorageService.deleteQuietly(stored.objectKey());
            throw error;
        }
    }

    // UC-66: Chỉ đọc file đính kèm tài sản thuộc cơ sở được quyền xem.
    public ImageContent image(Identity actor, UUID branchId, UUID id) {
        StoredImage metadata = equipmentAssetRepository.image(scope(actor, branchId), id);
        if (metadata == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Thiết bị chưa có ảnh");
        try { return new ImageContent(metadata.originalName(), metadata.mimeType(), equipmentAssetFileStorageService.read(metadata.objectKey())); }
        catch (IOException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy file ảnh thiết bị"); }
    }

    // BR-111: Chuẩn hóa giá trị text tùy chọn trước khi lưu hồ sơ.
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
