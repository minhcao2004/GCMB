/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: HuyDV
 * Created: 10/10/2026
 * Description: Khai báo cấu hình cookie bảo mật, địa chỉ frontend và địa chỉ email gửi thư.
 */

package com.gcmb.backend.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gcmb.auth")
// Cấu hình cookie an toàn, origin frontend và địa chỉ gửi email.
public record AuthSettings(boolean secureCookie, String frontendOrigin, String mailFrom) {}
