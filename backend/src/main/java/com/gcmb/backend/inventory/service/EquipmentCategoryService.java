/**
 * Copyright (c) 2026 SEP490-G12.
 * Author: TrongNV
 * Description: Kiểm tra quy tắc nghiệp vụ và phạm vi truy cập UC60–UC64.
 */
package com.gcmb.backend.inventory.service;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.inventory.dto.EquipmentCategoryDtos.Command;
import com.gcmb.backend.inventory.dto.EquipmentCategoryDtos.View;
import com.gcmb.backend.inventory.repository.EquipmentCategoryRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EquipmentCategoryService {
    private final EquipmentCategoryRepository repository;
    // BR-111: Tiêm repository để mọi API danh mục đi qua cùng lớp kiểm tra quy tắc nghiệp vụ.
    public EquipmentCategoryService(EquipmentCategoryRepository repository) { this.repository = repository; }

    // Danh mục nhóm là toàn chuỗi; quản lý cấp cao ghi và quản lý cơ sở chỉ có quyền đọc.
    private UUID requireScope(Identity actor, UUID requestedBranch) {
        if (actor == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập lại");
        if ("BRANCH_MANAGER".equals(actor.role()) && actor.branchId() != null
                && (requestedBranch == null || actor.branchId().equals(requestedBranch))) return null;
        if ("HEAD_OFFICE_MANAGER".equals(actor.role())) return null;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem đối với nhóm thiết bị.");
    }

    // BR-111: Mã và tên là thông tin bắt buộc, không chấp nhận chuỗi trống hoặc chỉ có khoảng trắng.
    private Command validate(Command command) {
        if (command == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng bổ sung thông tin nhóm thiết bị.");
        if (command.code() == null || command.code().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng bổ sung mã nhóm thiết bị.");
        if (command.name() == null || command.name().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng bổ sung tên nhóm thiết bị.");
        if (!command.code().trim().matches("[A-Z0-9]+(?:-[A-Z0-9]+)*"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã nhóm chỉ gồm chữ in hoa, số và dấu gạch ngang; ví dụ EQ-AUDIO");
        if (command.code().trim().length() > 60) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã nhóm thiết bị chỉ được chứa tối đa 60 ký tự.");
        if (command.name().trim().length() > 200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên nhóm thiết bị chỉ được chứa tối đa 200 ký tự.");
        if (command.description() != null && command.description().trim().length() > 500)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mô tả chỉ được chứa tối đa 500 ký tự.");
        command = new Command(command.code().trim(), command.name().trim(), command.description() == null ? "" : command.description().trim());
        return command;
    }

    // UC-61: Chỉ đọc danh mục trong phạm vi cơ sở, không làm thay đổi dữ liệu.
    public List<View> list(Identity actor, UUID branchId, String search, Boolean active) {
        return repository.list(requireScope(actor, branchId), search == null ? "" : search.trim(), active);
    }

    // UC-60: Tạo nhóm hoạt động mặc định; mã không được trùng trong cùng cơ sở.
    public View create(Identity actor, UUID branchId, Command command) {
        if (actor == null || !"HEAD_OFFICE_MANAGER".equals(actor.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền tạo đối với nhóm thiết bị.");
        try { return repository.create(requireScope(actor, branchId), validate(command)); }
        catch (org.springframework.dao.DuplicateKeyException error) { throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Mã nhóm thiết bị “" + command.code().trim() + "” đã tồn tại trong danh mục toàn chuỗi. Vui lòng nhập mã khác.", error); }
        catch (DataIntegrityViolationException error) { throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Chưa thể xác nhận kết quả tạo nhóm thiết bị. Vui lòng kiểm tra lại trạng thái của nhóm thiết bị trước khi thực hiện lại.", error); }
    }

    // UC-62: Lưu thông tin xác nhận và giữ nguyên các tham chiếu lịch sử.
    public View update(Identity actor, UUID branchId, UUID id, Command command) {
        if (actor == null || !"HEAD_OFFICE_MANAGER".equals(actor.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền cập nhật đối với nhóm thiết bị.");
        try {
            View result = repository.update(requireScope(actor, branchId), id, validate(command));
            if (result == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy nhóm thiết bị trong phạm vi cơ sở");
            return result;
        } catch (org.springframework.dao.DuplicateKeyException error) { throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Mã nhóm thiết bị “" + command.code().trim() + "” đã tồn tại trong danh mục toàn chuỗi. Vui lòng nhập mã khác.", error); }
        catch (DataIntegrityViolationException error) { throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Chưa thể xác nhận kết quả cập nhật nhóm thiết bị. Vui lòng kiểm tra lại trạng thái của nhóm thiết bị trước khi thực hiện lại.", error); }
    }

    // UC-63: Chỉ cho phép hai trạng thái hoạt động/ngừng hoạt động và không xóa asset đã liên kết.
    public View setActive(Identity actor, UUID branchId, UUID id, Boolean active) {
        if (actor == null || !"HEAD_OFFICE_MANAGER".equals(actor.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền đổi trạng thái đối với nhóm thiết bị.");
        if (active == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng bổ sung trạng thái nhóm thiết bị.");
        View result = repository.setActive(requireScope(actor, branchId), id, active);
        if (result == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy nhóm thiết bị trong phạm vi cơ sở");
        return result;
    }

    // BR-118/BR-191: Xóa chỉ được thực hiện khi nhóm chưa từng được dùng bởi bất kỳ thiết bị nào.
    public void delete(Identity actor, UUID branchId, UUID id) {
        if (actor == null || !"HEAD_OFFICE_MANAGER".equals(actor.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền xóa đối với nhóm thiết bị.");
        try {
            if (!repository.deleteUnused(requireScope(actor, branchId), id))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể thực hiện cách xóa này vì sẽ làm mất hoặc thay đổi dữ liệu liên quan. Lịch sử nghiệp vụ và các bản ghi liên quan phải được giữ nguyên.");
        } catch (DataIntegrityViolationException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể thực hiện cách xóa này vì sẽ làm mất hoặc thay đổi dữ liệu liên quan. Lịch sử nghiệp vụ và các bản ghi liên quan phải được giữ nguyên.");
        }
    }
}
