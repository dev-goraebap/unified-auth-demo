package com.example.demo.module.identity.application.auth;

import com.example.demo.module.identity.domain.credential.LocalCredential;
import com.example.demo.module.identity.domain.credential.LocalCredentialRepository;
import com.example.demo.module.identity.domain.social.SocialProvider;
import com.example.demo.module.identity.domain.user.Gender;
import com.example.demo.module.identity.domain.user.UserRepository;
import com.example.demo.module.identity.infrastructure.verification.MockVerificationProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 로컬 가입(PASS 우선) 통합 테스트. 실제 postgres에 붙어 verify→가입 전체를 검증한다.
 * {@code @Transactional}로 각 테스트는 롤백된다.
 */
@SpringBootTest
@Transactional
class SignupServiceTest {

    @Autowired SignupService signupService;
    @Autowired SocialAuthService socialAuthService;
    @Autowired MockVerificationProvider mockProvider;
    @Autowired UserRepository userRepository;
    @Autowired LocalCredentialRepository localCredentialRepository;

    private String reference(String name, LocalDate birth, Gender gender) {
        return mockProvider.startVerification(name, birth, gender, "01000000000");
    }

    @Test
    void 신규_로컬가입은_사용자와_자격증명을_만든다() {
        String ref = reference("홍길동", LocalDate.of(1990, 1, 1), Gender.M);

        AuthenticatedUser user = signupService.signupLocal(ref, "hong", "password123");

        assertThat(user.userId()).isNotNull();
        assertThat(user.name()).isEqualTo("홍길동");
        assertThat(userRepository.findById(user.userId())).isPresent();
        assertThat(localCredentialRepository.existsById(user.userId())).isTrue();
    }

    @Test
    void 비밀번호는_평문이_아니라_해시로_저장된다() {
        String ref = reference("김해시", LocalDate.of(1991, 2, 2), Gender.M);

        AuthenticatedUser user = signupService.signupLocal(ref, "kim", "password123");

        LocalCredential credential = localCredentialRepository.findById(user.userId()).orElseThrow();
        assertThat(credential.getPasswordHash()).isNotEqualTo("password123");
        assertThat(credential.getPasswordHash()).startsWith("$2"); // BCrypt 접두어
    }

    @Test
    void 같은_DI로_로컬가입을_또_하면_거부된다() {
        // 같은 사람정보 → 같은 DI. 첫 가입 후 재가입 시도.
        signupService.signupLocal(reference("이중복", LocalDate.of(1988, 8, 8), Gender.F), "lee1", "password123");

        assertThatThrownBy(() ->
                signupService.signupLocal(reference("이중복", LocalDate.of(1988, 8, 8), Gender.F), "lee2", "password123"))
                .isInstanceOf(LocalCredentialAlreadyExistsException.class);
    }

    @Test
    void 이미_쓰는_아이디로는_가입할_수_없다() {
        signupService.signupLocal(reference("사용자갑", LocalDate.of(1990, 3, 3), Gender.M), "sameid", "password123");

        // 다른 사람(다른 DI)이 같은 아이디로 가입 시도.
        assertThatThrownBy(() ->
                signupService.signupLocal(reference("사용자을", LocalDate.of(1995, 4, 4), Gender.F), "sameid", "password123"))
                .isInstanceOf(DuplicateLoginIdException.class);
    }

    @Test
    void 소셜로만_있던_사용자에_로컬계정을_붙인다_계정통합() {
        // 먼저 소셜로 가입(같은 DI의 사용자 생성).
        String name = "통합대상";
        LocalDate birth = LocalDate.of(1993, 6, 6);
        socialAuthService.linkOrRegister(SocialProvider.KAKAO, "kakao-999",
                mockProvider.startVerification(name, birth, Gender.M, "01000000000"));

        long usersBefore = userRepository.count();

        // 같은 사람이 로컬 가입 → 새 사용자 생성 없이 기존 사용자에 자격증명만 추가.
        AuthenticatedUser user = signupService.signupLocal(reference(name, birth, Gender.M), "merge", "password123");

        assertThat(userRepository.count()).isEqualTo(usersBefore); // 사용자 수 그대로
        assertThat(localCredentialRepository.existsById(user.userId())).isTrue();
    }
}
