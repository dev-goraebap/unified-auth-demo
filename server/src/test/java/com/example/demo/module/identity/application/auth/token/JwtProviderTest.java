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

        // 서명 마지막 글자를 바꿔 위조.
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');

        assertThatThrownBy(() -> jwtProvider.parseUserId(tampered, now.plusSeconds(60)))
                .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void 형식이_틀린_토큰은_거부된다() {
        assertThatThrownBy(() -> jwtProvider.parseUserId("not-a-jwt", Instant.now()))
                .isInstanceOf(InvalidAccessTokenException.class);
    }
}
