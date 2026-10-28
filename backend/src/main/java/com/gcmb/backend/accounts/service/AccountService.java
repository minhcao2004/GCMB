/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: Kiểm tra dữ liệu tạo tài khoản, hồ sơ nhân viên, vai trò, cơ sở và thông tin trùng; băm mật khẩu và lưu trong giao dịch.
 */

package com.gcmb.backend.accounts.service;

import com.gcmb.backend.accounts.dto.AccountDtos.*;
import com.gcmb.backend.accounts.repository.AccountRepository;
import com.gcmb.backend.branch.service.BranchAdministrationService;
import com.gcmb.backend.auth.exception.AuthFailure;
import com.gcmb.backend.auth.security.AuthCrypto;
import com.gcmb.backend.auth.service.AuthValidation;
import com.gcmb.backend.auth.dto.Identity;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository repository;
    private final AuthCrypto crypto;
    private final BranchAdministrationService branches;

    public AccountService(
        AccountRepository repository,
        AuthCrypto crypto,
        BranchAdministrationService branches
    ) {
        this.repository = repository;
        this.crypto = crypto;
        this.branches = branches;
    }

    @Transactional(readOnly = true)
    public AccountPage list(String q, String role, UUID branch, String status, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || q.length() > 200) {
            throw new AuthFailure(400, "form", "Bộ lọc hoặc phân trang không hợp lệ");
        }
        return repository.list(q.trim(), role, branch, status, page, size);
    }

    @Transactional(readOnly = true)
    public Options options() {
        return repository.options();
    }

    public UUID managerScope(Identity actor, boolean writing) {
        if (
            actor == null ||
            !"BRANCH_MANAGER".equals(actor.role()) ||
            actor.branchId() == null ||
            !repository.manages(actor.id(), actor.branchId(), writing)
        ) throw new AuthFailure(
            403,
            "form",
            "Bạn không có quyền quản lý tài khoản đối với cơ sở này."
        ); // MSG24
        return actor.branchId();
    }

    @Transactional(readOnly = true)
    public AccountPage list(
        String q,
        String role,
        UUID branch,
        String status,
        int page,
        int size,
        Identity actor
    ) {
        if ("HEAD_OFFICE_MANAGER".equals(actor.role())) return list(
            q,
            role,
            branch,
            status,
            page,
            size
        );
        UUID scope = managerScope(actor, false);
        if (branch != null && !scope.equals(branch)) throw new AuthFailure(
            403,
            "form",
            "Bạn không có quyền xem tài khoản đối với cơ sở này."
        );
        if (!role.isEmpty() && !"RECEPTIONIST".equals(role)) return new AccountPage(
            List.of(),
            0,
            page,
            size
        );
        return list(q, "RECEPTIONIST", scope, status, page, size);
    }

    @Transactional(readOnly = true)
    public Options options(Identity actor) {
        return "HEAD_OFFICE_MANAGER".equals(actor.role())
            ? options()
            : repository.branchOptions(managerScope(actor, false));
    }

    @Transactional
    public AccountView create(CreateAccount input, Identity actor) {
        input = fromEmployee(input);
        if (!"HEAD_OFFICE_MANAGER".equals(actor.role())) {
            UUID scope = managerScope(actor, true);
            if (
                input == null ||
                !"RECEPTIONIST".equals(input.role()) ||
                !scope.equals(input.branchId()) ||
                !repository.employeeInBranch(input.employeeId(), scope)
            ) throw new AuthFailure(
                403,
                "form",
                "Bạn chỉ được tạo tài khoản Lễ tân cho nhân viên thuộc cơ sở đang quản lý."
            );
        }
        return create(input, actor.id());
    }

    // BR-111/112/113/114/129/132/133/134/164; MSG34–40.
    @Transactional
    public AccountView create(CreateAccount input, UUID actor) {
        input = fromEmployee(input);
        String username = AuthValidation.required(
            input.username(),
            "username",
            "tên đăng nhập",
            100,
            true
        );
        if (!username.matches("[A-Za-z0-9._-]{3,100}")) {
            throw new AuthFailure(
                400,
                "username",
                "Tên đăng nhập phải có từ 3 đến 100 ký tự và chỉ được dùng chữ cái không dấu, chữ số, dấu chấm (.), dấu gạch dưới (_) hoặc dấu gạch nối (-)."
            );
        }
        String email = AuthValidation.email(input.email());
        // BR-114; dùng cùng chính sách với đổi và đặt lại mật khẩu.
        AuthValidation.passwordStrength(input.password());
        if (input.employeeId() == null) {
            throw new AuthFailure(400, "employeeId", "Vui lòng chọn hồ sơ nhân viên");
        }
        if (!repository.lockAvailableEmployee(input.employeeId())) {
            throw new AuthFailure(
                409,
                "employeeId",
                "Chỉ có thể tạo tài khoản cho nhân viên đang hoạt động và chưa có tài khoản. Vui lòng chọn nhân viên khác."
            );
        }
        UUID role = input.role() == null ? null : repository.roleId(input.role());
        if (role == null) {
            throw new AuthFailure(400, "role", "Vui lòng chọn vai trò hợp lệ");
        }
        boolean chain = List.of("HEAD_OFFICE_MANAGER", "ACCOUNTANT").contains(input.role());
        if (chain && input.branchId() != null) {
            throw new AuthFailure(400, "branchId", "Vai trò này có phạm vi toàn chuỗi");
        }
        if (!chain && (input.branchId() == null || !repository.activeBranch(input.branchId()))) {
            throw new AuthFailure(400, "branchId", "Vui lòng chọn cơ sở đang hoạt động");
        }
        for (String field : List.of("username", "email")) {
            if (
                field.equals("email")
                    ? repository.emailReserved(email, input.employeeId(), null)
                    : repository.exists(field, username)
            ) {
                throw new AuthFailure(
                    409,
                    field,
                    field.equals("username")
                        ? "Tên đăng nhập này đã được sử dụng. Vui lòng chọn tên khác."
                        : "Email này đã được sử dụng cho hồ sơ nhân viên hoặc tài khoản khác. Vui lòng cập nhật email trong hồ sơ nhân viên trước khi tạo tài khoản."
                );
            }
        }
        try {
            UUID id = repository.insert(
                input,
                username,
                email,
                crypto.encode(input.password()),
                role,
                actor
            );
            if ("BRANCH_MANAGER".equals(input.role())) branches.syncAccountManager(id, actor);
            return repository.find(id);
        } catch (DuplicateKeyException ex) {
            throw new AuthFailure(
                409,
                "form",
                "Tên đăng nhập hoặc email vừa được sử dụng. Vui lòng kiểm tra lại"
            );
        }
    }

    // UC-04, BR-133: chỉ chọn nhân viên; vai trò, email và cơ sở được đọc lại dưới khóa giao dịch.
    private CreateAccount fromEmployee(CreateAccount input) {
        if (input == null) throw new AuthFailure(
            400,
            "form",
            "Vui lòng bổ sung thông tin tài khoản."
        );
        String username = AuthValidation.required(
            input.username(),
            "username",
            "tên đăng nhập",
            100,
            true
        );
        if (!username.matches("[A-Za-z0-9._-]{3,100}")) throw new AuthFailure(
            400,
            "username",
            "Tên đăng nhập phải có từ 3 đến 100 ký tự và chỉ được dùng chữ cái không dấu, chữ số, dấu chấm (.), dấu gạch dưới (_) hoặc dấu gạch nối (-)."
        );
        AuthValidation.passwordStrength(input.password());
        if (input.employeeId() == null) throw new AuthFailure(
            400,
            "employeeId",
            "Vui lòng bổ sung hồ sơ nhân viên."
        ); // MSG02
        if (!repository.lockAvailableEmployee(input.employeeId())) throw new AuthFailure(
            409,
            "employeeId",
            "Chỉ có thể tạo tài khoản cho nhân viên đang hoạt động và chưa có tài khoản. Vui lòng chọn nhân viên khác."
        ); // MSG39
        var profile = repository.employeeProfile(input.employeeId());
        if (profile == null) throw new AuthFailure(
            409,
            "employeeId",
            "Không tìm thấy thông tin cấp tài khoản trong hồ sơ. Vui lòng tải lại hồ sơ nhân viên."
        );
        if (
            profile.role() == null ||
            !List.of(
                "HEAD_OFFICE_MANAGER",
                "BRANCH_MANAGER",
                "RECEPTIONIST",
                "ACCOUNTANT"
            ).contains(profile.role())
        ) throw new AuthFailure(
            400,
            "employeeId",
            "Hồ sơ nhân viên chưa có vai trò được cấp tài khoản. Vui lòng cập nhật hồ sơ trước."
        );
        if (profile.email() == null || profile.email().isBlank()) throw new AuthFailure(
            400,
            "employeeId",
            "Hồ sơ nhân viên chưa có email. Vui lòng cập nhật hồ sơ trước."
        );
        boolean chain = List.of("HEAD_OFFICE_MANAGER", "ACCOUNTANT").contains(profile.role());
        if (!chain && profile.branchId() == null) throw new AuthFailure(
            400,
            "employeeId",
            "Nhân viên chưa được gán cơ sở. Vui lòng cập nhật phân công trước."
        );
        return new CreateAccount(
            input.employeeId(),
            input.username(),
            input.password(),
            profile.email(),
            profile.role(),
            chain ? null : profile.branchId()
        );
    }
}
