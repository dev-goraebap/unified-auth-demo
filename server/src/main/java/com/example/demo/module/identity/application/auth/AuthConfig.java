package com.example.demo.module.identity.application.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 인증 유스케이스가 쓰는 빈. 지금은 비밀번호 해시기(BCrypt)뿐이다.
 * <p>
 * spring-security-crypto만 의존하므로 Spring Security 필터체인은 켜지지 않는다
 * (엔드포인트가 잠기지 않는다). 인증/인가 필터는 슬라이스 3에서 필요해질 때 도입한다.
 */
@Configuration
public class AuthConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
