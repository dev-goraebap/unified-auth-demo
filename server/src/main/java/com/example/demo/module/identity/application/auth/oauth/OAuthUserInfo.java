package com.example.demo.module.identity.application.auth.oauth;

/**
 * 소셜 제공자가 준 사용자 정보. 핵심은 {@code providerUserId}(카카오 id / 구글 sub) —
 * 이 값이 우리 소셜 계정의 자연키가 된다. email·name은 참고용(있을 수도 없을 수도).
 */
public record OAuthUserInfo(String providerUserId, String email, String name) {
}
