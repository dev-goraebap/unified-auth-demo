package com.example.demo.module.identity.domain.credential;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LocalCredentialRepository extends JpaRepository<LocalCredential, UUID> {

    Optional<LocalCredential> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);
}
