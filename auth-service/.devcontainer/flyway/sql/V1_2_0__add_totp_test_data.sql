-- Flyway migration: V1_2__add_totp_test_data.sql
-- Test data for 2FA functionality (development/testing only)
--
-- This migration adds test users with and without TOTP enabled to support
-- testing the 2FA implementation during development.
--
-- NOTE: This file is for development/testing only and should NOT be used in production.
--       Remove this migration before deploying to production environments.
-- NOTE: Secret key is stored encrypted in production. For testing, we use a test key.

-- ============================================================================
-- TEST DATA: Users with 2FA
-- ============================================================================

-- Test User 1: 2FA-enabled user (credentials provided below)
-- This secret generates 6-digit codes and can be used with any TOTP app
-- WARNING: These are test credentials - regenerate for any non-test environment
INSERT INTO private_schema.totp_secrets (
  id,
  user_id,
  secret_key_encrypted,
  algorithm,
  period_seconds,
  digits,
  verified_at,
  backup_codes_generated_at,
  created_at,
  updated_at
) SELECT
  gen_random_uuid(),
  user_id,
  pgcrypto.encrypt(
    pgcrypto.convert('JBSWY3DPEBLW64TMMQ======', 'SQL_ASCII'),
    pgcrypto.convert('test-encryption-key-dev-only', 'SQL_ASCII'),
    'aes'
  ),
  'SHA1',
  30,
  6,
  now(),
  now(),
  now(),
  now()
FROM private_schema.users
WHERE username = 'admin'
AND NOT EXISTS (
  SELECT 1 FROM private_schema.totp_secrets WHERE user_id = private_schema.users.user_id
);

-- Generate test backup codes for admin user
-- In production, codes should be single-use and hashed
-- These test codes use a simple pattern for ease of testing
INSERT INTO private_schema.backup_codes (
  id,
  user_id,
  generation_batch_id,
  code_hash,
  used_at,
  created_at
)
SELECT
  gen_random_uuid(),
  u.user_id,
  gen_random_uuid(),  -- All codes in this batch share the same ID
  '$2a$10$' || substring(md5(random()::text), 1, 53),  -- Simulated bcrypt hash
  NULL,  -- Not yet used
  now()
FROM private_schema.users u
WHERE u.username = 'admin'
AND EXISTS (
  SELECT 1 FROM private_schema.totp_secrets ts WHERE ts.user_id = u.user_id
)
AND NOT EXISTS (
  SELECT 1 FROM private_schema.backup_codes bc WHERE bc.user_id = u.user_id
)
-- Generate 10 backup codes (cross join to duplicate rows)
CROSS JOIN generate_series(1, 10);

-- Note: users table no longer has totp_enabled or totp_verified_at columns
-- TOTP status is tracked in totp_secrets.verified_at

-- ============================================================================
-- AUDIT LOG: Log the test data initialization
-- ============================================================================

INSERT INTO private_schema.audit_logs (
  id,
  actor_id,
  actor_type,
  action,
  target_type,
  target_id,
  metadata,
  created_at
)
SELECT
  gen_random_uuid(),
  u.user_id,
  'MIGRATION',
  'TOTP_ENABLED',
  'totp_secrets',
  ts.id,
  jsonb_build_object(
    'test_secret', 'JBSWY3DPEBLW64TMMQ======',
    'note', 'Test data for 2FA development/testing only'
  ),
  now()
FROM private_schema.users u
JOIN private_schema.totp_secrets ts ON u.user_id = ts.user_id
WHERE u.username = 'admin'
LIMIT 1;

-- ============================================================================
-- TEST REFERENCE: TOTP Secret Information
-- ============================================================================
--
-- IMPORTANT: For testing 2FA with the test secret below, use a TOTP app or
-- generate codes with the following information:
--
-- Secret (Base32):     JBSWY3DPEBLW64TMMQ======
-- Algorithm:           SHA1
-- Period:              30 seconds
-- Digits:              6
--
-- To generate test codes:
-- 1. Use https://totp.dcode.fr/ or any TOTP generator
-- 2. Input the base32 secret above
-- 3. Match algorithm and period settings
-- 4. Generate 6-digit codes
--
-- Sample test codes (valid for 30-second windows):
-- - These codes change every 30 seconds
-- - Use current time-based codes during testing
--
-- Backup Codes:
-- - 10 single-use backup codes are generated automatically
-- - Query: SELECT code_hash FROM private_schema.backup_codes WHERE user_id = <admin_user_id>
--
-- WARNING: This is FOR TESTING ONLY - never use in production
--

-- ============================================================================
-- CLEANUP REFERENCE (if needed during development)
-- ============================================================================
--
-- To remove test 2FA data:
-- DELETE FROM private_schema.backup_codes WHERE user_id = (SELECT user_id FROM private_schema.users WHERE username = 'admin');
-- DELETE FROM private_schema.totp_secrets WHERE user_id = (SELECT user_id FROM private_schema.users WHERE username = 'admin');
-- DELETE FROM private_schema.audit_logs WHERE action IN ('TOTP_ENABLED', 'BACKUP_CODES_GENERATED') AND actor_type = 'MIGRATION';
--

