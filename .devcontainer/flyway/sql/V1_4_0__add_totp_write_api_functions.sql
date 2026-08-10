-- Flyway migration: V1_4_0__add_totp_write_api_functions.sql
-- Add write-side SECURITY DEFINER API functions for the 2FA enrolment and management flows.
--
-- FUNCTIONS CREATED (5 total — all SECURITY DEFINER):
--  - api_schema.store_totp_secret(user_id, encrypted_secret)  : Upsert pending TOTP secret during setup
--  - api_schema.enable_totp(user_id)                          : Activate TOTP after first-code verification
--  - api_schema.disable_totp(user_id)                         : Remove TOTP + backup codes (CASCADE)
--  - api_schema.insert_backup_codes(user_id, hashes, batch)   : Atomic batch replacement of backup codes
--  - api_schema.consume_backup_code(user_id, code_hash)       : Single-use backup code redemption
--
-- SECURITY MODEL (matches existing V1_0_6 read functions):
--  - SECURITY DEFINER: each function runs as owner_role, not the caller (api_role)
--  - SET search_path: locked to pg_catalog + private_schema to prevent search-path injection
--  - REVOKE ALL … FROM PUBLIC then GRANT EXECUTE … TO ${API_USER} on every function
--  - Functions owned by owner_role (ALTER FUNCTION … OWNER TO owner_role)
--
-- IDEMPOTENCY:
--  - store_totp_secret uses INSERT … ON CONFLICT DO UPDATE (safe to re-run setup)
--  - enable_totp guards with WHERE verified_at IS NULL (no-op if already enabled)
--  - disable_totp returns false if the row is already absent (no error)
--  - insert_backup_codes: DELETE-then-INSERT inside one transaction step (no partial state)
--  - consume_backup_code guards with WHERE used_at IS NULL (second call returns false)
--
-- DEPENDENCIES: Tables (V1_0_2), Roles (V1_0_1), Read functions (V1_0_6)

-- ============================================================================
-- 1. STORE TOTP SECRET  (pending setup — verified_at left NULL)
-- ============================================================================
-- Called by the TotpSetupProvider adapter after generating + encrypting the secret.
-- Uses ON CONFLICT to allow re-setup (overwrites any pending-but-unverified secret).
-- If the user already has a verified secret the row is still overwritten and verified_at
-- is reset to NULL, which forces the user through EnableTotp again — intentional.

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
        user_id,
        secret_key_encrypted,
        algorithm,
        period_seconds,
        digits,
        verified_at
    )
    VALUES (
        p_user_id,
        p_encrypted_secret,
        'SHA1',
        30,
        6,
        NULL   -- always pending after (re-)setup
    )
    ON CONFLICT (user_id) DO UPDATE
        SET secret_key_encrypted = excluded.secret_key_encrypted,
            verified_at          = NULL,          -- reset if re-setup
            backup_codes_generated_at = NULL,     -- old codes become stale
            updated_at           = now();
$$;

COMMENT ON FUNCTION api_schema.store_totp_secret(uuid, bytea) IS
    'Upsert a pending (unverified) TOTP secret for the given user. '
    'Called during the TOTP setup flow before the user scans the QR code. '
    'verified_at is deliberately set to NULL so the secret is not active until '
    'enable_totp() is called after the user proves possession of the secret. '
    'SECURITY DEFINER runs as owner_role.';

ALTER FUNCTION api_schema.store_totp_secret(uuid, bytea) OWNER TO owner_role;
REVOKE ALL ON FUNCTION api_schema.store_totp_secret(uuid, bytea) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION api_schema.store_totp_secret(uuid, bytea) TO ${API_USER};

-- ============================================================================
-- 2. ENABLE TOTP  (mark the pending secret as verified)
-- ============================================================================
-- Called by the TotpSetupProvider adapter after the user submits a valid first OTP.
-- Returns TRUE if the row was updated (was pending → now active).
-- Returns FALSE if the user has no pending secret (already enabled, or row absent).

CREATE OR REPLACE FUNCTION api_schema.enable_totp(
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
    SET    verified_at = now(),
           updated_at  = now()
    WHERE  user_id     = p_user_id
      AND  verified_at IS NULL;   -- only activate once; idempotent re-call returns false

    RETURN FOUND;
END;
$$;

COMMENT ON FUNCTION api_schema.enable_totp(uuid) IS
    'Activate TOTP for a user whose secret is in the pending state (verified_at IS NULL). '
    'Returns TRUE when the row was updated; FALSE when the secret is already verified or absent. '
    'The EnableTotpCommandHandler maps FALSE to TOTP_ALREADY_ENABLED. '
    'SECURITY DEFINER runs as owner_role.';

ALTER FUNCTION api_schema.enable_totp(uuid) OWNER TO owner_role;
REVOKE ALL ON FUNCTION api_schema.enable_totp(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION api_schema.enable_totp(uuid) TO ${API_USER};

-- ============================================================================
-- 3. DISABLE TOTP  (remove secret + backup codes via CASCADE)
-- ============================================================================
-- Deletes the totp_secrets row; backup_codes rows are removed automatically by
-- the ON DELETE CASCADE FK defined in V1_0_2.
-- Returns TRUE if a row was deleted, FALSE if the user had no TOTP secret.

CREATE OR REPLACE FUNCTION api_schema.disable_totp(
    p_user_id uuid
)
RETURNS boolean
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = pg_catalog, private_schema
AS $$
BEGIN
    DELETE FROM private_schema.totp_secrets
    WHERE  user_id = p_user_id;

    RETURN FOUND;
END;
$$;

COMMENT ON FUNCTION api_schema.disable_totp(uuid) IS
    'Remove TOTP for a user by deleting the totp_secrets row. '
    'Backup codes are removed automatically (ON DELETE CASCADE). '
    'Returns TRUE if TOTP was active and has been removed; FALSE if no row existed. '
    'SECURITY DEFINER runs as owner_role.';

ALTER FUNCTION api_schema.disable_totp(uuid) OWNER TO owner_role;
REVOKE ALL ON FUNCTION api_schema.disable_totp(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION api_schema.disable_totp(uuid) TO ${API_USER};

-- ============================================================================
-- 4. INSERT BACKUP CODES  (atomic batch replacement)
-- ============================================================================
-- Replaces ALL existing backup codes for the user with a new batch in one statement.
-- The adapter calls this after BCrypt-hashing the plaintext codes generated by
-- BackupCodeGenerator. Plaintext codes are never sent to the database.
--
-- p_code_hashes  — BCrypt hashes of the backup codes (array of text)
-- p_batch_id     — caller-generated UUID that groups the new batch; used by the app
--                  to invalidate old batches (DELETE WHERE batch_id != current_batch_id)

CREATE OR REPLACE FUNCTION api_schema.insert_backup_codes(
    p_user_id     uuid,
    p_code_hashes text[],
    p_batch_id    uuid
)
RETURNS void
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = pg_catalog, private_schema
AS $$
BEGIN
    -- Atomically replace all codes: old codes are invalidated, new batch inserted.
    DELETE FROM private_schema.backup_codes
    WHERE  user_id = p_user_id;

    INSERT INTO private_schema.backup_codes (user_id, generation_batch_id, code_hash)
    SELECT p_user_id, p_batch_id, unnested_hash
    FROM   unnest(p_code_hashes) AS unnested_hash;

    -- Record the generation timestamp on the parent secret row.
    UPDATE private_schema.totp_secrets
    SET    backup_codes_generated_at = now(),
           updated_at                = now()
    WHERE  user_id = p_user_id;
END;
$$;

COMMENT ON FUNCTION api_schema.insert_backup_codes(uuid, text[], uuid) IS
    'Atomically replace all backup codes for a user with a new BCrypt-hashed batch. '
    'Old codes are deleted first so no stale codes survive the rotation. '
    'p_code_hashes must contain BCrypt hashes — plaintext codes must never be stored. '
    'p_batch_id groups the codes so the adapter can later invalidate old batches. '
    'SECURITY DEFINER runs as owner_role.';

ALTER FUNCTION api_schema.insert_backup_codes(uuid, text[], uuid) OWNER TO owner_role;
REVOKE ALL ON FUNCTION api_schema.insert_backup_codes(uuid, text[], uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION api_schema.insert_backup_codes(uuid, text[], uuid) TO ${API_USER};

-- ============================================================================
-- 5. CONSUME BACKUP CODE  (single-use redemption)
-- ============================================================================
-- Marks exactly one backup code as used by setting used_at = now().
-- The adapter first fetches all unused code hashes for the user, BCrypt-compares
-- each against the submitted code in Java (BcryptPasswordVerifier), then calls
-- this function with the matched hash to consume it atomically.
--
-- The WHERE used_at IS NULL guard means:
--   - a second concurrent call for the same hash will find used_at IS NOT NULL and
--     return FALSE — preventing double-use even under concurrent requests.

CREATE OR REPLACE FUNCTION api_schema.consume_backup_code(
    p_user_id   uuid,
    p_code_hash text
)
RETURNS boolean
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = pg_catalog, private_schema
AS $$
BEGIN
    UPDATE private_schema.backup_codes
    SET    used_at = now()
    WHERE  user_id    = p_user_id
      AND  code_hash  = p_code_hash
      AND  used_at    IS NULL;    -- single-use: prevents double-consumption

    RETURN FOUND;
END;
$$;

COMMENT ON FUNCTION api_schema.consume_backup_code(uuid, text) IS
    'Mark a single backup code as used (single-use enforcement). '
    'p_code_hash must be the exact BCrypt hash stored in backup_codes.code_hash. '
    'The adapter resolves which hash to pass by BCrypt-comparing all unused hashes '
    'in Java and calling this function only for the matched hash. '
    'Returns TRUE if the code was consumed; FALSE if already used or not found. '
    'The WHERE used_at IS NULL predicate makes concurrent redemption safe. '
    'SECURITY DEFINER runs as owner_role.';

ALTER FUNCTION api_schema.consume_backup_code(uuid, text) OWNER TO owner_role;
REVOKE ALL ON FUNCTION api_schema.consume_backup_code(uuid, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION api_schema.consume_backup_code(uuid, text) TO ${API_USER};

