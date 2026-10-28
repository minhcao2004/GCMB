/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 10/10/2026
 * Description: Chứa email, mã yêu cầu khôi phục, mã xác nhận và mật khẩu mới cần xác nhận.
 */

package com.gcmb.backend.auth.dto;

// Dữ liệu xác nhận mã và mật khẩu mới; tránh in bí mật vào log.
public class ResetRequest {

    public String email;
    public String challenge;
    public String code;
    public String password;
    public String confirmation;
}
