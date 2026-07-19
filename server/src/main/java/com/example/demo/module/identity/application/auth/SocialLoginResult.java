package com.example.demo.module.identity.application.auth;

/**
 * 소셜 로그인 시도 결과.
 * <ul>
 *   <li>{@code AUTHENTICATED} — (provider, providerUserId)가 이미 연결돼 바로 로그인됨.</li>
 *   <li>{@code VERIFICATION_REQUIRED} — 아직 연결 안 됨. 프론트는 본인인증(PASS) 후
 *       연결/가입(확인화면)으로 유도한다. 이 경우 {@link #user()}는 null.</li>
 * </ul>
 */
public record SocialLoginResult(Status status, AuthenticatedUser user) {

    public enum Status { AUTHENTICATED, VERIFICATION_REQUIRED }

    public static SocialLoginResult authenticated(AuthenticatedUser user) {
        return new SocialLoginResult(Status.AUTHENTICATED, user);
    }

    public static SocialLoginResult verificationRequired() {
        return new SocialLoginResult(Status.VERIFICATION_REQUIRED, null);
    }
}
