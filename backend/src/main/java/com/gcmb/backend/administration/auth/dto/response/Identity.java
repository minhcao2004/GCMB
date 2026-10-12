/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 10/10/2026
 * Description: Chứa thông tin định danh, vai trò và đường dẫn chuyển hướng của người dùng.
 */

package com.gcmb.backend.administration.auth.dto.response;

import java.util.UUID;

public record Identity(UUID id, String username, String role, String destination) {}
