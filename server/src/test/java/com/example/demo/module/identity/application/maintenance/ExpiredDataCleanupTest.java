package com.example.demo.module.identity.application.maintenance;

import com.example.demo.module.identity.domain.token.RefreshToken;
import com.example.demo.module.identity.domain.token.RefreshTokenRepository;
import com.example.demo.module.identity.domain.user.Gender;
import com.example.demo.module.identity.domain.user.User;
import com.example.demo.module.identity.domain.user.UserRepository;
import com.example.demo.module.identity.domain.verification.Verification;
import com.example.demo.module.identity.domain.verification.VerificationPurpose;
import com.example.demo.module.identity.domain.verification.VerificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** 만료 데이터 청소(ADR-0005/0006) 통합 테스트. 만료 행만 지우고 유효 행은 남는지 검증한다. */
@SpringBootTest
@Transactional
class ExpiredDataCleanupTest {

    @Autowired ExpiredDataCleanup cleanup;
    @Autowired VerificationRepository verificationRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired UserRepository userRepository;

    @Test
    void 만료된_verification과_refresh_token만_삭제된다() {
        Instant now = Instant.now();

        // verification: 만료 1건 + 유효 1건
        verificationRepository.save(Verification.issue(
                VerificationPurpose.PASS_RESULT, "expired-ref", "{\"x\":1}", now.minusSeconds(3600)));
        verificationRepository.save(Verification.issue(
                VerificationPurpose.PASS_RESULT, "fresh-ref", "{\"x\":1}", now.plusSeconds(3600)));

        // refresh_token: 만료 1건 + 유효 1건 (User 필요)
        User user = userRepository.save(User.register(
                "di-cleanup-test", null, "청소대상", LocalDate.of(1990, 1, 1), Gender.M, null));
        String expiredHash = "a".repeat(64);
        String freshHash = "b".repeat(64);
        refreshTokenRepository.save(RefreshToken.issue(user, expiredHash, now.minusSeconds(3600)));
        refreshTokenRepository.save(RefreshToken.issue(user, freshHash, now.plusSeconds(3600)));

        long deleted = cleanup.purgeExpiredAt(now);

        assertThat(deleted).isGreaterThanOrEqualTo(2);
        // 만료 행은 사라지고
        assertThat(verificationRepository.findByReference("expired-ref")).isEmpty();
        assertThat(refreshTokenRepository.findByTokenHash(expiredHash)).isEmpty();
        // 유효 행은 남는다
        assertThat(verificationRepository.findByReference("fresh-ref")).isPresent();
        assertThat(refreshTokenRepository.findByTokenHash(freshHash)).isPresent();
    }
}
