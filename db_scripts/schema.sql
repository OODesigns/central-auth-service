-- Main schema definition for the application
-- IMPORTANT: Ensure the database role 'your_app_user' (or your chosen app user name)
-- has been created and granted CONNECT permission to this database BEFORE running this script.

-- Drop objects in reverse order of dependency or creation
-- Drop trigger first if it exists, as it depends on the function and table
DROP TRIGGER IF EXISTS update_users_updated_at ON users;

-- Drop function if it exists
DROP FUNCTION IF EXISTS update_updated_at_column();

-- Drop tables if they exist to ensure a clean setup (optional, for development)
-- Drop in reverse order of creation due to foreign keys if they existed
DROP TABLE IF EXISTS invalidated_jwts;
DROP TABLE IF EXISTS users;


-- Create the 'users' table
CREATE TABLE users
(
    user_id       SERIAL PRIMARY KEY,                                 -- Auto-incrementing integer for unique user ID
    username      VARCHAR(50) UNIQUE NOT NULL,                        -- Username, must be unique and not null
    password_hash VARCHAR(255)       NOT NULL,                        -- Store hashed passwords, not plain text
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP, -- Timestamp of when the user was created
    updated_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP  -- Timestamp of when the user was last updated
);

-- Create the 'invalidated_jwts' table
-- Corresponds to the InvalidatedJwtEntity
CREATE TABLE invalidated_jwts
(
    jti              VARCHAR(255) PRIMARY KEY NOT NULL, -- JWT ID, from @Id @Column(length = 255)
    expiry_timestamp TIMESTAMP WITH TIME ZONE NOT NULL  -- Expiry timestamp of the token, from @Column(nullable = false)
    -- JPA typically maps camelCase 'expiryTimestamp' to snake_case 'expiry_timestamp'
);

-- Create indexes for performance
CREATE INDEX idx_users_username ON users(username);

-- Index for invalidated_jwts table based on @Table(indexes = {@Index(...)})
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
    BEFORE UPDATE
    ON users
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- -- Grant permissions to the application user
-- GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE users TO your_app_user;
-- GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE invalidated_jwts TO your_app_user;
--
-- -- Grant usage on sequences for SERIAL primary keys
-- GRANT USAGE, SELECT ON SEQUENCE users_user_id_seq TO your_app_user;

COMMIT;
