package com.example.demo.module.identity.infrastructure.verification;

import com.example.demo.module.identity.domain.user.Gender;

import java.time.LocalDate;

/**
 * Mock 인증창이 입력받아 verification.payload(jsonb)에 담는 값.
 * verify(reference) 시 이 값을 꺼내 DI/CI를 결정적으로 합성한다(ADR-0004).
 */
public record MockPassPayload(
        String name,
        LocalDate birthDate,
        Gender gender,
        String phone
) {
}
