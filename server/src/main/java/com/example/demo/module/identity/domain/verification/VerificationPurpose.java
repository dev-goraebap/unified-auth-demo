package com.example.demo.module.identity.domain.verification;

/** 임시데이터 용도. */
public enum VerificationPurpose {
    /** 소셜 로그인 리다이렉트 CSRF 방지용 state. */
    OAUTH_STATE,
    /** 본인인증 결과(DI/CI 등) 임시 보관 — 가입/연결 완료 전 익명 단계. */
    PASS_RESULT,
    /** 소셜 OAuth 콜백에서 얻은 providerUserId 임시 보관 — PASS/연결 완료 전. */
    SOCIAL_LINK_TICKET
}
