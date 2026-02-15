-- Flyway migration: V1_0_5__create_triggers.sql
-- Attach triggers to tables for Central Auth Service (CAS)
--
-- TRIGGERS CREATED (12 total):
--  - trg_set_users_updated_at: Auto-update users.updated_at
--  - trg_set_trusted_clients_updated_at: Auto-update trusted_clients.updated_at
--  - trg_audit_users: Log user lifecycle and MFA policy events
--  - trg_audit_invalidated_jwts: Log token invalidations
--  - trg_audit_refresh_tokens: Log refresh token lifecycle
--  - trg_audit_trusted_clients: Log client changes
--  - trg_audit_role_permissions: Log permission changes
--  - trg_audit_user_roles: Log role assignments
--  - trg_audit_totp_enabled: Log 2FA enablement
--  - trg_audit_totp_disabled: Log 2FA disablement
--  - trg_audit_totp_last_used: Log 2FA usage
--  - trg_audit_backup_codes_generated: Log backup code generation
--
-- DEPENDENCIES: Trigger functions (V1_0_4), Tables (V1_0_2)

-- ============================================================================
-- UPDATED_AT TRIGGERS
-- ============================================================================

CREATE TRIGGER trg_set_users_updated_at
  BEFORE UPDATE ON private_schema.users
  FOR EACH ROW
  EXECUTE FUNCTION private_schema.set_updated_at_timestamp();

CREATE TRIGGER trg_set_trusted_clients_updated_at
  BEFORE UPDATE ON private_schema.trusted_clients
  FOR EACH ROW
  EXECUTE FUNCTION private_schema.set_updated_at_timestamp();

-- ============================================================================
-- AUDIT TRIGGERS
-- ============================================================================

CREATE TRIGGER trg_audit_users
  AFTER INSERT OR UPDATE OR DELETE ON private_schema.users
  FOR EACH ROW
  EXECUTE FUNCTION private_schema.audit_users();

CREATE TRIGGER trg_audit_invalidated_jwts
  AFTER INSERT ON private_schema.invalidated_jwts
  FOR EACH ROW
  EXECUTE FUNCTION private_schema.audit_invalidated_jwts();

CREATE TRIGGER trg_audit_refresh_tokens
  AFTER INSERT OR UPDATE OR DELETE ON private_schema.refresh_tokens
  FOR EACH ROW
  EXECUTE FUNCTION private_schema.audit_refresh_tokens();

CREATE TRIGGER trg_audit_trusted_clients
  AFTER INSERT OR UPDATE OR DELETE ON private_schema.trusted_clients
  FOR EACH ROW
  EXECUTE FUNCTION private_schema.audit_trusted_clients();

CREATE TRIGGER trg_audit_role_permissions
  AFTER INSERT OR DELETE ON private_schema.role_permissions
  FOR EACH ROW
  EXECUTE FUNCTION private_schema.audit_role_permissions();

CREATE TRIGGER trg_audit_user_roles
  AFTER INSERT OR DELETE ON private_schema.user_roles
  FOR EACH ROW
  EXECUTE FUNCTION private_schema.audit_user_roles();

-- ============================================================================
-- TOTP AUDIT TRIGGERS
-- ============================================================================

CREATE TRIGGER trg_audit_totp_enabled
  AFTER UPDATE ON private_schema.totp_secrets
  FOR EACH ROW
  EXECUTE FUNCTION private_schema.audit_totp_enabled();

CREATE TRIGGER trg_audit_totp_disabled
  AFTER UPDATE ON private_schema.totp_secrets
  FOR EACH ROW
  EXECUTE FUNCTION private_schema.audit_totp_disabled();

CREATE TRIGGER trg_audit_totp_last_used
  AFTER UPDATE ON private_schema.totp_secrets
  FOR EACH ROW
  EXECUTE FUNCTION private_schema.audit_totp_last_used();

CREATE TRIGGER trg_audit_backup_codes_generated
  AFTER UPDATE ON private_schema.totp_secrets
  FOR EACH ROW
  EXECUTE FUNCTION private_schema.audit_backup_codes_generated();

