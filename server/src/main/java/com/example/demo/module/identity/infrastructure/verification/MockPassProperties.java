package com.example.demo.module.identity.infrastructure.verification;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Mock 본인인증 설정. DI/CI 합성에 쓰는 솔트와 임시데이터 TTL.
 * 데모라 기본값을 코드에 두되, 운영 유사 환경에서는 {@code auth.pass.mock.*}로 덮어쓴다.
 *
 * @param siteSalt   DI 생성용 사이트 솔트(서비스별 상수). 실제 PASS의 site 개념.
 * @param commonSalt CI 생성용 공통 솔트. 실제 PASS의 공통 개념.
 * @param ttl        본인인증 결과(reference) 유효기간. 이 시간 지나면 verify 불가.
 */
@ConfigurationProperties(prefix = "auth.pass.mock")
public record MockPassProperties(
        String siteSalt,
        String commonSalt,
        Duration ttl
) {
    public MockPassProperties {
        if (siteSalt == null || siteSalt.isBlank()) siteSalt = "demo-site-salt";
        if (commonSalt == null || commonSalt.isBlank()) commonSalt = "demo-common-salt";
        if (ttl == null) ttl = Duration.ofMinutes(10);
    }
}
