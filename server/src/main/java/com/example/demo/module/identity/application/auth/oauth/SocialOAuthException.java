package com.example.demo.module.identity.application.auth.oauth;

/** 소셜 OAuth 처리 실패(state 불일치·code 교환 실패·티켓 만료 등). → 400 */
public class SocialOAuthException extends RuntimeException {
    public SocialOAuthException(String message) {
        super(message);
    }

    public SocialOAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
