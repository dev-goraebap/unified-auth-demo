#!/usr/bin/env bash
# 개발 DB 데이터 초기화 — 데이터 테이블만 비운다(스키마·Flyway 이력은 보존).
# 사용: ./scripts/dev-reset.sh
# 접속 정보는 환경변수로 덮어쓸 수 있다: DB_CONTAINER, DB_USER, DB_NAME
set -euo pipefail

CONTAINER="${DB_CONTAINER:-postgres-local}"
DB_USER="${DB_USER:-postgres}"
DB_NAME="${DB_NAME:-unified_auth}"

echo "▶ ${CONTAINER}/${DB_NAME} 데이터 초기화 중…"
docker exec "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" \
  -c "TRUNCATE TABLE users, local_credentials, social_accounts, refresh_tokens, verification RESTART IDENTITY CASCADE;"

echo "✓ 완료 — 스키마·Flyway 이력은 유지됨. 남은 행 수:"
docker exec "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -c \
  "SELECT 'users='||count(*) FROM users
   UNION ALL SELECT 'local_credentials='||count(*) FROM local_credentials
   UNION ALL SELECT 'social_accounts='||count(*) FROM social_accounts
   UNION ALL SELECT 'refresh_tokens='||count(*) FROM refresh_tokens
   UNION ALL SELECT 'verification='||count(*) FROM verification;"
