package com.example.demo.module.identity.application.auth;

import java.util.UUID;

/**
 * 인증 성공 응답(API DTO). 슬라이스 2는 "누가 인증됐는지"만 돌려준다.
 * 슬라이스 3에서 여기에 accessToken 등 토큰 필드가 더해진다(ADR-0006).
 */
public record AuthResponse(UUID userId, String name) {

    public static AuthResponse from(AuthenticatedUser user) {
        return new AuthResponse(user.userId(), user.name());
    }
}
