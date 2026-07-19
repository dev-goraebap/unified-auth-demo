package com.example.demo.module.identity.application.auth;

import com.example.demo.module.identity.application.auth.token.InvalidAccessTokenException;
import com.example.demo.module.identity.application.auth.token.InvalidRefreshTokenException;
import com.example.demo.module.identity.application.auth.token.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * 로컬(ID/PW) 가입·로그인 + 세션(토큰) 엔드포인트. 소셜은 {@link SocialAuthController}가 담당한다.
 * (ADR-0001: 컨트롤러는 해당 기능의 application 패키지에 둔다.)
 * <p>
 * 성공 응답: Access Token(JWT)은 바디, Refresh Token은 httpOnly 쿠키(ADR-0006).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final SignupService signupService;
    private final LoginService loginService;
    private final TokenService tokenService;
    private final AuthTokenResponder responder;

    public AuthController(SignupService signupService, LoginService loginService,
                          TokenService tokenService, AuthTokenResponder responder) {
        this.signupService = signupService;
        this.loginService = loginService;
        this.tokenService = tokenService;
        this.responder = responder;
    }

    /** 로컬 가입(PASS 우선). reference는 본인인증 완료 후 프론트가 전달한 식별자. */
    @PostMapping("/signup")
    public AuthResponse signup(@RequestBody SignupRequest request, HttpServletResponse response) {
        request.validate();
        AuthenticatedUser user = signupService.signupLocal(
                request.reference(), request.loginId(), request.password());
        return responder.write(tokenService.issueFor(user, Instant.now()), response);
    }

    /** 로컬 로그인. */
    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request, HttpServletResponse response) {
        request.validate();
        AuthenticatedUser user = loginService.loginLocal(request.loginId(), request.password());
        return responder.write(tokenService.issueFor(user, Instant.now()), response);
    }

    /** Access Token 재발급 — RFT 쿠키를 회전한다. */
    @PostMapping("/refresh")
    public AuthResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        String rawRefreshToken = responder.readRefreshToken(request)
                .orElseThrow(() -> new InvalidRefreshTokenException("refresh token 쿠키가 없습니다. 다시 로그인해 주세요."));
        return responder.write(tokenService.refresh(rawRefreshToken, Instant.now()), response);
    }

    /** 로그아웃 — RFT를 폐기하고 쿠키를 만료시킨다(멱등). */
    @PostMapping("/logout")
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        responder.readRefreshToken(request).ifPresent(raw -> tokenService.logout(raw, Instant.now()));
        responder.clearRefreshCookie(response);
    }

    /** 현재 사용자 — Access Token 검증 데모(Authorization: Bearer). */
    @GetMapping("/me")
    public MeResponse me(@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        UUID userId = tokenService.authenticate(bearerToken(authorization), Instant.now());
        return new MeResponse(userId);
    }

    private static String bearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new InvalidAccessTokenException("Authorization: Bearer 토큰이 필요합니다");
        }
        return authorizationHeader.substring("Bearer ".length());
    }

    /**
     * @param reference 본인인증 식별자(필수)
     * @param loginId   로그인 아이디(필수)
     * @param password  비밀번호(필수, 8자 이상)
     */
    public record SignupRequest(String reference, String loginId, String password) {
        void validate() {
            if (reference == null || reference.isBlank()) throw new IllegalArgumentException("reference는 필수입니다");
            if (loginId == null || loginId.isBlank()) throw new IllegalArgumentException("loginId는 필수입니다");
            if (password == null || password.length() < 8) throw new IllegalArgumentException("password는 8자 이상이어야 합니다");
        }
    }

    public record LoginRequest(String loginId, String password) {
        void validate() {
            if (loginId == null || loginId.isBlank()) throw new IllegalArgumentException("loginId는 필수입니다");
            if (password == null || password.isBlank()) throw new IllegalArgumentException("password는 필수입니다");
        }
    }

    public record MeResponse(UUID userId) {
    }
}
