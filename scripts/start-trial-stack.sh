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

environment_file=".trial.env"
password_file=".trial-admin-password"
project_name="central-auth-service-trial"
trial_database_port="${TRIAL_POSTGRES_HOST_PORT:-55432}"
fresh_credentials=false

if [[ ! -f "$environment_file" ]]; then
    fresh_credentials=true
    admin_password="$(openssl rand -hex 24)"
    admin_hash="$(./gradlew --quiet generateDatabaseTestBcryptHash -PbcryptPassword="$admin_password")"
    admin_hash_for_compose="${admin_hash//\$/\$\$}"
    cat > "$environment_file" <<EOF
POSTGRES_USER=postgres
POSTGRES_PASSWORD=A!$(openssl rand -hex 24)
POSTGRES_DB=postgres
POSTGRES_HOST_PORT=$trial_database_port
APP_DB=auth_db
API_USER=app_user
API_PASSWORD=A!$(openssl rand -hex 24)
MAINTENANCE_USER=maintenance_user
MAINTENANCE_PASSWORD=A!$(openssl rand -hex 24)
ADMIN_PASSWORD_HASH=$admin_hash_for_compose
JWT_SECRET=$(openssl rand -hex 32)
JWT_ACTIVE_KEY_ID=JWT_SECRET
JWT_PREVIOUS_KEY_IDS=
TOTP_ENCRYPTION_KEY=$(openssl rand -hex 32)
KEYSTORE_PASSWORD=A!$(openssl rand -hex 24)
TRUSTSTORE_PASSWORD=A!$(openssl rand -hex 24)
ALLOW_PLAINTEXT=true
GRPC_REFLECTION_ENABLED=true
GRPC_HEALTH_ENABLED=true
GRPC_HOST_PORT=50051
GRPCUI_HOST_PORT=8080
PROMETHEUS_HOST_PORT=9090
GRAFANA_HOST_PORT=3000
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=A!$(openssl rand -hex 24)
PROMETHEUS_RETENTION=1d
DEPLOYMENT_ENVIRONMENT=trial
EOF
    umask 077
    printf '%s\n' "$admin_password" > "$password_file"
    chmod 600 "$environment_file" "$password_file"
elif ! grep -q '^POSTGRES_HOST_PORT=' "$environment_file"; then
    printf '\nPOSTGRES_HOST_PORT=%s\n' "$trial_database_port" >> "$environment_file"
fi

if [[ "$fresh_credentials" == "true" ]]; then
    "${compose[@]}" --env-file "$environment_file" -p "$project_name" --profile trial --profile observability down --volumes --remove-orphans >/dev/null 2>&1 || true
fi

"${compose[@]}" --env-file "$environment_file" -p "$project_name" --profile trial --profile observability up -d --build

echo "Trial stack is starting."
echo "gRPC explorer: http://127.0.0.1:8080"
echo "Prometheus:    http://127.0.0.1:9090"
echo "Grafana:       http://127.0.0.1:3000"
echo "Admin password is stored only in $password_file"
echo "Use ./scripts/stop-trial-stack.sh to stop the stack."

if [[ "${TRIAL_RUN_SMOKE_TEST:-true}" == "true" ]]; then
    echo "Running the real trial smoke test: 100 users, login, update, delete, and metrics."
    ./gradlew --quiet smokeTest
    echo "Trial smoke test passed."
else
    echo "Trial smoke test skipped (set TRIAL_RUN_SMOKE_TEST=true to run it)."
fi