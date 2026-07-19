# ADR-0004. 본인인증 Provider 추상화 (Mock ↔ 실제 PASS 교체)

- 상태: 수용(Accepted)
- 일자: 2026-07-19

## 맥락

이 서비스의 신원 기준은 **본인인증(PASS)으로 확인한 DI**다. 그런데 실제 PASS는
인증 대행사와 계약해야 테스트조차 어렵다. 계약 전 개발 단계에서도 가입·로그인·계정
연결의 전체 흐름을 검증할 수 있어야 한다.

핵심은 "본인인증을 누가 수행하느냐"는 바뀌어도, 그 **결과 데이터의 모양(DI·CI·이름·
생년월일·성별·휴대폰)** 은 동일하다는 점이다.

## 결정

본인인증을 **하나의 인터페이스로 추상화**하고, 구현체를 설정으로 교체한다.

```
interface IdentityVerificationProvider {
    VerificationResult verify(...);   // { di, ci, name, birth, gender, phone }
}
```

- `MockVerificationProvider`(데모용): 입력값에서 **결정적(deterministic)** 으로 DI/CI를
  생성한다.
  - `identityCore = 이름 + 생년월일 + 성별`
  - `DI = Base64(SHA-512(SITE_SALT + identityCore))` — 88자 문자열
  - `CI = Base64(SHA-512(COMMON_SALT + identityCore))`
  - 해시는 **SHA-512**를 쓴다. 실제 PASS DI 형식(64바이트 → Base64 88자)과 **동일 길이**를
    맞추기 위함이다(SHA-256은 32바이트 → 44자라 길이가 다르다). Mock 전용 결정적 합성값이므로
    알고리즘 선택의 보안 함의는 없고, "실제와 동일한 모양"이 유일한 기준이다.
  - 같은 사람 정보 → 항상 같은 DI(=재가입/기존계정 연결 흐름 재현). 정보가 다르면
    다른 DI(=신규 가입 흐름).
  - 휴대폰번호는 DI 재료에서 제외한다 — 실제 PASS에서 DI는 번호가 바뀌어도 불변이기 때문.
- `PassVerificationProvider`(실서비스용): 실제 PASS 연동. 계약 후 구현한다.
- 교체는 설정 한 줄(`auth.pass.provider = mock | real`)로 한다.
  DI를 소비하는 쪽(가입·로그인·연결 로직)은 Mock인지 실제인지 알지 못한다.

### DI 저장 형식

실제 DI 형식(64바이트 → Base64 약 88자)에 맞춰 Mock도 동일 길이로 생성한다.
DB 컬럼·조회 로직이 실제 PASS와 동일하게 유지되어, 교체 시 도메인 로직 변경이 없다.

### 본인인증 흐름 — 애그리게이터 패턴 채택

실제 연동 대상은 **애그리게이터(Bootpay·PortOne) 패턴**을 기준으로 삼는다(KCP식 직접
연동의 백엔드 콜백·복호화 방식은 채택하지 않는다). 이 패턴은 3단계다.

```
① 프론트 SDK가 인증창(팝업) 오픈 → 사용자 인증
② 성공 시 식별자(reference)만 프론트에 반환 → 서버로 전달
   (Bootpay=receipt_id / PortOne=identityVerificationId)
③ 백엔드가 그 식별자로 REST 단건조회 → CI/DI·이름 등 획득
```

우리 `IdentityVerificationProvider.verify(reference)`는 이 3단계 중 ③을 담당한다.
`reference`는 애그리게이터가 준 식별자다.

- **Mock**: 프론트는 "가짜 인증창"에서 이름·생년월일·성별을 입력받아 서버에 임시저장하고
  **가짜 식별자(reference)** 를 돌려준다. `MockVerificationProvider.verify(reference)`는
  저장된 입력값을 꺼내 DI/CI를 합성해 반환한다.
- **실제**: `BootpayProvider`/`PortOneProvider.verify(reference)`가 애그리게이터 REST를
  호출해 결과를 받아 매핑한다.

Bootpay ↔ PortOne은 같은 패턴이라, 실제 계약한 쪽으로 provider 구현만 교체하면 된다.
프론트는 SDK 호출부만 바뀌고 백엔드 도메인 로직은 그대로다. (근거: `docs/기획/본인인증-방식비교.html`)

## 결과

- **장점**: 계약 전에도 전체 인증 흐름을 개발·테스트할 수 있다. 실제 PASS 도입 시
  **provider 구현체만 교체**하면 되고, 기획·화면·도메인 로직은 그대로 재사용된다.
- **데모 한계**: 이름·생년월일·성별이 같으면 동일 DI로 취급된다(실제로는 주민번호로 구분).
  충돌이 문제되면 Mock 입력에 "주민번호 뒷자리" 같은 식별필드를 추가해 `identityCore`에
  넣는다.
- **연관**: 본인인증 결과(DI 등)를 계정 생성 완료 전까지 어디에 임시 보관할지는
  [ADR-0005](0005-verification-temporary-data-storage.md)에서 다룬다.
