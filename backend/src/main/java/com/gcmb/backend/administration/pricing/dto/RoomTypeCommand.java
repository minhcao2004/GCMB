/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 12/10/2026
 * Description: DTO nhận dữ liệu tạo và cập nhật hạng phòng.
 */

package com.gcmb.backend.administration.pricing.dto;

public record RoomTypeCommand(String code, String name, String serviceType,
                              Integer capacity, String description) {}
