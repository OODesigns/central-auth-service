-- Resolve a machine client from the SHA-256 fingerprint of its peer certificate.
CREATE OR REPLACE FUNCTION api_schema.find_trusted_client_by_fingerprint(p_fingerprint text)
RETURNS TABLE(id uuid, fingerprint text, expires_at timestamptz, revoked_at timestamptz)
LANGUAGE sql
SECURITY DEFINER
SET search_path = private_schema, pg_temp
AS $$
  SELECT tc.id, tc.fingerprint, tc.expires_at, tc.revoked_at
  FROM private_schema.trusted_clients tc
  WHERE tc.fingerprint = lower(trim(p_fingerprint))
  LIMIT 1;
$$;

COMMENT ON FUNCTION api_schema.find_trusted_client_by_fingerprint(text) IS
  'Resolves a machine-to-machine client certificate fingerprint.';
ALTER FUNCTION api_schema.find_trusted_client_by_fingerprint(text) OWNER TO owner_role;
REVOKE ALL ON FUNCTION api_schema.find_trusted_client_by_fingerprint(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION api_schema.find_trusted_client_by_fingerprint(text) TO ${API_USER};