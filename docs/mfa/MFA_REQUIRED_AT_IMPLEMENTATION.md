---
title: mfa_required_at Implementation - Date-Based Boolean
date: 2026-01-11
---

# mfa_required_at: Flexible Date-Based Boolean

## Problem Solved

The `mfa_required_at` column is now correctly used as a **date-based boolean** with built-in flexibility:

```
Before: Just checked if NOT NULL (boolean)
After:  Checks if date <= NOW() (date-based boolean with future support)
```

## Implementation

### Database Schema (No Changes Needed)
```sql
mfa_required_at TIMESTAMPTZ  -- Stores timestamp with timezone
```

### Java Logic (LoginCommandHandler.java)

**Method: `isMFAPolicyEnforced(User)`**
```java
private boolean isMFAPolicyEnforced(final User user) {
    if (user.mfaRequiredAt() == null) {
        return false;  // Not required
    }
    
    // MFA is required if the date is now or in the past
    return !user.mfaRequiredAt().isAfter(java.time.Instant.now());
}
```

## How It Works

### State 1: MFA Not Required
```sql
mfa_required_at = NULL
```
- User can login without MFA
- `isMFAPolicyEnforced()` returns `false`

### State 2: MFA Required Immediately
```sql
mfa_required_at = 2026-01-11 00:00:00 UTC  (date <= NOW())
```
- User MUST have 2FA setup
- `isMFAPolicyEnforced()` returns `true`
- If not enrolled → blocked with setup request

### State 3: MFA Required in Future (Phased Rollout)
```sql
mfa_required_at = 2026-02-01 00:00:00 UTC  (date > NOW())
```
- User can login WITHOUT 2FA today
- `isMFAPolicyEnforced()` returns `false` (future date)
- When 2026-02-01 arrives → returns `true`
- User must enroll before that date

## Use Cases

### Use Case 1: Immediate Enforcement
```sql
-- Admin enforces 2FA for a user right now
UPDATE users SET mfa_required_at = NOW() WHERE user_id = '...';

-- Result: User blocked at login until they setup 2FA
```

### Use Case 2: Phased Rollout (Wave Release)
```sql
-- Wave 1: Enforce for admin on 2026-01-15
UPDATE users SET mfa_required_at = '2026-01-15' 
WHERE role = 'ADMIN';

-- Wave 2: Enforce for managers on 2026-02-01
UPDATE users SET mfa_required_at = '2026-02-01' 
WHERE role = 'MANAGER';

-- Wave 3: Enforce for all users on 2026-03-01
UPDATE users SET mfa_required_at = '2026-03-01' 
WHERE mfa_required_at IS NULL;

-- Users get warning emails: "2FA required by [DATE]"
```

### Use Case 3: Grace Period (Future Enhancement)
```java
// Add grace period logic later
if (isMFAPolicyEnforced(user)) {
    Instant deadline = user.mfaRequiredAt();
    Duration timeSinceRequired = Duration.between(deadline, clock.now());
    
    if (timeSinceRequired.toDays() > 30) {
        // Grace period expired - hard block
        return block("MFA_SETUP_DEADLINE_PASSED");
    } else if (timeSinceRequired.toDays() > 0) {
        // Still in grace period - warn but allow
        return warn("MFA setup required in " + 
                   (30 - timeSinceRequired.toDays()) + " days");
    }
}
```

## Benefits

| Benefit | Description |
|---------|-------------|
| **Flexible** | Supports immediate enforcement OR future dates |
| **Scalable** | Can implement grace periods later |
| **Simple** | Single date check, not multiple boolean flags |
| **Auditable** | Timestamp shows WHEN enforcement was set |
| **Future-Proof** | Date-based allows phased rollouts |

## Database Queries

### Find users with MFA enforcement in effect right now
```sql
SELECT user_id, username, mfa_required_at
FROM users
WHERE mfa_required_at IS NOT NULL
  AND mfa_required_at <= NOW();
```

### Find users with MFA enforcement scheduled for future
```sql
SELECT user_id, username, mfa_required_at
FROM users
WHERE mfa_required_at > NOW();
```

### Find users WITHOUT 2FA who must enroll soon (next 7 days)
```sql
SELECT u.user_id, u.username, u.mfa_required_at
FROM users u
LEFT JOIN totp_secrets ts ON u.user_id = ts.user_id
WHERE u.mfa_required_at IS NOT NULL
  AND u.mfa_required_at <= NOW()
  AND ts.verified_at IS NULL
ORDER BY u.mfa_required_at;
```

## Current Implementation

### Step 3: Enforce MFA Policy
```java
private LoginResult enforceMFAPolicyFirst(final UserId userId) {
    return userRepository.findById(userId)
        .map(user -> {
            // Check if MFA is required by policy (check current date)
            if (isMFAPolicyEnforced(user)) {
                // MFA is required - check if user has enrolled
                final Optional<UserId> hasEnrolled =
                    totpStatusReader.check2FAStatus(userId);

                if (hasEnrolled.isEmpty()) {
                    // MFA is REQUIRED but not enrolled - block
                    return LoginResult.required2FA(...);
                }
            }
            // Continue to next check...
        })
        .orElseGet(...);
}
```

## Summary

✅ **`mfa_required_at` is now a date-based boolean**
- NULL = not required
- Date <= NOW() = required right now
- Date > NOW() = required on that future date

✅ **Supports immediate and phased enforcement**
- Immediate: `UPDATE users SET mfa_required_at = NOW()`
- Phased: `UPDATE users SET mfa_required_at = '2026-02-01'` for wave release

✅ **Ready for future grace period logic**
- Can add deadline enforcement later
- Can calculate days remaining
- Can send warning emails

**Status: IMPLEMENTED ✅**

