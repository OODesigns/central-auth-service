#!/bin/bash
set -e

# Source environment variables if .env exists
# Source environment variables if .env exists
if [ -f /workspace/.devcontainer/.env ]; then
  set -a
  . /workspace/.devcontainer/.env
  set +a
fi

# Run flyway migrate
flyway -configFiles=/workspace/flyway/conf/flyway.conf migrate
