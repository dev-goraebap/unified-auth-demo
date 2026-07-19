package com.example.demo.module.identity.application.auth;

/** 가입 시 이미 사용 중인 로그인 아이디를 요청한 경우. → 409 Conflict */
public class DuplicateLoginIdException extends RuntimeException {
    public DuplicateLoginIdException(String message) {
        super(message);
    }
}
