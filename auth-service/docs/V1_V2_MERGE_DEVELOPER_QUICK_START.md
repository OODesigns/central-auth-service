# V1/V2 Merge - Developer Quick Start

## What Changed

Security hardening from V2 has been **merged into V1**. Since we're still in development, there's now a single migration file with everything built-in.

## Files to Update in Your Application

### 1. Connection String
**File:** `src/main/resources/application.properties` (or `.env`)

```properties
# OLD (deprecated but still works)
spring.datasource.username=app_user

# NEW (use this)
spring.datasource.username=app_auth
```

### 2. Database Queries (Optional but Recommended)

```java
// OLD: Direct table access (still works but less secure)
SELECT * FROM users WHERE username = ?

// NEW: Call functions (secure, SECURITY DEFINER)
SELECT * FROM auth_api.find_user_credentials(?)
```

## Available Functions (in auth_api schema)

All of these are **SECURITY DEFINER** (run as auth_owner):

### Login
```sql
SELECT * FROM auth_api.find_user_credentials('username');
-- Returns: user_id, username, password_hash, password_reset_required_at
```

### User Data
```sql
SELECT * FROM auth_api.get_user(user_id);
-- Returns: user_id, username, permissions[]
```

### 2FA Check
```sql
SELECT * FROM auth_api.get_totp_status(user_id);
-- Returns: user_id (if 2FA enabled) or empty result
```

### TOTP Encryption
```sql
SELECT auth_api.encrypt_totp_secret(secret, encryption_key);
-- Returns: encrypted bytea
```

## What's Protected Now

```
❌ app_auth CANNOT:
   - SELECT tables directly
   - INSERT/UPDATE/DELETE rows directly
   - CREATE objects
   - GRANT permissions

✅ app_auth CAN:
   - EXECUTE auth_api.* functions
   - ONLY access data via controlled functions
```

## Legacy Compatibility

Old code still works:
- `auth.find_user_credentials()` - calls auth_api version
- `auth.get_user()` - calls auth_api version
- `auth.get_totp_status()` - calls auth_api version
- `auth.encrypt_totp_secret()` - calls auth_api version

**Recommended:** Update to `auth_api.*` eventually, but not urgently.

## Testing

```bash
# Run migrations
./gradlew flywayMigrate

# Update app code
# - Change app_user → app_auth
# - (Optional) Update auth.* → auth_api.*

# Test
./gradlew test
```

## If You Get Permission Errors

If you see errors like `permission denied for schema auth_private`:

1. Check connection string uses `app_auth` (not `app_user`)
2. Check queries use `auth_api.find_user_credentials()` (not direct table access)
3. Check application is using function calls for all database access

## Architecture

```
┌─────────────────────────────────────────┐
│ Application (Spring Boot)               │
└─────────────────┬───────────────────────┘
                  │
            ┌─────▼──────┐
            │ app_auth   │
            │ (LOGIN)    │
            └─────┬──────┘
                  │
        ┌─────────▼──────────────────┐
        │ PostgreSQL Database        │
        ├────────────────────────────┤
        │ auth_api (API layer)       │
        │ - find_user_credentials()  │
        │ - get_user()               │
        │ - get_totp_status()        │
        │ - encrypt_totp_secret()    │
        │ (All SECURITY DEFINER)     │
        ├────────────────────────────┤
        │ auth_private (data layer)  │
        │ - users table              │
        │ - roles, permissions       │
        │ - tokens, audit logs, etc. │
        │ (No direct app access)     │
        └────────────────────────────┘
```

## Questions?

- Architecture: See `DATABASE_SECURITY_HARDENING.md`
- Functions: See `API_FUNCTIONS_REFERENCE.md`
- Troubleshooting: See `DATABASE_SECURITY_QUICK_REFERENCE.md`
- Merge details: See `V1_V2_MERGE_COMPLETION_REPORT.md`

## Summary

✅ Security hardening is now built-in  
✅ No separate V2 migration file  
✅ Single V1 migration with everything  
✅ Backward compatible (old queries still work)  
✅ Ready to deploy

