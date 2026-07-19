package com.example.demo.module.identity.domain.credential;

import com.example.demo.module.identity.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * 로컬 계정(ID/PW). 사용자당 최대 1개 — user_id를 PK로 두어 1:1을 스키마로 강제한다.
 * 도메인은 평문 비밀번호를 모른다. 이미 해시된 값만 받는다.
 */
@Entity
@Table(name = "local_credentials")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LocalCredential {

    @Id
    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "login_id", nullable = false, unique = true, length = 50)
    private String loginId;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private LocalCredential(User user, String loginId, String passwordHash) {
        if (user == null) throw new IllegalArgumentException("user는 필수입니다");
        if (loginId == null || loginId.isBlank()) throw new IllegalArgumentException("loginId는 필수입니다");
        if (passwordHash == null || passwordHash.isBlank()) throw new IllegalArgumentException("passwordHash는 필수입니다");
        this.user = user;
        this.loginId = loginId;
        this.passwordHash = passwordHash;
    }

    /** 이미 해시된 비밀번호로 로컬 계정을 만든다(해싱은 애플리케이션 레이어에서). */
    public static LocalCredential create(User user, String loginId, String passwordHash) {
        return new LocalCredential(user, loginId, passwordHash);
    }

    public void changePassword(String newPasswordHash) {
        if (newPasswordHash == null || newPasswordHash.isBlank()) throw new IllegalArgumentException("passwordHash는 필수입니다");
        this.passwordHash = newPasswordHash;
    }
}
