# Home Control System - Documentation Index

> **Purpose:** This is the single source of truth for all project documentation. Everything is stored locally in this repository so nothing is lost regardless of external service availability.

---

## 📁 Documentation Structure

```
home-control-system/
├── README.md                          # Project overview, env vars, Flyway setup
├── docs/                              # All written documentation (this directory)
│   ├── README.md                      # ← YOU ARE HERE (master index)
│   ├── Architecture & Design
│   ├── Security Hardening
│   ├── 2FA / MFA Implementation
│   ├── Database & Migrations
│   └── Development Guides
├── design/                            # Visual diagrams (PlantUML)
│   ├── CAS User + Role Schema.puml
│   ├── LoginFlow_SequenceDiagram.puml
│   ├── login/                         # Login flow diagrams & specs
│   └── themes/                        # PlantUML themes
└── .github/
    └── copilot-instructions.md        # AI assistant context (architecture summary)
```

---

## 🏗️ Architecture & Design

| Document | Description |
|----------|-------------|
| [ARCHITECTURE_DIAGRAMS.md](ARCHITECTURE_DIAGRAMS.md) | Visual architecture guide - schema layout, role hierarchy, access control flows, audit trail, compliance mapping |
| [AUTH_POLICY.md](AUTH_POLICY.md) | Authentication policy rules and enforcement |
| [UserCredentialReader_Design.md](UserCredentialReader_Design.md) | Design for the UserCredentialReader component |
| [DatabaseConfig_Refactoring.md](DatabaseConfig_Refactoring.md) | Database configuration refactoring notes |
| [SELECTIVE_PERMISSION_LOADING.md](SELECTIVE_PERMISSION_LOADING.md) | Strategy for loading permissions only when needed |
| [PASSWORD_RESET_VS_LOGIN_FLOWS.md](PASSWORD_RESET_VS_LOGIN_FLOWS.md) | How password reset and login flows differ (terminal branches) |
| [PASSWORD_RESET_2FA_SECURE_FLOW.md](PASSWORD_RESET_2FA_SECURE_FLOW.md) | Secure flow combining password reset with 2FA |

---

## 🔐 Security Hardening (V1→V2 Migration)

### Start Here
| Document | Description |
|----------|-------------|
| [INDEX_SECURITY_HARDENING.md](INDEX_SECURITY_HARDENING.md) | Security hardening documentation index |
| [README_SECURITY_HARDENING.md](README_SECURITY_HARDENING.md) | Master overview of security changes |
| [DATABASE_SECURITY_QUICK_REFERENCE.md](DATABASE_SECURITY_QUICK_REFERENCE.md) | Quick reference card for DB security |

### Detailed Documentation
| Document | Description |
|----------|-------------|
| [DATABASE_SECURITY_HARDENING.md](DATABASE_SECURITY_HARDENING.md) | Complete technical reference (700+ lines) - SECURITY DEFINER, search_path, CIS benchmarks |
| [SECURITY_HARDENING_SUMMARY.md](SECURITY_HARDENING_SUMMARY.md) | Executive summary of hardening work |
| [DELIVERABLES_SUMMARY.md](DELIVERABLES_SUMMARY.md) | All deliverables from security hardening |

### V1/V2 Merge Documentation
| Document | Description |
|----------|-------------|
| [COMPLETE_INDEX_V1_V2_MERGE.md](COMPLETE_INDEX_V1_V2_MERGE.md) | Complete index of V1/V2 merge documentation |
| [MIGRATION_IMPLEMENTATION_V1_TO_V2.md](MIGRATION_IMPLEMENTATION_V1_TO_V2.md) | Implementation details for V1→V2 migration |
| [V1_V2_MERGE_COMPLETION_REPORT.md](V1_V2_MERGE_COMPLETION_REPORT.md) | Completion report with verification results |
| [V1_V2_MERGE_ACTION_ITEMS.md](V1_V2_MERGE_ACTION_ITEMS.md) | Team checklist and testing procedures |
| [V1_V2_MERGE_DEVELOPER_QUICK_START.md](V1_V2_MERGE_DEVELOPER_QUICK_START.md) | 5-minute developer guide for V2 changes |

---

## 🔑 2FA / MFA Implementation

### Start Here
| Document | Description |
|----------|-------------|
| [2FA_INDEX.md](2FA_INDEX.md) | 2FA documentation navigation index |
| [2FA_QUICK_REFERENCE.md](2FA_QUICK_REFERENCE.md) | At-a-glance summary, key tables, commands |

### Implementation Guides
| Document | Description |
|----------|-------------|
| [2FA_IMPLEMENTATION_SUMMARY.md](2FA_IMPLEMENTATION_SUMMARY.md) | Overview of all 2FA changes delivered |
| [2FA_IMPLEMENTATION_GUIDE.md](2FA_IMPLEMENTATION_GUIDE.md) | Phase-by-phase implementation instructions |
| [2FA_IMPLEMENTATION_CHECKLIST.md](2FA_IMPLEMENTATION_CHECKLIST.md) | Task breakdown and progress tracking |
| [2FA_SCHEMA_UPDATES.md](2FA_SCHEMA_UPDATES.md) | Complete database schema for 2FA tables |
| [2FA_FILES_CREATED.md](2FA_FILES_CREATED.md) | Inventory of all new/modified files |
| [2FA_RISK_ASSESSMENT.md](2FA_RISK_ASSESSMENT.md) | Risk assessment for 2FA implementation |

### MFA Policy & Architecture
| Document | Description |
|----------|-------------|
| [MFA_POLICY_CHECK_DATABASE_VS_APPLICATION.md](MFA_POLICY_CHECK_DATABASE_VS_APPLICATION.md) | Where MFA policy is enforced (DB vs app layer) |
| [MFA_REQUIRED_AT_IMPLEMENTATION.md](MFA_REQUIRED_AT_IMPLEMENTATION.md) | Implementation of mfa_required_at column |
| [MFA_REQUIRED_AT_COLUMN_ANALYSIS.md](MFA_REQUIRED_AT_COLUMN_ANALYSIS.md) | Analysis of the mfa_required_at column design |

---

## 🗄️ Database & Migrations

| Document | Description |
|----------|-------------|
| [API_FUNCTIONS_REFERENCE.md](API_FUNCTIONS_REFERENCE.md) | Reference for all auth_api database functions |
| [FLYWAY_CONFIGURATION_SETUP.md](FLYWAY_CONFIGURATION_SETUP.md) | Flyway configuration and setup guide |
| [FLYWAY_PRODUCTION_SAFETY.md](FLYWAY_PRODUCTION_SAFETY.md) | Production safety guidelines for migrations |
| [FLYWAY_REORGANIZATION.md](FLYWAY_REORGANIZATION.md) | Flyway migration file reorganization |
| [NAMING_CONVENTION_REFACTOR.md](NAMING_CONVENTION_REFACTOR.md) | Database naming convention standards |
| [USER_ROLE_SIMPLIFICATION.md](USER_ROLE_SIMPLIFICATION.md) | Simplification of user/role model |
| [USER_ROLE_SIMPLIFICATION_COMPLETE.md](USER_ROLE_SIMPLIFICATION_COMPLETE.md) | Completion report for role simplification |

---

## 📐 Design Diagrams (PlantUML)

Located in `/design/`:

| File | Description |
|------|-------------|
| `CAS User + Role Schema.puml` | Entity relationship diagram for users, roles, permissions |
| `LoginFlow_SequenceDiagram.puml` | High-level login sequence diagram |
| **`login/` directory:** | |
| `0 - Request Guardrails & Rate Limiting (Multi-Key).puml` | Rate limiting architecture |
| `1 - Credential Verification.puml` | Password verification flow |
| `2 - MFA Policy & Challenge.puml` | MFA decision tree |
| `3 - Post Authentication.puml` | Post-auth processing |
| `4 - Authorization & Token Issuance.puml` | Token generation flow |
| `LOGIN_FLOW_DIAGRAMS_README.md` | Complete login flow architecture documentation |
| `TOKENS.md` | MFA Challenge Token specification (TTL, claims, validation) |
| `mfa/Perform MFA challenge.puml` | MFA challenge sequence diagram |

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
4. `docs/V1_V2_MERGE_DEVELOPER_QUICK_START.md` → DB security context
5. `docs/2FA_QUICK_REFERENCE.md` → Current feature work

### Security Reviewer
1. `docs/DATABASE_SECURITY_HARDENING.md` → Full technical reference
2. `docs/ARCHITECTURE_DIAGRAMS.md` → Visual security architecture
3. `docs/2FA_RISK_ASSESSMENT.md` → 2FA risk analysis
4. `design/login/TOKENS.md` → Token security spec

### Architect
1. `design/login/LOGIN_FLOW_DIAGRAMS_README.md` → Complete login architecture
2. `docs/SELECTIVE_PERMISSION_LOADING.md` → Data loading strategy
3. `docs/MFA_POLICY_CHECK_DATABASE_VS_APPLICATION.md` → Policy enforcement design
4. `docs/AUTH_POLICY.md` → Authentication rules

---

## 🔄 Document History

All documentation is version-controlled in this repository. If you previously referenced documentation from external sources, it has been consolidated here.

**Last Updated:** July 2026
**Maintained by:** OODesigns team
