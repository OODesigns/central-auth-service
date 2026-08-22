# 2FA Implementation Summary

The service implements authenticator-app TOTP using hexagonal architecture and a gRPC delivery adapter.

## Architecture

- The domain contains validated values, TOTP/backup-code generation, token services, and port contracts.
- Application command handlers orchestrate setup, enable, login challenge verification, and disable flows.
- Infrastructure adapters implement encrypted secret storage, BCrypt backup-code verification, JOOQ database access, JWT verification, rate limiting, and gRPC transport.
- PostgreSQL functions expose a restricted `api_schema`; application credentials do not access `private_schema` tables directly.

## Enrollment and login

Enrollment stores a pending encrypted secret. The first OTP is verified against that pending secret before activation. Login-time verification reads active secrets only, preventing incomplete enrollment from becoming an authentication factor.

An enrolled user who passes password authentication receives a five-minute token with `aud: 2fa_verification`. `VerifyTotpCommandHandler` validates that token, applies per-user rate limiting, verifies an OTP or consumes a backup code, loads permissions, and issues the normal access/refresh pair.

## Recovery and disable

Backup codes are generated once, returned as validated values, stored only as BCrypt hashes, and consumed atomically. Disabling TOTP requires password re-authentication using a credential lookup keyed by server-validated user ID.

## Verification

Unit tests cover handlers, domain services, result types, and adapters. Database tests exercise migration functions and TOTP round trips against PostgreSQL. `GrpcSmokeTest` exercises the live gRPC flow. JaCoCo enforces 100% line coverage.

See [PROJECT_STATUS_AND_COMPLETION_PLAN.md](../project/PROJECT_STATUS_AND_COMPLETION_PLAN.md) for current remaining work.