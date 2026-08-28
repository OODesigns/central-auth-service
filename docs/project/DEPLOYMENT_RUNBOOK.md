# Deployment Runbook

This is the top-level operational guide for returning to the project and replaying the local deployment flow in the correct order.

Use this document as the entry point. It points to the more detailed guides below and keeps the flow ordered from fastest validation to environment-specific checks.

## 1. Critical auth validation

Start here when returning to the repo after a change or after a break.

```bash
cd /home/ood/Documents/projects/central-auth-service
./gradlew criticalAuthTests --console=plain
```

See:
- [CRITICAL_PATH_TESTS.md](CRITICAL_PATH_TESTS.md)
- [LOCAL_DEPLOYMENT_CHECKLIST.md](LOCAL_DEPLOYMENT_CHECKLIST.md)

## 2. Broader code validation

If the critical auth path passes, run the standard project tests:

```bash
./gradlew test --console=plain
```

If you also want the aggregate coverage snapshot:

```bash
./gradlew jacocoTestReport jacocoTestCoverageVerification --console=plain
```

## 3. Database-backed validation

Database-specific validation is a separate tier from unit tests and should be run when DB behavior matters.

```bash
./gradlew databaseIntegrationTest -PincludeDbTests
```

See:
- [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md)
- [SECURITY_ROLLOUT.md](SECURITY_ROLLOUT.md)

## 4. Local stack bootstrap

When validating a local deployment, start the trial stack to recreate the environment that the app expects.

```bash
./scripts/start-trial-stack.sh
```

This script automatically fills in required local env values and starts the Docker Compose environment.

See:
- [scripts/start-trial-stack.sh](../../scripts/start-trial-stack.sh)
- [compose.yml](../../compose.yml)
- [README.md](../../README.md)

## 5. Smoke tests

Once the stack is up, run the smoke checks intended to resemble a real deployment:

```bash
./gradlew smokeTest
```

## 6. Environment and TLS validation

Before claiming a deployment is healthy, validate the environment-specific configuration.

```bash
./scripts/validate-deployment.sh
./scripts/verify-tls.sh <HOST> <PORT> [SERVER_NAME]
```

This is especially important for secrets, keystore/truststore values, and certificate trust.

See:
- [README.md](../../README.md)
- [scripts/validate-deployment.sh](../../scripts/validate-deployment.sh)
- [scripts/verify-tls.sh](../../scripts/verify-tls.sh)

## 7. Decision rule

Use the following confidence model:

- Local auth logic: validated by critical tests
- Local app behavior: validated by unit tests
- DB-backed behavior: validated by database integration checks
- Local deployment health: validated by trial stack startup and smoke tests
- Environment trust: validated by TLS and deployment scripts

If any environment-specific checks are skipped, treat the deployment as local smoke only, not as full deployment validation.

## 8. Full operation order

The order to follow when returning to a machine is:

1. run critical auth tests
2. run unit test suite
3. run database integration tests if relevant
4. start the trial stack
5. run smoke tests
6. run deployment and TLS validation
7. only then call the deployment locally healthy
