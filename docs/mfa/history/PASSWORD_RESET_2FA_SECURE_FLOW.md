---
title: Secure Flow: Password Reset + 2FA Setup
date: 2026-01-11
---

> **Historical planning document.** This document describes REST password-reset endpoints and proposed reset-token behavior not exposed by the current `auth.proto`. See [PROJECT_STATUS_AND_COMPLETION_PLAN.md](../../project/PROJECT_STATUS_AND_COMPLETION_PLAN.md).

# Secure Flow: Password Reset + 2FA Setup

## Current Schema State

Users table has:
- `password_reset_required_at` TIMESTAMPTZ (default now())
- `mfa_required_at` TIMESTAMPTZ (NULL until 2FA is enforced)

**Key States:**
```sql
-- New user after seed data
password_reset_required_at = NOW()  -- Must reset password
mfa_required_at = NULL              -- 2FA optional

-- After admin enforces 2FA
password_reset_required_at = NULL   -- Password OK
mfa_required_at = NOW()             -- Must set up 2FA

-- After both completed
password_reset_required_at = NULL   -- Password set
mfa_required_at = NULL (or NOT NULL) -- 2FA enrolled or optional
```

---

## The Secure Flow

### 1. Initial Login Attempt (New User)

```
User tries: login(username, password)
  ↓
Check: password_reset_required_at IS NOT NULL?
  → YES: Block login, return "PASSWORD_RESET_REQUIRED"
  → NO: Continue
  ↓
Check: mfa_required_at IS NOT NULL AND totp_verified_at IS NULL?
  → YES: Block login, return "MFA_SETUP_REQUIRED"
  → NO: Continue
  ↓
Issue tokens (if no other blocks)
```

### 2. Password Reset Required

```
User must call: resetPassword(username, oldPassword, newPassword)
  ↓
1. Verify old password is correct
2. Hash new password
3. UPDATE users SET password_hash = ?, password_reset_required_at = NULL
4. Return: "Password reset successful"
  ↓
User can now login (if 2FA not required)
```

### 3. 2FA Setup Required (After Password Reset)

```
User calls: setupOrEnroll2FA()
  ↓
Check: Can only setup if user is authenticated
  (Password must be valid, no password_reset_required_at)
  ↓
1. Generate TOTP secret
2. Return QR code + secret
3. INSERT INTO totp_secrets with verified_at = NULL
  ↓
User scans QR code, enters code:
  ↓
4. Verify TOTP code is correct
5. UPDATE totp_secrets SET verified_at = NOW()
6. Return: "2FA enabled successfully"
  ↓
User can now login normally
```

---

## Recommended Code: Authentication Flow Update

### New Result Type: AuthStatus

Add to `LoginResult.java`:

```java
public sealed interface LoginResult {
    record Success(...) implements LoginResult {}
    record Required2FA(...) implements LoginResult {}
    record PasswordResetRequired(UserId userId) implements LoginResult {}
    record Failure(...) implements LoginResult {}
}
```

### Updated LoginCommandHandler

```java
/**
 * Authenticate user with mandatory password reset and 2FA enforcement.
 * 
 * Flow:
 * 1. Verify password
 * 2. Check if password reset is required
 * 3. Check if 2FA setup is required
 * 4. Return appropriate result
 * 
 * Blocking order (strict security):
 * - password_reset_required_at IS NOT NULL → Block with PASSWORD_RESET_REQUIRED
 * - mfa_required_at IS NOT NULL AND verified_at IS NULL → Block with MFA_SETUP_REQUIRED
 * - All checks pass → Issue tokens
 */
private LoginResult authenticateUser(final LoginCommand command) {
    return credentialReader.findCredentialsByUsername(command.username())
        .map(cred -> Credentials.of(cred, command.password()))
        .flatMap(authService::getAuthenticatedUser)
        .flatMap(this::checkPasswordReset)      // ← NEW: Check password first
        .flatMap(this::check2FASetup)           // ← NEW: Then check 2FA
        .flatMap(this::getResponse)             // ← EXISTING: Issue tokens
        .orElseGet(() -> LoginResult.failure(...));
}

/**
 * Check if user must reset password before proceeding.
 * Blocking check: if password_reset_required_at IS NOT NULL, user CANNOT login.
 * 
 * @param userId authenticated user ID (password already verified)
 * @return PasswordResetRequired if reset needed, Success for continue
 */
private Optional<LoginResult> checkPasswordReset(final UserId userId) {
    Optional<User> user = userRepository.findById(userId);
    
    if (user.isPresent() && user.get().passwordResetRequiredAt() != null) {
        // Block login: password reset is mandatory
        return Optional.of(LoginResult.passwordResetRequired(userId));
    }
    
    return Optional.of(LoginResult.success(...)); // Placeholder to continue chain
}

/**
 * Check if user must setup 2FA before proceeding.
 * Blocking check: if mfa_required_at IS NOT NULL AND verified_at IS NULL, user CANNOT login.
 * 
 * @param userId authenticated user ID (password verified, no reset required)
 * @return Required2FA if setup needed, continue to token generation
 */
private Optional<LoginResult> check2FASetup(final UserId userId) {
    final Optional<User> user = userRepository.findById(userId);
    
    if (user.isPresent() && user.get().mfaRequiredAt() != null) {
        // Check if 2FA is actually verified
        final Optional<UserId> has2FA = totpStatusReader.check2FAStatus(userId);
        
        if (has2FA.isEmpty()) {
            // Block login: 2FA is required but not set up
            return Optional.of(LoginResult.required2FA(
                tokenService.generate2FASetupToken(userId),  // ← Different token for setup
                userId
            ));
        }
    }
    
    // All checks passed, proceed to token generation
    return getResponse(userId);
}
```

---

## Secure Ordering: Why This Matters

### WRONG ORDER (Insecure):
```
1. Check 2FA first
2. Check password reset later

Problem: User can get stuck in 2FA setup loop while password_reset_required_at = TRUE
         They never set new password, so 2FA token would work but is pointless
```

### RIGHT ORDER (Secure):
```
1. Check password reset FIRST (highest priority)
2. Check 2FA setup second
3. Issue tokens third

Reason: Password is foundation. User cannot proceed until password is reset.
        Only after password is valid can they setup 2FA.
        Only after both are valid can they login normally.
```

---

## State Machine Diagram

```
┌─────────────────────────────────────────────────────────────┐
│ New User (or Password Reset Forced)                         │
│ password_reset_required_at = NOW()                          │
│ mfa_required_at = NULL (or NOT NULL if 2FA enforced)       │
└─────────────────────────────────────────────────────────────┘
                        ↓
            [Login Attempt]
                        ↓
          [Check: password_reset_required_at]
                        ↓
                    NO ↙ ↘ YES
                   /     \
        [Continue] ← Block: PASSWORD_RESET_REQUIRED
            ↓          ↓
      [User calls: resetPassword(old, new)]
            ↓
    [UPDATE password_hash, SET password_reset_required_at = NULL]
            ↓
     [Password Reset Complete]
            ↓
┌─────────────────────────────────────────────────────────────┐
│ User After Password Reset                                   │
│ password_reset_required_at = NULL ✓                         │
│ mfa_required_at = NOW() (if 2FA enforced)                  │
└─────────────────────────────────────────────────────────────┘
                        ↓
            [Login Attempt]
                        ↓
         [Check: mfa_required_at IS NOT NULL
              AND totp_verified_at IS NULL]
                        ↓
                    NO ↙ ↘ YES
                   /     \
        [Issue Tokens]  Block: MFA_SETUP_REQUIRED
            ↓                ↓
        [Success]    [User calls: setupOrEnroll2FA()]
                          ↓
                  [1. Generate secret, show QR]
                          ↓
                  [2. User scans and enters code]
                          ↓
                  [3. UPDATE totp_secrets.verified_at = NOW()]
                          ↓
                  [2FA Setup Complete]
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ User After Both Complete                                    │
│ password_reset_required_at = NULL ✓                         │
│ totp_secrets.verified_at = NOT NULL ✓                       │
└─────────────────────────────────────────────────────────────┘
                        ↓
            [Login Attempt]
                        ↓
            [All checks pass]
                        ↓
           [Issue full tokens]
                        ↓
            [Success - User logged in]
```

---

## New Result Types Needed

### In LoginResult.java

```java
/**
 * Password reset is required before user can proceed.
 * User must call POST /auth/reset-password endpoint.
 */
record PasswordResetRequired(UserId userId) implements LoginResult {
    public PasswordResetRequired {
        Objects.requireNonNull(userId, "User ID is required");
    }

    @Override
    public <T> Mapper<T> mapTo(final Function<SuccessResult, T> successMapper) {
        return new MapperPasswordResetRequired<>(this);
    }

    static final class MapperPasswordResetRequired<T> implements Mapper<T> {
        private final PasswordResetRequired result;

        MapperPasswordResetRequired(final PasswordResetRequired result) {
            this.result = result;
        }

        @Override
        public T orElse(final Function<FailureResult, T> failureMapper) {
            return failureMapper.apply(new FailureResult(
                "PASSWORD_RESET_REQUIRED",
                "Password reset is mandatory. Use /auth/reset-password endpoint."
            ));
        }
    }
}
```

### In LoginCommandHandler

Add new methods:

```java
/**
 * Check if password reset is mandatory.
 * Blocks login until password is updated.
 */
private Optional<LoginResult> checkPasswordReset(final UserId userId) {
    // Fetch user from DB
    // Check password_reset_required_at IS NOT NULL
    // Return PasswordResetRequired if needed
}

/**
 * Check if 2FA setup is mandatory.
 * Blocks login until 2FA is enrolled.
 */
private Optional<LoginResult> check2FASetup(final UserId userId) {
    // Check mfa_required_at IS NOT NULL
    // Check totp_verified_at IS NULL
    // Return Required2FA if needed
}
```

---

## REST Endpoints Needed

### 1. Reset Password (Before Login)
```
POST /auth/reset-password
{
  "username": "john_doe",
  "oldPassword": "current_password",
  "newPassword": "new_secure_password"
}

Response 200:
{
  "status": "success",
  "message": "Password reset successful. You can now login."
}
```

### 2. Setup 2FA (After Password Reset)
```
POST /auth/2fa/setup/initiate
(No auth required - user proves identity via password first)

Response 200:
{
  "status": "totp_required",
  "qrCode": "data:image/png;base64,...",
  "secret": "JBSWY3DPEBLW64TMMQ======",
  "setupToken": "restricted_token_..."
}

---

POST /auth/2fa/setup/verify
{
  "setupToken": "restricted_token_...",
  "totpCode": "123456"
}

Response 200:
{
  "status": "success",
  "message": "2FA enabled. You can now login."
}
```

### 3. Login (Main Path)
```
POST /auth/login
{
  "username": "john_doe",
  "password": "new_secure_password",
  "ipAddress": "192.168.1.1"
}

Responses:
{
  "status": "password_reset_required",
  "userId": "uuid",
  "message": "Password reset required"
} → User must call /auth/reset-password

{
  "status": "mfa_setup_required",
  "setupToken": "...",
  "userId": "uuid",
  "message": "2FA setup required"
} → User must call /auth/2fa/setup/

{
  "status": "totp_required",
  "verificationToken": "...",
  "userId": "uuid",
  "message": "Enter 2FA code"
} → User must call /auth/verify-2fa with TOTP code

{
  "status": "success",
  "accessToken": "...",
  "refreshToken": "...",
  "userId": "uuid",
  "permissions": ["read", "write"]
} → User is authenticated
```

---

## Summary: Secure Ordering

| Step | Check | Blocking? | Action |
|------|-------|-----------|--------|
| 1 | password_reset_required_at IS NOT NULL | ✅ YES | Return PASSWORD_RESET_REQUIRED |
| 2 | mfa_required_at IS NOT NULL AND verified_at IS NULL | ✅ YES | Return MFA_SETUP_REQUIRED |
| 3 | 2FA enabled but not yet verified in this login | ⚠️ MAYBE | Return TOTP_REQUIRED |
| 4 | All checks pass | ✅ NO | Issue full tokens |

**Enforce this ordering strictly in LoginCommandHandler!**

---

## Current Code Location to Update

**File:** `src/main/java/com/oodesigns/cas/application/command/LoginCommandHandler.java`

**Current flow:**
```
authenticateUser()
  → credentialReader.findCredentialsByUsername()
  → authService.getAuthenticatedUser()
  → getResponse()  ← Only checks 2FA, NOT password reset!
```

**Should be:**
```
authenticateUser()
  → credentialReader.findCredentialsByUsername()
  → authService.getAuthenticatedUser()
  → checkPasswordReset()  ← NEW: Check password reset first
  → check2FASetup()       ← NEW: Check 2FA second
  → getResponse()         ← EXISTING: Issue tokens
```

---

**Status:** Ready for implementation ✅

