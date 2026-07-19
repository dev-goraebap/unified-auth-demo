package com.example.demo.module.identity.application.verification;

/**
 * reference에 해당하는 본인인증 결과가 없거나(존재하지 않음/용도 불일치) 만료된 경우.
 */
public class VerificationNotFoundException extends RuntimeException {

    public VerificationNotFoundException(String message) {
        super(message);
    }
}
