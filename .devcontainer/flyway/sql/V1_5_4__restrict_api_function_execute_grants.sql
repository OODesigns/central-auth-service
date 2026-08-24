-- Restrict application execution to explicitly reviewed API functions.

ALTER DEFAULT PRIVILEGES
    FOR ROLE owner_role
    IN SCHEMA api_schema
    REVOKE EXECUTE ON FUNCTIONS FROM ${API_USER};

REVOKE ALL ON ALL FUNCTIONS IN SCHEMA api_schema FROM ${API_USER};

GRANT EXECUTE ON FUNCTION api_schema.find_user_credentials(text) TO ${API_USER};
GRANT EXECUTE ON FUNCTION api_schema.get_user(uuid) TO ${API_USER};
GRANT EXECUTE ON FUNCTION api_schema.get_totp_status(uuid) TO ${API_USER};
GRANT EXECUTE ON FUNCTION api_schema.store_totp_secret(uuid, bytea) TO ${API_USER};
GRANT EXECUTE ON FUNCTION api_schema.enable_totp(uuid) TO ${API_USER};
GRANT EXECUTE ON FUNCTION api_schema.disable_totp(uuid, text) TO ${API_USER};
GRANT EXECUTE ON FUNCTION api_schema.insert_backup_codes(uuid, text[], uuid) TO ${API_USER};
GRANT EXECUTE ON FUNCTION api_schema.consume_backup_code(uuid, text) TO ${API_USER};
GRANT EXECUTE ON FUNCTION api_schema.get_totp_secret(uuid) TO ${API_USER};
GRANT EXECUTE ON FUNCTION api_schema.get_pending_totp_secret(uuid) TO ${API_USER};
GRANT EXECUTE ON FUNCTION api_schema.find_unused_backup_code_hashes(uuid) TO ${API_USER};
GRANT EXECUTE ON FUNCTION api_schema.mark_totp_last_used(uuid) TO ${API_USER};
GRANT EXECUTE ON FUNCTION api_schema.find_user_credentials_by_id(uuid) TO ${API_USER};
GRANT EXECUTE ON FUNCTION api_schema.store_refresh_token(uuid, text) TO ${API_USER};
GRANT EXECUTE ON FUNCTION api_schema.rotate_refresh_token(text, text) TO ${API_USER};
GRANT EXECUTE ON FUNCTION api_schema.invalidate_jwt(uuid, text, timestamptz, text) TO ${API_USER};
GRANT EXECUTE ON FUNCTION api_schema.is_jwt_invalidated(uuid) TO ${API_USER};
GRANT EXECUTE ON FUNCTION api_schema.consume_totp_counter(uuid, bigint) TO ${API_USER};
GRANT EXECUTE ON FUNCTION api_schema.consume_login_rate_limit(text, integer, integer) TO ${API_USER};