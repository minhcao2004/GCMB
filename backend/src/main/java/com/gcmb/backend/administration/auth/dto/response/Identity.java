/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 13/10/2026
 * Description: Thông tin nhận diện tài khoản và cơ sở được phân quyền.
 */

package com.gcmb.backend.administration.auth.dto.response;

import java.util.UUID;

public record Identity(UUID id, String username, String role, String destination, UUID branchId, String branchName) {}
