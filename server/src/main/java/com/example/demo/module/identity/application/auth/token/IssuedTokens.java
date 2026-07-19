package com.example.demo.module.identity.application.auth.token;

import com.example.demo.module.identity.application.auth.AuthenticatedUser;

import java.time.Instant;

/**
 * 발급된 토큰 한 벌. Access Token(JWT)은 응답 바디로, Refresh Token 원문은 httpOnly 쿠키로
 * 나간다(ADR-0006). 이 record는 웹 계층으로 전달되는 내부 결과다.
 *
 * @param user                인증된 사용자
 * @param accessToken         JWT Access Token
 * @param accessTokenExpiresAt Access Token 만료시각
 * @param refreshToken        RFT 원문(쿠키로만 내보낸다. 바디·로그에 노출 금지)
 */
public record IssuedTokens(
        AuthenticatedUser user,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken
) {
}
