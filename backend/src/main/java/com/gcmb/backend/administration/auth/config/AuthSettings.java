package com.gcmb.backend.administration.auth.config;



import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gcmb.auth")
// Cấu hình cookie an toàn, origin frontend và địa chỉ gửi email.
public record AuthSettings(boolean secureCookie, String frontendOrigin, String mailFrom) {}
