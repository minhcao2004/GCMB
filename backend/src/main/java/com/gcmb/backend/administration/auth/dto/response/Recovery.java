package com.gcmb.backend.administration.auth.dto.response;

// Challenge gắn với email dùng cho bước xác nhận đặt lại mật khẩu.
public record Recovery(String challenge, String email) {}
