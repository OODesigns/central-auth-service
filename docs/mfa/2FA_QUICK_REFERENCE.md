# 2FA Quick Reference

## Status

TOTP 2FA is implemented end to end over gRPC. Status is derived from `totp_secrets.verified_at`: `NULL` is pending and a timestamp is active.

## RPC flow

1. `SetupTotp` creates and stores a pending secret and returns an `otpauth://` URI.
2. `EnableTotp` verifies the first OTP against the pending secret, activates it, and returns one-time-visible backup codes.
3. `Login` verifies the password and returns a short-lived 2FA verification token for enrolled users.
4. `VerifyTotp` validates that token, rate-limits the user, verifies an OTP or consumes a backup code, and returns access and refresh tokens.
5. `DisableTotp` re-authenticates the user by password before deleting the secret and backup codes.

## Token separation

| Token | Audience | Lifetime | Purpose |
|---|---|---:|---|
| Access | none | 15 minutes | Authorized API access |
| Refresh | `refresh_token` | 7 days | Rotating session continuation |
| 2FA verification | `2fa_verification` | 5 minutes | Complete login challenge only |

## Security properties

- RFC 6238 HMAC-SHA1, six digits, 30-second period, bounded skew
- TOTP secrets encrypted before database storage
- Backup codes generated from a cryptographic RNG and stored as BCrypt hashes
- Backup-code consumption is atomic and single-use
- Pending secrets cannot satisfy login-time TOTP checks
- Per-user verification rate limiting
- Password re-authentication before TOTP disable
- TLS and optional mutual TLS at the gRPC server

## Key locations

- Domain ports: `src/main/java/com/oodesigns/cas/domain/service/Ports.java`
- Command handlers: `src/main/java/com/oodesigns/cas/application/command/`
- JOOQ adapters: `src/main/java/com/oodesigns/cas/infrastructure/adapter/`
- gRPC contract: `src/main/proto/auth.proto`
- Migrations: `.devcontainer/flyway/sql/`
- Database flow tests: `src/test/java/com/oodesigns/cas/integration/database/TotpDatabaseIntegrationTest.java`
- Live gRPC smoke test: `src/test/java/com/oodesigns/cas/integration/grpc/GrpcSmokeTest.java`

## Test commands

```bash
./gradlew test --tests '*Totp*'
./gradlew test integrationTest jacocoTestCoverageVerification
./gradlew databaseIntegrationTest -PincludeDbTests
```