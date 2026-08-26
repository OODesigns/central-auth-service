#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

env -u DB_HOST -u DB_PORT -u DB_USER -u DB_PASSWORD -u APP_DB -u APP_PASSWORD \
    -u POSTGRES_USER -u POSTGRES_PASSWORD -u API_USER -u API_PASSWORD \
    ./gradlew clean test integrationTest dependencies

if [[ "${INCLUDE_DB_TESTS:-false}" == "true" ]]; then
    RUN_DATABASE_TESTS=true ./gradlew databaseIntegrationTest -PincludeDbTests
fi

env -u DB_HOST -u DB_PORT -u DB_USER -u DB_PASSWORD -u APP_DB -u APP_PASSWORD \
    -u POSTGRES_USER -u POSTGRES_PASSWORD -u API_USER -u API_PASSWORD \
    ./gradlew jacocoTestCoverageVerification

if [[ "${SKIP_SUPPLY_CHAIN_SCAN:-false}" != "true" ]]; then
    command -v osv-scanner >/dev/null || {
        echo "osv-scanner is required; set SKIP_SUPPLY_CHAIN_SCAN=true only for an intentional partial check" >&2
        exit 1
    }
    osv-scanner scan source -r .
fi

if [[ "${SKIP_CONTAINER_SCAN:-false}" != "true" ]]; then
    command -v docker >/dev/null || {
        echo "docker is required; set SKIP_CONTAINER_SCAN=true only for an intentional partial check" >&2
        exit 1
    }
    command -v trivy >/dev/null || {
        echo "trivy is required; set SKIP_CONTAINER_SCAN=true only for an intentional partial check" >&2
        exit 1
    }
    image="central-auth-service:security-check"
    docker build --tag "${image}" .
    trivy image --exit-code 1 --severity HIGH,CRITICAL "${image}"
fi