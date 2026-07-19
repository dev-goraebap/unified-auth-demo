package com.example.demo.module.identity.application.auth.oauth;

import com.example.demo.module.identity.domain.social.SocialProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 소셜 OAuth(카카오·구글) 설정. 엔드포인트·리다이렉트·스코프는 {@code application.properties}(커밋),
 * client-id/secret은 {@code application-local.properties}(gitignore)에서 주입된다. 네이버는 미지원.
 */
@ConfigurationProperties("auth.oauth")
public record OAuthProperties(Registration kakao, Registration google) {

    public record Registration(
            String clientId,
            String clientSecret,
            String authorizeUri,
            String tokenUri,
            String userinfoUri,
            String redirectUri,
            String scope
    ) {
    }

    public Registration forProvider(SocialProvider provider) {
        return switch (provider) {
            case KAKAO -> kakao;
            case GOOGLE -> google;
            default -> throw new IllegalArgumentException("지원하지 않는 소셜 제공자입니다: " + provider);
        };
    }
}
