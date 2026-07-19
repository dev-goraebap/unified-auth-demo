package com.example.demo.module.identity.application.auth.token;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** JWT(HS256) 발급·검증 단위 테스트. now를 주입해 만료를 결정적으로 검증한다. */
@SpringBootTest
class JwtProviderTest {

    @Autowired JwtProvider jwtProvider;

    @Test
    void 발급한_토큰을_다시_파싱하면_userId가_나온다() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-19T00:00:00Z");

        JwtProvider.IssuedJwt issued = jwtProvider.issue(userId, now);

        assertThat(issued.token().split("\\.")).hasSize(3); // header.payload.signature
        assertThat(jwtProvider.parseUserId(issued.token(), now.plusSeconds(60))).isEqualTo(userId);
    }

    @Test
    void 만료된_토큰은_거부된다() {
        Instant now = Instant.parse("2026-07-19T00:00:00Z");
        JwtProvider.IssuedJwt issued = jwtProvider.issue(UUID.randomUUID(), now);

        // access-ttl(15분)을 넘긴 시점에 검증.
        Instant afterExpiry = now.plus(Duration.ofMinutes(16));
        assertThatThrownBy(() -> jwtProvider.parseUserId(issued.token(), afterExpiry))
                .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void 서명이_변조된_토큰은_거부된다() {
        Instant now = Instant.parse("2026-07-19T00:00:00Z");
        String token = jwtProvider.issue(UUID.randomUUID(), now).token();

        // 서명부(마지막 '.' 이후) 첫 글자를 바꿔 위조한다.
        // 마지막 글자는 base64url 패딩 비트라 A↔B로 바꿔도 디코딩 시 무시돼 실제 바이트가 안 바뀔 수 있다
        // (결정적이지 않음). 첫 글자는 6비트 전부가 유효해 바꾸면 서명 바이트가 반드시 달라진다.
        int sigStart = token.lastIndexOf('.') + 1;
        char first = token.charAt(sigStart);
        String tampered = token.substring(0, sigStart) + (first == 'A' ? 'B' : 'A') + token.substring(sigStart + 1);

        assertThatThrownBy(() -> jwtProvider.parseUserId(tampered, now.plusSeconds(60)))
                .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void 형식이_틀린_토큰은_거부된다() {
        assertThatThrownBy(() -> jwtProvider.parseUserId("not-a-jwt", Instant.now()))
                .isInstanceOf(InvalidAccessTokenException.class);
    }
}
