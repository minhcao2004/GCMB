package com.gcmb.backend.administration.auth.security;



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
    public String code() { return String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000)); }
    public String token() {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public String encode(String value) { return "{pbkdf2-gcmb}" + encoder.encode(value); }
    public boolean matches(String raw, String stored) {
        if (stored == null) return false;
        if (stored.startsWith("{pbkdf2-gcmb}")) return encoder.matches(raw, stored.substring(13));
        if (stored.startsWith("{bcrypt}")) return bcrypt.matches(raw, stored.substring(8));
        if (stored.matches("^\\$2[aby]\\$.*")) return bcrypt.matches(raw, stored);
        return false; // Never accept plaintext legacy passwords.
    }
    public static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
