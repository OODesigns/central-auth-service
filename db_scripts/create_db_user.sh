#!/bin/bash
# Script to create a Postgres-SQL database user securely

# Database connection parameters
DB_HOST=${DB_HOST:-localhost}
DB_PORT=${DB_PORT:-5432}
DB_NAME=${DB_NAME:-home_control}
ADMIN_USER=${ADMIN_USER:-postgres}

# App user parameters
APP_USER=${APP_USER:-app_user}

# Prompt for admin password if needed
read -rsp "Enter password for Postgres-SQL admin user '$ADMIN_USER': " ADMIN_PASSWORD
echo

# Prompt for the new app user password
read -rsp "Enter a strong password for the app user '$APP_USER': " APP_USER_PASSWORD
echo
read -rsp "Confirm password: " APP_USER_PASSWORD_CONFIRM
echo

# Check if passwords match
if [[ "$APP_USER_PASSWORD" != "$APP_USER_PASSWORD_CONFIRM" ]]; then
    echo "Error: Passwords do not match."
    exit 1
fi

# Check if password is empty
if [[ -z "$APP_USER_PASSWORD" ]]; then
    echo "Error: Password cannot be empty."
    exit 1
fi

# Create SQL script
SQL_SCRIPT=$(cat <<EOF
DO
\$do\$
DECLARE
    app_username TEXT := '$APP_USER';
    app_password TEXT := '$APP_USER_PASSWORD';
BEGIN
   IF NOT EXISTS (
      SELECT FROM pg_catalog.pg_roles
      WHERE  rolname = app_username) THEN

      -- Create the role with LOGIN privilege
      EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', app_username, app_password);
      RAISE NOTICE 'Role ''%'' created with the provided password.', app_username;
   ELSE
      -- Update password for existing role
      EXECUTE format('ALTER ROLE %I WITH PASSWORD %L', app_username, app_password);
      RAISE NOTICE 'Role ''%'' already exists. Password has been updated.', app_username;
   END IF;
END
\$do\$;

-- Grant the ability for this new user to connect to the database
GRANT CONNECT ON DATABASE $DB_NAME TO $APP_USER;
EOF
)

# Execute the SQL script
echo "Creating/updating user '$APP_USER'..."
if PGPASSWORD="$ADMIN_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$ADMIN_USER" -d "$DB_NAME" -c "$SQL_SCRIPT"; then
    echo "User '$APP_USER' has been successfully created/updated."
    echo "Next steps:"
    echo "1. Configure your application to use this username and password."
    echo "2. Run schema creation/migration scripts to grant appropriate permissions."
else
    echo "Error: Failed to create/update user. Check the error message above."
    exit 1
fi
