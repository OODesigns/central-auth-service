#!/usr/bin/env bash
set -euo pipefail

batch_size="${RATE_LIMIT_CLEANUP_BATCH_SIZE:-1000}"
retention_hours="${AUDIT_RETENTION_HOURS:-2160}"

: "${DB_HOST:?DB_HOST is required}"
: "${DB_PORT:?DB_PORT is required}"
: "${APP_DB:?APP_DB is required}"
: "${MAINTENANCE_USER:?MAINTENANCE_USER is required}"
: "${MAINTENANCE_PASSWORD:?MAINTENANCE_PASSWORD is required}"

export PGPASSWORD="${MAINTENANCE_PASSWORD}"

psql --host "${DB_HOST}" --port "${DB_PORT}" --dbname "${APP_DB}" --username "${MAINTENANCE_USER}" \
  -v ON_ERROR_STOP=1 \
  -v batch_size="${batch_size}" \
  -v audit_before="$(date -u -d "-${retention_hours} hours" '+%Y-%m-%d %H:%M:%S%z')" \
  -c "SELECT api_schema.cleanup_expired_login_rate_limits(:'batch_size');" \
  -c "SELECT api_schema.cleanup_audit_logs(:'audit_before'::timestamptz, :'batch_size');"