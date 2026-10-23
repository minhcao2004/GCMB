/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: TrongNV
 * Created: 13/10/2026
 * Description: DTO nhận yêu cầu phân công quản lý cơ sở.
 */

package com.gcmb.backend.branch.dto;
import java.util.UUID;
public record BranchManagerAssignmentCommand(UUID userId) {}
