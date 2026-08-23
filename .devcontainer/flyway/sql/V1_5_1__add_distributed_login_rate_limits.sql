CREATE TABLE IF NOT EXISTS private_schema.login_rate_limits (
    bucket_key text PRIMARY KEY,
    attempts integer NOT NULL,
    window_started_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_login_rate_limits_expires_at
    ON private_schema.login_rate_limits (expires_at);

ALTER TABLE private_schema.login_rate_limits OWNER TO owner_role;
REVOKE ALL ON TABLE private_schema.login_rate_limits FROM PUBLIC;

CREATE OR REPLACE FUNCTION api_schema.consume_login_rate_limit(
    p_bucket_key text,
    p_max_attempts integer,
    p_window_seconds integer
)
RETURNS boolean
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = pg_catalog, private_schema
AS $$
DECLARE
    v_attempts integer;
    v_now timestamptz := clock_timestamp();
    v_window interval;
BEGIN
    IF p_bucket_key IS NULL OR p_bucket_key = ''
       OR p_max_attempts <= 0 OR p_window_seconds <= 0 THEN
        RETURN FALSE;
    END IF;

    v_window := make_interval(secs => p_window_seconds);

    DELETE FROM private_schema.login_rate_limits
    WHERE bucket_key IN (
        SELECT bucket_key
        FROM private_schema.login_rate_limits
        WHERE expires_at < v_now
        ORDER BY expires_at
        LIMIT 100
    );

    INSERT INTO private_schema.login_rate_limits (
        bucket_key, attempts, window_started_at, expires_at
    ) VALUES (
        p_bucket_key, 1, v_now, v_now + v_window
    )
    ON CONFLICT (bucket_key) DO UPDATE
        SET attempts = CASE
                WHEN login_rate_limits.expires_at <= v_now THEN 1
                ELSE login_rate_limits.attempts + 1
            END,
            window_started_at = CASE
                WHEN login_rate_limits.expires_at <= v_now THEN v_now
                ELSE login_rate_limits.window_started_at
            END,
            expires_at = CASE
                WHEN login_rate_limits.expires_at <= v_now THEN v_now + v_window
                ELSE login_rate_limits.expires_at
            END
    RETURNING attempts INTO v_attempts;

    RETURN v_attempts <= p_max_attempts;
END;
$$;

ALTER FUNCTION api_schema.consume_login_rate_limit(text, integer, integer) OWNER TO owner_role;
REVOKE ALL ON FUNCTION api_schema.consume_login_rate_limit(text, integer, integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION api_schema.consume_login_rate_limit(text, integer, integer) TO ${API_USER};