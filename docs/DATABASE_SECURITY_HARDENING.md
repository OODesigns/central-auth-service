# PostgreSQL Security Hardening: Schema-Based Access Control

## Overview

This document describes the security hardening implemented in `V2__harden_security_schema_roles.sql`. The migration establishes proper role-based access control and separates concerns into distinct schemas, following PostgreSQL best practices.

## Architecture Changes

### Before (V1)
```
public schema (default)
├── tables: users, roles, permissions, ...
├── functions: auth.find_user_credentials(), auth.get_user(), ...
└── app_user role can directly SELECT/INSERT/UPDATE/DELETE tables
```

**Problems:**
- `app_user` has direct table access (overly permissive)
- Functions don't use SECURITY DEFINER (run as caller, not owner)
- No search_path isolation (vulnerable to schema injection)
- No role separation between owner and application
- DEFAULT PRIVILEGES not locked down

### After (V2)
```
auth_private schema (data layer)
├── tables: users, roles, permissions, ... (owned by auth_owner)
└── Triggers: audit functions (SECURITY DEFINER)

auth_api schema (API layer)
├── find_user_credentials() - SECURITY DEFINER, locked search_path
├── get_user() - SECURITY DEFINER, locked search_path
├── get_totp_status() - SECURITY DEFINER, locked search_path
└── encrypt_totp_secret() - SECURITY DEFINER, locked search_path

Roles:
├── auth_owner (NOLOGIN) - owns all tables and functions
├── app_auth (LOGIN) - application connection, EXECUTE only on auth_api functions
└── public (revoked from all)
```

**Benefits:**
- ✅ Application cannot read/write tables directly
- ✅ Application can ONLY execute approved functions
- ✅ Functions run as auth_owner with known privileges
- ✅ All functions lock search_path (prevents schema injection)
- ✅ Clear separation of concerns
- ✅ Audit trail of all operations

## Key Concepts

### 1. SECURITY DEFINER Functions

```sql
CREATE FUNCTION auth_api.find_user_credentials(p_username text)
SECURITY DEFINER
SET search_path = pg_catalog, auth_private
AS $$
  SELECT ...
$$;
```

**What it does:**
- Function runs as the **owner** (auth_owner), not the caller (app_auth)
- Even though `app_auth` lacks SELECT on `auth_private.users`, the function can access it
- The caller's permissions don't matter; the owner's do

**Why it matters:**
- Owner controls what data is returned (selective exposure)
- Owner can enforce business logic (audit, validation)
- Caller cannot escalate privileges (can't use the function to access more than intended)

### 2. Search Path Locking

```sql
CREATE FUNCTION auth_api.find_user_credentials(p_username text)
SECURITY DEFINER
SET search_path = pg_catalog, auth_private  ← Locks this!
AS $$
  SELECT ... FROM users  -- Resolves to auth_private.users
$$;
```

**What it does:**
- `search_path` controls which schema is searched for unqualified names
- `SET search_path = pg_catalog, auth_private` restricts to only these schemas
- Unqualified `users` in function resolves to `auth_private.users` (if it exists there)

**Why it matters (security):**
- **Prevents schema injection attacks**
- Example attack without locking:
  ```sql
  -- Attacker creates malicious schema with fake table
  CREATE SCHEMA attacker_schema;
  CREATE TABLE attacker_schema.users (user_id uuid, password_hash text, admin boolean);
  INSERT INTO attacker_schema.users VALUES (...);
  
  -- If function search_path is not locked, it might resolve 'users' to attacker schema
  SELECT * FROM auth_api.find_user_credentials('admin');  -- Returns attacker's fake data!
  ```
- With locked search_path, function always uses `auth_private.users`

### 3. Schema-Based Access Control

**auth_private schema:**
- Contains all tables (data stores)
- No grants to `public` or `app_auth`
- Only `auth_owner` can access directly
- Internal audit triggers run as auth_owner

**auth_api schema:**
- Contains all public entry-point functions
- Only `auth_api` functions are EXECUTE-able by `app_auth`
- No direct table access from `app_auth`
- Each function explicitly controls what data it returns

**Example:**
```sql
-- app_auth cannot do this (no SELECT on auth_private.users)
SELECT * FROM auth_private.users;  -- ERROR

-- app_auth CAN do this (has EXECUTE on auth_api function)
SELECT * FROM auth_api.find_user_credentials('admin');  -- OK (calls function as auth_owner)
```

## Role Hierarchy

### auth_owner (NOLOGIN)
- **Purpose:** Data owner, runs all functions with SECURITY DEFINER
- **Permissions:** Owns all tables, triggers, functions in auth_private and auth_api
- **Login:** NO (never connects directly)
- **Use:** Only for SECURITY DEFINER context

### app_auth (LOGIN)
- **Purpose:** Application database connection
- **Permissions:** EXECUTE only on auth_api functions, USAGE on schemas
- **Login:** YES (application uses this to connect)
- **Use:** Normal application queries (INSERT, UPDATE, DELETE) via stored procedures

### Connection Pool
```
Application (connection pool)
    ↓
app_auth role
    ↓
PostgreSQL
    ↓
    ├─ Can EXECUTE auth_api.find_user_credentials() → runs as auth_owner
    ├─ Can EXECUTE auth_api.get_user() → runs as auth_owner
    ├─ Can EXECUTE auth_api.get_totp_status() → runs as auth_owner
    └─ Cannot SELECT/INSERT/UPDATE auth_private tables
```

## Migration Steps

### Step 1: Create Schemas
```sql
CREATE SCHEMA auth_private;  -- Data stores
CREATE SCHEMA auth_api;      -- Public API
```

### Step 2: Create Roles
```sql
CREATE ROLE auth_owner NOLOGIN;  -- Owner
CREATE ROLE app_auth WITH LOGIN;  -- Application
```

### Step 3: Move Tables
```sql
ALTER TABLE users SET SCHEMA auth_private;
ALTER TABLE users OWNER TO auth_owner;
-- ... repeat for all tables
```

### Step 4: Recreate Functions with SECURITY DEFINER
```sql
CREATE FUNCTION auth_api.find_user_credentials(...)
SECURITY DEFINER
SET search_path = pg_catalog, auth_private
AS $$
  SELECT ... FROM auth_private.users ...
$$;
```

### Step 5: Grant Selective Access
```sql
GRANT EXECUTE ON FUNCTION auth_api.find_user_credentials(text) TO app_auth;
GRANT USAGE ON SCHEMA auth_api TO app_auth;

-- NO GRANTS on auth_private to app_auth!
REVOKE ALL ON SCHEMA auth_private FROM app_auth;
```

## Application Code Changes

### Before (connecting as app_user)
```java
// Direct table access (not recommended)
SELECT u.user_id, u.password_hash FROM users u WHERE u.username = ?;
```

### After (connecting as app_auth, calling functions)
```java
// Function call (secure)
SELECT user_id, password_hash FROM auth_api.find_user_credentials(?);
```

**Database connection string:**
```properties
# Old (direct table access)
spring.datasource.url=jdbc:postgresql://localhost:5432/cas?user=app_user&password=...

# New (function-based access)
spring.datasource.url=jdbc:postgresql://localhost:5432/cas?user=app_auth&password=...
```

## Audit Trail

All operations are logged via triggers that run as `auth_owner`:

```sql
-- Audit table
CREATE TABLE auth_private.audit_logs (
  id uuid PRIMARY KEY,
  actor_id uuid,              -- Who did it?
  action varchar(50),         -- USER_CREATED, LOGIN, etc.
  target_type varchar(50),    -- users, refresh_tokens, etc.
  target_id uuid,             -- Which record?
  metadata jsonb,             -- Extra context
  created_at timestamptz
);

-- Example audit entries
INSERT INTO audit_logs VALUES (
  gen_random_uuid(),
  'user-123',
  'USER_UPDATED',
  'users',
  'user-123',
  '{"username": "alice", "updated_fields": ["password_hash"]}',
  now()
);
```

## Verification Checklist

- [ ] `app_auth` can EXECUTE `auth_api.*` functions
- [ ] `app_auth` CANNOT SELECT `auth_private.*` tables
- [ ] Functions are marked SECURITY DEFINER
- [ ] Functions set search_path explicitly
- [ ] Default privileges are revoked for public
- [ ] `auth_owner` is NOLOGIN (never login directly)
- [ ] All audit triggers run as auth_owner
- [ ] Application code updated to call `auth_api.*` functions
- [ ] Connection string updated to use `app_auth` role

### Test queries:
```sql
-- Test 1: app_auth cannot read tables directly
\c cas app_auth
SELECT * FROM auth_private.users;  -- ERROR: permission denied

-- Test 2: app_auth can execute functions
SELECT * FROM auth_api.find_user_credentials('admin');  -- OK

-- Test 3: functions run with correct privileges
SELECT current_user;  -- Should be app_auth
SELECT * FROM auth_api.find_user_credentials('admin');  -- Runs as auth_owner internally

-- Test 4: search_path is locked in function
-- (No visible test, but function definitions show: SET search_path = ...)
```

## Performance Considerations

### SECURITY DEFINER Overhead
- **Negligible** (<1% for most queries)
- Function call overhead dominates, not privilege check
- Query planner still optimizes effectively

### Query Plans
- May differ slightly due to schema qualification
- Example: `FROM auth_private.users` vs `FROM users`
- Usually no functional difference

### Monitoring
```sql
-- Find slowest functions
SELECT query, calls, mean_exec_time
FROM pg_stat_statements
WHERE query LIKE '%auth_api%'
ORDER BY mean_exec_time DESC;

-- Check audit log size
SELECT schemaname, tablename, pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename))
FROM pg_tables
WHERE schemaname = 'auth_private'
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;
```

## Compliance & Security Standards

### CIS PostgreSQL Benchmarks Addressed

✅ **1.2 Create REVOKE EXECUTE on all functions from public**
- Done: `REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA auth_api FROM PUBLIC`

✅ **4.2 Ensure that `search_path` is set to a restricted value**
- Done: Each function sets `SET search_path = pg_catalog, auth_private`

✅ **5.1 Ensure that all databases are owned by a role that owns nothing else**
- Done: `auth_owner` owns only auth tables/functions (not system objects)

✅ **Prevent privilege escalation via schema injection**
- Done: Locked search_path in all SECURITY DEFINER functions

### OWASP Authentication Cheat Sheet Compliance

✅ **A07:2021 – Identification and Authentication Failures**
- All password operations audited
- MFA policies enforced before token issuance
- Suspicious authentication logged

✅ **A02:2021 – Cryptographic Failures**
- Passwords stored as bcrypt hashes (never plaintext)
- TOTP secrets encrypted with AES-CBC
- Audit logs immutable (append-only)

## Troubleshooting

### Issue: "permission denied for schema auth_private"
```
ERROR: permission denied for schema auth_private
```
**Solution:** Ensure app_auth has USAGE on auth_api, not auth_private
```sql
GRANT USAGE ON SCHEMA auth_api TO app_auth;
REVOKE ALL ON SCHEMA auth_private FROM app_auth;
```

### Issue: "function xyz already exists"
```
ERROR: function xyz(text) already exists
```
**Solution:** Migration is idempotent but check for manually created functions
```sql
DROP FUNCTION IF EXISTS auth_api.find_user_credentials(text);
```

### Issue: Function returns wrong results
**Probable cause:** search_path not locked, resolving to wrong schema
**Check:** Verify `SET search_path` in function definition
```sql
\df+ auth_api.find_user_credentials
```

### Issue: Audit not recording events
**Probable cause:** Trigger not firing (check for errors during migration)
**Check:** Verify triggers exist
```sql
SELECT * FROM information_schema.triggers WHERE trigger_schema = 'auth_private';
```

## Future Enhancements

1. **Row-Level Security (RLS)**
   - Policy: Users can only see their own records
   - Policy: Admins can see all records
   - Applied to: audit_logs, refresh_tokens, totp_secrets

2. **Column-Level Encryption**
   - Encrypt: totp_secrets.secret_key (double-encrypted)
   - Encrypt: backup_codes.code_hash (with key rotation)

3. **Automatic Password Rotation**
   - Policy: Force password change every 90 days
   - Policy: Disallow last 5 passwords
   - Audit: Log all password changes

4. **MFA Enforcement Levels**
   - User-level: No enforcement
   - Role-level: All admins must have 2FA
   - Org-level: Enforce for all users in org

5. **Rate Limiting at DB Level**
   - Use `pg_stat_statements` to detect brute force
   - Auto-block after N failed auth attempts
   - Reset after timeout

## References

- [PostgreSQL Security Best Practices](https://wiki.postgresql.org/wiki/SQL_Injection)
- [CIS PostgreSQL Benchmarks](https://www.cisecurity.org/benchmark/postgresql)
- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
- [PostgreSQL SECURITY DEFINER](https://www.postgresql.org/docs/current/sql-createfunction.html)
- [PostgreSQL search_path](https://www.postgresql.org/docs/current/runtime-config-client.html#GUC-SEARCH-PATH)

