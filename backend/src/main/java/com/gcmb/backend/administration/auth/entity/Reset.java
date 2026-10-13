package com.gcmb.backend.administration.auth.entity;

import java.time.Instant;
import java.util.UUID;

// Trạng thái mã khôi phục đã dùng hoặc bị thu hồi.
public record Reset(UUID id, String hash, Instant usedAt, Instant revokedAt) {}
