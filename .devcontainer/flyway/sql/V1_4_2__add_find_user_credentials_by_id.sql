-- Flyway migration: V1_4_2__add_find_user_credentials_by_id.sql
-- Add SECURITY DEFINER API function for re-authentication by user ID.
--
-- FUNCTION CREATED (1 total — SECURITY DEFINER):
--  - api_schema.find_user_credentials_by_id(uuid): Re-auth lookup — fetch password hash by user ID
--
-- SECURITY MODEL:
--  - SECURITY DEFINER: function runs as owner_role, not the caller (api_role)
--  - SET search_path: locked to pg_catalog + private_schema to prevent search-path injection
--  - REVOKE ALL … FROM PUBLIC then GRANT EXECUTE … TO ${API_USER}
--  - Function owned by owner_role
--
-- USE CASE:
--  - Used by DisableTotpCommandHandler for password re-authentication.
--  - Keyed by userId (UUID) from a verified session — never by a client-supplied username —
--    to prevent TOCTOU attacks where an attacker supplies a different user's credentials.
--
-- DEPENDENCIES: Tables (V1_0_2), Roles (V1_0_1), API functions (V1_0_6)

CREATE OR REPLACE FUNCTION api_schema.find_user_credentials_by_id(p_user_id uuid)
RETURNS TABLE (
    user_id       uuid,
    password_hash text
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, private_schema
AS $$
    SELECT
        u.user_id,
        u.password_hash
    FROM private_schema.users u
    WHERE u.user_id = p_user_id;
$$;

COMMENT ON FUNCTION api_schema.find_user_credentials_by_id(uuid) IS
    'Retrieves user credentials (password hash) by user ID for re-authentication flows. '
    'Used when the user identity is already established via a verified session or token '
    'and we must confirm knowledge of the account password (e.g. disabling 2FA). '
    'SECURITY DEFINER runs as owner_role.';

ALTER FUNCTION api_schema.find_user_credentials_by_id(uuid) OWNER TO owner_role;
REVOKE ALL ON FUNCTION api_schema.find_user_credentials_by_id(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION api_schema.find_user_credentials_by_id(uuid) TO ${API_USER};

