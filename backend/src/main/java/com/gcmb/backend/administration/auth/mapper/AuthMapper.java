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
    // Lớp chỉ chứa hàm ánh xạ tĩnh, không tạo instance.
    private AuthMapper() {}
    // Ánh xạ hàng tài khoản và vai trò từ JDBC.
    public static Account account(ResultSet rs, int row) throws SQLException {
        return new Account(rs.getObject("id", UUID.class), rs.getString("username"), rs.getString("email"), rs.getString("password_hash"), rs.getString("status"), rs.getString("role"));
    }
    // Ánh xạ trạng thái mã khôi phục từ JDBC.
    public static Reset reset(ResultSet rs, int row) throws SQLException {
        return new Reset(rs.getObject("id", UUID.class), rs.getString("code_hash"), instant(rs, "used_at"), instant(rs, "revoked_at"));
    }
    // Ánh xạ danh tính phiên sang dữ liệu dùng bởi Spring Security.
    public static Identity identity(ResultSet rs, int row) throws SQLException {
        return new Identity(rs.getObject("id", UUID.class), rs.getString("username"), rs.getString("code"), destination(rs.getString("code")));
    }
    // Đọc timestamp có thể rỗng từ ResultSet.
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
    // Ánh xạ vai trò tới trang đích sau đăng nhập.
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
