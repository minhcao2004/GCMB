/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 13/10/2026
 * Description: Thực thể tài khoản quản lý và liên kết cơ sở.
 */

package com.gcmb.backend.administration.auth.entity;

import java.util.UUID;

public record Account(UUID id, String username, String email, String hash, String status, String role,
                      UUID branchId, String branchName) {}
