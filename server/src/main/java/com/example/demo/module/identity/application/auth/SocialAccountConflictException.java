package com.example.demo.module.identity.application.auth;

/** 이미 다른 사용자에게 연결된 소셜 계정을 연결하려는 경우(중복연결 거부). → 409 Conflict */
public class SocialAccountConflictException extends RuntimeException {
    public SocialAccountConflictException(String message) {
        super(message);
    }
}
