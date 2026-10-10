package com.gcmb.backend.administration.auth.config;

import com.gcmb.backend.administration.auth.service.AuthService;
import com.gcmb.backend.administration.auth.security.SessionFilter;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;

@Configuration
@EnableConfigurationProperties(AuthSettings.class)
public class AuthSecurity {
    @Bean SecurityFilterChain security(HttpSecurity http, AuthService auth, AuthSettings policy) throws Exception {
        CookieCsrfTokenRepository csrf = new CookieCsrfTokenRepository();
        csrf.setCookieCustomizer(cookie -> cookie.path("/api").sameSite("Lax").httpOnly(true).secure(policy.secureCookie()));
        return http
            .cors(cors -> cors.configurationSource(request -> {
                CorsConfiguration config = new CorsConfiguration();
                config.setAllowedOrigins(List.of(policy.frontendOrigin()));
                config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
                config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
                config.setAllowCredentials(true); return config;
            }))
            .csrf(config -> config.csrfTokenRepository(csrf))
            .sessionManagement(config -> config.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(config -> config.disable())
            .formLogin(config -> config.disable()).httpBasic(config -> config.disable()).logout(config -> config.disable())
            .authorizeHttpRequests(config -> config
                .requestMatchers("/api/v1/auth/csrf", "/api/v1/auth/health", "/api/v1/auth/login", "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password").permitAll()
                .requestMatchers("/api/v1/auth/me", "/api/v1/auth/logout").authenticated()
                .requestMatchers("/api/v1/head-office/**").hasRole("HEAD_OFFICE_MANAGER")
                .requestMatchers("/api/v1/branch-management/**").hasRole("BRANCH_MANAGER")
                .requestMatchers("/api/v1/reception/**").hasRole("RECEPTIONIST")
                .requestMatchers("/api/v1/accounting/**").hasRole("ACCOUNTANT")
                .anyRequest().denyAll())
            .exceptionHandling(config -> config
                .authenticationEntryPoint((request, response, e) -> {
                    response.setStatus(401); response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"message\":\"Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại\"}");
                })
                .accessDeniedHandler((request, response, e) -> {
                    response.setStatus(403); response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"message\":\"Yêu cầu không được phép hoặc mã bảo vệ đã hết hạn. Vui lòng tải lại trang\"}");
                }))
            .addFilterBefore(new SessionFilter(auth), UsernamePasswordAuthenticationFilter.class).build();
    }
}
