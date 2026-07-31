-- Flyway migration: V1_0_0__create_schemas.sql
-- Create database schemas for Central Auth Service (CAS)
--
-- SCHEMAS CREATED:
--  - private_schema: Data layer (tables - no direct application access)
--  - api_schema: API layer (SECURITY DEFINER functions - safe entry points)
--
-- DEPENDENCIES: None
-- EXECUTED FIRST: Yes (schemas must exist before tables/functions)

CREATE EXTENSION IF NOT EXISTS pgcrypto;


-- ============================================================================
-- CREATE SCHEMAS
-- ============================================================================

-- SCHEMA: private_schema (data layer - tables only)
CREATE SCHEMA private_schema;
COMMENT ON SCHEMA private_schema IS 'SCHEMA: Data layer - contains all authentication tables. No direct application access.';

-- SCHEMA: api_schema (API layer - SECURITY DEFINER functions)
CREATE SCHEMA api_schema;
COMMENT ON SCHEMA api_schema IS 'SCHEMA: API layer - safe entry points for applications';

-- ============================================================================
-- TRANSFER SCHEMA OWNERSHIP TO owner_role
-- ============================================================================
-- NOTE: owner_role is created in V1_0_1. Schema ownership is transferred
-- later in V1_0_1 after owner_role exists. This ensures owner_role truly
-- owns all objects and ALTER DEFAULT PRIVILEGES work correctly.

