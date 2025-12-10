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
DROP TABLE  IF EXISTS trusted_clients;
DROP TABLE IF EXISTS audit_logs;

-- === USERS TABLE ===
CREATE TABLE users (
    user_id       SERIAL PRIMARY KEY,
    username      VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- === ROLES TABLE ===
CREATE TABLE roles (
    role_id     SERIAL PRIMARY KEY,
    name        VARCHAR(50) UNIQUE NOT NULL,
    description TEXT
);

-- === USER_ROLES (many-to-many) ===
CREATE TABLE user_roles (
    user_id INT NOT NULL,
    role_id INT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES roles(role_id) ON DELETE CASCADE
);

-- === PERMISSIONS TABLE ===
CREATE TABLE permissions (
    permission_id SERIAL PRIMARY KEY,
    name          VARCHAR(100) UNIQUE NOT NULL
);

-- === ROLE_PERMISSIONS (many-to-many) ===
CREATE TABLE role_permissions (
    role_id       INT NOT NULL,
    permission_id INT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    FOREIGN KEY (role_id) REFERENCES roles(role_id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permissions(permission_id) ON DELETE CASCADE
);

-- === INVALIDATED JWTs ===
CREATE TABLE invalidated_jwts (
    jti              VARCHAR(255) PRIMARY KEY NOT NULL,
    expiry_timestamp TIMESTAMP WITH TIME ZONE NOT NULL
);

-- --------------------------------------------------------------------------------
-- Table: trusted_clients
-- Purpose: Stores metadata for machine-to-machine (M2M) certificate-authenticated clients.
-- Used for auditing, certificate validation, and revocation tracking.
-- --------------------------------------------------------------------------------
CREATE TABLE trusted_clients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    common_name TEXT NOT NULL,
    subject_dn TEXT NOT NULL UNIQUE,
    fingerprint TEXT NOT NULL UNIQUE,

    description TEXT,
    issued_by TEXT,
    issued_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    expires_at TIMESTAMP WITH TIME ZONE,
    revoked BOOLEAN DEFAULT false,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

-- --------------------------------------------------------------------------------
-- Table: audit_logs
-- Purpose: General-purpose audit trail table for recording user or system actions.
-- Tracks who performed an action, on what target, with optional metadata.
-- Supports multiple actor types: USER, CERT, SERVICE, SYSTEM, etc.
-- --------------------------------------------------------------------------------

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    actor_id UUID,
    actor_type VARCHAR(20) NOT NULL CHECK (actor_type IN ('USER', 'SERVICE', 'CERT', 'SYSTEM')),

    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(50),
    target_id UUID,

    metadata JSONB,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT now()
);

-- === INDEXES ===
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_invalidated_jwts_expiry ON invalidated_jwts(expiry_timestamp);

-- Indexes for audit_logs
CREATE INDEX idx_audit_logs_actor ON audit_logs(actor_id);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);

-- A trigger to update the 'updated_at' timestamp automatically for users
CREATE OR REPLACE FUNCTION set_updated_at_timestamp()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER trg_set_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at_timestamp();

CREATE TRIGGER trg_set_trusted_clients_updated_at
    BEFORE UPDATE ON trusted_clients
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at_timestamp();

INSERT INTO roles (name, description) VALUES
    ('admin', 'Administrator'),
    ('user', 'Regular user'),
    ('kiosk', 'Wall mounted public user');

INSERT INTO permissions (name) VALUES
    ('manage_users');
INSERT INTO role_permissions (role_id, permission_id) VALUES
    (1, 1);

-- enable pgcrypto so we can hash passwords in-DB
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- seed the admin account with DB-side bcrypt hashing
INSERT INTO users (username, password_hash, created_at, updated_at)
VALUES (
           'admin',
           crypt('${ADMIN_PASSWORD}', gen_salt('bf')),
           NOW(),
           NOW()
       )
ON CONFLICT (username) DO NOTHING;





