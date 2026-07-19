package com.example.demo.module.identity.application.maintenance;

import com.example.demo.module.identity.domain.token.RefreshTokenRepository;
import com.example.demo.module.identity.domain.verification.VerificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 만료된 임시데이터 주기 청소(ADR-0005·0006).
 * <ul>
 *   <li>{@code verification}: 만료된 본인인증 결과·OAuth state(익명 임시데이터).</li>
 *   <li>{@code refresh_token}: 만료된 RFT. 폐기됐어도 만료 전이면 회전체인 추적 위해 남긴다.</li>
 * </ul>
 * 기본 매시 정각 실행({@code auth.cleanup.cron}으로 조정). 데모라 삭제 정책은 단순하다.
 */
@Component
public class ExpiredDataCleanup {

    private static final Logger log = LoggerFactory.getLogger(ExpiredDataCleanup.class);

    private final VerificationRepository verificationRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public ExpiredDataCleanup(VerificationRepository verificationRepository,
                              RefreshTokenRepository refreshTokenRepository) {
        this.verificationRepository = verificationRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /** 스케줄 진입점. 트랜잭션 경계는 여기(프록시가 감싼다). */
    @Scheduled(cron = "${auth.cleanup.cron:0 0 * * * *}")
    @Transactional
    public void purgeExpired() {
        purgeExpiredAt(Instant.now());
    }

    /**
     * 기준 시각 이전에 만료된 행을 삭제한다. {@code now}를 인자로 받아 테스트에서 결정적으로 검증한다.
     * (호출자 트랜잭션 안에서 실행 — 스케줄 경로는 {@link #purgeExpired()}가 연다.)
     *
     * @return 삭제된 총 건수
     */
    public long purgeExpiredAt(Instant now) {
        long verifications = verificationRepository.deleteByExpiresAtBefore(now);
        long refreshTokens = refreshTokenRepository.deleteByExpiresAtBefore(now);
        if (verifications + refreshTokens > 0) {
            log.info("만료 데이터 청소: verification {}건, refresh_token {}건 삭제", verifications, refreshTokens);
        }
        return verifications + refreshTokens;
    }
}
