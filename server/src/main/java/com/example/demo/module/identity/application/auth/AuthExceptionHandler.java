package com.example.demo.module.identity.application.auth;

import com.example.demo.module.identity.application.auth.oauth.SocialOAuthException;
import com.example.demo.module.identity.application.auth.token.InvalidAccessTokenException;
import com.example.demo.module.identity.application.auth.token.InvalidRefreshTokenException;
import com.example.demo.module.identity.application.verification.VerificationNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 인증 유스케이스 예외를 HTTP 상태로 매핑한다(데모용 단순 핸들러).
 * 응답 바디는 {@code {code, message}} 한 형태로 통일한다.
 */
@RestControllerAdvice
public class AuthExceptionHandler {

    public record ErrorResponse(String code, String message) {
    }

    /** 로그인 실패 → 401 */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(InvalidCredentialsException e) {
        return build(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", e.getMessage());
    }

    /** Access Token 무효/만료 → 401 */
    @ExceptionHandler(InvalidAccessTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAccessToken(InvalidAccessTokenException e) {
        return build(HttpStatus.UNAUTHORIZED, "INVALID_ACCESS_TOKEN", e.getMessage());
    }

    /** Refresh Token 무효/만료 → 401 */
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRefreshToken(InvalidRefreshTokenException e) {
        return build(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", e.getMessage());
    }

    /** 중복(아이디·로컬계정·소셜연결) → 409 */
    @ExceptionHandler(DuplicateLoginIdException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateLoginId(DuplicateLoginIdException e) {
        return build(HttpStatus.CONFLICT, "DUPLICATE_LOGIN_ID", e.getMessage());
    }

    @ExceptionHandler(LocalCredentialAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleLocalExists(LocalCredentialAlreadyExistsException e) {
        return build(HttpStatus.CONFLICT, "LOCAL_CREDENTIAL_EXISTS", e.getMessage());
    }

    @ExceptionHandler(SocialAccountConflictException.class)
    public ResponseEntity<ErrorResponse> handleSocialConflict(SocialAccountConflictException e) {
        return build(HttpStatus.CONFLICT, "SOCIAL_ACCOUNT_CONFLICT", e.getMessage());
    }

    /** 소셜 OAuth 처리 실패 → 400 */
    @ExceptionHandler(SocialOAuthException.class)
    public ResponseEntity<ErrorResponse> handleSocialOAuth(SocialOAuthException e) {
        return build(HttpStatus.BAD_REQUEST, "SOCIAL_OAUTH_FAILED", e.getMessage());
    }

    /** 본인인증 결과 없음/만료 → 404 */
    @ExceptionHandler(VerificationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleVerificationNotFound(VerificationNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, "VERIFICATION_NOT_FOUND", e.getMessage());
    }

    /** 요청 검증 실패 → 400 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage());
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }
}
