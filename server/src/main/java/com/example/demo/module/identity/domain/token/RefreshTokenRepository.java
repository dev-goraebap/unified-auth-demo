package com.example.demo.module.identity.domain.token;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUser_Id(UUID userId);

    /** 주기 배치 청소용 — 만료된 RFT 삭제(ADR-0006). 폐기됐어도 만료 전이면 회전체인 추적 위해 남긴다. */
    long deleteByExpiresAtBefore(Instant threshold);
}
