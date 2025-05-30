#!/bin/bash
set -e

# This script is automatically executed by the Postgres container

# 1) Create the non-superuser application role
psql --username "$APP_USER" --dbname="${POSTGRES_DB}" <<-EOSQL
  CREATE ROLE cas_app_user
    WITH LOGIN
         PASSWORD '${APP_PASSWORD}'
         NOSUPERUSER
         NOCREATEDB
         NOCREATEROLE
         NOREPLICATION;
EOSQL

# 2) Grant minimal privileges on the schema and its tables
psql --username "$APP_USER" --dbname="${POSTGRES_DB}" <<-EOSQL
  -- Allow the app user to connect and use the public schema
  GRANT CONNECT ON DATABASE auth_db TO cas_app_user;
  GRANT USAGE   ON SCHEMA public     TO cas_app_user;

  -- Grant CRUD on all existing tables
  GRANT SELECT, INSERT, UPDATE, DELETE
    ON ALL TABLES IN SCHEMA public
    TO cas_app_user;

  -- Ensure future tables also inherit these privileges
  ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE
    ON TABLES TO cas_app_user;
EOSQL


