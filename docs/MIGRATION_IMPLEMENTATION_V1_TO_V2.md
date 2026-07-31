# Migration Implementation Guide: V1 → V2

## Overview

This guide walks through implementing the security hardening migration from the current schema (V1) to the hardened schema (V2) with role-based access control and SECURITY DEFINER functions.

## Timeline

| Phase | Duration | Activity |
|-------|----------|----------|
| 1. Preparation | 1 day | Backup, test migration in staging, review changes |
| 2. Deployment | 1-2 hours | Run migration, verify, update app code |
| 3. Verification | 1-2 hours | Test all functions, check audit logs |
| 4. Cleanup | 1 day | Remove old functions (optional, can keep for compat) |

## Phase 1: Preparation

### 1.1 Backup Current Database

```bash
# Create full backup
pg_dump -U postgres cas > /backups/cas-v1-full.sql

# Create backup of just schema
pg_dump -U postgres -s cas > /backups/cas-v1-schema.sql

# Create backup of just data
pg_dump -U postgres -a cas > /backups/cas-v1-data.sql

# Verify backups
ls -lh /backups/cas-v1-*
gzip /backups/cas-v1-*.sql
```

### 1.2 Test Migration in Staging

```bash
# Create staging environment
docker run -d --name cas-staging-db \
  -e POSTGRES_DB=cas \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  postgres:15

# Wait for startup
sleep 5

# Restore from backup
docker exec cas-staging-db \
  psql -U postgres -f /backups/cas-v1-full.sql.gz -d cas

# Run Flyway migration (just V2)
cd /mnt/data/projects/central-auth-service
./gradlew flywayMigrate \
  -Dflyway.url=jdbc:postgresql://localhost:5432/cas \
  -Dflyway.user=postgres \
  -Dflyway.password=postgres \
  -Dflyway.sqlMigrationPrefix=V2

# Check for errors
echo $?  # Should be 0

# Verify migration results
docker exec cas-staging-db psql -U postgres -d cas << EOF
  \dn  -- List schemas (should see auth_private, auth_api)
  \du  -- List roles (should see auth_owner, app_auth)
  SELECT * FROM information_schema.schemata WHERE schema_name IN ('auth_private', 'auth_api');
  SELECT routine_schema, routine_name FROM information_schema.routines WHERE routine_schema IN ('auth_api');
EOF
```

### 1.3 Test Application Code

```bash
# Update connection string temporarily
# spring.datasource.username=app_auth

# Run tests
./gradlew test

# Check for errors
# Look for: "permission denied", "function not found", etc.

# If errors:
# 1. Check function names (now auth_api.*)
# 2. Check search_path settings
# 3. Verify role permissions
```

### 1.4 Review Compatibility

```bash
# Check if application uses:
# - Direct table selects? → Create adapter functions
# - Stored procedures? → Migrate to auth_api schema
# - Views? → Update to reference auth_api functions or new tables

# Known areas to check:
grep -r "FROM users" src/main/java/  # Direct table access
grep -r "FROM roles" src/main/java/
grep -r "FROM permissions" src/main/java/
grep -r "SELECT \*" src/main/java/ --include="*.java"  # May be direct access
```

## Phase 2: Deployment

### 2.1 Pre-Deployment Checklist

- [ ] Backup completed and verified
- [ ] Staging migration successful
- [ ] Tests pass in staging
- [ ] Application code updated to use auth_api.*
- [ ] Database password for app_auth is secure
- [ ] Change control approved
- [ ] Rollback plan in place

### 2.2 Apply Migration

```bash
# Option A: Run Flyway from Gradle
cd /mnt/data/projects/central-auth-service
./gradlew flywayMigrate \
  -Dflyway.url=jdbc:postgresql://localhost:5432/cas \
  -Dflyway.user=postgres \
  -Dflyway.password=$POSTGRES_PASSWORD

# Option B: Manual SQL execution
psql -U postgres -d cas << EOF
  -- Set this before running migration
  SET ROLE postgres;
  \i .devcontainer/flyway/sql/V2__harden_security_schema_roles.sql
EOF

# Monitor progress
tail -f /var/log/postgresql/postgresql.log  # If available
```

### 2.3 Verify Migration Success

```bash
# Check for errors
psql -U postgres -d cas << EOF
  -- Should return no errors
  SELECT * FROM pg_stat_activity WHERE state = 'idle in transaction';
  
  -- Verify schemas created
  SELECT schema_name FROM information_schema.schemata 
  WHERE schema_name IN ('auth_private', 'auth_api');
  
  -- Verify roles created
  SELECT rolname, rolcanlogin FROM pg_roles 
  WHERE rolname IN ('auth_owner', 'app_auth');
  
  -- Verify tables migrated
  SELECT table_schema, COUNT(*) FROM information_schema.tables 
  GROUP BY table_schema ORDER BY table_schema;
  
  -- Verify functions created
  SELECT routine_schema, routine_name FROM information_schema.routines 
  WHERE routine_schema = 'auth_api' ORDER BY routine_name;
EOF
```

### 2.4 Update Application

```bash
# 1. Update database connection properties
# File: src/main/resources/application.properties
# OR: src/main/resources/application-prod.properties

# OLD:
spring.datasource.username=app_user

# NEW:
spring.datasource.username=app_auth

# 2. Update JOOQ or raw SQL queries
# OLD: SELECT * FROM users WHERE username = ?
# NEW: SELECT * FROM auth_api.find_user_credentials(?)

# 3. Rebuild and redeploy application
./gradlew build
# Deploy to environment
```

### 2.5 Monitor Initial Requests

```bash
# Watch for errors in application logs
tail -f logs/app.log | grep -i "error\|permission\|function"

# If errors occur:
# 1. Check function names (auth_api.* vs auth.*)
# 2. Check role of current user
# 3. Verify connection string
# 4. Roll back if necessary
```

## Phase 3: Verification

### 3.1 Functional Testing

```bash
# Test each API function
psql -U app_auth -d cas << EOF
  -- Should work: app_auth can execute
  SELECT * FROM auth_api.find_user_credentials('admin');
  
  -- Should fail: app_auth cannot select tables
  \try SELECT * FROM auth_private.users;
  
  -- Should fail: app_auth cannot select old schema
  \try SELECT * FROM public.users;
EOF

# Test with application
curl -X POST http://localhost:8080/api/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password"}'
  
# Expected: 200 OK (or 401 if creds wrong, but no permission error)
```

### 3.2 Audit Trail Check

```bash
# Verify audit logs are recording
psql -U postgres -d cas << EOF
  SELECT action, COUNT(*), MAX(created_at) 
  FROM auth_private.audit_logs 
  WHERE created_at > NOW() - INTERVAL '1 hour'
  GROUP BY action 
  ORDER BY MAX(created_at) DESC;
EOF

# Should see recent entries:
# USER_CREATED, USER_UPDATED, TOKEN_ISSUED, etc.
```

### 3.3 Performance Check

```bash
# Reset statistics
psql -U postgres -d cas -c "SELECT pg_stat_statements_reset();"

# Run some queries
for i in {1..100}; do
  curl -s http://localhost:8080/api/login \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"password"}' > /dev/null
done

# Check stats
psql -U postgres -d cas << EOF
  SELECT query, calls, total_time, mean_time 
  FROM pg_stat_statements 
  WHERE query LIKE '%auth_api%' 
  ORDER BY mean_time DESC LIMIT 5;
EOF

# Expected: mean_time should be <10ms for functions
```

### 3.4 Security Verification

```bash
# Test 1: app_auth cannot escalate privileges
psql -U app_auth -d cas << EOF
  -- Try to create a table (should fail)
  CREATE TABLE test_table (id uuid);
  
  -- Try to create a function (should fail)
  CREATE FUNCTION test_func() RETURNS void AS 'SELECT 1;' LANGUAGE sql;
  
  -- Try to grant permissions (should fail)
  GRANT ALL ON auth_private.users TO app_auth;
EOF
# All should fail with permission denied

# Test 2: Schema injection prevention
psql -U app_auth -d cas << EOF
  -- Create attacker schema with fake table
  CREATE SCHEMA attacker;
  CREATE TABLE attacker.users (user_id uuid);
  
  -- Try to trick function into using attacker's table
  -- (This should NOT work - function has locked search_path)
  SELECT * FROM auth_api.find_user_credentials('admin');
  -- Should return auth_private.users data, NOT attacker.users data
EOF

# Test 3: Verify SECURITY DEFINER
psql -U postgres -d cas << EOF
  -- Check function definition
  \df+ auth_api.find_user_credentials
  
  -- Should show:
  -- - "SECURITY DEFINER"
  -- - "SET search_path = pg_catalog, auth_private"
EOF
```

## Phase 4: Cleanup (Optional)

### 4.1 Remove Old Functions (if backward compat not needed)

```sql
-- Only after confirming all code uses new auth_api.* functions
DROP FUNCTION IF EXISTS auth.find_user_credentials(text);
DROP FUNCTION IF EXISTS auth.get_user(uuid);
DROP FUNCTION IF EXISTS auth.get_totp_status(uuid);
DROP FUNCTION IF EXISTS auth.encrypt_totp_secret(text, text);

-- Remove old schema views
DROP SCHEMA auth CASCADE;  -- ⚠️ WARNING: Also drops functions!
```

### 4.2 Compress Audit Logs (Archive Old Data)

```bash
# If audit_logs table is large, archive older entries
psql -U postgres -d cas << EOF
  -- Create archive table
  CREATE TABLE auth_private.audit_logs_archive AS 
  SELECT * FROM auth_private.audit_logs 
  WHERE created_at < NOW() - INTERVAL '6 months';
  
  -- Delete from main table
  DELETE FROM auth_private.audit_logs 
  WHERE created_at < NOW() - INTERVAL '6 months';
  
  -- Reindex
  REINDEX TABLE auth_private.audit_logs;
EOF
```

### 4.3 Update Documentation

- [ ] Add DATABASE_SECURITY_HARDENING.md to wiki
- [ ] Update runbook with new connection string
- [ ] Document new functions in API docs
- [ ] Update ER diagram to show schema separation
- [ ] Add architecture diagram to wiki

## Rollback Plan

If migration fails and needs to be rolled back:

### Option 1: Restore from Backup (Recommended)

```bash
# Stop application
systemctl stop cas-service

# Drop current database
psql -U postgres -c "DROP DATABASE cas;"

# Restore from backup
psql -U postgres < /backups/cas-v1-full.sql.gz

# Restart application
systemctl start cas-service

# Verify
curl http://localhost:8080/api/health
```

### Option 2: Revert Migration (If Backup Not Available)

```bash
# Delete new schemas
psql -U postgres -d cas << EOF
  DROP SCHEMA auth_api CASCADE;
  DROP SCHEMA auth_private CASCADE;
EOF

# Drop new roles
psql -U postgres << EOF
  DROP ROLE auth_owner;
  DROP ROLE app_auth;
EOF

# Restore old functions from V1 migration
-- Re-run V1__init_schema.sql manually
```

## Troubleshooting Common Issues

### Issue 1: Migration Hangs

```
-- Timeout waiting for locks
```

**Solution:**
```bash
# Check for active connections
psql -U postgres -d cas -c "SELECT * FROM pg_stat_activity WHERE state != 'idle';"

# Kill blocking sessions
psql -U postgres << EOF
  SELECT pg_terminate_backend(pid) FROM pg_stat_activity 
  WHERE datname = 'cas' AND usename NOT IN ('postgres', 'app_auth');
EOF

# Retry migration
```

### Issue 2: app_auth Cannot Execute Functions

```
ERROR: permission denied for function auth_api.find_user_credentials
```

**Solution:**
```sql
-- Verify grants
SELECT grantee, privilege_type 
FROM information_schema.role_routine_grants 
WHERE routine_schema = 'auth_api' AND grantee = 'app_auth';

-- Re-grant if missing
GRANT EXECUTE ON FUNCTION auth_api.find_user_credentials(text) TO app_auth;
GRANT EXECUTE ON FUNCTION auth_api.get_user(uuid) TO app_auth;
GRANT EXECUTE ON FUNCTION auth_api.get_totp_status(uuid) TO app_auth;
GRANT EXECUTE ON FUNCTION auth_api.encrypt_totp_secret(text, text) TO app_auth;
```

### Issue 3: Tables Not Migrated to auth_private

```
ERROR: relation "auth_private.users" does not exist
```

**Solution:**
```sql
-- Check current schema
SELECT table_schema, table_name FROM information_schema.tables 
WHERE table_name IN ('users', 'roles', 'permissions');

-- If tables still in 'public', re-run migration
-- Or manually move them:
ALTER TABLE users SET SCHEMA auth_private;
ALTER TABLE users OWNER TO auth_owner;
-- (repeat for all tables)
```

### Issue 4: Old Functions Still Being Called

```
ERROR: function auth.find_user_credentials(text) does not exist
```

**Solution:**
- Update application code to use `auth_api.*` instead of `auth.*`
- Search: `grep -r "FROM auth\." src/`
- Replace: `FROM auth_api.`

## Success Criteria

Migration is complete when ALL of these pass:

- ✅ Flyway reports: "Successfully applied 1 migration"
- ✅ `auth_private` schema exists with all tables
- ✅ `auth_api` schema exists with all functions
- ✅ `auth_owner` role created and owns all tables/functions
- ✅ `app_auth` can EXECUTE all auth_api functions
- ✅ `app_auth` cannot SELECT auth_private tables directly
- ✅ All functions marked SECURITY DEFINER
- ✅ All functions have `SET search_path = pg_catalog, auth_private`
- ✅ Application tests pass
- ✅ Login endpoint works
- ✅ No permission denied errors in logs
- ✅ Audit logs recording events normally
- ✅ Performance metrics <1% slower than V1

## Sign-off

- [ ] DBA verified migration success
- [ ] Security team approved access model
- [ ] QA verified all functions work
- [ ] DevOps confirmed rollback plan
- [ ] Application team updated code

Migration approved by: _____________ Date: _________

