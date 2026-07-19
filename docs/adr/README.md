# 아키텍처 결정 기록 (ADR)

이 폴더는 통합인증 데모의 **아키텍처 결정 기록(Architecture Decision Record)** 을 모아둔다.
"왜 이렇게 결정했는가"의 이력을 남겨, 나중에 규칙이 바뀌더라도 그 배경을 추적할 수 있게 한다.

## 작성 원칙

- ADR 하나는 **하나의 결정**을 다룬다.
- 형식: `제목 / 상태 / 맥락 / 결정 / 결과`.
- 상태: `제안(Proposed)` → `수용(Accepted)` → (필요 시) `대체됨(Superseded by ADR-XXXX)`.
- 결정을 뒤집을 때 기존 ADR을 지우지 않는다. 새 ADR을 쓰고 기존 것을 `대체됨`으로 표시한다.
- 이 데모는 규모가 작으므로, ADR도 **"바꾸기 비싼 결정"만** 기록한다.

## 목록

| 번호 | 제목 | 상태 |
|---|---|---|
| [0001](0001-modular-monolith-context-packages.md) | 모듈러 모놀리스 + DDD-lite 컨텍스트 패키지 구조 | 수용 |
| [0002](0002-lightweight-cqrs-jpa-jooq.md) | 경량 CQRS와 데이터 접근 — 명령 JPA / 조회 jOOQ(동적 타입) | 수용 |
| [0003](0003-postgresql-local-docker-demo-db.md) | PostgreSQL 로컬 도커 데모 DB | 수용 |
| [0004](0004-identity-verification-provider-abstraction.md) | 본인인증 Provider 추상화 (Mock ↔ 실제 PASS 교체) | 수용 |
| [0005](0005-verification-temporary-data-storage.md) | 인증 임시데이터(verification) 저장 전략 | 제안 |
| [0006](0006-session-strategy-jwt-refresh-token.md) | 세션 전략 — JWT Access Token + Refresh Token | 수용 |
