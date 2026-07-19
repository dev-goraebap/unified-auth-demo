package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 프론트(Angular SPA)와 백엔드가 다른 origin이라 CORS를 연다(데모용).
 * <p>
 * RFT는 httpOnly 쿠키로 오가므로 {@code allowCredentials(true)}가 필수다. 자격증명을 허용할 때는
 * {@code "*"} origin을 쓸 수 없어, 프론트 origin을 명시한다({@code auth.web.cors-origins}).
 * 프론트는 요청에 {@code withCredentials}를 켜야 쿠키가 실린다(ADR-0006).
 */
@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public WebCorsConfig(@Value("${auth.web.cors-origins:http://localhost:4200}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
