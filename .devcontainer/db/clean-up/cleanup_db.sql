-- @formatter:off
-- noinspection SqlUnresolvedReference
-- Flyway migration: cleanup_db.sql
-- Clean up existing objects from previous migrations or failed runs
--
-- PURPOSE:
--  This migration runs FIRST (V0.9.9) before any other migrations.
--  It safely removes all existing objects to ensure a clean state.
--  Uses IF EXISTS clauses so it's safe even on fresh databases.
--
-- ⚠️  PRODUCTION SAFETY: Uses RESTRICT (Safe Default)
--
--  This migration uses RESTRICT for all schema drops:
--    DROP SCHEMA IF EXISTS private_schema RESTRICT;
--    DROP SCHEMA IF EXISTS api_schema RESTRICT;
--
--  Why RESTRICT:
--    ✅ Fails if schema contains objects
--    ✅ Prevents accidental data loss
--    ✅ Forces intentional cleanup before dropping
--    ✅ Safe-by-default approach (fail-closed, not fail-open)
--
--  When RESTRICT fails (expected behavior):
--    ERROR: cannot drop schema private_schema because other objects depend on it
--
-- ============================================================================
-- DROP TRIGGERS
-- ============================================================================

DROP TRIGGER IF EXISTS trg_audit_users ON private_schema.users;
DROP TRIGGER IF EXISTS trg_audit_invalidated_jwts ON private_schema.invalidated_jwts;
DROP TRIGGER IF EXISTS trg_audit_refresh_tokens ON private_schema.refresh_tokens;
DROP TRIGGER IF EXISTS trg_audit_trusted_clients ON private_schema.trusted_clients;
DROP TRIGGER IF EXISTS trg_audit_role_permissions ON private_schema.role_permissions;
DROP TRIGGER IF EXISTS trg_audit_user_roles ON private_schema.user_roles;
DROP TRIGGER IF EXISTS trg_audit_totp_enabled ON private_schema.totp_secrets;
DROP TRIGGER IF EXISTS trg_audit_totp_last_used ON private_schema.totp_secrets;
DROP TRIGGER IF EXISTS trg_audit_totp_disabled ON private_schema.totp_secrets;
DROP TRIGGER IF EXISTS trg_audit_backup_codes_generated ON private_schema.totp_secrets;
DROP TRIGGER IF EXISTS trg_set_trusted_clients_updated_at ON private_schema.trusted_clients;
DROP TRIGGER IF EXISTS trg_set_users_updated_at ON private_schema.users;

-- ============================================================================
-- DROP TRIGGER FUNCTIONS
-- ============================================================================

DROP FUNCTION IF EXISTS private_schema.audit_users();
DROP FUNCTION IF EXISTS private_schema.audit_invalidated_jwts();
DROP FUNCTION IF EXISTS private_schema.audit_refresh_tokens();
DROP FUNCTION IF EXISTS private_schema.audit_trusted_clients();
DROP FUNCTION IF EXISTS private_schema.audit_role_permissions();
DROP FUNCTION IF EXISTS private_schema.audit_user_roles();
DROP FUNCTION IF EXISTS private_schema.audit_totp_enabled();
DROP FUNCTION IF EXISTS private_schema.audit_totp_last_used();
DROP FUNCTION IF EXISTS private_schema.audit_totp_disabled();
DROP FUNCTION IF EXISTS private_schema.audit_backup_codes_generated();
DROP FUNCTION IF EXISTS private_schema.set_updated_at_timestamp();

-- ============================================================================
-- DROP API FUNCTIONS
-- ============================================================================

DROP FUNCTION IF EXISTS api_schema.find_user_credentials(text);
DROP FUNCTION IF EXISTS api_schema.get_user(uuid);
DROP FUNCTION IF EXISTS api_schema.get_totp_status(uuid);
DROP FUNCTION IF EXISTS api_schema.encrypt_totp_secret(text, text);


-- ============================================================================
-- DROP INDEXES
-- ============================================================================

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
DROP INDEX IF EXISTS idx_totp_secrets_user_id;
DROP INDEX IF EXISTS idx_totp_secrets_active;
DROP INDEX IF EXISTS idx_backup_codes_user_id;
DROP INDEX IF EXISTS idx_backup_codes_used;
DROP INDEX IF EXISTS idx_backup_codes_generation_batch;

-- ============================================================================
-- DROP TABLES
-- ============================================================================

DROP TABLE IF EXISTS backup_codes;
DROP TABLE IF EXISTS totp_secrets;
DROP TABLE IF EXISTS refresh_tokens;
DROP TABLE IF EXISTS user_roles;
DROP TABLE IF EXISTS role_permissions;
DROP TABLE IF EXISTS invalidated_jwts;
DROP TABLE IF EXISTS permissions;
DROP TABLE IF EXISTS roles;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS trusted_clients;
DROP TABLE IF EXISTS audit_logs;

-- ============================================================================
-- DROP SCHEMAS (using RESTRICT - production-safe default)
-- ============================================================================

DROP SCHEMA IF EXISTS private_schema RESTRICT;
DROP SCHEMA IF EXISTS api_schema RESTRICT;

