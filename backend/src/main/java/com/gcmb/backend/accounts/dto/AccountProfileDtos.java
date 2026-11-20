/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 12/10/2026
 * Description: Dữ liệu xem và cập nhật thông tin tài khoản đang đăng nhập.
 */
package com.gcmb.backend.accounts.dto;

import com.gcmb.backend.accounts.dto.AccountDtos.AccountView;

public final class AccountProfileDtos {

    private AccountProfileDtos() {}

    public record Profile(
        AccountView account,
        String fullName,
        String phone,
        boolean linkedEmployee,
        String bankName,
        String bankAccount,
        String bankHolder
    ) {
        public Profile(AccountView account, String fullName, String phone, boolean linkedEmployee) {
            this(account, fullName, phone, linkedEmployee, null, null, null);
        }
    }

    // Không nhận ID, vai trò hoặc cơ sở từ phía trình duyệt để tránh sửa tài khoản khác.
    public record UpdateProfile(
        String fullName,
        String phone,
        String email,
        String currentPassword,
        String bankName,
        String bankAccount,
        String bankHolder
    ) {
        public UpdateProfile(String fullName, String phone, String email, String currentPassword) {
            this(fullName, phone, email, currentPassword, null, null, null);
        }
    }
}
