/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: Kiểm tra dữ liệu tạo tài khoản, hồ sơ nhân viên, vai trò, cơ sở và thông tin trùng; băm mật khẩu và lưu trong giao dịch.
 */

package com.gcmb.backend.administration.accounts.service;

import com.gcmb.backend.administration.accounts.dto.AccountDtos.*;
import com.gcmb.backend.administration.accounts.repository.AccountRepository;
import com.gcmb.backend.administration.auth.exception.AuthFailure;
import com.gcmb.backend.administration.auth.security.AuthCrypto;
import com.gcmb.backend.administration.auth.validator.AuthValidation;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final AccountRepository repository;
    private final AuthCrypto crypto;

    public AccountService(AccountRepository repository, AuthCrypto crypto) {
        this.repository = repository;
        this.crypto = crypto;
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

    @Transactional
    public AccountView create(CreateAccount input, UUID actor) {
        String username = AuthValidation.required(
            input.username(),
            "username",
            "tên đăng nhập",
            100,
            true
        );
        if (!username.matches("[A-Za-z0-9._-]{3,100}")) {
            throw new AuthFailure(400, "username", "Tên đăng nhập gồm 3–100 chữ, số hoặc dấu . _ -");
        }
        String email = AuthValidation.email(input.email());
        AuthValidation.required(input.password(), "password", "mật khẩu", 128, false);
        if (input.employeeId() == null) {
            throw new AuthFailure(400, "employeeId", "Vui lòng chọn hồ sơ nhân viên");
        }
        if (!repository.lockAvailableEmployee(input.employeeId())) {
            throw new AuthFailure(409, "employeeId", "Nhân viên không còn hoạt động hoặc đã có tài khoản");
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
            if (repository.exists(field, field.equals("username") ? username : email)) {
                throw new AuthFailure(409, field, field.equals("username") ? "Tên đăng nhập đã tồn tại" : "Email đã tồn tại");
            }
        }
        try {
            return repository.find(
                repository.insert(
                    input,
                    username,
                    email,
                    crypto.encode(input.password()),
                    role,
                    actor
                )
            );
        } catch (DuplicateKeyException ex) {
            throw new AuthFailure(409, "form", "Tên đăng nhập hoặc email vừa được sử dụng. Vui lòng kiểm tra lại");
        }
    }
}
