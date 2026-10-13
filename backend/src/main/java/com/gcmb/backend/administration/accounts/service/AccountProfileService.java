/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: Kiểm tra dữ liệu cá nhân và xác nhận mật khẩu khi đổi email khôi phục.
 */
package com.gcmb.backend.administration.accounts.service;

import com.gcmb.backend.administration.accounts.dto.AccountProfileDtos.*;
import com.gcmb.backend.administration.accounts.repository.AccountProfileRepository;
import com.gcmb.backend.administration.auth.exception.AuthFailure;
import com.gcmb.backend.administration.auth.security.AuthCrypto;
import com.gcmb.backend.administration.auth.validator.AuthValidation;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountProfileService {
    private final AccountProfileRepository repository;
    private final AuthCrypto crypto;

    public AccountProfileService(AccountProfileRepository repository, AuthCrypto crypto) {
        this.repository = repository;
        this.crypto = crypto;
    }

    @Transactional(readOnly = true)
    public Profile view(UUID actor) {
        try {
            Profile profile = repository.find(actor);
            if (!"ACTIVE".equals(profile.account().status())) {
                throw new AuthFailure(403, "form", "Tài khoản không còn hoạt động");
            }
            return profile;
        } catch (EmptyResultDataAccessException exception) {
            throw new AuthFailure(404, "form", "Tài khoản không tồn tại");
        }
    }

    @Transactional
    public Profile update(UUID actor, UpdateProfile input) {
        String email = AuthValidation.email(input.email());
        String hash = repository.lockActiveAccount(actor);
        if (hash == null) {
            throw new AuthFailure(403, "form", "Tài khoản không còn hoạt động");
        }
        Profile before = repository.find(actor);
        String fullName = null;
        String phone = null;
        if (before.linkedEmployee()) {
            fullName = AuthValidation.required(input.fullName(), "fullName", "họ tên", 200, true);
            phone = input.phone() == null || input.phone().isBlank() ? null : input.phone().trim();
            if (phone != null && (!phone.matches("\\+?[0-9 ()-]{7,30}")
                    || phone.replaceAll("[^0-9]", "").length() < 7)) {
                throw new AuthFailure(400, "phone", "Số điện thoại cần 7–30 ký tự, gồm số và dấu + ( ) -");
            }
        } else if ((input.fullName() != null && !input.fullName().isBlank())
                || (input.phone() != null && !input.phone().isBlank())) {
            throw new AuthFailure(409, "form", "Tài khoản chưa liên kết hồ sơ nhân viên");
        }
        boolean emailChanged = !email.equals(before.account().email());
        if (emailChanged) {
            String password = AuthValidation.required(
                    input.currentPassword(), "currentPassword", "mật khẩu hiện tại", 128, false
            );
            if (!crypto.matches(password, hash)) {
                throw new AuthFailure(400, "currentPassword", "Mật khẩu hiện tại không đúng");
            }
            if (repository.emailTaken(actor, email)) {
                throw new AuthFailure(409, "email", "Email đã được sử dụng bởi tài khoản khác");
            }
        }
        if (!emailChanged && Objects.equals(fullName, before.fullName()) && Objects.equals(phone, before.phone())) {
            return before;
        }
        try {
            repository.update(actor, fullName, phone, email, emailChanged);
        } catch (DuplicateKeyException exception) {
            throw new AuthFailure(409, "email", "Email vừa được sử dụng bởi tài khoản khác");
        }
        Profile after = repository.find(actor);
        repository.audit(before, after);
        return after;
    }
}
