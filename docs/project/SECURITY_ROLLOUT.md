# Production Security Rollout

This runbook describes the production automation and operational controls required after the application security work is complete. GitHub-hosted CI is intentionally disabled for this repository. Run these stages from an approved internal runner such as Jenkins, GitLab CI, Argo Workflows, or the deployment platform's native pipeline.

## Automated Pipeline

The production pipeline must run these stages in order and stop on any failure:

1. Compile and run unit and integration tests.
2. Enforce the JaCoCo coverage requirement.
3. Verify dependency locks and run OSV scanning.
4. Build one immutable Docker image.
5. Run Trivy against that exact image and reject HIGH or CRITICAL findings.
6. Publish the image to the approved registry and record its digest.
7. Validate and apply database migrations as a single pre-deployment job.
8. Deploy by image digest using rolling or blue/green replacement.
9. Run health, login, TOTP, refresh, and logout smoke tests.
10. Promote the deployment only when all smoke tests pass.

Do not deploy mutable tags such as `latest`. Do not rebuild the image between scanning and deployment.

## Local Security Gate

The checked-in gate is:

```bash
scripts/security-check.sh
```

Use `INCLUDE_DB_TESTS=true` when the migrated PostgreSQL test stack is available. The default behavior requires OSV, Docker, and Trivy. The `SKIP_*` options are for intentional partial local checks and must not be used by the production pipeline.

## Database Migrations

Run Flyway as a one-shot Kubernetes Job, init job, or pre-deployment pipeline stage. Application instances must not start until this job succeeds:

```bash
flyway validate
flyway migrate
flyway info
```

The production job must:

- Back up PostgreSQL before migration.
- Apply migrations through `V1_5_4`.
- Use a dedicated migration identity rather than the application identity.
- Allow only one migration job to run; Flyway's schema-history lock provides additional protection.
- Verify that `api_schema.consume_totp_counter` and `api_schema.consume_login_rate_limit` are executable by the application role.
- Never use `validateOnMigrate=false` or automatically run `flyway repair`.
- Use a forward corrective migration instead of destructive rollback whenever possible.

## TLS and Certificates

Automate certificate issuance and renewal through cert-manager, the cloud certificate service, or the organization's PKI. Mount certificates and stores read-only and configure:

```text
ALLOW_PLAINTEXT=false
KEYSTORE_PATH=<mounted PKCS12 or JKS path>
TRUSTSTORE_PATH=<mounted truststore path when mTLS is required>
```

Store `KEYSTORE_PASSWORD` and `TRUSTSTORE_PASSWORD` in the secret manager. Alert before certificate expiry and verify renewal with an automated TLS handshake test. Require mTLS for internal machine-to-machine callers when the deployment trust model calls for it.

## Secret Management

Use Vault, AWS Secrets Manager, Azure Key Vault, Google Secret Manager, or an equivalent production secret manager. Provision these values at runtime:

- Database credentials
- JWT active and previous signing keys
- TOTP encryption key
- Keystore and truststore passwords
- Any migration-only credentials

Do not store secrets in images, Git, Compose files, build logs, or pipeline variables that are visible to untrusted jobs. Restrict each workload identity to only the secrets it needs. Secret changes require a rolling restart because the current adapters retrieve environment-backed values from the process environment.

## JWT Key Rotation

Automate rotation as a controlled workflow:

1. Generate a new key in the secret manager.
2. Expose its secret name through `JWT_ACTIVE_KEY_ID`.
3. Move the old secret name into the comma-separated `JWT_PREVIOUS_KEY_IDS` value.
4. Roll out all application instances.
5. Verify new tokens carry the new `kid` and old tokens still verify.
6. Keep the previous key available for at least seven days plus the permitted clock skew.
7. Remove the previous key in a later approved rollout.

Emergency compromise rotation may revoke sessions immediately instead of retaining the previous key.

## TOTP and JWT Compatibility

After deployment:

- Confirm new TOTP secrets begin with the `CAS` version header and use AES-GCM.
- Confirm existing legacy AES-CBC rows still verify during migration.
- Confirm newly issued JWTs contain `ver=2`, top-level claims, and a `kid` header.
- Retain legacy JWT verification for at least seven days, the maximum refresh-token lifetime.
- Remove legacy CBC and nested JWT readers only after stored secrets have been re-encrypted and all legacy tokens have expired. That future removal is a separate reviewed code change, not part of this deployment.

## Backups and Recovery

Production PostgreSQL must have encrypted automated backups and point-in-time recovery. Automation must monitor backup age and failure status. At a scheduled interval, restore a backup into an isolated environment and run database integrity plus authentication smoke tests.

Document and test:

- Recovery point objective and recovery time objective
- Database restore procedure
- Secret and certificate recovery procedure
- Compromised JWT-key response
- Failed migration response
- Regional or host failure response

A backup is not considered validated until a restore test succeeds.

## Monitoring and Alerts

Collect service, gRPC, database, host, and deployment metrics. Alert on:

- Repeated failed logins or TOTP attempts
- Distributed rate-limiter database errors
- Token verification and revocation-store errors
- TLS handshake failures and approaching certificate expiry
- Database connection saturation, storage pressure, and replication lag
- Migration failures
- Backup failures or excessive backup age
- Unexpected service restarts and failed health checks

Route alerts to the operational incident channel with an owner and response runbook. Avoid logging passwords, raw tokens, TOTP secrets, backup codes, or encryption keys.

## Audit Retention

Define retention according to legal and organizational requirements. Restrict access to audit data, encrypt archives, and use immutable or write-once storage where required. Monitor failed archival jobs and periodically prove that retained events can be queried during an investigation.

## Deployment and Rollback

Use rolling or blue/green deployment. Automatically stop promotion when health or authentication smoke tests fail. Application rollback may return to the previous image only when its schema compatibility is known. Prefer a forward database fix over down migrations.

Require human approval for:

- Flyway checksum repair
- Destructive schema changes
- Emergency key revocation
- Production restore or failover
- Disabling a security scanner or deployment gate

## Production Acceptance

Production is ready only when all of the following evidence exists:

- The final image digest passed OSV and Trivy policy.
- Flyway validation and migrations succeeded without ignored validation errors.
- TLS is active and plaintext is disabled.
- Secrets originate from the production secret manager.
- JWT rotation was tested with active and previous keys.
- Database and gRPC smoke tests passed against the deployed release.
- Backup restore was demonstrated.
- Monitoring, alert routing, audit retention, and incident runbooks were reviewed by their owners.