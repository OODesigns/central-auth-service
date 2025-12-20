-- Flyway migration: V1__init_cas_schema.sql
-- Initial schema for Central Auth Service (CAS)

-- Drop trigger and function first (safe for re-run in dev)
DROP TRIGGER IF EXISTS trg_set_trusted_clients_updated_at ON trusted_clients;
DROP TRIGGER IF EXISTS trg_set_users_updated_at ON users;
DROP FUNCTION IF EXISTS set_updated_at_timestamp();

-- Drop tables in dependency order
DROP TABLE IF EXISTS role_permissions;
DROP TABLE IF EXISTS permissions;
DROP TABLE IF EXISTS user_roles;
DROP TABLE IF EXISTS roles;
DROP TABLE IF EXISTS invalidated_jwts;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS trusted_clients;
DROP TABLE IF EXISTS audit_logs;

-- enable pgcrypto so we can use gen_random_uuid
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================================================================
-- USERS
-- ============================================================================

CREATE TABLE users (
    user_id       SERIAL PRIMARY KEY,
    username      VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ DEFAULT now(),
    updated_at    TIMESTAMPTZ DEFAULT now()
);

COMMENT ON TABLE users IS
    'Application users and authentication credentials';

COMMENT ON COLUMN users.user_id IS
    'Internal numeric user identifier';

COMMENT ON COLUMN users.username IS
    'Unique login name';

COMMENT ON COLUMN users.password_hash IS
    'Hashed user password';

COMMENT ON COLUMN users.created_at IS
    'Timestamp when the user record was created';

COMMENT ON COLUMN users.updated_at IS
    'Timestamp of last update (auto-managed by trigger)';

-- ============================================================================
-- ROLES
-- ============================================================================

CREATE TABLE roles (
    role_id     SERIAL PRIMARY KEY,
    name        VARCHAR(50) UNIQUE NOT NULL,
    description TEXT
);

COMMENT ON TABLE roles IS
    'High-level roles that group permissions';

COMMENT ON COLUMN roles.role_id IS
    'Internal role identifier';

COMMENT ON COLUMN roles.name IS
    'Unique role name (e.g. admin, user, kiosk)';

COMMENT ON COLUMN roles.description IS
    'Human-readable role description';

-- ============================================================================
-- USER_ROLES (many-to-many)
-- ============================================================================

CREATE TABLE user_roles (
    user_id INT NOT NULL,
    role_id INT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES roles(role_id) ON DELETE CASCADE
);

COMMENT ON TABLE user_roles IS
    'Join table mapping users to roles';

COMMENT ON COLUMN user_roles.user_id IS
    'References users.user_id';

COMMENT ON COLUMN user_roles.role_id IS
    'References roles.role_id';

-- ============================================================================
-- PERMISSIONS
-- ============================================================================

CREATE TABLE permissions (
                             permission_id SERIAL PRIMARY KEY,
                             name          VARCHAR(100) UNIQUE NOT NULL
);

COMMENT ON TABLE permissions IS
    'Fine-grained permissions used for authorization';

COMMENT ON COLUMN permissions.permission_id IS
    'Internal permission identifier';

COMMENT ON COLUMN permissions.name IS
    'Unique permission name (e.g. manage_users)';

-- ============================================================================
-- ROLE_PERMISSIONS (many-to-many)
-- ============================================================================

CREATE TABLE role_permissions (
    role_id       INT NOT NULL,
    permission_id INT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    FOREIGN KEY (role_id) REFERENCES roles(role_id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permissions(permission_id) ON DELETE CASCADE
);

COMMENT ON TABLE role_permissions IS
    'Join table mapping roles to permissions';

COMMENT ON COLUMN role_permissions.role_id IS
    'Role granting the permission';

COMMENT ON COLUMN role_permissions.permission_id IS
    'Granted permission';

-- ============================================================================
-- INVALIDATED_JWTS
-- ============================================================================

CREATE TABLE invalidated_jwts (
    id               SERIAL PRIMARY KEY,
    jti              VARCHAR(255),
    token_hash       VARCHAR(255) NOT NULL,
    reason           TEXT,
    expiry_timestamp TIMESTAMPTZ NOT NULL,
    created_at       TIMESTAMPTZ DEFAULT now()
);

COMMENT ON TABLE invalidated_jwts IS
    'Stores revoked or invalidated JWTs to prevent reuse';

COMMENT ON COLUMN invalidated_jwts.id IS
    'Internal identifier';

COMMENT ON COLUMN invalidated_jwts.jti IS
    'JWT ID claim (if present)';

COMMENT ON COLUMN invalidated_jwts.token_hash IS
    'Hash of the JWT token';

COMMENT ON COLUMN invalidated_jwts.reason IS
    'Reason the token was invalidated';

COMMENT ON COLUMN invalidated_jwts.expiry_timestamp IS
    'Original JWT expiration timestamp';

COMMENT ON COLUMN invalidated_jwts.created_at IS
    'Timestamp when the token was invalidated';

-- ============================================================================
-- TRUSTED_CLIENTS
-- ============================================================================

CREATE TABLE trusted_clients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    common_name TEXT NOT NULL,
    subject_dn TEXT NOT NULL UNIQUE,
    fingerprint TEXT NOT NULL UNIQUE,

    description TEXT,
    issued_by TEXT,
    issued_at TIMESTAMPTZ DEFAULT now(),
    expires_at TIMESTAMPTZ,
    revoked BOOLEAN DEFAULT false,

    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

COMMENT ON TABLE trusted_clients IS
    'Machine-to-machine (M2M) certificate-authenticated clients';

COMMENT ON COLUMN trusted_clients.id IS
    'Unique client identifier (UUID)';

COMMENT ON COLUMN trusted_clients.common_name IS
    'Certificate common name (CN)';

COMMENT ON COLUMN trusted_clients.subject_dn IS
    'Full certificate subject distinguished name';

COMMENT ON COLUMN trusted_clients.fingerprint IS
    'Unique certificate fingerprint';

COMMENT ON COLUMN trusted_clients.description IS
    'Human-readable description of the client';

COMMENT ON COLUMN trusted_clients.issued_by IS
    'Certificate issuing authority';

COMMENT ON COLUMN trusted_clients.issued_at IS
    'Certificate issue timestamp';

COMMENT ON COLUMN trusted_clients.expires_at IS
    'Certificate expiration timestamp';

COMMENT ON COLUMN trusted_clients.revoked IS
    'Whether the client certificate is revoked';

COMMENT ON COLUMN trusted_clients.created_at IS
    'Record creation timestamp';

COMMENT ON COLUMN trusted_clients.updated_at IS
    'Last update timestamp (auto-managed by trigger)';

-- ============================================================================
-- AUDIT_LOGS
-- ============================================================================

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    actor_id UUID,
    actor_type VARCHAR(20) NOT NULL
        CHECK (actor_type IN ('USER', 'SERVICE', 'CERT', 'SYSTEM')),

    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(50),
    target_id UUID,

    metadata JSONB,

    created_at TIMESTAMPTZ DEFAULT now()
);

COMMENT ON TABLE audit_logs IS
    'General-purpose audit trail for user and system actions';

COMMENT ON COLUMN audit_logs.id IS
    'Audit record identifier';

COMMENT ON COLUMN audit_logs.actor_id IS
    'Identifier of the actor performing the action';

COMMENT ON COLUMN audit_logs.actor_type IS
    'Actor type: USER, SERVICE, CERT, or SYSTEM';

COMMENT ON COLUMN audit_logs.action IS
    'Action performed';

COMMENT ON COLUMN audit_logs.target_type IS
    'Type of entity acted upon';

COMMENT ON COLUMN audit_logs.target_id IS
    'Identifier of the target entity';

COMMENT ON COLUMN audit_logs.metadata IS
    'Optional structured metadata';

COMMENT ON COLUMN audit_logs.created_at IS
    'Timestamp when the action occurred';

-- ============================================================================
-- INDEXES
-- ============================================================================

CREATE INDEX idx_users_username ON users(username);
CREATE UNIQUE INDEX idx_token_hash ON invalidated_jwts(token_hash);
CREATE INDEX idx_jti ON invalidated_jwts(jti);
CREATE INDEX idx_expiry_timestamp ON invalidated_jwts(expiry_timestamp);

CREATE INDEX idx_audit_logs_actor ON audit_logs(actor_id);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);

-- ============================================================================
-- UPDATED_AT TRIGGER
-- ============================================================================

CREATE OR REPLACE FUNCTION set_updated_at_timestamp()
    RETURNS TRIGGER AS
$$
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
