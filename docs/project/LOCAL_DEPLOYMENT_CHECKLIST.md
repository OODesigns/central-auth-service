# Local Deployment Checklist

Use this when returning to the project after a break. It keeps the local validation loop short and explicit.

## 1. Critical auth path

Run the focused auth validation first:

```bash
cd /home/ood/Documents/projects/central-auth-service
./gradlew criticalAuthTests --console=plain
```

This should validate the core login flow, MFA state handling, rate limiting, and API response mapping.

## 2. Broader unit validation

If the critical auth path passes, run the standard unit suite:

```bash
./gradlew test --console=plain
```

## 3. Coverage snapshot

Optional but useful when checking the repo health:

```bash
./gradlew jacocoTestReport jacocoTestCoverageVerification --console=plain
```

## 4. Database-backed verification

These are separate from the core unit checks and should be run when database behavior matters:

```bash
./gradlew databaseIntegrationTest -PincludeDbTests
```

If the project requires Dockerized DB setup, also validate the database stack before relying on database-backed flows.

## 5. Trial stack / docker-compose startup

Validate the local stack startup before declaring local deployment healthy:

```bash
./scripts/start-trial-stack.sh
```

Then confirm the stack is responding as expected.

## 6. External smoke checks

Run the smoke or external validation suite when the environment is meant to resemble a real deployment:

```bash
./gradlew smokeTest
```

## 7. Environment and TLS validation

Before assuming a local deployment is ready, verify:

- required environment variables are populated
- secrets and keystore/truststore values are present
- TLS certificates and trust configuration are valid
- any deployment-specific `.env` or trial config files are in place

Useful checks may include:

```bash
./scripts/verify-tls.sh
./scripts/validate-deployment.sh
```

## 8. Deployment confidence rule

A local deployment is only considered ready when all relevant layers pass:

- critical auth path tests
- unit tests
- database-backed integration checks if DB behavior is involved
- docker-compose / trial stack startup
- smoke checks
- environment and TLS validation

If any of the deployment-specific checks are skipped, the deployment should be treated as local smoke only, not production-equivalent validation.
