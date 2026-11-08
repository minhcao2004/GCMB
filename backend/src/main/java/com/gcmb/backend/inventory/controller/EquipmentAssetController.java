/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 28/10
 * Description: REST API quản lý tài sản thiết bị theo UC65–UC69.
 */
package com.gcmb.backend.inventory.controller;

import com.gcmb.backend.audit.service.MutationAudit;
import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.inventory.dto.EquipmentAssetDtos.Command;
import com.gcmb.backend.inventory.dto.EquipmentAssetDtos.ImageContent;
import com.gcmb.backend.inventory.dto.EquipmentAssetDtos.Options;
import com.gcmb.backend.inventory.dto.EquipmentAssetDtos.StatusCommand;
import com.gcmb.backend.inventory.dto.EquipmentAssetDtos.View;
import com.gcmb.backend.inventory.service.EquipmentAssetService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class EquipmentAssetController {
    private final EquipmentAssetService service;
    private final MutationAudit audit;

    // BR-118: Các thao tác ghi asset và ảnh được ghi audit cùng giao dịch.
    public EquipmentAssetController(EquipmentAssetService service, MutationAudit audit) {
        this.service = service;
        this.audit = audit;
    }

    // UC-66: Tra cứu và lọc tài sản trong phạm vi được phân quyền.
    @GetMapping({"/api/v1/branch-management/equipment-assets", "/api/v1/head-office/equipment-assets"})
    public List<View> list(@AuthenticationPrincipal Identity actor, @RequestParam(required = false) UUID branchId,
                           @RequestParam(defaultValue = "") String search, @RequestParam(required = false) UUID roomId,
                           @RequestParam(defaultValue = "") String status, @RequestParam(required = false) Boolean active) {
        return service.list(actor, branchId, search, roomId, status, active);
    }

    // UC-65/67: Cung cấp lựa chọn nhóm và phòng hợp lệ theo chi nhánh đang thao tác.
    @GetMapping({"/api/v1/branch-management/equipment-assets/options", "/api/v1/head-office/equipment-assets/options"})
    public Options options(@AuthenticationPrincipal Identity actor, @RequestParam(required = false) UUID branchId,
                           @RequestParam(required = false) UUID assetId) {
        return service.options(actor, branchId, assetId);
    }

    // UC-65/BR-202: Tạo thiết bị với mã, tên, nhóm và vị trí bắt buộc.
    @PostMapping({"/api/v1/branch-management/equipment-assets", "/api/v1/head-office/equipment-assets"})
    public View create(@AuthenticationPrincipal Identity actor, @RequestParam(required = false) UUID branchId,
                       @RequestBody Command command) {
        return audit.record("CREATE_EQUIPMENT_ASSET", "ASSET", null,
            () -> service.create(actor, branchId, command), View::id);
    }

    // UC-67/BR-110: Cập nhật hồ sơ nhưng không cho request thay đổi cơ sở của tài sản.
    @PutMapping({"/api/v1/branch-management/equipment-assets/{id}", "/api/v1/head-office/equipment-assets/{id}"})
    public View update(@AuthenticationPrincipal Identity actor, @RequestParam(required = false) UUID branchId,
                       @PathVariable UUID id, @RequestBody Command command) {
        return audit.record("UPDATE_EQUIPMENT_ASSET", "ASSET", id,
            () -> service.update(actor, branchId, id, command), View::id);
    }

    // UC-68: Bật/ngừng sử dụng tài sản mà không thay đổi tình trạng vận hành hay lịch sử.
    @PutMapping({"/api/v1/branch-management/equipment-assets/{id}/status", "/api/v1/head-office/equipment-assets/{id}/status"})
    public View status(@AuthenticationPrincipal Identity actor, @RequestParam(required = false) UUID branchId,
                       @PathVariable UUID id, @RequestBody StatusCommand command) {
        return audit.record("CHANGE_EQUIPMENT_ASSET_STATUS", "ASSET", id,
            () -> service.setActive(actor, branchId, id, command.active()), View::id);
    }

    // UC-69/BR-118: Xóa chỉ khi thiết bị chưa có sự cố, sửa chữa, điều chuyển hoặc lịch sử thay thế.
    @DeleteMapping({"/api/v1/branch-management/equipment-assets/{id}", "/api/v1/head-office/equipment-assets/{id}"})
    public void delete(@AuthenticationPrincipal Identity actor, @RequestParam(required = false) UUID branchId,
                       @PathVariable UUID id) {
        audit.record("DELETE_EQUIPMENT_ASSET", "ASSET", id, () -> { service.delete(actor, branchId, id); return null; }, null);
    }

    // BR-115/BR-116: Lưu ảnh hợp lệ tối đa 20 MB vào hồ sơ thiết bị và ghi audit.
    @PostMapping(value = {"/api/v1/branch-management/equipment-assets/{id}/image", "/api/v1/head-office/equipment-assets/{id}/image"},
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public View uploadImage(@AuthenticationPrincipal Identity actor, @RequestParam(required = false) UUID branchId,
                            @PathVariable UUID id, @RequestPart("image") MultipartFile image) {
        return audit.record("UPLOAD_EQUIPMENT_ASSET_IMAGE", "ASSET", id,
            () -> service.uploadImage(actor, branchId, id, image), View::id);
    }

    // UC-66: Trả nội dung ảnh/chứng từ mà không công khai object key lưu trữ.
    @GetMapping({"/api/v1/branch-management/equipment-assets/{id}/image", "/api/v1/head-office/equipment-assets/{id}/image"})
    public ResponseEntity<byte[]> image(@AuthenticationPrincipal Identity actor, @RequestParam(required = false) UUID branchId,
                                        @PathVariable UUID id) {
        ImageContent image = service.image(actor, branchId, id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.mimeType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(image.originalName()).build().toString())
            .body(image.bytes());
    }
}
