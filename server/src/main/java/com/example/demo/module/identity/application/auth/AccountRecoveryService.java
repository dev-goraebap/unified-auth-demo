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
 * 계정 찾기/복구 — <b>PASS 본인인증 기반</b>(비로그인). DI를 확정해 그 사람의 로컬 계정을 찾아
 * 아이디를 알려주거나 비밀번호를 재설정한다. 소셜만 있고 로컬이 없는 계정은 존재하지 않으므로
 * (모든 계정은 ID/PW 보유) DI가 맞으면 항상 로컬 자격증명이 있다.
 */
@Service
public class AccountRecoveryService {

    private final IdentityVerificationProvider verificationProvider;
    private final UserRepository userRepository;
    private final LocalCredentialRepository localCredentialRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountRecoveryService(IdentityVerificationProvider verificationProvider,
                                  UserRepository userRepository,
                                  LocalCredentialRepository localCredentialRepository,
                                  PasswordEncoder passwordEncoder) {
        this.verificationProvider = verificationProvider;
        this.userRepository = userRepository;
        this.localCredentialRepository = localCredentialRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** PASS 본인인증으로 DI를 확정해 로그인 아이디를 찾는다. */
    @Transactional(readOnly = true)
    public String findLoginId(String reference) {
        return credentialOf(reference).getLoginId();
    }

    /** PASS 본인인증으로 DI를 확정해 비밀번호를 재설정한다(새 비밀번호는 해시 저장). */
    @Transactional
    public void resetPassword(String reference, String newRawPassword) {
        credentialOf(reference).changePassword(passwordEncoder.encode(newRawPassword));
    }

    private LocalCredential credentialOf(String reference) {
        VerificationResult verified = verificationProvider.verify(reference);
        User user = userRepository.findByDi(verified.di())
                .orElseThrow(() -> new AccountNotFoundException("가입된 계정이 없습니다. 회원가입을 진행해 주세요."));
        return localCredentialRepository.findById(user.getId())
                .orElseThrow(() -> new AccountNotFoundException("로컬(ID/PW) 계정이 없습니다."));
    }
}
