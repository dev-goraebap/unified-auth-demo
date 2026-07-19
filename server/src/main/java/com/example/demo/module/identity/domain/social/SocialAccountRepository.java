package com.example.demo.module.identity.domain.social;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, UUID> {

    /** 소셜 로그인 시 (provider, providerUserId)로 연결된 계정을 찾는다. */
    Optional<SocialAccount> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);

    List<SocialAccount> findByUser_Id(UUID userId);

    /** 연동 해제 시 현재 사용자의 특정 제공자 연결을 찾는다. */
    Optional<SocialAccount> findByUser_IdAndProvider(UUID userId, SocialProvider provider);
}
