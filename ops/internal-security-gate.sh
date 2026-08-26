#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

: "${RELEASE_IMAGE_DIGEST:?RELEASE_IMAGE_DIGEST must be supplied by the approved internal runner}"
: "${DEPLOYMENT_APPROVAL_ID:?DEPLOYMENT_APPROVAL_ID must identify the approved change record}"

INCLUDE_DB_TESTS=true ./scripts/security-check.sh
./scripts/validate-deployment.sh
./scripts/verify-image-digest.sh "${RELEASE_IMAGE_DIGEST}"

echo "Internal security gate passed for ${RELEASE_IMAGE_DIGEST} with approval ${DEPLOYMENT_APPROVAL_ID}."
