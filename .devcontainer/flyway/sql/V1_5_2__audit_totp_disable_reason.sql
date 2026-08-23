-- Make TOTP disable operations auditable and preserve the caller's reason.

CREATE OR REPLACE FUNCTION private_schema.audit_users()
  RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER
  SET search_path = pg_catalog, private_schema
AS $$
DECLARE target_record RECORD;
BEGIN
  target_record := CASE TG_OP WHEN 'DELETE' THEN OLD ELSE NEW END;
  INSERT INTO private_schema.audit_logs (
    actor_id, actor_type, action, target_type, target_id, metadata
  ) VALUES (
    NULLIF(current_setting('app.actor_id', true), '')::UUID,
    COALESCE(NULLIF(current_setting('app.actor_type', true), ''), 'MIGRATION'),
    CASE TG_OP
      WHEN 'INSERT' THEN 'USER_CREATED'
      WHEN 'UPDATE' THEN CASE WHEN OLD.password_hash IS DISTINCT FROM NEW.password_hash
        THEN 'USER_PASSWORD_ROTATED' ELSE 'USER_UPDATED' END
      WHEN 'DELETE' THEN 'USER_DISABLED'
    END,
    'users', target_record.user_id,
    jsonb_build_object(
      'user_id', target_record.user_id, 'username', target_record.username,
      'role_id', target_record.role_id,
      'password_reset_required_at', target_record.password_reset_required_at,
      'mfa_required_at', target_record.mfa_required_at,
      'created_at', target_record.created_at, 'updated_at', target_record.updated_at
    )
  );
  RETURN CASE TG_OP WHEN 'DELETE' THEN OLD ELSE NEW END;
END;
$$;

CREATE OR REPLACE FUNCTION private_schema.audit_invalidated_jwts()
  RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER
  SET search_path = pg_catalog, private_schema
AS $$
BEGIN
  INSERT INTO private_schema.audit_logs (
    actor_id, actor_type, action, target_type, target_id, metadata
  ) VALUES (
    NULLIF(current_setting('app.actor_id', true), '')::UUID,
    COALESCE(NULLIF(current_setting('app.actor_type', true), ''), 'MIGRATION'),
    'TOKEN_INVALIDATED', 'invalidated_jwts', NEW.id,
    jsonb_build_object(
      'jti', NEW.jti, 'reason', NEW.reason,
      'expiry_timestamp', NEW.expiry_timestamp, 'created_at', NEW.created_at
    )
  );
  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION private_schema.audit_refresh_tokens()
  RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER
  SET search_path = pg_catalog, private_schema
AS $$
DECLARE target_record RECORD;
BEGIN
  target_record := CASE TG_OP WHEN 'DELETE' THEN OLD ELSE NEW END;
  INSERT INTO private_schema.audit_logs (
    actor_id, actor_type, action, target_type, target_id, metadata
  ) VALUES (
    NULLIF(current_setting('app.actor_id', true), '')::UUID,
    COALESCE(NULLIF(current_setting('app.actor_type', true), ''), 'MIGRATION'),
    CASE TG_OP
      WHEN 'INSERT' THEN 'REFRESH_TOKEN_ISSUED'
      WHEN 'UPDATE' THEN CASE WHEN NEW.rotated_at IS NOT NULL
        THEN 'REFRESH_TOKEN_ROTATED' ELSE 'REFRESH_TOKEN_REVOKED' END
      WHEN 'DELETE' THEN 'REFRESH_TOKEN_REVOKED'
    END,
    'refresh_tokens', target_record.id,
    jsonb_build_object(
      'user_id', target_record.user_id, 'client_id', target_record.client_id,
      'family_id', target_record.family_id, 'issued_at', target_record.issued_at,
      'expires_at', target_record.expires_at, 'revoked_at', target_record.revoked_at,
      'revoke_reason', target_record.revoke_reason, 'rotated_at', target_record.rotated_at,
      'issued_ip', target_record.issued_ip, 'last_used_at', target_record.last_used_at,
      'created_at', target_record.created_at
    )
  );
  RETURN CASE TG_OP WHEN 'DELETE' THEN OLD ELSE NEW END;
END;
$$;

CREATE OR REPLACE FUNCTION private_schema.audit_totp_disabled()
  RETURNS TRIGGER
  LANGUAGE plpgsql
  SECURITY DEFINER
  SET search_path = pg_catalog, private_schema
AS $$
DECLARE
  target_id uuid;
  target_user_id uuid;
  previous_verified_at timestamptz;
BEGIN
  IF TG_OP = 'DELETE' THEN
    target_id := OLD.id;
    target_user_id := OLD.user_id;
    previous_verified_at := OLD.verified_at;
  ELSIF OLD.verified_at IS NOT NULL AND NEW.verified_at IS NULL THEN
    target_id := NEW.id;
    target_user_id := NEW.user_id;
    previous_verified_at := OLD.verified_at;
  ELSE
    RETURN CASE TG_OP WHEN 'DELETE' THEN OLD ELSE NEW END;
  END IF;

  INSERT INTO private_schema.audit_logs (
    actor_id, actor_type, action, target_type, target_id, metadata
  ) VALUES (
    NULLIF(current_setting('app.actor_id', true), '')::UUID,
    COALESCE(NULLIF(current_setting('app.actor_type', true), ''), 'USER'),
    'TOTP_DISABLED',
    'totp_secrets',
    target_id,
    jsonb_build_object(
      'user_id', target_user_id,
      'was_verified_at', previous_verified_at,
      'reason', COALESCE(NULLIF(current_setting('app.disable_reason', true), ''), 'UNSPECIFIED')
    )
  );

  RETURN CASE TG_OP WHEN 'DELETE' THEN OLD ELSE NEW END;
END;
$$;

DROP TRIGGER IF EXISTS trg_audit_totp_disabled ON private_schema.totp_secrets;

CREATE TRIGGER trg_audit_totp_disabled
  AFTER UPDATE OR DELETE ON private_schema.totp_secrets
  FOR EACH ROW
  EXECUTE FUNCTION private_schema.audit_totp_disabled();

CREATE OR REPLACE FUNCTION api_schema.disable_totp(
    p_user_id uuid,
    p_reason text
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
    PERFORM set_config(
        'app.disable_reason',
        COALESCE(NULLIF(trim(p_reason), ''), 'UNSPECIFIED'),
        true
    );

    DELETE FROM private_schema.totp_secrets
    WHERE user_id = p_user_id;

    v_removed := FOUND;

    DELETE FROM private_schema.backup_codes
    WHERE user_id = p_user_id;

    RETURN v_removed;
END;
$$;

COMMENT ON FUNCTION api_schema.disable_totp(uuid, text) IS
    'Remove TOTP and backup codes and emit a TOTP_DISABLED audit event with the supplied reason. SECURITY DEFINER runs as owner_role.';

ALTER FUNCTION api_schema.disable_totp(uuid, text) OWNER TO owner_role;
REVOKE ALL ON FUNCTION api_schema.disable_totp(uuid, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION api_schema.disable_totp(uuid, text) TO ${API_USER};

DROP FUNCTION IF EXISTS api_schema.disable_totp(uuid);