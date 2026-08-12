-- Flyway migration: V1_4_5__allow_pending_totp_secret_lookup.sql
-- Allow api_schema.get_totp_secret(uuid) to return the pending secret during TOTP
-- enrollment, not only already-verified secrets.
--
-- WHY: EnableTotpCommandHandler verifies the first OTP against the pending secret
-- immediately after SetupTotpCommandHandler stores it. The previous V1_4_1 function
-- filtered on verified_at IS NOT NULL, which made pending secrets invisible and
-- caused the enable flow to fail end-to-end.
--
-- SECURITY: The function still runs as SECURITY DEFINER under owner_role and only
-- exposes the encrypted secret + metadata for the requested user_id.

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
    WHERE ts.user_id = p_user_id;
$$;

COMMENT ON FUNCTION api_schema.get_totp_secret(uuid) IS
    'Returns the encrypted TOTP secret and configuration for a user. Used by the TOTP verifier adapter to validate user-entered codes during both enrollment and normal login. SECURITY DEFINER runs as owner_role.';

ALTER FUNCTION api_schema.get_totp_secret(uuid) OWNER TO owner_role;
REVOKE ALL ON FUNCTION api_schema.get_totp_secret(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION api_schema.get_totp_secret(uuid) TO ${API_USER};

