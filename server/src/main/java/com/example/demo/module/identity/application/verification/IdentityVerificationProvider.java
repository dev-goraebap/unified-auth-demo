package com.example.demo.module.identity.application.verification;

/**
 * 본인인증 추상화(ADR-0004). 애그리게이터 패턴(Bootpay·PortOne)의 3단계 중 ③을 담당한다.
 *
 * <pre>
 * ① 프론트 SDK가 인증창을 열어 사용자 인증
 * ② 성공 시 식별자(reference)만 프론트에 반환 → 서버로 전달
 * ③ 백엔드가 그 reference로 단건 조회 → DI/CI·이름 등 획득   ← 이 인터페이스
 * </pre>
 *
 * 설정 {@code auth.pass.provider=mock|real} 로 구현체를 교체한다. 소비하는 도메인 로직은
 * Mock인지 실제인지 알지 못한다.
 */
public interface IdentityVerificationProvider {

    /**
     * 애그리게이터가 준 식별자(reference)로 본인인증 결과를 조회한다.
     *
     * @param reference 인증창 성공 시 프론트가 전달한 식별자
     * @return 본인인증 결과(DI/CI·이름·생년월일·성별·휴대폰)
     * @throws VerificationNotFoundException reference에 해당하는 인증 결과가 없거나 만료된 경우
     */
    VerificationResult verify(String reference);
}
