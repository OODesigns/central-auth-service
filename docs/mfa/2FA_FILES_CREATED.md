# 2FA Implementation Inventory

This is a current navigation aid, not a historical file-count report.

## Domain and application

- `src/main/java/com/oodesigns/cas/domain/service/Ports.java`
- `src/main/java/com/oodesigns/cas/domain/service/TotpCodeGenerator.java`
- `src/main/java/com/oodesigns/cas/domain/service/BackupCodeGenerator.java`
- `src/main/java/com/oodesigns/cas/domain/value/TotpCode.java`
- `src/main/java/com/oodesigns/cas/domain/value/SecretFor2FA.java`
- `src/main/java/com/oodesigns/cas/domain/value/BackupCode.java`
- `src/main/java/com/oodesigns/cas/application/command/SetupTotpCommandHandler.java`
- `src/main/java/com/oodesigns/cas/application/command/EnableTotpCommandHandler.java`
- `src/main/java/com/oodesigns/cas/application/command/VerifyTotpCommandHandler.java`
- `src/main/java/com/oodesigns/cas/application/command/DisableTotpCommandHandler.java`

## Infrastructure

- `src/main/java/com/oodesigns/cas/infrastructure/adapter/JooqTotpStatusReader.java`
- `src/main/java/com/oodesigns/cas/infrastructure/adapter/JooqTotpVerifier.java`
- `src/main/java/com/oodesigns/cas/infrastructure/adapter/JooqTotpSetupProvider.java`
- `src/main/java/com/oodesigns/cas/infrastructure/adapter/TotpRateLimiter.java`
- `src/main/java/com/oodesigns/cas/infrastructure/grpc/AuthGrpcService.java`
- `src/main/proto/auth.proto`

## Database

TOTP behavior is built incrementally by Flyway migrations under `.devcontainer/flyway/sql/`, especially `V1_4_0`, `V1_4_1`, `V1_4_4`, `V1_4_5`, and `V1_4_7`.

## Tests

- `src/test/java/com/oodesigns/cas/domain/service/TotpCodeGeneratorTest.java`
- `src/test/java/com/oodesigns/cas/domain/service/BackupCodeGeneratorTest.java`
- `src/test/java/com/oodesigns/cas/application/command/*Totp*Test.java`
- `src/test/java/com/oodesigns/cas/infrastructure/adapter/JooqTotp*Test.java`
- `src/test/java/com/oodesigns/cas/integration/database/TotpDatabaseIntegrationTest.java`
- `src/test/java/com/oodesigns/cas/integration/grpc/GrpcSmokeTest.java`

See [2FA_INDEX.md](2FA_INDEX.md) for the documentation map.