# ADR-0003. PostgreSQL 로컬 도커 데모 DB

- 상태: 수용(Accepted)
- 일자: 2026-07-19

## 맥락

데모의 데이터 저장소가 필요하다. 개발자 로컬에 **도커로 PostgreSQL 16**이 이미 떠 있고
(`postgres-local`, 포트 5432), 그 안에는 다른 용도의 DB(`todolist`)가 함께 있다.
데모 데이터가 기존 DB와 섞이면 곤란하다.

## 결정

- DBMS는 **PostgreSQL**을 쓴다.
- 기존 도커 인스턴스 안에 **데모 전용 데이터베이스 `unified_auth`** 를 별도로 만들어
  다른 DB와 격리한다.
- 접속 정보(호스트/포트/계정)는 **커밋하지 않는다** — `application-local.properties`
  (gitignore 처리) 또는 환경변수로 주입한다. 데모 기본값:
  `jdbc:postgresql://localhost:5432/unified_auth`.
- 스키마 관리는 **Flyway**가 단일 소스로 담당한다(마이그레이션 스크립트).
  JPA `ddl-auto`는 `validate`로만 사용해 엔티티와 실제 스키마의 정합성만 검증한다.

## 결과

- **장점**: 데모 데이터가 `todolist` 등 기존 DB와 완전히 분리된다. 접속 정보가
  저장소에 남지 않는다.
- **비용 / 후속 결정**
  - **스키마 관리 도구(확정, 2026-07-19)**: jOOQ 동적 타입은 코드젠이 없어 스키마 소스가
    곧 "실제 DB 상태"가 된다. 데모 초기에는 JPA `ddl-auto=update`로 빠르게 시작했으나,
    **`ddl-auto=update`가 enum CHECK 제약을 갱신하지 못해 새 enum 값 INSERT 가 깨지는 문제**를
    겪었다(`verification_purpose_check` / `SOCIAL_LINK_TICKET`). 이를 계기로 **Flyway로 전환**한다.
  - **전환 방식(2026-07-19)**: `spring.jpa.hibernate.ddl-auto=validate`로 낮추고, 스키마는
    Flyway 마이그레이션(`db/migration/V{n}__*.sql`)으로만 변경한다. 기존 도커 DB 는 이미
    스키마가 존재하므로 `baseline-on-migrate=true`로 V1(베이스라인)을 재실행하지 않고 baseline
    처리하고, 빈 DB 는 V1 부터 정상 생성된다. 이후 스키마 변경(enum 값 추가 포함)은 반드시
    새 마이그레이션 파일로 수행한다.
  - 로컬 도커에 의존하므로, 다른 개발자가 재현하려면 동일한 컨테이너 기동이 전제된다.
    필요해지면 `docker-compose.yml`로 기동 절차를 코드화한다(현재 미도입).
