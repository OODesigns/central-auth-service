# Design Diagrams

PlantUML sources and their generated PNGs are grouped by purpose.

## Current Diagrams

### Architecture

- [Runtime architecture PNG](architecture/RuntimeArchitecture.png) ([source](architecture/Runtime_Architecture.puml))
- [Authentication data model PNG](architecture/AuthenticationDataModel.png) ([source](architecture/CAS%20User%20%2B%20Role%20Schema.puml))

### Authentication

- [Complete login and MFA branching PNG](authentication/LoginSequenceDiagram.png) ([source](authentication/LoginFlow_SequenceDiagram.puml))
- [TOTP lifecycle PNG](authentication/TOTP_Lifecycle.png) ([source](authentication/TOTP_Lifecycle.puml))
- [Refresh-token rotation PNG](authentication/RefreshRotation.png) ([source](authentication/Refresh_Token_Rotation.puml))
- [Logout and access-token revocation PNG](authentication/LogoutRevocation.png) ([source](authentication/Logout_Access_Token_Revocation.puml))
- [Request guardrails and rate limiting PNG](authentication/login/0%20-%20Request%20Guardrails%20%26%20Rate%20Limiting%20%28Multi-Key%29.png) ([source](authentication/login/0%20-%20Request%20Guardrails%20%26%20Rate%20Limiting%20%28Multi-Key%29.puml))
- [Credential verification PNG](authentication/login/1%20-%20Credential%20Verification.png) ([source](authentication/login/1%20-%20Credential%20Verification.puml))
- [MFA policy and challenge PNG](authentication/login/2%20-%20MFA%20Policy%20%26%20Challenge.png) ([source](authentication/login/2%20-%20MFA%20Policy%20%26%20Challenge.puml))
- [MFA challenge execution PNG](authentication/login/mfa/Perform%20MFA%20challenge.png) ([source](authentication/login/mfa/Perform%20MFA%20challenge.puml))

### Security operations

- [Deployment security stack PNG](security/DeploymentSecurityStack.png) ([source](security/DeploymentSecurityStack.puml))
- [Internal security enforcement model PNG](security/InternalSecurityEnforcement.png) ([source](security/InternalSecurityEnforcement.puml))
- [Administrator-issued account recovery PNG](security/AdminRecoveryFlow.png) ([source](security/AdminRecoveryFlow.puml))

## Shared Theme

All Central Auth Service diagrams include [themes/dark-theme.puml](themes/dark-theme.puml). Relative include paths must be adjusted when moving a source file.

Sequence diagrams use green backgrounds for successful paths, red backgrounds for failures, and amber backgrounds for required follow-up actions. The shared theme increases text size, removes shadows, and wraps long messages for easier scanning.

## Rendering

With a PlantUML jar available at `$PLANTUML_JAR`:

```bash
find design -type f -name '*.puml' ! -path 'design/themes/*' -print0 \
  | xargs -0 java -Djava.awt.headless=true -jar "$PLANTUML_JAR" -tpng
```

Generated PNGs are stored beside their source files. The current source-to-image names are listed above.