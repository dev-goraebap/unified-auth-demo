package com.example.demo.module.identity.application.auth.oauth;

import com.example.demo.module.identity.application.auth.AuthenticatedUser;
import com.example.demo.module.identity.application.auth.SocialAuthService;
import com.example.demo.module.identity.application.auth.SocialLoginResult;
import com.example.demo.module.identity.domain.social.SocialProvider;
import com.example.demo.module.identity.domain.verification.Verification;
import com.example.demo.module.identity.domain.verification.VerificationPurpose;
import com.example.demo.module.identity.domain.verification.VerificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * 소셜 OAuth 흐름 오케스트레이션. 실제 제공자 통신은 {@link SocialOAuthClient}에 맡기고,
 * 얻은 providerUserId를 <b>기존 검증된 계정 로직</b>({@link SocialAuthService})에 넘긴다.
 * <p>
 * CSRF 방지 state와 콜백 후 providerUserId를 {@code verification} 테이블에 잠깐 저장한다
 * (OAUTH_STATE / SOCIAL_LINK_TICKET, ADR-0005). 10분 후 만료·청소된다.
 */
@Service
public class SocialOAuthService {

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SocialOAuthClient oauthClient;
    private final VerificationRepository verificationRepository;
    private final SocialAuthService socialAuthService;
    private final ObjectMapper objectMapper;

    public SocialOAuthService(SocialOAuthClient oauthClient,
                              VerificationRepository verificationRepository,
                              SocialAuthService socialAuthService,
                              ObjectMapper objectMapper) {
        this.oauthClient = oauthClient;
        this.verificationRepository = verificationRepository;
        this.socialAuthService = socialAuthService;
        this.objectMapper = objectMapper;
    }

    /** 제공자 인가 URL 생성 + state 저장. 프론트는 이 URL로 브라우저를 보낸다. */
    @Transactional
    public String authorizeUrl(SocialProvider provider) {
        String state = newToken();
        Instant expiresAt = Instant.now().plus(TTL);
        verificationRepository.save(Verification.issue(
                VerificationPurpose.OAUTH_STATE, state,
                writeJson(Map.of("provider", provider.name())), expiresAt));
        return oauthClient.authorizeUrl(provider, state);
    }

    /**
     * 콜백: state 검증 → code 교환 → providerUserId 획득.
     * 이미 연결된 소셜이면 로그인(AUTHENTICATED), 아니면 providerUserId를 티켓에 담아 돌려준다.
     */
    @Transactional
    public CallbackResult handleCallback(SocialProvider provider, String code, String state) {
        consumeState(provider, state);

        OAuthUserInfo userInfo = oauthClient.exchange(provider, code);
        SocialLoginResult login = socialAuthService.login(provider, userInfo.providerUserId());
        if (login.status() == SocialLoginResult.Status.AUTHENTICATED) {
            return CallbackResult.authenticated(login.user());
        }

        String ticket = newToken();
        verificationRepository.save(Verification.issue(
                VerificationPurpose.SOCIAL_LINK_TICKET, ticket,
                writeJson(Map.of("provider", provider.name(), "providerUserId", userInfo.providerUserId())),
                Instant.now().plus(TTL)));
        return CallbackResult.verificationRequired(ticket);
    }

    /** (비로그인) 티켓 + PASS 본인인증으로 DI를 확정해 소셜을 연결/가입한다. */
    @Transactional
    public AuthenticatedUser completeWithPass(String ticket, String reference) {
        Ticket t = consumeTicket(ticket);
        return socialAuthService.linkOrRegister(t.provider(), t.providerUserId(), reference).user();
    }

    /** (로그인 상태) 티켓의 소셜을 현재 사용자에 연결한다(PASS 불필요). */
    @Transactional
    public AuthenticatedUser linkToUser(UUID userId, String ticket) {
        Ticket t = consumeTicket(ticket);
        return socialAuthService.linkToUser(userId, t.provider(), t.providerUserId());
    }

    private void consumeState(SocialProvider provider, String state) {
        Verification saved = verificationRepository.findByReference(state)
                .filter(v -> v.getPurpose() == VerificationPurpose.OAUTH_STATE)
                .filter(v -> !v.isExpired(Instant.now()))
                .orElseThrow(() -> new SocialOAuthException("유효하지 않은 state입니다(만료·위조 가능). 다시 시도해 주세요."));
        Map<String, Object> payload = readJson(saved.getPayload());
        if (!provider.name().equals(payload.get("provider"))) {
            throw new SocialOAuthException("state의 제공자가 일치하지 않습니다.");
        }
        verificationRepository.delete(saved); // 1회용
    }

    private Ticket consumeTicket(String ticket) {
        Verification saved = verificationRepository.findByReference(ticket)
                .filter(v -> v.getPurpose() == VerificationPurpose.SOCIAL_LINK_TICKET)
                .filter(v -> !v.isExpired(Instant.now()))
                .orElseThrow(() -> new SocialOAuthException("유효하지 않은 연결 티켓입니다. 소셜 로그인을 다시 시도해 주세요."));
        Map<String, Object> payload = readJson(saved.getPayload());
        verificationRepository.delete(saved); // 1회용
        return new Ticket(SocialProvider.valueOf((String) payload.get("provider")),
                (String) payload.get("providerUserId"));
    }

    private String newToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String writeJson(Map<String, Object> value) {
        return objectMapper.writeValueAsString(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJson(String json) {
        return objectMapper.readValue(json, Map.class);
    }

    /** 콜백 결과: 바로 로그인 가능(AUTHENTICATED) 또는 본인인증/연결 필요(+티켓). */
    public record CallbackResult(Status status, AuthenticatedUser user, String ticket) {
        public enum Status { AUTHENTICATED, VERIFICATION_REQUIRED }

        static CallbackResult authenticated(AuthenticatedUser user) {
            return new CallbackResult(Status.AUTHENTICATED, user, null);
        }

        static CallbackResult verificationRequired(String ticket) {
            return new CallbackResult(Status.VERIFICATION_REQUIRED, null, ticket);
        }
    }

    private record Ticket(SocialProvider provider, String providerUserId) {
    }
}
