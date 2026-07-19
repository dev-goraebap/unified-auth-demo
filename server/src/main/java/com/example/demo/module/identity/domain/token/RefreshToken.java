package com.example.demo.module.identity.domain.token;

import com.example.demo.module.identity.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Refresh Token(ADR-0006). 원문이 아닌 SHA-256 해시만 저장한다.
 * 회전 시 기존 토큰을 폐기(revoked_at)하고 새 토큰으로 교체(replaced_by)해 체인을 남긴다.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 토큰 원문의 SHA-256 hex(64자). 원문은 저장하지 않는다. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** 회전 체인 — 이 토큰을 대체한 새 토큰의 id. */
    @Column(name = "replaced_by", columnDefinition = "uuid")
    private UUID replacedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private RefreshToken(User user, String tokenHash, Instant expiresAt) {
        if (user == null) throw new IllegalArgumentException("user는 필수입니다");
        if (tokenHash == null || tokenHash.isBlank()) throw new IllegalArgumentException("tokenHash는 필수입니다");
        if (expiresAt == null) throw new IllegalArgumentException("expiresAt는 필수입니다");
        this.id = UUID.randomUUID();
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public static RefreshToken issue(User user, String tokenHash, Instant expiresAt) {
        return new RefreshToken(user, tokenHash, expiresAt);
    }

    /** 로그아웃 등으로 토큰을 폐기한다. */
    public void revoke(Instant now) {
        if (this.revokedAt == null) {
            this.revokedAt = now;
        }
    }

    /** 회전: 이 토큰을 폐기하고 새 토큰으로 교체 표시한다. */
    public void rotateTo(UUID newTokenId, Instant now) {
        revoke(now);
        this.replacedBy = newTokenId;
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
