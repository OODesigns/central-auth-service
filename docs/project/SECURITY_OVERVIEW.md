# Current-State Technical Security Overview

## Purpose and evidence standard

This document explains how Central Auth Service is hardened today for engineers, security reviewers, and operators. It describes controls that are present in the current Java code, tests, configuration, container definition, and Flyway migrations. It also identifies boundaries where security depends on deployment controls or where enforcement is not yet implemented.

The implementation and tests are authoritative. This overview does not claim that sensitive values never enter immutable JVM objects, that every RPC is authenticated, or that operational controls are automatic when the repository only provides a script or runbook.

## Current posture

| Area | Current state |
| --- | --- |
| Password handling | Plaintext passwords are moved into clearable `char[]` value objects at the service boundary, cloned defensively, masked in logs, and cleared through `AutoCloseable`. Protobuf and BCrypt still impose short-lived immutable `String` boundaries. |
| Authentication | Login rate limiting runs before credential lookup; missing users and bad passwords share the same public outcome; BCrypt verifies stored password hashes. |
| Rate-limit identity | Login rate limiting uses the gRPC transport peer address; the client-supplied `ip_address` field is retained only for wire compatibility and is not trusted. |
| MFA | TOTP secrets are encrypted, pending enrollment is separated from active state, accepted counters prevent replay, backup codes are hashed and single-use, and verification is rate-limited. |
| Tokens | Access, refresh, and 2FA tokens have separate purposes and lifetimes. Refresh tokens rotate atomically, reuse revokes the family, and access-token JTIs can be revoked. |
| Database | Application access is constrained to `api_schema` functions over `private_schema` data, with `SECURITY DEFINER`, locked `search_path`, explicit ownership, and restricted grants. |
| Transport and runtime | TLS is fail-closed unless plaintext is explicitly enabled. The production container runs as a non-root user with a read-only filesystem and reduced Linux privileges. |
| Supply chain | Dependencies are locked; the local security gate runs tests, coverage, OSV, container build, and Trivy scanning. An approved internal runner must enforce that gate. |
| Principal enforcement | A gRPC interceptor now verifies bearer tokens for protected RPCs, binds the principal to request context, and restricts MFA management to the authenticated user. Required-MFA bootstrap uses a short-lived enrollment token. Role-based authorization remains limited. |

## Trust boundaries

The service follows hexagonal architecture: domain and application code depend on ports, while infrastructure adapters handle gRPC, PostgreSQL, BCrypt, JWT, encryption, and rate limiting. The principal boundaries are:

1. The gRPC boundary converts wire values into validated domain values.
2. Command handlers order security checks and decide which result can leave the application layer.
3. Infrastructure adapters perform cryptography and external I/O behind domain ports.
4. PostgreSQL exposes approved functions rather than direct application table access.
5. The deployment platform supplies TLS material, database credentials, signing keys, encryption keys, and pipeline enforcement.

When a trusted proxy or sidecar terminates the client connection, it must preserve the original peer address through a trusted transport boundary. The service does not trust arbitrary forwarded-IP metadata; otherwise the rate limiter sees the proxy address and cannot distinguish clients.

Login, refresh, and TOTP challenge exchange remain credential-bearing public entry points; protected management methods require the interceptor's verified bearer or enrollment token. Network reachability, TLS client authentication, and upstream authorization must not be mistaken for application-level principal authorization unless the deployment explicitly provides and verifies them.

## Sensitive data in Java

### Passwords use clearable character arrays

[Password](../../src/main/java/com/oodesigns/cas/domain/value/Password.java) owns a private `char[]` rather than retaining a plaintext `String`:

- `Password.of(char[])` validates and clones the caller's array, so later caller mutation cannot change the value object.
- `chars()` returns another clone rather than exposing internal storage.
- `close()` and `clear()` overwrite owned characters with null characters.
- `toString()` returns `Password{***}` and cannot reveal the secret accidentally.
- Password length is constrained to 14 through 128 characters, and whitespace-only values are rejected without imposing composition rules.

[Credentials](../../src/main/java/com/oodesigns/cas/domain/value/Credentials.java) groups the stored credential record with the supplied password and implements `AutoCloseable`. [AuthenticationService](../../src/main/java/com/oodesigns/cas/domain/service/AuthenticationService.java) uses try-with-resources so password cleanup occurs on success, mismatch, or exception.

At the gRPC boundary, [AuthGrpcService](../../src/main/java/com/oodesigns/cas/infrastructure/grpc/AuthGrpcService.java) converts the protobuf password into a temporary `char[]`, creates the domain password, and clears the temporary array in a `finally` block.

### Signing and keystore passwords

[KeyPassword](../../src/main/java/com/oodesigns/cas/domain/value/KeyPassword.java) extends the same clearable password model and requires at least 32 characters for HS256 key material. UTF-8 conversion clears its temporary character array and encoder buffer. Callers receiving the returned byte array are responsible for clearing it after cryptographic initialization.

### Usernames are not treated as secrets

[Username](../../src/main/java/com/oodesigns/cas/domain/value/Username.java) remains a validated, normalized `String`. This is deliberate: a username is a stable identity and lookup key, not a clearable authentication secret. Converting usernames to `char[]` would add lifecycle complexity without removing their necessary presence in requests, indexes, logs, and database lookups.

The security distinction is therefore:

- Plaintext passwords and key passwords use clearable arrays wherever the local API permits.
- Usernames, token identifiers, stored password hashes, and other non-plaintext identity data use immutable value objects and validation.

### Immutable-string boundaries remain

Java cannot guarantee complete erasure of secrets from process memory. Current unavoidable or compatibility boundaries include:

- Protobuf exposes the incoming password as an immutable Java `String` before conversion.
- [BcryptPasswordVerifier](../../src/main/java/com/oodesigns/cas/infrastructure/adapter/BcryptPasswordVerifier.java) must create a temporary `String` because Spring Security's `PasswordEncoder` API accepts `CharSequence`.
- `Password.of(String)` and `KeyPassword.of(String)` remain available primarily for tests and compatibility; production call sites should prefer `char[]`.
- JWTs, environment variables, JDBC configuration, Base32 TOTP material, and one-time backup codes cross APIs that use strings or bytes.

JWT HMAC conversion and access/refresh-token hashing explicitly clear their temporary byte arrays after each operation. TOTP encryption already clears its temporary buffers. Clearing arrays reduces exposure duration and prevents common accidental retention. It does not prove that garbage-collected memory, copied library buffers, heap dumps, crash dumps, swap, or debugger access contain no secret material. Production hardening should also restrict diagnostics, heap dumps, process inspection, and host access.

## Login and password verification

[LoginCommandHandler](../../src/main/java/com/oodesigns/cas/application/command/LoginCommandHandler.java) applies controls in security-sensitive order:

1. Derive the peer IP from the gRPC transport, then check IP, username, and combined IP-plus-username rate limits.
2. Retrieve the minimal stored credential record.
3. Verify the supplied password with BCrypt.
4. Load the full user only after password verification.
5. Evaluate MFA enrollment and policy.
6. Evaluate password-reset requirements.
7. Issue full tokens only after all applicable checks pass.

Absent credentials and password mismatches both return `INVALID_CREDENTIALS`, reducing account-enumeration detail in the public result. [BcryptPasswordVerifier](../../src/main/java/com/oodesigns/cas/infrastructure/adapter/BcryptPasswordVerifier.java) suppresses malformed-hash detail and returns an empty result instead of exposing verification internals.

The service identifies `PASSWORD_RESET_REQUIRED`, but [auth.proto](../../src/main/proto/auth.proto) does not define recovery RPCs or reset-scoped tokens. The approved future model is administrator-issued recovery: no public request-password-reset RPC and no email delivery dependency. A complete recovery workflow is outside the current service contract and must not be inferred from the login outcome.

## MFA and TOTP

### Enrollment and secret protection

[JooqTotpSetupProvider](../../src/main/java/com/oodesigns/cas/infrastructure/adapter/JooqTotpSetupProvider.java) generates TOTP secrets from `SecureRandom`. [TotpSecretCipher](../../src/main/java/com/oodesigns/cas/infrastructure/adapter/TotpSecretCipher.java) writes versioned AES-GCM envelopes, providing confidentiality and integrity for new secrets. Legacy untagged AES-CBC values remain readable for compatibility and should be retired through an operational re-encryption plan.

Pending and active secret retrieval are separate. A pending enrollment secret cannot satisfy login-time verification. Activation requires a valid setup code before `verified_at` is recorded and backup codes are returned for one-time display.

### Verification and replay resistance

[TotpCodeGenerator](../../src/main/java/com/oodesigns/cas/domain/service/TotpCodeGenerator.java) implements RFC 6238 verification with a bounded plus-or-minus-one time-step window and constant-time code comparison. The database atomically advances `last_accepted_counter`, so an accepted TOTP counter cannot be reused. See [V1_5_0__prevent_totp_replay.sql](../../.devcontainer/flyway/sql/V1_5_0__prevent_totp_replay.sql).

Backup codes are generated from a cryptographic random source, stored as BCrypt hashes, replaced as a batch, and consumed atomically once. [JooqTotpVerifierTest](../../src/test/java/com/oodesigns/cas/infrastructure/adapter/JooqTotpVerifierTest.java) covers replay and concurrent backup-code consumption behavior.

TOTP verification is rate-limited per user. Disabling TOTP requires password reauthentication before the secret and backup codes are removed.

### MFA authorization

`GrpcAuthInterceptor` requires an access token for `DisableTotp` and `Logout`, and accepts either an access token or the dedicated short-lived MFA-enrollment token for `SetupTotp` and `EnableTotp`. [AuthGrpcService](../../src/main/java/com/oodesigns/cas/infrastructure/grpc/AuthGrpcService.java) checks that any supplied user identifier matches the verified principal. The enrollment token is issued only after password authentication when policy requires MFA but no active secret exists.

The interceptor provides principal binding. For `DisableTotp` and `AdminDisableTotp`, it reloads current permissions from the user repository before dispatch; unavailable authorization state fails closed. `USER_REQUESTED` is restricted to the caller's own account; cross-user actions and `ADMIN_FORCED`, `SECURITY_INCIDENT`, and `RECOVERY_FLOW` require the database-seeded `manage_mfa` permission.

## Tokens and sessions

[TokenService](../../src/main/java/com/oodesigns/cas/domain/service/TokenService.java) separates token purposes:

| Token | Audience | Lifetime | Intended use |
| --- | --- | ---: | --- |
| Access | `access_token` | 15 minutes | Authorized API access |
| Refresh | `refresh_token` | 7 days | Rotating session continuation |
| 2FA verification | `2fa_verification` | 5 minutes | Complete the MFA login challenge only |

Version 2 tokens include subject, audience where applicable, JTI, issued-at, expiration, and a `ver: 2` marker. [JwtTokenVerifier](../../src/main/java/com/oodesigns/cas/infrastructure/adapter/JwtTokenVerifier.java) validates signatures, token purpose, expiry, and access-token revocation state.

The active signing key is used for issuance, while an allowlisted active-plus-previous key set supports manual rotation. Current limitations are:

- HS256 uses shared symmetric secrets; compromise of a verification key permits signing.
- Legacy tokens may have no issuer claim during the controlled migration window.KEEP_DB_TEST_ENV=true ./scripts/run-database-tests.sh New version-2 tokens use and require issuer `central-auth-service`; retire legacy verification after the maximum token lifetime and revoke remaining legacy sessions where possible.
- Verification tries allowed keys rather than selecting by `kid`.
- Key distribution, retirement, and emergency rotation are operational procedures rather than an integrated key-management service.

Refresh tokens are stored as SHA-256 hashes, rotated atomically under database locking, and organized into token families. Reuse detection revokes the family. Logout records the access-token JTI as revoked.

`GrpcAuthInterceptor` is the central access-token guard for protected gRPC methods. It rejects missing, malformed, invalid, expired, wrong-purpose, and revoked bearer tokens before dispatch, and places the verified user ID in request context. Refresh and TOTP challenge exchange intentionally use their own body tokens and remain public entry points.

## Rate limiting

Login uses independent IP, normalized-username, and combined-key limits. [DatabaseLoginRateLimiter](../../src/main/java/com/oodesigns/cas/infrastructure/adapter/DatabaseLoginRateLimiter.java) delegates to an atomic PostgreSQL function, allowing limits to be shared across service instances. Database failures deny the login attempt rather than silently bypassing the limiter.

TOTP verification defaults to the distributed PostgreSQL limiter in [DatabaseTotpRateLimiter](../../src/main/java/com/oodesigns/cas/infrastructure/adapter/DatabaseTotpRateLimiter.java), using the same atomic fixed-window function as login. The process-local [TotpRateLimiter](../../src/main/java/com/oodesigns/cas/infrastructure/adapter/TotpRateLimiter.java) remains available only through explicit `TOTP_RATE_LIMIT_BACKEND=memory` configuration for local development and is bounded at 100,000 tracked keys with expiry eviction. Database rate-limit rows have no independent hard cardinality cap or cleanup worker and require scheduled retention cleanup. Capacity limits, retention, and abuse monitoring remain operational concerns.

## Database isolation

Flyway migrations establish two database trust zones:

- `private_schema` owns authentication tables and internal state.
- `api_schema` exposes approved operations as functions.

The application role is non-superuser and receives function execution rather than direct table privileges. Security-definer functions lock `search_path` to trusted schemas, revoke default `PUBLIC` execution, grant only the application role, and are owned by a non-login owner role. [V1_0_1__create_roles.sql](../../.devcontainer/flyway/sql/V1_0_1__create_roles.sql) establishes the role model; later API migrations repeat explicit revoke/grant ownership controls.

Flyway provides ordered, checksummed, single-application migration history. Foundational migrations are intended to run once through Flyway and are not all independently idempotent; plain `CREATE SCHEMA`, `CREATE TABLE`, and `CREATE INDEX` statements remain. Production safety therefore depends on immutable applied migrations, `flyway validate`, backups, a dedicated migration identity, and reviewed forward migrations as described in [SECURITY_ROLLOUT.md](SECURITY_ROLLOUT.md).

## Transport, runtime, and secrets

[GrpcTlsConfigurer](../../src/main/java/com/oodesigns/cas/infrastructure/grpc/GrpcTlsConfigurer.java) fails startup when TLS material is absent unless plaintext is explicitly enabled. Configuring a truststore enables client-certificate authentication. The service now explicitly allows TLS 1.3 and TLS 1.2 with AEAD cipher suites rather than relying on Netty's default protocol/cipher selection.

mTLS verifies trust chains and now maps the peer certificate's SHA-256 DER fingerprint to an active machine-to-machine terminal/service record through the API schema when `REQUIRE_MACHINE_CLIENT=true`. Unknown, expired, or revoked certificates are rejected before dispatch. Certificates identify devices or services, not human users; they do not replace bearer authentication for user-scoped protected RPCs. The explicit machine-client policy currently applies to every RPC when enabled.

[Dockerfile](../../Dockerfile) uses a Java 25 Alpine runtime and a non-root application user. [compose.yml](../../compose.yml) defaults to TLS, uses a read-only application filesystem, mounts a temporary filesystem for `/tmp`, and sets `no-new-privileges`.

Database credentials, JWT signing keys, TOTP encryption keys, and keystore/truststore passwords are supplied at runtime. Linux-local deployments can use the file-backed `KeySupplier` with `SECRETS_BACKEND=file` and a read-only `SECRETS_DIRECTORY` mount such as `/run/secrets`; filenames are key IDs. Environment-backed values remain the compatibility default and are visible to sufficiently privileged process and host inspection. Rotation scheduling and local key-generation controls remain operational responsibilities.

## Supply chain and verification

The application exposes OpenTelemetry metrics for gRPC calls and authentication events at the configured Prometheus endpoint. Metric labels are limited to RPC method, gRPC status, result category, and deployment environment; usernames, user IDs, IP addresses, certificate fingerprints, tokens, and error messages are not labels. Prometheus and Grafana are separate optional Compose services with persistent volumes and checked-in provisioning. The maintained unit, integration, and database integration test tiers pass, and the configured JaCoCo class line-coverage gate is verified at 100%.

[gradle.lockfile](../../gradle.lockfile) pins resolved dependency versions. [build.gradle](../../build.gradle) enforces JaCoCo line coverage at 100 percent, excluding designated generated or bootstrap classes.

[scripts/security-check.sh](../../scripts/security-check.sh) provides the local security gate:

1. Clean unit and integration tests.
2. JaCoCo coverage verification.
3. Dependency resolution and lock verification.
4. OSV dependency scanning.
5. Docker image build.
6. Trivy rejection of HIGH or CRITICAL image findings.
7. Optional database integration tests when the PostgreSQL stack is available.

The repository now includes a GitHub security workflow and the checked-in gate. Production must invoke the gate from its approved pipeline, preserve scan evidence and approvals, and deploy the exact scanned image digest.

## Audit and privacy

Database triggers record security-relevant changes. TOTP write operations execute in a JOOQ transaction that sets authenticated-user and machine-client context with transaction-local PostgreSQL settings before the mutation; a database-backed gRPC test verifies that `TOTP_DISABLED` records the authenticated actor. Other audited write paths still need the same integration before actor attribution can be considered complete service-wide.

Before `V1_5_2`, some audit triggers serialized complete database records, which could include password hashes or token hashes. `V1_5_2` replaces those payloads with allowlisted metadata and emits a reasoned TOTP-disable event on deletion. Audit storage must still be treated as sensitive, access-controlled, retained for a defined period, and excluded from general application logging and analytics exports.

The current TOTP disable function deletes the row and `V1_5_2` adds a DELETE-capable trigger that records `TOTP_DISABLED` with the supplied reason. Audit context uses transaction-local PostgreSQL settings, never pooled session state. Audit events are retained for 365 days and cleanup must run only through the separate maintenance identity.

Application logs must not contain plaintext passwords, TOTP secrets, backup codes, JWT signing keys, complete tokens, email addresses, or recovery tokens. Masked value objects reduce accidental disclosure, but a structured-log backend and trace exporter have not yet been configured. Migration `V1_5_6` adds bounded cleanup functions for expired rate-limit rows and audit retention.

## Priority residual risks

The following table records the status of the findings from the original security review. Resolved items are implemented in the current source and migrations; residual items require additional authorization policy or deployment/operations work.

| Priority | Risk | Required direction |
| --- | --- | --- |
| Resolved | Protected MFA/logout RPCs lacked principal binding and central token enforcement. | `GrpcAuthInterceptor` now enforces bearer/enrollment token policy, user-ID matching, and current repository-backed `manage_mfa` permission checks for MFA-sensitive RPCs. |
| Resolved | Malformed bearer tokens could escape value-object construction before transport rejection. | The interceptor now maps malformed access and enrollment tokens to `UNAUTHENTICATED`; regression coverage protects this boundary. |
| Resolved | gRPC had no explicit inbound payload bounds. | The server defaults to 1 MiB messages and 16 KiB metadata; review these limits against deployed clients and enforce deadlines at clients or ingress. |
| Resolved | Business, authentication, authorization, and validation failures used successful gRPC responses instead of canonical statuses. | The existing service now returns canonical statuses with `google.rpc.Status` and `ErrorInfo`; the legacy response `Error` fields remain only for protobuf source compatibility. |
| Medium | Request deadlines are not enforced server-side; health/reflection exposure depends on configuration. | Require client or ingress deadlines, keep the configured connection limits, use the standard health service, and keep reflection disabled in production unless explicitly authorized. |
| Medium | Audit actor context is implemented only for TOTP writes; retention scheduling and service-wide coverage remain operational work. | Apply transaction-local context to every audited write, schedule 365-day maintenance cleanup, and test attributable events and redaction. |
| Medium | Database rate-limit storage still depends on scheduled maintenance for cleanup and hard capacity monitoring. | `V1_5_6` provides bounded cleanup functions and `scripts/cleanup-rate-limits.sh` uses a separate maintenance identity; schedule it, enforce storage bounds, and monitor growth. |
| Medium | JWT and TOTP key rotation is manual; legacy CBC TOTP values remain readable. | Define automated key lifecycle, previous-key retirement, emergency rotation, and re-encryption procedures. |
| Resolved with limits | mTLS certificates were not mapped to application principals; certificate revocation was not integrated. | SHA-256 certificate fingerprints now resolve active machine-client records and reject unknown, expired, or revoked certificates. PKI issuance, revocation distribution, and per-RPC policy remain deployment responsibilities. |
| Low | Plaintext secrets cross immutable library/API boundaries. | Keep boundaries short, restrict diagnostics and host access, and prefer clearable APIs when dependencies permit. |

## Reviewer verification

The remaining follow-up work is tracked in [SECURITY_TODO.md](SECURITY_TODO.md). Canonical gRPC status migration is complete for the current service contract; failures now use transport statuses with standard `google.rpc.Status` details.

Use these checks when reviewing a release:

```bash
./gradlew clean test integrationTest jacocoTestCoverageVerification
./gradlew databaseIntegrationTest -PincludeDbTests
./scripts/security-check.sh
```

Reviewers should also verify:

- Production has `ALLOW_PLAINTEXT=false`, or plaintext is reachable only behind an independently verified TLS boundary.
- MFA management RPCs are not exposed to untrusted callers without compensating authorization.
- The active and previous JWT key set matches the approved rotation state.
- TOTP encryption keys and legacy-cipher migration are documented.
- Flyway validation succeeds against a restored production copy before deployment.
- Audit access, retention, redaction, and actor attribution meet organizational policy.
- The deployed image digest is the same image that passed OSV, Trivy, tests, and approval.

## Evidence map

- Service contract: [auth.proto](../../src/main/proto/auth.proto)
- Login orchestration: [LoginCommandHandler](../../src/main/java/com/oodesigns/cas/application/command/LoginCommandHandler.java)
- Sensitive values: [Password](../../src/main/java/com/oodesigns/cas/domain/value/Password.java), [Credentials](../../src/main/java/com/oodesigns/cas/domain/value/Credentials.java), [KeyPassword](../../src/main/java/com/oodesigns/cas/domain/value/KeyPassword.java)
- MFA implementation: [TotpSecretCipher](../../src/main/java/com/oodesigns/cas/infrastructure/adapter/TotpSecretCipher.java), [JooqTotpVerifier](../../src/main/java/com/oodesigns/cas/infrastructure/adapter/JooqTotpVerifier.java)
- Token implementation: [TokenService](../../src/main/java/com/oodesigns/cas/domain/service/TokenService.java), [JwtTokenVerifier](../../src/main/java/com/oodesigns/cas/infrastructure/adapter/JwtTokenVerifier.java), [JooqRefreshTokenStore](../../src/main/java/com/oodesigns/cas/infrastructure/adapter/JooqRefreshTokenStore.java)
- Database controls: [Flyway migrations](../../.devcontainer/flyway/sql/)
- Runtime controls: [GrpcTlsConfigurer](../../src/main/java/com/oodesigns/cas/infrastructure/grpc/GrpcTlsConfigurer.java), [Dockerfile](../../Dockerfile), [compose.yml](../../compose.yml)
- Security gate: [security-check.sh](../../scripts/security-check.sh)
- Production operations: [SECURITY_ROLLOUT.md](SECURITY_ROLLOUT.md)
