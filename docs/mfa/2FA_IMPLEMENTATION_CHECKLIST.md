# 2FA Implementation Checklist

This checklist reflects the current gRPC implementation. TOTP status is derived from `totp_secrets.verified_at`; there is no `users.totp_enabled` flag.

## Domain and application

- [x] Validated TOTP secret, code, and backup-code value objects
- [x] RFC 6238 TOTP generation and verification
- [x] Cryptographically secure backup-code generation
- [x] Setup, enable, verify, and disable commands, results, and handlers
- [x] Login challenge using a short-lived `aud: 2fa_verification` token
- [x] MFA enrollment policy and password-reset routing
- [x] Password re-authentication before disabling TOTP
- [x] Per-user rate limiting for OTP and backup-code attempts

## Database and adapters

- [x] Encrypted TOTP secret storage
- [x] Separate pending and active secret lookup
- [x] Atomic TOTP enable and disable operations
- [x] BCrypt-hashed, single-use backup codes
- [x] JOOQ status, verifier, and setup adapters
- [x] User credential lookup by user ID
- [x] Database integration tests for migration and adapter behavior

## gRPC delivery

- [x] `SetupTotp`
- [x] `EnableTotp`
- [x] `VerifyTotp`
- [x] `DisableTotp`
- [x] Login response branch for required 2FA
- [x] Error mapping through response `oneof` values
- [x] End-to-end live-server smoke test
- [x] TLS and optional mutual TLS configuration

## Security verification

- [x] Wrong, expired, or malformed verification tokens are rejected
- [x] Pending secrets cannot satisfy login-time verification
- [x] Backup codes are consumed once
- [x] TOTP checks allow only the configured bounded clock skew
- [x] Secrets and passwords use sensitive value-object handling
- [x] Unit and integration suites satisfy the 100% JaCoCo gate

## Operational follow-up

- [ ] Validate the GitHub Actions workflow on a clean runner
- [ ] Add security-event metrics and alerting
- [ ] Document account recovery and administrative reset procedures
- [ ] Load-test verification and rate-limiting behavior