# Design Diagrams

PlantUML sources and their generated PNGs are grouped by purpose.

## Current Diagrams

### Architecture

- [Runtime architecture](architecture/Runtime_Architecture.puml)
- [Authentication data model](architecture/CAS%20User%20%2B%20Role%20Schema.puml)

### Authentication

- [Complete login and MFA branching](authentication/LoginFlow_SequenceDiagram.puml)
- [TOTP lifecycle](authentication/TOTP_Lifecycle.puml)
- [Refresh-token rotation](authentication/Refresh_Token_Rotation.puml)
- [Logout and access-token revocation](authentication/Logout_Access_Token_Revocation.puml)
- [Request guardrails and rate limiting](authentication/login/0%20-%20Request%20Guardrails%20%26%20Rate%20Limiting%20%28Multi-Key%29.puml)
- [Credential verification](authentication/login/1%20-%20Credential%20Verification.puml)
- [MFA policy and challenge](authentication/login/2%20-%20MFA%20Policy%20%26%20Challenge.puml)
- [MFA challenge execution](authentication/login/mfa/Perform%20MFA%20challenge.puml)

## Historical Diagrams

The files under `history/login/` describe superseded REST-era or proposed behavior and are retained for design history:

- [Post-authentication proposal](history/login/3%20-%20Post%20Authentication.puml)
- [Authorization and token-issuance proposal](history/login/4%20-%20Authorization%20%26%20Token%20Issuance.puml)
- [Historical login-flow guide](history/login/LOGIN_FLOW_DIAGRAMS_README.md)
- [Historical token notes](history/login/TOKENS.md)

## Shared Theme

All Central Auth Service diagrams include [themes/dark-theme.puml](themes/dark-theme.puml). Relative include paths must be adjusted when moving a source file.

## Rendering

With a PlantUML jar available at `$PLANTUML_JAR`:

```bash
find design -type f -name '*.puml' ! -path 'design/themes/*' -print0 \
  | xargs -0 java -Djava.awt.headless=true -jar "$PLANTUML_JAR" -tpng
```

Generated PNGs are stored beside their source files.