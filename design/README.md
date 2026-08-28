# Design Diagrams

PlantUML sources and their generated PNGs are grouped by purpose.

## Current Diagrams

### Architecture

- [Runtime architecture PNG](architecture/RuntimeArchitecture.png) ([source](architecture/Runtime_Architecture.puml))
- [Authentication data model PNG](architecture/CAS%20User%20%2B%20Role%20Schema.png) ([source](architecture/CAS%20User%20%2B%20Role%20Schema.puml))

### Authentication

- [Complete login and MFA branching PNG](authentication/LoginSequenceDiagram.png) ([source](authentication/LoginFlow_SequenceDiagram.puml))
- [TOTP enrollment PNG](authentication/totp/TOTPEnrollment.png) ([source](authentication/totp/01-TOTP-Enrollment.puml))
- [TOTP verification and disable PNG](authentication/totp/TOTPVerification.png) ([source](authentication/totp/02-TOTP-Verification.puml))
- [TOTP disable PNG](authentication/totp/TOTPDisable.png) ([source](authentication/totp/03-TOTP-Disable.puml))
- [Refresh-token validation PNG](authentication/refresh/RefreshValidation.png) ([source](authentication/refresh/01-Refresh-Validation.puml))
- [Refresh-token rotation outcomes PNG](authentication/refresh/RefreshRotationOutcomes.png) ([source](authentication/refresh/02-Refresh-Rotation-Outcomes.puml))
- [Logout and access-token revocation PNG](authentication/LogoutRevocation.png) ([source](authentication/Logout_Access_Token_Revocation.puml))
- [Request guardrails and rate limiting PNG](authentication/login/0%20-%20Request%20Guardrails%20%26%20Rate%20Limiting%20%28Multi-Key%29.png) ([source](authentication/login/0%20-%20Request%20Guardrails%20%26%20Rate%20Limiting%20%28Multi-Key%29.puml))
- [Credential verification PNG](authentication/login/1%20-%20Credential%20Verification.png) ([source](authentication/login/1%20-%20Credential%20Verification.puml))
- [MFA policy and challenge PNG](authentication/login/2%20-%20MFA%20Policy%20%26%20Challenge.png) ([source](authentication/login/2%20-%20MFA%20Policy%20%26%20Challenge.puml))
- [MFA challenge execution PNG](authentication/login/mfa/Perform%20MFA%20challenge.png) ([source](authentication/login/mfa/Perform%20MFA%20challenge.puml))

### Security operations

- [Deployment security stack PNG](security/DeploymentSecurityStack.png) ([source](security/DeploymentSecurityStack.puml))
- [Internal security enforcement model PNG](security/InternalSecurityEnforcement.png) ([source](security/InternalSecurityEnforcement.puml))
- [Recovery token issuance PNG](security/recovery/RecoveryTokenIssuance.png) ([source](security/recovery/01-Recovery-Token-Issuance.puml))
- [Recovery completion PNG](security/recovery/RecoveryCompletion.png) ([source](security/recovery/02-Recovery-Completion.puml))

## Shared Theme

All Central Auth Service diagrams include [themes/dark-theme.puml](themes/dark-theme.puml). Relative include paths must be adjusted when moving a source file.

The complete TOTP, refresh-token, and administrator recovery sources remain available at `authentication/TOTP_Lifecycle.puml`, `authentication/Refresh_Token_Rotation.puml`, and `security/AdminRecoveryFlow.puml` as end-to-end references. Their focused replacements are grouped in `authentication/totp/`, `authentication/refresh/`, and `security/recovery/` for easier reading.

Sequence diagrams use soft, semi-transparent semantic colors: pastel pink `#F3B6BE99` for failures, pastel green `#C7E8C999` for successful paths, and amber for required follow-up actions. Object colors are grouped by type: blue for participants and actors, green for data stores and entities, yellow for components and interfaces, lavender for containers, and neutral gray for activities. The shared theme increases text size, removes shadows, and wraps long messages for easier scanning.

## Rendering

With a PlantUML jar available at `$PLANTUML_JAR`:

```bash
find design -type f -name '*.puml' ! -path 'design/themes/*' -print0 \
  | xargs -0 java -Djava.awt.headless=true -jar "$PLANTUML_JAR" -tpng
```

Generated PNGs are stored beside their source files. The current source-to-image names are listed above.