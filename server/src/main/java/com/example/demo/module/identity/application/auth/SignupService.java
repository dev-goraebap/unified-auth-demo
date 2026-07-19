package com.example.demo.module.identity.application.auth;

import com.example.demo.module.identity.application.verification.IdentityVerificationProvider;
import com.example.demo.module.identity.application.verification.VerificationResult;
import com.example.demo.module.identity.domain.credential.LocalCredential;
import com.example.demo.module.identity.domain.credential.LocalCredentialRepository;
import com.example.demo.module.identity.domain.user.User;
import com.example.demo.module.identity.domain.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로컬(ID/PW) 가입 — <b>PASS 우선</b>. 본인인증으로 DI를 확정한 뒤 계정을 만든다.
 * <p>
 * DI가 앵커이므로 같은 사람(같은 DI)은 항상 같은 {@link User}에 묶인다.
 * <ul>
 *   <li>DI에 해당하는 사용자가 없으면 새로 등록한다(신규 가입).</li>
 *   <li>이미 있으면(소셜로만 가입했던 사람 등) 그 사용자에 로컬 계정을 붙인다(계정 통합).</li>
 *   <li>이미 로컬 계정이 있으면 거부한다(로그인하라고 안내).</li>
 * </ul>
 */
@Service
public class SignupService {

    private final IdentityVerificationProvider verificationProvider;
    private final UserRepository userRepository;
    private final LocalCredentialRepository localCredentialRepository;
    private final PasswordEncoder passwordEncoder;

    public SignupService(IdentityVerificationProvider verificationProvider,
                         UserRepository userRepository,
                         LocalCredentialRepository localCredentialRepository,
                         PasswordEncoder passwordEncoder) {
        this.verificationProvider = verificationProvider;
        this.userRepository = userRepository;
        this.localCredentialRepository = localCredentialRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthenticatedUser signupLocal(String reference, String loginId, String rawPassword) {
        // ① PASS(본인인증) 결과로 DI 확보 — 출처(Mock/실제)는 알지 못한다.
        VerificationResult verified = verificationProvider.verify(reference);
        // ② 확정된 신원으로 사용자 + 로컬 자격증명 생성.
        User user = registerLocalAccount(verified, loginId, rawPassword);
        return new AuthenticatedUser(user.getId(), user.getName());
    }

    /**
     * 확정된 신원(PASS 결과)으로 사용자와 로컬(ID/PW) 자격증명을 만든다. 로컬 가입과
     * 소셜 회원가입이 공유하는 핵심 로직 — 소셜 회원가입도 "소셜정보 + ID/PW"이므로 여기서
     * 계정을 만든 뒤 소셜 연결만 덧붙인다({@code SocialAuthService.registerWithSocial}).
     * <p>
     * DI가 앵커이므로 같은 사람(같은 DI)은 항상 같은 {@link User}에 묶인다. 다만 모든 계정은
     * 반드시 ID/PW를 갖도록 바뀌었으므로, 기존 DI 사용자는 이미 로컬 계정이 있어 여기서 거부된다.
     *
     * @return 생성/확정된 사용자
     */
    @Transactional
    public User registerLocalAccount(VerificationResult verified, String loginId, String rawPassword) {
        // ① DI 앵커로 사용자 확정: 없으면 신규 등록.
        User user = userRepository.findByDi(verified.di())
                .orElseGet(() -> userRepository.save(User.register(
                        verified.di(), verified.ci(), verified.name(),
                        verified.birthDate(), verified.gender(), verified.phone())));

        // ② 이미 로컬 계정이 있는 사용자면 가입 대신 로그인해야 한다.
        if (localCredentialRepository.existsById(user.getId())) {
            throw new LocalCredentialAlreadyExistsException("이미 로컬 계정이 있는 사용자입니다. 로그인해 주세요.");
        }
        // ③ 로그인 아이디 중복 확인.
        if (localCredentialRepository.existsByLoginId(loginId)) {
            throw new DuplicateLoginIdException("이미 사용 중인 아이디입니다.");
        }

        // ④ 비밀번호는 애플리케이션에서 해시해 저장(도메인은 평문을 모른다).
        LocalCredential credential = LocalCredential.create(user, loginId, passwordEncoder.encode(rawPassword));
        localCredentialRepository.save(credential);

        return user;
    }
}
