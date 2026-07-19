package com.example.demo.module.identity.domain.verification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * 인증 진행 중 임시데이터(OAuth state / 본인인증 결과). TTL 있음.
 * 아직 가입 전 익명 단계라 users 와 FK로 묶이지 않는다.
 * reference(토큰)로만 조회하며, payload 는 용도별로 형태가 달라 jsonb 로 유연하게 담는다.
 */
@Entity
@Table(name = "verification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Verification {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VerificationPurpose purpose;

    /** 클라이언트에 돌려주는 조회 토큰. */
    @Column(nullable = false, unique = true, length = 128)
    private String reference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private Verification(VerificationPurpose purpose, String reference, String payload, Instant expiresAt) {
        if (purpose == null) throw new IllegalArgumentException("purpose는 필수입니다");
        if (reference == null || reference.isBlank()) throw new IllegalArgumentException("reference는 필수입니다");
        if (payload == null || payload.isBlank()) throw new IllegalArgumentException("payload는 필수입니다");
        if (expiresAt == null) throw new IllegalArgumentException("expiresAt는 필수입니다");
        this.id = UUID.randomUUID();
        this.purpose = purpose;
        this.reference = reference;
        this.payload = payload;
        this.expiresAt = expiresAt;
    }

    public static Verification issue(VerificationPurpose purpose, String reference, String payload, Instant expiresAt) {
        return new Verification(purpose, reference, payload, expiresAt);
    }

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }
}
