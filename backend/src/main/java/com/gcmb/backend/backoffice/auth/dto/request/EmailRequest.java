/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 10/10/2026
 * Description: Chứa email được gửi lên trong yêu cầu khôi phục mật khẩu.
 */

package com.gcmb.backend.administration.auth.dto.request;

// Email gửi mã khôi phục; không đưa dữ liệu riêng tư vào toString.
public class EmailRequest {
    public String email;
}
