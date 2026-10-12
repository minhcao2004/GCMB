/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 10/10/2026
 * Description: Chứa kết quả đăng nhập nội bộ, gồm người dùng, token phiên và thời gian lưu cookie.
 */

package com.gcmb.backend.administration.auth.service;

import com.gcmb.backend.administration.auth.dto.response.Identity;

// Internal result: the session token must only be sent through an HttpOnly cookie.
public record LoginResult(Identity user, String token, long seconds) {}
