-- Flyway migration: V1_1__seed_auth_data.sql
-- Bootstrap authorization data for Central Auth Service (CAS)
--
-- This migration seeds:
--  - static roles (admin, user, kiosk)
--  - static permissions (user/cert/audit management)
--  - role → permission mappings
--  - bootstrap admin user (forced password rotation on first login)
--  - admin user → admin role assignment
--
-- IMPORTANT REQUIREMENTS:
--
--  1. ADMIN PASSWORD INJECTION (REQUIRED)
--     Set Flyway placeholder: ${ADMIN_PASSWORD_HASH}
--     Configure via .env file: ADMIN_PASSWORD_HASH=<bcrypt_hash>
--     
--     The placeholder MUST be replaced before running this migration.
--     If not configured, the literal string '${ADMIN_PASSWORD_HASH}' will be inserted
--     and the admin user will not be able to log in.
--
--  2. IDEMPOTENCY
--     All INSERT statements use ON CONFLICT (name/role) DO NOTHING
--     Safe to run multiple times without errors
--
--  3. ROLE PERMISSIONS
--     - admin: Full permissions (create/update/delete users, certs, audit)
--     - user:  No permissions (view-only, handled at application layer)
--     - kiosk: No permissions (public terminal, view-only access)
--     
--     To grant permissions to 'user' or 'kiosk' roles, uncomment and modify
--     the commented INSERT statements below in the PERMISSIONS section.
--
-- SECURITY NOTES:
--  - Admin user password_reset_required_at is set to NOW()
--    Admin MUST change password on first login
--  - Consider rotating admin password in production deployment
--  - Use environment-based password injection in CI/CD, not hardcoded

-- ============================================================================
-- SEED ROLES (STATIC CONFIG)
-- ============================================================================

INSERT INTO private_schema.roles (name, description)
VALUES ('admin', 'Administrator'),
       ('user', 'Regular user'),
       ('kiosk', 'Wall mounted public user')
ON CONFLICT (name) DO NOTHING;

-- ============================================================================
-- SEED PERMISSIONS (STATIC CONFIG)
-- ============================================================================

INSERT INTO private_schema.permissions (name)
VALUES ('create_user'),
       ('update_user'),
       ('delete_user'),
       ('create_certificate'),
       ('revoke_certificate'),
       ('view_audit_log'),
       ('clear_audit_log')
ON CONFLICT (name) DO NOTHING;

-- ============================================================================
-- MAP ROLES → PERMISSIONS
-- ============================================================================

-- Admins can manage users and audit logs
INSERT INTO private_schema.role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM private_schema.roles r
  CROSS JOIN private_schema.permissions p
WHERE r.name = 'admin'
  AND p.name IN ('create_user', 'update_user', 'delete_user',
                 'create_certificate', 'revoke_certificate', 'view_audit_log',
                 'clear_audit_log')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ...existing code...

-- ============================================================================
-- BOOTSTRAP ADMIN USER (FORCE PASSWORD ROTATION)
-- ============================================================================

-- NOTE:
--  - password_hash must be injected via Flyway placeholder or environment
--  - admin must rotate password on first login (password_reset_required_at is set to now())

INSERT INTO private_schema.users (username, password_hash, password_reset_required_at, created_at, updated_at)
VALUES ('admin', '${ADMIN_PASSWORD_HASH}', NOW(), NOW(), NOW())
ON CONFLICT (username) DO NOTHING;

-- ============================================================================
-- ASSIGN ADMIN ROLE TO ADMIN USER
-- ============================================================================

INSERT INTO private_schema.user_roles (user_id, role_id)
SELECT u.user_id, r.role_id
FROM private_schema.users u
  CROSS JOIN private_schema.roles r
WHERE u.username = 'admin'
  AND r.name = 'admin'
ON CONFLICT (user_id, role_id) DO NOTHING;