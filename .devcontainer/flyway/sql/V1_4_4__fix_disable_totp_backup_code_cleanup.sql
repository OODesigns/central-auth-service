-- Flyway migration: V1_4_4__fix_disable_totp_backup_code_cleanup.sql
-- Fix api_schema.disable_totp() to also delete the user's backup codes.
--
-- BUG: The V1_4_0 version only deleted the totp_secrets row and relied on
-- "ON DELETE CASCADE" for backup codes. However, backup_codes references
-- private_schema.users (not totp_secrets), so backup codes survived a 2FA
-- disable — leaving usable recovery codes for an account with 2FA turned off.
--
-- SECURITY: Backup codes MUST be invalidated when 2FA is disabled.

CREATE OR REPLACE FUNCTION api_schema.disable_totp(
    p_user_id uuid
)
RETURNS boolean
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = pg_catalog, private_schema
AS $$
DECLARE
    v_removed boolean;
BEGIN
    DELETE FROM private_schema.totp_secrets
    WHERE  user_id = p_user_id;

    v_removed := FOUND;

    -- Backup codes reference users (not totp_secrets), so they must be
    -- deleted explicitly when 2FA is disabled.
    DELETE FROM private_schema.backup_codes
    WHERE  user_id = p_user_id;

    RETURN v_removed;
END;
$$;

COMMENT ON FUNCTION api_schema.disable_totp(uuid) IS
    'Remove TOTP for a user by deleting the totp_secrets row and all backup codes. Returns TRUE if TOTP was active and has been removed; FALSE if no row existed. SECURITY DEFINER runs as owner_role.';

ALTER FUNCTION api_schema.disable_totp(uuid) OWNER TO owner_role;
REVOKE ALL ON FUNCTION api_schema.disable_totp(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION api_schema.disable_totp(uuid) TO ${API_USER};

