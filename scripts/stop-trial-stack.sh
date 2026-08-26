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

project_name="central-auth-service-trial"

if [[ -f .trial.env ]]; then
    "${compose[@]}" --env-file .trial.env -p "$project_name" --profile trial --profile observability down --remove-orphans
else
    docker ps -aq --filter "label=com.docker.compose.project=$project_name" | xargs -r docker rm -f
fi

if [[ "${REMOVE_TRIAL_DATA:-false}" == "true" ]]; then
    if [[ -f .trial.env ]]; then
        "${compose[@]}" --env-file .trial.env -p "$project_name" --profile trial --profile observability down --volumes --remove-orphans
    fi
    rm -f .trial.env .trial-admin-password
    echo "Trial data and generated credentials removed."
fi