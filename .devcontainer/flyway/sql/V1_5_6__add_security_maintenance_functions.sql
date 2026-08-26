-- Security maintenance functions for bounded rate-limit storage and audit retention.

CREATE OR REPLACE FUNCTION api_schema.cleanup_expired_login_rate_limits(
    p_batch_size integer DEFAULT 1000
)
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, private_schema
AS $$
DECLARE
    v_deleted integer;
BEGIN
    IF p_batch_size IS NULL OR p_batch_size < 1 OR p_batch_size > 10000 THEN
        RAISE EXCEPTION 'p_batch_size must be between 1 and 10000';
    END IF;

    WITH expired AS (
        SELECT bucket_key
        FROM private_schema.login_rate_limits
        WHERE expires_at < clock_timestamp()
        ORDER BY expires_at
        LIMIT p_batch_size
        FOR UPDATE SKIP LOCKED
    )
    DELETE FROM private_schema.login_rate_limits limits
    USING expired
    WHERE limits.bucket_key = expired.bucket_key;

    GET DIAGNOSTICS v_deleted = ROW_COUNT;
    RETURN v_deleted;
END;
$$;

ALTER FUNCTION api_schema.cleanup_expired_login_rate_limits(integer) OWNER TO owner_role;
REVOKE ALL ON FUNCTION api_schema.cleanup_expired_login_rate_limits(integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION api_schema.cleanup_expired_login_rate_limits(integer) TO ${API_USER};

CREATE OR REPLACE FUNCTION api_schema.cleanup_audit_logs(
    p_before timestamptz,
    p_batch_size integer DEFAULT 1000
)
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, private_schema
AS $$
DECLARE
    v_deleted integer;
BEGIN
    IF p_before IS NULL OR p_batch_size IS NULL OR p_batch_size < 1 OR p_batch_size > 10000 THEN
        RAISE EXCEPTION 'p_before and a batch size between 1 and 10000 are required';
    END IF;

    WITH old_events AS (
        SELECT id
        FROM private_schema.audit_logs
        WHERE created_at < p_before
        ORDER BY created_at
        LIMIT p_batch_size
        FOR UPDATE SKIP LOCKED
    )
    DELETE FROM private_schema.audit_logs logs
    USING old_events
    WHERE logs.id = old_events.id;

    GET DIAGNOSTICS v_deleted = ROW_COUNT;
    RETURN v_deleted;
END;
$$;

ALTER FUNCTION api_schema.cleanup_audit_logs(timestamptz, integer) OWNER TO owner_role;
REVOKE ALL ON FUNCTION api_schema.cleanup_audit_logs(timestamptz, integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION api_schema.cleanup_audit_logs(timestamptz, integer) TO ${API_USER};