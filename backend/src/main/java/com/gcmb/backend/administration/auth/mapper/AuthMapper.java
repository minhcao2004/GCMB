package com.gcmb.backend.administration.auth.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import com.gcmb.backend.administration.auth.entity.Account;
import com.gcmb.backend.administration.auth.entity.Reset;
import com.gcmb.backend.administration.auth.dto.response.Identity;
import com.gcmb.backend.administration.auth.exception.AuthFailure;

public final class AuthMapper {
    private AuthMapper() {}
    public static Account account(ResultSet rs, int row) throws SQLException {
        return new Account(rs.getObject("id", UUID.class), rs.getString("username"), rs.getString("email"), rs.getString("password_hash"), rs.getString("status"), rs.getString("role"));
    }
    public static Reset reset(ResultSet rs, int row) throws SQLException {
        return new Reset(rs.getObject("id", UUID.class), rs.getString("code_hash"), instant(rs, "used_at"), instant(rs, "revoked_at"));
    }
    public static Identity identity(ResultSet rs, int row) throws SQLException {
        return new Identity(rs.getObject("id", UUID.class), rs.getString("username"), rs.getString("code"), destination(rs.getString("code")));
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
