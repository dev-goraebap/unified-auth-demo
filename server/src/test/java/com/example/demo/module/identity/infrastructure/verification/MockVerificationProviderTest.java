package com.example.demo.module.identity.infrastructure.verification;

import com.example.demo.module.identity.application.verification.VerificationNotFoundException;
import com.example.demo.module.identity.application.verification.VerificationResult;
import com.example.demo.module.identity.domain.user.Gender;
import com.example.demo.module.identity.domain.verification.Verification;
import com.example.demo.module.identity.domain.verification.VerificationPurpose;
import com.example.demo.module.identity.domain.verification.VerificationRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mock 본인인증(ADR-0004) 통합 테스트. 실제 postgres에 붙어 start→verify 전체를 검증한다.
 * {@code @Transactional}로 각 테스트는 롤백되어 DB를 더럽히지 않는다.
 */
@SpringBootTest
@Transactional
class MockVerificationProviderTest {

    @Autowired
    MockVerificationProvider provider;

    @Autowired
    VerificationRepository verificationRepository;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void start_후_verify하면_입력값과_DI가_돌아온다() {
        String reference = provider.startVerification("홍길동", LocalDate.of(1990, 1, 1), Gender.M, "01012345678");

        VerificationResult result = provider.verify(reference);

        assertThat(result.name()).isEqualTo("홍길동");
        assertThat(result.birthDate()).isEqualTo(LocalDate.of(1990, 1, 1));
        assertThat(result.gender()).isEqualTo(Gender.M);
        assertThat(result.phone()).isEqualTo("01012345678");
        // 실제 PASS DI 형식과 동일한 길이(Base64 88자).
        assertThat(result.di()).hasSize(88);
        assertThat(result.ci()).hasSize(88);
        assertThat(result.di()).isNotEqualTo(result.ci()); // 솔트가 다르므로
    }

    @Test
    void 같은_사람정보는_항상_같은_DI를_만든다() {
        String ref1 = provider.startVerification("김철수", LocalDate.of(1985, 5, 5), Gender.M, "01011112222");
        String ref2 = provider.startVerification("김철수", LocalDate.of(1985, 5, 5), Gender.M, "01099998888");

        VerificationResult r1 = provider.verify(ref1);
        VerificationResult r2 = provider.verify(ref2);

        // 휴대폰번호가 달라도(재료 제외) DI/CI는 동일 — 재가입/기존계정 연결 재현.
        assertThat(r1.di()).isEqualTo(r2.di());
        assertThat(r1.ci()).isEqualTo(r2.ci());
    }

    @Test
    void 다른_사람정보는_다른_DI를_만든다() {
        String ref1 = provider.startVerification("이영희", LocalDate.of(1992, 3, 3), Gender.F, null);
        String ref2 = provider.startVerification("이영희", LocalDate.of(1992, 3, 4), Gender.F, null);

        assertThat(provider.verify(ref1).di()).isNotEqualTo(provider.verify(ref2).di());
    }

    @Test
    void 존재하지_않는_reference는_예외() {
        assertThatThrownBy(() -> provider.verify("no-such-reference"))
                .isInstanceOf(VerificationNotFoundException.class);
    }

    @Test
    void 만료된_본인인증은_예외() throws Exception {
        String payload = objectMapper.writeValueAsString(
                new MockPassPayload("만료자", LocalDate.of(2000, 1, 1), Gender.F, null));
        Verification expired = Verification.issue(
                VerificationPurpose.PASS_RESULT, "expired-ref", payload, Instant.now().minusSeconds(60));
        verificationRepository.save(expired);

        assertThatThrownBy(() -> provider.verify("expired-ref"))
                .isInstanceOf(VerificationNotFoundException.class);
    }

    @Test
    void 용도가_PASS_RESULT가_아니면_찾지_못한다() throws Exception {
        Verification oauthState = Verification.issue(
                VerificationPurpose.OAUTH_STATE, "state-ref", "{\"state\":\"x\"}", Instant.now().plusSeconds(300));
        verificationRepository.save(oauthState);

        assertThatThrownBy(() -> provider.verify("state-ref"))
                .isInstanceOf(VerificationNotFoundException.class);
    }
}
