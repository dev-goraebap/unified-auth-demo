package com.example.demo.module.identity.domain.verification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface VerificationRepository extends JpaRepository<Verification, UUID> {

    Optional<Verification> findByReference(String reference);

    /** 주기 배치 청소용 — 만료된 행 삭제(ADR-0005 결정). */
    long deleteByExpiresAtBefore(Instant threshold);
}
