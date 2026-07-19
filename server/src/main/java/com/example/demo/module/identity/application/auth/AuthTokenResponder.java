package com.example.demo.module.identity.application.auth;

import com.example.demo.module.identity.application.auth.token.IssuedTokens;
import com.example.demo.module.identity.application.auth.token.TokenProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;

/**
 * 토큰의 HTTP 표현을 담당한다(ADR-0006). Access Token은 바디로, Refresh Token 원문은
 * httpOnly 쿠키로 내보낸다. 쿠키는 인증 엔드포인트(/api/auth)에만 실리도록 path를 좁힌다.
 */
@Component
public class AuthTokenResponder {

    private final TokenProperties properties;

    public AuthTokenResponder(TokenProperties properties) {
        this.properties = properties;
    }

    /** 발급된 토큰을 응답에 싣는다: RFT는 쿠키로, 나머지는 {@link AuthResponse} 바디로.
     *  remember=true면 영속 쿠키(Max-Age), false면 세션 쿠키(Max-Age 미출력)로 굽는다. */
    public AuthResponse write(IssuedTokens tokens, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                refreshCookie(tokens.refreshToken(), tokens.remember()).toString());
        return new AuthResponse(
                tokens.user().userId(), tokens.user().name(),
                tokens.accessToken(), tokens.accessTokenExpiresAt());
    }

    /** 로그아웃 — RFT 쿠키를 즉시 만료시킨다. */
    public void clearRefreshCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, expiredRefreshCookie().toString());
    }

    /** 요청 쿠키에서 RFT 원문을 읽는다(쿠키 이름은 설정값). */
    public Optional<String> readRefreshToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> properties.refreshCookie().equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    private ResponseCookie refreshCookie(String rawToken, boolean remember) {
        ResponseCookie.ResponseCookieBuilder base = baseCookie(rawToken);
        // remember=true → Max-Age 부여(영속). false → maxAge 미설정(기본 -1) → Max-Age 미출력 = 세션 쿠키.
        return remember ? base.maxAge(properties.refreshTtl()).build() : base.build();
    }

    private ResponseCookie expiredRefreshCookie() {
        return baseCookie("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(properties.refreshCookie(), value)
                .httpOnly(true)
                .secure(properties.refreshCookieSecure())
                .path("/api/auth")
                .sameSite("Lax");
    }
}
