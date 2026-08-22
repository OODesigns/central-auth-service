---
title: Authentication & 2FA Trust Boundary Policy
date: 2026-01-09
audience: Backend Engineers, Security Team
---

# Authentication & 2FA Trust Boundary Policy

⚠️ **CRITICAL:** This document defines where authentication enforcement happens and where it doesn't.

---

## Trust Boundary: Application, Not Database

### The Rule

```
┌─────────────────────────────────────────────────────────┐
│ APPLICATION LAYER (Trust Boundary)                      │
│                                                          │
│ LoginCommandHandler                                     │
│ ↓ Verifies password                                     │
│ ↓ Checks 2FA requirement (mfa_required_at)             │
│ ↓ Checks 2FA verification (totp_verified_at)           │
│ ↓ Issues TokenPair only if all checks pass             │
│                                                          │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│ DATABASE LAYER (No Auth Checks)                         │
│                                                          │
│ TokenService.generateTokens()                           │
│ - Does NOT check mfa_required_at                        │
│ - Does NOT check totp_verified_at                       │
│ - Issues tokens based on User object only              │
│                                                          │
│ ⚠️ Database will NOT prevent rogue code from issuing   │
│    tokens if LoginCommandHandler is bypassed            │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

### Why This Design?

1. **Separation of Concerns**
   - Database: Data integrity, cascading deletes
   - Application: Business logic, auth policies

2. **Flexibility**
   - Different auth endpoints (OAuth, SAML, LDAP) can exist
   - All must implement same MFA checks
   - Database doesn't enforce "one way to auth"

3. **Performance**
   - No database constraints on token issuance
   - Auth checks happen in memory, not via queries

### The Risk

If someone writes code like this:

```java
// ❌ WRONG: Bypasses LoginCommandHandler
User user = userRepository.findById(userId).orElseThrow();
TokenService.TokenPair tokens = tokenService.generateTokens(user);
return tokens;
```

The database **will not prevent** this, even if:
- `user.mfaRequiredAt IS NOT NULL` (2FA is required)
- `totp_secrets.verified_at IS NULL` (2FA not verified)

The tokens will be issued anyway.

---

## Required MFA Checks

These checks **MUST** happen before issuing tokens, in this order:

1. If policy requires MFA and no verified TOTP secret exists, return the `LoginResponse.error` branch with `MFA_SETUP_REQUIRED`. The client starts enrollment with `SetupTotp`.
2. If a verified TOTP secret exists, return `LoginResponse.totp_required` with a short-lived verification token. This applies whether MFA is optional or required.
3. The client calls `VerifyTotp` with that token and a TOTP or backup code. Invalid codes return `VerifyTotpResponse.error` with `INVALID_TOTP_CODE`.
4. Only `VerifyTotp` issues the full access and refresh token pair for an enrolled user.
5. If TOTP is not enrolled and policy does not require it, evaluate the password-reset requirement before issuing tokens.

See [GRPC_API_REFERENCE.md](../api/GRPC_API_REFERENCE.md) for the complete wire contract and error-code matrix.

---

## Where These Checks MUST Happen

### Correct Implementations

**In LoginCommandHandler:**
```java
credentialReader.findCredentialsByUsername(command.username())
    .map(cred -> Credentials.of(cred, command.password()))
    .flatMap(authService::getAuthenticatedUser)
    .flatMap(this::getResponse);
```

`buildLoginResponse` enforces enrollment, challenge, password-reset, and token-issuance ordering. `VerifyTotpCommandHandler` validates the restricted token and code before issuing full tokens.

### Incorrect Implementations (Will Bypass MFA)

**Direct TokenService call:**
```java
// ❌ WRONG - No MFA checks
User user = userRepository.findById(userId).orElseThrow();
TokenService.TokenPair tokens = tokenService.generateTokens(user);
response.setTokens(tokens);  // Issued without MFA verification!
```

**Skipping totpStatusReader check:**
```java
// ❌ WRONG - Assumes MFA is not required
User user = userRepository.findById(userId).orElseThrow();
// If mfa_required_at != null, we should have stopped here!
return LoginResult.success(tokens, user.userId(), user.permissions());
```

**Not verifying TOTP code:**
```java
// ❌ WRONG - Token issued without TOTP verification
if (user.mfaRequiredAt() != null) {
    // User has MFA required but...
    // We're issuing tokens without verifying TOTP code!
    return LoginResult.success(tokens, ...);
}
```

---

## Audit Trail Consequences

### What Gets Logged

```sql
-- If MFA checks pass:
INSERT INTO audit_logs VALUES (action='TOKEN_ISSUED', ...);

-- If MFA checks fail:
INSERT INTO audit_logs VALUES (action='TOKEN_INVALIDATED', ...);
-- No TOKEN_ISSUED row
```

### The Risk

If you bypass LoginCommandHandler:

```sql
-- User without TOTP enrolled tries to login
UPDATE users SET mfa_required_at = NOW() WHERE user_id = 'abc-123';

-- Rogue service issues token anyway
-- Database logs:
INSERT INTO audit_logs VALUES (action='TOKEN_ISSUED', ...);
-- ❌ WRONG: Token issued for user without verified 2FA!

-- Later investigation:
SELECT * FROM audit_logs WHERE action='TOKEN_ISSUED' AND target_id='abc-123';
-- Shows token was issued, but DB didn't prevent it
-- Auth policy was violated in application layer
```

---

## Code Checklist

When issuing tokens, you **MUST** verify:

- [ ] User provided valid username/password (or other primary auth)
- [ ] User is not disabled/deleted
- [ ] If `user.mfaRequiredAt IS NOT NULL`:
  - [ ] User has TOTP enrolled (`totp_secrets.verified_at IS NOT NULL`)
  - [ ] User provided valid TOTP code (if TOTP is enabled)
- [ ] Rate limiting checks passed
- [ ] Session checks passed (if applicable)
- [ ] Only THEN call `tokenService.generateTokens(user)`

**Recommended Pattern:**
```java
public LoginResult authenticateUser(final LoginCommand command) {
    // Step 1: Verify password
    Optional<User> user = passwordVerification(command);
    if (user.isEmpty()) {
        return LoginResult.failure("INVALID_CREDENTIALS", "...");
    }
    
    // Step 2: Check 2FA requirement
    Optional<UserId> has2FA = totpStatusReader.check2FAStatus(user.get().userId());
    if (user.get().mfaRequiredAt() != null && has2FA.isEmpty()) {
        return LoginResult.totpRequired(generateRestrictedToken(...), ...);
    }
    
    // Step 3: Issue full tokens (only after all checks pass)
    TokenPair tokens = tokenService.generateTokens(user.get());
    return LoginResult.success(tokens, ...);
}
```

---

## Questions for Security Team

1. **Database Enforcement:** Do we want a CHECK constraint preventing `mfa_required_at != NULL AND verified_at IS NULL` at token issuance time?
   - **Pro:** Catches rogue code
   - **Con:** Duplicates application logic, slow

2. **Internal Service Auth:** Can internal services bypass MFA checks?
   - **Recommendation:** No. All auth paths through LoginCommandHandler

3. **Audit Alert:** Should we alert if `TOKEN_ISSUED` occurs for user with `mfa_required_at IS NOT NULL AND verified_at IS NULL`?
   - **Recommendation:** Yes. This is a policy violation

4. **Key Rotation:** How do we detect/respond if unauthorized tokens are issued?
   - **Recommendation:** Regular token revocation audit, correlation with `TOKEN_INVALIDATED` logs

---

## Summary

| Aspect | Responsibility |
|--------|-----------------|
| **Auth Decisions** | Application Layer (LoginCommandHandler) |
| **Data Integrity** | Database Layer (ON DELETE CASCADE, constraints) |
| **Trust Boundary** | Login Command Handler only |
| **Enforcement** | 100% in application code |
| **Database Role** | Store state, not enforce policy |

---

## Links

- Architecture diagrams: [ARCHITECTURE_DIAGRAMS.md](ARCHITECTURE_DIAGRAMS.md)
- gRPC contract: [GRPC_API_REFERENCE.md](../api/GRPC_API_REFERENCE.md)
- Historical risk assessment: [2FA_RISK_ASSESSMENT.md](../mfa/history/2FA_RISK_ASSESSMENT.md)
- Historical implementation plan: [2FA_IMPLEMENTATION_GUIDE.md](../mfa/history/2FA_IMPLEMENTATION_GUIDE.md)

---

**Last Updated:** 2026-01-09  
**Status:** ✅ Production Policy  
**Contact:** Security Team

