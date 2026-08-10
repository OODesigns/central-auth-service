-- Flyway migration: V1_4_1__add_totp_read_api_functions.sql
-- Add read-side SECURITY DEFINER API functions for the 2FA verification flow.
--
-- FUNCTIONS CREATED (3 total — all SECURITY DEFINER):
--  - api_schema.get_totp_secret(user_id)            : Retrieve encrypted TOTP secret + parameters
--  - api_schema.find_unused_backup_code_hashes(user_id) : List unused backup-code hashes
--  - api_schema.mark_totp_last_used(user_id)        : Record successful 2FA usage timestamp
--
-- SECURITY MODEL:
--  - SECURITY DEFINER: each function runs as owner_role, not the caller (api_role)
--  - SET search_path: locked to pg_catalog + private_schema to prevent search-path injection
--  - REVOKE ALL … FROM PUBLIC then GRANT EXECUTE … TO ${API_USER} on every function
--  - Functions owned by owner_role (ALTER FUNCTION … OWNER TO owner_role)
--
-- DEPENDENCIES: Tables (V1_0_2), Roles (V1_0_1), Write functions (V1_4_0)

-- ============================================================================
-- 1. GET TOTP SECRET  (encrypted secret + metadata)
-- ============================================================================

CREATE OR REPLACE FUNCTION api_schema.get_totp_secret(
    p_user_id uuid
)
RETURNS TABLE (
    secret_key_encrypted bytea,
    algorithm varchar(10),
    period_seconds integer,
    digits integer
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, private_schema
AS $$
    SELECT
        ts.secret_key_encrypted,
        ts.algorithm,
        ts.period_seconds,
        ts.digits
    FROM private_schema.totp_secrets ts
    WHERE ts.user_id = p_user_id
      AND ts.verified_at IS NOT NULL;
$$;

COMMENT ON FUNCTION api_schema.get_totp_secret(uuid) IS
    'Returns the encrypted TOTP secret and configuration for an active 2FA enrollment. '
    'Used by the TOTP verifier adapter to validate user-entered codes. '
    'SECURITY DEFINER runs as owner_role.';

ALTER FUNCTION api_schema.get_totp_secret(uuid) OWNER TO owner_role;
REVOKE ALL ON FUNCTION api_schema.get_totp_secret(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION api_schema.get_totp_secret(uuid) TO ${API_USER};

-- ============================================================================
-- 2. FIND UNUSED BACKUP-CODE HASHES
-- ============================================================================

CREATE OR REPLACE FUNCTION api_schema.find_unused_backup_code_hashes(
    p_user_id uuid
)
RETURNS text[]
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, private_schema
AS $$
    SELECT COALESCE(array_agg(bc.code_hash ORDER BY bc.created_at, bc.id), ARRAY[]::text[])
    FROM private_schema.backup_codes bc
    WHERE bc.user_id = p_user_id
      AND bc.used_at IS NULL
$$;

COMMENT ON FUNCTION api_schema.find_unused_backup_code_hashes(uuid) IS
    'Returns all currently unused backup-code hashes for a user as a text array. '
    'The verifier adapter compares the submitted plaintext code against the hashes in Java '
    'and then calls consume_backup_code() for the matching hash. '
    'SECURITY DEFINER runs as owner_role.';

ALTER FUNCTION api_schema.find_unused_backup_code_hashes(uuid) OWNER TO owner_role;
REVOKE ALL ON FUNCTION api_schema.find_unused_backup_code_hashes(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION api_schema.find_unused_backup_code_hashes(uuid) TO ${API_USER};

-- ============================================================================
-- 3. MARK TOTP LAST USED
-- ============================================================================

CREATE OR REPLACE FUNCTION api_schema.mark_totp_last_used(
    p_user_id uuid
)
RETURNS boolean
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = pg_catalog, private_schema
AS $$
BEGIN
    UPDATE private_schema.totp_secrets
    SET last_used_at = now(),
        updated_at = now()
    WHERE user_id = p_user_id
      AND verified_at IS NOT NULL;

    RETURN FOUND;
END;
$$;

COMMENT ON FUNCTION api_schema.mark_totp_last_used(uuid) IS
    'Updates totp_secrets.last_used_at after a successful TOTP or backup-code verification. '
    'SECURITY DEFINER runs as owner_role.';

ALTER FUNCTION api_schema.mark_totp_last_used(uuid) OWNER TO owner_role;
REVOKE ALL ON FUNCTION api_schema.mark_totp_last_used(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION api_schema.mark_totp_last_used(uuid) TO ${API_USER};


