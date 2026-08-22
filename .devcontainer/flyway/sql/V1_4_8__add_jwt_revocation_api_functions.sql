-- Flyway migration: V1_4_8__add_jwt_revocation_api_functions.sql
-- Add SECURITY DEFINER API functions for access-token logout / revocation.
--
-- FUNCTIONS CREATED (2 total — all SECURITY DEFINER):
--  - api_schema.invalidate_jwt(jti, token_hash, expiry_timestamp, reason)
--      Record a revoked access token in private_schema.invalidated_jwts.
--  - api_schema.is_jwt_invalidated(jti)
--      Check whether an access token JTI has already been revoked.
--
-- SECURITY MODEL:
--  - SECURITY DEFINER: each function runs as owner_role, not the caller (api_role)
--  - SET search_path: locked to pg_catalog + private_schema to prevent search-path injection
--  - REVOKE ALL … FROM PUBLIC then GRANT EXECUTE … TO ${API_USER} on every function
--  - Functions owned by owner_role (ALTER FUNCTION … OWNER TO owner_role)
--
-- IDEMPOTENCY: CREATE OR REPLACE FUNCTION — safe to re-run.

-- ============================================================================
-- 1. INVALIDATE JWT
-- ============================================================================
CREATE OR REPLACE FUNCTION api_schema.invalidate_jwt(
    p_jti              uuid,
    p_token_hash       text,
    p_expiry_timestamp timestamptz,
    p_reason           text
)
RETURNS void
LANGUAGE sql
VOLATILE
SECURITY DEFINER
SET search_path = pg_catalog, private_schema
AS $$
    INSERT INTO private_schema.invalidated_jwts (jti, token_hash, reason, expiry_timestamp)
    VALUES (p_jti::text, p_token_hash, p_reason, p_expiry_timestamp)
    ON CONFLICT (token_hash) DO NOTHING;
$$;

COMMENT ON FUNCTION api_schema.invalidate_jwt(uuid, text, timestamptz, text) IS
    'Records a revoked access JWT in private_schema.invalidated_jwts using both the stable JTI '
    'and the token hash. Used by logout and other revocation flows. SECURITY DEFINER runs as owner_role.';

ALTER FUNCTION api_schema.invalidate_jwt(uuid, text, timestamptz, text) OWNER TO owner_role;
REVOKE ALL ON FUNCTION api_schema.invalidate_jwt(uuid, text, timestamptz, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION api_schema.invalidate_jwt(uuid, text, timestamptz, text) TO ${API_USER};

-- ============================================================================
-- 2. CHECK JWT REVOCATION
-- ============================================================================
CREATE OR REPLACE FUNCTION api_schema.is_jwt_invalidated(
    p_jti uuid
)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, private_schema
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM private_schema.invalidated_jwts
        WHERE jti = p_jti::text
    );
$$;

COMMENT ON FUNCTION api_schema.is_jwt_invalidated(uuid) IS
    'Returns true when the supplied JWT JTI exists in private_schema.invalidated_jwts. Used by '
    'access-token validation paths to reject revoked tokens. SECURITY DEFINER runs as owner_role.';

ALTER FUNCTION api_schema.is_jwt_invalidated(uuid) OWNER TO owner_role;
REVOKE ALL ON FUNCTION api_schema.is_jwt_invalidated(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION api_schema.is_jwt_invalidated(uuid) TO ${API_USER};
