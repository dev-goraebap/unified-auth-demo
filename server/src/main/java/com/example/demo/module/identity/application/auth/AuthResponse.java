package com.example.demo.module.identity.application.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * 인증 성공 응답(API DTO). Access Token은 바디로 내려주고, 프론트는 메모리에 보관한다(ADR-0006).
 * Refresh Token은 바디에 담지 않는다 — httpOnly 쿠키로만 나간다.
 *
 * @param userId               인증된 사용자 id
 * @param name                 사용자 이름
 * @param accessToken          JWT Access Token(Authorization: Bearer 로 사용)
 * @param accessTokenExpiresAt Access Token 만료시각(프론트 재발급 판단용)
 */
public record AuthResponse(UUID userId, String name, String accessToken, Instant accessTokenExpiresAt) {
}
