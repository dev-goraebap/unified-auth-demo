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
 * 소셜(카카오·네이버·구글) 로그인/연결/가입. providerUserId는 OAuth 콜백이 채운다.
 * <p>
 * 계정은 DI 앵커를 따르되, <b>모든 계정은 ID/PW(로컬 자격증명)를 반드시 가진다</b>. 따라서 소셜
 * 회원가입도 "소셜정보 + ID/PW"다. 처음 보는 소셜 계정은 본인인증(PASS)으로 DI를 확정한 뒤:
 * <ul>
 *   <li>DI가 기존 회원과 일치 → 소셜만 그 계정에 연결하고 로그인({@code AUTHENTICATED}).</li>
 *   <li>DI가 처음 → 계정을 만들지 않고 회원가입 필요를 알린다({@code SIGNUP_REQUIRED}).
 *       이후 ID/PW까지 받아 {@link #registerWithSocial}로 계정을 생성한다.</li>
 * </ul>
 * 이미 다른 사용자에게 연결된 소셜 계정은 거부한다(중복연결 거부).
 */
@Service
public class SocialAuthService {

    private final IdentityVerificationProvider verificationProvider;
    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final SignupService signupService;

    public SocialAuthService(IdentityVerificationProvider verificationProvider,
                             UserRepository userRepository,
                             SocialAccountRepository socialAccountRepository,
                             SignupService signupService) {
        this.verificationProvider = verificationProvider;
        this.userRepository = userRepository;
        this.socialAccountRepository = socialAccountRepository;
        this.signupService = signupService;
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
     * PASS 본인인증까지 마친 소셜 계정을 판정한다.
     * <ul>
     *   <li>DI가 기존 회원과 일치 → 소셜을 그 계정에 연결하고 로그인({@code AUTHENTICATED}).</li>
     *   <li>DI가 처음 → 아직 계정을 만들지 않고 {@code SIGNUP_REQUIRED}. (이후 ID/PW 입력 단계로.)</li>
     * </ul>
     *
     * @param reference 프론트가 소셜 인증 후 완료한 본인인증의 식별자
     */
    @Transactional(readOnly = true)
    public SocialCompleteResult completeWithPass(SocialProvider provider, String providerUserId, String reference) {
        VerificationResult verified = verificationProvider.verify(reference);
        Optional<User> found = userRepository.findByDi(verified.di());
        Optional<SocialAccount> existingSocial =
                socialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId);

        // ① 이 소셜이 이미 연결돼 있으면: 본인(같은 DI)이면 멱등 로그인, 남이면 중복연결 거부.
        if (existingSocial.isPresent()) {
            User owner = existingSocial.get().getUser();
            if (found.isPresent() && owner.getId().equals(found.get().getId())) {
                return SocialCompleteResult.authenticated(new AuthenticatedUser(owner.getId(), owner.getName()));
            }
            throw new SocialAccountConflictException("이미 다른 계정에 연결된 소셜 계정입니다.");
        }

        // ② 소셜 미연결 + 계정 없음 → 일반 회원가입과 동일하게 ID/PW를 받아야 한다.
        if (found.isEmpty()) {
            return SocialCompleteResult.signupRequired();
        }

        // ③ 소셜 미연결 + 기존 회원 → 아직 연결하지 않고 "연동하시겠습니까?" 확인을 요구한다.
        return SocialCompleteResult.linkRequired(found.get().getName());
    }

    /**
     * 사용자 확인 후, 기존 회원 계정에 소셜을 실제로 연결하고 로그인시킨다({@code LINK_REQUIRED} 확정).
     * PASS로 다시 DI를 확정해 계정을 찾는다(티켓·reference는 확인 단계까지 살아있다).
     */
    @Transactional
    public AuthenticatedUser confirmLink(SocialProvider provider, String providerUserId, String reference) {
        VerificationResult verified = verificationProvider.verify(reference);
        User user = userRepository.findByDi(verified.di())
                .orElseThrow(() -> new IllegalStateException("연동할 계정을 찾을 수 없습니다. 다시 시도해 주세요."));
        linkSocial(user, provider, providerUserId); // 멱등/중복연결 거부 포함.
        return new AuthenticatedUser(user.getId(), user.getName());
    }

    /** 현재 사용자의 특정 소셜 연동을 해제한다. 로컬(ID/PW) 계정은 항상 남으므로 안전. 없으면 멱등. */
    @Transactional
    public void unlink(UUID userId, SocialProvider provider) {
        socialAccountRepository.findByUser_IdAndProvider(userId, provider)
                .ifPresent(socialAccountRepository::delete);
    }

    /**
     * 소셜 회원가입 — <b>소셜정보 + ID/PW</b>로 신규 계정을 만든다. PASS로 신원을 확정하고,
     * 로컬 가입과 동일한 로직으로 사용자·자격증명을 생성한 뒤 소셜을 연결한다.
     *
     * @param reference PASS 본인인증 식별자
     * @param loginId   로그인 아이디
     * @param rawPassword 평문 비밀번호(해시는 내부에서)
     */
    @Transactional
    public AuthenticatedUser registerWithSocial(SocialProvider provider, String providerUserId,
                                                String reference, String loginId, String rawPassword) {
        // 안전장치: 이 소셜 계정이 이미 누군가에게 연결돼 있으면 가입이 아니라 로그인/거부 대상.
        if (socialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId).isPresent()) {
            throw new SocialAccountConflictException("이미 다른 계정에 연결된 소셜 계정입니다.");
        }
        VerificationResult verified = verificationProvider.verify(reference);
        User user = signupService.registerLocalAccount(verified, loginId, rawPassword);
        socialAccountRepository.save(SocialAccount.link(user, provider, providerUserId));
        return new AuthenticatedUser(user.getId(), user.getName());
    }

    /**
     * 이미 로그인한 사용자가 소셜 계정을 자기 계정에 연결한다(PASS 불필요 — 이미 신원 확립됨).
     * 같은 소셜이 이미 본인에게 연결됐으면 멱등, 남에게 연결됐으면 거부한다.
     */
    @Transactional
    public AuthenticatedUser linkToUser(UUID userId, SocialProvider provider, String providerUserId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));
        linkSocial(user, provider, providerUserId);
        return new AuthenticatedUser(user.getId(), user.getName());
    }

    /** 소셜 계정을 사용자에 연결한다. 이미 본인에게 연결됐으면 멱등, 남에게 연결됐으면 거부. */
    private void linkSocial(User user, SocialProvider provider, String providerUserId) {
        Optional<SocialAccount> existing =
                socialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId);
        if (existing.isPresent()) {
            if (!existing.get().getUser().getId().equals(user.getId())) {
                throw new SocialAccountConflictException("이미 다른 계정에 연결된 소셜 계정입니다.");
            }
            return; // 이미 본인에게 연결됨(멱등).
        }
        socialAccountRepository.save(SocialAccount.link(user, provider, providerUserId));
    }
}
