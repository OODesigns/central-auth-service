#!/bin/bash
set -e

# ============================================================================
# PostgreSQL Container Initialization Script
# ============================================================================
# This script runs automatically when the PostgreSQL container starts for the
# first time. It only handles database creation. All role and schema management
# is delegated to Flyway migrations to maintain a single source of truth.
#
# ROLE DISTINCTION:
#   ${POSTGRES_USER}  = PostgreSQL superuser (admin)
#                       • Created by official postgres:15 image
#                       • Used ONLY for initialization and Flyway execution
#                       • Never used by the application
#
#   ${API_USER}       = Limited API service role (non-superuser)
#                       • Created by Flyway migration (V1_0_1__create_roles.sql)
#                       • Used by the application at runtime
#                       • Has minimal privileges (EXECUTE on api_schema.* only)
#                       • Cannot create/drop databases, roles, or schemas
#
# EXECUTION FLOW:
#   1. This script creates the application database
#   2. Flyway migrations handle all roles, schemas, and permissions
#   3. Application connects using ${API_USER} credentials
# ============================================================================
echo "----> Step 1: Creating application database (owned by POSTGRES_USER admin)"

# Check if database already exists (idempotent - safe to re-run)
# Returns "1" if database exists, empty string if not
DB_EXISTS=$(
  psql \
    --username="${POSTGRES_USER}" \
    --dbname="${POSTGRES_DB}" \
    --tuples-only \
    --no-align \
    -c "SELECT 1 FROM pg_database WHERE datname='${APP_DB}';"
)
# psql options explained:
#   --tuples-only:  Suppresses header and footer, returns only data rows
#   --no-align:     Removes formatting/padding for clean variable assignment
#   Result:         DB_EXISTS="1" if exists, "" if not

# Create database if it doesn't exist
# Owner: POSTGRES_USER (admin) will manage schema and roles
# Encoding: UTF-8 with C.utf8 locale for deterministic, portable behavior
if [[ -z "${DB_EXISTS}" ]]; then
  echo "Creating database \"${APP_DB}\" owned by POSTGRES_USER (admin)…"
  psql --username="${POSTGRES_USER}" --dbname="${POSTGRES_DB}" <<-EOSQL
CREATE DATABASE ${APP_DB}
  WITH OWNER = '${POSTGRES_USER}'
       -- ENCODING = 'UTF8': Full Unicode support for all text data
       ENCODING = 'UTF8'
       -- LC_COLLATE = 'C.utf8': Byte-order collation
       --   Ensures consistent sorting across all systems (dev, test, prod)
       --   Critical for cryptographic operations and deterministic queries
       LC_COLLATE = 'C.utf8'
       -- LC_CTYPE = 'C.utf8': Character classification in UTF-8
       --   'C' locale ensures consistent character properties across systems
       --   Not dependent on server locale settings
       LC_CTYPE = 'C.utf8';
EOSQL

  echo "Database ${APP_DB} created (owned by admin user)."
else
  echo "Database \"${APP_DB}\" already exists; skipping creation."
fi

echo "----> Step 2: Database initialization complete"
echo "      ✅ Database \"${APP_DB}\" created (owned by POSTGRES_USER admin)"
echo "      ✅ Awaiting Flyway migrations to configure roles and schemas"
echo ""
echo "      Flyway will:"
echo "      • Create api_role (${API_USER}) with LOGIN and security constraints"
echo "      • Create private_schema and api_schema"
echo "      • Create SECURITY DEFINER functions in api_schema"
echo "      • Grant api_role EXECUTE permissions on api_schema.* functions only"
