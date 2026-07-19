package com.example.demo.module.identity.application.auth;

import com.example.demo.module.identity.application.verification.IdentityVerificationProvider;
import com.example.demo.module.identity.application.verification.VerificationResult;
import com.example.demo.module.identity.domain.social.SocialAccount;
import com.example.demo.module.identity.domain.social.SocialAccountRepository;
import com.example.demo.module.identity.domain.social.SocialProvider;
import com.example.demo.module.identity.domain.user.User;
import com.example.demo.module.identity.domain.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 소셜(카카오·네이버·구글) 로그인/연결. 데모에서는 OAuth 대신 (provider, providerUserId)를
 * 직접 받는다(실제 연동 시 OAuth 콜백이 이 식별자를 채운다).
 * <p>
 * 연결도 DI 앵커를 따른다. 처음 보는 소셜 계정은 본인인증(PASS)으로 DI를 확정한 뒤
 * 그 DI의 사용자에 붙인다 — 없으면 신규 생성(CREATED), 있으면 통합(MERGED, 확인화면 대상).
 * 이미 다른 사용자에게 연결된 소셜 계정은 거부한다(중복연결 거부).
 */
@Service
public class SocialAuthService {

    private final IdentityVerificationProvider verificationProvider;
    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;

    public SocialAuthService(IdentityVerificationProvider verificationProvider,
                             UserRepository userRepository,
                             SocialAccountRepository socialAccountRepository) {
        this.verificationProvider = verificationProvider;
        this.userRepository = userRepository;
        this.socialAccountRepository = socialAccountRepository;
    }

    /** 소셜 로그인 시도. 이미 연결됐으면 즉시 로그인, 아니면 본인인증이 필요함을 알린다. */
    @Transactional(readOnly = true)
    public SocialLoginResult login(SocialProvider provider, String providerUserId) {
        return socialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId)
                .map(account -> {
                    User user = account.getUser();
                    return SocialLoginResult.authenticated(new AuthenticatedUser(user.getId(), user.getName()));
                })
                .orElseGet(SocialLoginResult::verificationRequired);
    }

    /**
     * 본인인증(PASS)을 거친 소셜 계정을 연결하거나, DI가 처음이면 신규 사용자로 가입시킨다.
     *
     * @param reference 프론트가 소셜 인증 후 완료한 본인인증의 식별자
     */
    @Transactional
    public SocialLinkResult linkOrRegister(SocialProvider provider, String providerUserId, String reference) {
        // ① PASS 결과로 DI 확보 → DI 앵커로 사용자 확정(없으면 신규).
        VerificationResult verified = verificationProvider.verify(reference);
        Optional<User> found = userRepository.findByDi(verified.di());
        User user = found.orElseGet(() -> userRepository.save(User.register(
                verified.di(), verified.ci(), verified.name(),
                verified.birthDate(), verified.gender(), verified.phone())));
        boolean userCreated = found.isEmpty();

        // ② 이미 연결된 소셜 계정이면: 본인이면 멱등 로그인, 남의 것이면 중복연결 거부.
        Optional<SocialAccount> existing =
                socialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId);
        if (existing.isPresent()) {
            if (existing.get().getUser().getId().equals(user.getId())) {
                return new SocialLinkResult(new AuthenticatedUser(user.getId(), user.getName()),
                        SocialLinkResult.Outcome.ALREADY_LINKED);
            }
            throw new SocialAccountConflictException("이미 다른 계정에 연결된 소셜 계정입니다.");
        }

        // ③ 연결.
        socialAccountRepository.save(SocialAccount.link(user, provider, providerUserId));
        return new SocialLinkResult(new AuthenticatedUser(user.getId(), user.getName()),
                userCreated ? SocialLinkResult.Outcome.CREATED : SocialLinkResult.Outcome.MERGED);
    }

    /**
     * 이미 로그인한 사용자가 소셜 계정을 자기 계정에 연결한다(PASS 불필요 — 이미 신원 확립됨).
     * 같은 소셜이 이미 본인에게 연결됐으면 멱등, 남에게 연결됐으면 거부한다.
     */
    @Transactional
    public AuthenticatedUser linkToUser(UUID userId, SocialProvider provider, String providerUserId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));

        Optional<SocialAccount> existing =
                socialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId);
        if (existing.isPresent()) {
            if (!existing.get().getUser().getId().equals(userId)) {
                throw new SocialAccountConflictException("이미 다른 계정에 연결된 소셜 계정입니다.");
            }
            return new AuthenticatedUser(user.getId(), user.getName()); // 이미 연결됨(멱등)
        }

        socialAccountRepository.save(SocialAccount.link(user, provider, providerUserId));
        return new AuthenticatedUser(user.getId(), user.getName());
    }
}
