package com.example.demo.module.identity.application.account;

import com.example.demo.module.identity.application.auth.token.InvalidAccessTokenException;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

/**
 * "내 계정" 조회(ADR-0002 읽기 측). 명령은 JPA, <b>조회는 jOOQ 동적 타입</b>(정적 코드젠 미사용).
 * 여러 테이블(users·local_credentials·social_accounts)을 가볍게 읽어 화면용 뷰로 조립한다.
 */
@Service
public class AccountQueryService {

    private final DSLContext dsl;

    public AccountQueryService(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Transactional(readOnly = true)
    public AccountsResponse findMyAccounts(UUID userId) {
        Field<UUID> userIdField = field("user_id", UUID.class);

        // users — 이름(토큰이 가리키는 사용자는 존재해야 한다).
        Field<String> nameField = field("name", String.class);
        Record userRow = dsl.select(nameField)
                .from(table("users"))
                .where(field("id", UUID.class).eq(userId))
                .fetchOne();
        if (userRow == null) {
            throw new InvalidAccessTokenException("사용자를 찾을 수 없습니다");
        }

        // local_credentials — 0..1
        Field<String> loginIdField = field("login_id", String.class);
        Record localRow = dsl.select(loginIdField)
                .from(table("local_credentials"))
                .where(userIdField.eq(userId))
                .fetchOne();
        AccountsResponse.LocalAccount local =
                localRow == null ? null : new AccountsResponse.LocalAccount(localRow.get(loginIdField));

        // social_accounts — 0..N (연결 순)
        Field<String> providerField = field("provider", String.class);
        List<AccountsResponse.SocialAccount> socials = dsl.select(providerField)
                .from(table("social_accounts"))
                .where(userIdField.eq(userId))
                .orderBy(field("created_at"))
                .fetch(providerField)
                .stream()
                .map(AccountsResponse.SocialAccount::new)
                .toList();

        return new AccountsResponse(userId, userRow.get(nameField), local, socials);
    }
}
