-- "로그인 상태 유지"(remember-me) 지원: RFT 쿠키를 영속(true)/세션(false)으로 구울지 저장한다.
-- 회전 시 이 값을 승계해 쿠키 종류를 유지한다(ADR-0006).
-- 기존 행은 현재 동작(항상 영속)을 보존하도록 DEFAULT true.
ALTER TABLE refresh_tokens ADD COLUMN remember boolean NOT NULL DEFAULT true;
