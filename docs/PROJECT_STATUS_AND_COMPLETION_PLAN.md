# Project Status & Completion Plan

**Date of review:** 2026-08-09 (re-verified against a live build — coverage failures below are actual gradle output)
**Branch:** master (at `bb9ec40` "WIP - Removed M:M Role")
**Build state:** compiles; unit + integration tests pass; `jacocoTestCoverageVerification` **FAILS**

---

## Development environment (devcontainer)

The `.devcontainer/` provides a **complete runnable stack** — nothing needs to be provisioned:

| Service | Container | Notes |
|---|---|---|
| `app` | `auth-service` | Java dev container, project mounted at `/workspaces`; env vars pre-wired: `DATABASE_URL` (`jdbc:postgresql://db:5432/…`), `JWT_SECRET`, `KEYSTORE_PASSWORD`, `TRUSTSTORE_PASSWORD` |
| `db` | `auth-db` | Postgres 15, persisted volume, healthcheck, **exposed on host port 5432** (so `databaseIntegrationTest` also works from outside the container) |
| `flyway` | `flyway-migrations` | Runs `migrate` automatically once `db` is healthy; mounts `.devcontainer/flyway/sql/` and `flyway.conf`; placeholders fed from env (`API_USER`, `API_PASSWORD`, `ADMIN_PASSWORD_HASH`, …) |

Implications for the plan:

- **New migrations apply automatically** — drop `V1_4_x__*.sql` into `.devcontainer/flyway/sql/` and re-run the `flyway` service (`docker compose up flyway`).
- **Database tests need no extra setup**: `./gradlew databaseIntegrationTest -PincludeDbTests` targets the compose `db` on `localhost:5432`.
- **Phase 3 wiring is already half-done**: the `app` service exposes the env vars `Main` will need (`DATABASE_URL`, `JWT_SECRET`, keystore/truststore passwords).
- Required env vars come from the compose `.env` (see `CLAUDE.md` list).

---

## Where the project stands

### Done and solid

- **Domain layer complete:** value objects (including `SecretFor2FA`, `BackupCode`), all port
  interfaces in `Ports.java` (`TotpVerifier`, `TotpSetupProvider`, `TotpStatusReader`),
  `AuthenticationService`, `TokenService` (including the 5-minute 2FA verification token,
  `aud: 2fa_verification`).
- **Login flow works** through `LoginCommandHandler` with rate limiting and the 2FA branch,
  proven by mock integration tests (`LoginMockIntegrationTest`, `AdminLoginMockIntegrationTest`).
- **Database schema fully migrated** (10 migrations, `V1_0_0` … `V1_3_0` in
  `.devcontainer/flyway/sql/`): `totp_secrets`, `backup_codes`, `refresh_tokens`,
  `invalidated_jwts`, audit triggers, and API functions `find_user_credentials`, `get_user`,
  `get_totp_status`, `encrypt_totp_secret`.
- **Pre-2FA adapters exist (6 of 9 ports implemented):**

  | Port | Adapter | Status |
  |---|---|---|
  | `PasswordVerifier` | `BcryptPasswordVerifier` | ✅ |
  | `TokenSigner` | `JwtTokenSigner` | ✅ |
  | `Clock` | `SystemClock` | ✅ |
  | `RateLimiter` | `LoginRateLimiter` (Bucket4j) | ✅ |
  | `UserCredentialRetriever` | `UserCredentialReader` (JOOQ) | ✅ |
  | `UserRetriever` | `UserRepository` (JOOQ) | ✅ |
  | `TotpStatusReader` | — | ❌ none |
  | `TotpVerifier` | — | ❌ none |
  | `TotpSetupProvider` | — | ❌ none |

### Broken right now

- **Coverage gate fails (build red).** Verified 2026-08-09; exact violations:
  - 0% coverage: `DisableTotpCommand`, `DisableTotpCommandHandler`, `DisableReason`,
    `DisableTotpResult` (+ `SuccessResult`, `FailureResult`, `MapperSuccess`, `MapperFailure`),
    `LoginResult.Required2FAResult` (+ `Mapper2FARequired`),
    `LoginResult.PasswordResetRequiredResult` (+ `MapperPasswordResetRequired`)
  - Partial: `LoginResult` (50%), `TokenService` (75%), `LoginRateLimiter` (88%),
    `LoginCommandHandler` (96%)
- **Security gap (acknowledged in code):** `DisableTotpCommandHandler.verifyPasswordForDisable()`
  is a stub that accepts any non-blank password. Root cause: it has a `userId` but
  `UserCredentialRetriever` requires a `username`.
- **Documented-but-unimplemented enforcement:** `LoginCommandHandler`'s javadoc promises
  MFA-enrollment enforcement and password-reset routing (`User` carries `mfaRequiredAt` /
  `passwordResetRequiredAt`), but the code never checks either field —
  `PasswordResetRequiredResult` is unreachable.

### Missing entirely

1. **No 2FA infrastructure adapters.** `TotpStatusReader`, `TotpVerifier`, `TotpSetupProvider`
   have no production implementations — only test lambdas. The login 2FA branch cannot run
   against a real database.
2. **No TOTP/backup-code generation.** No RFC 6238 code generator, no backup code generator,
   no library dependency for either.
3. **Incomplete database API.** Migrations only cover *reading* TOTP status. No functions to
   store/enable/disable secrets or create/consume backup codes.
4. **Missing handlers.** Nothing consumes the `Required2FAResult` verification token (no
   `VerifyTotpCommandHandler`); no `SetupTotpCommandHandler` / `EnableTotpCommandHandler`.
5. **No delivery layer.** No `main()`, no REST or gRPC server anywhere (the gRPC server from
   commit `fd2dadb` was later removed). The service currently cannot run.
6. **Unused token infrastructure.** `refresh_tokens` and `invalidated_jwts` tables exist but no
   refresh/logout code uses them.
7. **Housekeeping.** Uncommitted changes on master (Gradle wrapper upgrade, CLAUDE.md, misc);
   `docs/2FA_IMPLEMENTATION_CHECKLIST.md` is stale (references the removed `totp_enabled`
   column — status is now derived from `totp_verified_at`); no CI workflows.

---

## Plan to finish

### Phase 0 — Get the build green (small, do first)

- [ ] 0.1 Commit the pending wrapper/CLAUDE.md changes as tidy commits.
- [ ] 0.2 `DisableTotpCommandTest` — construction, null checks (`Objects.requireNonNull`).
- [ ] 0.3 `DisableTotpResultTest` — all variants, `mapTo`/`orElse` fluent paths, `DisableReason`.
- [ ] 0.4 `DisableTotpCommandHandlerTest` — Mockito, all branches (success, wrong password,
      not enrolled), mocking the `Ports.*` interfaces.
- [ ] 0.5 `LoginResultTest` additions — `Required2FAResult` and `PasswordResetRequiredResult`
      variants + mappers.
- [ ] 0.6 `TokenServiceTest` addition — `generate2FAVerificationToken` (claims: 5-min expiry,
      `aud: 2fa_verification`).
- [ ] 0.7 Cover remaining `LoginRateLimiter` (88%→100%) and `LoginCommandHandler` (96%→100%) lines.

**Exit criteria:** `./gradlew test integrationTest jacocoTestCoverageVerification` passes.

### Phase 1 — Finish the application-layer 2FA flow

- [ ] 1.1 Implement documented enforcement in `LoginCommandHandler`:
      `MFA_SETUP_REQUIRED` failure when `mfaRequiredAt` set but user not enrolled;
      `PasswordResetRequiredResult` when `passwordResetRequiredAt` set. Update tests.
- [ ] 1.2 Fix the `DisableTotpCommandHandler` re-authentication gap: add username to the
      command **or** add a `UserPasswordVerifier` port keyed by `userId` (preferred — avoids
      trusting client-supplied usernames).
- [ ] 1.3 TOTP primitives (domain, JDK-only per hexagonal rule):
      RFC 6238 `TotpCodeGenerator` (HMAC-SHA1, 30s step, ±1 step skew) and
      `BackupCodeGenerator` (`SecureRandom`, `XXXX-XXXX-XXXX-XXXX`, bcrypt-hashed via port).
- [ ] 1.4 `SetupTotpCommandHandler` — generate secret + `otpauth://` URI, persist pending secret.
- [ ] 1.5 `EnableTotpCommandHandler` — verify first code, set `totp_verified_at`, return
      one-time-visible backup codes.
- [ ] 1.6 `VerifyTotpCommandHandler` — validate the 2FA verification token
      (`aud: 2fa_verification`, unexpired), accept OTP **or** backup code (consume it),
      issue full access + refresh tokens.
- [ ] 1.7 All new commands/results follow existing patterns: records, sealed result interfaces
      with `mapTo`/`orElse`, `ValidatedValue` for new value types, 100% coverage.

### Phase 2 — Database + adapters

- [ ] 2.1 New Flyway migration(s) (`V1_4_x__…`) for write-side API functions:
      `store_totp_secret`, `enable_totp`, `disable_totp`, `insert_backup_codes`,
      `consume_backup_code` — idempotent, `REVOKE ALL … FROM PUBLIC` + `GRANT EXECUTE … TO
      app_user`, `${VARIABLE}` placeholders for secrets.
- [ ] 2.2 `JooqTotpStatusReader` (wraps existing `api_schema.get_totp_status`).
- [ ] 2.3 `JooqTotpVerifier`, `JooqTotpSetupProvider` (hand-written JOOQ `Routines` pattern,
      as in `UserCredentialReader`).
- [ ] 2.4 In-memory mock adapters for the integration tier
      (`MockTotpStatusReader` etc., alongside existing `MockRateLimiter`/`MockTokenSigner`).
- [ ] 2.5 `@Tag("database")` tests for each new adapter + migration
      (`databaseIntegrationTest -PincludeDbTests`).

### Phase 3 — Make it runnable (⚠ needs a transport decision)

- [ ] 3.1 **Decide transport.** Recommended: JDK `com.sun.net.httpserver.HttpServer`
      (framework-free, fits the zero-framework rule). Alternatives: reintroduce gRPC, or a
      lightweight framework. Docs and `LoginResult` messages already assume REST paths
      (`/auth/login`, `/auth/2fa/setup`, `/auth/reset-password`, …).
- [ ] 3.2 Delivery layer in `infrastructure/http/` + `Main` wiring `DatabaseConfig` and all
      adapters; endpoints: `POST /auth/login`, `POST /auth/2fa/verify`, `POST /auth/2fa/setup`,
      `POST /auth/2fa/enable`, `POST /auth/2fa/disable`, `GET /health`.
- [ ] 3.3 TLS wiring using existing `KEYSTORE_PASSWORD` / `TRUSTSTORE_PASSWORD` env vars and
      `KeySupplier` infrastructure.
- [ ] 3.4 End-to-end smoke test tier against docker-compose.

### Phase 4 — Token lifecycle + hardening

- [ ] 4.1 Refresh handler with rotation using the `refresh_tokens` table.
- [ ] 4.2 Logout / revocation using `invalidated_jwts` (check on token validation path).
- [ ] 4.3 Rate limiting on 2FA verification attempts (currently only login is limited).
- [ ] 4.4 Update stale docs (`2FA_IMPLEMENTATION_CHECKLIST.md`'s `totp_enabled` references).
- [ ] 4.5 CI workflow (GitHub Actions) running `test`, `integrationTest`,
      `jacocoTestCoverageVerification`.

### Sizing & order

| Phase | Effort | Blocked by |
|---|---|---|
| 0 | ~½–1 day | nothing — **start here** |
| 1 | 2–3 days | Phase 0 |
| 2 | 2–3 days | Phase 1 (ports/handlers define adapter contracts) |
| 3 | 1–2 days | transport decision; Phases 1–2 |
| 4 | incremental | Phase 3 |

---

## Model budget (agent cost control)

**Principle:** work that clones an existing pattern → cheap/included model; security design or
novel algorithms → premium model briefly, then hand implementation back to a cheap model.
The 100% JaCoCo gate + test tiers act as a safety net for cheap-model output — anything a
test can verify, use the cheap model and let the build catch mistakes.

| Task | Model tier | Why |
|---|---|---|
| **Phase 0** — all missing tests (0.2–0.7) | 🟢 Cheap/included (e.g., GPT-5 mini / Gemini Flash class, 0–0.33x) | Pure pattern-cloning from `LoginCommandHandlerTest`, `LoginResultTest`. Verifiable by JaCoCo — the coverage gate catches any model mistakes for free |
| **Phase 1.1** — login enforcement | 🟡 Mid (Sonnet class, 1x) | Small logic change but security-ordered; needs care with the check sequence |
| **Phase 1.2** — fix disable-password gap | 🔴 Premium, one short session (Opus/GPT-5 class) | This is a *security design decision* (new port vs command change). Get the design decided, then implement with a cheap model |
| **Phase 1.3** — `TotpCodeGenerator` (RFC 6238) | 🔴 Premium for the algorithm, 🟢 cheap for tests | Crypto correctness (HMAC, time-step, constant-time compare) is where cheap models make subtle mistakes. Test vectors from RFC 6238 Appendix B make verification objective |
| **Phase 1.3** — `BackupCodeGenerator` | 🟢 Cheap | SecureRandom + formatting, trivially testable |
| **Phase 1.4–1.7** — setup/enable/verify handlers | 🟡 Mid | Follows `LoginCommandHandler` shape, but token-validation logic in `VerifyTotpCommandHandler` deserves 1x attention |
| **Phase 2.1** — Flyway migrations | 🟡 Mid | SQL security (REVOKE/GRANT, idempotency) — mistakes are cheap to make, annoying to fix in prod |
| **Phase 2.2–2.4** — JOOQ adapters + mocks | 🟢 Cheap | Direct clone of `UserCredentialReader` pattern |
| **Phase 2.5** — `@Tag("database")` tests | 🟢 Cheap | Pattern-following, verifiable against compose DB |
| **Phase 3.1** — transport decision | 🔴 Premium, single discussion | Architecture decision with long-term consequences — worth one expensive conversation, then it's settled |
| **Phase 3.2–3.3** — HTTP layer + TLS wiring | 🟡 Mid | New code, no existing pattern to clone; TLS config errors are subtle |
| **Phase 3.4** — smoke tests | 🟢 Cheap | Mechanical |
| **Phase 4.1–4.2** — refresh/logout | 🟡 Mid | Token rotation semantics need care |
| **Phase 4.3–4.5** — 2FA rate limit, docs, CI | 🟢 Cheap | Rate limiter clones `LoginRateLimiter`; docs/CI are boilerplate |

**Budget summary:** ~70% of remaining work is 🟢 cheap-model territory, ~25% is 🟡 mid-tier,
and only **3 short 🔴 premium sessions** are needed (1.2 design, 1.3 TOTP algorithm, 3.1
transport decision).

---

*Re-verified 2026-08-09 by running `./gradlew test integrationTest jacocoTestCoverageVerification`
— failures listed under "Broken right now" are exact gradle output.*

