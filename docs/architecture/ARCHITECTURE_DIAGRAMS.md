# Architecture Diagrams

This index describes diagrams matching the implementation as of 2026-08-22. Behavior is defined by the Java application, `src/main/proto/auth.proto`, Flyway migrations through `V1_4_8`, and the tests. Current status is maintained in [PROJECT_STATUS_AND_COMPLETION_PLAN.md](../project/PROJECT_STATUS_AND_COMPLETION_PLAN.md).

## Current diagrams

| Diagram | Purpose | Implementation sources |
| --- | --- | --- |
| [Runtime_Architecture.puml](../../design/architecture/Runtime_Architecture.puml) | gRPC runtime, hexagonal ports, adapters, PostgreSQL schemas, TLS/mTLS boundaries, and the current absence of an access-token interceptor | [Main.java](../../src/main/java/com/oodesigns/cas/Main.java), [GrpcTlsConfigurer.java](../../src/main/java/com/oodesigns/cas/infrastructure/grpc/GrpcTlsConfigurer.java), [Ports.java](../../src/main/java/com/oodesigns/cas/domain/service/Ports.java) |
| [LoginFlow_SequenceDiagram.puml](../../design/authentication/LoginFlow_SequenceDiagram.puml) | Login ordering, three-key rate limiting, MFA setup/error, 2FA challenge, password reset, and token issuance | [LoginCommandHandler.java](../../src/main/java/com/oodesigns/cas/application/command/LoginCommandHandler.java), [auth.proto](../../src/main/proto/auth.proto) |
| [0 - Request Guardrails & Rate Limiting (Multi-Key).puml](../../design/authentication/login/0%20-%20Request%20Guardrails%20%26%20Rate%20Limiting%20%28Multi-Key%29.puml) | IP, username, and IP+username login buckets | [LoginRateLimiter.java](../../src/main/java/com/oodesigns/cas/infrastructure/adapter/LoginRateLimiter.java) |
| [2 - MFA Policy & Challenge.puml](../../design/authentication/login/2%20-%20MFA%20Policy%20%26%20Challenge.puml) | MFA policy routing and terminal gRPC responses | [LoginCommandHandler.java](../../src/main/java/com/oodesigns/cas/application/command/LoginCommandHandler.java) |
| [Perform MFA challenge.puml](../../design/authentication/login/mfa/Perform%20MFA%20challenge.puml) | Per-user limiting, TOTP/backup-code verification, and full token issuance | [VerifyTotpCommandHandler.java](../../src/main/java/com/oodesigns/cas/application/command/VerifyTotpCommandHandler.java), [TotpRateLimiter.java](../../src/main/java/com/oodesigns/cas/infrastructure/adapter/TotpRateLimiter.java) |
| [TOTP_Lifecycle.puml](../../design/authentication/TOTP_Lifecycle.puml) | Pending secret enrollment, activation, backup codes, verification, and disable cleanup | [SetupTotpCommandHandler.java](../../src/main/java/com/oodesigns/cas/application/command/SetupTotpCommandHandler.java), [EnableTotpCommandHandler.java](../../src/main/java/com/oodesigns/cas/application/command/EnableTotpCommandHandler.java), [DisableTotpCommandHandler.java](../../src/main/java/com/oodesigns/cas/application/command/DisableTotpCommandHandler.java) |
| [Refresh_Token_Rotation.puml](../../design/authentication/Refresh_Token_Rotation.puml) | Atomic rotation and family revocation on reuse | [RefreshTokenCommandHandler.java](../../src/main/java/com/oodesigns/cas/application/command/RefreshTokenCommandHandler.java), [V1_4_6__add_refresh_token_api_functions.sql](../../.devcontainer/flyway/sql/V1_4_6__add_refresh_token_api_functions.sql) |
| [Logout_Access_Token_Revocation.puml](../../design/authentication/Logout_Access_Token_Revocation.puml) | Logout persistence and JTI-based rejection contract; explicitly notes that no protected gRPC interceptor consumes access tokens yet | [LogoutCommandHandler.java](../../src/main/java/com/oodesigns/cas/application/command/LogoutCommandHandler.java), [JwtTokenVerifier.java](../../src/main/java/com/oodesigns/cas/infrastructure/adapter/JwtTokenVerifier.java), [V1_4_8__add_jwt_revocation_api_functions.sql](../../.devcontainer/flyway/sql/V1_4_8__add_jwt_revocation_api_functions.sql) |
| [CAS User + Role Schema.puml](../../design/architecture/CAS%20User%20%2B%20Role%20Schema.puml) | Current ten-table authentication persistence model: users, authorization, TOTP, tokens, trusted clients, and audit data | [V1_0_2__create_tables.sql](../../.devcontainer/flyway/sql/V1_0_2__create_tables.sql) |

## Historical or superseded material

These remain design history, not current API specifications. They describe REST endpoints, generic WebAuthn/MFA claims, old table names, or proposed work:

- `design/history/login/LOGIN_FLOW_DIAGRAMS_README.md`
- `design/history/login/TOKENS.md`
- `docs/mfa/history/2FA_IMPLEMENTATION_GUIDE.md`
- `docs/mfa/history/2FA_RISK_ASSESSMENT.md`
- `docs/mfa/history/2FA_SCHEMA_UPDATES.md`
- `docs/mfa/history/PASSWORD_RESET_2FA_SECURE_FLOW.md`

The old `design/history/login/3 - Post Authentication.puml` and `design/history/login/4 - Authorization & Token Issuance.puml` are historical planning slices. Current branching and token behavior is owned by the diagrams above.

## Validation

All 12 PlantUML sources were compiled and rendered successfully with PlantUML 1.2025.4 on 2026-08-22. Generated PNGs are stored beside their sources. Local include paths, diagram index targets, and changed-file whitespace were also validated.
