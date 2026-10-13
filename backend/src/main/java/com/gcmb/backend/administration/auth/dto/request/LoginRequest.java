package com.gcmb.backend.administration.auth.dto.request;

// Dữ liệu đăng nhập; không tạo toString để tránh lộ mật khẩu vào log.
public class LoginRequest {
    public String username; public String password; public boolean remember;
}
