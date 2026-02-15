-- Flyway migration: V1_0_4__create_trigger_functions.sql
-- Create trigger functions for Central Auth Service (CAS)
--
-- FUNCTIONS CREATED (11 total):
--  - set_updated_at_timestamp(): Auto-update timestamp
--  - audit_users(): Log user lifecycle and MFA policy events
--  - audit_invalidated_jwts(): Log token invalidations
--  - audit_refresh_tokens(): Log refresh token events
--  - audit_trusted_clients(): Log client certificate events
--  - audit_role_permissions(): Log role permission changes
--  - audit_user_roles(): Log user role assignments
--  - audit_totp_enabled(): Log 2FA enablement
--  - audit_totp_disabled(): Log 2FA disablement
--  - audit_totp_last_used(): Log 2FA usage
--  - audit_backup_codes_generated(): Log backup code generation
--
-- SECURITY: All functions use SECURITY DEFINER + locked search_path
-- DEPENDENCIES: Tables (V1_0_2)

-- ============================================================================
-- UPDATED_AT TRIGGER FUNCTION
-- ============================================================================

CREATE OR REPLACE FUNCTION private_schema.set_updated_at_timestamp()
  RETURNS TRIGGER
  LANGUAGE plpgsql
  SECURITY DEFINER
  SET search_path = pg_catalog, private_schema
AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$;

COMMENT ON FUNCTION private_schema.set_updated_at_timestamp() IS
  'Internal trigger function - sets updated_at timestamp. SECURITY DEFINER ensures it runs as owner_role.';

-- ============================================================================
-- AUDIT TRIGGER FUNCTIONS
-- ============================================================================

CREATE OR REPLACE FUNCTION private_schema.audit_users()
  RETURNS TRIGGER
  LANGUAGE plpgsql
  SECURITY DEFINER
  SET search_path = pg_catalog, private_schema
AS $$
DECLARE
  target_record RECORD;
BEGIN
  -- Use NEW for INSERT/UPDATE, OLD for DELETE
  target_record := CASE TG_OP WHEN 'DELETE' THEN OLD ELSE NEW END;

  -- Log the primary user event (INSERT/UPDATE/DELETE)
  INSERT INTO private_schema.audit_logs (
    actor_id,
    actor_type,
    action,
    target_type,
    target_id,
    metadata
  ) VALUES (
    NULLIF(current_setting('app.actor_id', true), '')::UUID,
    COALESCE(NULLIF(current_setting('app.actor_type', true), ''), 'MIGRATION'),
    CASE TG_OP
      WHEN 'INSERT' THEN 'USER_CREATED'
      WHEN 'UPDATE' THEN
        CASE WHEN OLD.password_hash IS DISTINCT FROM NEW.password_hash
          THEN 'USER_PASSWORD_ROTATED'
          ELSE 'USER_UPDATED'
        END
      WHEN 'DELETE' THEN 'USER_DISABLED'
    END,
    'users',
    target_record.user_id,
    to_jsonb(target_record)
  );

  -- Detect MFA policy transitions (only on UPDATE)
  IF TG_OP = 'UPDATE' THEN
    -- Transition: NULL → NOT NULL (MFA became required)
    IF OLD.mfa_required_at IS NULL AND NEW.mfa_required_at IS NOT NULL THEN
      INSERT INTO private_schema.audit_logs (
        actor_id,
        actor_type,
        action,
        target_type,
        target_id,
        metadata
      ) VALUES (
        NULLIF(current_setting('app.actor_id', true), '')::UUID,
        COALESCE(NULLIF(current_setting('app.actor_type', true), ''), 'MIGRATION'),
        'USER_MFA_REQUIRED',
        'users',
        NEW.user_id,
        jsonb_build_object(
          'user_id', NEW.user_id,
          'username', NEW.username,
          'mfa_required_at', NEW.mfa_required_at,
          'reason', 'MFA enforcement policy applied'
        )
      );
    -- Transition: NOT NULL → NULL (MFA requirement removed)
    ELSIF OLD.mfa_required_at IS NOT NULL AND NEW.mfa_required_at IS NULL THEN
      INSERT INTO private_schema.audit_logs (
        actor_id,
        actor_type,
        action,
        target_type,
        target_id,
        metadata
      ) VALUES (
        NULLIF(current_setting('app.actor_id', true), '')::UUID,
        COALESCE(NULLIF(current_setting('app.actor_type', true), ''), 'MIGRATION'),
        'USER_MFA_REQUIRED_REMOVED',
        'users',
        NEW.user_id,
        jsonb_build_object(
          'user_id', NEW.user_id,
          'username', NEW.username,
          'was_required_at', OLD.mfa_required_at,
          'reason', 'MFA enforcement policy removed'
        )
      );
    END IF;
  END IF;

  -- Return appropriate record for trigger
  RETURN CASE TG_OP WHEN 'DELETE' THEN OLD ELSE NEW END;
END;
$$;

CREATE OR REPLACE FUNCTION private_schema.audit_invalidated_jwts()
  RETURNS TRIGGER
  LANGUAGE plpgsql
  SECURITY DEFINER
  SET search_path = pg_catalog, private_schema
AS $$
BEGIN
  INSERT INTO private_schema.audit_logs (
    actor_id,
    actor_type,
    action,
    target_type,
    target_id,
    metadata
  ) VALUES (
    NULLIF(current_setting('app.actor_id', true), '')::UUID,
    COALESCE(NULLIF(current_setting('app.actor_type', true), ''), 'MIGRATION'),
    'TOKEN_INVALIDATED',
    'invalidated_jwts',
    NEW.id,
    to_jsonb(NEW)
  );
  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION private_schema.audit_refresh_tokens()
  RETURNS TRIGGER
  LANGUAGE plpgsql
  SECURITY DEFINER
  SET search_path = pg_catalog, private_schema
AS $$
DECLARE
  target_record RECORD;
BEGIN
  -- Use NEW for INSERT/UPDATE, OLD for DELETE
  target_record := CASE TG_OP WHEN 'DELETE' THEN OLD ELSE NEW END;

  INSERT INTO private_schema.audit_logs (
    actor_id,
    actor_type,
    action,
    target_type,
    target_id,
    metadata
  ) VALUES (
    NULLIF(current_setting('app.actor_id', true), '')::UUID,
    COALESCE(NULLIF(current_setting('app.actor_type', true), ''), 'MIGRATION'),
    CASE TG_OP
      WHEN 'INSERT' THEN 'REFRESH_TOKEN_ISSUED'
      WHEN 'UPDATE' THEN
        CASE WHEN NEW.rotated_at IS NOT NULL
          THEN 'REFRESH_TOKEN_ROTATED'
          ELSE 'REFRESH_TOKEN_REVOKED'
        END
      WHEN 'DELETE' THEN 'REFRESH_TOKEN_REVOKED'
    END,
    'refresh_tokens',
    target_record.id,
    to_jsonb(target_record)
  );

  -- Return appropriate record for trigger
  RETURN CASE TG_OP WHEN 'DELETE' THEN OLD ELSE NEW END;
END;
$$;

CREATE OR REPLACE FUNCTION private_schema.audit_trusted_clients()
  RETURNS TRIGGER
  LANGUAGE plpgsql
  SECURITY DEFINER
  SET search_path = pg_catalog, private_schema
AS $$
DECLARE
  target_record RECORD;
BEGIN
  -- Use NEW for INSERT/UPDATE, OLD for DELETE
  target_record := CASE TG_OP WHEN 'DELETE' THEN OLD ELSE NEW END;

  INSERT INTO private_schema.audit_logs (
    actor_id,
    actor_type,
    action,
    target_type,
    target_id,
    metadata
  ) VALUES (
    NULLIF(current_setting('app.actor_id', true), '')::UUID,
    COALESCE(NULLIF(current_setting('app.actor_type', true), ''), 'MIGRATION'),
    CASE TG_OP
      WHEN 'INSERT' THEN 'TRUSTED_CLIENT_CREATED'
      WHEN 'UPDATE' THEN
        CASE WHEN NEW.revoked_at IS NOT NULL AND OLD.revoked_at IS NULL
          THEN 'TRUSTED_CLIENT_REVOKED'
          ELSE 'TRUSTED_CLIENT_UPDATED'
        END
      WHEN 'DELETE' THEN 'TRUSTED_CLIENT_REVOKED'
    END,
    'trusted_clients',
    target_record.id,
    to_jsonb(target_record)
  );

  -- Return appropriate record for trigger
  RETURN CASE TG_OP WHEN 'DELETE' THEN OLD ELSE NEW END;
END;
$$;

CREATE OR REPLACE FUNCTION private_schema.audit_role_permissions()
  RETURNS TRIGGER
  LANGUAGE plpgsql
  SECURITY DEFINER
  SET search_path = pg_catalog, private_schema
AS $$
DECLARE
  target_record RECORD;
  composite_id UUID;
BEGIN
  -- Use NEW for INSERT, OLD for DELETE
  target_record := CASE TG_OP WHEN 'DELETE' THEN OLD ELSE NEW END;
  -- Create deterministic UUID from composite key for audit trail
  composite_id := md5(target_record.role_id::text || ':' || target_record.permission_id::text)::uuid;

  INSERT INTO private_schema.audit_logs (
    actor_id,
    actor_type,
    action,
    target_type,
    target_id,
    metadata
  ) VALUES (
    NULLIF(current_setting('app.actor_id', true), '')::UUID,
    COALESCE(NULLIF(current_setting('app.actor_type', true), ''), 'MIGRATION'),
    CASE TG_OP
      WHEN 'INSERT' THEN 'ROLE_PERMISSION_ASSIGNED'
      WHEN 'DELETE' THEN 'ROLE_PERMISSION_REMOVED'
    END,
    'role_permissions',
    composite_id,
    jsonb_build_object(
      'role_id', target_record.role_id,
      'permission_id', target_record.permission_id
    )
  );

  RETURN CASE TG_OP WHEN 'DELETE' THEN OLD ELSE NEW END;
END;
$$;

-- ============================================================================
-- USER ROLES AUDIT FUNCTION
-- ============================================================================

CREATE OR REPLACE FUNCTION private_schema.audit_user_roles()
  RETURNS TRIGGER
  LANGUAGE plpgsql
  SECURITY DEFINER
  SET search_path = pg_catalog, private_schema
AS $$
DECLARE
  target_record RECORD;
  composite_id UUID;
BEGIN
  target_record := CASE TG_OP WHEN 'DELETE' THEN OLD ELSE NEW END;
  composite_id := md5(target_record.user_id::text || ':' || target_record.role_id::text)::uuid;

  INSERT INTO private_schema.audit_logs (
    actor_id,
    actor_type,
    action,
    target_type,
    target_id,
    metadata
  ) VALUES (
    NULLIF(current_setting('app.actor_id', true), '')::UUID,
    COALESCE(NULLIF(current_setting('app.actor_type', true), ''), 'MIGRATION'),
    CASE TG_OP
      WHEN 'INSERT' THEN 'USER_ROLE_ASSIGNED'
      WHEN 'DELETE' THEN 'USER_ROLE_REMOVED'
    END,
    'user_roles',
    composite_id,
    jsonb_build_object(
      'user_id', target_record.user_id,
      'role_id', target_record.role_id
    )
  );

  RETURN CASE TG_OP WHEN 'DELETE' THEN OLD ELSE NEW END;
END;
$$;

-- ============================================================================
-- TOTP AUDIT FUNCTIONS
-- ============================================================================

CREATE OR REPLACE FUNCTION private_schema.audit_totp_enabled()
  RETURNS TRIGGER
  LANGUAGE plpgsql
  SECURITY DEFINER
  SET search_path = pg_catalog, private_schema
AS $$
BEGIN
  -- Only fire when verified_at transitions from NULL to NOT NULL
  IF OLD.verified_at IS NULL AND NEW.verified_at IS NOT NULL THEN
    INSERT INTO private_schema.audit_logs (
      actor_id,
      actor_type,
      action,
      target_type,
      target_id,
      metadata
    ) VALUES (
      NULLIF(current_setting('app.actor_id', true), '')::UUID,
      COALESCE(NULLIF(current_setting('app.actor_type', true), ''), 'USER'),
      'TOTP_ENABLED',
      'totp_secrets',
      NEW.id,
      jsonb_build_object(
        'user_id', NEW.user_id,
        'verified_at', NEW.verified_at
      )
    );
  END IF;
  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION private_schema.audit_totp_disabled()
  RETURNS TRIGGER
  LANGUAGE plpgsql
  SECURITY DEFINER
  SET search_path = pg_catalog, private_schema
AS $$
BEGIN
  -- Only fire when verified_at transitions from NOT NULL to NULL
  IF OLD.verified_at IS NOT NULL AND NEW.verified_at IS NULL THEN
    INSERT INTO private_schema.audit_logs (
      actor_id,
      actor_type,
      action,
      target_type,
      target_id,
      metadata
    ) VALUES (
      NULLIF(current_setting('app.actor_id', true), '')::UUID,
      COALESCE(NULLIF(current_setting('app.actor_type', true), ''), 'USER'),
      'TOTP_DISABLED',
      'totp_secrets',
      NEW.id,
      jsonb_build_object(
        'user_id', NEW.user_id,
        'was_verified_at', OLD.verified_at,
        'reason', 'User disabled 2FA'
      )
    );
  END IF;
  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION private_schema.audit_totp_last_used()
  RETURNS TRIGGER
  LANGUAGE plpgsql
  SECURITY DEFINER
  SET search_path = pg_catalog, private_schema
AS $$
BEGIN
  -- Only fire when last_used_at changes
  IF OLD.last_used_at IS DISTINCT FROM NEW.last_used_at THEN
    INSERT INTO private_schema.audit_logs (
      actor_id,
      actor_type,
      action,
      target_type,
      target_id,
      metadata
    ) VALUES (
      NULLIF(current_setting('app.actor_id', true), '')::UUID,
      COALESCE(NULLIF(current_setting('app.actor_type', true), ''), 'USER'),
      'TOTP_LAST_USED',
      'totp_secrets',
      NEW.id,
      jsonb_build_object(
        'user_id', NEW.user_id,
        'last_used_at', NEW.last_used_at
      )
    );
  END IF;
  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION private_schema.audit_backup_codes_generated()
  RETURNS TRIGGER
  LANGUAGE plpgsql
  SECURITY DEFINER
  SET search_path = pg_catalog, private_schema
AS $$
BEGIN
  -- Only fire when backup_codes_generated_at changes
  IF OLD.backup_codes_generated_at IS DISTINCT FROM NEW.backup_codes_generated_at THEN
    INSERT INTO private_schema.audit_logs (
      actor_id,
      actor_type,
      action,
      target_type,
      target_id,
      metadata
    ) VALUES (
      NULLIF(current_setting('app.actor_id', true), '')::UUID,
      COALESCE(NULLIF(current_setting('app.actor_type', true), ''), 'USER'),
      'BACKUP_CODES_GENERATED',
      'totp_secrets',
      NEW.id,
      jsonb_build_object(
        'user_id', NEW.user_id,
        'generated_at', NEW.backup_codes_generated_at
      )
    );
  END IF;
  RETURN NEW;
END;
$$;

