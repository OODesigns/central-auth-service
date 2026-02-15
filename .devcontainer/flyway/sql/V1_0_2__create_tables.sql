-- Flyway migration: V1_0_2__create_tables.sql
-- Create all data tables for Central Auth Service (CAS)
--
-- TABLES CREATED (11 total):
--  - users: User accounts and credentials
--  - roles: Role definitions
--  - permissions: Permission definitions
--  - user_roles: User-to-role mappings
--  - role_permissions: Role-to-permission mappings
--  - invalidated_jwts: Revoked access tokens
--  - refresh_tokens: Session refresh tokens
--  - trusted_clients: Certificate-based clients
--  - totp_secrets: 2FA TOTP secrets
--  - backup_codes: 2FA backup codes
--  - audit_logs: Security audit trail
--
-- DEPENDENCIES: Schemas (V1_0_0), Roles (V1_0_1)
-- ============================================================================

CREATE TABLE private_schema.users (
  user_id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  username                   VARCHAR(50) UNIQUE NOT NULL,
  password_hash              VARCHAR(255) NOT NULL,
  password_reset_required_at TIMESTAMPTZ DEFAULT now(),
  mfa_required_at            TIMESTAMPTZ,
  created_at                 TIMESTAMPTZ DEFAULT now(),
  updated_at                 TIMESTAMPTZ DEFAULT now()
);

COMMENT ON TABLE private_schema.users IS
  'Application users and authentication credentials';
COMMENT ON COLUMN private_schema.users.user_id IS
  'Unique user identifier (UUID primary key)';
COMMENT ON COLUMN private_schema.users.username IS
  'Unique login name';
COMMENT ON COLUMN private_schema.users.password_hash IS
  'Hashed user password (never store plaintext)';
COMMENT ON COLUMN private_schema.users.password_reset_required_at IS
  'Timestamp when password reset was required; NULL if password reset is not required';
COMMENT ON COLUMN private_schema.users.mfa_required_at IS
  'Timestamp when 2FA became mandatory for this user. Used to enforce role/org-level 2FA policies. ' ||
  'If NOT NULL and totp_secrets.verified_at IS NULL, user is BLOCKED until 2FA is enrolled. Allows: ' ||
  '"User hasn''t enrolled yet" (NULL), "User is blocked until they enroll" (NOT NULL), "User has enrolled" (verified_at NOT NULL). ' ||
  'Enforcement logic: if mfa_required_at IS NOT NULL AND totp_secrets.verified_at IS NULL, reject login with "MFA_REQUIRED_SETUP".';
COMMENT ON COLUMN private_schema.users.created_at IS
  'Timestamp when the user record was created';
COMMENT ON COLUMN private_schema.users.updated_at IS
  'Timestamp of last update (auto-managed by trigger)';

-- ============================================================================
-- ROLES TABLE
-- ============================================================================

CREATE TABLE private_schema.roles (
  role_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name        VARCHAR(50) UNIQUE NOT NULL,
  description TEXT
);

COMMENT ON TABLE private_schema.roles IS
  'Role definitions. Values are static and seeded separately';
COMMENT ON COLUMN private_schema.roles.role_id IS
  'Unique role identifier (UUID primary key)';
COMMENT ON COLUMN private_schema.roles.name IS
  'Unique role name (e.g. admin, user, kiosk)';
COMMENT ON COLUMN private_schema.roles.description IS
  'Human-readable role description';

-- ============================================================================
-- USER_ROLES TABLE
-- ============================================================================

CREATE TABLE private_schema.user_roles (
  user_id UUID NOT NULL,
  role_id UUID NOT NULL,
  created_at TIMESTAMPTZ DEFAULT now(),
  PRIMARY KEY (user_id, role_id),
  FOREIGN KEY (user_id) REFERENCES private_schema.users(user_id) ON DELETE CASCADE,
  FOREIGN KEY (role_id) REFERENCES private_schema.roles(role_id) ON DELETE CASCADE
);

COMMENT ON TABLE private_schema.user_roles IS
  'Join table mapping users to roles (many-to-many)';
COMMENT ON COLUMN private_schema.user_roles.user_id IS
  'References users.user_id. ON DELETE CASCADE removes all role assignments when a user is deleted';
COMMENT ON COLUMN private_schema.user_roles.role_id IS
  'References roles.role_id. ON DELETE CASCADE removes mappings when a role is deleted';
COMMENT ON COLUMN private_schema.user_roles.created_at IS
  'Timestamp when the role assignment was created';

-- ============================================================================
-- PERMISSIONS TABLE
-- ============================================================================

CREATE TABLE private_schema.permissions (
  permission_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name          VARCHAR(100) UNIQUE NOT NULL
);

COMMENT ON TABLE private_schema.permissions IS
  'Permission definitions. Values are static and seeded separately';
COMMENT ON COLUMN private_schema.permissions.permission_id IS
  'Unique permission identifier (UUID primary key)';
COMMENT ON COLUMN private_schema.permissions.name IS
  'Unique permission name (e.g. manage_users)';

-- ============================================================================
-- ROLE_PERMISSIONS TABLE
-- ============================================================================

CREATE TABLE private_schema.role_permissions (
  role_id       UUID NOT NULL,
  permission_id UUID NOT NULL,
  created_at    TIMESTAMPTZ DEFAULT now(),
  PRIMARY KEY (role_id, permission_id),
  FOREIGN KEY (role_id) REFERENCES private_schema.roles(role_id) ON DELETE CASCADE,
  FOREIGN KEY (permission_id) REFERENCES private_schema.permissions(permission_id) ON DELETE CASCADE
);

COMMENT ON TABLE private_schema.role_permissions IS
  'Join table mapping roles to permissions (many-to-many)';
COMMENT ON COLUMN private_schema.role_permissions.role_id IS
  'References roles.role_id. ON DELETE CASCADE removes permission mappings when a role is deleted';
COMMENT ON COLUMN private_schema.role_permissions.permission_id IS
  'References permissions.permission_id. ON DELETE CASCADE removes mappings when a permission is deleted';
COMMENT ON COLUMN private_schema.role_permissions.created_at IS
  'Timestamp when the permission was assigned to the role';

-- ============================================================================
-- INVALIDATED JWTs TABLE
-- ============================================================================

CREATE TABLE private_schema.invalidated_jwts (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  jti              VARCHAR(255),
  token_hash       VARCHAR(255) NOT NULL,
  reason           TEXT,
  expiry_timestamp TIMESTAMPTZ NOT NULL,
  created_at       TIMESTAMPTZ DEFAULT now()
);

COMMENT ON TABLE private_schema.invalidated_jwts IS
  'Revoked or invalidated access JWTs. Access tokens themselves are never stored';
COMMENT ON COLUMN private_schema.invalidated_jwts.id IS
  'Unique identifier for the invalidated token entry (UUID primary key)';
COMMENT ON COLUMN private_schema.invalidated_jwts.jti IS
  'JWT ID (jti claim) if present';
COMMENT ON COLUMN private_schema.invalidated_jwts.token_hash IS
  'Hash of the invalidated JWT';
COMMENT ON COLUMN private_schema.invalidated_jwts.reason IS
  'Reason the JWT was invalidated (logout, admin revoke, compromise, etc.)';
COMMENT ON COLUMN private_schema.invalidated_jwts.expiry_timestamp IS
  'Original expiration timestamp of the JWT';
COMMENT ON COLUMN private_schema.invalidated_jwts.created_at IS
  'Timestamp when the token was invalidated';

-- ============================================================================
-- REFRESH TOKENS TABLE
-- ============================================================================

CREATE TABLE private_schema.refresh_tokens (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id               UUID NOT NULL REFERENCES private_schema.users(user_id) ON DELETE CASCADE,
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

COMMENT ON TABLE private_schema.refresh_tokens IS
  'Hashed refresh tokens for session continuation. Tokens are rotated on use and can be revoked';
COMMENT ON COLUMN private_schema.refresh_tokens.id IS
  'Internal refresh token identifier (UUID)';
COMMENT ON COLUMN private_schema.refresh_tokens.user_id IS
  'References users.user_id. ON DELETE CASCADE removes refresh tokens when a user is deleted';
COMMENT ON COLUMN private_schema.refresh_tokens.client_id IS
  'Optional client/application identifier that requested the token';
COMMENT ON COLUMN private_schema.refresh_tokens.token_hash IS
  'Hash of the refresh token (never store raw token value)';
COMMENT ON COLUMN private_schema.refresh_tokens.family_id IS
  'Groups refresh tokens issued from the same login session to support rotation and reuse detection';
COMMENT ON COLUMN private_schema.refresh_tokens.issued_at IS
  'Timestamp when the refresh token was issued';
COMMENT ON COLUMN private_schema.refresh_tokens.expires_at IS
  'Timestamp after which the refresh token is no longer valid';
COMMENT ON COLUMN private_schema.refresh_tokens.revoked_at IS
  'Timestamp when the refresh token was revoked';
COMMENT ON COLUMN private_schema.refresh_tokens.revoke_reason IS
  'Reason the refresh token was revoked';
COMMENT ON COLUMN private_schema.refresh_tokens.replaced_by_token_hash IS
  'Hash of the replacement refresh token after rotation';
COMMENT ON COLUMN private_schema.refresh_tokens.rotated_at IS
  'Timestamp when the refresh token was rotated';
COMMENT ON COLUMN private_schema.refresh_tokens.issued_ip IS
  'IP address from which the refresh token was issued';
COMMENT ON COLUMN private_schema.refresh_tokens.issued_user_agent IS
  'User agent associated with refresh token issuance';
COMMENT ON COLUMN private_schema.refresh_tokens.last_used_at IS
  'Timestamp when the refresh token was last successfully used';
COMMENT ON COLUMN private_schema.refresh_tokens.created_at IS
  'Timestamp when the refresh token record was created';

-- ============================================================================
-- TRUSTED CLIENTS TABLE
-- ============================================================================

CREATE TABLE private_schema.trusted_clients (
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

COMMENT ON TABLE private_schema.trusted_clients IS
  'Certificate-authenticated machine-to-machine clients';
COMMENT ON COLUMN private_schema.trusted_clients.id IS
  'Unique identifier for the trusted client (UUID)';
COMMENT ON COLUMN private_schema.trusted_clients.common_name IS
  'Certificate common name (CN)';
COMMENT ON COLUMN private_schema.trusted_clients.subject_dn IS
  'Full subject distinguished name from the client certificate';
COMMENT ON COLUMN private_schema.trusted_clients.fingerprint IS
  'Unique certificate fingerprint';
COMMENT ON COLUMN private_schema.trusted_clients.description IS
  'Human-readable description of the client';
COMMENT ON COLUMN private_schema.trusted_clients.issued_by IS
  'Certificate issuing authority';
COMMENT ON COLUMN private_schema.trusted_clients.issued_at IS
  'Timestamp when the certificate was issued';
COMMENT ON COLUMN private_schema.trusted_clients.expires_at IS
  'Certificate expiration timestamp';
COMMENT ON COLUMN private_schema.trusted_clients.revoked_at IS
  'Timestamp when the client certificate was revoked; NULL if not revoked';
COMMENT ON COLUMN private_schema.trusted_clients.revoke_reason IS
  'Reason the client certificate was revoked';
COMMENT ON COLUMN private_schema.trusted_clients.created_at IS
  'Timestamp when the client record was created';
COMMENT ON COLUMN private_schema.trusted_clients.updated_at IS
  'Timestamp when the client record was last updated';

-- ============================================================================
-- TOTP SECRETS (2FA)
-- ============================================================================

CREATE TABLE private_schema.totp_secrets (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id               UUID NOT NULL UNIQUE REFERENCES private_schema.users(user_id) ON DELETE CASCADE,
  secret_key_encrypted  BYTEA NOT NULL,
  algorithm             VARCHAR(10) NOT NULL DEFAULT 'SHA1' CHECK (algorithm IN ('SHA1', 'SHA256', 'SHA512')),
  period_seconds        INTEGER NOT NULL DEFAULT 30 CHECK (period_seconds IN (30, 60)),
  digits                INTEGER NOT NULL DEFAULT 6 CHECK (digits >= 6 AND digits <= 8),
  verified_at           TIMESTAMPTZ,
  last_used_at          TIMESTAMPTZ,
  backup_codes_generated_at TIMESTAMPTZ,
  created_at            TIMESTAMPTZ DEFAULT now(),
  updated_at            TIMESTAMPTZ DEFAULT now()
);

COMMENT ON TABLE private_schema.totp_secrets IS
  'Time-based One-Time Password (TOTP) secrets for authenticator app-based 2FA. Secret key is encrypted at rest using AES-CBC (semantic security, random IVs - NOT ECB). 2FA status determined by verified_at: NULL = disabled, NOT NULL = enabled. Encryption uses external master key for key separation principle.';
COMMENT ON COLUMN private_schema.totp_secrets.id IS
  'Unique identifier for the TOTP secret record (UUID)';
COMMENT ON COLUMN private_schema.totp_secrets.user_id IS
  'References users.user_id. UNIQUE ensures one TOTP secret per user. ON DELETE CASCADE removes secret when user is deleted';
COMMENT ON COLUMN private_schema.totp_secrets.secret_key_encrypted IS
  'ENCRYPTED Base32-encoded TOTP secret using AES-CBC encryption with PKCS7 padding via pgcrypto (NOT ECB - semantic security required). Never stored in plaintext - encryption key must be a 256-bit server-side master key stored separately, NOT in database. Each encryption generates a random IV (included in ciphertext). Decrypted only during TOTP verification, then immediately discarded. If database is compromised, plaintext secrets remain cryptographically protected.';
COMMENT ON COLUMN private_schema.totp_secrets.algorithm IS
  'HMAC algorithm used for TOTP generation. Allowed: SHA1, SHA256, SHA512. Default is SHA1 for compatibility with most authenticator apps. Enforced by CHECK constraint to prevent invalid algorithms.';
COMMENT ON COLUMN private_schema.totp_secrets.period_seconds IS
  'Time window in seconds for TOTP validity. Allowed: 30 (default, standard), 60 (less common). Enforced by CHECK constraint.';
COMMENT ON COLUMN private_schema.totp_secrets.digits IS
  'Number of digits in generated OTP. Allowed: 6-8 digits. Default 6 (standard). Enforced by CHECK constraint.';
COMMENT ON COLUMN private_schema.totp_secrets.verified_at IS
  'Single source of truth for 2FA status. NULL = 2FA disabled (secret created but not verified). NOT NULL = 2FA enabled (timestamp when user verified the secret during setup).';
COMMENT ON COLUMN private_schema.totp_secrets.last_used_at IS
  'Timestamp when TOTP was last successfully used for authentication. NULL until first use. Useful for security analytics: detect dormant 2FA, investigate account activity, validate "was 2FA actually used?" scenarios.';
COMMENT ON COLUMN private_schema.totp_secrets.backup_codes_generated_at IS
  'Timestamp when backup codes were last generated for account recovery. NULL if not yet generated. When new codes are generated, old codes should be invalidated (see backup_codes.generation_batch_id).';
COMMENT ON COLUMN private_schema.totp_secrets.created_at IS
  'Timestamp when the TOTP secret was created';
COMMENT ON COLUMN private_schema.totp_secrets.updated_at IS
  'Timestamp of last update (auto-managed by trigger)';

-- ============================================================================
-- BACKUP CODES (TOTP Account Recovery)
-- ============================================================================

CREATE TABLE private_schema.backup_codes (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id               UUID NOT NULL REFERENCES private_schema.users(user_id) ON DELETE CASCADE,
  generation_batch_id   UUID NOT NULL,
  code_hash             TEXT NOT NULL,
  used_at               TIMESTAMPTZ,
  created_at            TIMESTAMPTZ DEFAULT now()
);

COMMENT ON TABLE private_schema.backup_codes IS
  'Single-use backup codes for account recovery when authenticator device is lost. Generated and stored hashed. Supports batch invalidation when new codes are generated via generation_batch_id.';
COMMENT ON COLUMN private_schema.backup_codes.id IS
  'Unique identifier for the backup code record (UUID)';
COMMENT ON COLUMN private_schema.backup_codes.user_id IS
  'References users.user_id. ON DELETE CASCADE removes backup codes when user is deleted';
COMMENT ON COLUMN private_schema.backup_codes.generation_batch_id IS
  'Groups codes by generation batch (UUID). Allows invalidating old codes when new ones are generated. All codes in a batch are created at the same time (see totp_secrets.backup_codes_generated_at). App can invalidate old batches: DELETE FROM backup_codes WHERE user_id = ? AND generation_batch_id != current_batch_id.';
COMMENT ON COLUMN private_schema.backup_codes.code_hash IS
  'Hash of the backup code (never store plaintext). User receives plaintext codes only during setup. Hashed with bcrypt or similar (20+ rounds minimum). Cannot be recovered even if database is compromised.';
COMMENT ON COLUMN private_schema.backup_codes.used_at IS
  'Timestamp when the backup code was used for account recovery. NULL until first use. After use, code cannot be reused (enforced by app logic: reject if used_at IS NOT NULL).';
COMMENT ON COLUMN private_schema.backup_codes.created_at IS
  'Timestamp when the backup code was generated';

-- ============================================================================
-- AUDIT LOGS
-- ============================================================================

CREATE TABLE private_schema.audit_logs (
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
      'USER_MFA_REQUIRED',
      'USER_MFA_REQUIRED_REMOVED',
      'USER_ROLE_ASSIGNED',
      'USER_ROLE_REMOVED',
      'TOKEN_ISSUED',
      'TOKEN_INVALIDATED',
      'REFRESH_TOKEN_ISSUED',
      'REFRESH_TOKEN_ROTATED',
      'REFRESH_TOKEN_REVOKED',
      'TRUSTED_CLIENT_CREATED',
      'TRUSTED_CLIENT_UPDATED',
      'TRUSTED_CLIENT_REVOKED',
      'ROLE_PERMISSION_ASSIGNED',
      'ROLE_PERMISSION_REMOVED',
      'TOTP_ENABLED',
      'TOTP_DISABLED',
      'TOTP_LAST_USED',
      'BACKUP_CODES_GENERATED'
    )
  )
);

COMMENT ON TABLE private_schema.audit_logs IS
  'Security audit trail for identity, token, and trust lifecycle events';
COMMENT ON COLUMN private_schema.audit_logs.id IS
  'Audit record identifier (UUID)';
COMMENT ON COLUMN private_schema.audit_logs.actor_id IS
  'Identifier of the actor performing the action';
COMMENT ON COLUMN private_schema.audit_logs.actor_type IS
  'Actor classification (USER, SERVICE, CERT, SYSTEM, MIGRATION)';
COMMENT ON COLUMN private_schema.audit_logs.action IS
  'Normalized audit action name (controlled vocabulary)';
COMMENT ON COLUMN private_schema.audit_logs.target_type IS
  'Type of entity acted upon';
COMMENT ON COLUMN private_schema.audit_logs.target_id IS
  'Identifier of the target entity';
COMMENT ON COLUMN private_schema.audit_logs.metadata IS
  'Structured metadata associated with the audit event';
COMMENT ON COLUMN private_schema.audit_logs.created_at IS
  'Timestamp when the audited action occurred';

