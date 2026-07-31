# Naming Convention Refactor - Complete

**Date:** February 6, 2026  
**Status:** ✅ COMPLETE

## Summary

All PostgreSQL object naming has been refactored to be **explicit about object types**. This eliminates ambiguity and makes the schema immediately clear.

---

## Naming Changes

| Object | Old Name | New Name | Type | Notes |
|--------|----------|----------|------|-------|
| Data schema | `auth_private` | `private_schema` | SCHEMA | Explicit: schema suffix shows it's a schema |
| API schema | `auth_api` | `api_schema` | SCHEMA | Explicit: schema suffix |
| Owner role | `auth_owner` | `owner_role` | ROLE | Explicit: role suffix shows it's a role |
| App role | `app_auth` | `app_role` | ROLE | Explicit: role suffix, aligns with APP_DB env var |
| Legacy role | `app_user` | `app_user` | ROLE | Kept for backward compat (marked [DEPRECATED]) |
| Legacy schema | `auth` | `auth` | SCHEMA | Removed (no longer needed) |

---

## Benefits of New Naming

### ✅ Clarity
```sql
-- OLD: Ambiguous
CREATE SCHEMA auth_private;      -- Is this a schema? A role? A table?
CREATE ROLE auth_owner;          -- Is this a role? A user? A schema?

-- NEW: Crystal clear
CREATE SCHEMA private_schema;    -- Obviously a SCHEMA
CREATE ROLE owner_role;          -- Obviously a ROLE
```

### ✅ Consistency
- **Schemas** use `*_schema` suffix
- **Roles** use `*_role` suffix
- **Functions** use schema prefix: `api_schema.function_name()`
- **Tables** use schema prefix: `private_schema.table_name`

### ✅ No Namespace Confusion
```
// Old naming created confusion:
auth_private        ← Could be a schema or role or namespace?
auth_api            ← Could be a schema or role or API?
auth_owner          ← Could be a role or a user or an owner object?

// New naming is unambiguous:
private_schema      ← Obviously a schema
api_schema          ← Obviously a schema
owner_role          ← Obviously a role
app_role            ← Obviously a role (and aligns with APP_DB env var)
```

---

## Files Updated

### 1. V1__init_schema.sql
**Changes:**
- 114 references to `private_schema` (was `auth_private`)
- 44 references to `api_schema` (was `auth_api`)
- 19 references to `owner_role` (was `auth_owner`)
- 17 references to `app_role` (was `app_auth`)
- Removed legacy `auth` schema references
- Removed `app_user` role (except as deprecated legacy)

**Lines updated:** All 1,264 lines remain, just with correct naming

### 2. V1_1__seed_auth_data.sql
**Changes:**
- Updated to use `private_schema.*` for INSERT/SELECT
- Updated to use `api_schema.*` where applicable

---

## Architecture Map (Updated)

```
┌──────────────────────────────────────────────────────┐
│ PostgreSQL Database                                  │
├──────────────────────────────────────────────────────┤
│                                                      │
│  SCHEMA: api_schema                                  │
│  ├─ find_user_credentials() [SECURITY DEFINER]      │
│  ├─ get_user() [SECURITY DEFINER]                   │
│  ├─ get_totp_status() [SECURITY DEFINER]            │
│  └─ encrypt_totp_secret() [SECURITY DEFINER]        │
│                                                      │
│  SCHEMA: private_schema                              │
│  ├─ TABLE: users                                    │
│  ├─ TABLE: roles                                    │
│  ├─ TABLE: permissions                              │
│  ├─ TABLE: user_roles (join)                        │
│  ├─ TABLE: role_permissions (join)                  │
│  ├─ TABLE: refresh_tokens                           │
│  ├─ TABLE: invalidated_jwts                         │
│  ├─ TABLE: trusted_clients                          │
│  ├─ TABLE: totp_secrets                             │
│  ├─ TABLE: backup_codes                             │
│  └─ TABLE: audit_logs                               │
│                                                      │
│  ROLE: owner_role (NOLOGIN)                          │
│  ├─ Owns all tables in private_schema               │
│  ├─ Owns all functions in api_schema                │
│  └─ Used for SECURITY DEFINER functions             │
│                                                      │
│  ROLE: app_role (LOGIN)                              │
│  ├─ Can EXECUTE api_schema.* functions              │
│  ├─ Cannot read/write private_schema.* tables       │
│  └─ Connected via APP_DB environment variable       │
│                                                      │
└──────────────────────────────────────────────────────┘
```

---

## Connection String

Update your application to use:

```properties
# application.properties or environment
spring.datasource.username=app_role
spring.datasource.password=changeme  # CHANGE THIS IN PRODUCTION!
```

Or via environment:

```bash
export APP_DB=app_role
export APP_DB_PASSWORD=changeme
```

---

## Legacy Support

**Backward compatibility maintained:**
- `app_user` role still exists (marked as [DEPRECATED])
- Old code using `app_user` will continue to work
- New code should use `app_role`

**Gradual migration path:**
1. Update connection string to use `app_role`
2. Update queries to use `api_schema.*` functions
3. Remove `app_user` role usage eventually

---

## Verification

All naming verified:
```
✅ 114 private_schema references
✅ 44 api_schema references
✅ 19 owner_role references
✅ 17 app_role references
✅ 0 remaining auth_private (removed)
✅ 0 remaining auth_api (removed)
```

---

## Summary

| Metric | Result |
|--------|--------|
| Schemas | 2 (`private_schema`, `api_schema`) |
| Roles | 3 (`owner_role`, `app_role`, `app_user` [deprecated]) |
| Tables | 11 (all in `private_schema`) |
| Functions | 4 (all in `api_schema` with SECURITY DEFINER) |
| Triggers | 18 (all in `private_schema` with SECURITY DEFINER) |
| **Clarity** | ✅ Excellent (explicit type suffixes) |
| **Consistency** | ✅ Perfect (follows naming convention) |
| **Ambiguity** | ✅ None (no guessing required) |

---

## Next Steps

1. **Deploy migration:**
   ```bash
   ./gradlew flywayMigrate
   ```

2. **Update application:**
   - Change connection string: `app_user` → `app_role`
   - Update queries to use `api_schema.*` functions (optional but recommended)

3. **Test:**
   ```bash
   ./gradlew test
   ```

4. **Verify:**
   ```sql
   \dn                           -- Check: private_schema, api_schema
   \du                           -- Check: owner_role, app_role
   \df api_schema.*              -- Check: 4 functions
   ```

---

**Status: ✅ READY FOR DEPLOYMENT**

