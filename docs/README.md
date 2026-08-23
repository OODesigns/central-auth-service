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
│   ├── mfa/                           # Current TOTP/MFA documentation
│   └── project/                       # Production rollout and operations
├── design/                            # Visual diagrams (PlantUML + PNG)
│   ├── README.md                      # Design diagram index
│   ├── architecture/                  # Runtime and persistence models
│   ├── authentication/                # Current authentication flows
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

---

## 🔐 Security & Operations

| Document | Description |
|----------|-------------|
| [SECURITY_ROLLOUT.md](project/SECURITY_ROLLOUT.md) | Production automation, migrations, TLS, secrets, rotation, scanning, backups, monitoring, and recovery runbook |

---

## 🔑 2FA / MFA

| Document | Description |
|----------|-------------|
| [2FA_INDEX.md](mfa/2FA_INDEX.md) | 2FA documentation navigation index |
| [2FA_QUICK_REFERENCE.md](mfa/2FA_QUICK_REFERENCE.md) | At-a-glance summary, key tables, commands |

---

## 📐 Design Diagrams (PlantUML)

See the [design diagram index](../design/README.md) for current sources and generated PNGs.

| File | Description |
|------|-------------|
| `architecture/` | Runtime architecture and persistence schema |
| `authentication/` | Login, TOTP, refresh, logout, and request guardrails |
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
- **Database:** PostgreSQL + JOOQ, all application access through `api_schema` functions

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
1. `docs/architecture/ARCHITECTURE_DIAGRAMS.md` → Visual security architecture
2. `docs/api/GRPC_API_REFERENCE.md` → Service contract and error model
3. `docs/mfa/2FA_QUICK_REFERENCE.md` → Current MFA security properties
4. `docs/project/SECURITY_ROLLOUT.md` → Production security operations

### Architect
1. `design/README.md` → Current design diagram index
2. `docs/architecture/ARCHITECTURE_DIAGRAMS.md` → Current architecture and flow index
3. `docs/api/GRPC_API_REFERENCE.md` → Service boundary and outcomes
4. `docs/mfa/2FA_QUICK_REFERENCE.md` → Current MFA behavior
