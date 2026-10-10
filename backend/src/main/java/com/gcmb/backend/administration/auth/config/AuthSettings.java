package com.gcmb.backend.administration.auth.config;



import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gcmb.auth")
public record AuthSettings(boolean secureCookie, String frontendOrigin, String mailFrom) {}
