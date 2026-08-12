-- Flyway migration: V1_4_7__split_pending_totp_secret_lookup.sql
-- Separate the pending-secret lookup (enrollment) from the active-secret lookup (login).
--
-- BACKGROUND:
--  V1_4_5 relaxed api_schema.get_totp_secret(uuid) to return BOTH pending and verified
--  secrets so the enable flow could read the just-stored pending secret. Side effect:
--  the login-time verifier (JooqTotpVerifier.verifyCode → get_totp_secret) then also
--  accepted codes against a *pending* secret, i.e. before the user finished enrolment.
--
-- FIX:
--  - get_totp_secret(uuid)          → reverts to ACTIVE secrets only (verified_at IS NOT NULL).
--                                     Used by the login-time 2FA challenge (verifyCode).
--  - get_pending_totp_secret(uuid)  → NEW: PENDING secrets only (verified_at IS NULL).
--                                     Used by the enable flow (verifySetupCode) to validate the
--                                     first OTP before activation.
--
-- SECURITY MODEL (unchanged): SECURITY DEFINER under owner_role, locked search_path,
-- REVOKE ALL FROM PUBLIC then GRANT EXECUTE TO ${API_USER}, owned by owner_role.
--
-- DEPENDENCIES: Read functions (V1_4_1), pending-lookup relaxation (V1_4_5)

-- ============================================================================
-- 1. GET TOTP SECRET  (ACTIVE / verified secrets only — login-time verification)
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
    'Returns the encrypted TOTP secret and configuration for an ACTIVE 2FA enrollment '
    '(verified_at IS NOT NULL). Used by the TOTP verifier adapter to validate codes during '
    'login. Pending (not-yet-verified) secrets are intentionally invisible here — use '
    'get_pending_totp_secret during enrollment. SECURITY DEFINER runs as owner_role.';

ALTER FUNCTION api_schema.get_totp_secret(uuid) OWNER TO owner_role;
REVOKE ALL ON FUNCTION api_schema.get_totp_secret(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION api_schema.get_totp_secret(uuid) TO ${API_USER};

-- ============================================================================
-- 2. GET PENDING TOTP SECRET  (PENDING secrets only — enrollment verification)
-- ============================================================================
CREATE OR REPLACE FUNCTION api_schema.get_pending_totp_secret(
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
      AND ts.verified_at IS NULL;
$$;

COMMENT ON FUNCTION api_schema.get_pending_totp_secret(uuid) IS
    'Returns the encrypted TOTP secret for a PENDING enrollment (verified_at IS NULL). Used by '
    'the enable flow to validate the first OTP code before activating 2FA. SECURITY DEFINER '
    'runs as owner_role.';

ALTER FUNCTION api_schema.get_pending_totp_secret(uuid) OWNER TO owner_role;
REVOKE ALL ON FUNCTION api_schema.get_pending_totp_secret(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION api_schema.get_pending_totp_secret(uuid) TO ${API_USER};

