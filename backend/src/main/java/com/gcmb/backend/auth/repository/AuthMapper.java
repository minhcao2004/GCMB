/**
 * Copyright (c) 2026 SEP490-G12.
 *

 * Author: HuyDV
 * Created: 10/10/2026
 * Description: Ánh xạ kết quả truy vấn thành dữ liệu xác thực và xác định đường dẫn theo vai trò.


 */

package com.gcmb.backend.auth.repository;

import com.gcmb.backend.auth.dto.Identity;
import com.gcmb.backend.auth.repository.model.Account;
import com.gcmb.backend.auth.repository.model.Reset;
import com.gcmb.backend.auth.exception.AuthFailure;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

public final class AuthMapper {

    // Lớp chỉ chứa hàm ánh xạ tĩnh, không tạo instance.
    private AuthMapper() {}

    // Ánh xạ hàng tài khoản và vai trò từ JDBC.
    public static Account account(ResultSet rs, int row) throws SQLException {
        return new Account(
            rs.getObject("id", UUID.class),
            rs.getString("username"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getString("status"),
            rs.getString("role"),
            rs.getObject("branch_id", UUID.class),
            rs.getString("branch_name")
        );
    }

    // Ánh xạ trạng thái mã khôi phục từ JDBC.
    public static Reset reset(ResultSet rs, int row) throws SQLException {
        return new Reset(
            rs.getObject("id", UUID.class),
            rs.getString("code_hash"),
            instant(rs, "used_at"),
            instant(rs, "revoked_at")
        );
    }

    // Ánh xạ danh tính phiên sang dữ liệu dùng bởi Spring Security.
    public static Identity identity(ResultSet rs, int row) throws SQLException {
        String role = rs.getString("code");
        // BR-177: dữ liệu cơ sở tạm ngừng vẫn được đọc, nhưng không được sửa.
        return new Identity(
            rs.getObject("id", UUID.class),
            rs.getString("username"),
            role,
            destination(role),
            rs.getObject("branch_id", UUID.class),
            rs.getString("branch_name"),
            java.util.List.of("BRANCH_MANAGER", "RECEPTIONIST").contains(role) &&
                "SUSPENDED".equals(rs.getString("branch_status"))
        );
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
