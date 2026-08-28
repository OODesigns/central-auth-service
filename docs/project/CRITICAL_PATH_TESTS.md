# Critical Path Tests

This project should treat a small set of behavior-first tests as the primary contract for auth correctness. These are the scenarios that matter most to the product and should be run before broader coverage checks.

## BDD-style scenarios

These tests are written in JUnit, but the naming and intent follow Given / When / Then behavior.

### 1. Successful login
Given a valid username and password, and the user is not blocked by MFA or reset requirements
When the login command is processed
Then the user receives a success result with access and refresh tokens

### 2. Invalid credentials
Given a username that does not exist or a password that does not match
When the login command is processed
Then the system returns INVALID_CREDENTIALS

### 3. Rate limited request
Given the user or IP has exceeded the configured threshold
When login is attempted again
Then the system returns RATE_LIMITED

### 4. MFA required and already enrolled
Given the user has MFA enabled and enrolled
When login is attempted
Then the system returns a 2FA verification requirement and does not issue full tokens

### 5. MFA required but not enrolled
Given the user has MFA enforcement enabled but no TOTP secret is enrolled
When login is attempted
Then the system returns an MFA enrollment requirement

### 6. Password reset required
Given the user is flagged for password reset
When login is attempted
Then the system returns PASSWORD_RESET_REQUIRED instead of issuing a full session

### 7. Runtime failure handling
Given an unexpected downstream failure occurs during authentication or token generation
When the login flow is executed
Then the system returns INTERNAL_ERROR without leaking implementation details

## Current implementation mapping

The repo already covers these behaviors in the following test classes:

- LoginCommandHandlerTest
- LoginResultTest
- AuthGrpcServiceTest
- LoginRateLimiterTest

## Recommended execution order

Run the high-value tests first when verifying auth changes:

```bash
./gradlew criticalAuthTests
```

You can also run the individual classes directly if needed:

```bash
./gradlew test --tests 'com.oodesigns.cas.application.command.LoginCommandHandlerTest'
./gradlew test --tests 'com.oodesigns.cas.application.command.LoginResultTest'
./gradlew test --tests 'com.oodesigns.cas.infrastructure.grpc.AuthGrpcServiceTest'
./gradlew test --tests 'com.oodesigns.cas.infrastructure.adapter.LoginRateLimiterTest'
```

Use JaCoCo as a secondary signal, not as the only definition of correctness. The critical path tests are the real business proof.
