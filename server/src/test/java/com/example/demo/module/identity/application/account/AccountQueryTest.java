package com.example.demo.module.identity.application.account;

import com.example.demo.module.identity.application.auth.AuthenticatedUser;
import com.example.demo.module.identity.application.auth.SignupService;
import com.example.demo.module.identity.application.auth.SocialAuthService;
import com.example.demo.module.identity.application.auth.token.TokenService;
import com.example.demo.module.identity.domain.social.SocialProvider;
import com.example.demo.module.identity.domain.user.Gender;
import com.example.demo.module.identity.domain.user.UserRepository;
import com.example.demo.module.identity.infrastructure.verification.MockVerificationProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 내 계정 조회(jOOQ 동적조회) 엔드투엔드 테스트. 한 사람에 로컬+소셜 2개를 묶은 뒤
 * {@code GET /api/users/me/accounts}가 통합 뷰를 돌려주는지 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountQueryTest {

    @Autowired MockMvc mvc;
    @Autowired SignupService signupService;
    @Autowired SocialAuthService socialAuthService;
    @Autowired TokenService tokenService;
    @Autowired MockVerificationProvider mockProvider;
    @Autowired UserRepository userRepository;

    private String sameReference() {
        // 같은 사람정보 → 같은 DI → 같은 사용자에 통합.
        return mockProvider.startVerification("계정보유", LocalDate.of(1990, 1, 1), Gender.M, "01000000000");
    }

    @Test
    void 내_계정은_로컬과_소셜을_통합해서_보여준다() throws Exception {
        AuthenticatedUser user = signupService.signupLocal(sameReference(), "acctuser", "password123");
        socialAuthService.linkOrRegister(SocialProvider.KAKAO, "kakao-1", sameReference());
        socialAuthService.linkOrRegister(SocialProvider.GOOGLE, "google-1", sameReference());

        // jOOQ(원시 SQL)가 같은 트랜잭션의 JPA 변경을 보도록 flush.
        userRepository.flush();

        String accessToken = tokenService.issueFor(user, Instant.now()).accessToken();

        mvc.perform(get("/api/users/me/accounts").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("계정보유"))
                .andExpect(jsonPath("$.local.loginId").value("acctuser"))
                .andExpect(jsonPath("$.socials.length()").value(2))
                .andExpect(jsonPath("$.socials[0].provider").value("KAKAO"))
                .andExpect(jsonPath("$.socials[1].provider").value("GOOGLE"));
    }

    @Test
    void 토큰_없이_조회하면_401() throws Exception {
        mvc.perform(get("/api/users/me/accounts")).andExpect(status().isUnauthorized());
    }
}
