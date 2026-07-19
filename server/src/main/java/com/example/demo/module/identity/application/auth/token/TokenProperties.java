package com.example.demo.module.identity.application.auth.token;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 세션 토큰 설정(ADR-0006). 권장값을 기본으로 둔다.
 *
 * @param jwtSecret           Access Token(JWT) HMAC-SHA256 서명 키. 최소 32바이트.
 *                            데모 기본값이 있으나 운영에서는 반드시 {@code auth.token.jwt-secret}로 덮어쓴다.
 * @param accessTtl           Access Token 수명(권장 15분).
 * @param refreshTtl          Refresh Token 수명(권장 14일).
 * @param refreshCookie       RFT를 담는 httpOnly 쿠키 이름.
 * @param refreshCookieSecure 쿠키 Secure 속성. 로컬 http 개발은 false, 운영(https)은 true.
 */
@ConfigurationProperties(prefix = "auth.token")
public record TokenProperties(
        String jwtSecret,
        Duration accessTtl,
        Duration refreshTtl,
        String refreshCookie,
        boolean refreshCookieSecure
) {
    public TokenProperties {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            jwtSecret = "demo-only-insecure-jwt-secret-change-me-please-32b+";
        }
        if (accessTtl == null) accessTtl = Duration.ofMinutes(15);
        if (refreshTtl == null) refreshTtl = Duration.ofDays(14);
        if (refreshCookie == null || refreshCookie.isBlank()) refreshCookie = "refresh_token";
    }
}
