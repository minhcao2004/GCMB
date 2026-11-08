/**
 * Copyright (c) 2026 SEP490-G12.
 * Author: TrongNV
 * Description: REST API danh mục thiết bị cho UC60–UC64, phân tuyến theo vai trò.
 */
package com.gcmb.backend.inventory.controller;

import com.gcmb.backend.audit.service.MutationAudit;
import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.inventory.dto.EquipmentCategoryDtos.Command;
import com.gcmb.backend.inventory.dto.EquipmentCategoryDtos.StatusCommand;
import com.gcmb.backend.inventory.dto.EquipmentCategoryDtos.View;
import com.gcmb.backend.inventory.service.EquipmentCategoryService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class EquipmentCategoryController {
    private final EquipmentCategoryService service;
    private final MutationAudit audit;
    // BR-118: Các lệnh ghi được bao trong audit transaction để lưu lịch sử thay đổi.
    public EquipmentCategoryController(EquipmentCategoryService service, MutationAudit audit) { this.service = service; this.audit = audit; }

    // UC-61: Tra cứu bằng từ khóa và trạng thái trong cơ sở được quyền truy cập.
    @GetMapping({"/api/v1/branch-management/equipment-categories", "/api/v1/head-office/equipment-categories"})
    public List<View> list(@AuthenticationPrincipal Identity actor, @RequestParam(required = false) UUID branchId,
                           @RequestParam(defaultValue = "") String search, @RequestParam(required = false) Boolean active) {
        return service.list(actor, branchId, search, active);
    }

    // UC-60: Tạo nhóm thiết bị mới và ghi lại thao tác trong audit log.
    @PostMapping({"/api/v1/branch-management/equipment-categories", "/api/v1/head-office/equipment-categories"})
    public View create(@AuthenticationPrincipal Identity actor, @RequestParam(required = false) UUID branchId, @RequestBody Command command) {
        return audit.record("CREATE_EQUIPMENT_CATEGORY", "ASSET_CATEGORY", null, () -> service.create(actor, branchId, command), View::id);
    }

    // UC-62: Cập nhật nhóm thiết bị đã chọn trong đúng phạm vi.
    @PutMapping({"/api/v1/branch-management/equipment-categories/{id}", "/api/v1/head-office/equipment-categories/{id}"})
    public View update(@AuthenticationPrincipal Identity actor, @RequestParam(required = false) UUID branchId, @PathVariable UUID id, @RequestBody Command command) {
        return audit.record("UPDATE_EQUIPMENT_CATEGORY", "ASSET_CATEGORY", id, () -> service.update(actor, branchId, id, command), View::id);
    }

    // UC-63: Đổi trạng thái nhưng vẫn giữ mọi thiết bị đang tham chiếu nhóm.
    @PutMapping({"/api/v1/branch-management/equipment-categories/{id}/status", "/api/v1/head-office/equipment-categories/{id}/status"})
    public View status(@AuthenticationPrincipal Identity actor, @RequestParam(required = false) UUID branchId, @PathVariable UUID id, @RequestBody StatusCommand command) {
        return audit.record("CHANGE_EQUIPMENT_CATEGORY_STATUS", "ASSET_CATEGORY", id, () -> service.setActive(actor, branchId, id, command.active()), View::id);
    }

    // UC-64 / BR-118 / BR-191: Ghi audit và chỉ xóa nhóm chưa có thiết bị liên kết.
    @DeleteMapping({"/api/v1/branch-management/equipment-categories/{id}", "/api/v1/head-office/equipment-categories/{id}"})
    public void delete(@AuthenticationPrincipal Identity actor, @RequestParam(required = false) UUID branchId, @PathVariable UUID id) {
        audit.record("DELETE_EQUIPMENT_CATEGORY", "ASSET_CATEGORY", id, () -> { service.delete(actor, branchId, id); return null; }, null);
    }
}
