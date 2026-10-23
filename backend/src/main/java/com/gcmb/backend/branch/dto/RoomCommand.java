/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 13/10/2026
 * Description: DTO nhận dữ liệu tạo và cập nhật phòng.
 */

package com.gcmb.backend.branch.dto;

import java.util.UUID;

public record RoomCommand(String code, String name, Integer floorNumber, UUID roomTypeId,
                          Integer capacity, String description) {}
