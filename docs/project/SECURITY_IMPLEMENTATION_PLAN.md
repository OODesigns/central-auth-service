# Security Implementation Plan

This document records the remaining security implementation work, the completed repo changes, and the external decisions required before account recovery can be exposed.

## Completed in this repository

### Structured security events

`GrpcSecurityEventInterceptor` is registered by `Main` for every gRPC request. It generates or accepts a restricted correlation ID and writes JSON security events containing only:

- event name
- full gRPC method name
- gRPC status
- success or failure category
- deployment environment
- correlation ID

It does not inspect request values, so plaintext passwords, bearer tokens, reset material, TOTP codes, and backup codes cannot enter these events through the interceptor. Container/runtime logging must collect stdout/stderr or the Java logging sink, enforce access control, and retain or delete event data according to the approved policy.

The existing Prometheus/Grafana Compose profile remains the metrics and alerts stack. Trace collection requires the deployment platform to provide an approved OpenTelemetry collector endpoint before an OTLP exporter is configured; this repository deliberately does not guess an endpoint or credential.

### Production gate ownership

GitHub-hosted automation is manual-only and is not the production approval boundary. The provider-neutral `ops/internal-security-gate.sh` script is the command entry point for an approved internal runner. It requires:

- `RELEASE_IMAGE_DIGEST` for the immutable artifact being promoted
- `DEPLOYMENT_APPROVAL_ID` for the approved change record
- database integration test access and the scanning tools required by `security-check.sh`

The internal runner must execute that script before deployment and preserve its scan, test, migration, TLS, image-digest, and approval evidence.

## Account recovery implementation sequence

Account recovery uses an administrator-issued recovery token. CAS does not expose a public request-password-reset RPC and does not depend on an email provider. An authorized administrator delivers the one-time token through an organization-approved out-of-band support channel. The repository now implements the token, storage, protected issuance RPC, and public completion RPC; support-process approval and production database migration remain deployment prerequisites.

1. Completed: `RecoveryToken`, reset-purpose claims, short expiration, JTI, hash-only persistence, and atomic single-use consumption.
2. Completed: database API functions issue and consume recovery tokens, update passwords, revoke refresh-token families, and require MFA re-enrollment.
3. Completed: ports exist for recovery-token storage, privileged issuance, and session revocation. No mail or verified-email port is needed.
4. Completed: `IssueRecoveryToken` requires the current `manage_recovery` permission, invalidates previous unused tokens, and shows the new token only once to the administrator.
5. Completed: `CompleteRecovery` accepts only the reset-purpose token and a new password, with one public failure outcome for invalid, expired, and consumed tokens.
6. Completed: `IssueRecoveryToken` is protected and `CompleteRecovery` is the only public recovery RPC in `auth.proto`; `GrpcAuthInterceptor` enforces access-token and permission requirements.
7. In progress: complete unit and database integration coverage for authorization, token expiry/replay/concurrent consumption, password update, refresh-family revocation, MFA re-enrollment, and audit attribution.
8. Required before production: internal-runner smoke tests must cover issuance and completion, and the support team must approve the out-of-band identity-verification and token-delivery procedure.

## Acceptance criteria

Account recovery is complete only when all of the following evidence exists:

- Only an administrator with `manage_recovery` can issue recovery material, after the support process verifies the target user's identity out of band.
- No public request-password-reset RPC or email delivery adapter exists.
- Recovery tokens have a reset-only purpose, expire quickly, are stored only as hashes, and are atomically single-use.
- Reset completion changes the password and invalidates active refresh sessions.
- Audit records contain event metadata but no recovery token.
- The approved support procedure records identity verification and token delivery outside CAS without storing the recovery token in general-purpose ticket text.
- The internal security gate and recovery smoke tests passed for the exact deployed image digest.
