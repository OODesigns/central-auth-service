#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

./gradlew clean test integrationTest jacocoTestCoverageVerification
RUN_DATABASE_TESTS=true ./gradlew databaseIntegrationTest -PincludeDbTests

if [[ "${RUN_TLS_CHECK:-false}" == "true" ]]; then
  "${PWD}/scripts/verify-tls.sh" "${TLS_CHECK_HOST}" "${TLS_CHECK_PORT}" "${TLS_CHECK_SERVER_NAME:-${TLS_CHECK_HOST}}"
fi

docker compose config --quiet