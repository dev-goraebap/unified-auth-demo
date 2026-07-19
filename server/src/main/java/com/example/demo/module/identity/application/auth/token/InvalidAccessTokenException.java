package com.example.demo.module.identity.application.auth.token;

/** Access Token(JWT)이 위조·손상됐거나 만료된 경우. → 401 Unauthorized */
public class InvalidAccessTokenException extends RuntimeException {
    public InvalidAccessTokenException(String message) {
        super(message);
    }
}
