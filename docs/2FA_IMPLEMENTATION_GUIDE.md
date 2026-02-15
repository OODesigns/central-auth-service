# 2FA Implementation Guide for Developers

## Overview

This guide provides step-by-step instructions for implementing the 2FA (TOTP) infrastructure adapters and application layer handlers to complete the 2FA feature.

## Phase 1: Infrastructure Adapters

### 1.1 TOTP Code Generator Adapter

Create `src/main/java/com/oodesigns/cas/infrastructure/adapter/TotpCodeGenerator.java`

**Responsibilities:**
- Generate TOTP secrets (base32-encoded random bytes)
- Verify TOTP codes against secrets
- Handle clock skew tolerance

**Dependencies:**
- Apache Commons Codec (for Base32 encoding)
- TOTP4J or similar library for TOTP verification
- System clock for time-based validation

**Key Methods:**
```java
public class TotpCodeGenerator {
    public String generateSecret()          // Generate 160-bit random secret
    public boolean verifyCode(String secret, String code)  // Verify 6-digit code
    public String getQrCodeUrl(String secret, String username)  // QR code generation
}
```

### 1.2 Backup Code Generator Adapter

Create `src/main/java/com/oodesigns/cas/infrastructure/adapter/BackupCodeGenerator.java`

**Responsibilities:**
- Generate backup codes in format XXXX-XXXX-XXXX-XXXX
- Hash backup codes for storage
- Validate plaintext codes against hashes

**Dependencies:**
- BCryptPasswordEncoder (already in project)
- Secure random number generator

**Key Methods:**
```java
public class BackupCodeGenerator {
    public List<String> generateCodes(int count)  // Generate plaintext codes
    public String hashCode(String code)           // Hash for storage
    public boolean verifyCode(String plaintext, String hash)  // Verify code
}
```

### 1.3 TOTP Verifier Adapter

Create `src/main/java/com/oodesigns/cas/infrastructure/adapter/JooqTotpVerifier.java`

**Implements:** `Ports.TotpVerifier`

**Responsibilities:**
- Query TOTP secret from database
- Verify OTP codes
- Manage backup code consumption
- Check 2FA enabled status

**Database Operations:**
```sql
SELECT secret_key FROM totp_secrets WHERE user_id = ? AND verified_at IS NOT NULL
UPDATE backup_codes SET used_at = NOW() WHERE user_id = ? AND code_hash = ? AND used_at IS NULL
SELECT COUNT(*) FROM backup_codes WHERE user_id = ? AND used_at IS NULL
```

### 1.4 TOTP Setup Provider Adapter

Create `src/main/java/com/oodesigns/cas/infrastructure/adapter/JooqTotpSetupProvider.java`

**Implements:** `Ports.TotpSetupProvider`

**Responsibilities:**
- Generate and store new TOTP secrets
- Enable/disable TOTP
- Generate and store backup codes
- Manage TOTP lifecycle

**Database Operations:**
```sql
INSERT INTO totp_secrets (user_id, secret_key, ...) VALUES (?, ?, ...)
UPDATE totp_secrets SET verified_at = NOW() WHERE user_id = ?
UPDATE totp_secrets SET backup_codes_generated_at = NOW() WHERE user_id = ?
INSERT INTO backup_codes (user_id, code_hash) VALUES (?, ?)
DELETE FROM totp_secrets WHERE user_id = ?
DELETE FROM backup_codes WHERE user_id = ?
```

## Phase 2: Application Layer Handlers

### 2.1 Enable TOTP Command

Create `src/main/java/com/oodesigns/cas/application/command/EnableTotpCommand.java`

```java
public record EnableTotpCommand(
    UserId userId,
    String totpCode  // 6-digit verification code
) {}
```

### 2.2 Enable TOTP Command Handler

Create `src/main/java/com/oodesigns/cas/application/command/EnableTotpCommandHandler.java`

**Flow:**
1. Retrieve pending TOTP secret for user
2. Verify provided code against secret
3. If valid:
   - Call `setupProvider.enableTotp(userId)`
   - Generate backup codes
   - Return codes to user
4. If invalid:
   - Return failure result

### 2.3 Verify TOTP Command

Create `src/main/java/com/oodesigns/cas/application/command/VerifyTotpCommand.java`

```java
public record VerifyTotpCommand(
    UserId userId,
    String totpCode  // 6-digit code OR backup code
) {}
```

### 2.4 Verify TOTP Command Handler

Create `src/main/java/com/oodesigns/cas/application/command/VerifyTotpCommandHandler.java`

**Flow:**
1. Check if input is 6-digit code or backup code format
2. If 6-digit:
   - Call `verifier.verifyCode(userId, code)`
   - Return success if valid
3. If backup code format:
   - Call `verifier.verifyBackupCode(userId, code)`
   - Return success if valid
4. Return failure if both invalid

## Phase 3: Update Authentication Flow

### 3.1 Modify LoginCommandHandler

**Current Flow:**
```
Password verification → Issue tokens
```

**New Flow:**
```
Password verification → Check TOTP enabled
├─ FALSE: Issue tokens
└─ TRUE: Require TOTP verification
    ├─ Verify OTP code: Issue tokens
    └─ Verify backup code: Issue tokens (mark code as used)
```

**Implementation:**
```java
private LoginResult authenticateUser(final LoginCommand command) {
    return credentialReader.findCredentialsByUsername(command.username())
        .map(cred -> Credentials.of(cred, command.password()))
        .flatMap(authService::getAuthenticatedUser)
        .flatMap(user -> {
            if (totpVerifier.isTotpEnabled(user.userId())) {
                // Require TOTP in separate request
                return createPendingTotpResult(user);
            } else {
                // Continue normal flow
                return authenticateWithoutTotp(user);
            }
        })
        .orElseGet(() -> LoginResult.failure("INVALID_CREDENTIALS", "..."));
}
```

## Phase 4: REST Endpoints

### 4.1 Setup 2FA

```
POST /auth/2fa/setup

Response:
{
  "secret": "JBSWY3DPEBLW64TMMQ======",
  "qrCode": "data:image/png;base64,...",
  "setupCode": "uuid-for-verification"
}
```

### 4.2 Verify Setup

```
POST /auth/2fa/setup/verify

Request:
{
  "setupCode": "uuid-from-setup",
  "totpCode": "123456"
}

Response:
{
  "backupCodes": [
    "ABCD-EFGH-IJKL-MNOP",
    ...
  ]
}
```

### 4.3 Verify 2FA During Login

```
POST /auth/login/verify-totp

Request:
{
  "sessionToken": "pending-totp-token",
  "totpCode": "123456"  // or backup code
}

Response:
{
  "accessToken": "...",
  "refreshToken": "..."
}
```

### 4.4 Disable 2FA

```
DELETE /auth/2fa

Requires:
- Admin role, or
- User providing current password for confirmation
```

### 4.5 Generate New Backup Codes

```
POST /auth/2fa/backup-codes

Response:
{
  "backupCodes": [
    "ABCD-EFGH-IJKL-MNOP",
    ...
  ]
}
```

## Phase 5: Testing Strategy

### Unit Tests

1. **Value Objects:**
   - `TotpSecretTest.java` ✓ (already created)
   - `BackupCodeTest.java` ✓ (already created)

2. **Adapters:**
   - `TotpCodeGeneratorTest.java` - Test secret generation and verification
   - `BackupCodeGeneratorTest.java` - Test code generation and hashing
   - `JooqTotpVerifierTest.java` - Mock JOOQ queries
   - `JooqTotpSetupProviderTest.java` - Mock JOOQ queries

3. **Command Handlers:**
   - `EnableTotpCommandHandlerTest.java` - Mock setup provider
   - `VerifyTotpCommandHandlerTest.java` - Mock verifier

4. **Integration:**
   - `TotpDatabaseIntegrationTest.java` - Test with real database

### Test Coverage Requirements

- 100% line coverage on new code (enforced by JaCoCo)
- Edge cases:
  - Clock skew tolerance (±30 seconds)
  - Multiple backup code usage attempts
  - TOTP during concurrent requests
  - Backup code hash verification failures

## Security Checklist

- [ ] TOTP secrets encrypted at rest (consider column encryption)
- [ ] Backup codes hashed with bcrypt (20+ rounds)
- [ ] Rate limiting on OTP verification (max 5 attempts per 30 seconds)
- [ ] Rate limiting on backup code usage (max 10 attempts per minute)
- [ ] Remove secrets from logs/error messages
- [ ] Audit trail for all 2FA operations
- [ ] Secure random number generation for codes
- [ ] TLS/HTTPS for all 2FA endpoints
- [ ] CORS restrictions on 2FA endpoints
- [ ] CSRF tokens on setup endpoints

## Performance Considerations

1. **TOTP Verification:**
   - Cache active secrets in Redis (5-min TTL)
   - Index on `totp_secrets(user_id)` ✓ (already created)
   - Index on `totp_secrets(verified_at)` ✓ (already created)

2. **Backup Codes:**
   - Index on `backup_codes(user_id)` ✓ (already created)
   - Batch delete unused codes periodically
   - Cache user's TOTP enabled flag

3. **QR Code Generation:**
   - Cache QR codes (30-min TTL, user-scoped)
   - Generate on-demand only if not cached

## Monitoring & Metrics

Implement metrics for:
- TOTP setup success rate
- TOTP verification failures (by reason: invalid code, expired, etc.)
- Backup code usage rate
- 2FA adoption percentage
- Average time to complete 2FA setup
- Rate limit violations

## Deployment Notes

1. **Database Migration:**
   - Run `V1__init_schema.sql` to create tables
   - Optionally run `V1_2__add_totp_test_data.sql` for testing

2. **Backward Compatibility:**
   - Existing users have `totp_verified_at = NULL` (2FA disabled by default)
   - No breaking changes to authentication API
   - TOTP is additive feature

3. **Rollback Plan:**
   - If TOTP adapter fails: set `totp_verified_at = NULL` for all users
   - Backup codes remain for recovery
   - Original login flow still works

4. **Admin Operations:**
   - Support tool to reset user's 2FA
   - Support tool to generate emergency backup codes
   - Audit log review tools for 2FA investigations

## Dependencies

Add to `build.gradle`:
```gradle
// TOTP library (if not already present)
implementation 'com.warrenstrange:googleauth:1.5.0'

// QR Code generation (if needed)
implementation 'com.google.zxing:core:3.5.1'
implementation 'com.google.zxing:javase:3.5.1'

// Already in project:
// - org.springframework.security:spring-security-crypto
// - io.github.bucket4j:bucket4j-core (rate limiting)
```

## References

- RFC 6238: TOTP Algorithm
- RFC 4648: Base32 Encoding
- OWASP 2FA Implementation Guide
- Google Authenticator Protocols

## Timeline Estimate

- Phase 1 (Adapters): 2-3 days
- Phase 2 (Handlers): 1-2 days
- Phase 3 (Auth flow): 1 day
- Phase 4 (Endpoints): 1-2 days
- Phase 5 (Testing): 2-3 days
- **Total**: 7-11 days for complete implementation

