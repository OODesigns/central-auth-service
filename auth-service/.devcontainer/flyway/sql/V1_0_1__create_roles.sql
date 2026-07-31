-- Flyway migration: V1_0_1__create_roles.sql
-- Create and configure security roles for Central Auth Service (CAS)
--
-- ROLES CREATED:
--  - owner_role (NOLOGIN): Owns all tables and functions
--  - api_role (LOGIN): Application database connection (executes api_schema functions only)
--
-- DEPENDENCIES: Schemas (V1_0_0)
-- SECURITY: Enforces principle of least privilege

-- ============================================================================
-- CREATE ROLES
-- ============================================================================

-- ROLE: owner_role (NOLOGIN) - owns all tables and functions
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'owner_role') THEN
    CREATE ROLE owner_role NOLOGIN;
  END IF;
END
$$;

COMMENT ON ROLE owner_role IS 'ROLE: Owner of all tables and functions. ' ||
    'NOLOGIN (never logs in directly). Used only for SECURITY DEFINER function execution.';

-- Transfer schema ownership to owner_role
-- Ownership grants full control: USAGE, CREATE, DROP, and all object management
-- No explicit GRANTs needed - ownership implies all privileges on the schema
ALTER SCHEMA private_schema OWNER TO owner_role;
ALTER SCHEMA api_schema OWNER TO owner_role;


-- ============================================================================
-- ROLE: api_role (LOGIN) - restricted API connection
-- ============================================================================

DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '${API_USER}') THEN
    EXECUTE format(
        'CREATE ROLE %I WITH LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION',
        '${API_USER}',
        '${API_PASSWORD}'
    );
  END IF;
END
$$;

COMMENT ON ROLE ${API_USER} IS 'ROLE: Application database connection with minimal privileges. ' ||
    'Constraints: NOSUPERUSER, NOCREATEDB, NOCREATEROLE, NOREPLICATION. ' ||
    'Can ONLY execute api_schema.* functions via SECURITY DEFINER. ' ||
    'Cannot read/write private_schema.* tables directly.';

-- Grant schema usage to api_schema only (NOT private_schema)
-- Note: api_role accesses private_schema ONLY through SECURITY DEFINER functions
--       which run as owner_role, not api_role directly
GRANT USAGE ON SCHEMA api_schema TO ${API_USER};

-- ============================================================================
-- ENFORCE LEAST PRIVILEGE
-- ============================================================================

-- Revoke all default PUBLIC permissions on both schemas (defense in depth)
REVOKE ALL ON SCHEMA private_schema FROM PUBLIC;
REVOKE ALL ON SCHEMA api_schema FROM PUBLIC;

-- Revoke default privileges for future objects (prevents accidental public access)
-- private_schema: Only owner_role should create/manage tables and sequences
ALTER DEFAULT PRIVILEGES FOR ROLE owner_role IN SCHEMA private_schema REVOKE ALL ON TABLES FROM PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE owner_role IN SCHEMA private_schema REVOKE ALL ON SEQUENCES FROM PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE owner_role IN SCHEMA private_schema REVOKE ALL ON FUNCTIONS FROM PUBLIC;

-- api_schema: Only owner_role should create/manage functions; api_role can only EXECUTE
ALTER DEFAULT PRIVILEGES FOR ROLE owner_role IN SCHEMA api_schema REVOKE ALL ON TABLES FROM PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE owner_role IN SCHEMA api_schema REVOKE ALL ON SEQUENCES FROM PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE owner_role IN SCHEMA api_schema REVOKE ALL ON FUNCTIONS FROM PUBLIC;

-- Grant EXECUTE on future api_schema functions to api_role
-- This allows api_role to call SECURITY DEFINER functions created by owner_role
--
-- SECURITY DEFINER: Functions run with owner_role's privileges (not the caller's).
-- This lets api_role execute functions that access private_schema tables,
-- even though api_role has no direct access to private_schema.
ALTER DEFAULT PRIVILEGES
    FOR ROLE owner_role           -- (1) When owner_role creates objects...
    IN SCHEMA api_schema          -- (2) ...in api_schema...
    GRANT EXECUTE ON FUNCTIONS    -- (3) ...automatically grant EXECUTE on functions...
    TO ${API_USER};               -- (4) ...to api_role

