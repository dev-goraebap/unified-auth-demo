package com.example.demo.module.identity.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 사람 = DI 앵커. 통합 인증의 신원 기준점.
 * 본인인증(PASS/Mock)으로 확인한 DI를 중심으로 로컬·소셜 계정이 매달린다.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    /** 본인인증 DI(앵커). 중복가입 방지를 위해 unique. */
    @Column(nullable = false, unique = true, length = 88)
    private String di;

    /** 크로스서비스 대비 CI. 채널에 따라 없을 수 있어 nullable. */
    @Column(length = 88)
    private String ci;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 1)
    private Gender gender;

    @Column(length = 20)
    private String phone;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private User(String di, String ci, String name, LocalDate birthDate, Gender gender, String phone) {
        if (di == null || di.isBlank()) throw new IllegalArgumentException("di는 필수입니다");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name은 필수입니다");
        if (birthDate == null) throw new IllegalArgumentException("birthDate는 필수입니다");
        if (gender == null) throw new IllegalArgumentException("gender는 필수입니다");
        this.id = UUID.randomUUID();
        this.di = di;
        this.ci = ci;
        this.name = name;
        this.birthDate = birthDate;
        this.gender = gender;
        this.phone = phone;
    }

    /** 본인인증 결과로 신규 사용자를 등록한다. */
    public static User register(String di, String ci, String name, LocalDate birthDate, Gender gender, String phone) {
        return new User(di, ci, name, birthDate, gender, phone);
    }
}
