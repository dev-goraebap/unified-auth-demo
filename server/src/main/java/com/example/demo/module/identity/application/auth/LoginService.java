package com.example.demo.module.identity.application.auth;

import com.example.demo.module.identity.domain.credential.LocalCredential;
import com.example.demo.module.identity.domain.credential.LocalCredentialRepository;
import com.example.demo.module.identity.domain.user.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로컬(ID/PW) 로그인. loginId로 자격증명을 찾아 BCrypt로 비밀번호를 검증한다.
 * 아이디가 없거나 비밀번호가 틀리면 동일한 예외로 처리한다(사용자 열거 방지).
 */
@Service
public class LoginService {

    private final LocalCredentialRepository localCredentialRepository;
    private final PasswordEncoder passwordEncoder;

    public LoginService(LocalCredentialRepository localCredentialRepository,
                        PasswordEncoder passwordEncoder) {
        this.localCredentialRepository = localCredentialRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser loginLocal(String loginId, String rawPassword) {
        LocalCredential credential = localCredentialRepository.findByLoginId(loginId)
                .orElseThrow(() -> new InvalidCredentialsException("아이디 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(rawPassword, credential.getPasswordHash())) {
            throw new InvalidCredentialsException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        User user = credential.getUser(); // 트랜잭션 안에서 지연로딩
        return new AuthenticatedUser(user.getId(), user.getName());
    }
}
