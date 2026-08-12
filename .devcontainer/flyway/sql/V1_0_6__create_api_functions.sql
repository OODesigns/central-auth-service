-- Flyway migration: V1_0_6__create_api_functions.sql
-- Create SECURITY DEFINER API entry point functions for Central Auth Service (CAS)
--
-- FUNCTIONS CREATED (4 total - all SECURITY DEFINER):
--  - api_schema.find_user_credentials(username): Login - fetch password hash
--  - api_schema.get_user(user_id): Fetch user with permissions
--  - api_schema.get_totp_status(user_id): Check if 2FA enabled
--  - api_schema.encrypt_totp_secret(secret, key): Encrypt TOTP secrets
--
-- SECURITY: All functions run as owner_role with locked search_path
-- DEPENDENCIES: Tables (V1_0_2), Roles (V1_0_1)
-- GRANTS: EXECUTE permission granted to ${API_USER} via DEFAULT PRIVILEGES

-- ============================================================================
-- LOGIN CREDENTIALS FUNCTION (SECURITY DEFINER)
-- ============================================================================

CREATE OR REPLACE FUNCTION api_schema.find_user_credentials(p_username text)
RETURNS TABLE (
  user_id uuid,
  username text,
  password_hash text,
  password_reset_required_at timestamptz
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, private_schema
AS $$
  SELECT
    u.user_id,
    u.username,
    u.password_hash,
    u.password_reset_required_at
  FROM private_schema.users u
  WHERE u.username = p_username;
$$;

COMMENT ON FUNCTION api_schema.find_user_credentials(text) IS
  'Retrieves user credentials for password-based authentication. SECURITY DEFINER runs as owner_role. Primary entry point for login.';

ALTER FUNCTION api_schema.find_user_credentials(text) OWNER TO owner_role;

-- ============================================================================
-- POST-AUTHENTICATION USER FUNCTION (SECURITY DEFINER)
-- ============================================================================

CREATE OR REPLACE FUNCTION api_schema.get_user(p_user_id uuid)
RETURNS TABLE (
  user_id uuid,
  username text,
  permissions text[],
  password_reset_required_at timestamptz,
  mfa_required_at timestamptz
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, private_schema
AS $$
  SELECT
    u.user_id,
    u.username,
    COALESCE(array_agg(DISTINCT p.name ORDER BY p.name), ARRAY[]::text[]) AS permissions,
    u.password_reset_required_at,
    u.mfa_required_at
  FROM private_schema.users u
  LEFT JOIN private_schema.roles r ON u.role_id = r.role_id
  LEFT JOIN private_schema.role_permissions rp ON r.role_id = rp.role_id
  LEFT JOIN private_schema.permissions p ON rp.permission_id = p.permission_id
  WHERE u.user_id = p_user_id
  GROUP BY u.user_id, u.username, u.password_reset_required_at, u.mfa_required_at;
$$;

COMMENT ON FUNCTION api_schema.get_user(uuid) IS
  'Retrieves authenticated user with permissions for authorization. SECURITY DEFINER runs as owner_role. Called after successful authentication.';

ALTER FUNCTION api_schema.get_user(uuid) OWNER TO owner_role;

-- ============================================================================
-- 2FA STATUS CHECK FUNCTION (SECURITY DEFINER)
-- ============================================================================

CREATE OR REPLACE FUNCTION api_schema.get_totp_status(p_user_id uuid)
RETURNS TABLE (
  user_id uuid
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, private_schema
AS $$
  SELECT ts.user_id
  FROM private_schema.totp_secrets ts
  WHERE ts.user_id = p_user_id
    AND ts.verified_at IS NOT NULL;
$$;

COMMENT ON FUNCTION api_schema.get_totp_status(uuid) IS
  'Checks if 2FA (TOTP) is enabled for user. SECURITY DEFINER runs as owner_role. Used to determine if TOTP verification required.';

ALTER FUNCTION api_schema.get_totp_status(uuid) OWNER TO owner_role;

-- ============================================================================
-- TOTP ENCRYPTION FUNCTION (SECURITY DEFINER)
-- ============================================================================

CREATE OR REPLACE FUNCTION api_schema.encrypt_totp_secret(p_secret text, p_encryption_key text)
RETURNS BYTEA
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
  SELECT encrypt(
    convert_to(p_secret, 'UTF8'),
    convert_to(p_encryption_key, 'UTF8'),
    'aes-cbc/pad:pkcs'
  );
$$;

COMMENT ON FUNCTION api_schema.encrypt_totp_secret(text, text) IS
  'Encrypt a TOTP secret using AES-CBC. SECURITY DEFINER runs as owner_role. Uses semantic-secure AES-CBC (not ECB).';

ALTER FUNCTION api_schema.encrypt_totp_secret(text, text) OWNER TO owner_role;

