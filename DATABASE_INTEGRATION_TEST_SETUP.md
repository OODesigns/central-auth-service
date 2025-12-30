# Database Integration Tests - Setup Guide

## Overview

The `AdminLoginDatabaseIntegrationTest` now uses **real database infrastructure** instead of mocks. It connects to a real PostgreSQL database (via Testcontainers) and uses the actual adapters from the codebase.

## Architecture

```
AdminLoginDatabaseIntegrationTest
    ↓
    [Testcontainers PostgreSQL 15]  ← Real database
    ↓
    [Flyway Migrations]  ← Create schema + seed data
    ↓
    [JOOQ DSL Context]  ← Database connection
    ↓
    Real Infrastructure Adapters:
    - JooqUserCredentialReader (implements Ports.UserCredentialReader)
    - JooqUserRepository (implements Ports.UserRepository)
    - BcryptPasswordVerifier (implements Ports.PasswordVerifier)
    ↓
    LoginCommandHandler
    ↓
    AuthenticationService + TokenService
    ↓
    Test Assertions
```

## Key Components

### 1. **Testcontainers Integration**
```java
@Testcontainers
class AdminLoginDatabaseIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("test_auth_db")
        .withUsername("test_user")
        .withPassword("test_password");
}
```
- Automatically spins up a PostgreSQL 15 container
- Container is reused across test methods
- Torn down after all tests complete

### 2. **Flyway Migrations**
```java
@BeforeAll
static void setupDatabase() {
    runFlywayMigrations();
}

private static void runFlywayMigrations() {
    org.flywaydb.core.Flyway flyway = org.flywaydb.core.Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .locations("filesystem:../.devcontainer/flyway/sql")
        .placeholders(Map.of("ADMIN_PASSWORD", hashPassword(ADMIN_PASSWORD)))
        .load();
    
    flyway.migrate();
}
```
- Runs V1__init_schema.sql and V1_1__seed_auth_data.sql
- Creates full database schema (users, roles, permissions, user_roles, etc.)
- Seeds admin user with bcrypt-hashed password
- Migrations tracked in flyway_schema_history table

### 3. **Real Database Connection via JOOQ**
```java
@BeforeEach
void setUp() {
    dslContext = createDslContext();
    
    var userCredentialReader = new JooqUserCredentialReader(dslContext);
    var userRepository = new JooqUserRepository(dslContext);
    var passwordVerifier = new BcryptPasswordVerifier();
    
    loginHandler = new LoginCommandHandler(
        authService, tokenService, 
        userCredentialReader, userRepository,  // Real JOOQ adapters
        rateLimiter
    );
}

private DSLContext createDslContext() {
    var dataSource = new org.postgresql.ds.PGSimpleDataSource();
    dataSource.setServerName(postgres.getHost());
    dataSource.setPortNumber(postgres.getFirstMappedPort());
    dataSource.setDatabaseName(postgres.getDatabaseName());
    dataSource.setUser(postgres.getUsername());
    dataSource.setPassword(postgres.getPassword());
    
    return DSL.using(dataSource, SQLDialect.POSTGRES);
}
```
- Creates DSL context with real PostgreSQL connection
- Uses actual JOOQ adapters (not mocks)
- Executes real SQL queries against the database

### 4. **Real Authentication Flow**
When `testAdminLoginWithDatabaseCredentials()` runs:

1. LoginCommand created with admin username/password
2. LoginCommandHandler invokes AuthenticationService.authenticate()
3. AuthenticationService calls JooqUserCredentialReader.findCredentialsByUsername()
4. JooqUserCredentialReader executes: `SELECT * FROM auth.find_user_credentials(?)`
5. PostgreSQL function returns user_id and password_hash from database
6. BcryptPasswordVerifier.verify() compares provided password with database hash
7. If match, TokenService generates JWT tokens
8. JooqUserRepository retrieves user permissions via `SELECT * FROM auth.get_user(?)`

## Dependencies

The following were added to `build.gradle`:

```gradle
testImplementation 'org.testcontainers:testcontainers:1.19.8'
testImplementation 'org.testcontainers:postgresql:1.19.8'
testImplementation 'org.testcontainers:junit-jupiter:1.19.8'
testImplementation 'org.flywaydb:flyway-core:9.22.3'
```

## Adapter Changes

The following adapters were made public (constructor changed from package-private to public):

### JooqUserRepository
```java
// Before: final class JooqUserRepository (package-private)
// After:  public final class JooqUserRepository

// Before: JooqUserRepository(final DSLContext dsl)
// After:  public JooqUserRepository(final DSLContext dsl)
```

### JooqUserCredentialReader
```java
// Before: final class JooqUserCredentialReader (package-private)
// After:  public final class JooqUserCredentialReader

// Before: JooqUserCredentialReader(final DSLContext dsl)
// After:  public JooqUserCredentialReader(final DSLContext dsl)
```

This allows tests to instantiate these adapters directly with a test database context.

## Test Coverage

### AdminLoginDatabaseIntegrationTest

1. **testAdminLoginWithDatabaseCredentials()** - Real database login flow
   - Verifies admin user exists in database
   - Tests full authentication chain with real adapters
   - Confirms JWT token generation

2. **testAdminLoginFailsWithWrongPassword()** - BCrypt validation
   - Tests password verification against real database hash
   - Verifies error handling and timing attack resistance

3. **testJooqUserCredentialReaderQueries()** - JOOQ queries work
   - Tests `auth.find_user_credentials()` PostgreSQL function
   - Verifies JOOQ can read from real database

4. **testJooqUserRepositoryQueries()** - User permissions loading
   - Tests `auth.get_user()` PostgreSQL function
   - Verifies permissions loaded from database

5. **testDatabaseSchemaCreatedByFlyway()** - Schema validation
   - Confirms all required tables exist
   - Confirms PostgreSQL functions exist

6. **testFlywayMigrationHistoryTracked()** - Migration tracking
   - Verifies Flyway successfully tracked migrations
   - Checks migration count

7. **testAdminUserExistsInDatabaseWithRole()** - Admin user validation
   - Confirms admin user created by migration
   - Confirms admin role assigned

## Running the Tests

```bash
# Run all integration tests (including database tests)
./gradlew test

# Run only database integration tests
./gradlew test --tests AdminLoginDatabaseIntegrationTest

# Run specific test method
./gradlew test --tests AdminLoginDatabaseIntegrationTest.testAdminLoginWithDatabaseCredentials
```

## Prerequisites

- Docker daemon must be running (Testcontainers uses it)
- PostgreSQL driver is in classpath (org.postgresql:postgresql:42.7.3)
- Flyway migrations are accessible at `../.devcontainer/flyway/sql/`

## Comparison: Mock vs Database Tests

| Aspect | AdminLoginMockIntegrationTest | AdminLoginDatabaseIntegrationTest |
|--------|-------------------------------|----------------------------------|
| **Database** | In-memory only | Real PostgreSQL via Testcontainers |
| **Speed** | ~100ms | ~5-10s |
| **Flyway** | Not run | Runs V1 + V1_1 migrations |
| **Adapters** | Mock/InMemory | Real JOOQ + BCrypt + JWT |
| **Use Case** | Fast feedback loop | Pre-commit validation, E2E testing |
| **Schema** | Simulated | Real database with all constraints |
| **Functions** | N/A | Tests auth.find_user_credentials(), auth.get_user() |

## Troubleshooting

### Docker not found
```
Error: Docker not available
```
Solution: Ensure Docker daemon is running (`docker ps` should work)

### Migration not found
```
Error: File not found: ../.devcontainer/flyway/sql/V1__init_schema.sql
```
Solution: Verify working directory is `central-auth-service/` when running tests

### Port already in use
```
Error: Port 5432 already in use
```
Solution: Testcontainers finds available port automatically, but if PostgreSQL is running locally, stop it or let Testcontainers use different port

### Placeholder not replaced
```
Error: Admin user cannot login
```
Solution: Check that ADMIN_PASSWORD placeholder is properly bcrypt-hashed in migrations
