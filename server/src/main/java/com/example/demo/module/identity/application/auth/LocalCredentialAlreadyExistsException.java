package com.example.demo.module.identity.application.auth;

/** 이미 로컬 계정을 가진 사용자(같은 DI)가 다시 로컬 가입을 시도한 경우. → 409 Conflict */
public class LocalCredentialAlreadyExistsException extends RuntimeException {
    public LocalCredentialAlreadyExistsException(String message) {
        super(message);
    }
}
