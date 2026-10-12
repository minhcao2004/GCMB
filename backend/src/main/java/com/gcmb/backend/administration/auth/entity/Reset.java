/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 10/10/2026
 * Description: Biểu diễn dữ liệu đặt lại mật khẩu, gồm mã băm và thời điểm sử dụng hoặc thu hồi.
 */

package com.gcmb.backend.administration.auth.entity;

import java.time.Instant;
import java.util.UUID;

public record Reset(UUID id, String hash, Instant usedAt, Instant revokedAt) {}
