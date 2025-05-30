#!/bin/bash
set -e

# Source environment variables if .env exists
if [ -f /.devcontainer/.env ]; then
  set -a
  # Use . instead of source for better compatibility
  . /.devcontainer/.env
  set +a
fi

# Run flyway migrate
flyway -configFiles=/workspace/flyway/conf/flyway.conf migrate