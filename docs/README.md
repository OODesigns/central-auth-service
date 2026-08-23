# Central Auth Service - Documentation Index

> **Purpose:** This is the single source of truth for all project documentation. Everything is stored locally in this repository so nothing is lost regardless of external service availability.

---

## 📁 Documentation Structure

```
central-auth-service/
├── README.md                          # Project overview, env vars, Flyway setup
├── docs/                              # All written documentation (this directory)
│   ├── README.md                      # ← YOU ARE HERE (master index)
│   ├── api/                           # External service contracts
│   ├── architecture/                  # Architecture and policy decisions
│   ├── database/                      # Database, Flyway, and security references
│   │   └── migration-history/         # Superseded migration-era material
│   ├── mfa/                           # Current TOTP/MFA documentation
│   │   └── history/                   # Superseded MFA plans
│   ├── project/                       # Current project status
│   └── home-control/                  # Home-control integration material
├── design/                            # Visual diagrams (PlantUML + PNG)
│   ├── README.md                      # Design diagram index
│   ├── architecture/                  # Runtime and persistence models
│   ├── authentication/                # Current authentication flows
│   ├── history/                       # Superseded design material
│   └── themes/                        # Shared PlantUML themes
└── .github/
    └── copilot-instructions.md        # AI assistant context (architecture summary)
```

---

## 🏗️ Architecture & Design

| Document | Description |
|----------|-------------|
| [ARCHITECTURE_DIAGRAMS.md](architecture/ARCHITECTURE_DIAGRAMS.md) | Current diagram index and implementation references |
| [GRPC_API_REFERENCE.md](api/GRPC_API_REFERENCE.md) | Authoritative request, response, outcome, and error contract for all seven gRPC RPCs |
| [AUTH_POLICY.md](architecture/AUTH_POLICY.md) | Authentication policy rules and enforcement |
| [UserCredentialReader_Design.md](architecture/UserCredentialReader_Design.md) | Design for the UserCredentialReader component |
| [DatabaseConfig_Refactoring.md](architecture/DatabaseConfig_Refactoring.md) | Database configuration refactoring notes |
| [SELECTIVE_PERMISSION_LOADING.md](architecture/SELECTIVE_PERMISSION_LOADING.md) | Strategy for loading permissions only when needed |
| [PASSWORD_RESET_VS_LOGIN_FLOWS.md](architecture/PASSWORD_RESET_VS_LOGIN_FLOWS.md) | How password reset and login flows differ |
| [PASSWORD_RESET_2FA_SECURE_FLOW.md](mfa/history/PASSWORD_RESET_2FA_SECURE_FLOW.md) | Historical password-reset and 2FA proposal |

---

## 🔐 Security Hardening (V1→V2 Migration)

### Start Here
| Document | Description |
|----------|-------------|
| [SECURITY_ROLLOUT.md](project/SECURITY_ROLLOUT.md) | Production automation, migrations, TLS, secrets, rotation, scanning, backups, monitoring, and recovery runbook |
| [DATABASE_SECURITY_QUICK_REFERENCE.md](database/DATABASE_SECURITY_QUICK_REFERENCE.md) | Quick reference card for DB security |
| [DATABASE_SECURITY_HARDENING.md](database/DATABASE_SECURITY_HARDENING.md) | Technical database security reference |
| [Migration history index](database/migration-history/INDEX_SECURITY_HARDENING.md) | Historical security-hardening documentation set |

### Detailed Documentation
| Document | Description |
|----------|-------------|
| [SECURITY_HARDENING_SUMMARY.md](database/migration-history/SECURITY_HARDENING_SUMMARY.md) | Historical executive summary of hardening work |
| [DELIVERABLES_SUMMARY.md](database/migration-history/DELIVERABLES_SUMMARY.md) | Historical hardening deliverables |
| [README_SECURITY_HARDENING.md](database/migration-history/README_SECURITY_HARDENING.md) | Historical security-hardening overview |

### V1/V2 Merge Documentation
| Document | Description |
|----------|-------------|
| [COMPLETE_INDEX_V1_V2_MERGE.md](database/migration-history/COMPLETE_INDEX_V1_V2_MERGE.md) | Complete index of V1/V2 merge documentation |
| [MIGRATION_IMPLEMENTATION_V1_TO_V2.md](database/migration-history/MIGRATION_IMPLEMENTATION_V1_TO_V2.md) | Historical V1-to-V2 migration plan |
| [V1_V2_MERGE_COMPLETION_REPORT.md](database/migration-history/V1_V2_MERGE_COMPLETION_REPORT.md) | Historical completion report |
| [V1_V2_MERGE_ACTION_ITEMS.md](database/migration-history/V1_V2_MERGE_ACTION_ITEMS.md) | Historical team checklist |
| [V1_V2_MERGE_DEVELOPER_QUICK_START.md](database/migration-history/V1_V2_MERGE_DEVELOPER_QUICK_START.md) | Historical developer quick start |

---

## 🔑 2FA / MFA Implementation

### Start Here
| Document | Description |
|----------|-------------|
| [2FA_INDEX.md](mfa/2FA_INDEX.md) | 2FA documentation navigation index |
| [2FA_QUICK_REFERENCE.md](mfa/2FA_QUICK_REFERENCE.md) | At-a-glance summary, key tables, commands |

### Implementation Guides
| Document | Description |
|----------|-------------|
| [2FA_IMPLEMENTATION_SUMMARY.md](mfa/2FA_IMPLEMENTATION_SUMMARY.md) | Overview of all 2FA changes delivered |
| [2FA_IMPLEMENTATION_CHECKLIST.md](mfa/2FA_IMPLEMENTATION_CHECKLIST.md) | Completed controls and operational follow-up |
| [2FA_FILES_CREATED.md](mfa/2FA_FILES_CREATED.md) | Current source, test, and migration inventory |
| [2FA_IMPLEMENTATION_GUIDE.md](mfa/history/2FA_IMPLEMENTATION_GUIDE.md) | Historical implementation plan |
| [2FA_SCHEMA_UPDATES.md](mfa/history/2FA_SCHEMA_UPDATES.md) | Historical schema proposal |
| [2FA_RISK_ASSESSMENT.md](mfa/history/2FA_RISK_ASSESSMENT.md) | Historical risk assessment |

### MFA Policy & Architecture
| Document | Description |
|----------|-------------|
| [MFA_POLICY_CHECK_DATABASE_VS_APPLICATION.md](mfa/MFA_POLICY_CHECK_DATABASE_VS_APPLICATION.md) | Where MFA policy is enforced (DB vs app layer) |
| [MFA_REQUIRED_AT_IMPLEMENTATION.md](mfa/MFA_REQUIRED_AT_IMPLEMENTATION.md) | Implementation of `mfa_required_at` |
| [MFA_REQUIRED_AT_COLUMN_ANALYSIS.md](mfa/MFA_REQUIRED_AT_COLUMN_ANALYSIS.md) | Analysis of the `mfa_required_at` design |

---

## 🗄️ Database & Migrations

| Document | Description |
|----------|-------------|
| [API_FUNCTIONS_REFERENCE.md](database/API_FUNCTIONS_REFERENCE.md) | Reference for database API functions |
| [FLYWAY_CONFIGURATION_SETUP.md](database/FLYWAY_CONFIGURATION_SETUP.md) | Flyway configuration and setup guide |
| [FLYWAY_PRODUCTION_SAFETY.md](database/FLYWAY_PRODUCTION_SAFETY.md) | Production safety guidelines for migrations |
| [FLYWAY_REORGANIZATION.md](database/FLYWAY_REORGANIZATION.md) | Flyway migration file reorganization |
| [NAMING_CONVENTION_REFACTOR.md](database/NAMING_CONVENTION_REFACTOR.md) | Database naming convention standards |
| [USER_ROLE_SIMPLIFICATION.md](database/USER_ROLE_SIMPLIFICATION.md) | Simplification of user/role model |
| [USER_ROLE_SIMPLIFICATION_COMPLETE.md](database/USER_ROLE_SIMPLIFICATION_COMPLETE.md) | Completion report for role simplification |

---

## 📐 Design Diagrams (PlantUML)

See the [design diagram index](../design/README.md) for current sources and generated PNGs.

| File | Description |
|------|-------------|
| `architecture/` | Runtime architecture and persistence schema |
| `authentication/` | Login, TOTP, refresh, logout, and request guardrails |
| `history/login/` | Superseded login and token planning material |
| `themes/` | Shared PlantUML styling |

---

## 🛠️ Development Quick Reference

### Architecture (Hexagonal / Ports & Adapters)

```
src/main/java/com/oodesigns/cas/
├── domain/           # Pure business logic, no framework deps
│   ├── value/        # Value objects (Username, Password, TotpSecret, etc.)
│   ├── model/        # Domain models and entities
│   ├── result/       # Sealed result interfaces (LoginResult, etc.)
│   └── service/      # Ports.java - all port interface definitions
├── application/      # Command handlers (LoginCommandHandler, etc.)
└── infrastructure/   # Adapters implementing Ports.* interfaces
    ├── adapter/      # JOOQ, BCrypt, JWT, Bucket4j adapters
    └── config/       # Configuration classes
```

### Key Principles
- **Value Objects:** Extend `ValidatedValue<T>`, private constructor, `of()` factory method
- **Results:** Sealed interfaces with `mapTo()/orElse()` fluent pattern (no exceptions)
- **Sensitive Data:** `Password`/`Credentials` implement `AutoCloseable` (char arrays, cleared after use)
- **Testing:** 100% line coverage enforced via JaCoCo
- **Database:** PostgreSQL + JOOQ, all access through `auth_api` stored functions

### Build & Test Commands
```bash
./gradlew build                                      # Compile + unit tests
./gradlew test                                       # Unit tests with JaCoCo
./gradlew integrationTest                            # Integration tests (no DB)
./gradlew databaseIntegrationTest -PincludeDbTests   # DB tests (requires docker)
./gradlew jacocoTestCoverageVerification             # Verify 100% coverage
```

---

## 📋 Reading Order by Role

### New Developer
1. Root `README.md` → Environment setup
2. This file (`docs/README.md`) → Documentation map
3. `.github/copilot-instructions.md` → Architecture patterns
4. `docs/api/GRPC_API_REFERENCE.md` → Service contract
5. `docs/mfa/2FA_QUICK_REFERENCE.md` → Current MFA behavior

### Security Reviewer
1. `docs/database/DATABASE_SECURITY_HARDENING.md` → Full technical reference
2. `docs/architecture/ARCHITECTURE_DIAGRAMS.md` → Visual security architecture
3. `docs/mfa/history/2FA_RISK_ASSESSMENT.md` → Historical 2FA risk analysis
4. `design/history/login/TOKENS.md` → Historical token design notes

### Architect
1. `design/README.md` → Current design diagram index
2. `docs/architecture/SELECTIVE_PERMISSION_LOADING.md` → Data loading strategy
3. `docs/mfa/MFA_POLICY_CHECK_DATABASE_VS_APPLICATION.md` → Policy enforcement design
4. `docs/architecture/AUTH_POLICY.md` → Authentication rules

---

## 🔄 Document History

All documentation is version-controlled in this repository. If you previously referenced documentation from external sources, it has been consolidated here.

**Last Updated:** July 2026
**Maintained by:** OODesigns team
