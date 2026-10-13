/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 10/10/2026
 * Description: Chứa tên đăng nhập, mật khẩu và lựa chọn duy trì đăng nhập.
 */

package com.gcmb.backend.administration.auth.dto.request;

// Dữ liệu đăng nhập; không tạo toString để tránh lộ mật khẩu vào log.
public class LoginRequest {
    public String username; public String password; public boolean remember;
}
