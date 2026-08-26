-- Administrator-issued recovery tokens. Raw tokens never enter PostgreSQL.
CREATE TABLE private_schema.recovery_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES private_schema.users(user_id) ON DELETE CASCADE,
    issued_by UUID NOT NULL REFERENCES private_schema.users(user_id) ON DELETE RESTRICT,
    token_hash TEXT NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX recovery_tokens_active_user_idx
    ON private_schema.recovery_tokens (user_id, expires_at) WHERE consumed_at IS NULL;

INSERT INTO private_schema.permissions (name)
VALUES ('manage_recovery')
ON CONFLICT (name) DO NOTHING;

INSERT INTO private_schema.role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM private_schema.roles r CROSS JOIN private_schema.permissions p
WHERE r.name = 'admin' AND p.name = 'manage_recovery'
ON CONFLICT (role_id, permission_id) DO NOTHING;

ALTER TABLE private_schema.audit_logs DROP CONSTRAINT chk_audit_action;
ALTER TABLE private_schema.audit_logs ADD CONSTRAINT chk_audit_action CHECK (action IN (
    'USER_CREATED', 'USER_UPDATED', 'USER_DISABLED', 'USER_PASSWORD_ROTATED',
    'USER_MFA_REQUIRED', 'USER_MFA_REQUIRED_REMOVED', 'TOKEN_ISSUED', 'TOKEN_INVALIDATED',
    'REFRESH_TOKEN_ISSUED', 'REFRESH_TOKEN_ROTATED', 'REFRESH_TOKEN_REVOKED',
    'TRUSTED_CLIENT_CREATED', 'TRUSTED_CLIENT_UPDATED', 'TRUSTED_CLIENT_REVOKED',
    'ROLE_PERMISSION_ASSIGNED', 'ROLE_PERMISSION_REMOVED', 'TOTP_ENABLED', 'TOTP_DISABLED',
    'TOTP_LAST_USED', 'BACKUP_CODES_GENERATED', 'RECOVERY_ISSUED', 'RECOVERY_COMPLETED',
    'RECOVERY_FAILED'
));

CREATE OR REPLACE FUNCTION api_schema.issue_recovery_token(
    p_administrator_id uuid, p_target_user_id uuid, p_token_hash text
) RETURNS void
LANGUAGE plpgsql VOLATILE SECURITY DEFINER
SET search_path = pg_catalog, private_schema
AS $$
BEGIN
    UPDATE private_schema.recovery_tokens
    SET consumed_at = now()
    WHERE user_id = p_target_user_id AND consumed_at IS NULL;

    INSERT INTO private_schema.recovery_tokens (user_id, issued_by, token_hash, expires_at)
    VALUES (p_target_user_id, p_administrator_id, p_token_hash, now() + interval '15 minutes');

    INSERT INTO private_schema.audit_logs (actor_id, actor_type, action, target_type, target_id, metadata)
    VALUES (p_administrator_id, 'USER', 'RECOVERY_ISSUED', 'USER', p_target_user_id, '{}'::jsonb);
END;
$$;

CREATE OR REPLACE FUNCTION api_schema.consume_recovery_token(
    p_user_id uuid, p_token_hash text, p_password_hash text
) RETURNS text
LANGUAGE plpgsql VOLATILE SECURITY DEFINER
SET search_path = pg_catalog, private_schema
AS $$
DECLARE
    v_token private_schema.recovery_tokens%ROWTYPE;
BEGIN
    SELECT * INTO v_token FROM private_schema.recovery_tokens
    WHERE user_id = p_user_id AND token_hash = p_token_hash
    FOR UPDATE;

    IF NOT FOUND OR v_token.consumed_at IS NOT NULL OR v_token.expires_at <= now() THEN
        INSERT INTO private_schema.audit_logs (actor_type, action, target_type, target_id, metadata)
        VALUES ('SYSTEM', 'RECOVERY_FAILED', 'USER', p_user_id, '{}'::jsonb);
        RETURN 'INVALID_OR_CONSUMED';
    END IF;

    UPDATE private_schema.recovery_tokens SET consumed_at = now() WHERE id = v_token.id;
    UPDATE private_schema.users
    SET password_hash = p_password_hash, password_reset_required_at = NULL,
        mfa_required_at = now(), updated_at = now()
    WHERE user_id = p_user_id;
    UPDATE private_schema.refresh_tokens
    SET revoked_at = now(), revoke_reason = 'account_recovery'
    WHERE user_id = p_user_id AND revoked_at IS NULL;
    DELETE FROM private_schema.totp_secrets WHERE user_id = p_user_id;
    INSERT INTO private_schema.audit_logs (actor_type, action, target_type, target_id, metadata)
    VALUES ('SYSTEM', 'RECOVERY_COMPLETED', 'USER', p_user_id, '{}'::jsonb);
    RETURN 'COMPLETED';
END;
$$;

ALTER FUNCTION api_schema.issue_recovery_token(uuid, uuid, text) OWNER TO owner_role;
ALTER FUNCTION api_schema.consume_recovery_token(uuid, text, text) OWNER TO owner_role;
REVOKE ALL ON TABLE private_schema.recovery_tokens FROM PUBLIC;
REVOKE ALL ON FUNCTION api_schema.issue_recovery_token(uuid, uuid, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION api_schema.consume_recovery_token(uuid, text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION api_schema.issue_recovery_token(uuid, uuid, text) TO ${API_USER};
GRANT EXECUTE ON FUNCTION api_schema.consume_recovery_token(uuid, text, text) TO ${API_USER};