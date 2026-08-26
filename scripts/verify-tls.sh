#!/usr/bin/env bash
set -euo pipefail

host="${1:?usage: verify-tls.sh HOST PORT [SERVER_NAME]}"
port="${2:?usage: verify-tls.sh HOST PORT [SERVER_NAME]}"
server_name="${3:-${host}}"

timeout "${TLS_CHECK_TIMEOUT_SECONDS:-10}" \
  openssl s_client -connect "${host}:${port}" -servername "${server_name}" \
  -verify_return_error -brief </dev/null