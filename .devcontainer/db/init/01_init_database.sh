#!/bin/bash
set -e

# This script is automatically executed by the Postgres container

# Connect to the “default” database that always exists (often "postgres" or whatever
# you passed as POSTGRES_DB). Since this DB is guaranteed to exist at this point,
# you won’t get a “database does not exist” error.

echo "----> Creating application role (if not exists)"
psql --username="${POSTGRES_USER}" --dbname="${POSTGRES_DB}" <<-EOSQL
  DO \$\$
  BEGIN
    IF NOT EXISTS (
      SELECT FROM pg_roles WHERE rolname = '${APP_USER}'
    ) THEN
      CREATE ROLE ${APP_USER}
        WITH LOGIN
             PASSWORD '${APP_PASSWORD}'
             NOSUPERUSER
             NOCREATEDB
             NOCREATEROLE
             NOREPLICATION;
    END IF;
  END
  \$\$;
EOSQL

echo "----> Creating application database (if not exists)"
DB_EXISTS=$(
  psql \
    --username="${POSTGRES_USER}" \
    --dbname="${POSTGRES_DB}" \
    --tuples-only \
    --no-align \
    -c "SELECT 1 FROM pg_database WHERE datname='${APP_DB}';"
)

# (2) If it doesn't exist, create it
if [[ -z "${DB_EXISTS}" ]]; then
  echo "Creating database \"${APP_DB}\" owned by \"${POSTGRES_USER}\"…"
  psql --username="${POSTGRES_USER}" --dbname="${POSTGRES_DB}" <<-EOSQL
CREATE DATABASE ${APP_DB}
  WITH OWNER = '${POSTGRES_USER}'
       ENCODING = 'UTF8'
       LC_COLLATE = 'C.utf8'
       LC_CTYPE = 'C.utf8';
EOSQL

  echo "Database ${APP_DB} created."
else
  echo "Database \"${APP_DB}\" already exists; skipping creation."
fi

echo "----> Granting privileges on ${APP_DB} to ${APP_USER}"
psql --username="${POSTGRES_USER}" --dbname="${POSTGRES_DB}" <<-EOSQL
  -- Allow the app user to connect and use the public schema
  GRANT CONNECT ON DATABASE ${APP_DB} TO ${APP_USER};
  GRANT USAGE   ON SCHEMA public TO ${APP_USER};

  -- Grant CRUD on all existing tables
  GRANT SELECT, INSERT, UPDATE, DELETE
    ON ALL TABLES IN SCHEMA public
    TO ${APP_USER};

  -- Ensure future tables also inherit these privileges
  ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE
    ON TABLES TO ${APP_USER};
EOSQL