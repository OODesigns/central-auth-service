-- Flyway migration: V1_4_6__add_refresh_token_api_functions.sql
-- Add write-side SECURITY DEFINER API functions for refresh-token rotation with reuse detection.
--
-- FUNCTIONS CREATED (2 total — all SECURITY DEFINER):
--  - api_schema.store_refresh_token(user_id, token_hash)      : Record a newly issued refresh
--                                                               token, starting a new family.
--  - api_schema.rotate_refresh_token(old_hash, new_hash)      : Atomically consume the presented
--                                                               token and issue its replacement in
--                                                               the same family; detect reuse.
--
-- SECURITY MODEL (matches existing V1_0_6 / V1_4_x functions):
--  - SECURITY DEFINER: each function runs as owner_role, not the caller (api_role)
--  - SET search_path: locked to pg_catalog + private_schema to prevent search-path injection
--  - REVOKE ALL … FROM PUBLIC then GRANT EXECUTE … TO ${API_USER} on every function
--  - Functions owned by owner_role (ALTER FUNCTION … OWNER TO owner_role)
--
-- ROTATION / REUSE-DETECTION SEMANTICS:
--  - A token is "current" while rotated_at IS NULL AND revoked_at IS NULL.
--  - Legitimate rotation marks the presented token rotated_at = revoked_at = now()
--    (revoke_reason = 'rotated'), records replaced_by_token_hash, and inserts a fresh token
--    in the SAME family_id.
--  - Presenting an already-rotated/revoked token is treated as compromise: the ENTIRE family
--    is revoked (revoke_reason = 'reuse_detected') and 'REUSE_DETECTED' is returned.
--  - SELECT … FOR UPDATE serialises concurrent rotations of the same token.
--  - The refresh-token lifetime (7 days) is owned here to match TokenService.REFRESH_TOKEN_TTL.
--
-- IDEMPOTENCY: CREATE OR REPLACE FUNCTION — safe to re-run.
--
-- DEPENDENCIES: Tables (V1_0_2), Indexes (V1_0_3), Roles (V1_0_1), API functions (V1_0_6)

-- ============================================================================
-- 1. STORE REFRESH TOKEN  (new family — family_id / expires_at defaulted here)
-- ============================================================================
CREATE OR REPLACE FUNCTION api_schema.store_refresh_token(
    p_user_id    uuid,
    p_token_hash text
)
RETURNS void
LANGUAGE sql
VOLATILE
SECURITY DEFINER
SET search_path = pg_catalog, private_schema
AS $$
    INSERT INTO private_schema.refresh_tokens (user_id, token_hash, expires_at)
    VALUES (p_user_id, p_token_hash, now() + interval '7 days');
$$;

COMMENT ON FUNCTION api_schema.store_refresh_token(uuid, text) IS
    'Records a newly issued refresh token (hashed), starting a new token family with a 7-day '
    'expiry. Called after a successful login or 2FA verification. SECURITY DEFINER runs as owner_role.';

ALTER FUNCTION api_schema.store_refresh_token(uuid, text) OWNER TO owner_role;
REVOKE ALL ON FUNCTION api_schema.store_refresh_token(uuid, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION api_schema.store_refresh_token(uuid, text) TO ${API_USER};

-- ============================================================================
-- 2. ROTATE REFRESH TOKEN  (atomic consume-and-replace + reuse detection)
-- ============================================================================
CREATE OR REPLACE FUNCTION api_schema.rotate_refresh_token(
    p_old_hash text,
    p_new_hash text
)
RETURNS text
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = pg_catalog, private_schema
AS $$
DECLARE
    v_row private_schema.refresh_tokens%ROWTYPE;
BEGIN
    -- Serialise concurrent rotations of the same token.
    SELECT * INTO v_row
    FROM   private_schema.refresh_tokens
    WHERE  token_hash = p_old_hash
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN 'NOT_FOUND';
    END IF;

    -- Reuse detection: the presented token was already consumed or revoked → assume
    -- compromise and revoke every still-active token in the family.
    IF v_row.rotated_at IS NOT NULL OR v_row.revoked_at IS NOT NULL THEN
        UPDATE private_schema.refresh_tokens
        SET    revoked_at    = now(),
               revoke_reason = 'reuse_detected'
        WHERE  family_id  = v_row.family_id
          AND  revoked_at IS NULL;
        RETURN 'REUSE_DETECTED';
    END IF;

    IF v_row.expires_at <= now() THEN
        UPDATE private_schema.refresh_tokens
        SET    revoked_at    = now(),
               revoke_reason = 'expired'
        WHERE  id = v_row.id;
        RETURN 'EXPIRED';
    END IF;

    -- Rotate: consume the presented token and record its replacement.
    UPDATE private_schema.refresh_tokens
    SET    rotated_at             = now(),
           revoked_at             = now(),
           revoke_reason          = 'rotated',
           replaced_by_token_hash = p_new_hash,
           last_used_at           = now()
    WHERE  id = v_row.id;

    INSERT INTO private_schema.refresh_tokens (user_id, token_hash, family_id, expires_at)
    VALUES (v_row.user_id, p_new_hash, v_row.family_id, now() + interval '7 days');

    RETURN 'ROTATED';
END;
$$;

COMMENT ON FUNCTION api_schema.rotate_refresh_token(text, text) IS
    'Atomically rotates a refresh token: consumes the presented token (hashed) and issues a '
    'replacement in the same family. Returns ROTATED, REUSE_DETECTED (family revoked), EXPIRED, '
    'or NOT_FOUND. SECURITY DEFINER runs as owner_role.';

ALTER FUNCTION api_schema.rotate_refresh_token(text, text) OWNER TO owner_role;
REVOKE ALL ON FUNCTION api_schema.rotate_refresh_token(text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION api_schema.rotate_refresh_token(text, text) TO ${API_USER};

