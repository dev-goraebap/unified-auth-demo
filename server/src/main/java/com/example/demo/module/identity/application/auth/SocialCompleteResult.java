package com.example.demo.module.identity.application.auth;

/**
 * 소셜 로그인에서 PASS 본인인증까지 마친 뒤의 판정 결과. <b>이 단계에서는 아직 연결하지 않는다</b>
 * — 실제 연결은 사용자 확인(프론트) 후 별도 호출에서 이뤄진다.
 * <ul>
 *   <li>{@code AUTHENTICATED} — 이미 이 사용자에게 연결된 소셜(멱등). 바로 로그인.</li>
 *   <li>{@code SIGNUP_REQUIRED} — DI가 처음 보는 사람. 일반 회원가입과 동일하게 ID/PW를 받아
 *       소셜정보 + ID/PW로 계정을 만든다.</li>
 *   <li>{@code LINK_REQUIRED} — DI가 기존 회원과 일치. "○○ 님 계정에 연동하시겠습니까?"를
 *       물어본 뒤, 확인 시 소셜을 그 계정에 연결하고 로그인한다. {@code name}은 확인화면 표시용.</li>
 * </ul>
 */
public record SocialCompleteResult(Status status, AuthenticatedUser user, String name) {

    public enum Status { AUTHENTICATED, SIGNUP_REQUIRED, LINK_REQUIRED }

    public static SocialCompleteResult authenticated(AuthenticatedUser user) {
        return new SocialCompleteResult(Status.AUTHENTICATED, user, null);
    }

    public static SocialCompleteResult signupRequired() {
        return new SocialCompleteResult(Status.SIGNUP_REQUIRED, null, null);
    }

    public static SocialCompleteResult linkRequired(String name) {
        return new SocialCompleteResult(Status.LINK_REQUIRED, null, name);
    }
}
