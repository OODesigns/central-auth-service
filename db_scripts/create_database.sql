-- create_database.sql
-- Purpose: Creates the portal database.
--
CREATE DATABASE mvhr_portal
    WITH
    OWNER = postgres -- Or your main administrative user, if different
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_GB.UTF-8'
    LC_CTYPE = 'en_GB.UTF-8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;

-- Optional: Add a comment to the database
COMMENT ON DATABASE mvhr_portal IS 'Database for the web view of the Home Control application.';

