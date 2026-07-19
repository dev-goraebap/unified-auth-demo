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

        // ② DI 앵커로 사용자 확정: 없으면 신규 등록, 있으면 기존 사용자에 통합.
        User user = userRepository.findByDi(verified.di())
                .orElseGet(() -> userRepository.save(User.register(
                        verified.di(), verified.ci(), verified.name(),
                        verified.birthDate(), verified.gender(), verified.phone())));

        // ③ 이미 로컬 계정이 있는 사용자면 가입 대신 로그인해야 한다.
        if (localCredentialRepository.existsById(user.getId())) {
            throw new LocalCredentialAlreadyExistsException("이미 로컬 계정이 있는 사용자입니다. 로그인해 주세요.");
        }
        // ④ 로그인 아이디 중복 확인.
        if (localCredentialRepository.existsByLoginId(loginId)) {
            throw new DuplicateLoginIdException("이미 사용 중인 아이디입니다.");
        }

        // ⑤ 비밀번호는 애플리케이션에서 해시해 저장(도메인은 평문을 모른다).
        LocalCredential credential = LocalCredential.create(user, loginId, passwordEncoder.encode(rawPassword));
        localCredentialRepository.save(credential);

        return new AuthenticatedUser(user.getId(), user.getName());
    }
}
