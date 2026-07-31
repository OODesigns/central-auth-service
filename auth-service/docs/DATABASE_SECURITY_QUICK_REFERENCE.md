# Database Security Quick Reference

## For Developers

### Connection String (application.properties)
```properties
# Updated to use app_auth role with function-based access
spring.datasource.username=app_auth
spring.datasource.password=${DB_APP_AUTH_PASSWORD:changeme}

# URL remains the same
spring.datasource.url=jdbc:postgresql://localhost:5432/cas
```

### Java Code Pattern: Calling Secure Functions

```java
// ❌ DON'T: Direct table access (not allowed anymore)
String query = "SELECT user_id, password_hash FROM users WHERE username = ?";
ResultSet rs = connection.prepareStatement(query).executeQuery();

// ✅ DO: Call auth_api functions (SECURITY DEFINER)
String query = "SELECT user_id, password_hash FROM auth_api.find_user_credentials(?)";
ResultSet rs = connection.prepareStatement(query).executeQuery();
```

### JOOQ Integration

```java
// Old pattern (direct table access)
// Users table was in 'public' schema
DSL.select()
   .from(PUBLIC.USERS)
   .where(PUBLIC.USERS.USERNAME.eq(username))
   .fetch();

// New pattern (function-based access)
// Functions are in 'auth_api' schema
DSL.selectFrom(table(name("auth_api.find_user_credentials"), Field.of("username")))
   .fetch();
```

### SQL Functions Available

| Function | Signature | Purpose | Schema |
|----------|-----------|---------|--------|
| find_user_credentials | `(username text)` | Get pwd hash for login | auth_api |
| get_user | `(user_id uuid)` | Get user + permissions | auth_api |
| get_totp_status | `(user_id uuid)` | Check if 2FA enabled | auth_api |
| encrypt_totp_secret | `(secret text, key text)` | Encrypt TOTP secret | auth_api |

### Audit Trail Access

```sql
-- View all login attempts
SELECT actor_id, action, created_at, metadata
FROM auth_api.audit_logs  -- ❌ This won't work!
-- auth_private.audit_logs is not accessible to app_auth

-- Solution: Create a new auth_api function for safe audit access
CREATE FUNCTION auth_api.get_audit_logs(
  p_action VARCHAR(50) DEFAULT NULL,
  p_limit INTEGER DEFAULT 100
)
RETURNS TABLE (actor_id uuid, action varchar, created_at timestamptz, metadata jsonb)
SECURITY DEFINER
SET search_path = pg_catalog, auth_private
AS $$
  SELECT actor_id, action, created_at, metadata
  FROM auth_private.audit_logs
  WHERE (p_action IS NULL OR action = p_action)
  ORDER BY created_at DESC
  LIMIT p_limit;
$$;
```

### Troubleshooting

#### Error: "permission denied for schema auth_private"
```
ERROR: permission denied for schema auth_private
CONTEXT: PL/pgSQL function auth_api.find_user_credentials(text)
```
**Cause:** Connection using wrong role
**Fix:** Check `spring.datasource.username=app_auth` in properties

#### Error: "function auth.find_user_credentials does not exist"
```
ERROR: function auth.find_user_credentials(text) does not exist
```
**Cause:** Old function location (auth schema)
**Fix:** Update to `auth_api.find_user_credentials` (new location)

#### Error: "cannot execute SELECT in a read-only transaction"
**Cause:** Function being called in read-only context
**Fix:** Check transaction isolation level; functions should work in any mode

### Testing

```java
@Test
void testCanCallAuthApiFunctions() {
    // This should work - app_auth can execute functions
    List<UserCredentials> credentials = 
        jdbcTemplate.query(
            "SELECT * FROM auth_api.find_user_credentials(?)",
            new Object[]{"admin"},
            new RowMapper<UserCredentials>() {...}
        );
    assertNotNull(credentials);
}

@Test
void testCannotDirectlyAccessTables() {
    // This should fail - app_auth cannot SELECT tables
    assertThrows(DataAccessException.class, () -> {
        jdbcTemplate.query(
            "SELECT * FROM auth_private.users",
            new Object[]{},
            new RowMapper<User>() {...}
        );
    });
}
```

### Docker Development Setup

```bash
# Start database with proper roles
docker-compose up -d

# Verify roles are created
docker exec <postgres_container> psql -U postgres -c "\du"
# Should show: auth_owner (NOLOGIN), app_auth (LOGIN, inherited roles)

# Verify schemas exist
docker exec <postgres_container> psql -U postgres -c "\dn"
# Should show: auth_private, auth_api

# Test connection as app_auth
docker exec <postgres_container> psql -U app_auth -d cas -c "SELECT * FROM auth_api.find_user_credentials('admin');"
```

## For DBAs / DevOps

### Pre-Deployment Checklist

```bash
# 1. Backup current database
pg_dump cas > /backups/cas-pre-v2-migration.sql

# 2. Test migration in staging
flyway migrate -placeholders.ADMIN_PASSWORD_HASH=$ADMIN_HASH -url="jdbc:postgresql://staging:5432/cas"

# 3. Verify no errors
# Check: all 4 functions created in auth_api schema
# Check: all tables migrated to auth_private schema
# Check: app_auth can execute functions

# 4. Deploy to production
flyway migrate -placeholders.ADMIN_PASSWORD_HASH=$ADMIN_HASH -url="jdbc:postgresql://prod:5432/cas"

# 5. Post-deployment verification
psql -U postgres cas << EOF
  SELECT schema_name FROM information_schema.schemata WHERE schema_name IN ('auth_private', 'auth_api');
  SELECT function_name FROM information_schema.routines WHERE routine_schema IN ('auth_api');
  SELECT grantee, privilege_type FROM information_schema.role_table_grants WHERE table_schema = 'auth_private' AND grantee != 'postgres';
EOF
```

### Role Setup (Manual)

If roles aren't automatically created by migration:

```bash
# Connect as postgres superuser
psql -U postgres

# Create roles
CREATE ROLE auth_owner NOLOGIN;
CREATE ROLE app_auth WITH LOGIN PASSWORD 'your_password_here';

# Grant basic permissions
GRANT USAGE ON SCHEMA auth_private, auth_api TO app_auth;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA auth_api TO app_auth;

# Verify
\du  # List roles
\dn  # List schemas
```

### Monitoring

```bash
# Monitor failed authentication attempts
psql -U postgres cas << EOF
SELECT action, COUNT(*), MAX(created_at)
FROM auth_private.audit_logs
WHERE action LIKE '%PASSWORD%' OR action LIKE '%INVALID%'
GROUP BY action
ORDER BY MAX(created_at) DESC;
EOF

# Monitor function performance
psql -U postgres -d cas -c "SELECT * FROM pg_stat_statements WHERE query LIKE '%auth_api%' ORDER BY mean_exec_time DESC LIMIT 10;"

# Monitor role access patterns
psql -U postgres -d cas << EOF
SELECT usename, datname, application_name, query_start, state
FROM pg_stat_activity
WHERE usename = 'app_auth'
ORDER BY query_start DESC;
EOF
```

### Rollback Plan

If migration fails or must be rolled back:

```bash
# 1. Restore from backup
pg_restore /backups/cas-pre-v2-migration.sql

# 2. Or manually restore with Flyway
flyway clean  # ⚠️ WARNING: Deletes all schemas
flyway migrate -target=1.0  # Migrate only to V1

# 3. Verify old schema is restored
psql -U app_user -d cas -c "SELECT * FROM users LIMIT 1;"
```

## Schema Overview

```
┌─────────────────────────────────────────────────────────┐
│ PostgreSQL Database: cas                                │
└─────────────────────────────────────────────────────────┘
         │
    ┌────┴────┬───────────────┬──────────────────┐
    │          │               │                  │
┌───▼───┐ ┌───▼───────┐ ┌────▼──────┐ ┌──────▼──────┐
│public │ │auth_private│ │ auth_api  │ │ pg_catalog  │
├───────┤ ├───────────┤ ├───────────┤ └─────────────┘
│views  │ │ tables    │ │ functions │
│(compat)│ │ (data)    │ │ (entry    │
│       │ │ triggers  │ │  points)  │
└───────┘ │ (SECURITY │ │           │
          │  DEFINER) │ │           │
          │           │ │ ✓ EXECUTE │
          │ ✗ SELECT  │ │ by app_  │
          │ ✗ INSERT  │ │ auth      │
          │ ✗ UPDATE  │ │           │
          │ ✗ DELETE  │ └───────────┘
          └───────────┘

app_auth can:
 ✓ EXECUTE auth_api functions
 ✓ USE auth_api schema
 ✓ USE auth_private schema (for query resolution in functions)
 ✗ SELECT/INSERT/UPDATE/DELETE auth_private tables directly

auth_owner (SECURITY DEFINER):
 ✓ Owns all tables in auth_private
 ✓ Owns all functions in auth_api
 ✓ Runs all functions (no direct login)
 ✓ Controls what app_auth can access
```

## FAQs

**Q: Why NOLOGIN for auth_owner?**
A: Prevents accidental direct login. Only SECURITY DEFINER functions use this role.

**Q: Why separate schemas?**
A: Clear separation of concerns. Data layer (auth_private) vs API layer (auth_api).

**Q: Can I add more functions?**
A: Yes, create in auth_api schema with SECURITY DEFINER and locked search_path:
```sql
CREATE FUNCTION auth_api.my_new_function(...)
SECURITY DEFINER
SET search_path = pg_catalog, auth_private
AS $$ ... $$;

ALTER FUNCTION auth_api.my_new_function(...) OWNER TO auth_owner;
GRANT EXECUTE ON FUNCTION auth_api.my_new_function(...) TO app_auth;
```

**Q: What about audit logs?**
A: Triggers write to auth_private.audit_logs. Create auth_api functions for safe access.

**Q: Performance impact?**
A: <1%. SECURITY DEFINER + search_path locking is negligible.

**Q: Can I still use raw SQL?**
A: Only through auth_api functions. Direct table access is blocked.

