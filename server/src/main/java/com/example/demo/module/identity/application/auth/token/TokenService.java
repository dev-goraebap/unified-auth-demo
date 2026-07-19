package com.example.demo.module.identity.application.auth.token;

import com.example.demo.module.identity.application.auth.AuthenticatedUser;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * 세션 토큰 오케스트레이션(ADR-0006). Access Token(JWT)과 Refresh Token 발급/재발급/폐기를
 * 한데 묶는다. 가입·로그인 유스케이스가 낸 {@link AuthenticatedUser}를 토큰 한 벌로 감싼다.
 */
@Service
public class TokenService {

    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;

    public TokenService(JwtProvider jwtProvider, RefreshTokenService refreshTokenService) {
        this.jwtProvider = jwtProvider;
        this.refreshTokenService = refreshTokenService;
    }

    /** 로그인/가입 성공 → Access Token + 새 RFT를 발급한다(기본: 로그인 유지 ON). */
    public IssuedTokens issueFor(AuthenticatedUser user, Instant now) {
        return issueFor(user, true, now);
    }

    /** 로그인/가입 성공 → Access Token + 새 RFT를 발급한다. {@code remember}로 쿠키 종류를 정한다. */
    public IssuedTokens issueFor(AuthenticatedUser user, boolean remember, Instant now) {
        JwtProvider.IssuedJwt access = jwtProvider.issue(user.userId(), now);
        String refresh = refreshTokenService.issue(user.userId(), remember, now);
        return new IssuedTokens(user, access.token(), access.expiresAt(), refresh, remember);
    }

    /** RFT 회전 → 새 Access Token + 새 RFT. 쿠키 종류(remember)는 이전 토큰에서 승계. */
    public IssuedTokens refresh(String rawRefreshToken, Instant now) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(rawRefreshToken, now);
        JwtProvider.IssuedJwt access = jwtProvider.issue(rotation.user().userId(), now);
        return new IssuedTokens(rotation.user(), access.token(), access.expiresAt(),
                rotation.rawToken(), rotation.remember());
    }

    /** 로그아웃 → RFT 폐기. */
    public void logout(String rawRefreshToken, Instant now) {
        refreshTokenService.revoke(rawRefreshToken, now);
    }

    /** Access Token 검증 → userId. 실패 시 {@link InvalidAccessTokenException}. */
    public UUID authenticate(String accessToken, Instant now) {
        return jwtProvider.parseUserId(accessToken, now);
    }
}
