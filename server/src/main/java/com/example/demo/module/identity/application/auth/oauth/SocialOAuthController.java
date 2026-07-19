package com.example.demo.module.identity.application.auth.oauth;

import com.example.demo.module.identity.application.auth.AuthResponse;
import com.example.demo.module.identity.application.auth.AuthTokenResponder;
import com.example.demo.module.identity.application.auth.AuthenticatedUser;
import com.example.demo.module.identity.application.auth.SocialCompleteResult;
import com.example.demo.module.identity.application.auth.token.BearerTokenAuthenticator;
import com.example.demo.module.identity.application.auth.token.TokenService;
import com.example.demo.module.identity.domain.social.SocialProvider;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * 실제 소셜 OAuth(카카오·구글) 엔드포인트. 프론트 흐름:
 * <pre>
 * ① GET  /{provider}/authorize → 인가 URL 받아 브라우저를 제공자로 리다이렉트
 * ② 제공자 콜백 http://localhost:4200/auth/callback/{provider}?code&state → 프론트가 code 수신
 * ③ POST /callback {provider, code, state}
 *      → 이미 연결: AUTHENTICATED(토큰 발급)
 *      → 미연결: VERIFICATION_REQUIRED(+ticket)
 * ④ 미연결 처리:
 *      - 비로그인: PASS 본인인증 후 POST /complete {ticket, reference}
 *          → 기존 회원(DI 일치): AUTHENTICATED(토큰 발급, 소셜 연결 완료)
 *          → 신규(DI 처음): SIGNUP_REQUIRED → POST /signup {ticket, reference, loginId, password}
 *            (소셜정보 + ID/PW로 회원가입. 일반 회원가입과 동일하게 ID/PW를 받는다.)
 *      - 로그인:   POST /link {ticket} (현재 계정에 바로 연결)
 * </pre>
 */
@RestController
@RequestMapping("/api/auth/social")
public class SocialOAuthController {

    private final SocialOAuthService socialOAuthService;
    private final TokenService tokenService;
    private final AuthTokenResponder responder;
    private final BearerTokenAuthenticator authenticator;

    public SocialOAuthController(SocialOAuthService socialOAuthService, TokenService tokenService,
                                 AuthTokenResponder responder, BearerTokenAuthenticator authenticator) {
        this.socialOAuthService = socialOAuthService;
        this.tokenService = tokenService;
        this.responder = responder;
        this.authenticator = authenticator;
    }

    @GetMapping("/{provider}/authorize")
    public AuthorizeResponse authorize(@PathVariable String provider) {
        return new AuthorizeResponse(socialOAuthService.authorizeUrl(parse(provider)));
    }

    @PostMapping("/callback")
    public CallbackResponse callback(@RequestBody CallbackRequest request, HttpServletResponse response) {
        request.validate();
        SocialOAuthService.CallbackResult result =
                socialOAuthService.handleCallback(parse(request.provider()), request.code(), request.state());
        if (result.status() == SocialOAuthService.CallbackResult.Status.AUTHENTICATED) {
            AuthResponse auth = responder.write(tokenService.issueFor(result.user(), Instant.now()), response);
            return new CallbackResponse("AUTHENTICATED", auth, null);
        }
        return new CallbackResponse("VERIFICATION_REQUIRED", null, result.ticket());
    }

    /**
     * (비로그인) PASS 본인인증까지 마치고 판정. 이 단계에서는 연결/가입하지 않는다.
     * <ul>
     *   <li>AUTHENTICATED — 이미 연결된 소셜(멱등) → 토큰 발급</li>
     *   <li>SIGNUP_REQUIRED — 신규 → ID/PW 입력 후 {@code /signup}</li>
     *   <li>LINK_REQUIRED — 기존 회원 → "○○ 님 계정에 연동?" 확인 후 {@code /link-confirm}</li>
     * </ul>
     */
    @PostMapping("/complete")
    public CompleteResponse complete(@RequestBody CompleteRequest request, HttpServletResponse response) {
        request.validate();
        SocialCompleteResult result = socialOAuthService.completeWithPass(request.ticket(), request.reference());
        return switch (result.status()) {
            case AUTHENTICATED -> new CompleteResponse("AUTHENTICATED",
                    responder.write(tokenService.issueFor(result.user(), Instant.now()), response), null);
            case LINK_REQUIRED -> new CompleteResponse("LINK_REQUIRED", null, result.name());
            case SIGNUP_REQUIRED -> new CompleteResponse("SIGNUP_REQUIRED", null, null);
        };
    }

    /** (비로그인) 확인 후 기존 회원 계정에 소셜을 연결하고 로그인. */
    @PostMapping("/link-confirm")
    public AuthResponse linkConfirm(@RequestBody CompleteRequest request, HttpServletResponse response) {
        request.validate();
        AuthenticatedUser user = socialOAuthService.confirmLink(request.ticket(), request.reference());
        return responder.write(tokenService.issueFor(user, Instant.now()), response);
    }

    /** (비로그인) 신규 소셜 회원가입 — 소셜정보 + ID/PW로 계정을 만든다. */
    @PostMapping("/signup")
    public AuthResponse signup(@RequestBody SocialSignupRequest request, HttpServletResponse response) {
        request.validate();
        AuthenticatedUser user = socialOAuthService.signupWithSocial(
                request.ticket(), request.reference(), request.loginId(), request.password());
        return responder.write(tokenService.issueFor(user, Instant.now()), response);
    }

    /** (로그인 상태) 현재 계정의 소셜 연동 해제. */
    @DeleteMapping("/{provider}")
    public void unlink(@PathVariable String provider,
                       @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        UUID userId = authenticator.authenticate(authorization, Instant.now());
        socialOAuthService.unlink(userId, parse(provider));
    }

    /** (로그인 상태) 소셜을 현재 계정에 연결. */
    @PostMapping("/link")
    public AuthResponse link(@RequestBody LinkRequest request,
                             @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                             HttpServletResponse response) {
        request.validate();
        UUID userId = authenticator.authenticate(authorization, Instant.now());
        AuthenticatedUser user = socialOAuthService.linkToUser(userId, request.ticket());
        return responder.write(tokenService.issueFor(user, Instant.now()), response);
    }

    private SocialProvider parse(String provider) {
        try {
            return SocialProvider.valueOf(provider.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("알 수 없는 소셜 제공자입니다: " + provider);
        }
    }

    public record AuthorizeResponse(String authorizeUrl) {
    }

    public record CallbackRequest(String provider, String code, String state) {
        void validate() {
            if (provider == null || provider.isBlank()) throw new IllegalArgumentException("provider는 필수입니다");
            if (code == null || code.isBlank()) throw new IllegalArgumentException("code는 필수입니다");
            if (state == null || state.isBlank()) throw new IllegalArgumentException("state는 필수입니다");
        }
    }

    /** status = AUTHENTICATED | VERIFICATION_REQUIRED. AUTHENTICATED면 user, 아니면 ticket. */
    public record CallbackResponse(String status, AuthResponse user, String ticket) {
    }

    public record CompleteRequest(String ticket, String reference) {
        void validate() {
            if (ticket == null || ticket.isBlank()) throw new IllegalArgumentException("ticket은 필수입니다");
            if (reference == null || reference.isBlank()) throw new IllegalArgumentException("reference는 필수입니다");
        }
    }

    /**
     * status = AUTHENTICATED | SIGNUP_REQUIRED | LINK_REQUIRED.
     * AUTHENTICATED면 user(로그인 완료), LINK_REQUIRED면 name(확인화면 표시용), SIGNUP_REQUIRED면 둘 다 null.
     */
    public record CompleteResponse(String status, AuthResponse user, String name) {
    }

    public record SocialSignupRequest(String ticket, String reference, String loginId, String password) {
        void validate() {
            if (ticket == null || ticket.isBlank()) throw new IllegalArgumentException("ticket은 필수입니다");
            if (reference == null || reference.isBlank()) throw new IllegalArgumentException("reference는 필수입니다");
            if (loginId == null || loginId.isBlank()) throw new IllegalArgumentException("loginId는 필수입니다");
            if (password == null || password.length() < 8) throw new IllegalArgumentException("password는 8자 이상이어야 합니다");
        }
    }

    public record LinkRequest(String ticket) {
        void validate() {
            if (ticket == null || ticket.isBlank()) throw new IllegalArgumentException("ticket은 필수입니다");
        }
    }
}
