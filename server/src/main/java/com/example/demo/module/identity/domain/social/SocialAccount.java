package com.example.demo.module.identity.domain.social;

import com.example.demo.module.identity.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * 소셜 계정. 사용자당 0..N.
 * (provider, provider_user_id) 유니크 — 한 소셜 계정이 두 사용자에게 붙지 못한다.
 * 데모 방침: 최소 연동정보만 저장(프로필 캐시 없음).
 */
@Entity
@Table(
        name = "social_accounts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_social_provider_uid",
                columnNames = {"provider", "provider_user_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SocialProvider provider;

    @Column(name = "provider_user_id", nullable = false, length = 255)
    private String providerUserId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private SocialAccount(User user, SocialProvider provider, String providerUserId) {
        if (user == null) throw new IllegalArgumentException("user는 필수입니다");
        if (provider == null) throw new IllegalArgumentException("provider는 필수입니다");
        if (providerUserId == null || providerUserId.isBlank()) throw new IllegalArgumentException("providerUserId는 필수입니다");
        this.id = UUID.randomUUID();
        this.user = user;
        this.provider = provider;
        this.providerUserId = providerUserId;
    }

    /** 소셜 계정을 사용자에게 연결한다. */
    public static SocialAccount link(User user, SocialProvider provider, String providerUserId) {
        return new SocialAccount(user, provider, providerUserId);
    }
}
