package com.gcmb.backend.administration.auth.dto.request;

// Dữ liệu xác nhận mã và mật khẩu mới; tránh in bí mật vào log.
public class ResetRequest {
    public String email; public String challenge; public String code; public String password; public String confirmation;
}
