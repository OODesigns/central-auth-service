-- Remove TOTP enrollment created by the legacy V1_2_0 test-data migration.
-- The audit marker identifies only migration-owned data, preserving real enrollments.

DELETE FROM private_schema.backup_codes
WHERE user_id IN (
    SELECT ts.user_id
    FROM private_schema.totp_secrets ts
    JOIN private_schema.audit_logs al ON al.target_id = ts.id
    WHERE al.actor_type = 'MIGRATION'
      AND al.action = 'TOTP_ENABLED'
      AND al.metadata ? 'test_secret'
);

DELETE FROM private_schema.totp_secrets
WHERE id IN (
    SELECT al.target_id
    FROM private_schema.audit_logs al
    WHERE al.actor_type = 'MIGRATION'
      AND al.action = 'TOTP_ENABLED'
      AND al.metadata ? 'test_secret'
);

DELETE FROM private_schema.audit_logs
WHERE actor_type = 'MIGRATION'
  AND action = 'TOTP_ENABLED'
  AND metadata ? 'test_secret';