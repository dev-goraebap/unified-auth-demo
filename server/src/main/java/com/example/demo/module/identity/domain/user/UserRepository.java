package com.example.demo.module.identity.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** 명령용 저장소 — DI로 사용자를 찾는다(ADR-0002: 저장소는 JpaRepository 직접 상속). */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByDi(String di);

    boolean existsByDi(String di);
}
