package com.example.demo.module.identity.infrastructure.verification;

import com.example.demo.module.identity.application.verification.IdentityVerificationProvider;
import com.example.demo.module.identity.application.verification.VerificationNotFoundException;
import com.example.demo.module.identity.application.verification.VerificationResult;
import com.example.demo.module.identity.domain.user.Gender;
import com.example.demo.module.identity.domain.verification.Verification;
import com.example.demo.module.identity.domain.verification.VerificationPurpose;
import com.example.demo.module.identity.domain.verification.VerificationRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;

/**
 * 데모용 Mock 본인인증(ADR-0004). 애그리게이터(Bootpay·PortOne) 패턴을 흉내낸다.
 *
 * <ul>
 *   <li>{@link #startVerification} — 가짜 인증창이 받은 이름/생년월일/성별/휴대폰을
 *       verification 테이블에 임시저장하고 <b>가짜 식별자(reference)</b>를 돌려준다.
 *       실제 연동에서는 이 단계가 사라지고 프론트가 애그리게이터 SDK를 직접 호출한다.</li>
 *   <li>{@link #verify} — reference로 저장값을 꺼내 DI/CI를 <b>결정적</b>으로 합성한다.
 *       같은 사람 정보 → 항상 같은 DI(재가입/기존계정 연결 재현).</li>
 * </ul>
 *
 * {@code auth.pass.provider=mock}(기본)일 때만 빈으로 등록된다. 실제 연동 시 이 빈 대신
 * BootpayProvider/PortOneProvider가 {@link IdentityVerificationProvider}를 구현한다.
 */
@Component
@ConditionalOnProperty(name = "auth.pass.provider", havingValue = "mock", matchIfMissing = true)
public class MockVerificationProvider implements IdentityVerificationProvider {

    /** DI/CI를 실제 PASS 형식과 같은 길이(Base64 88자)로 맞추기 위해 SHA-512(64바이트)를 쓴다.
     *  ADR-0004 본문은 SHA-256으로 기술했으나, 컬럼 호환(varchar(88))·"실제와 동일 길이"
     *  목적을 우선해 88자를 생성하는 SHA-512를 채택한다. 어차피 Mock 전용 합성값이다. */
    private static final String HASH_ALGORITHM = "SHA-512";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final VerificationRepository verificationRepository;
    private final ObjectMapper objectMapper;
    private final MockPassProperties properties;

    public MockVerificationProvider(VerificationRepository verificationRepository,
                                    ObjectMapper objectMapper,
                                    MockPassProperties properties) {
        this.verificationRepository = verificationRepository;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * (Mock 전용) 가짜 인증창 제출 → 입력값을 임시저장하고 reference를 발급한다.
     *
     * @return 프론트에 돌려줄 가짜 식별자(reference)
     */
    @Transactional
    public String startVerification(String name, LocalDate birthDate, Gender gender, String phone) {
        String reference = newReference();
        String payload = writePayload(new MockPassPayload(name, birthDate, gender, phone));
        Instant expiresAt = Instant.now().plus(properties.ttl());

        Verification verification =
                Verification.issue(VerificationPurpose.PASS_RESULT, reference, payload, expiresAt);
        verificationRepository.save(verification);
        return reference;
    }

    @Override
    @Transactional(readOnly = true)
    public VerificationResult verify(String reference) {
        Verification verification = verificationRepository.findByReference(reference)
                .filter(v -> v.getPurpose() == VerificationPurpose.PASS_RESULT)
                .orElseThrow(() -> new VerificationNotFoundException("본인인증 정보를 찾을 수 없습니다"));

        if (verification.isExpired(Instant.now())) {
            throw new VerificationNotFoundException("본인인증이 만료되었습니다. 다시 시도해 주세요");
        }

        MockPassPayload payload = readPayload(verification.getPayload());
        String identityCore = payload.name() + "|" + payload.birthDate() + "|" + payload.gender();

        // 휴대폰번호는 DI 재료에서 제외 — 실제 PASS에서 DI는 번호가 바뀌어도 불변이기 때문(ADR-0004).
        String di = hash88(properties.siteSalt() + identityCore);
        String ci = hash88(properties.commonSalt() + identityCore);

        return new VerificationResult(di, ci, payload.name(), payload.birthDate(), payload.gender(), payload.phone());
    }

    /** SecureRandom 48바이트 → URL-safe Base64(64자). varchar(128) 이내. */
    private String newReference() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Base64(SHA-512(input)) — 88자. 실제 PASS DI 길이와 동일. */
    private String hash88(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(HASH_ALGORITHM + " 미지원 — JVM 구성 오류", e);
        }
    }

    // Jackson 3(tools.jackson)은 체크 예외를 던지지 않는다(JacksonException=unchecked).
    private String writePayload(MockPassPayload payload) {
        return objectMapper.writeValueAsString(payload);
    }

    private MockPassPayload readPayload(String json) {
        return objectMapper.readValue(json, MockPassPayload.class);
    }
}
