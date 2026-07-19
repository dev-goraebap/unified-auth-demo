package com.example.demo.module.identity.application.account;

import java.util.List;
import java.util.UUID;

/**
 * "내 계정" 조회 결과(읽기 모델). 한 사람(DI 앵커)에 묶인 로컬·소셜 계정을 한눈에 보여준다.
 * 계정연결 화면이 소비한다.
 *
 * @param userId  사용자 id
 * @param name    본인인증으로 확인된 이름
 * @param local   로컬 계정(없으면 null)
 * @param socials 연결된 소셜 계정 목록(연결 순)
 */
public record AccountsResponse(UUID userId, String name, LocalAccount local, List<SocialAccount> socials) {

    public record LocalAccount(String loginId) {
    }

    public record SocialAccount(String provider) {
    }
}
