#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

if docker compose version >/dev/null 2>&1; then
    compose=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
    compose=(docker-compose)
else
    echo "Docker Compose is required. Install the Docker Compose plugin or docker-compose." >&2
    exit 1
fi

docker info >/dev/null 2>&1 || {
    echo "The Docker daemon is not available. Start Docker, then rerun this script." >&2
    exit 1
}

test_port="${DB_TEST_POSTGRES_HOST_PORT:-55432}"
project_name="cas-db-test-$$"
environment_file="$(mktemp "${TMPDIR:-/tmp}/cas-db-test.XXXXXX.env")"
admin_password="$(openssl rand -hex 24)"
postgres_password="A!$(openssl rand -hex 24)"
api_password="A!$(openssl rand -hex 24)"
maintenance_password="A!$(openssl rand -hex 24)"
jwt_secret="$(openssl rand -hex 32)"
totp_key="$(openssl rand -hex 32)"

cleanup() {
    if [[ "${KEEP_DB_TEST_ENV:-false}" != "true" ]]; then
        "${compose[@]}" --env-file "$environment_file" -p "$project_name" down --volumes --remove-orphans >/dev/null 2>&1 || true
        rm -f "$environment_file"
    else
        echo "Keeping test environment: $environment_file"
    fi
}
trap cleanup EXIT

admin_hash="$(./gradlew --quiet generateDatabaseTestBcryptHash -PbcryptPassword="$admin_password")"
admin_hash_for_compose="${admin_hash//\$/\$\$}"

cat > "$environment_file" <<EOF
POSTGRES_USER=postgres
POSTGRES_PASSWORD=$postgres_password
POSTGRES_DB=postgres
APP_DB=auth_db
API_USER=app_user
API_PASSWORD=$api_password
MAINTENANCE_USER=maintenance_user
MAINTENANCE_PASSWORD=$maintenance_password
ADMIN_PASSWORD_HASH=$admin_hash_for_compose
JWT_SECRET=$jwt_secret
JWT_ACTIVE_KEY_ID=JWT_SECRET
JWT_PREVIOUS_KEY_IDS=
TOTP_ENCRYPTION_KEY=$totp_key
KEYSTORE_PASSWORD=A!$(openssl rand -hex 24)
TRUSTSTORE_PASSWORD=A!$(openssl rand -hex 24)
POSTGRES_HOST_PORT=$test_port
ALLOW_PLAINTEXT=true
DEPLOYMENT_ENVIRONMENT=database-test
EOF

"${compose[@]}" --env-file "$environment_file" -p "$project_name" up -d db flyway
"${compose[@]}" --env-file "$environment_file" -p "$project_name" wait flyway

export DB_HOST=127.0.0.1
export DB_PORT="$test_port"
export APP_DB=auth_db
export DB_USER=app_user
export APP_PASSWORD="$api_password"
export POSTGRES_USER=postgres
export POSTGRES_PASSWORD="$postgres_password"
export API_USER=app_user
export API_PASSWORD="$api_password"
export ADMIN_PASSWORD_PLAIN="$admin_password"
export JWT_SECRET="$jwt_secret"
export TOTP_ENCRYPTION_KEY="$totp_key"

RUN_DATABASE_TESTS=true ./gradlew databaseIntegrationTest -PincludeDbTests