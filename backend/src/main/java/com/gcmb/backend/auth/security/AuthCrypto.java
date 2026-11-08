/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 10/10/2026
 * Description: Sinh mã xác nhận và token ngẫu nhiên, băm dữ liệu và kiểm tra mật khẩu.
 */

package com.gcmb.backend.auth.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AuthCrypto {

    private final SecureRandom random = new SecureRandom();
    private final PasswordEncoder encoder = new Pbkdf2PasswordEncoder("", 16, 600_000, 256);
    private final PasswordEncoder bcrypt = new BCryptPasswordEncoder();

    // Sinh mã xác nhận 6 chữ số, kể cả số 0 ở đầu.
    public String code() {
        return String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
    }

    // Sinh token ngẫu nhiên dùng cho phiên hoặc challenge.
    public String token() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // Băm mật khẩu/mã bằng PBKDF2 với prefix nhận diện thuật toán.
    public String encode(String value) {
        return "{pbkdf2-gcmb}" + encoder.encode(value);
    }

    // Kiểm tra hash PBKDF2 hiện tại hoặc bcrypt cũ; không nhận plaintext.
    public boolean matches(String raw, String stored) {
        if (stored == null) return false;
        if (stored.startsWith("{pbkdf2-gcmb}")) return encoder.matches(raw, stored.substring(13));
        if (stored.startsWith("{bcrypt}")) return bcrypt.matches(raw, stored.substring(8));
        if (stored.matches("^\\$2[aby]\\$.*")) return bcrypt.matches(raw, stored);
        return false; // Never accept plaintext legacy passwords.
    }

    // Băm token bằng SHA-256 trước khi lưu hoặc tìm trong database.
    public static String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
