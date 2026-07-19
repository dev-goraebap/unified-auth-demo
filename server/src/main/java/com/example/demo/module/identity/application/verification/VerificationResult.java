package com.example.demo.module.identity.application.verification;

import com.example.demo.module.identity.domain.user.Gender;

import java.time.LocalDate;

/**
 * 본인인증 결과(ADR-0004). "누가 본인인증을 수행했는가"(Mock/실제 PASS)와 무관하게
 * 결과 데이터의 모양은 동일하다. 이 값을 소비하는 가입·로그인·연결 로직은 출처를 모른다.
 *
 * @param di        본인인증 DI(앵커). 필수.
 * @param ci        크로스서비스용 CI. 채널에 따라 없을 수 있어 nullable.
 * @param name      실명
 * @param birthDate 생년월일
 * @param gender    성별
 * @param phone     휴대폰번호(nullable). DI 재료에는 포함하지 않는다.
 */
public record VerificationResult(
        String di,
        String ci,
        String name,
        LocalDate birthDate,
        Gender gender,
        String phone
) {
}
