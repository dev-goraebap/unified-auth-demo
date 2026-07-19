package com.example.demo.module.identity.application.auth.token;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code Authorization: Bearer <JWT>} 헤더에서 Access Token을 꺼내 검증하고 userId를 돌려준다.
 * <p>
 * 아직 SecurityFilterChain을 두지 않으므로(보호 리소스가 소수) 보호 엔드포인트가 직접 호출한다.
 * 인증 필터가 필요해지면 이 로직을 필터로 옮긴다.
 */
@Component
public class BearerTokenAuthenticator {

    private static final String PREFIX = "Bearer ";

    private final TokenService tokenService;

    public BearerTokenAuthenticator(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    /** Bearer 헤더 검증 → userId. 헤더가 없거나 토큰이 무효/만료면 {@link InvalidAccessTokenException}. */
    public UUID authenticate(String authorizationHeader, Instant now) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(PREFIX)) {
            throw new InvalidAccessTokenException("Authorization: Bearer 토큰이 필요합니다");
        }
        return tokenService.authenticate(authorizationHeader.substring(PREFIX.length()), now);
    }
}
