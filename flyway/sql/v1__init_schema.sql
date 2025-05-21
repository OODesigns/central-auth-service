-- Flyway migration: V1__init_cas_schema.sql
-- Initial schema for Central Auth Service (CAS)

-- Drop trigger and function first (safe for re-run in dev)
DROP TRIGGER IF EXISTS update_users_updated_at ON users;
DROP FUNCTION IF EXISTS update_updated_at_column();

-- Drop tables in dependency order
DROP TABLE IF EXISTS role_permissions;
DROP TABLE IF EXISTS permissions;
DROP TABLE IF EXISTS user_roles;
DROP TABLE IF EXISTS roles;
DROP TABLE IF EXISTS invalidated_jwts;
DROP TABLE IF EXISTS users;

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

-- === INDEXES ===
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_invalidated_jwts_expiry ON invalidated_jwts(expiry_timestamp);

-- A trigger to update the 'updated_at' timestamp automatically for users
CREATE OR REPLACE FUNCTION update_updated_at_column()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

INSERT INTO roles (name, description) VALUES
    ('admin', 'Administrator'),
    ('user', 'Regular user'),
    ('kiosk', 'Wall mounted public user');

INSERT INTO permissions (name) VALUES
    ('manage_users');
INSERT INTO role_permissions (role_id, permission_id) VALUES
    (1, 1);

-- need to create an app database password not just the admin one


