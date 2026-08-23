# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
./gradlew build                          # Compile + unit tests
./gradlew test                           # Unit tests with JaCoCo report
./gradlew integrationTest                # Integration tests (no database required)
./gradlew databaseIntegrationTest -PincludeDbTests  # Database tests (requires docker-compose)
./gradlew jacocoTestCoverageVerification # Verify 100% line coverage

# Run a single test class
./gradlew test --tests "com.oodesigns.cas.domain.value.UsernameTest"

# Run a single test method
./gradlew test --tests "com.oodesigns.cas.domain.value.UsernameTest.someMethod"
```

**100% line coverage is enforced** via JaCoCo. Excluded classes: `Ports.java` and `DatabaseContextFactory`. Coverage aggregates across unit + integration tiers, so a class only exercised by integration tests still counts.

## Architecture

Hexagonal Architecture (Ports & Adapters):

- **`domain/`** — Pure business logic, zero framework dependencies. `Ports.java` defines all port interfaces.
- **`application/command/`** — Command handlers (e.g., `LoginCommandHandler`) that orchestrate domain services and inject port implementations.
- **`infrastructure/adapter/`** — Concrete `Ports.*` implementations: `UserCredentialReader` (JOOQ), `BcryptPasswordVerifier`, `JwtTokenSigner`, `LoginRateLimiter` (Bucket4j).
- **`infrastructure/config/`** — Database connection setup (`DatabaseConfig`, `DatabaseContextFactory`).
- **`util/`** — Shared utilities for file loading, properties reading, and the `ValidatedValue<T>` base class.

**Dependency rule**: only `infrastructure` knows about frameworks. `domain` and `application` import nothing outside the JDK.

### Login Flow

`LoginCommandHandler.handle()` executes this security-ordered sequence:

1. Rate limit check (IP, username, IP+username buckets via `Ports.RateLimiter`)
2. Credential lookup via `Ports.UserCredentialRetriever` → `AuthenticationService` verifies password
3. 2FA status check via `Ports.TotpStatusReader` — if enabled, returns `Required2FAResult` with a short-lived verification token (5 min, `aud: 2fa_verification`)
4. Full user load via `Ports.UserRetriever` → `TokenService` issues access (15 min) + refresh (7 day) tokens

## Key Patterns

### Value Objects (`ValidatedValue<T>`)

All domain values extend `ValidatedValue<T>`. Validation happens only in the static factory method, never in the constructor:

```java
public final class Username extends ValidatedValue<String> {
    private Username(String value) { super(value); }  // private, no validation
    public static Username of(String value) { /* validate, then: */ return new Username(value); }
}
```

`toString()` uses `getDisplayValue()` — override this to mask sensitive values (see `Password`).

### Fluent Result Pattern (`mapTo` / `orElse`)

Sealed interfaces carry results without `instanceof`. Never switch on subtypes directly:

```java
loginResult
    .mapTo(success -> handleSuccess(success))
    .orElse(failure -> handleFailure(failure));
```

Applied in: `LoginResult` (4 variants), `Ports.RateLimitResult` (2 variants).

### Sensitive Data (`AutoCloseable`)

`Password` and `Credentials` use char arrays and zero memory on `close()`. Always use try-with-resources:

```java
try (credentials) {
    return passwordVerifier.verify(credentials);
}
```

### JOOQ Without Code Generation

`UserCredentialReader` uses hand-written inner classes (`Routines`, `UserCredentialsRecord`) that simulate JOOQ's generated-code structure. DB functions live in the `api_schema` PostgreSQL schema (e.g., `api_schema.find_user_credentials(?)`).

## Test Tiers

| Tag | Gradle task | Requires |
|-----|-------------|----------|
| *(none)* | `test` | Nothing |
| `@Tag("integration")` | `integrationTest` | Nothing (in-memory mocks) |
| `@Tag("database")` | `databaseIntegrationTest -PincludeDbTests` | docker-compose |

Integration tests use hand-rolled mock adapters in `src/test/.../infrastructure/adapter/`: `InMemoryUserRepository`, `MockPasswordVerifier`, `MockClock`, `MockTokenSigner`, `MockRateLimiter`.

## Database

- PostgreSQL via docker-compose (`.devcontainer/docker-compose.yml`)
- Flyway migrations in `.devcontainer/flyway/sql/`, naming: `V{major}_{minor}_{patch}__{description}.sql`
- 2FA status is derived from `totp_secrets.verified_at`: a pending secret has `NULL`, and an active secret has its verification timestamp. There is no separate boolean flag.
- Required env vars: `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`, `APP_DB`, `APP_USER`, `APP_PASSWORD`, `ADMIN_PASSWORD_HASH`, `DATABASE_URL`, `JWT_SECRET`, `KEYSTORE_PASSWORD`, `TRUSTSTORE_PASSWORD`

### Migration conventions

- All migrations must be idempotent (`IF NOT EXISTS`, `ON CONFLICT DO NOTHING`)
- Always `REVOKE ALL … FROM PUBLIC` then `GRANT EXECUTE … TO app_user` for every new function
- Use Flyway placeholders (`${VARIABLE}`) for secrets, never hardcode them
