-- Flyway migration: V1_3__add_auth_flow_permissions.sql
-- Add permissions for password reset and 2FA setup flows
--
-- New permissions:
--  - change_password: Allows user to change their own password (password reset flow)
--  - setup_mfa: Allows user to set up 2FA/WebAuthn (MFA enrollment flow)
--  - view_settings: Allows user to view their account settings
--
-- These permissions are used for selective permission loading in the login flow:
-- - Password reset branch loads only "change_password"
-- - MFA setup branch loads only "setup_mfa"
-- - Normal login loads full role-based permissions
--
-- All users (admin, user, kiosk) can perform their own password reset and 2FA setup
-- These are user-scoped actions, not admin actions

-- ============================================================================
-- ADD NEW PERMISSIONS
-- ============================================================================

INSERT INTO private_schema.permissions (name)
VALUES ('change_password'),
       ('setup_mfa'),
       ('view_settings')
ON CONFLICT (name) DO NOTHING;

-- ============================================================================
-- GRANT PERMISSIONS TO ALL ROLES (User-scoped actions)
-- ============================================================================

-- All authenticated users can change their own password
INSERT INTO private_schema.role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM private_schema.roles r
  CROSS JOIN private_schema.permissions p
WHERE p.name = 'change_password'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- All authenticated users can set up their own 2FA
INSERT INTO private_schema.role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM private_schema.roles r
  CROSS JOIN private_schema.permissions p
WHERE p.name = 'setup_mfa'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- All authenticated users can view their own settings
INSERT INTO private_schema.role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM private_schema.roles r
  CROSS JOIN private_schema.permissions p
WHERE p.name = 'view_settings'
ON CONFLICT (role_id, permission_id) DO NOTHING;

