package com.example.demo.module.identity.application.auth;

import java.util.UUID;

/**
 * 인증에 성공한 사용자 신원(유스케이스 결과). 가입·로컬 로그인·소셜 로그인이 공통으로 반환한다.
 * <p>
 * 슬라이스 2는 여기까지(누가 인증되었는가)만 책임진다. 이 신원을 JWT Access Token +
 * Refresh Token으로 감싸 발급하는 일은 슬라이스 3(ADR-0006)에서 이 결과를 소비해 처리한다.
 */
public record AuthenticatedUser(UUID userId, String name) {
}
