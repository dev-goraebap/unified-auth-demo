package com.example.demo.module.identity.application.auth;

import com.example.demo.module.identity.domain.user.Gender;
import com.example.demo.module.identity.infrastructure.verification.MockVerificationProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 세션 토큰(ADR-0006) 엔드투엔드 통합 테스트. 실제 postgres + MockMvc로
 * signup→me→refresh(회전)→logout 전체를 검증한다. {@code @Transactional}로 롤백된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthTokenEndpointTest {

    @Autowired MockMvc mvc;
    @Autowired MockVerificationProvider mockProvider;
    @Autowired ObjectMapper objectMapper;

    @Test
    void 가입하면_토큰이_발급되고_me_refresh_logout이_동작한다() throws Exception {
        String reference = mockProvider.startVerification("토큰이", LocalDate.of(1990, 1, 1), Gender.M, "01000000000");

        // ① 가입 → Access Token(바디) + RFT(httpOnly 쿠키)
        MvcResult signup = mvc.perform(post("/api/auth/signup").contentType(APPLICATION_JSON)
                        .content(json(Map.of("reference", reference, "loginId", "tokenuser", "password", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.name").value("토큰이"))
                .andReturn();

        Cookie refreshCookie = signup.getResponse().getCookie("refresh_token");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.isHttpOnly()).isTrue();
        assertThat(refreshCookie.getValue()).isNotBlank();
        String accessToken = field(signup, "accessToken");

        // ② /me — Access Token 검증
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").isNotEmpty());

        // 토큰 없으면 401
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());

        // ③ refresh — RFT 회전(새 쿠키·새 access)
        MvcResult refreshed = mvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        Cookie rotated = refreshed.getResponse().getCookie("refresh_token");
        assertThat(rotated).isNotNull();
        assertThat(rotated.getValue()).isNotEqualTo(refreshCookie.getValue()); // 회전됨

        // ④ 회전된 이전 RFT는 폐기 → 재사용 시 401
        mvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized());

        // ⑤ 로그아웃 → 쿠키 만료
        MvcResult loggedOut = mvc.perform(post("/api/auth/logout").cookie(rotated))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(loggedOut.getResponse().getCookie("refresh_token").getMaxAge()).isZero();

        // ⑥ 로그아웃 후 RFT는 무효 → 401
        mvc.perform(post("/api/auth/refresh").cookie(rotated))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 로그인_실패는_401을_준다() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content(json(Map.of("loginId", "nobody", "password", "whatever12"))))
                .andExpect(status().isUnauthorized());
    }

    private String json(Map<String, Object> body) {
        return objectMapper.writeValueAsString(body);
    }

    private String field(MvcResult result, String name) throws Exception {
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return (String) body.get(name);
    }
}
