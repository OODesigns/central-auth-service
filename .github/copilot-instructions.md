# Copilot Instructions for Home Control System

## Architecture Overview

This is a **Hexagonal Architecture (Ports & Adapters)** authentication service in Java 22 with strict separation:

- **domain/** - Pure business logic, no framework dependencies. Contains `Ports.java` interface definitions
- **application/** - Command handlers orchestrating domain services (e.g., `LoginCommandHandler`)
- **infrastructure/** - Adapters implementing `Ports.*` interfaces (JOOQ, BCrypt, JWT, Bucket4j)

All external dependencies flow through `Ports` interfaces defined in `src/main/java/com/oodesigns/cas/domain/service/Ports.java`.

## Key Patterns

### Value Objects with ValidatedValue Base
All domain values extend `ValidatedValue<T>` with this pattern:
```java
public final class Username extends ValidatedValue<String> {
    private Username(String value) { super(value); }  // private constructor
    public static Username of(String value) { /* validate then construct */ }  // factory method
}
```
Validation happens in `of()` factory methods, NOT constructors. See `Username.java` for an example.

### Fluent Result Pattern (mapTo/orElse)
Results use sealed interfaces with fluent mapping instead of exceptions:
```java
result.mapTo(success -> handleSuccess(success))
      .orElse(failure -> handleFailure(failure));
```
Applied in: `LoginResult`, `RateLimitResult`. Never use `instanceof` checks.

### Sensitive Data Handling
- `Password` and `Credentials` implement `AutoCloseable` - always use try-with-resources
- Char arrays for passwords, cleared after use via `close()`
- `KeyPassword` extends `Password` for keystore secrets

## Testing

### Test Tiers (JUnit Tags)
```bash
./gradlew test                          # Unit tests only (default, excludes integration)
./gradlew integrationTest               # Integration tests (no database)
./gradlew databaseIntegrationTest -PincludeDbTests  # Database tests (requires docker-compose)
```

### Coverage Requirement
**100% line coverage enforced** via JaCoCo (excludes `Ports.java` and `DatabaseContextFactory`).

### Mocking Pattern
Tests use Mockito with `@ExtendWith(MockitoExtension.class)`. Mock all `Ports.*` interfaces in unit tests. See `LoginCommandHandlerTest.java` for an example.

## Database

- **PostgreSQL** via docker-compose with Flyway migrations
- **JOOQ** for type-safe queries - adapters in `infrastructure/adapter/`
- Stored procedures in `auth` schema (e.g., `auth.find_user_credentials()`)
- Config via `application.properties` with `${ENV_VAR:default}` syntax

## Build Commands

```bash
./gradlew build                    # Compile + unit tests
./gradlew test                     # Unit tests with JaCoCo report
./gradlew jacocoTestCoverageVerification  # Verify 100% coverage
```

## Conventions

- **Records** for DTOs, value objects, and immutable data carriers
- **Sealed interfaces** for result types with exhaustive handling
- **Optional** chaining for null-safe flows (no null returns from public methods)
- **Objects.requireNonNull()** in all constructors for required dependencies
- **Final fields and parameters** everywhere - immutability by default
