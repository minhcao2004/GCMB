package com.gcmb.backend.administration.auth.entity;

import java.time.Instant;
import java.util.UUID;

public record Reset(UUID id, String hash, Instant usedAt, Instant revokedAt) {}
