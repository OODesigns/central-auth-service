#!/bin/bash
set -e

# This script is automatically executed by the Postgres container during initialization
# 
# ROLE DISTINCTION:
#   POSTGRES_USER  = PostgreSQL server administrator (superuser)
#                    Used only for initialization and schema management
#                    Connection variables: POSTGRES_USER, POSTGRES_PASSWORD, POSTGRES_DB
#   
#   APP_USER       = Limited application service role (non-superuser)
#                    Used by the application at runtime to connect and query
#                    Has minimal privileges: SELECT, INSERT, UPDATE, DELETE on tables
#                    Cannot create/drop databases, roles, or schemas
#                    Connection variables: APP_USER, APP_PASSWORD, APP_DB

# Step 1: Create the limited APP_USER role (non-admin)
# The default "postgres" database is used for these initial setup operations
# since it always exists and we need admin privileges to create roles

echo "----> Step 1: Creating limited application role (if not exists)"
psql --username="${POSTGRES_USER}" --dbname="${POSTGRES_DB}" <<-EOSQL
  DO \$\$
  BEGIN
    IF NOT EXISTS (
      SELECT FROM pg_roles WHERE rolname = '${APP_USER}'
    ) THEN
      CREATE ROLE ${APP_USER}
        WITH LOGIN
             PASSWORD '${APP_PASSWORD}'
             NOSUPERUSER          -- APP_USER cannot create/drop databases or roles
             NOCREATEDB           -- Cannot create databases
             NOCREATEROLE         -- Cannot create other roles
             NOREPLICATION;       -- Cannot set up replication
    END IF;
  END
  \$\$;
EOSQL

echo "----> Step 2: Creating application database (owned by POSTGRES_USER admin)"
DB_EXISTS=$(
  psql \
    --username="${POSTGRES_USER}" \
    --dbname="${POSTGRES_DB}" \
    --tuples-only \
    --no-align \
    -c "SELECT 1 FROM pg_database WHERE datname='${APP_DB}';"
)

# (2) If it doesn't exist, create it (owned by POSTGRES_USER, not APP_USER)
if [[ -z "${DB_EXISTS}" ]]; then
  echo "Creating database \"${APP_DB}\" owned by POSTGRES_USER (admin)…"
  psql --username="${POSTGRES_USER}" --dbname="${POSTGRES_DB}" <<-EOSQL
CREATE DATABASE ${APP_DB}
  WITH OWNER = '${POSTGRES_USER}'
       ENCODING = 'UTF8'
       LC_COLLATE = 'C.utf8'
       LC_CTYPE = 'C.utf8';
EOSQL

  echo "Database ${APP_DB} created (owned by admin user)."
else
  echo "Database \"${APP_DB}\" already exists; skipping creation."
fi

echo "----> Step 3: Creating auth schema in application database"
psql --username="${POSTGRES_USER}" --dbname="${APP_DB}" <<-EOSQL
  CREATE SCHEMA IF NOT EXISTS auth;
EOSQL

echo "----> Step 4: Granting minimal required privileges to APP_USER"
echo "      (POSTGRES_USER retains full admin access)"
psql --username="${POSTGRES_USER}" --dbname="${APP_DB}" <<-EOSQL
  -- APP_USER privileges: SELECT, INSERT, UPDATE, DELETE only
  -- (Cannot: CREATE, DROP, ALTER, or manage roles)
  
  GRANT CONNECT ON DATABASE ${APP_DB} TO ${APP_USER};
  GRANT USAGE   ON SCHEMA public TO ${APP_USER};
  GRANT USAGE   ON SCHEMA auth TO ${APP_USER};

  -- Grant CRUD on all existing tables
  GRANT SELECT, INSERT, UPDATE, DELETE
    ON ALL TABLES IN SCHEMA public
    TO ${APP_USER};

  -- Ensure future tables also inherit these privileges
  ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE
    ON TABLES TO ${APP_USER};

  -- Grant execute on auth schema functions (read-only operations)
  GRANT EXECUTE
    ON ALL FUNCTIONS IN SCHEMA auth
    TO ${APP_USER};

  -- Ensure future functions in auth schema inherit these privileges
  ALTER DEFAULT PRIVILEGES IN SCHEMA auth
    GRANT EXECUTE
    ON FUNCTIONS TO ${APP_USER};
EOSQL

echo "----> Database initialization complete"
echo "      POSTGRES_USER (admin) can manage schema and roles"
echo "      APP_USER (service) can query and modify data only"