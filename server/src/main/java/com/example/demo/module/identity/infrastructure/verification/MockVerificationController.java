package com.example.demo.module.identity.infrastructure.verification;

import com.example.demo.module.identity.domain.user.Gender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * (데모 전용) Mock 인증창 백엔드 — 애그리게이터 SDK를 흉내낸다(ADR-0004).
 * 프론트의 "가짜 인증창"이 입력값을 이 엔드포인트로 제출하면 reference를 돌려준다.
 * 이후 프론트는 이 reference를 가입/로그인 API에 실어 보내고, 서버는
 * {@link MockVerificationProvider#verify}로 DI/CI를 얻는다.
 *
 * 실제 연동 시 이 컨트롤러는 사라진다(프론트가 애그리게이터 SDK를 직접 호출).
 */
@RestController
@RequestMapping("/api/verification/mock")
@ConditionalOnProperty(name = "auth.pass.provider", havingValue = "mock", matchIfMissing = true)
public class MockVerificationController {

    private final MockVerificationProvider mockProvider;

    public MockVerificationController(MockVerificationProvider mockProvider) {
        this.mockProvider = mockProvider;
    }

    /** 가짜 인증창 제출 → reference 발급. */
    @PostMapping("/start")
    public StartResponse start(@RequestBody StartRequest request) {
        request.validate();
        String reference = mockProvider.startVerification(
                request.name(), request.birthDate(), request.gender(), request.phone());
        return new StartResponse(reference);
    }

    /**
     * @param name      실명(필수)
     * @param birthDate 생년월일(필수, yyyy-MM-dd)
     * @param gender    성별(필수, M/F)
     * @param phone     휴대폰번호(선택)
     */
    public record StartRequest(String name, LocalDate birthDate, Gender gender, String phone) {
        void validate() {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("name은 필수입니다");
            if (birthDate == null) throw new IllegalArgumentException("birthDate는 필수입니다");
            if (gender == null) throw new IllegalArgumentException("gender는 필수입니다(M 또는 F)");
        }
    }

    public record StartResponse(String reference) {
    }
}
