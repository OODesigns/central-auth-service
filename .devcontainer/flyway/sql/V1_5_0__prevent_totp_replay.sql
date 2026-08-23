-- Prevent reuse of an accepted TOTP counter while preserving the existing +/-1 window.

ALTER TABLE private_schema.totp_secrets
    ADD COLUMN IF NOT EXISTS last_accepted_counter bigint;

COMMENT ON COLUMN private_schema.totp_secrets.last_accepted_counter IS
    'Highest RFC 6238 counter accepted for login. Atomic monotonic updates prevent TOTP replay.';

CREATE OR REPLACE FUNCTION api_schema.consume_totp_counter(
    p_user_id uuid,
    p_counter bigint
)
RETURNS boolean
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = pg_catalog, private_schema
AS $$
BEGIN
    IF p_counter < 0 THEN
        RETURN FALSE;
    END IF;

    UPDATE private_schema.totp_secrets
    SET last_accepted_counter = p_counter,
        last_used_at = now(),
        updated_at = now()
    WHERE user_id = p_user_id
      AND verified_at IS NOT NULL
      AND (last_accepted_counter IS NULL OR last_accepted_counter < p_counter);

    RETURN FOUND;
END;
$$;

COMMENT ON FUNCTION api_schema.consume_totp_counter(uuid, bigint) IS
    'Atomically accepts a TOTP counter only when it is newer than every counter previously accepted for the user.';

ALTER FUNCTION api_schema.consume_totp_counter(uuid, bigint) OWNER TO owner_role;
REVOKE ALL ON FUNCTION api_schema.consume_totp_counter(uuid, bigint) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION api_schema.consume_totp_counter(uuid, bigint) TO ${API_USER};

CREATE OR REPLACE FUNCTION api_schema.store_totp_secret(
    p_user_id          uuid,
    p_encrypted_secret bytea
)
RETURNS void
LANGUAGE sql
VOLATILE
SECURITY DEFINER
SET search_path = pg_catalog, private_schema
AS $$
    INSERT INTO private_schema.totp_secrets (
        user_id, secret_key_encrypted, algorithm, period_seconds, digits,
        verified_at, last_accepted_counter
    )
    VALUES (p_user_id, p_encrypted_secret, 'SHA1', 30, 6, NULL, NULL)
    ON CONFLICT (user_id) DO UPDATE
        SET secret_key_encrypted = excluded.secret_key_encrypted,
            verified_at = NULL,
            last_accepted_counter = NULL,
            last_used_at = NULL,
            backup_codes_generated_at = NULL,
            updated_at = now();
$$;

ALTER FUNCTION api_schema.store_totp_secret(uuid, bytea) OWNER TO owner_role;
REVOKE ALL ON FUNCTION api_schema.store_totp_secret(uuid, bytea) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION api_schema.store_totp_secret(uuid, bytea) TO ${API_USER};