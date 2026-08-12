-- Flyway migration: V1_4_3__fix_private_schema_table_ownership.sql
-- Transfer ownership of all private_schema tables and sequences to owner_role.
--
-- WHY: SECURITY DEFINER functions in api_schema are owned by owner_role
-- (see V1_0_6, V1_4_0, V1_4_1, V1_4_2) and therefore execute with owner_role
-- privileges. Tables created by the Flyway superuser (V1_0_2) remained owned
-- by that superuser, so owner_role had no privileges on them and every
-- SECURITY DEFINER function failed with "permission denied for table ...".
--
-- Ownership implies all privileges, and V1_0_1 default privileges already
-- REVOKE ALL from PUBLIC, so no additional grants are required.
--
-- IDEMPOTENT: re-running ALTER ... OWNER TO with the same owner is a no-op.

DO $$
DECLARE
  obj record;
BEGIN
  -- Tables
  FOR obj IN
    SELECT schemaname, tablename
    FROM pg_tables
    WHERE schemaname = 'private_schema'
  LOOP
    EXECUTE format('ALTER TABLE %I.%I OWNER TO owner_role', obj.schemaname, obj.tablename);
  END LOOP;

  -- Sequences
  FOR obj IN
    SELECT schemaname, sequencename
    FROM pg_sequences
    WHERE schemaname = 'private_schema'
  LOOP
    EXECUTE format('ALTER SEQUENCE %I.%I OWNER TO owner_role', obj.schemaname, obj.sequencename);
  END LOOP;
END
$$;

