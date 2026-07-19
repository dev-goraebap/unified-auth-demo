package com.example.demo.module.identity.application.auth;

/**
 * 로컬 로그인 실패(아이디 없음 또는 비밀번호 불일치). → 401 Unauthorized
 * 아이디 존재 여부를 노출하지 않도록 두 경우 모두 동일 메시지를 쓴다(사용자 열거 방지).
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
