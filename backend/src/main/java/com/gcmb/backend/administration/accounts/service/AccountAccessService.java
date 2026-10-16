/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: Kiểm tra và thực hiện đổi vai trò, phạm vi cơ sở hoặc thu hồi truy cập.
 */
package com.gcmb.backend.administration.accounts.service;

import com.gcmb.backend.administration.accounts.dto.AccountAccessDtos.*;
import com.gcmb.backend.administration.accounts.dto.AccountDtos.AccountView;
import com.gcmb.backend.administration.accounts.repository.AccountAccessRepository;
import com.gcmb.backend.administration.accounts.repository.AccountRepository;
import com.gcmb.backend.administration.branch.service.BranchAdministrationService;
import com.gcmb.backend.administration.auth.exception.AuthFailure;
import com.gcmb.backend.administration.auth.validator.AuthValidation;
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
        if (chain && input.branchId() != null) {
            throw new AuthFailure(400, "branchId", "Vai trò này có phạm vi toàn chuỗi");
        }
        if (!chain && (input.branchId() == null || !accounts.activeBranch(input.branchId()))) {
            throw new AuthFailure(400, "branchId", "Vui lòng chọn cơ sở đang hoạt động");
        }
        if (!"BRANCH_MANAGER".equals(input.role()) && before.role().equals(input.role())
                && Objects.equals(before.branchId(), input.branchId())) {
            return before;
        }
        access.setRole(id, roleId, input.branchId());
        branches.syncAccountManager(id, actor);
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

    @Transactional(readOnly = true)
    public List<PermissionView> permissions(String role) {
        if (accounts.roleId(role) == null) {
            throw new AuthFailure(400, "role", "Vai trò không hợp lệ");
        }
        return access.permissions(role);
    }
}
