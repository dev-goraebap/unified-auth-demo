package com.example.demo.module.identity.application.auth.token;

/** Refresh Token이 없거나 이미 폐기·만료된 경우(재발급 불가). → 401 Unauthorized */
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
