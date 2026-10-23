/**
 * Copyright (c) 2026 SEP490-G12.
 *

 * Author: HuyDV
 * Created: 10/10/2026
 * Description: Chứa thông tin định danh, vai trò và đường dẫn chuyển hướng của người dùng.
 * Author: TrongNV
 * Created: 13/10/2026
 * Description: Thông tin nhận diện tài khoản và cơ sở được phân quyền.

 */

package com.gcmb.backend.auth.dto;

import java.util.UUID;

// Danh tính công khai của phiên, cơ sở được gán và trang đích theo vai trò.
public record Identity(UUID id, String username, String role, String destination,
                       UUID branchId, String branchName) {}
