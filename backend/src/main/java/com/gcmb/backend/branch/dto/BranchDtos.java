/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 14/10/2026
 * Description: Dữ liệu đầu vào quản lý thông tin và trạng thái cơ sở.
 */
package com.gcmb.backend.branch.dto;

public final class BranchDtos {

    private BranchDtos() {}

    public record BranchCommand(
        String code,
        String name,
        String address,
        String phone,
        String notes
    ) {}

    public record StatusCommand(String status, String reason) {}
}
