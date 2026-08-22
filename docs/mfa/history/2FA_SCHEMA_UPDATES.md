> **Historical planning document.** The current schema uses pending/active TOTP secret state and `verified_at`; it does not use `users.totp_enabled`. See [PROJECT_STATUS_AND_COMPLETION_PLAN.md](../../project/PROJECT_STATUS_AND_COMPLETION_PLAN.md).

# 2FA Schema Updates - Authenticator App Support

## Overview

The database schema has been updated to support Time-based One-Time Password (TOTP) 2FA using authenticator apps (Google Authenticator, Microsoft Authenticator, Authy, etc.).

## Schema Changes

### 1. Users Table - New Columns

Added one column to track 2FA status:

```sql
totp_verified_at TIMESTAMPTZ
```

| Column | Type | Purpose |
|--------|------|---------|
| `totp_verified_at` | TIMESTAMPTZ | Timestamp when the TOTP secret was verified during setup. `NULL` = 2FA disabled, `NOT NULL` = 2FA enabled |

**Status Derivation:**
- `totp_verified_at IS NULL` → 2FA is **disabled**
- `totp_verified_at IS NOT NULL` → 2FA is **enabled** (timestamp value indicates when it was verified)

### 2. New Table: `totp_secrets`

Stores the TOTP secret key and configuration for each user.

```sql
CREATE TABLE totp_secrets (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id               UUID NOT NULL UNIQUE REFERENCES users(user_id) ON DELETE CASCADE,
  secret_key            TEXT NOT NULL,
  algorithm             VARCHAR(10) NOT NULL DEFAULT 'SHA1',
  period_seconds        INTEGER NOT NULL DEFAULT 30,
  digits                INTEGER NOT NULL DEFAULT 6,
  verified_at           TIMESTAMPTZ,
  backup_codes_generated_at TIMESTAMPTZ,
  created_at            TIMESTAMPTZ DEFAULT now(),
  updated_at            TIMESTAMPTZ DEFAULT now()
);
```

| Column | Type | Purpose | Notes |
|--------|------|---------|-------|
| `id` | UUID | Primary key | |
| `user_id` | UUID | References user | UNIQUE constraint ensures one secret per user |
| `secret_key` | TEXT | Base32-encoded TOTP secret | Should be encrypted at rest in production |
| `algorithm` | VARCHAR(10) | HMAC algorithm | Default: `SHA1` (compatible with most authenticator apps) |
| `period_seconds` | INTEGER | Time window for OTP validity | Default: `30` seconds (standard) |
| `digits` | INTEGER | Number of OTP digits | Default: `6` (range: 6-8 for compatibility) |
| `verified_at` | TIMESTAMPTZ | When secret was verified | `NULL` until first successful verification |
| `backup_codes_generated_at` | TIMESTAMPTZ | When backup codes were last generated | `NULL` if not yet generated |
| `created_at` | TIMESTAMPTZ | Record creation timestamp | |
| `updated_at` | TIMESTAMPTZ | Last update timestamp | Auto-managed by trigger |

**Indexes:**
- `idx_totp_secrets_user_id` - Direct lookup by user
- `idx_totp_secrets_active` - Lookup active (verified) secrets

### 3. New Table: `backup_codes`

Stores single-use backup codes for account recovery when authenticator device is lost.

```sql
CREATE TABLE backup_codes (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id           UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
  code_hash         TEXT NOT NULL,
  used_at           TIMESTAMPTZ,
  created_at        TIMESTAMPTZ DEFAULT now()
);
```

| Column | Type | Purpose | Notes |
|--------|------|---------|-------|
| `id` | UUID | Primary key | |
| `user_id` | UUID | References user | Cascades on user delete |
| `code_hash` | TEXT | Hash of the backup code | Never store plaintext (use bcrypt or similar) |
| `used_at` | TIMESTAMPTZ | When code was used | `NULL` until first use; code cannot be reused |
| `created_at` | TIMESTAMPTZ | Record creation timestamp | |

**Indexes:**
- `idx_backup_codes_user_id` - Lookup all codes for a user
- `idx_backup_codes_used` - Find unused codes

## Audit Trail

### New Audit Actions

Two new audit actions track 2FA lifecycle:

| Action | Trigger | Purpose |
|--------|---------|---------|
| `TOTP_ENABLED` | `totp_secrets.verified_at` changes from NULL → timestamp | User successfully enables 2FA |
| `BACKUP_CODES_GENERATED` | `totp_secrets.backup_codes_generated_at` changes from NULL → timestamp | User generates backup codes |

### Audit Trigger Functions

**`audit_totp_enabled()`** - Triggers on `totp_secrets` UPDATE
- Records when user first verifies their TOTP secret during setup
- Creates audit log entry with `action = 'TOTP_ENABLED'`
- Only fires once (when `verified_at` transitions from NULL)

**`audit_backup_codes_generated()`** - Triggers on `totp_secrets` UPDATE
- Records when user generates backup codes for account recovery
- Creates audit log entry with `action = 'BACKUP_CODES_GENERATED'`
- Only fires once per generation (when `backup_codes_generated_at` transitions from NULL)

## Implementation Flow

### 2FA Setup Flow

1. User requests to enable 2FA
2. Generate new TOTP secret (Base32-encoded random bytes)
3. Insert row into `totp_secrets` table
4. Return QR code (contains secret + metadata) to user
5. User scans with authenticator app
6. User enters 6-digit OTP to verify
7. On successful verification:
   - Update `totp_secrets.verified_at = NOW()`
   - Update `users.totp_enabled = TRUE`
   - **Trigger fires:** `audit_totp_enabled()` logs `TOTP_ENABLED`
8. Generate 10 single-use backup codes
9. Hash each code with bcrypt
10. Insert rows into `backup_codes` table
11. Return plaintext codes to user (display once only)
12. Update `totp_secrets.backup_codes_generated_at = NOW()`
13. **Trigger fires:** `audit_backup_codes_generated()` logs `BACKUP_CODES_GENERATED`

### Login Flow with 2FA

1. User logs in with username + password
2. Password verification succeeds
3. Check `users.totp_enabled`:
   - If `FALSE`: Issue JWT tokens (normal flow)
   - If `TRUE`: Proceed to 2FA verification
4. User enters 6-digit OTP from authenticator app
5. Verify OTP against `totp_secrets.secret_key` for user
6. If valid: Issue JWT tokens
7. If invalid but user has backup codes: Allow backup code entry
8. If backup code valid: Mark code as used (`backup_codes.used_at = NOW()`), issue tokens

### Account Recovery (Lost Authenticator)

1. User cannot access 2FA during login
2. User enters backup code instead of OTP
3. Hash backup code and lookup in `backup_codes`
4. If found with `used_at IS NULL`: Accept, mark as used
5. After recovery: User can disable old 2FA and setup new secret

## Security Considerations

1. **Secret Storage**: `totp_secrets.secret_key` should be encrypted at rest in production
2. **Backup Codes**: Always hash with bcrypt or similar; never store plaintext
3. **Backup Codes Retrieval**: Only show plaintext codes once during generation
4. **Secret Protection**: Protect secret during transmission (TLS/HTTPS)
5. **Audit Trail**: All 2FA events are logged for compliance and incident investigation
6. **Clock Skew**: Implement reasonable clock skew tolerance (±30 seconds recommended)
7. **Rate Limiting**: Apply rate limiting to OTP/backup code verification attempts
8. **Revocation**: Implement ability to revoke/reset 2FA if account is compromised

## Database Permissions

```sql
GRANT SELECT, INSERT, UPDATE ON totp_secrets TO app_user;
GRANT SELECT, INSERT, UPDATE ON backup_codes TO app_user;
GRANT SELECT ON users TO app_user;
```

## Migration Notes

- Flyway migration `V1__init_schema.sql` includes all 2FA tables and indexes
- Existing users have `totp_enabled = FALSE` by default
- No existing data is affected by the schema addition
- All foreign key constraints use `ON DELETE CASCADE` for data consistency

## Testing

```sql
-- Verify 2FA tables exist
SELECT tablename FROM pg_tables 
WHERE tablename IN ('totp_secrets', 'backup_codes');

-- Verify indexes
SELECT indexname FROM pg_indexes 
WHERE tablename IN ('totp_secrets', 'backup_codes');

-- Check users table has 2FA columns
SELECT column_name FROM information_schema.columns 
WHERE table_name = 'users' 
AND column_name IN ('totp_enabled', 'totp_verified_at');
```

## Future Enhancements

1. **WebAuthn Support**: Add FIDO2/WebAuthn for hardware security keys
2. **SMS OTP Fallback**: Optional SMS-based OTP as secondary method
3. **Remember Device**: Option to skip 2FA on trusted devices for 30 days
4. **2FA Policy Enforcement**: Admin-level policies requiring 2FA for roles
5. **2FA Method Recovery**: UI flow for re-enrolling after device loss
6. **Phone Number Verification**: For SMS OTP and account recovery

