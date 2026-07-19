package com.example.demo.module.identity.application.auth;

import com.example.demo.module.identity.application.auth.token.TokenService;
import com.example.demo.module.identity.domain.social.SocialProvider;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * 소셜(카카오·네이버·구글) 로그인/연결 엔드포인트.
 * <p>
 * 데모라 OAuth 대신 (provider, providerUserId)를 직접 받는다. 프론트 흐름:
 * <pre>
 * ① POST /login → 이미 연결됐으면 AUTHENTICATED(토큰 발급), 아니면 VERIFICATION_REQUIRED
 * ② VERIFICATION_REQUIRED면 본인인증(PASS) 후 확인화면 → POST /link 로 연결/가입(토큰 발급)
 * </pre>
 * 인증 성공 시 로컬과 동일하게 Access Token(바디)+RFT(쿠키)를 발급한다(ADR-0006).
 */
@RestController
@RequestMapping("/api/auth/social")
public class SocialAuthController {

    private final SocialAuthService socialAuthService;
    private final TokenService tokenService;
    private final AuthTokenResponder responder;

    public SocialAuthController(SocialAuthService socialAuthService,
                                TokenService tokenService, AuthTokenResponder responder) {
        this.socialAuthService = socialAuthService;
        this.tokenService = tokenService;
        this.responder = responder;
    }

    @PostMapping("/login")
    public SocialLoginResponse login(@RequestBody SocialLoginRequest request, HttpServletResponse response) {
        request.validate();
        SocialLoginResult result = socialAuthService.login(request.provider(), request.providerUserId());
        if (result.status() != SocialLoginResult.Status.AUTHENTICATED) {
            return new SocialLoginResponse(result.status().name(), null);
        }
        AuthResponse auth = responder.write(tokenService.issueFor(result.user(), Instant.now()), response);
        return new SocialLoginResponse(result.status().name(), auth);
    }

    @PostMapping("/link")
    public SocialLinkResponse link(@RequestBody SocialLinkRequest request, HttpServletResponse response) {
        request.validate();
        SocialLinkResult result = socialAuthService.linkOrRegister(
                request.provider(), request.providerUserId(), request.reference());
        AuthResponse auth = responder.write(tokenService.issueFor(result.user(), Instant.now()), response);
        return new SocialLinkResponse(auth, result.outcome().name());
    }

    public record SocialLoginRequest(SocialProvider provider, String providerUserId) {
        void validate() {
            if (provider == null) throw new IllegalArgumentException("provider는 필수입니다");
            if (providerUserId == null || providerUserId.isBlank()) throw new IllegalArgumentException("providerUserId는 필수입니다");
        }
    }

    /** @param reference 소셜 인증 후 완료한 본인인증(PASS) 식별자 */
    public record SocialLinkRequest(SocialProvider provider, String providerUserId, String reference) {
        void validate() {
            if (provider == null) throw new IllegalArgumentException("provider는 필수입니다");
            if (providerUserId == null || providerUserId.isBlank()) throw new IllegalArgumentException("providerUserId는 필수입니다");
            if (reference == null || reference.isBlank()) throw new IllegalArgumentException("reference는 필수입니다");
        }
    }

    /** status = AUTHENTICATED | VERIFICATION_REQUIRED. user는 AUTHENTICATED일 때만 채워진다. */
    public record SocialLoginResponse(String status, AuthResponse user) {
    }

    /** outcome = CREATED | MERGED | ALREADY_LINKED */
    public record SocialLinkResponse(AuthResponse user, String outcome) {
    }
}
