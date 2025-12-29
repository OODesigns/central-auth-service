-- Flyway migration: V1__init_schema.sql
-- Initial schema for Central Auth Service (CAS)
--
-- Structure only. Seed data is handled in V1_1__seed_auth_data.sql

-- ============================================================================
-- CLEANUP (safe for re-run in dev only)
-- ============================================================================

DROP TRIGGER IF EXISTS trg_audit_users ON users;
DROP TRIGGER IF EXISTS trg_audit_invalidated_jwts ON invalidated_jwts;
DROP TRIGGER IF EXISTS trg_audit_refresh_tokens ON refresh_tokens;
DROP TRIGGER IF EXISTS trg_audit_trusted_clients ON trusted_clients;
DROP TRIGGER IF EXISTS trg_audit_role_permissions ON role_permissions;
DROP TRIGGER IF EXISTS trg_audit_user_roles ON user_roles;
DROP TRIGGER IF EXISTS trg_set_trusted_clients_updated_at ON trusted_clients;
DROP TRIGGER IF EXISTS trg_set_users_updated_at ON users;
DROP FUNCTION IF EXISTS audit_users();
DROP FUNCTION IF EXISTS audit_invalidated_jwts();
DROP FUNCTION IF EXISTS audit_refresh_tokens();
DROP FUNCTION IF EXISTS audit_trusted_clients();
DROP FUNCTION IF EXISTS audit_role_permissions();
DROP FUNCTION IF EXISTS audit_user_roles();
DROP FUNCTION IF EXISTS set_updated_at_timestamp();
DROP FUNCTION IF EXISTS auth.find_user_credentials(text);
DROP FUNCTION IF EXISTS auth.get_user(uuid);

DROP INDEX IF EXISTS idx_users_username;
DROP INDEX IF EXISTS idx_token_hash;
DROP INDEX IF EXISTS idx_jti;
DROP INDEX IF EXISTS idx_expiry_timestamp;
DROP INDEX IF EXISTS idx_refresh_tokens_user_id;
DROP INDEX IF EXISTS idx_refresh_tokens_family_id;
DROP INDEX IF EXISTS idx_refresh_tokens_expires_at;
DROP INDEX IF EXISTS idx_refresh_tokens_token_hash;
DROP INDEX IF EXISTS idx_refresh_tokens_active;
DROP INDEX IF EXISTS idx_user_roles_user_id;
DROP INDEX IF EXISTS idx_user_roles_role_id;
DROP INDEX IF EXISTS idx_trusted_clients_revoked;
DROP INDEX IF EXISTS idx_audit_logs_actor;
DROP INDEX IF EXISTS idx_audit_logs_action;
DROP INDEX IF EXISTS idx_audit_logs_created_at;
DROP INDEX IF EXISTS idx_audit_logs_action_actor_time;

DROP TABLE IF EXISTS refresh_tokens;
DROP TABLE IF EXISTS user_roles;
DROP TABLE IF EXISTS role_permissions;
DROP TABLE IF EXISTS invalidated_jwts;
DROP TABLE IF EXISTS permissions;
DROP TABLE IF EXISTS roles;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS trusted_clients;
DROP TABLE IF EXISTS audit_logs;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================================================================
-- ROLES & PERMISSIONS (database-level security)
-- ============================================================================

-- Create app-level role for application connections
CREATE ROLE app_user WITH LOGIN PASSWORD 'changeme';
COMMENT ON ROLE app_user IS 'Application-level database connection role with minimal required permissions';

-- Create auth schema for auth-related functions
CREATE SCHEMA IF NOT EXISTS auth;
GRANT USAGE ON SCHEMA auth TO app_user;
COMMENT ON SCHEMA auth IS 'Authentication and authorization functions';

-- ============================================================================
-- USERS
-- ============================================================================

CREATE TABLE users (
  user_id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  username                   VARCHAR(50) UNIQUE NOT NULL,
  password_hash              VARCHAR(255) NOT NULL,
  password_reset_required_at TIMESTAMPTZ DEFAULT now(),
  created_at                 TIMESTAMPTZ DEFAULT now(),
  updated_at                 TIMESTAMPTZ DEFAULT now()
);

COMMENT ON TABLE users IS
  'Application users and authentication credentials';
COMMENT ON COLUMN users.user_id IS
  'Unique user identifier (UUID primary key)';
COMMENT ON COLUMN users.username IS
  'Unique login name';
COMMENT ON COLUMN users.password_hash IS
  'Hashed user password (never store plaintext)';
COMMENT ON COLUMN users.password_reset_required_at IS
  'Timestamp when password reset was required; NULL if password reset is not required';
COMMENT ON COLUMN users.created_at IS
  'Timestamp when the user record was created';
COMMENT ON COLUMN users.updated_at IS
  'Timestamp of last update (auto-managed by trigger)';

-- ============================================================================
-- ROLES
-- ============================================================================

CREATE TABLE roles (
  role_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name        VARCHAR(50) UNIQUE NOT NULL,
  description TEXT
);

COMMENT ON TABLE roles IS
  'Role definitions. Values are static and seeded separately';
COMMENT ON COLUMN roles.role_id IS
  'Unique role identifier (UUID primary key)';
COMMENT ON COLUMN roles.name IS
  'Unique role name (e.g. admin, user, kiosk)';
COMMENT ON COLUMN roles.description IS
  'Human-readable role description';

-- ============================================================================
-- USER_ROLES
-- ============================================================================

CREATE TABLE user_roles (
  user_id UUID NOT NULL,
  role_id UUID NOT NULL,
  created_at TIMESTAMPTZ DEFAULT now(),
  PRIMARY KEY (user_id, role_id),
  FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
  FOREIGN KEY (role_id) REFERENCES roles(role_id) ON DELETE CASCADE
);

COMMENT ON TABLE user_roles IS
  'Join table mapping users to roles (many-to-many)';
COMMENT ON COLUMN user_roles.user_id IS
  'References users.user_id. ON DELETE CASCADE removes all role assignments when a user is deleted';
COMMENT ON COLUMN user_roles.role_id IS
  'References roles.role_id. ON DELETE CASCADE removes mappings when a role is deleted';
COMMENT ON COLUMN user_roles.created_at IS
  'Timestamp when the role assignment was created';

-- ============================================================================
-- PERMISSIONS
-- ============================================================================

CREATE TABLE permissions (
  permission_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name          VARCHAR(100) UNIQUE NOT NULL
);

COMMENT ON TABLE permissions IS
  'Permission definitions. Values are static and seeded separately';
COMMENT ON COLUMN permissions.permission_id IS
  'Unique permission identifier (UUID primary key)';
COMMENT ON COLUMN permissions.name IS
  'Unique permission name (e.g. manage_users)';

-- ============================================================================
-- ROLE_PERMISSIONS
-- ============================================================================

CREATE TABLE role_permissions (
  role_id       UUID NOT NULL,
  permission_id UUID NOT NULL,
  created_at    TIMESTAMPTZ DEFAULT now(),
  PRIMARY KEY (role_id, permission_id),
  FOREIGN KEY (role_id) REFERENCES roles(role_id) ON DELETE CASCADE,
  FOREIGN KEY (permission_id) REFERENCES permissions(permission_id) ON DELETE CASCADE
);

COMMENT ON TABLE role_permissions IS
  'Join table mapping roles to permissions (many-to-many)';
COMMENT ON COLUMN role_permissions.role_id IS
  'References roles.role_id. ON DELETE CASCADE removes permission mappings when a role is deleted';
COMMENT ON COLUMN role_permissions.permission_id IS
  'References permissions.permission_id. ON DELETE CASCADE removes mappings when a permission is deleted';
COMMENT ON COLUMN role_permissions.created_at IS
  'Timestamp when the permission was assigned to the role';

-- ============================================================================
-- INVALIDATED JWTs
-- ============================================================================

CREATE TABLE invalidated_jwts (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  jti              VARCHAR(255),
  token_hash       VARCHAR(255) NOT NULL,
  reason           TEXT,
  expiry_timestamp TIMESTAMPTZ NOT NULL,
  created_at       TIMESTAMPTZ DEFAULT now()
);

COMMENT ON TABLE invalidated_jwts IS
  'Revoked or invalidated access JWTs. Access tokens themselves are never stored';
COMMENT ON COLUMN invalidated_jwts.id IS
  'Unique identifier for the invalidated token entry (UUID primary key)';
COMMENT ON COLUMN invalidated_jwts.jti IS
  'JWT ID (jti claim) if present';
COMMENT ON COLUMN invalidated_jwts.token_hash IS
  'Hash of the invalidated JWT';
COMMENT ON COLUMN invalidated_jwts.reason IS
  'Reason the JWT was invalidated (logout, admin revoke, compromise, etc.)';
COMMENT ON COLUMN invalidated_jwts.expiry_timestamp IS
  'Original expiration timestamp of the JWT';
COMMENT ON COLUMN invalidated_jwts.created_at IS
  'Timestamp when the token was invalidated';

-- ============================================================================
-- REFRESH TOKENS
-- ============================================================================

CREATE TABLE refresh_tokens (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id               UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
  client_id             TEXT,
  token_hash            TEXT NOT NULL UNIQUE,
  family_id             UUID NOT NULL DEFAULT gen_random_uuid(),
  issued_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at            TIMESTAMPTZ NOT NULL,
  revoked_at            TIMESTAMPTZ,
  revoke_reason         TEXT,
  replaced_by_token_hash TEXT,
  rotated_at            TIMESTAMPTZ,
  issued_ip             INET,
  issued_user_agent     TEXT,
  last_used_at          TIMESTAMPTZ,
  created_at            TIMESTAMPTZ DEFAULT now()
);

COMMENT ON TABLE refresh_tokens IS
  'Hashed refresh tokens for session continuation. Tokens are rotated on use and can be revoked';
COMMENT ON COLUMN refresh_tokens.id IS
  'Internal refresh token identifier (UUID)';
COMMENT ON COLUMN refresh_tokens.user_id IS
  'References users.user_id. ON DELETE CASCADE removes refresh tokens when a user is deleted';
COMMENT ON COLUMN refresh_tokens.client_id IS
  'Optional client/application identifier that requested the token';
COMMENT ON COLUMN refresh_tokens.token_hash IS
  'Hash of the refresh token (never store raw token value)';
COMMENT ON COLUMN refresh_tokens.family_id IS
  'Groups refresh tokens issued from the same login session to support rotation and reuse detection';
COMMENT ON COLUMN refresh_tokens.issued_at IS
  'Timestamp when the refresh token was issued';
COMMENT ON COLUMN refresh_tokens.expires_at IS
  'Timestamp after which the refresh token is no longer valid';
COMMENT ON COLUMN refresh_tokens.revoked_at IS
  'Timestamp when the refresh token was revoked';
COMMENT ON COLUMN refresh_tokens.revoke_reason IS
  'Reason the refresh token was revoked';
COMMENT ON COLUMN refresh_tokens.replaced_by_token_hash IS
  'Hash of the replacement refresh token after rotation';
COMMENT ON COLUMN refresh_tokens.rotated_at IS
  'Timestamp when the refresh token was rotated';
COMMENT ON COLUMN refresh_tokens.issued_ip IS
  'IP address from which the refresh token was issued';
COMMENT ON COLUMN refresh_tokens.issued_user_agent IS
  'User agent associated with refresh token issuance';
COMMENT ON COLUMN refresh_tokens.last_used_at IS
  'Timestamp when the refresh token was last successfully used';
COMMENT ON COLUMN refresh_tokens.created_at IS
  'Timestamp when the refresh token record was created';

-- ============================================================================
-- TRUSTED CLIENTS
-- ============================================================================

CREATE TABLE trusted_clients (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  common_name TEXT NOT NULL,
  subject_dn  TEXT NOT NULL UNIQUE,
  fingerprint TEXT NOT NULL UNIQUE,
  description TEXT,
  issued_by   TEXT,
  issued_at   TIMESTAMPTZ DEFAULT now(),
  expires_at  TIMESTAMPTZ,
  revoked_at  TIMESTAMPTZ,
  revoke_reason TEXT,
  created_at  TIMESTAMPTZ DEFAULT now(),
  updated_at  TIMESTAMPTZ DEFAULT now()
);

COMMENT ON TABLE trusted_clients IS
  'Certificate-authenticated machine-to-machine clients';
COMMENT ON COLUMN trusted_clients.id IS
  'Unique identifier for the trusted client (UUID)';
COMMENT ON COLUMN trusted_clients.common_name IS
  'Certificate common name (CN)';
COMMENT ON COLUMN trusted_clients.subject_dn IS
  'Full subject distinguished name from the client certificate';
COMMENT ON COLUMN trusted_clients.fingerprint IS
  'Unique certificate fingerprint';
COMMENT ON COLUMN trusted_clients.description IS
  'Human-readable description of the client';
COMMENT ON COLUMN trusted_clients.issued_by IS
  'Certificate issuing authority';
COMMENT ON COLUMN trusted_clients.issued_at IS
  'Timestamp when the certificate was issued';
COMMENT ON COLUMN trusted_clients.expires_at IS
  'Certificate expiration timestamp';
COMMENT ON COLUMN trusted_clients.revoked_at IS
  'Timestamp when the client certificate was revoked; NULL if not revoked';
COMMENT ON COLUMN trusted_clients.revoke_reason IS
  'Reason the client certificate was revoked';
COMMENT ON COLUMN trusted_clients.created_at IS
  'Timestamp when the client record was created';
COMMENT ON COLUMN trusted_clients.updated_at IS
  'Timestamp when the client record was last updated';

-- ============================================================================
-- AUDIT LOGS
-- ============================================================================

CREATE TABLE audit_logs (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  actor_id    UUID,
  actor_type  VARCHAR(20) NOT NULL CHECK (actor_type IN ('USER', 'SERVICE', 'CERT', 'SYSTEM', 'MIGRATION')),
  action      VARCHAR(50) NOT NULL,
  target_type VARCHAR(50),
  target_id   UUID,
  metadata    JSONB,
  created_at  TIMESTAMPTZ DEFAULT now(),
  CONSTRAINT chk_audit_action CHECK (
    action IN (
      'USER_CREATED',
      'USER_UPDATED',
      'USER_DISABLED',
      'USER_PASSWORD_ROTATED',
      'USER_ROLE_ASSIGNED',
      'USER_ROLE_REMOVED',
      'TOKEN_ISSUED',
      'TOKEN_INVALIDATED',
      'REFRESH_TOKEN_ISSUED',
      'REFRESH_TOKEN_ROTATED',
      'REFRESH_TOKEN_REVOKED',
      'TRUSTED_CLIENT_CREATED',
      'TRUSTED_CLIENT_REVOKED',
      'ROLE_PERMISSION_ASSIGNED',
      'ROLE_PERMISSION_REMOVED'
    )
  )
);

COMMENT ON TABLE audit_logs IS
  'Security audit trail for identity, token, and trust lifecycle events';
COMMENT ON COLUMN audit_logs.id IS
  'Audit record identifier (UUID)';
COMMENT ON COLUMN audit_logs.actor_id IS
  'Identifier of the actor performing the action';
COMMENT ON COLUMN audit_logs.actor_type IS
  'Actor classification (USER, SERVICE, CERT, SYSTEM, MIGRATION)';
COMMENT ON COLUMN audit_logs.action IS
  'Normalized audit action name (controlled vocabulary)';
COMMENT ON COLUMN audit_logs.target_type IS
  'Type of entity acted upon';
COMMENT ON COLUMN audit_logs.target_id IS
  'Identifier of the target entity';
COMMENT ON COLUMN audit_logs.metadata IS
  'Structured metadata associated with the audit event';
COMMENT ON COLUMN audit_logs.created_at IS
  'Timestamp when the audited action occurred';

-- ============================================================================
-- INDEXES
-- ============================================================================

CREATE INDEX idx_users_username ON users(username);

CREATE UNIQUE INDEX idx_token_hash ON invalidated_jwts(token_hash);
CREATE INDEX idx_jti ON invalidated_jwts(jti);
CREATE INDEX idx_expiry_timestamp ON invalidated_jwts(expiry_timestamp);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens(family_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_active ON refresh_tokens(user_id) WHERE revoked_at IS NULL;

CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);

CREATE INDEX idx_trusted_clients_revoked ON trusted_clients(revoked_at);

CREATE INDEX idx_audit_logs_actor ON audit_logs(actor_id);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at DESC);
CREATE INDEX idx_audit_logs_action_actor_time
  ON audit_logs (action, actor_id, created_at DESC);

-- ============================================================================
-- UPDATED_AT TRIGGERS
-- ============================================================================

CREATE OR REPLACE FUNCTION set_updated_at_timestamp()
  RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_set_users_updated_at
  BEFORE UPDATE ON users
  FOR EACH ROW
  EXECUTE FUNCTION set_updated_at_timestamp();

CREATE TRIGGER trg_set_trusted_clients_updated_at
  BEFORE UPDATE ON trusted_clients
  FOR EACH ROW
  EXECUTE FUNCTION set_updated_at_timestamp();

-- ============================================================================
-- AUDIT TRIGGER FUNCTIONS
-- ============================================================================

CREATE OR REPLACE FUNCTION audit_users()
  RETURNS TRIGGER AS $$
DECLARE
  target_record RECORD;
BEGIN
  -- Use NEW for INSERT/UPDATE, OLD for DELETE
  target_record := CASE TG_OP WHEN 'DELETE' THEN OLD ELSE NEW END;
  
  INSERT INTO audit_logs (
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
  
  -- Return appropriate record for trigger
  RETURN CASE TG_OP WHEN 'DELETE' THEN OLD ELSE NEW END;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION audit_invalidated_jwts()
  RETURNS TRIGGER AS $$
BEGIN
  INSERT INTO audit_logs (
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
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION audit_refresh_tokens()
  RETURNS TRIGGER AS $$
DECLARE
  target_record RECORD;
BEGIN
  -- Use NEW for INSERT/UPDATE, OLD for DELETE
  target_record := CASE TG_OP WHEN 'DELETE' THEN OLD ELSE NEW END;
  
  INSERT INTO audit_logs (
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
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION audit_trusted_clients()
  RETURNS TRIGGER AS $$
DECLARE
  target_record RECORD;
BEGIN
  -- Use NEW for INSERT/UPDATE, OLD for DELETE
  target_record := CASE TG_OP WHEN 'DELETE' THEN OLD ELSE NEW END;
  
  INSERT INTO audit_logs (
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
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION audit_role_permissions()
  RETURNS TRIGGER AS $$
DECLARE
  target_record RECORD;
  composite_id UUID;
BEGIN
  -- Use NEW for INSERT, OLD for DELETE
  target_record := CASE TG_OP WHEN 'DELETE' THEN OLD ELSE NEW END;
  -- Create deterministic UUID from composite key for audit trail
  composite_id := md5(target_record.role_id::text || ':' || target_record.permission_id::text)::uuid;
  
  INSERT INTO audit_logs (
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
    to_jsonb(target_record)
  );
  
  -- Return appropriate record for trigger
  RETURN CASE TG_OP WHEN 'DELETE' THEN OLD ELSE NEW END;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION audit_user_roles()
  RETURNS TRIGGER AS $$
DECLARE
  target_record RECORD;
  composite_id UUID;
BEGIN
  -- Use NEW for INSERT, OLD for DELETE
  target_record := CASE TG_OP WHEN 'DELETE' THEN OLD ELSE NEW END;
  -- Create deterministic UUID from composite key for audit trail
  composite_id := md5(target_record.user_id::text || ':' || target_record.role_id::text)::uuid;
  
  INSERT INTO audit_logs (
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
    to_jsonb(target_record)
  );
  
  -- Return appropriate record for trigger
  RETURN CASE TG_OP WHEN 'DELETE' THEN OLD ELSE NEW END;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- AUDIT TRIGGERS
-- ============================================================================

CREATE TRIGGER trg_audit_users
  AFTER INSERT OR UPDATE OR DELETE ON users
  FOR EACH ROW
  EXECUTE FUNCTION audit_users();

CREATE TRIGGER trg_audit_invalidated_jwts
  AFTER INSERT ON invalidated_jwts
  FOR EACH ROW
  EXECUTE FUNCTION audit_invalidated_jwts();

CREATE TRIGGER trg_audit_refresh_tokens
  AFTER INSERT OR UPDATE OR DELETE ON refresh_tokens
  FOR EACH ROW
  EXECUTE FUNCTION audit_refresh_tokens();

CREATE TRIGGER trg_audit_trusted_clients
  AFTER INSERT OR UPDATE OR DELETE ON trusted_clients
  FOR EACH ROW
  EXECUTE FUNCTION audit_trusted_clients();

CREATE TRIGGER trg_audit_role_permissions
  AFTER INSERT OR DELETE ON role_permissions
  FOR EACH ROW
  EXECUTE FUNCTION audit_role_permissions();

CREATE TRIGGER trg_audit_user_roles
  AFTER INSERT OR DELETE ON user_roles
  FOR EACH ROW
  EXECUTE FUNCTION audit_user_roles();
-- ============================================================================
-- AUTH FUNCTIONS
-- ============================================================================

CREATE OR REPLACE FUNCTION auth.find_user_credentials(p_username text)
RETURNS TABLE (
  user_id uuid,
  username text,
  password_hash text,
  password_reset_required_at timestamptz
)
-- LANGUAGE sql: Function body is written in SQL (not plpgsql, python, etc.)
LANGUAGE sql
-- STABLE: Function is deterministic (same input = same output) and doesn't modify data
--   PostgreSQL can optimize queries by caching results within a transaction
STABLE
-- AS $$...$$ : Delimiter syntax for function body. $$ avoids quote escaping issues
--   (alternative to single quotes which require doubling internal quotes)
AS $$
  SELECT
    u.user_id,
    u.username,
    u.password_hash,
    u.password_reset_required_at
  FROM public.users u
  WHERE u.username = p_username;
$$;

COMMENT ON FUNCTION auth.find_user_credentials(text) IS
  'Retrieves user credentials for password-based authentication. Used by the auth service to fetch password hash for verification.';

ALTER FUNCTION auth.find_user_credentials(text)
SET search_path = public, pg_temp;

REVOKE ALL ON FUNCTION auth.find_user_credentials(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION auth.find_user_credentials(text) TO app_user;

-- ============================================================================
-- POST-AUTHENTICATION USER FUNCTION
-- ============================================================================

CREATE OR REPLACE FUNCTION auth.get_user(p_user_id uuid)
RETURNS TABLE (
  user_id uuid,
  username text,
  permissions text[]
)
-- LANGUAGE sql: Function body is written in SQL
LANGUAGE sql
-- STABLE: Deterministic function, safe to cache within transaction
STABLE
-- AS $$...$$ : Delimiter syntax for function body
AS $$
  SELECT
    u.user_id,
    u.username,
    COALESCE(array_agg(DISTINCT p.name ORDER BY p.name), ARRAY[]::text[]) AS permissions
  FROM public.users u
  LEFT JOIN public.user_roles ur ON u.user_id = ur.user_id
  LEFT JOIN public.roles r ON ur.role_id = r.role_id
  LEFT JOIN public.role_permissions rp ON r.role_id = rp.role_id
  LEFT JOIN public.permissions p ON rp.permission_id = p.permission_id
  WHERE u.user_id = p_user_id
  GROUP BY u.user_id, u.username;
$$;

COMMENT ON FUNCTION auth.get_user(uuid) IS
  'Retrieves authenticated user with permissions for authorization. Returns user_id, username, and aggregated permissions array. Used after successful authentication to load user data for the User domain entity.';

ALTER FUNCTION auth.get_user(uuid)
SET search_path = public, pg_temp;

REVOKE ALL ON FUNCTION auth.get_user(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION auth.get_user(uuid) TO app_user;