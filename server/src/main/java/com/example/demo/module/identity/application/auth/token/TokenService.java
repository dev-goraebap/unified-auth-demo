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

    /** 로그인/가입 성공 → Access Token + 새 RFT를 발급한다. */
    public IssuedTokens issueFor(AuthenticatedUser user, Instant now) {
        JwtProvider.IssuedJwt access = jwtProvider.issue(user.userId(), now);
        String refresh = refreshTokenService.issue(user.userId(), now);
        return new IssuedTokens(user, access.token(), access.expiresAt(), refresh);
    }

    /** RFT 회전 → 새 Access Token + 새 RFT. RFT가 유효하지 않으면 예외. */
    public IssuedTokens refresh(String rawRefreshToken, Instant now) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(rawRefreshToken, now);
        JwtProvider.IssuedJwt access = jwtProvider.issue(rotation.user().userId(), now);
        return new IssuedTokens(rotation.user(), access.token(), access.expiresAt(), rotation.rawToken());
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
