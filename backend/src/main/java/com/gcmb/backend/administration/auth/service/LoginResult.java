package com.gcmb.backend.administration.auth.service;

import com.gcmb.backend.administration.auth.dto.response.Identity;

// Kết quả nội bộ gồm danh tính và token chỉ được đưa vào cookie HttpOnly.
public record LoginResult(Identity user, String token, long seconds) {}
