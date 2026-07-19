package com.example.demo.module.identity.application.auth.oauth;

import com.example.demo.module.identity.domain.social.SocialProvider;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 소셜 제공자(카카오·구글)와의 실제 OAuth2 authorization-code 통신.
 * <ul>
 *   <li>{@link #authorizeUrl} — 사용자를 보낼 제공자 인가 URL을 만든다.</li>
 *   <li>{@link #exchange} — 콜백 code를 access token으로 교환하고 userinfo로 providerUserId를 얻는다.</li>
 * </ul>
 * client secret은 이 서버 안에서만 쓰인다(프론트에 노출되지 않음).
 */
@Component
public class SocialOAuthClient {

    private final OAuthProperties properties;
    private final RestClient rest = RestClient.create();

    public SocialOAuthClient(OAuthProperties properties) {
        this.properties = properties;
    }

    public String authorizeUrl(SocialProvider provider, String state) {
        OAuthProperties.Registration reg = properties.forProvider(provider);
        StringBuilder url = new StringBuilder(reg.authorizeUri())
                .append("?response_type=code")
                .append("&client_id=").append(encode(reg.clientId()))
                .append("&redirect_uri=").append(encode(reg.redirectUri()))
                .append("&state=").append(encode(state));
        if (reg.scope() != null && !reg.scope().isBlank()) {
            url.append("&scope=").append(encode(reg.scope()));
        }
        return url.toString();
    }

    public OAuthUserInfo exchange(SocialProvider provider, String code) {
        OAuthProperties.Registration reg = properties.forProvider(provider);
        String accessToken = requestAccessToken(reg, code);
        Map<String, Object> userInfo = requestUserInfo(reg, accessToken);
        return switch (provider) {
            // 카카오: 최상위 id(Long) = providerUserId, 이메일은 kakao_account 안(동의 시).
            case KAKAO -> new OAuthUserInfo(String.valueOf(userInfo.get("id")), kakaoEmail(userInfo), null);
            // 구글(OIDC): sub = providerUserId.
            case GOOGLE -> new OAuthUserInfo(
                    (String) userInfo.get("sub"), (String) userInfo.get("email"), (String) userInfo.get("name"));
            default -> throw new SocialOAuthException("지원하지 않는 소셜 제공자입니다: " + provider);
        };
    }

    private String requestAccessToken(OAuthProperties.Registration reg, String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", reg.clientId());
        if (reg.clientSecret() != null && !reg.clientSecret().isBlank()) {
            form.add("client_secret", reg.clientSecret());
        }
        form.add("redirect_uri", reg.redirectUri());
        form.add("code", code);
        try {
            Map<String, Object> token = rest.post()
                    .uri(reg.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(MAP_TYPE);
            Object accessToken = token == null ? null : token.get("access_token");
            if (accessToken == null) {
                throw new SocialOAuthException("소셜 토큰 교환 응답에 access_token이 없습니다");
            }
            return accessToken.toString();
        } catch (SocialOAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new SocialOAuthException("소셜 토큰 교환에 실패했습니다", e);
        }
    }

    private Map<String, Object> requestUserInfo(OAuthProperties.Registration reg, String accessToken) {
        try {
            Map<String, Object> info = rest.get()
                    .uri(reg.userinfoUri())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(MAP_TYPE);
            if (info == null || info.isEmpty()) {
                throw new SocialOAuthException("소셜 사용자 정보 조회 응답이 비어 있습니다");
            }
            return info;
        } catch (SocialOAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new SocialOAuthException("소셜 사용자 정보 조회에 실패했습니다", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static String kakaoEmail(Map<String, Object> userInfo) {
        Object account = userInfo.get("kakao_account");
        if (account instanceof Map<?, ?> map) {
            Object email = ((Map<String, Object>) map).get("email");
            return email == null ? null : email.toString();
        }
        return null;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static final org.springframework.core.ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new org.springframework.core.ParameterizedTypeReference<>() {
            };
}
