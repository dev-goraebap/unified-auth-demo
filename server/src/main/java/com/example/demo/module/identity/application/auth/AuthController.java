package com.example.demo.module.identity.application.auth;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로컬(ID/PW) 가입·로그인 엔드포인트. 소셜은 {@link SocialAuthController}가 담당한다.
 * (ADR-0001: 컨트롤러는 해당 기능의 application 패키지에 둔다.)
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final SignupService signupService;
    private final LoginService loginService;

    public AuthController(SignupService signupService, LoginService loginService) {
        this.signupService = signupService;
        this.loginService = loginService;
    }

    /** 로컬 가입(PASS 우선). reference는 본인인증 완료 후 프론트가 전달한 식별자. */
    @PostMapping("/signup")
    public AuthResponse signup(@RequestBody SignupRequest request) {
        request.validate();
        AuthenticatedUser user = signupService.signupLocal(
                request.reference(), request.loginId(), request.password());
        return AuthResponse.from(user);
    }

    /** 로컬 로그인. */
    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        request.validate();
        AuthenticatedUser user = loginService.loginLocal(request.loginId(), request.password());
        return AuthResponse.from(user);
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
}
