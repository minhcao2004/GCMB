/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 10/10/2026
 * Description: Kiểm tra dữ liệu bắt buộc, định dạng email, xác nhận mật khẩu và mã xác nhận sáu chữ số.
 */

package com.gcmb.backend.auth.service;

import com.gcmb.backend.auth.exception.AuthFailure;

import java.util.Locale;
import java.util.regex.Pattern;

public final class AuthValidation {
    // Lớp chỉ chứa quy tắc kiểm tra đầu vào tĩnh, không tạo instance.
    private AuthValidation() {}
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+$");
    // BR-111: chặn giá trị bắt buộc bị thiếu hoặc chỉ có khoảng trắng.
    // Giới hạn độ dài và chỉ trim khi hợp đồng của trường cho phép.
    public static String required(String value, String field, String label, int max, boolean trim) {
        if (value == null || value.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c) || c == 0xFEFF)) throw new AuthFailure(400, field, "Vui lòng bổ sung " + label + ".");
        String result = trim ? value.replaceAll("^[\\p{javaWhitespace}\\p{Z}\\uFEFF]+|[\\p{javaWhitespace}\\p{Z}\\uFEFF]+$", "") : value;
        if (result.length() > max) throw new AuthFailure(400, field, label + " chỉ được chứa tối đa " + max + " ký tự.");
        return result;
    }
    // BR-113: giới hạn email 64 ký tự trước @ và 254 ký tự tổng cộng.
    public static String email(String value) {
        String email = required(value, "email", "email", 254, true).toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(email).matches() || email.indexOf('@') > 64 || email.startsWith(".") || email.contains("..") || email.contains(".@"))
            throw new AuthFailure(400, "email", "Nhập địa chỉ email theo dạng ten@tenmien.com.");
        return email;
    }
    // BR-120: mật khẩu mới phải khớp trường xác nhận; không tự trim mật khẩu.
    public static void password(String password, String confirmation) {
        required(password, "password", "mật khẩu mới", Integer.MAX_VALUE, false);
        required(confirmation, "confirmation", "xác nhận mật khẩu", Integer.MAX_VALUE, false);
        if (!password.equals(confirmation)) throw new AuthFailure(400, "confirmation", "Mật khẩu xác nhận không khớp với mật khẩu mới.");
    }
    // Chỉ chấp nhận đúng 6 chữ số ASCII cho mã xác nhận.
    public static void code(String code) {
        if (code == null || code.isBlank()) throw new AuthFailure(400, "code", "Vui lòng bổ sung mã xác minh.");
        if (!code.matches("[0-9]{6}")) throw new AuthFailure(400, "code", "Mã xác nhận phải gồm đúng 6 chữ số từ 0–9");
    }
}
