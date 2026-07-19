package com.example.demo.module.identity.application.account;

import com.example.demo.module.identity.application.auth.token.BearerTokenAuthenticator;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * 내 계정 조회 엔드포인트(보호 리소스). Access Token으로 사용자를 식별해 연결된 계정을 돌려준다.
 * 계정연결 화면이 사용한다.
 */
@RestController
@RequestMapping("/api/users/me/accounts")
public class AccountQueryController {

    private final AccountQueryService accountQueryService;
    private final BearerTokenAuthenticator authenticator;

    public AccountQueryController(AccountQueryService accountQueryService,
                                  BearerTokenAuthenticator authenticator) {
        this.accountQueryService = accountQueryService;
        this.authenticator = authenticator;
    }

    @GetMapping
    public AccountsResponse myAccounts(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        UUID userId = authenticator.authenticate(authorization, Instant.now());
        return accountQueryService.findMyAccounts(userId);
    }
}
