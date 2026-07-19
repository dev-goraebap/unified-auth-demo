# identity 컨텍스트 — 도메인 · 스키마 설계

- 일자: 2026-07-19
- 관련 결정: [ADR-0001](../adr/0001-modular-monolith-context-packages.md)(컨텍스트),
  [ADR-0002](../adr/0002-lightweight-cqrs-jpa-jooq.md)(JPA/jOOQ),
  [ADR-0004](../adr/0004-identity-verification-provider-abstraction.md)(본인인증),
  [ADR-0005](../adr/0005-verification-temporary-data-storage.md)(임시데이터/RFT),
  [ADR-0006](../adr/0006-session-strategy-jwt-refresh-token.md)(세션)

> 이 문서는 **설계 제안**이다. DDL은 참조용이며, 초기에는 JPA `ddl-auto`가 스키마를
> 생성하고(ADR-0003) 안정되면 이 DDL을 Flyway로 옮긴다. 컬럼명·타입 등 세부는 검토 후 확정.

## 1. 범위

`identity` 컨텍스트 하나에 인증 전체를 담는다. 테이블 5종:

| 테이블 | 역할 | 관계 |
|---|---|---|
| `users` | 사람 = **DI 앵커**. 본인인증 프로필 | 루트 |
| `local_credentials` | 로컬 계정(ID/PW) | user 1 : 0..1 |
| `social_accounts` | 소셜 계정(kakao/naver/google) | user 1 : 0..N |
| `verification` | 임시데이터(OAuth state·본인인증 결과) + TTL | 독립(참조 전) |
| `refresh_tokens` | Refresh Token(회전·폐기) | user 1 : 0..N |

## 2. ERD

```mermaid
erDiagram
    users ||--o| local_credentials : "has (0..1)"
    users ||--o{ social_accounts : "has (0..N)"
    users ||--o{ refresh_tokens : "issues (0..N)"

    users {
        uuid id PK
        varchar di UK "본인인증 DI, 앵커"
        varchar ci "nullable"
        varchar name
        date birth_date
        char gender "M/F"
        varchar phone
        timestamptz created_at
        timestamptz updated_at
    }
    local_credentials {
        uuid user_id PK_FK
        varchar login_id UK
        varchar password_hash
        timestamptz created_at
        timestamptz updated_at
    }
    social_accounts {
        uuid id PK
        uuid user_id FK
        varchar provider "kakao|naver|google"
        varchar provider_user_id
        timestamptz created_at
    }
    refresh_tokens {
        uuid id PK
        uuid user_id FK
        varchar token_hash UK
        timestamptz expires_at
        timestamptz revoked_at "nullable"
        uuid replaced_by "회전 체인, nullable"
        timestamptz created_at
    }
    verification {
        uuid id PK
        varchar purpose "oauth_state|pass_result"
        varchar reference UK "클라이언트에 주는 토큰"
        jsonb payload
        timestamptz expires_at
        timestamptz created_at
    }
```

`verification`은 가입/연결 완료 전 단계라 아직 `users`와 FK로 묶이지 않는다(익명 단계).

## 3. 테이블 스키마 (PostgreSQL 16, 참조 DDL)

```sql
-- 사람 = DI 앵커
CREATE TABLE users (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    di          varchar(88)  NOT NULL UNIQUE,          -- 본인인증 DI(앵커)
    ci          varchar(88),                           -- 크로스서비스 대비, nullable
    name        varchar(100) NOT NULL,
    birth_date  date         NOT NULL,
    gender      char(1)      NOT NULL CHECK (gender IN ('M','F')),
    phone       varchar(20),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now()
);

-- 로컬 계정 (user당 0..1) — user_id를 PK로 두어 1:1 강제
CREATE TABLE local_credentials (
    user_id       uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    login_id      varchar(50)  NOT NULL UNIQUE,
    password_hash varchar(100) NOT NULL,               -- BCrypt
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now()
);

-- 소셜 계정 (user당 0..N) — 한 소셜계정은 한 user에만
CREATE TABLE social_accounts (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider         varchar(20) NOT NULL CHECK (provider IN ('kakao','naver','google')),
    provider_user_id varchar(255) NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_user_id)
);

-- Refresh Token (회전·폐기)
CREATE TABLE refresh_tokens (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  varchar(64) NOT NULL UNIQUE,           -- 원문 저장 X, SHA-256 hex
    expires_at  timestamptz NOT NULL,
    revoked_at  timestamptz,
    replaced_by uuid REFERENCES refresh_tokens(id),    -- 회전 체인
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

-- 임시데이터(OAuth state·본인인증 결과) + TTL
CREATE TABLE verification (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    purpose    varchar(30)  NOT NULL,                  -- 'oauth_state' | 'pass_result'
    reference  varchar(128) NOT NULL UNIQUE,           -- 클라이언트에 주는 조회 토큰
    payload    jsonb        NOT NULL,                  -- {di,ci,name,...} 또는 state 데이터
    expires_at timestamptz  NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_verification_expires ON verification(expires_at);  -- 만료 청소용
```

## 4. 도메인 모델 (DDD-lite)

ADR-0002에 따라 엔티티에 JPA 애너테이션을 직접 붙이고, 저장소는 `JpaRepository`를 직접
상속한다. 자가 검증은 유지한다 — `protected` 기본 생성자 + 정적 팩토리로 생성을 통제.

- **`User`** (애그리거트 루트): DI·프로필 보유. `User.register(VerificationResult)` 정적
  팩토리로 생성하며, DI·이름 필수 등 불변식을 생성 시점에 검증.
- **`LocalCredential`**: `login_id` + `password_hash`. 비밀번호는 `PasswordEncoder`(BCrypt)로
  해싱해 넘겨받는다(도메인은 평문을 모른다).
- **`SocialAccount`**: `(provider, provider_user_id)`로 식별. `SocialAccount.link(user, ...)`.
- **`RefreshToken`**: 원문이 아닌 해시를 저장. 회전 시 기존 토큰 `revoked_at` + 새 토큰
  발급, `replaced_by`로 체인.
- **`Verification`**: 애그리거트라기보다 인프라성 임시 저장. 만료·1회성.

> **애그리거트 경계(데모 완화)**: 엄밀히는 `User`가 `LocalCredential`·`SocialAccount`를
> 소유하는 한 애그리거트지만, 데모에서는 각 엔티티를 별도 저장소로 관리한다(컬렉션 매핑
> 부담 회피). 트랜잭션 경계만 유스케이스 서비스에서 지킨다.

## 5. 주요 설계 결정 노트

- **PK = UUID**: `users.id`가 JWT subject로 외부에 노출되므로, 순차 노출을 피해 UUID 사용
  (`gen_random_uuid()`, pg16 코어 내장).
- **DI/CI 길이**: 실제 PASS 형식(Base64 약 88자)에 맞춰 `varchar(88)`. Mock도 동일 길이 생성.
- **`ci` nullable**: 애그리게이터/대행사 채널에 따라 DI만 오고 CI가 없을 수 있음(본인인증-방식비교 참고).
- **비밀번호**: Spring Security `PasswordEncoder` 기본 **BCrypt**(`$2a$…` 60자, 여유 100).
  Argon2로 바꿀 여지 있음.
- **RFT는 해시 저장**: 토큰 원문을 DB에 두지 않고 SHA-256 해시만 저장(유출 대비).
- **`local_credentials`의 PK=user_id**: 1:1을 스키마로 강제(별도 unique 불필요).
- **`verification.payload = jsonb`**: purpose마다 형태가 달라(state vs 본인인증 결과) 유연하게.

## 6. 소소한 선택 — 확정 (2026-07-19)

1. **gender**: `char(1)` `M`/`F`로 확정.
2. **소셜 계정**: 프로필 캐시 컬럼을 두지 않는다 — **최소 연동정보만**(`provider`,
   `provider_user_id`). 표시용 이메일·닉네임은 필요해지면 추가.
3. **RFT 회전**: `replaced_by` **체인 유지**(탈취된 옛 토큰 재사용 탐지).
4. **verification 만료 청소**: **조회 시 만료조건 필터**(`expires_at > now()`)로 정합성을
   보장하고, **가벼운 주기 배치(@Scheduled)로 만료행 삭제**해 테이블 비대화를 막는다.

## 7. 다음 단계

1. 위 스키마로 JPA 엔티티 + 저장소 작성(`module/identity/domain`)
2. 본인인증 `IdentityVerificationProvider` + `MockVerificationProvider`(ADR-0004)
3. 가입/로그인/연결 유스케이스 서비스 + 컨트롤러(`module/identity/application`)
4. jOOQ 조회(마이페이지·계정 목록 등)
