# Home Control System

## Environment Parameters

The following environment variables are required to run the Home Control System:

### Database Configuration
- `POSTGRES_USER` - PostgreSQL database user
- `POSTGRES_PASSWORD` - PostgreSQL database password
- `POSTGRES_DB` - PostgreSQL database name
- `APP_DB` - Application database name
- `APP_USER` - Application database user
- `APP_PASSWORD` - Application database password

### Admin User Configuration
- `ADMIN_PASSWORD_HASH` - Admin user password (bcrypt-hashed) for Flyway database initialization
  - Used by Flyway migrations to seed the admin user in the database
  - Must be a bcrypt hash (e.g., `$2a$10$...`)
  - Example: `$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcg7b3XeKeUxWdeS86E36P4/liK`
- `ADMIN_PASSWORD_PLAIN` - Admin user plain text password for testing and manual login
  - Used by integration tests and for local development login
  - Example: `admin_initial_password`

### Application Configuration
- `DATABASE_URL` - JDBC connection string (format: `jdbc:postgresql://localhost:5432/{POSTGRES_DB}`)
- `NODE_ENV` - Environment type (e.g., `development`, `production`)
- `JWT_SECRET` - Secret key for JWT token signing

### Security Configuration
- `KEYSTORE_PASSWORD` - Password for the application's keystore file (.jks or .p12) containing the private key and certificate
- `TRUSTSTORE_PASSWORD` - Password for the truststore file containing trusted Certificate Authorities (CAs) or server certificates

## Database Migrations with Flyway

This project uses **Flyway** for database schema migrations and versioning.

### Migration Structure

Migrations are located in `.devcontainer/flyway/`:

- `conf/flyway.conf` - Flyway configuration file with database connection settings
- `sql/` - SQL migration scripts
  - Naming convention: `V{version}__{description}.sql` (e.g., `V1__init_schema.sql`, `V1_1__seed_auth_data.sql`)
  - Versions must be in ascending order
  - Double underscore separates version from description

### How Migrations Work

1. Migrations are automatically executed during Docker container startup via the Flyway service defined in `docker-compose.yml`
2. Flyway tracks applied migrations in the `flyway_schema_history` table
3. Only pending migrations (not already applied) are executed
4. Migration status can be reviewed in `MIGRATION_REVIEW.md`

### Creating New Migrations

1. Create a new SQL file in `.devcontainer/flyway/sql/` following the naming convention:
   - Example: `V2__add_user_roles.sql`
2. Write your SQL statements in the file
3. Ensure the version number is higher than existing migrations
4. When the Docker container restarts, Flyway will automatically detect and execute the new migration

### Configuration

Flyway configuration is defined in `.devcontainer/flyway/conf/flyway.conf`:
- Database URL, user, and password are sourced from environment variables
- Placeholder variables (like `ADMIN_PASSWORD_HASH`) can be used in SQL files with `${PLACEHOLDER_NAME}` syntax
- The `ADMIN_PASSWORD_HASH` placeholder is injected into migration scripts to securely seed the admin user

## Migration Best Practices

Follow these guidelines when creating new database migrations:

### Function Security
Always explicitly manage function permissions with both REVOKE and GRANT:
```sql
REVOKE ALL ON FUNCTION schema.function_name(...) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION schema.function_name(...) TO app_user;
```
This prevents unintended access and ensures only authorized roles can execute the function.

### Placeholder Validation
Document any Flyway placeholders required in your migration. Add a header comment:
```sql
-- REQUIRED: Set flyway.placeholders.variable_name=value
-- Example: -Dflyway.placeholders.variable_name=production_value
```

### Role Permission Clarity
Explicitly document role permission assignments, especially when intentionally empty:
```sql
-- Role has no special permissions (intentional - view-only access handled at app layer)
```

### Idempotency
All migrations must be safe to run multiple times without errors:
- Use `CREATE TABLE IF NOT EXISTS` for tables
- Use `CREATE SCHEMA IF NOT EXISTS` for schemas
- Use `ON CONFLICT ... DO NOTHING` for inserts
- Use `DROP IF EXISTS` for drops

### Index Naming
Use consistent naming conventions for indexes:
```sql
CREATE INDEX idx_table_column ON table(column);
```

### Code Organization
Keep related functionality organized with clear section headers:
```sql
-- ============================================================================
-- AUDIT TRIGGER FUNCTIONS
-- ============================================================================
```

### Security Principles Verified
- ✅ Passwords are hashed and injected via Flyway placeholders (never hardcoded)
- ✅ Sensitive operations have audit triggers for immutable audit logs
- ✅ Role-based access control is enforced at the database level
- ✅ `app_user` role has minimal required permissions only