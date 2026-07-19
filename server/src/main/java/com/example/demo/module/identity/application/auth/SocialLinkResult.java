package com.example.demo.module.identity.application.auth;

/**
 * 소셜 계정 연결/가입 결과. {@link Outcome}로 프론트가 어떤 흐름이었는지 안다.
 * <ul>
 *   <li>{@code CREATED} — DI가 처음 보는 사람이라 신규 사용자를 만들고 소셜을 연결(소셜 신규가입).</li>
 *   <li>{@code MERGED} — DI가 기존 사용자와 일치해 그 계정에 소셜을 연결(계정 통합, 확인화면 대상).</li>
 *   <li>{@code ALREADY_LINKED} — 이미 이 사용자에게 연결돼 있던 소셜(멱등 로그인).</li>
 * </ul>
 */
public record SocialLinkResult(AuthenticatedUser user, Outcome outcome) {

    public enum Outcome { CREATED, MERGED, ALREADY_LINKED }
}
