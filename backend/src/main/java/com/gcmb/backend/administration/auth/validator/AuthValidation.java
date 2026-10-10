package com.gcmb.backend.administration.auth.validator;

import com.gcmb.backend.administration.auth.exception.AuthFailure;

import java.util.Locale;
import java.util.regex.Pattern;

public final class AuthValidation {
    private AuthValidation() {}
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+$");
    public static String required(String value, String field, String label, int max, boolean trim) {
        if (value == null || value.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c) || c == 0xFEFF)) throw new AuthFailure(400, field, "Vui lòng nhập " + label);
        String result = trim ? value.replaceAll("^[\\p{javaWhitespace}\\p{Z}\\uFEFF]+|[\\p{javaWhitespace}\\p{Z}\\uFEFF]+$", "") : value;
        if (result.length() > max) throw new AuthFailure(400, field, label + " không được vượt quá " + max + " ký tự");
        return result;
    }
    public static String email(String value) {
        String email = required(value, "email", "email", 254, true).toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(email).matches() || email.indexOf('@') > 64 || email.startsWith(".") || email.contains("..") || email.contains(".@"))
            throw new AuthFailure(400, "email", "Email không đúng định dạng");
        return email;
    }
    public static void password(String password, String confirmation) {
        required(password, "password", "mật khẩu mới", Integer.MAX_VALUE, false);
        required(confirmation, "confirmation", "xác nhận mật khẩu", Integer.MAX_VALUE, false);
        if (!password.equals(confirmation)) throw new AuthFailure(400, "confirmation", "Mật khẩu xác nhận không khớp");
    }
    public static void code(String code) {
        if (code == null || code.isBlank()) throw new AuthFailure(400, "code", "Vui lòng nhập mã xác nhận");
        if (!code.matches("[0-9]{6}")) throw new AuthFailure(400, "code", "Mã xác nhận phải gồm đúng 6 chữ số từ 0–9");
    }
}
