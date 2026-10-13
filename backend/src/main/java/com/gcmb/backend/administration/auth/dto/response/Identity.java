package com.gcmb.backend.administration.auth.dto.response;

import java.util.UUID;

// Danh tính công khai của phiên và trang đích theo vai trò.
public record Identity(UUID id, String username, String role, String destination) {}
