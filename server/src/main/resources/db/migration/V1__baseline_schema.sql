-- V1 베이스라인: 기존 JPA ddl-auto(update)로 생성돼 있던 스키마를 Flyway 관리로 이관한다. (ADR-0003)
-- 이 시점 이후의 모든 스키마 변경은 V2, V3... 마이그레이션 스크립트로만 수행한다.
--
-- 주의: 이미 스키마가 존재하는 기존 DB 는 baseline-on-migrate 로 이 스크립트를 재실행하지 않는다.
--       따라서 이 파일은 "빈 DB 를 처음부터 세울 때"의 정본(canonical) 스키마 역할을 한다.
--       enum 성격의 컬럼은 varchar + CHECK 로 표현한다(네이티브 enum 미사용).

-- 통합 사용자(본인인증으로 확정된 실명 신원)
CREATE TABLE users (
    id         uuid          NOT NULL,
    name       varchar(100)  NOT NULL,
    birth_date date          NOT NULL,
    gender     char(1)       NOT NULL,
    phone      varchar(20),
    di         varchar(88)   NOT NULL,
    ci         varchar(88),
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT users_di_key UNIQUE (di),
    CONSTRAINT users_gender_check CHECK (gender IN ('M', 'F'))
);

-- 로컬 로그인(아이디/비밀번호) 자격증명. users 와 1:1.
CREATE TABLE local_credentials (
    user_id       uuid          NOT NULL,
    login_id      varchar(50)   NOT NULL,
    password_hash varchar(100)  NOT NULL,
    created_at    timestamp(6) with time zone NOT NULL,
    updated_at    timestamp(6) with time zone NOT NULL,
    CONSTRAINT local_credentials_pkey PRIMARY KEY (user_id),
    CONSTRAINT local_credentials_login_id_key UNIQUE (login_id),
    CONSTRAINT local_credentials_user_fk FOREIGN KEY (user_id) REFERENCES users (id)
);

-- 소셜 계정 연결(카카오/네이버/구글). (provider, provider_user_id) 유일.
CREATE TABLE social_accounts (
    id               uuid          NOT NULL,
    user_id          uuid          NOT NULL,
    provider         varchar(20)   NOT NULL,
    provider_user_id varchar(255)  NOT NULL,
    created_at       timestamp(6) with time zone NOT NULL,
    CONSTRAINT social_accounts_pkey PRIMARY KEY (id),
    CONSTRAINT uk_social_provider_uid UNIQUE (provider, provider_user_id),
    CONSTRAINT social_accounts_provider_check CHECK (provider IN ('KAKAO', 'NAVER', 'GOOGLE')),
    CONSTRAINT social_accounts_user_fk FOREIGN KEY (user_id) REFERENCES users (id)
);

-- 리프레시 토큰(회전/폐기 추적). ADR-0006.
CREATE TABLE refresh_tokens (
    id          uuid         NOT NULL,
    user_id     uuid         NOT NULL,
    token_hash  varchar(64)  NOT NULL,
    expires_at  timestamp(6) with time zone NOT NULL,
    revoked_at  timestamp(6) with time zone,
    replaced_by uuid,
    created_at  timestamp(6) with time zone NOT NULL,
    CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id),
    CONSTRAINT refresh_tokens_token_hash_key UNIQUE (token_hash),
    CONSTRAINT refresh_tokens_user_fk FOREIGN KEY (user_id) REFERENCES users (id)
);

-- 인증 진행 중 임시데이터(OAuth state / 본인인증 결과 / 소셜 연결 티켓). TTL 있음. ADR-0005.
-- 아직 가입 전 익명 단계라 users 와 FK 로 묶지 않는다. reference(토큰)로만 조회하고
-- payload 는 용도별로 형태가 달라 jsonb 로 유연하게 담는다.
CREATE TABLE verification (
    id         uuid          NOT NULL,
    purpose    varchar(30)   NOT NULL,
    reference  varchar(128)  NOT NULL,
    payload    jsonb         NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT verification_pkey PRIMARY KEY (id),
    CONSTRAINT verification_reference_key UNIQUE (reference),
    CONSTRAINT verification_purpose_check CHECK (purpose IN ('OAUTH_STATE', 'PASS_RESULT', 'SOCIAL_LINK_TICKET'))
);
