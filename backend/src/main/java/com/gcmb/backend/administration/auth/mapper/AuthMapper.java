/**
 * Copyright (c) 2026 SEP490-G12.
 *

 * Author: HuyDV
 * Created: 10/10/2026
 * Description: Ánh xạ kết quả truy vấn thành dữ liệu xác thực và xác định đường dẫn theo vai trò.


 */

package com.gcmb.backend.administration.auth.mapper;

import com.gcmb.backend.administration.auth.dto.response.Identity;
import com.gcmb.backend.administration.auth.entity.Account;
import com.gcmb.backend.administration.auth.entity.Reset;
import com.gcmb.backend.administration.auth.exception.AuthFailure;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

public final class AuthMapper {
    private AuthMapper() {}
    public static Account account(ResultSet rs, int row) throws SQLException {
        return new Account(rs.getObject("id", UUID.class), rs.getString("username"), rs.getString("email"), rs.getString("password_hash"), rs.getString("status"), rs.getString("role"), rs.getObject("branch_id",UUID.class), rs.getString("branch_name"));
    }
    public static Reset reset(ResultSet rs, int row) throws SQLException {
        return new Reset(rs.getObject("id", UUID.class), rs.getString("code_hash"), instant(rs, "used_at"), instant(rs, "revoked_at"));
    }
    public static Identity identity(ResultSet rs, int row) throws SQLException {
        String role=rs.getString("code");
        return new Identity(rs.getObject("id", UUID.class), rs.getString("username"), role, destination(role), rs.getObject("branch_id",UUID.class), rs.getString("branch_name"));
    }
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
    public static String destination(String role) {
        return switch (role) {
            case "HEAD_OFFICE_MANAGER" -> "/quan-ly-he-thong";
            case "BRANCH_MANAGER" -> "/quan-ly-chi-nhanh";
            case "RECEPTIONIST" -> "/le-tan";
            case "ACCOUNTANT" -> "/ke-toan";
            default -> throw new AuthFailure(403, "form", "Tài khoản chưa được cấp vai trò hợp lệ");
        };
    }
}
