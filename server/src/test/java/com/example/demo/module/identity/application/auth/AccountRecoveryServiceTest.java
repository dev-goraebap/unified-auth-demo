package com.example.demo.module.identity.application.auth;

import com.example.demo.module.identity.domain.credential.LocalCredential;
import com.example.demo.module.identity.domain.credential.LocalCredentialRepository;
import com.example.demo.module.identity.domain.user.Gender;
import com.example.demo.module.identity.infrastructure.verification.MockVerificationProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 아이디 찾기/비밀번호 재설정(PASS 기반) 통합 테스트. */
@SpringBootTest
@Transactional
class AccountRecoveryServiceTest {

    @Autowired SignupService signupService;
    @Autowired AccountRecoveryService recoveryService;
    @Autowired LoginService loginService;
    @Autowired MockVerificationProvider mockProvider;
    @Autowired LocalCredentialRepository localCredentialRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private String reference(String name, LocalDate birth, Gender gender) {
        return mockProvider.startVerification(name, birth, gender, "01000000000");
    }

    @Test
    void 본인인증하면_아이디를_찾아준다() {
        String name = "아이디찾기";
        LocalDate birth = LocalDate.of(1990, 1, 1);
        signupService.signupLocal(reference(name, birth, Gender.M), "findme", "password123");

        String loginId = recoveryService.findLoginId(reference(name, birth, Gender.M));

        assertThat(loginId).isEqualTo("findme");
    }

    @Test
    void 가입된_계정이_없으면_아이디찾기는_실패한다() {
        assertThatThrownBy(() ->
                recoveryService.findLoginId(reference("없는사람", LocalDate.of(1970, 7, 7), Gender.F)))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void 본인인증하면_비밀번호를_재설정하고_새_비밀번호로_로그인된다() {
        String name = "비번변경";
        LocalDate birth = LocalDate.of(1991, 3, 3);
        signupService.signupLocal(reference(name, birth, Gender.M), "pwuser", "oldpassword1");

        recoveryService.resetPassword(reference(name, birth, Gender.M), "newpassword2");

        // 새 비밀번호로 로그인 성공.
        AuthenticatedUser user = loginService.loginLocal("pwuser", "newpassword2");
        assertThat(user).isNotNull();
        // 저장된 해시가 새 비밀번호 기준.
        LocalCredential cred = localCredentialRepository.findByLoginId("pwuser").orElseThrow();
        assertThat(passwordEncoder.matches("newpassword2", cred.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("oldpassword1", cred.getPasswordHash())).isFalse();
    }
}
