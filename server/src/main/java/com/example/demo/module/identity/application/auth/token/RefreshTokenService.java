package com.example.demo.module.identity.application.auth.token;

import com.example.demo.module.identity.application.auth.AuthenticatedUser;
import com.example.demo.module.identity.domain.token.RefreshToken;
import com.example.demo.module.identity.domain.token.RefreshTokenRepository;
import com.example.demo.module.identity.domain.user.User;
import com.example.demo.module.identity.domain.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Refresh Token 저장·회전·폐기(ADR-0006). 원문은 클라이언트(httpOnly 쿠키)에만 있고,
 * 서버는 SHA-256 hex 해시만 저장한다({@link RefreshToken}). 재발급 시 기존 토큰을 폐기하고
 * 새 토큰으로 교체(replaced_by 체인)한다.
 */
@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final Duration ttl;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               UserRepository userRepository,
                               TokenProperties properties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.ttl = properties.refreshTtl();
    }

    /** 새 RFT를 발급해 저장하고 <b>원문</b>을 돌려준다(원문은 여기서만 노출). */
    @Transactional
    public String issue(UUID userId, boolean remember, Instant now) {
        User user = userRepository.getReferenceById(userId);
        return issueFor(user, remember, now).raw();
    }

    /**
     * RFT를 회전한다: 원문으로 활성 토큰을 찾아 폐기하고 새 토큰을 발급한다.
     *
     * @return 새 원문 + 소유 사용자
     * @throws InvalidRefreshTokenException 토큰이 없거나 이미 폐기·만료된 경우
     */
    @Transactional
    public Rotation rotate(String rawToken, Instant now) {
        RefreshToken current = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .filter(t -> t.isActive(now))
                .orElseThrow(() -> new InvalidRefreshTokenException("유효하지 않은 refresh token입니다. 다시 로그인해 주세요."));

        User user = current.getUser();
        boolean remember = current.isRemember(); // 회전해도 쿠키 종류(영속/세션) 유지.
        Issued next = issueFor(user, remember, now);
        current.rotateTo(next.entity().getId(), now);

        return new Rotation(new AuthenticatedUser(user.getId(), user.getName()), next.raw(), remember);
    }

    /** 로그아웃 — 원문에 해당하는 토큰을 폐기한다. 없으면 조용히 무시(멱등). */
    @Transactional
    public void revoke(String rawToken, Instant now) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> token.revoke(now));
    }

    private Issued issueFor(User user, boolean remember, Instant now) {
        String raw = newRawToken();
        RefreshToken saved = refreshTokenRepository.save(RefreshToken.issue(user, hash(raw), now.plus(ttl), remember));
        return new Issued(saved, raw);
    }

    private record Issued(RefreshToken entity, String raw) {
    }

    /** SecureRandom 32바이트 → URL-safe Base64(43자). */
    private String newRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 원문 → SHA-256 hex(64자). 도메인 저장 형식과 일치. */
    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 미지원 — JVM 구성 오류", e);
        }
    }

    public record Rotation(AuthenticatedUser user, String rawToken, boolean remember) {
    }
}
