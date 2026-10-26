/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: Kiểm tra và thực hiện đổi vai trò, phạm vi cơ sở hoặc thu hồi truy cập.
 */
package com.gcmb.backend.accounts.service;

import com.gcmb.backend.accounts.dto.AccountAccessDtos.*;
import com.gcmb.backend.accounts.dto.AccountDtos.AccountView;
import com.gcmb.backend.accounts.repository.AccountAccessRepository;
import com.gcmb.backend.accounts.repository.AccountRepository;
import com.gcmb.backend.branch.service.BranchAdministrationService;
import com.gcmb.backend.auth.exception.AuthFailure;
import com.gcmb.backend.auth.service.AuthValidation;
import com.gcmb.backend.auth.dto.Identity;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountAccessService {
    private final AccountRepository accounts;
    private final AccountAccessRepository access;
    private final BranchAdministrationService branches;

    public AccountAccessService(AccountRepository accounts, AccountAccessRepository access, BranchAdministrationService branches) {
        this.accounts = accounts;
        this.access = access;
        this.branches = branches;
    }

    private AccountView editable(UUID id, UUID actor) {
        if (id.equals(actor)) {
            throw new AuthFailure(400, "form", "Không thể thay đổi quyền hoặc thu hồi chính tài khoản đang đăng nhập");
        }
        if (!access.lockManagement(actor)) {
            throw new AuthFailure(403, "form", "Bạn không còn quyền quản lý tài khoản");
        }
        if (!access.lockAccount(id)) {
            throw new AuthFailure(404, "form", "Tài khoản không tồn tại");
        }
        AccountView account = accounts.find(id);
        if ("REVOKED".equals(account.status())) {
            throw new AuthFailure(409, "form", "Tài khoản đã bị thu hồi truy cập");
        }
        return account;
    }

    @Transactional
    public AccountView setRole(UUID id, SetRole input, UUID actor) {
        AccountView before = editable(id, actor);
        UUID roleId = input.role() == null ? null : accounts.roleId(input.role());
        if (roleId == null) {
            throw new AuthFailure(400, "role", "Vui lòng chọn vai trò hợp lệ");
        }
        boolean chain = List.of("HEAD_OFFICE_MANAGER", "ACCOUNTANT").contains(input.role());
        if (input.branchIds() != null && !"BRANCH_MANAGER".equals(input.role())) {
            throw new AuthFailure(400, "branchIds", "Chỉ quản lý cơ sở được phân công nhiều cơ sở");
        }
        List<UUID> managed = input.branchIds();
        if (managed != null && (managed.isEmpty() || managed.stream().anyMatch(Objects::isNull)
                || managed.stream().distinct().count() != managed.size())) {
            throw new AuthFailure(400, "branchIds", "Chọn ít nhất một cơ sở, không chọn trùng");
        }
        UUID primaryBranch = managed == null ? input.branchId() : managed.get(0);
        if (chain && input.branchId() != null) {
            throw new AuthFailure(400, "branchId", "Vai trò này có phạm vi toàn chuỗi");
        }
        if (!chain && (primaryBranch == null || !accounts.activeBranch(primaryBranch))) {
            throw new AuthFailure(400, "branchId", "Vui lòng chọn cơ sở đang hoạt động");
        }
        if (managed != null) for (UUID branch : managed) {
            if (!accounts.activeBranch(branch)) throw new AuthFailure(400, "branchIds", "Chỉ chọn các cơ sở đang hoạt động");
        }
        if (!"BRANCH_MANAGER".equals(input.role()) && before.role().equals(input.role())
                && Objects.equals(before.branchId(), input.branchId())) {
            return before;
        }
        access.setRole(id, roleId, primaryBranch);
        if (managed == null) branches.syncAccountManager(id, actor);
        else branches.replaceAccountManagers(id, managed, actor);
        // Buộc đăng nhập lại để phiên cũ không tiếp tục sử dụng phạm vi quyền trước đó.
        access.revokeTokens(id);
        AccountView after = accounts.find(id);
        access.audit(actor, "SET_ACCOUNT_ROLE", before, after, null);
        return after;
    }

    @Transactional
    public AccountView deactivate(UUID id, Deactivate input, UUID actor) {
        String reason = AuthValidation.required(input.reason(), "reason", "lý do thu hồi", 1000, true);
        AccountView before = editable(id, actor);
        access.deactivate(id);
        branches.syncAccountManager(id, actor);
        access.revokeTokens(id);
        AccountView after = accounts.find(id);
        access.audit(actor, "DEACTIVATE_USER_ACCOUNT", before, after, reason);
        return after;
    }

    // UC-07, BR-131, BR-177, BR-183: đổi trạng thái trong đúng phạm vi, giữ hồ sơ và audit.
    @Transactional
    public AccountView changeStatus(UUID id, Deactivate input, Identity actor, boolean activate) {
        if (id.equals(actor.id())) throw new AuthFailure(400, "form", "Không thể thay đổi trạng thái chính tài khoản đang đăng nhập.");
        if ("HEAD_OFFICE_MANAGER".equals(actor.role())) {
            if (!access.lockManagement(actor.id())) throw new AuthFailure(403, "form", "Bạn không có quyền quản lý tài khoản.");
        } else if (!"BRANCH_MANAGER".equals(actor.role()) || actor.branchId() == null
                || !accounts.manages(actor.id(), actor.branchId(), true)) {
            throw new AuthFailure(403, "form", "Bạn không có quyền đổi trạng thái tài khoản đối với cơ sở này.");
        }
        if (!access.lockAccount(id)) throw new AuthFailure(404, "form", "Tài khoản không tồn tại.");
        AccountView before = accounts.find(id);
        if ("BRANCH_MANAGER".equals(actor.role()) && (!"RECEPTIONIST".equals(before.role()) || !actor.branchId().equals(before.branchId())))
            throw new AuthFailure(403, "form", "Bạn chỉ được đổi trạng thái tài khoản Lễ tân thuộc cơ sở đang quản lý.");
        if (activate && "ACTIVE".equals(before.status()) || !activate && "REVOKED".equals(before.status())) return before; // BR-164
        String reason = activate ? null : AuthValidation.required(input == null ? null : input.reason(), "reason", "lý do vô hiệu hóa", 1000, true);
        if (activate) {
            if (!accounts.canReactivate(id)) throw new AuthFailure(409, "form", "Chỉ có thể kích hoạt tài khoản của nhân viên đang hoạt động.");
            if (List.of("RECEPTIONIST", "BRANCH_MANAGER").contains(before.role()) && !accounts.activeBranch(before.branchId()))
                throw new AuthFailure(409, "branchId", "Cơ sở hiện không hoạt động. Vui lòng kiểm tra phân công trước khi kích hoạt.");
            access.reactivate(id);
            if ("BRANCH_MANAGER".equals(before.role())) branches.syncAccountManager(id, actor.id());
        } else access.deactivate(id);
        // Giữ phân công khi tạm vô hiệu hóa để có thể mở lại; auth luôn kiểm tra trạng thái tài khoản.
        access.revokeTokens(id);
        AccountView after = accounts.find(id);
        access.audit(actor.id(), activate ? "REACTIVATE_USER_ACCOUNT" : "DEACTIVATE_USER_ACCOUNT", before, after, reason);
        return after;
    }

    @Transactional(readOnly = true)
    public List<PermissionView> permissions(String role) {
        if (accounts.roleId(role) == null) {
            throw new AuthFailure(400, "role", "Vai trò không hợp lệ");
        }
        return access.permissions(role);
    }
}
