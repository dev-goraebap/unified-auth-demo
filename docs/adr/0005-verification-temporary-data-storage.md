# ADR-0005. 인증 임시데이터(verification) 저장 전략

- 상태: 수용(Accepted)
- 일자: 2026-07-19

## 맥락

프론트(Angular)와 백엔드(Spring Boot)가 분리된 구조에서, 인증 흐름은 **여러 번의
요청·리다이렉트에 걸쳐** 진행된다. 이때 "완결되지 않은 상태"를 서버가 잠시 붙들어야
하는 지점이 생긴다. `better-auth` 같은 라이브러리가 `user`·`session`·`account` 외에
`verification` 테이블을 두는 이유와 같다.

이 데모에서 임시 보관이 필요한 데이터는 크게 셋이다.

1. **OAuth state / nonce** — 소셜 로그인 리다이렉트의 CSRF 방지 값. 요청을 보낼 때
   생성하고, 콜백에서 대조한 뒤 폐기한다. 수명 짧음(수 분).
2. **본인인증 결과 임시 보관** — Mock/실제 PASS가 DI를 돌려준 뒤, 사용자가 가입·연결을
   **완료하기 전까지** 그 DI·CI·이름 등을 서버가 들고 있어야 한다. 이 값을 클라이언트가
   들고 다니게 하면 위·변조가 가능하므로, **서버가 토큰으로만 참조**하게 한다. 수명 짧음.
3. **리프레시 토큰(RFT) 저장** — 세션 전략은 **JWT + Refresh Token으로 확정**
   ([ADR-0006](0006-session-strategy-jwt-refresh-token.md)). RFT는 서버 저장이 전제이므로
   회전·폐기(로그아웃)를 위해 저장소가 **반드시 필요**하다.

즉 **우려대로 "임시 데이터 보관소"는 필요하다.** 다만 그것을 어떤 형태로 둘지가 결정 포인트다.

## 검토한 선택지

### (A) DB 단일 `verification` 테이블 + TTL

하나의 테이블에 종류(purpose)·키(identifier)·payload(JSON)·만료시각(expires_at)을 담는다.

```
verification(
  id, purpose,        -- 'oauth_state' | 'pass_result' | ...
  identifier,         -- 조회 키(토큰 등)
  payload,            -- 검증 결과(JSON): di, ci, name ...
  expires_at, created_at
)
```

- 장점: better-auth 패턴과 동일해 이해가 쉽다. 재시작에도 남는다. Postgres만으로 끝.
- 단점: 만료 데이터 청소(정리 배치/쿼리 조건)가 필요하다.

### (B) 인메모리 (Caffeine 등 TTL 캐시)

- 장점: 스키마·정리 배치가 필요 없다. 가장 단순하다.
- 단점: 서버 재시작 시 사라지고, 다중 인스턴스에서 공유가 안 된다.
  → **데모(단일 인스턴스)에서는 대부분 문제되지 않는다.**

### (C) Redis

- 장점: TTL·분산에 이상적.
- 단점: 데모에 인프라(Redis 컨테이너)를 하나 더 얹는다. **과함.**

## 제안 (권장)

**(A) DB 단일 `verification` 테이블**을 권장한다.

- 이유: 이미 Postgres를 쓰고, 흐름(2번 "본인인증 결과 임시 보관")이 리다이렉트를 걸쳐
  이어지므로 **지속성이 있는 편이 데모 시연에 안정적**이다. 테이블 하나로 3종을 모두
  수용하므로 스키마 부담도 작다.
- better-auth가 4개(`user`/`session`/`account`/`verification`)를 만드는 것과 달리,
  이 데모는 도메인을 우리가 직접 설계하므로 `user`/`account`류는 우리 도메인 모델
  ([ADR-0001](0001-modular-monolith-context-packages.md))로 충분하고, **여기서 추가로
  필요한 건 `verification` 성격의 테이블 하나뿐**이다.
- 극단적 단순화를 원하면 (B) 인메모리도 데모 한정으로 수용 가능하다.

세션 전략은 JWT+RFT로 확정([ADR-0006](0006-session-strategy-jwt-refresh-token.md))되어,
**RFT 저장소는 필요**가 확정됐다. 최종 결정(2026-07-19):

- 임시데이터(OAuth state, 본인인증 결과 임시보관)는 **(A) `verification` 테이블 + TTL**.
- RFT는 회전·폐기 이력이 필요하므로 **별도 `refresh_token` 테이블**로 분리한다.
- 두 테이블 모두 `identity` 컨텍스트 안에 둔다([ADR-0001](0001-modular-monolith-context-packages.md) 확정).

## 결과

- **테이블 2종** — `verification`(임시데이터 + TTL), `refresh_token`(RFT 회전·폐기). 둘 다
  `identity` 컨텍스트 소속.
- `verification`은 만료 데이터 청소(정리 쿼리/배치)가 필요하다.
- 실제 스키마 컬럼 정의는 구현 착수 시 확정한다.
