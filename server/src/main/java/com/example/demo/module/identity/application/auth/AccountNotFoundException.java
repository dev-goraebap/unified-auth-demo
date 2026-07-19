package com.example.demo.module.identity.application.auth;

/** 아이디 찾기·비밀번호 재설정 시 본인인증(DI)에 해당하는 계정이 없을 때. */
public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String message) {
        super(message);
    }
}
