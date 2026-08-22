# Project Status and Completion Plan

**Review date:** 2026-08-22
**Branch:** `master`
**Build state:** `./gradlew clean test integrationTest jacocoTestCoverageVerification` passes with 100% coverage.

## Current Status

The authentication service is runnable over gRPC and implements the primary authentication lifecycle:

- Password login with multi-key rate limiting
- TOTP enrollment, verification, backup codes, and disable flow
- MFA enrollment and password-reset routing during login
- Access and refresh token issuance
- Refresh-token rotation with family-based reuse detection
- Per-user rate limiting for 2FA verification
- Access-token logout and revocation
- TLS and optional mutual TLS for the gRPC server

The domain and application layers remain framework-free. Infrastructure concerns are implemented through ports in `Ports.java` and JOOQ, JWT, Bucket4j, and gRPC adapters.

## Completed Work

### Phase 0: Build and coverage

- [x] Unit and non-database integration tests pass.
- [x] JaCoCo enforces 100% line coverage.
- [x] Database and gRPC smoke test tiers exist.

### Phase 1: Application-layer 2FA

- [x] Login produces a restricted 2FA challenge token for enrolled users.
- [x] Required MFA enrollment and password-reset states are enforced.
- [x] Setup, enable, verify, and disable command handlers are implemented.
- [x] TOTP generation follows RFC 6238 with bounded clock skew.
- [x] Backup codes are generated securely, hashed, and consumed once.
- [x] Disable requires password re-authentication by user ID.

### Phase 2: Database and adapters

- [x] TOTP read/write API functions and JOOQ adapters are implemented.
- [x] User credential lookup by user ID is implemented.
- [x] Refresh-token storage and atomic rotation functions are implemented.
- [x] Pending and active TOTP secret lookups are separated.
- [x] Access-token invalidation and revocation lookup functions are implemented in `V1_4_8`.
- [x] Security-definer functions use fixed search paths, revoke `PUBLIC`, and grant only the API role.

### Phase 3: Delivery and runtime

- [x] gRPC API covers login, TOTP setup/enable/verify/disable, refresh, and logout.
- [x] `Main` wires production adapters and handlers.
- [x] TLS and optional client certificate validation are supported.
- [x] The live gRPC smoke test covers login, TOTP, refresh rotation/reuse detection, logout, and revoked-token rejection.

### Phase 4: Token lifecycle and hardening

- [x] Refresh-token rotation and family revocation on reuse.
- [x] Access-token logout and JTI-based revocation enforcement.
- [x] 2FA verification rate limiting.
- [x] Legacy 2FA status documentation replaced with current gRPC documentation.

## Logout and Revocation Design

`LogoutCommandHandler` first verifies that the presented credential is a signed, unexpired access token. Access tokens are distinguished from refresh and 2FA tokens by the absence of an `aud` claim.

On logout, `JooqAccessTokenRevocationStore` stores:

- The token's stable UUID `jti`
- A SHA-256 hash of the compact token, never the raw token
- The original expiry timestamp
- The revocation reason

`JwtTokenVerifier.verifyAccessToken` checks the revocation store after signature, expiry, token type, subject, and JTI validation. A revoked token returns an empty result. Expired revocation rows can be removed after their original expiry as routine database maintenance.

## Remaining Work

### CI verification

The GitHub Actions workflow exists, but it should be exercised on a clean runner and corrected as needed. In particular, database-backed login requires a valid `JWT_SECRET`, and the fresh PostgreSQL service must receive all Flyway migrations before database tests run.

### Operational readiness

- [ ] Define production secret provisioning and rotation procedures.
- [ ] Add monitoring and alerting for login failures, 2FA failures, token reuse, and revocations.
- [ ] Add scheduled cleanup for expired `invalidated_jwts` and retired refresh-token rows.
- [ ] Document backup, restore, incident response, and account recovery procedures.
- [ ] Load-test rate limits and database-backed token operations at expected production concurrency.

## Verification Commands

```bash
./gradlew clean test integrationTest jacocoTestCoverageVerification
./gradlew databaseIntegrationTest -PincludeDbTests
```

The database tier requires the compose PostgreSQL/Flyway environment and secrets that satisfy the domain's 32-character minimum. On 2026-08-22, all 12 database-tier tests passed against schema version `1.4.8`, including the full gRPC logout and revoked-token flow.

## Next Priority

Make CI reproducible on a clean runner, then complete operational readiness. No core authentication-flow implementation ticket remains open.