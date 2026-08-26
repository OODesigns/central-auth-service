# Security Deployment Guide

## Start Here

This is the short guide for deploying Central Auth Service safely. The security build work is complete. The remaining work is to run the checks, keep evidence, and operate the service safely.

Use this guide with [ADMIN_RECOVERY_RUNBOOK.md](ADMIN_RECOVERY_RUNBOOK.md) when deploying the account-recovery feature.

## What Has Already Been Fixed

| Security concern | What protects us now | Why it matters |
| --- | --- | --- |
| Stolen or malformed tokens | Central bearer-token interceptor checks signature, expiry, purpose, revocation, and current permissions. | Stops a bad or old token reaching protected actions. |
| Users changing another user's settings | The server takes identity from the verified token, not a request field. | A user cannot choose someone else's ID in a request. |
| Dangerous admin actions | Admin actions need an explicit permission and reload permissions from the database. | Removing a permission works quickly, even if an old token still exists. |
| Password exposure | Passwords use clearable character arrays and are cleared after use where Java allows it. | Reduces accidental retention in memory and logs. |
| Login guessing | Login and MFA attempts are rate limited. | Slows password guessing and repeated MFA guesses. |
| MFA replay | Used TOTP counters and backup codes are consumed atomically. | A valid code cannot be reused. |
| Token theft after recovery | Recovery changes the password, revokes refresh sessions, and requires MFA enrollment again. | A stolen old session cannot continue after recovery. |
| Public recovery abuse | There is no public reset-request RPC and no email dependency. Only an authorized administrator can issue a recovery token. | Avoids account enumeration and untrusted recovery delivery. |
| Database overreach | The application calls reviewed `api_schema` functions instead of reading private tables directly. | Limits what a compromised application credential can do. |
| Unsafe transport | TLS is required unless plaintext is explicitly allowed; mTLS can identify approved machine clients. | Protects data in transit and rejects unknown machine certificates. |
| Oversized gRPC requests | The server has message and metadata size limits. | Helps prevent memory and metadata abuse. |
| Unclear gRPC failures | Failures use canonical gRPC status plus `google.rpc.ErrorInfo`. | Clients can handle errors reliably without parsing text. |
| Missing security evidence | Metrics, bounded security events, audit records, image scans, and deployment checks are available. | Helps detect problems and investigate them later. |

## The Release Steps

Do these in order. Stop if a step fails.

1. **Prepare secrets and configuration.** Copy `.env.example` to `.env`. Replace every example secret. Do not commit `.env`.
2. **Start PostgreSQL and apply migrations.** Run `docker compose up -d db flyway`. Flyway creates schemas, roles, database functions, and the recovery-token tables.
3. **Run all tests.** Follow the database-test steps below.
4. **Run the internal security gate.** The approved runner supplies `RELEASE_IMAGE_DIGEST` and `DEPLOYMENT_APPROVAL_ID`, then runs `ops/internal-security-gate.sh`.
5. **Deploy by image digest.** Do not deploy a mutable image tag such as `latest`.
6. **Check TLS, health, login, MFA, refresh, logout, and recovery.** Use [ADMIN_RECOVERY_RUNBOOK.md](ADMIN_RECOVERY_RUNBOOK.md) for recovery checks.
7. **Keep the evidence.** Save Flyway output, test results, scan results, image digest, approval ID, and smoke-test result with the release record.

## Database Tests: How the Environment Is Set

There are two kinds of database test.

### 1. Compose-backed tests

These use the local PostgreSQL container created by `compose.yml`.

1. Create `.env` from `.env.example`.
2. Set at least `POSTGRES_USER`, `POSTGRES_PASSWORD`, `API_USER`, `API_PASSWORD`, `APP_DB`, `ADMIN_PASSWORD_HASH`, `JWT_SECRET`, and `TOTP_ENCRYPTION_KEY`.
3. Start the database and migrations:

```bash
docker compose up -d db flyway
```

The database listens on `127.0.0.1:5432` by default. Test configuration uses these defaults:

```text
DB_HOST=localhost
DB_PORT=5432
APP_DB=auth_db
DB_USER=app_user
APP_PASSWORD=<the same API_PASSWORD from .env>
```

Override a value only when your local setup differs. For example:

```bash
export DB_PORT=55432
export APP_PASSWORD='your local application password'
```

### 2. Testcontainers test

One adapter test starts its own temporary PostgreSQL container. It needs Docker and `RUN_DATABASE_TESTS=true`. It does not use your Compose database.

### Run every database test

Use this command after Compose is healthy:

```bash
RUN_DATABASE_TESTS=true ./gradlew databaseIntegrationTest -PincludeDbTests
```

`-PincludeDbTests` tells Gradle to run tests tagged `database`. `RUN_DATABASE_TESTS=true` also enables the Testcontainers-backed adapter test. Both are needed for the complete database-test set.

### Easiest local option

Use the disposable helper when you do not already have a `.env` file. It generates temporary random credentials, creates an isolated PostgreSQL and Flyway environment, runs the database tests, and removes the containers, volumes, and temporary credential file afterward:

```bash
./scripts/run-database-tests.sh
```

It requires a running Docker daemon and Docker Compose. Set `KEEP_DB_TEST_ENV=true` only when investigating a failing test; the script then prints the temporary environment-file path and leaves the containers running.

## Regular Test Commands

```bash
./gradlew test
./gradlew integrationTest
RUN_DATABASE_TESTS=true ./gradlew databaseIntegrationTest -PincludeDbTests
./gradlew jacocoTestCoverageVerification
```

The first two do not need database credentials. `scripts/security-check.sh` deliberately removes database environment variables for those non-database tests, so local database settings cannot affect them.

## Things That Must Keep Happening

These are operational jobs, not missing code:

- Rotate JWT signing keys every 90 days and TOTP encryption keys every 180 days.
- Run audit and rate-limit cleanup using the separate maintenance identity.
- Check certificate expiry and test TLS renewal.
- Test a database backup restore on a schedule.
- Enforce gRPC deadlines in clients or the trusted ingress.
- Restrict who can read logs, traces, audits, and secrets.
- Use the administrator recovery process only after identity verification; never put a recovery token in normal ticket text or logs.

## Simple Rule

When in doubt: stop the release, keep the evidence, and ask Security or the release owner. A failed security check is a reason to investigate, not a setting to bypass.