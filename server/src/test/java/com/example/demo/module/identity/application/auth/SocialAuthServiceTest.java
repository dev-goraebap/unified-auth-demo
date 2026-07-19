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

/** 소셜 로그인/연결/가입 통합 테스트. 모든 계정은 소셜정보 + ID/PW 를 갖는다. */
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
    void 계정없는_사람은_PASS_후에도_회원가입이_필요하다_SIGNUP_REQUIRED() {
        // 처음 보는 DI → 아직 계정을 만들지 않고 ID/PW 입력을 요구한다.
        SocialCompleteResult result = socialAuthService.completeWithPass(
                SocialProvider.NAVER, "naver-new", reference("소셜신규", LocalDate.of(1994, 4, 4), Gender.F));

        assertThat(result.status()).isEqualTo(SocialCompleteResult.Status.SIGNUP_REQUIRED);
        assertThat(result.user()).isNull();
        // 아직 계정이 없으므로 소셜 로그인도 여전히 미연결.
        assertThat(socialAuthService.login(SocialProvider.NAVER, "naver-new").status())
                .isEqualTo(SocialLoginResult.Status.VERIFICATION_REQUIRED);
    }

    @Test
    void 소셜_회원가입은_소셜정보와_IDPW로_계정을_만든다() {
        AuthenticatedUser user = socialAuthService.registerWithSocial(
                SocialProvider.NAVER, "naver-new",
                reference("소셜신규", LocalDate.of(1994, 4, 4), Gender.F), "socialnew", "password123");

        assertThat(user.userId()).isNotNull();
        // 가입 후엔 소셜로 바로 로그인된다.
        SocialLoginResult login = socialAuthService.login(SocialProvider.NAVER, "naver-new");
        assertThat(login.status()).isEqualTo(SocialLoginResult.Status.AUTHENTICATED);
        assertThat(login.user().userId()).isEqualTo(user.userId());
    }

    @Test
    void 기존_회원_DI면_연동확인을_요구하고_확인하면_연결된다_LINK_REQUIRED() {
        String name = "기가입자";
        LocalDate birth = LocalDate.of(1990, 9, 9);
        // 먼저 로컬로 가입.
        AuthenticatedUser local = signupService.signupLocal(reference(name, birth, Gender.M), "existing", "password123");

        // 같은 사람이 소셜 로그인 → 아직 연결하지 않고 "○○ 님 계정에 연동?" 확인 요구.
        SocialCompleteResult result = socialAuthService.completeWithPass(
                SocialProvider.GOOGLE, "google-1", reference(name, birth, Gender.M));
        assertThat(result.status()).isEqualTo(SocialCompleteResult.Status.LINK_REQUIRED);
        assertThat(result.name()).isEqualTo(name);
        // 이 시점엔 아직 미연결.
        assertThat(socialAuthService.login(SocialProvider.GOOGLE, "google-1").status())
                .isEqualTo(SocialLoginResult.Status.VERIFICATION_REQUIRED);

        // 사용자가 확인 → 기존 계정에 연결 + 로그인.
        AuthenticatedUser linked = socialAuthService.confirmLink(
                SocialProvider.GOOGLE, "google-1", reference(name, birth, Gender.M));
        assertThat(linked.userId()).isEqualTo(local.userId());
        assertThat(socialAuthService.login(SocialProvider.GOOGLE, "google-1").status())
                .isEqualTo(SocialLoginResult.Status.AUTHENTICATED);
    }

    @Test
    void 소셜_연동을_해제할_수_있다() {
        String name = "해제대상";
        LocalDate birth = LocalDate.of(1992, 2, 2);
        AuthenticatedUser user = socialAuthService.registerWithSocial(
                SocialProvider.KAKAO, "kakao-unlink", reference(name, birth, Gender.M), "unlinkid", "password123");
        assertThat(socialAuthService.login(SocialProvider.KAKAO, "kakao-unlink").status())
                .isEqualTo(SocialLoginResult.Status.AUTHENTICATED);

        socialAuthService.unlink(user.userId(), SocialProvider.KAKAO);

        // 해제 후엔 다시 미연결.
        assertThat(socialAuthService.login(SocialProvider.KAKAO, "kakao-unlink").status())
                .isEqualTo(SocialLoginResult.Status.VERIFICATION_REQUIRED);
    }

    @Test
    void 같은_회원이_같은_소셜을_다시_거치면_멱등이다() {
        String name = "재연결자";
        LocalDate birth = LocalDate.of(1996, 6, 6);
        socialAuthService.registerWithSocial(SocialProvider.KAKAO, "kakao-dup",
                reference(name, birth, Gender.F), "reuser", "password123");

        // 이미 연결된 소셜을 다시 completeWithPass → 로그인(멱등).
        SocialCompleteResult again = socialAuthService.completeWithPass(
                SocialProvider.KAKAO, "kakao-dup", reference(name, birth, Gender.F));

        assertThat(again.status()).isEqualTo(SocialCompleteResult.Status.AUTHENTICATED);
    }

    @Test
    void 이미_다른_사용자에게_연결된_소셜은_거부된다() {
        // A가 소셜 kakao-shared 로 가입.
        socialAuthService.registerWithSocial(SocialProvider.KAKAO, "kakao-shared",
                reference("사용자에이", LocalDate.of(1980, 1, 1), Gender.M), "usera", "password123");

        // 다른 사람 B(다른 DI)가 같은 소셜 계정으로 완료 시도 → 거부.
        assertThatThrownBy(() -> socialAuthService.completeWithPass(SocialProvider.KAKAO, "kakao-shared",
                reference("사용자비", LocalDate.of(1985, 5, 5), Gender.F)))
                .isInstanceOf(SocialAccountConflictException.class);
    }
}
