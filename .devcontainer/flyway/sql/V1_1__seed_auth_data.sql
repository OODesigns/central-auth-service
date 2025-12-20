-- Flyway migration: V1_1__seed_auth_data.sql
-- Bootstrap authorization data for Central Auth Service (CAS)
--
-- This migration seeds:
--  - static roles
--  - static permissions
--  - role → permission mappings
--  - bootstrap admin user (forced password rotation)
--  - admin → admin-role assignment
--
-- All inserts are idempotent and safe to run once.

-- ============================================================================
-- SEED ROLES (STATIC CONFIG)
-- ============================================================================

INSERT INTO roles (name, description) VALUES
                                          ('admin', 'Administrator'),
                                          ('user', 'Regular user'),
                                          ('kiosk', 'Wall mounted public user')
ON CONFLICT (name) DO NOTHING;

-- ============================================================================
-- SEED PERMISSIONS (STATIC CONFIG)
-- ============================================================================

INSERT INTO permissions (name) VALUES
    ('create_user'),
    ('update_user'),
    ('delete_user'),
    ('create_certificate'),
    ('view_audit_log'),
    ('clear_audit_log')
ON CONFLICT (name) DO NOTHING;

-- ============================================================================
-- MAP ROLES → PERMISSIONS
-- ============================================================================

-- Admins can manage users and audit logs
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
         JOIN permissions p
              ON r.name = 'admin'
                  AND p.name IN ('create_user', 'update_user', 'delete_user', 
                  'create_certificate', 'view_audit_log', 'clear_audit_log')
ON CONFLICT DO NOTHING;

-- ============================================================================
-- BOOTSTRAP ADMIN USER (FORCE PASSWORD ROTATION)
-- ============================================================================

-- NOTE:
--  - password_hash must be injected via Flyway placeholder or environment
--  - admin must rotate password on first login

INSERT INTO users (
    username,
    password_hash,
    force_password_reset,
    created_at,
    updated_at
)
VALUES (
        'admin',
         password_hash,  -- pre-hashed, injected securely
         true,
         NOW(),
         NOW()
       )
ON CONFLICT (username) DO NOTHING;

-- ============================================================================
-- ASSIGN ADMIN ROLE TO ADMIN USER
-- ============================================================================

INSERT INTO user_roles (user_id, role_id)
SELECT u.user_id, r.role_id
FROM users u
         JOIN roles r
              ON u.username = 'admin'
                  AND r.name = 'admin'
ON CONFLICT DO NOTHING;
