package com.example.demo.module.identity.application.auth.token;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Access Token(JWT, HS256) 발급·검증(ADR-0006).
 * <p>
 * 데모라 외부 JWT 라이브러리 대신 표준 {@link Mac}(HMAC-SHA256)과 기존 Jackson으로
 * 직접 구성한다(의존성 최소화 + Boot4/Jackson3 정합). 클레임은 {@code sub}(userId)와
 * {@code iat}/{@code exp}뿐이다. 서명 검증은 상수시간 비교로 위조를 막는다.
 */
@Component
public class JwtProvider {

    /** {"alg":"HS256","typ":"JWT"} 를 Base64URL로 미리 인코딩. */
    private static final String ENCODED_HEADER =
            base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private final byte[] secret;
    private final Duration accessTtl;
    private final ObjectMapper objectMapper;

    public JwtProvider(TokenProperties properties, ObjectMapper objectMapper) {
        this.secret = properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
        this.accessTtl = properties.accessTtl();
        this.objectMapper = objectMapper;
    }

    /** 발급된 JWT와 만료시각. */
    public record IssuedJwt(String token, Instant expiresAt) {
    }

    public IssuedJwt issue(UUID userId, Instant now) {
        Instant expiresAt = now.plus(accessTtl);
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", userId.toString());
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", expiresAt.getEpochSecond());

        String encodedPayload = base64Url(objectMapper.writeValueAsBytes(claims));
        String signingInput = ENCODED_HEADER + "." + encodedPayload;
        String signature = base64Url(hmacSha256(signingInput));
        return new IssuedJwt(signingInput + "." + signature, expiresAt);
    }

    /** 서명·만료를 검증하고 userId(sub)를 돌려준다. 실패 시 {@link InvalidAccessTokenException}. */
    public UUID parseUserId(String token, Instant now) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new InvalidAccessTokenException("형식이 올바르지 않은 토큰입니다");
        }
        String signingInput = parts[0] + "." + parts[1];
        byte[] expected = hmacSha256(signingInput);
        byte[] actual = base64UrlDecode(parts[2]);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new InvalidAccessTokenException("서명이 유효하지 않은 토큰입니다");
        }

        Map<String, Object> claims = readClaims(parts[1]);
        long exp = ((Number) claims.get("exp")).longValue();
        if (now.getEpochSecond() >= exp) {
            throw new InvalidAccessTokenException("만료된 토큰입니다");
        }
        return UUID.fromString((String) claims.get("sub"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readClaims(String encodedPayload) {
        try {
            return objectMapper.readValue(base64UrlDecode(encodedPayload), Map.class);
        } catch (RuntimeException e) {
            throw new InvalidAccessTokenException("해석할 수 없는 토큰입니다");
        }
    }

    private byte[] hmacSha256(String input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 초기화 실패 — 구성 오류", e);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] base64UrlDecode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }
}
