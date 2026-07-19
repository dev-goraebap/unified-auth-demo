package com.example.demo.module.identity.application.auth.oauth;

import com.example.demo.module.identity.application.auth.AuthResponse;
import com.example.demo.module.identity.application.auth.AuthTokenResponder;
import com.example.demo.module.identity.application.auth.AuthenticatedUser;
import com.example.demo.module.identity.application.auth.token.BearerTokenAuthenticator;
import com.example.demo.module.identity.application.auth.token.TokenService;
import com.example.demo.module.identity.domain.social.SocialProvider;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
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
 *      - 비로그인: PASS 본인인증 후 POST /complete {ticket, reference} (DI로 연결/가입)
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

    /** (비로그인) PASS 본인인증까지 마치고 소셜 연결/가입. */
    @PostMapping("/complete")
    public AuthResponse complete(@RequestBody CompleteRequest request, HttpServletResponse response) {
        request.validate();
        AuthenticatedUser user = socialOAuthService.completeWithPass(request.ticket(), request.reference());
        return responder.write(tokenService.issueFor(user, Instant.now()), response);
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

    public record LinkRequest(String ticket) {
        void validate() {
            if (ticket == null || ticket.isBlank()) throw new IllegalArgumentException("ticket은 필수입니다");
        }
    }
}
