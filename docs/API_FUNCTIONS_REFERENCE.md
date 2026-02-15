# API Functions Reference

## Overview

All application database access goes through these SECURITY DEFINER functions in the `auth_api` schema. Each function:

1. **Runs as `auth_owner`** (not caller) - SECURITY DEFINER
2. **Locks search_path** - prevents schema injection
3. **Returns only necessary data** - least privilege principle
4. **Is audited** - via triggers on underlying tables

## Core Functions

### 1. find_user_credentials(username text)

**Purpose:** Retrieve user's password hash and reset status for login

**Returns:**
```sql
user_id uuid                -- User unique ID
username text               -- Username (for verification)
password_hash text          -- Bcrypt hash for verification
password_reset_required_at  -- NULL if no reset needed, timestamp if required
```

**Usage:**
```java
// Verify password during login
ResultSet rs = connection.prepareStatement(
  "SELECT user_id, password_hash, password_reset_required_at " +
  "FROM auth_api.find_user_credentials(?)"
).executeQuery("alice");

// Compare passwords
String storedHash = rs.getString("password_hash");
boolean matches = BCrypt.checkpw(providedPassword, storedHash);
```

**Security Notes:**
- Only returns password hash (not roles, permissions, MFA)
- Supports fail-fast password verification
- Minimal data exposure (defense-in-depth)

**Performance:**
- Simple indexed lookup on `auth_private.users.username`
- Expected: <1ms

### 2. get_user(user_id uuid)

**Purpose:** Load full user with permissions after password verification

**Returns:**
```sql
user_id uuid        -- User unique ID
username text       -- Username
permissions text[]  -- Aggregated permissions array
```

**Usage:**
```java
// Load user after password verified
ResultSet rs = connection.prepareStatement(
  "SELECT user_id, username, permissions FROM auth_api.get_user(?)"
).executeQuery(userId);

String[] perms = (String[]) rs.getArray("permissions").getArray();
// perms = {"view_audit_log", "create_user", ...}
```

**Security Notes:**
- Includes all permissions (used for authorization)
- Called AFTER password verified (not on every request)
- Aggregate done at DB level (cleaner than app-side joins)

**Performance:**
- Single query with 3 LEFT JOINs
- Indexed on: users(user_id), user_roles(user_id), role_permissions(role_id)
- Expected: 2-5ms

### 3. get_totp_status(user_id uuid)

**Purpose:** Check if 2FA (TOTP) is enabled for user

**Returns:**
```sql
user_id uuid  -- User unique ID (if 2FA enabled)
              -- Empty result set (if 2FA disabled)
```

**Usage:**
```java
// Check if 2FA is required
ResultSet rs = connection.prepareStatement(
  "SELECT user_id FROM auth_api.get_totp_status(?)"
).executeQuery(userId);

boolean mfaEnabled = rs.next();  // true if row returned
```

**Security Notes:**
- Only checks existence of verified TOTP (minimal exposure)
- Cannot be used to enumerate users (requires user_id input)
- Fast check for conditional authentication flow

**Performance:**
- Indexed lookup on `totp_secrets(user_id, verified_at)`
- Expected: <1ms

### 4. encrypt_totp_secret(secret text, encryption_key text)

**Purpose:** Encrypt TOTP secret before storage

**Returns:**
```sql
bytea  -- Encrypted secret (AES-CBC encrypted)
```

**Usage:**
```java
// Encrypt TOTP secret during setup
String secret = generateTotpSecret();
String encryptionKey = getMasterEncryptionKey();

PreparedStatement stmt = connection.prepareStatement(
  "SELECT auth_api.encrypt_totp_secret(?, ?)"
);
stmt.setString(1, secret);
stmt.setString(2, encryptionKey);
ResultSet rs = stmt.executeQuery();

byte[] encryptedSecret = rs.getBytes(1);
// Store encryptedSecret in totp_secrets.secret_key_encrypted
```

**Security Notes:**
- Uses AES-CBC (semantic security, random IV)
- Encryption key should be external (KMS, env var, NOT database)
- Each invocation generates new IV (even same secret encrypts differently)
- Implements STABLE (not IMMUTABLE) for flexibility

**Performance:**
- Encryption: ~1-5ms
- Uses pgcrypto (optimized C extension)

---

## Audit Functions (Internal Use)

These are trigger functions in `auth_private` schema. Called automatically by triggers, not directly by app.

### audit_users()
Triggers on: `INSERT`, `UPDATE`, `DELETE` on `auth_private.users`

Logs:
- USER_CREATED
- USER_PASSWORD_ROTATED
- USER_UPDATED
- USER_DISABLED
- USER_MFA_REQUIRED (detected on update)
- USER_MFA_REQUIRED_REMOVED (detected on update)

### audit_invalidated_jwts()
Triggers on: `INSERT` on `auth_private.invalidated_jwts`

Logs:
- TOKEN_INVALIDATED

### audit_refresh_tokens()
Triggers on: `INSERT`, `UPDATE`, `DELETE` on `auth_private.refresh_tokens`

Logs:
- REFRESH_TOKEN_ISSUED
- REFRESH_TOKEN_ROTATED
- REFRESH_TOKEN_REVOKED

### audit_totp_*()
Multiple trigger functions handle TOTP events:
- audit_totp_enabled() → TOTP_ENABLED
- audit_totp_last_used() → TOTP_LAST_USED
- audit_totp_disabled() → TOTP_DISABLED
- audit_backup_codes_generated() → BACKUP_CODES_GENERATED

---

## Adding New Functions

### Pattern 1: Read-Only Query

```sql
-- Add to auth_api schema
CREATE OR REPLACE FUNCTION auth_api.get_user_by_email(p_email text)
RETURNS TABLE (
  user_id uuid,
  username text,
  email text
)
LANGUAGE sql
STABLE  -- Deterministic, no modifications
SECURITY DEFINER  -- Runs as auth_owner
SET search_path = pg_catalog, auth_private  -- Prevent injection
AS $$
  SELECT u.user_id, u.username, u.email
  FROM auth_private.users u
  WHERE u.email = p_email;
$$;

-- Give app_auth permission
ALTER FUNCTION auth_api.get_user_by_email(text) OWNER TO auth_owner;
GRANT EXECUTE ON FUNCTION auth_api.get_user_by_email(text) TO app_auth;

-- Comment it
COMMENT ON FUNCTION auth_api.get_user_by_email(text) IS
  'Retrieve user by email address. Returns minimal data. Used for password reset flow.';
```

### Pattern 2: Write Operation (With Audit)

```sql
-- Add to auth_api schema
CREATE OR REPLACE FUNCTION auth_api.set_user_mfa_required(
  p_user_id uuid,
  p_required_at timestamptz
)
RETURNS void
LANGUAGE plpgsql
VOLATILE  -- Modifies data
SECURITY DEFINER
SET search_path = pg_catalog, auth_private
AS $$
BEGIN
  -- Set context for audit trigger
  PERFORM set_config('app.actor_type', 'SYSTEM', false);
  PERFORM set_config('app.actor_id', 'admin-service'::text, false);
  
  -- Update user (triggers audit automatically)
  UPDATE auth_private.users
  SET mfa_required_at = p_required_at
  WHERE user_id = p_user_id;
  
  -- Verify update succeeded
  IF NOT FOUND THEN
    RAISE EXCEPTION 'User not found: %', p_user_id;
  END IF;
END;
$$;

-- Give app_auth permission
ALTER FUNCTION auth_api.set_user_mfa_required(uuid, timestamptz) OWNER TO auth_owner;
GRANT EXECUTE ON FUNCTION auth_api.set_user_mfa_required(uuid, timestamptz) TO app_auth;

-- Comment it
COMMENT ON FUNCTION auth_api.set_user_mfa_required(uuid, timestamptz) IS
  'Enforce MFA requirement for user. Triggers audit trail. Used by admin service to mandate 2FA.';
```

### Pattern 3: Complex Logic (Transactional)

```sql
-- Add to auth_api schema
CREATE OR REPLACE FUNCTION auth_api.verify_totp_and_issue_token(
  p_user_id uuid,
  p_totp_code text,
  p_token_data jsonb
)
RETURNS TABLE (
  success boolean,
  token_family_id uuid,
  message text
)
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = pg_catalog, auth_private
AS $$
DECLARE
  v_secret_encrypted bytea;
  v_user_id uuid;
  v_token_family_id uuid;
BEGIN
  -- Verify TOTP code (must implement actual TOTP verification)
  SELECT user_id, secret_key_encrypted INTO v_user_id, v_secret_encrypted
  FROM auth_private.totp_secrets
  WHERE user_id = p_user_id AND verified_at IS NOT NULL;
  
  IF v_user_id IS NULL THEN
    RETURN QUERY SELECT false, NULL::uuid, 'TOTP not enabled'::text;
    RETURN;
  END IF;
  
  -- In real code: decrypt and verify TOTP code
  -- For now, just example structure
  
  -- Create refresh token
  v_token_family_id := gen_random_uuid();
  INSERT INTO auth_private.refresh_tokens (
    user_id, token_hash, family_id, expires_at
  ) VALUES (
    p_user_id,
    encode(digest(p_totp_code, 'sha256'), 'hex'),
    v_token_family_id,
    NOW() + INTERVAL '7 days'
  );
  
  -- Log usage
  UPDATE auth_private.totp_secrets
  SET last_used_at = NOW()
  WHERE user_id = p_user_id;
  
  RETURN QUERY SELECT true, v_token_family_id, 'MFA verification successful'::text;
END;
$$;

-- Give app_auth permission
ALTER FUNCTION auth_api.verify_totp_and_issue_token(uuid, text, jsonb) 
  OWNER TO auth_owner;
GRANT EXECUTE ON FUNCTION auth_api.verify_totp_and_issue_token(uuid, text, jsonb) 
  TO app_auth;
```

---

## Accessing Audit Logs

Audit logs are stored in `auth_private.audit_logs` but not directly accessible to `app_auth`. Create specific read functions:

### Example: Create Audit Access Function

```sql
-- Add to auth_api schema
CREATE OR REPLACE FUNCTION auth_api.get_audit_logs(
  p_action varchar(50) DEFAULT NULL,
  p_actor_id uuid DEFAULT NULL,
  p_limit integer DEFAULT 100
)
RETURNS TABLE (
  actor_id uuid,
  action varchar(50),
  target_type varchar(50),
  created_at timestamptz,
  metadata jsonb
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, auth_private
AS $$
  SELECT 
    actor_id, action, target_type, created_at, metadata
  FROM auth_private.audit_logs
  WHERE (p_action IS NULL OR action = p_action)
    AND (p_actor_id IS NULL OR actor_id = p_actor_id)
  ORDER BY created_at DESC
  LIMIT p_limit;
$$;

GRANT EXECUTE ON FUNCTION auth_api.get_audit_logs(varchar, uuid, integer) TO app_auth;

-- Usage
SELECT * FROM auth_api.get_audit_logs('USER_CREATED', NULL, 10);
```

---

## Best Practices for New Functions

✅ **DO:**
- Add `SECURITY DEFINER` to all external functions
- Lock `search_path` in every function
- Use schema-qualified names (`auth_private.users`, not `users`)
- Add meaningful comments explaining function purpose
- Use `LANGUAGE sql` for simple queries (performance)
- Use `LANGUAGE plpgsql` for complex logic
- Mark `STABLE` or `VOLATILE` correctly (optimizer uses this)
- Grant EXECUTE to `app_auth` after creating
- Assign ownership to `auth_owner`
- Implement input validation
- Return consistent types
- Handle NULL cases explicitly

❌ **DON'T:**
- Forget `SET search_path` (vulnerable to injection!)
- Use `CREATE OR REPLACE` in new migrations (use full rebuild)
- Grant `SELECT` on tables to `app_auth`
- Use dynamic SQL without proper escaping
- Return sensitive data (passwords, keys, etc.)
- Assume caller has any permissions
- Create functions in `public` schema (use `auth_api`)
- Forget COMMENT ON FUNCTION
- Mix concerns (auth + business logic) in one function
- Return unstructured data (use TABLE/RECORD types)

---

## Testing Functions

```sql
-- Test function exists and is callable
SELECT proname, prosecdef, provolatile
FROM pg_proc
WHERE proname = 'find_user_credentials' AND pronamespace::regnamespace::text = 'auth_api';
-- Should show: prosecdef=true (SECURITY DEFINER)

-- Test function as app_auth user
\c cas app_auth
SELECT * FROM auth_api.find_user_credentials('admin');
-- Should work

-- Test search_path protection
\c cas app_auth
CREATE SCHEMA attacker;
CREATE TABLE attacker.users (id uuid);
SELECT * FROM auth_api.find_user_credentials('admin');
-- Should still return auth_private.users data, not attacker.users

-- Test SECURITY DEFINER (runs as owner, not caller)
\c cas postgres
CREATE FUNCTION test_current_user() RETURNS text LANGUAGE sql AS 'SELECT current_user';
CREATE FUNCTION test_current_user_definer() RETURNS text LANGUAGE sql 
  SECURITY DEFINER AS 'SELECT current_user';

\c cas app_auth
SELECT test_current_user();  -- Returns 'app_auth'
SELECT test_current_user_definer();  -- Returns 'auth_owner' (owner of function)
```

---

## Performance Monitoring

```sql
-- Check function stats
SELECT query, calls, total_time, mean_time
FROM pg_stat_statements
WHERE query LIKE '%auth_api%'
ORDER BY mean_time DESC;

-- Check query plans
EXPLAIN ANALYZE SELECT * FROM auth_api.find_user_credentials('admin');

-- Monitor function overhead
SELECT
  f.proname,
  COUNT(*) as calls,
  ROUND(AVG(x.calls)::numeric, 2) as avg_calls,
  ROUND(AVG(x.total_time)::numeric, 2) as avg_total_ms,
  ROUND(AVG(x.mean_time)::numeric, 4) as avg_mean_ms
FROM pg_stat_statements x
JOIN pg_proc f ON x.funcname = f.proname
WHERE f.pronamespace = 'auth_api'::regnamespace
GROUP BY f.proname
ORDER BY avg_mean_ms DESC;
```

