package com.example.demo.module.identity.application.auth;

import com.example.demo.module.identity.domain.user.Gender;
import com.example.demo.module.identity.infrastructure.verification.MockVerificationProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 로컬 로그인 통합 테스트. */
@SpringBootTest
@Transactional
class LoginServiceTest {

    @Autowired LoginService loginService;
    @Autowired SignupService signupService;
    @Autowired MockVerificationProvider mockProvider;

    @BeforeEach
    void 가입해둔다() {
        String ref = mockProvider.startVerification("로그인자", LocalDate.of(1990, 1, 1), Gender.M, "01000000000");
        signupService.signupLocal(ref, "loginuser", "password123");
    }

    @Test
    void 올바른_자격증명이면_로그인된다() {
        AuthenticatedUser user = loginService.loginLocal("loginuser", "password123");

        assertThat(user.userId()).isNotNull();
        assertThat(user.name()).isEqualTo("로그인자");
    }

    @Test
    void 비밀번호가_틀리면_예외() {
        assertThatThrownBy(() -> loginService.loginLocal("loginuser", "wrongpass1"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void 없는_아이디면_예외() {
        assertThatThrownBy(() -> loginService.loginLocal("nobody", "password123"))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
