-- Flyway migration: V1_2__add_totp_test_data.sql
-- Test data for 2FA functionality (development/testing only)
--
-- This migration adds test users with and without TOTP enabled to support
-- testing the 2FA implementation during development.
--
-- Disabled by default. LOAD_TEST_DATA must be explicitly true and test-only secret
-- placeholders must be supplied by an isolated test environment.

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
  api_schema.encrypt_totp_secret(
    '${TOTP_TEST_SECRET}',
    '${TOTP_TEST_ENCRYPTION_KEY}'
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
AND '${LOAD_TEST_DATA}'::boolean
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
-- Generate 10 backup codes (cross join to duplicate rows)
CROSS JOIN generate_series(1, 10)
WHERE u.username = 'admin'
AND '${LOAD_TEST_DATA}'::boolean
AND EXISTS (
  SELECT 1 FROM private_schema.totp_secrets ts WHERE ts.user_id = u.user_id
)
AND NOT EXISTS (
  SELECT 1 FROM private_schema.backup_codes bc WHERE bc.user_id = u.user_id
);

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
    'test_data', true,
    'note', 'Test data for 2FA development/testing only'
  ),
  now()
FROM private_schema.users u
JOIN private_schema.totp_secrets ts ON u.user_id = ts.user_id
WHERE u.username = 'admin'
AND '${LOAD_TEST_DATA}'::boolean
LIMIT 1;

-- Test credentials are supplied only through explicit Flyway placeholders in an
-- isolated test environment. No reusable secret is stored in this migration.
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

