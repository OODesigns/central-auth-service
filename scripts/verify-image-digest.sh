#!/usr/bin/env bash
set -euo pipefail

image="${1:?usage: verify-image-digest.sh IMAGE EXPECTED_DIGEST}"
expected="${2:?usage: verify-image-digest.sh IMAGE EXPECTED_DIGEST}"

actual="$(docker image inspect "${image}" --format '{{index .RepoDigests 0}}' \
  | sed 's/.*@//')"

[[ "${actual}" == "${expected}" ]] || {
  printf 'image digest mismatch: expected %s, got %s\n' "${expected}" "${actual}" >&2
  exit 1
}