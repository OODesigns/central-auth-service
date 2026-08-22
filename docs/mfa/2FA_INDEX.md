# 2FA Documentation Index

The implemented transport is gRPC. Older REST endpoint plans are obsolete.

## Current references

- [Project status and completion plan](../project/PROJECT_STATUS_AND_COMPLETION_PLAN.md): authoritative project status and remaining work
- [2FA quick reference](2FA_QUICK_REFERENCE.md): flow, RPCs, security properties, and commands
- [2FA implementation summary](2FA_IMPLEMENTATION_SUMMARY.md): architecture and implementation overview
- [2FA implementation checklist](2FA_IMPLEMENTATION_CHECKLIST.md): completed controls and operational follow-up
- [2FA schema updates](history/2FA_SCHEMA_UPDATES.md): historical database design
- [2FA risk assessment](history/2FA_RISK_ASSESSMENT.md): historical security considerations and threat analysis
- [2FA implementation inventory](2FA_FILES_CREATED.md): key source, test, and migration locations

## Historical references

Some documents in this directory capture earlier design exploration. When they conflict with code or current status, use `PROJECT_STATUS_AND_COMPLETION_PLAN.md`, the Flyway migrations, and the Java/protobuf sources as authoritative.

## Verification

```bash
./gradlew test integrationTest jacocoTestCoverageVerification
./gradlew databaseIntegrationTest -PincludeDbTests
```