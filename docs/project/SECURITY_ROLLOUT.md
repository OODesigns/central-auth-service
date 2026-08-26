# Production Security Rollout

This runbook describes the production automation and operational controls required after the application security work is complete. Repository CI provides a baseline gate; production must also run these stages from an approved internal runner such as Jenkins, GitLab CI, Argo Workflows, or the deployment platform's native pipeline.

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

GitHub-hosted CI must not enforce this local gate as the authoritative production control. The repository continues to provide a developer convenience gate, while the approved internal runner, image digest record, and deployment approval chain remain the enforcement boundary for production.

Invoke `ops/internal-security-gate.sh` only from the approved internal runner. It requires `RELEASE_IMAGE_DIGEST` and `DEPLOYMENT_APPROVAL_ID`, runs the full local security gate with database tests, validates deployment prerequisites, and verifies the promoted digest. Preserve its output with the release evidence.

The repository-level completion state and the administrator-issued recovery implementation sequence are recorded in [SECURITY_IMPLEMENTATION_PLAN.md](SECURITY_IMPLEMENTATION_PLAN.md).
Deploy and operate the recovery flow using [ADMIN_RECOVERY_RUNBOOK.md](ADMIN_RECOVERY_RUNBOOK.md).

## Key rotation and compatibility policy

Rotate JWT and TOTP material on a documented schedule and require the same evidence path as a production change:

1. Generate a new active key using the approved local or platform-controlled procedure and mount it through the runtime secret path.
2. Register the new key ID as `JWT_ACTIVE_KEY_ID` and move the previous active secret into `JWT_PREVIOUS_KEY_IDS` only after the rollout has been validated.
3. Roll out all service instances with the new active key before removing the older one, and keep the previous key for at least seven days plus the configured clock skew.
4. For TOTP encryption, require a new AES-GCM secret only after verifying that the key lifecycle is coordinated with legacy encryption migration and the service can still validate existing ciphertext.
5. Require emergency rotation on suspected compromise, and revoke the relevant sessions or tokens immediately if the incident response demands it.
6. Retire legacy CBC and nested-token readers only after the stored secrets or tokens have been re-encrypted or expired and the migration has been tested in a staging or restored environment.

Evidence required before retirement of a previous key:

- successful token verification with the current and previous keys,
- passwordless or MFA smoke tests that still pass after rollover,
- recorded deployment digest and rollback plan,
- a documented incident owner for emergency rotation.

## Structured security logs and tracing policy

The service must emit structured, security-safe events for authentication, authorization, MFA, certificate validation, and database-guard failures. The logging contract is:

- Log JSON or another structured format with fixed keys and explicit event names.
- Include only low-risk metadata such as result category, RPC method, environment, actor type, correlation ID, and certificate fingerprint when needed; never log raw tokens, passwords, TOTP secrets, backup codes, signed JWTs, or recovery material.
- Bind logs to a correlation or request ID propagated across gRPC and database operations.
- Record one security event per outcome, with a stable classification (`auth_failure`, `mfa_required`, `mtls_reject`, `authz_denied`, `token_revoked`, `audit_event`) rather than logging raw exceptions.
- Redact or omit user identifiers that are not needed for an incident investigation, and avoid general-purpose analytics exports for raw security logs.
- Retain structured security logs under the same operational retention model as audit data, with a defined deletion window and access control.
- Route trace spans to the approved observability backend and keep labels bounded to method, status, result category, and environment only.

## Administrator-issued recovery requirement

The current service must not expose a public request-password-reset RPC or depend on an email provider. Account recovery begins only after an authorized support administrator verifies identity through the organization's approved out-of-band process.

The implementation must provide:

1. A protected `IssueRecoveryToken` RPC guarded by a current access token and the `manage_recovery` permission.
2. A dedicated short-lived, single-use recovery token with a reset-only audience, a JTI, and hash-only persistence.
3. A public `CompleteRecovery` RPC that accepts the recovery token and new password, with identical public failures for invalid, expired, and consumed tokens.
4. Atomic password update, refresh-session revocation, and MFA re-enrollment requirements on successful completion.
5. Attributable audit events for issuance, completion, and failure that never contain the recovery token.
6. A documented support procedure for identity verification and token delivery that does not place the recovery token in general-purpose ticket text.

A `PASSWORD_RESET_REQUIRED` login outcome remains informational and must not be treated as a complete recovery implementation.

## Database Migrations

Run Flyway as a one-shot Kubernetes Job, init job, or pre-deployment pipeline stage. Application instances must not start until this job succeeds:

```bash
flyway validate
flyway migrate
flyway info
```

The production job must:

- Back up PostgreSQL before migration.
- Apply migrations through the current migration version, including security maintenance migrations.
- Use a dedicated migration identity rather than the application identity.
- Allow only one migration job to run; Flyway's schema-history lock provides additional protection.
- Verify that `api_schema.consume_totp_counter` and `api_schema.consume_login_rate_limit` are executable by the application role.
- Verify that destructive cleanup functions are executable only by the separate maintenance identity, never by the application role.
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

For Linux-local deployments, mount root-readable secret files through Docker secrets, a Kubernetes secret volume, or systemd credentials and configure `SECRETS_BACKEND=file` with `SECRETS_DIRECTORY=/run/secrets`. The filename is the configured key ID, such as `JWT_SECRET` or `TOTP_ENCRYPTION_KEY`; the application reads it on demand and strips one trailing line break. `SECRETS_BACKEND=environment` remains the compatibility default.

Provision these values at runtime:

- Database credentials
- JWT active and previous signing keys
- TOTP encryption key
- Keystore and truststore passwords
- Any migration-only credentials
- Maintenance-job credentials for rate-limit and audit cleanup

Do not store secrets in images, Git, Compose files, build logs, or pipeline variables that are visible to untrusted jobs. Restrict each workload identity to only the secrets it needs. Secret changes require a rolling restart because the current adapters retrieve key material at operation time but do not watch mounts for changes.

## Key rotation schedule and controls

Use a controlled rotation calendar and a documented incident trigger. Rotation is mandatory when any key is suspected of compromise, when the active key has reached its lifecycle limit, or when a legacy algorithm or ciphertext mode is being retired.

| Material | Default cadence | Trigger for immediate rotation | Retention requirement |
| --- | --- | --- | --- |
| JWT signing key | 90 days | suspected compromise, security incident, key exposure, failed verification | keep previous key for at least 7 days plus permitted clock skew and validation backlog |
| TOTP encryption key | 180 days or at next re-encryption event | discovery of legacy or compromised ciphertext, suspected key leak | keep prior key only while existing encrypted secrets remain valid |
| mTLS / keystore trust material | per certificate policy, not to exceed 90 days | expiry alert, CA compromise, trust revocation | keep prior trust path until old certs are fully retired |

Operational owner: security engineering or platform security, with a release engineer and application owner both signing off on the rollout. Record the old and new key IDs, deployment digest, and verification results in the change record.

## JWT Key Rotation Runbook

Automate rotation as a controlled workflow:

1. Generate the replacement JWT signing key using the approved local or platform key-generation procedure and mount it through the runtime secret file or secret-manager path.
2. Record the new secret identifier, for example `JWT_ACTIVE_KEY_ID=jwt_kid_2026_09`, and prepare the rotation manifest.
3. Pre-stage the new value in the target secret store without deleting the live key. Confirm the application can read the file or secret and that the key identifier is accurate.
4. Update the deployment config to expose the new key as `JWT_ACTIVE_KEY_ID` and keep the old key in `JWT_PREVIOUS_KEY_IDS` until the rollout is validated.
5. Roll out all application instances and verify that new tokens carry the new `kid` claim and the service can still verify tokens signed by the previous key.
6. Run the authentication smoke tests: login, refresh, logout, token reuse, and a TOTP-assisted login if MFA is enabled.
7. Keep the previous key available for at least seven days plus the permitted clock skew, then remove it only in a later approved rollout.
8. Archive the rotation evidence: deployment digest, authentication smoke-test results, old/new key IDs, and the approved change record.

Emergency compromise rotation may revoke sessions immediately instead of retaining the previous key. When a JWT signing key is suspected to be compromised, treat the previous key as invalid immediately and rotate to a replacement key before resuming normal traffic.

## TOTP key rotation and compatibility

TOTP encryption keys require the same controlled review and planned retirement path:

1. Generate a replacement AES-GCM key and mount it to the runtime secret path.
2. Verify the service can read the new key and that it is referenced by the active TOTP key identifier.
3. Re-encrypt existing stored TOTP secrets in a controlled migration or maintenance job when the key policy requires a strict rotation.
4. Keep the legacy key available only while previously encrypted data still needs verification.
5. Retire legacy AES-CBC or nested compatibility readers only after the stored data has been re-encrypted and the compatibility window is confirmed complete.
6. Record the migration evidence: row counts, verification results, the exact key IDs, and the cutover/retirement approval.

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

Retain audit events for 365 days. Invoke `api_schema.cleanup_audit_logs` with a cutoff of `clock_timestamp() - interval '365 days'` using only the maintenance identity; the application role must not receive cleanup or deletion privileges. Restrict read access to the `view_audit_log` permission, encrypt retained archives, and exclude audit records from general analytics exports. Monitor cleanup failures and periodically prove that retained events can be queried during an investigation.

## Account Recovery

Account recovery uses an administrator-issued, out-of-band recovery token. CAS has no public request-password-reset RPC, email identity store, or mail adapter. A support administrator with `manage_recovery` verifies the user's identity outside CAS, issues a short-lived single-use token through the protected administrative RPC, and delivers it through the approved support channel. Completion updates the password, revokes refresh sessions, and requires MFA re-enrollment. The authoritative implementation sequence and evidence requirements are in [SECURITY_IMPLEMENTATION_PLAN.md](SECURITY_IMPLEMENTATION_PLAN.md).

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