# Integration Tests for Central Auth Service

This directory contains three distinct integration test suites for authentication and login functionality:

## 1. LoginMockIntegrationTest
**File:** `LoginMockIntegrationTest.java`  
**Type:** Mock-based integration test  
**Scope:** General login scenarios (not admin-specific)

### What it tests:
- Valid credentials login
- Invalid password handling
- Non-existent user handling
- Multiple users in system
- Rate limiting behavior
- Token generation and validation

### Dependencies:
- In-memory adapters (no external dependencies)
- Fast execution
- Suitable for CI/CD pipelines

### Test Coverage:
- `testCompleteLoginFlow()` - Successful login with valid credentials
- `testLoginWithInvalidPassword()` - Handles wrong password correctly
- `testLoginWithNonExistentUser()` - Generic error for non-existent users
- `testMultipleUsersInSystem()` - Multiple users can authenticate independently
- `testRateLimitingBlocks()` - Rate limiting enforced after N attempts
- `testTokenExpiration()` - Tokens expire at correct time

---

## 2. AdminLoginMockIntegrationTest
**File:** `AdminLoginMockIntegrationTest.java`  
**Type:** Mock-based integration test  
**Scope:** Admin user authentication scenarios

### What it tests:
- Admin login with correct credentials
- Admin login with incorrect password
- Admin role verification
- Admin role permissions
- Rate limiting per IP address
- Token timestamp validation
- Admin forced password reset requirement

### Dependencies:
- In-memory adapters (no external dependencies)
- Fast execution
- Admin-specific test data

### Test Coverage:
- `testAdminLoginWithCorrectCredentials()` - Admin can login with valid credentials
- `testAdminLoginWithIncorrectPassword()` - Wrong password fails properly
- `testAdminUserHasAdminRole()` - Admin user has admin role assigned
- `testAdminLoginRateLimiting()` - Rate limiting enforced per IP
- `testAdminLoginRateLimitingPerIP()` - Different IPs have independent rate limits
- `testAdminLoginTokenTimestamp()` - Token generated with correct timestamp

---

## 3. AdminLoginDatabaseIntegrationTest
**File:** `AdminLoginDatabaseIntegrationTest.java`  
**Type:** Real database integration test (Testcontainers + Flyway)  
**Scope:** Admin authentication against real PostgreSQL database

### What it tests:
- Admin login with credentials from real database (after Flyway migrations)
- Admin user creation by Flyway migration
- Admin role assignment in database
- Database schema validation
- Flyway migration execution
- Password hashing in database

### Dependencies:
- **Real PostgreSQL 15 database** (via Testcontainers)
- **Flyway migrations** (runs during test setup)
- Real password hashing (bcrypt)
- Slower execution (appropriate for pre-commit/pre-push checks)

### Test Coverage:
- `testAdminLoginWithDatabaseCredentials()` - Login with real database credentials
- `testAdminLoginFailsWithWrongPassword()` - Wrong password fails against real database
- `testDatabaseSchemaCreatedByFlyway()` - All required tables created
- `testFlywayMigrationHistoryTracked()` - Migrations tracked in database
- `testAdminUserExistsInDatabase()` - Admin user created with correct role

---

## Running the Tests

### Run all integration tests:
```bash
./gradlew test
```

### Run only mock tests (fast):
```bash
./gradlew test --tests "*MockIntegrationTest"
```

### Run only database integration tests (requires Docker):
```bash
./gradlew test --tests "*DatabaseIntegrationTest"
```

### Run specific test class:
```bash
./gradlew test --tests LoginMockIntegrationTest
./gradlew test --tests AdminLoginMockIntegrationTest
./gradlew test --tests AdminLoginDatabaseIntegrationTest
```

---

## Test Strategy

| Aspect | Mock Tests | Database Tests |
|--------|-----------|-----------------|
| **Speed** | Fast (~100ms) | Slow (~5-10s) |
| **Dependencies** | None | Docker + PostgreSQL |
| **Database State** | In-memory only | Real database |
| **Use Cases** | Fast feedback loop, CI/CD | Pre-commit validation, E2E testing |
| **Scope** | Unit-level integration | Full system integration |
| **Flyway** | Not run | Runs migrations |

---

## Architecture

All tests follow the hexagonal architecture pattern:

```
Test Code
   ↓
LoginCommandHandler (Application Command)
   ↓
AuthenticationService (Domain Service)
   ↓
Ports (UserRepository, PasswordVerifier, Clock, RateLimiter)
   ↓
Adapters:
  - Mock/InMemory (for LoginMockIntegrationTest, AdminLoginMockIntegrationTest)
  - Real PostgreSQL (for AdminLoginDatabaseIntegrationTest via Testcontainers)
```

This architecture ensures:
- Easy testing with different adapter implementations
- No tight coupling to external systems
- Clear separation of concerns
- Easy to add new implementations (e.g., real database)

---

## Adding New Tests

### For Mock Tests:
1. Create test in `LoginMockIntegrationTest` or `AdminLoginMockIntegrationTest`
2. Use `InMemoryUserRepository` and mock adapters
3. No external dependencies needed
4. Fast execution

### For Database Tests:
1. Add test to `AdminLoginDatabaseIntegrationTest`
2. Use `@Testcontainers` annotation
3. Query real database via JDBC
4. Slower but tests real behavior

---

## Debugging Tips

### Mock Tests Fail:
- Check `InMemoryUserRepository` state
- Verify mock adapter setup in `@BeforeEach`
- Check password hashing in `MockPasswordVerifier`

### Database Tests Fail:
- Ensure Docker daemon is running
- Check Flyway migration logs
- Verify database connection string
- Check `.env` file for password placeholders

### Rate Limiting Issues:
- `Bucket4jRateLimiter` has 5 attempts per minute per IP
- Reset in test setup or create new instance
- Different test IPs don't interfere with each other
