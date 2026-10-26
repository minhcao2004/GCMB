/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 10/10/2026
 * Description: Chứa kết quả đăng nhập nội bộ, gồm người dùng, token phiên và thời gian lưu cookie.
 */

package com.gcmb.backend.auth.dto;

import com.gcmb.backend.auth.dto.Identity;

// Kết quả nội bộ gồm danh tính và token chỉ được đưa vào cookie HttpOnly.
public record LoginResult(Identity user, String token, long seconds) {}
