# ADR-0006. 세션 전략 — JWT Access Token + Refresh Token

- 상태: 수용(Accepted)
- 일자: 2026-07-19

## 맥락

프론트(Angular SPA)와 백엔드(Spring Boot API)가 분리돼 있다. 로그인 이후의 인증 상태를
어떻게 유지할지 정해야 한다. 크게 **서버 세션 쿠키** 방식과 **토큰(JWT)** 방식이 있다.

## 결정

**JWT Access Token + Refresh Token(RFT)** 방식을 쓴다.

- **Access Token**: 수명이 짧은 JWT. 사용자 식별 정보(내부 user id 등)를 담아 API 요청
  인증에 쓴다. 서버는 서명 검증만으로 인증하므로 매 요청 DB 조회가 없다.
- **Refresh Token**: 수명이 긴 토큰. Access Token 만료 시 새 Access Token을 재발급받는
  데 쓴다. **서버 측에 저장**하여 **회전(rotation)** 과 **폐기(revocation)** 를 지원한다
  — 로그아웃·토큰 탈취 대응에 필요하다.

### Refresh Token 저장

RFT는 서버 저장이 전제이므로 **RFT 저장소(테이블)가 필요**하다. 이 저장 위치·형태는
인증 임시데이터 저장 전략([ADR-0005](0005-verification-temporary-data-storage.md))과
함께 확정한다(별도 `refresh_token`/`auth_session` 테이블).

## 결과

- **장점**: 프론트/백 분리 구조에 자연스럽다. Access Token은 무상태 검증이라 확장에 유리하고,
  RFT는 서버 저장이라 로그아웃·강제만료를 지원한다.
- **비용**: 재발급(`/auth/refresh`) 엔드포인트, RFT 회전·폐기 로직, RFT 저장소가 필요하다.

### 후속 결정 — 확정 (2026-07-19, 구현 착수 시)

권장값을 그대로 채택했다.

- **RFT 전달**: **httpOnly 쿠키**(XSS 토큰 탈취 방지). `path=/api/auth`, `SameSite=Lax`,
  `Secure`는 운영(https)에서 true(`auth.token.refresh-cookie-secure`). 응답 바디에 담지 않는다.
- **Access Token 보관(프론트)**: **메모리**(응답 바디로 내려주고 프론트가 메모리 보관).
- **토큰 수명**: **Access 15분 / RFT 14일**(`auth.token.access-ttl` / `refresh-ttl`).
- **회전 정책**: 재발급마다 기존 RFT를 폐기하고 새 RFT로 교체(`replaced_by` 체인).
  폐기된 RFT 재사용 시 401(재사용 감지의 기반).
- **Access Token 형식**: 자체 HS256 JWT(`sub`=userId, `iat`/`exp`). 데모라 외부 JWT 라이브러리
  없이 표준 `Mac`으로 구성(의존성 최소화). RFT는 JWT가 아닌 불투명 랜덤값, 서버에 SHA-256 해시만 저장.

구현: `module/identity/application/auth/token`(JwtProvider·RefreshTokenService·TokenService),
엔드포인트 `/api/auth`의 `refresh`·`logout`·`me`.
