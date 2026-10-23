/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 10/10/2026
 * Description: Chứa mã yêu cầu khôi phục và email sau khi gửi mã xác nhận.
 */

package com.gcmb.backend.administration.auth.dto.response;

// Challenge gắn với email dùng cho bước xác nhận đặt lại mật khẩu.
public record Recovery(String challenge, String email) {}
