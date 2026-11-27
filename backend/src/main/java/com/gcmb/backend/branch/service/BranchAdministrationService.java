/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 14/10/2026
 * Description: Quản lý cơ sở, phân công quản lý và kiểm tra điều kiện ngừng vận hành.
 */
package com.gcmb.backend.branch.service;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.branch.dto.BranchDtos.BranchCommand;
import com.gcmb.backend.branch.dto.BranchDtos.StatusCommand;
import com.gcmb.backend.branch.repository.BranchRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BranchAdministrationService {
    private final BranchRepository branchRepository;

    public BranchAdministrationService(BranchRepository branchRepository) {
        this.branchRepository = branchRepository;
    }

    public List<Map<String, Object>> list() {
        return branchRepository.findAll();
    }

    public Map<String, Object> get(UUID id) {
        var rows = branchRepository.findById(id);
        if (rows.isEmpty()) {
            throw failure(HttpStatus.NOT_FOUND, "Không tìm thấy cơ sở.");
        }
        return rows.get(0);
    }

    @Transactional
    // BR-136: mã cơ sở phải duy nhất trong toàn hệ thống.
    public Map<String, Object> create(BranchCommand input) {
        validate(input);
        String code = input.code().trim().toUpperCase(Locale.ROOT);
        if (branchRepository.codeExists(code, null)) {
            throw failure(HttpStatus.CONFLICT, "Mã cơ sở “" + code + "” đã tồn tại trong hệ thống. Vui lòng nhập mã khác.");
        }
        UUID id = UUID.randomUUID();
        try {
            branchRepository.insert(id, code, input.name().trim(), input.address().trim(),
                clean(input.phone()), clean(input.notes()));
        } catch (DataIntegrityViolationException duplicateCode) {
            throw failure(HttpStatus.CONFLICT, "Mã cơ sở “" + code + "” đã tồn tại trong hệ thống. Vui lòng nhập mã khác.");
        }
        return get(id);
    }

    @Transactional
    // BR-136: cập nhật mã nhưng vẫn phải giữ duy nhất giữa các cơ sở.
    public Map<String, Object> update(UUID id, BranchCommand input) {
        validate(input);
        lock(id);
        String code = input.code().trim().toUpperCase(Locale.ROOT);
        if (branchRepository.codeExists(code, id)) {
            throw failure(HttpStatus.CONFLICT, "Mã cơ sở “" + code + "” đã tồn tại trong hệ thống. Vui lòng nhập mã khác.");
        }
        try {
            branchRepository.update(id, code, input.name().trim(), input.address().trim(),
                clean(input.phone()), clean(input.notes()));
        } catch (DataIntegrityViolationException duplicateCode) {
            throw failure(HttpStatus.CONFLICT, "Mã cơ sở “" + code + "” đã tồn tại trong hệ thống. Vui lòng nhập mã khác.");
        }
        return get(id);
    }

    // Danh sách này dùng cho cả màn kiểm tra và kiểm tra lại trong giao dịch xác nhận.
    public List<Map<String, Object>> blockers(UUID id) {
        get(id);
        return branchRepository.findOpenWork(id);
    }

    @Transactional
    // BR-149: không chuyển trạng thái cơ sở khi còn booking, phiên sử dụng hoặc bill chưa xử lý.
    public Map<String, Object> deactivate(UUID id, StatusCommand input) {
        if (input == null || input.status() == null
                || !List.of("SUSPENDED", "CLOSED").contains(input.status())) {
            throw failure(HttpStatus.BAD_REQUEST, "Chọn Tạm ngừng hoặc Đã đóng.");
        }
        if (clean(input.reason()).isEmpty()) {
            throw failure(HttpStatus.BAD_REQUEST, "Vui lòng bổ sung lý do thay đổi trạng thái.");
        }
        if (clean(input.reason()).length() > 2000) {
            throw failure(HttpStatus.BAD_REQUEST, "Lý do thay đổi trạng thái chỉ được chứa tối đa 2000 ký tự.");
        }
        lock(id);
        String current = (String) get(id).get("status");
        if (current.equals(input.status())) {
            return get(id);
        }
        if (current.equals("CLOSED")) {
            throw failure(HttpStatus.CONFLICT, "Cơ sở đã đóng, không thể chuyển trạng thái này.");
        }
        if (!blockers(id).isEmpty()) {
            String branchName = (String) get(id).get("branchName");
            throw failure(HttpStatus.CONFLICT,
                "Chưa thể ngừng hoạt động cơ sở " + branchName + " vì còn booking chờ nhận phòng, khách đang sử dụng phòng hoặc bill chưa thanh toán. Vui lòng xử lý các mục còn tồn tại.");
        }
        branchRepository.updateStatus(id, input.status(), input.reason().trim());
        return get(id);
    }

    @Transactional
    public Map<String, Object> reactivate(UUID id) {
        lock(id);
        String current = (String) get(id).get("status");
        if ("ACTIVE".equals(current)) return get(id);
        if (!"SUSPENDED".equals(current)) {
            throw failure(HttpStatus.CONFLICT, "Chỉ mở lại cơ sở đang tạm ngừng.");
        }
        branchRepository.reactivate(id);
        return get(id);
    }

    public List<Map<String, Object>> managers() {
        return branchRepository.findManagerCandidates();
    }

    @Transactional
    public Map<String, Object> assign(UUID branchId, UUID userId, Identity actor) {
        return assign(branchId, userId, actor.id(), actor.role());
    }

    private Map<String, Object> assign(UUID branchId, UUID userId, UUID actorId, String actorRole) {
        if (userId == null) {
            throw failure(HttpStatus.BAD_REQUEST, "Vui lòng bổ sung tài khoản quản lý cơ sở.");
        }
        // Dùng cùng khóa với phân quyền tài khoản, tránh gán người vừa bị thu hồi quyền.
        branchRepository.lockManagerAssignments();
        if (!branchRepository.lockActiveManagerAccount(userId)) {
            throw failure(HttpStatus.CONFLICT, "Tài khoản được chọn không đủ điều kiện làm Quản lý cơ sở. Vui lòng chọn tài khoản Quản lý cơ sở khả dụng.");
        }
        lock(branchId);
        var branch = get(branchId);
        if (!"ACTIVE".equals(branch.get("status"))) {
            throw failure(HttpStatus.CONFLICT, "Cơ sở " + branch.get("branchName") + " hiện không hoạt động. Vui lòng chọn cơ sở đang hoạt động để phân công.");
        }
        if (branchRepository.findManagedBranchIds(userId).contains(branchId)) return get(branchId);
        if (branchRepository.countManagers(branchId) >= 2) {
            throw failure(HttpStatus.CONFLICT, "Cơ sở đã có đủ 2 quản lý. Hãy chuyển hoặc thu hồi phân công một quản lý trước.");
        }
        // Thêm phân công, giữ nguyên các cơ sở người này đang quản lý.
        branchRepository.touch(branchId);
        branchRepository.insertManagerAssignment(branchId, userId, actorId);
        branchRepository.setDefaultBranchIfMissing(userId, branchId);
        branchRepository.auditAssignManager(actorId, userId, actorRole, branchId);
        return get(branchId);
    }

    @Transactional
    public void replaceAccountManagers(UUID userId, List<UUID> branchIds, UUID actorId) {
        branchRepository.lockManagerAssignments();
        String actorRole = branchRepository.findRoleCode(actorId);
        for (UUID branchId : branchIds) assign(branchId, userId, actorId, actorRole);
        for (UUID branchId : branchRepository.findManagedBranchIds(userId)) {
            if (branchIds.contains(branchId)) continue;
            branchRepository.auditUnassignManager(actorId, userId, actorRole, branchId);
            branchRepository.deleteManagerAssignment(userId, branchId);
            branchRepository.touch(branchId);
        }
    }

    // Hai màn tài khoản và cơ sở dùng chung một luồng phân công trong cùng giao dịch.
    @Transactional
    public void syncAccountManager(UUID userId, UUID actorId) {
        branchRepository.lockManagerAssignments();
        var account = branchRepository.findAccountRole(userId);
        if ("BRANCH_MANAGER".equals(account.get("code")) && !"REVOKED".equals(account.get("status"))
                && account.get("branch_id") != null) {
            String actorRole = branchRepository.findRoleCode(actorId);
            assign((UUID) account.get("branch_id"), userId, actorId, actorRole);
            return;
        }
        branchRepository.removeAllManagerAssignments(actorId, userId);
    }

    public List<Map<String, Object>> staff(UUID id) {
        get(id);
        return branchRepository.findCurrentStaff(id);
    }

    public List<Map<String, Object>> history(UUID id) {
        get(id);
        return branchRepository.findHistory(id);
    }

    private void lock(UUID id) {
        if (!branchRepository.lockById(id)) {
            throw failure(HttpStatus.NOT_FOUND, "Không tìm thấy cơ sở.");
        }
    }


    // BR-111, BR-135, BR-179–181: kiểm tra trường bắt buộc, định dạng mã và độ dài thông tin cơ sở.
    private static void validate(BranchCommand input) {
        if (input == null) throw failure(HttpStatus.BAD_REQUEST, "Vui lòng bổ sung thông tin cơ sở.");
        if (clean(input.code()).isEmpty()) throw failure(HttpStatus.BAD_REQUEST, "Vui lòng bổ sung mã cơ sở.");
        if (!clean(input.code()).toUpperCase(Locale.ROOT).matches("[A-Z0-9_-]{2,60}")) {
            throw failure(HttpStatus.BAD_REQUEST, "Mã cơ sở phải có từ 2 đến 60 ký tự và chỉ được dùng chữ cái không dấu, chữ số, dấu gạch dưới (_) hoặc dấu gạch nối (-).");
        }
        if (clean(input.name()).isEmpty()) throw failure(HttpStatus.BAD_REQUEST, "Vui lòng bổ sung tên cơ sở.");
        if (clean(input.name()).length() > 200) {
            throw failure(HttpStatus.BAD_REQUEST, "Tên cơ sở chỉ được chứa tối đa 200 ký tự.");
        }
        if (clean(input.address()).isEmpty()) throw failure(HttpStatus.BAD_REQUEST, "Vui lòng bổ sung địa chỉ cơ sở.");
        if (clean(input.address()).length() > 2000) {
            throw failure(HttpStatus.BAD_REQUEST, "Địa chỉ cơ sở chỉ được chứa tối đa 2000 ký tự.");
        }
        com.gcmb.backend.auth.service.AuthValidation.phone(input.phone());
        if (clean(input.notes()).length() > 4000) {
            throw failure(HttpStatus.BAD_REQUEST, "Ghi chú chỉ được chứa tối đa 4000 ký tự.");
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static ResponseStatusException failure(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
