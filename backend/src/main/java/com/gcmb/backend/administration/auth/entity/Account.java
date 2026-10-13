package com.gcmb.backend.administration.auth.entity;

import java.util.UUID;

// Dữ liệu tài khoản đọc từ JDBC, gồm hash mật khẩu và vai trò.
public record Account(UUID id, String username, String email, String hash, String status, String role) {}
