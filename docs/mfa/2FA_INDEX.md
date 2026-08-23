# 2FA Documentation Index

The implemented transport is gRPC. These references describe the current implementation.

## Current references

- [2FA quick reference](2FA_QUICK_REFERENCE.md): flow, RPCs, security properties, and commands
- [Architecture diagrams](../architecture/ARCHITECTURE_DIAGRAMS.md): current authentication and TOTP flows
- [gRPC API reference](../api/GRPC_API_REFERENCE.md): request, response, and error contract
- [Security rollout](../project/SECURITY_ROLLOUT.md): production controls and operations

## Verification

```bash
./gradlew test integrationTest jacocoTestCoverageVerification
./gradlew databaseIntegrationTest -PincludeDbTests
```