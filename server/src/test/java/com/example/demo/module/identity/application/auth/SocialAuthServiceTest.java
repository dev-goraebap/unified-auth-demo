package com.example.demo.module.identity.application.auth;

import com.example.demo.module.identity.domain.social.SocialProvider;
import com.example.demo.module.identity.domain.user.Gender;
import com.example.demo.module.identity.infrastructure.verification.MockVerificationProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 소셜 로그인/연결 통합 테스트. */
@SpringBootTest
@Transactional
class SocialAuthServiceTest {

    @Autowired SocialAuthService socialAuthService;
    @Autowired SignupService signupService;
    @Autowired MockVerificationProvider mockProvider;

    private String reference(String name, LocalDate birth, Gender gender) {
        return mockProvider.startVerification(name, birth, gender, "01000000000");
    }

    @Test
    void 연결안된_소셜로그인은_본인인증을_요구한다() {
        SocialLoginResult result = socialAuthService.login(SocialProvider.KAKAO, "kakao-unknown");

        assertThat(result.status()).isEqualTo(SocialLoginResult.Status.VERIFICATION_REQUIRED);
        assertThat(result.user()).isNull();
    }

    @Test
    void 처음_보는_사람은_소셜연결로_신규가입된다_CREATED() {
        SocialLinkResult link = socialAuthService.linkOrRegister(
                SocialProvider.NAVER, "naver-new", reference("소셜신규", LocalDate.of(1994, 4, 4), Gender.F));

        assertThat(link.outcome()).isEqualTo(SocialLinkResult.Outcome.CREATED);

        // 연결 후엔 바로 로그인된다.
        SocialLoginResult login = socialAuthService.login(SocialProvider.NAVER, "naver-new");
        assertThat(login.status()).isEqualTo(SocialLoginResult.Status.AUTHENTICATED);
        assertThat(login.user().userId()).isEqualTo(link.user().userId());
    }

    @Test
    void 기존_사용자_DI면_소셜이_그_계정에_통합된다_MERGED() {
        String name = "기가입자";
        LocalDate birth = LocalDate.of(1990, 9, 9);
        // 먼저 로컬로 가입.
        AuthenticatedUser local = signupService.signupLocal(reference(name, birth, Gender.M), "existing", "password123");

        // 같은 사람이 소셜 연결 → 새 사용자 없이 기존 계정에 통합.
        SocialLinkResult link = socialAuthService.linkOrRegister(
                SocialProvider.GOOGLE, "google-1", reference(name, birth, Gender.M));

        assertThat(link.outcome()).isEqualTo(SocialLinkResult.Outcome.MERGED);
        assertThat(link.user().userId()).isEqualTo(local.userId());
    }

    @Test
    void 같은_사용자가_같은_소셜을_다시_연결하면_멱등이다_ALREADY_LINKED() {
        String name = "재연결자";
        LocalDate birth = LocalDate.of(1996, 6, 6);
        socialAuthService.linkOrRegister(SocialProvider.KAKAO, "kakao-dup", reference(name, birth, Gender.F));

        SocialLinkResult again = socialAuthService.linkOrRegister(
                SocialProvider.KAKAO, "kakao-dup", reference(name, birth, Gender.F));

        assertThat(again.outcome()).isEqualTo(SocialLinkResult.Outcome.ALREADY_LINKED);
    }

    @Test
    void 이미_다른_사용자에게_연결된_소셜은_거부된다() {
        // A가 소셜 kakao-shared 연결.
        socialAuthService.linkOrRegister(SocialProvider.KAKAO, "kakao-shared",
                reference("사용자에이", LocalDate.of(1980, 1, 1), Gender.M));

        // 다른 사람 B(다른 DI)가 같은 소셜 계정을 연결 시도 → 거부.
        assertThatThrownBy(() -> socialAuthService.linkOrRegister(SocialProvider.KAKAO, "kakao-shared",
                reference("사용자비", LocalDate.of(1985, 5, 5), Gender.F)))
                .isInstanceOf(SocialAccountConflictException.class);
    }
}
