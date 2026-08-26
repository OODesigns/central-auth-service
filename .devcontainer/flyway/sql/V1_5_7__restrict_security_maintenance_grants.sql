-- Audit retention is destructive and must not be executable by the application role.
REVOKE ALL ON FUNCTION api_schema.cleanup_audit_logs(timestamptz, integer) FROM ${API_USER};
REVOKE ALL ON FUNCTION api_schema.cleanup_expired_login_rate_limits(integer) FROM ${API_USER};

-- The approved maintenance identity is provisioned by deployment and granted explicitly
-- outside the application role. This migration intentionally creates no login role.