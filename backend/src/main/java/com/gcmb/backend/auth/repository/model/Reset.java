/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 10/10/2026
 * Description: Biểu diễn dữ liệu đặt lại mật khẩu, gồm mã băm và thời điểm sử dụng hoặc thu hồi.
 */

package com.gcmb.backend.auth.repository.model;

import java.time.Instant;
import java.util.UUID;

// Trạng thái mã khôi phục đã dùng hoặc bị thu hồi.
public record Reset(UUID id, String hash, Instant usedAt, Instant revokedAt) {}
