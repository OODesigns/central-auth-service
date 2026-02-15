-- Flyway migration: V1_0_3__create_indexes.sql
-- Create performance indexes for Central Auth Service (CAS)
--
-- INDEXES CREATED (19 total):
--  - Username lookup
--  - Token lookups (invalidated_jwts)
--  - Refresh token lookups and filtering
--  - Role and permission lookups
--  - Trusted client lookups
--  - TOTP and backup code lookups
--  - Audit log lookups and filtering
--
-- DEPENDENCIES: Tables (V1_0_2)
-- PURPOSE: Query performance and data integrity

-- ============================================================================
-- USER INDEXES
-- ============================================================================

CREATE INDEX idx_users_username ON private_schema.users(username);

-- ============================================================================
-- INVALIDATED JWT INDEXES
-- ============================================================================

CREATE UNIQUE INDEX idx_token_hash ON private_schema.invalidated_jwts(token_hash);
CREATE INDEX idx_jti ON private_schema.invalidated_jwts(jti);
CREATE INDEX idx_expiry_timestamp ON private_schema.invalidated_jwts(expiry_timestamp);

-- ============================================================================
-- REFRESH TOKEN INDEXES
-- ============================================================================

CREATE INDEX idx_refresh_tokens_user_id ON private_schema.refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_family_id ON private_schema.refresh_tokens(family_id);
CREATE INDEX idx_refresh_tokens_expires_at ON private_schema.refresh_tokens(expires_at);
CREATE INDEX idx_refresh_tokens_token_hash ON private_schema.refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_active ON private_schema.refresh_tokens(user_id) WHERE revoked_at IS NULL;

-- ============================================================================
-- USER ROLES INDEXES
-- ============================================================================

CREATE INDEX idx_user_roles_user_id ON private_schema.user_roles(user_id);
CREATE INDEX idx_user_roles_role_id ON private_schema.user_roles(role_id);

-- ============================================================================
-- TRUSTED CLIENTS INDEXES
-- ============================================================================

CREATE INDEX idx_trusted_clients_revoked ON private_schema.trusted_clients(revoked_at);

-- ============================================================================
-- TOTP SECRETS INDEXES
-- ============================================================================

CREATE INDEX idx_totp_secrets_user_id ON private_schema.totp_secrets(user_id);
CREATE INDEX idx_totp_secrets_active ON private_schema.totp_secrets(user_id) WHERE verified_at IS NOT NULL;

-- ============================================================================
-- BACKUP CODES INDEXES
-- ============================================================================

CREATE INDEX idx_backup_codes_user_id ON private_schema.backup_codes(user_id);
CREATE INDEX idx_backup_codes_used ON private_schema.backup_codes(used_at);
CREATE INDEX idx_backup_codes_generation_batch ON private_schema.backup_codes(generation_batch_id);
CREATE UNIQUE INDEX idx_backup_codes_unused ON private_schema.backup_codes(user_id, code_hash) WHERE used_at IS NULL;

-- ============================================================================
-- AUDIT LOGS INDEXES
-- ============================================================================

CREATE INDEX idx_audit_logs_actor ON private_schema.audit_logs(actor_id);
CREATE INDEX idx_audit_logs_action ON private_schema.audit_logs(action);
CREATE INDEX idx_audit_logs_created_at ON private_schema.audit_logs(created_at DESC);
CREATE INDEX idx_audit_logs_action_actor_time
  ON private_schema.audit_logs (action, actor_id, created_at DESC);

