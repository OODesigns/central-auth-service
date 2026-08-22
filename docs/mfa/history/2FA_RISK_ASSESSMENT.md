---
title: 2FA Schema Design Review - Risk Mitigation & Best Practices
date: 2026-01-09
---

> **Historical planning document.** References to `totp_enabled`, `auth.is_totp_enabled`, and REST integration describe an earlier design. Current status is derived from TOTP secret state; see [PROJECT_STATUS_AND_COMPLETION_PLAN.md](../../project/PROJECT_STATUS_AND_COMPLETION_PLAN.md).

# 2FA Schema: Sanity Checks & Risk Mitigation

## ✅ Part 1: Correct Behavior (Worth Understanding)

### 1.1 Multiple Audit Entries Per UPDATE ✅

**Current Behavior:**
When `totp_secrets` is updated, multiple triggers may fire independently:

```sql
UPDATE totp_secrets 
SET last_used_at = NOW(), verified_at = NOW()
WHERE user_id = ?;

-- Triggers fire:
-- 1. audit_totp_enabled() → checks verified_at NULL→NOT NULL
-- 2. audit_totp_last_used() → checks last_used_at IS DISTINCT
-- 3. audit_totp_disabled() → checks verified_at NOT NULL→NULL (doesn't match)
-- 4. audit_backup_codes_generated() → checks backup_codes_generated_at

-- Result: 2 audit_logs rows emitted from single UPDATE
```

**Example Audit Trail:**
```
UPDATE totp_secrets SET last_used_at = NOW(), verified_at = NOT NULL
  → TOTP_ENABLED (verified)
  → TOTP_LAST_USED (usage tracked)
  → 2 audit rows for 1 UPDATE statement

UPDATE totp_secrets SET last_used_at = NOW()
  → TOTP_LAST_USED (usage only)
  → 1 audit row for 1 UPDATE statement

UPDATE totp_secrets SET verified_at = NULL
  → TOTP_DISABLED (disabled)
  → 1 audit row for 1 UPDATE statement
```

**Implications:**

| Scenario | Audit Rows | Comments |
|----------|-----------|----------|
| Initial TOTP setup verification | 2 | TOTP_ENABLED + BACKUP_CODES_GENERATED |
| User logs in with TOTP | 1 | TOTP_LAST_USED |
| User disables TOTP | 1 | TOTP_DISABLED |
| Concurrent updates | Multiple | Race conditions possible |

**Design Decision: ✅ GOOD**
- Verbosity is safer than compression
- Log consumers expect multiple rows per transaction
- Easier to audit (explicit events) than implicit ones
- Matches distributed system patterns (eventual consistency)

**For Log Consumers:**
```sql
-- Correct: Expect multiple rows per logical operation
SELECT * FROM audit_logs 
WHERE target_id = 'user-id' AND action LIKE 'TOTP%'
ORDER BY created_at;

-- Handle: One UPDATE → multiple audit rows
-- Don't: Assume 1:1 mapping between user actions and audit rows
```

**Recommendation:** ✅ Document this in audit log consumer guidelines

---

### 1.2 USER_UPDATED Plus Specific Events ✅

**Current Behavior:**
When MFA policy changes, both generic and specific audit actions fire:

```sql
UPDATE users SET mfa_required_at = NOW() WHERE user_id = ?;

-- Triggers:
-- 1. audit_users() → checks mfa_required_at NULL→NOT NULL
--    Emits: USER_UPDATED (primary change)
--    Emits: USER_MFA_REQUIRED (specific policy change)
-- Result: 2 audit_logs rows
```

**Example Audit Trail:**
```
UPDATE users SET mfa_required_at = NOW()
  ↓
  Row 1: action='USER_UPDATED', metadata=<full user record>
  Row 2: action='USER_MFA_REQUIRED', metadata=<policy change details>
```

**Why Both?**

| Event | Purpose | Metadata |
|-------|---------|----------|
| `USER_UPDATED` | Track all user changes | Full user record snapshot |
| `USER_MFA_REQUIRED` | Track policy enforcement | Just policy timestamp & reason |

**Design Decision: ✅ GOOD (Verbose)**
- **Pro:** Two independent queries possible
  ```sql
  -- Find all user changes
  SELECT * FROM audit_logs WHERE action = 'USER_UPDATED';
  
  -- Find all MFA policy changes
  SELECT * FROM audit_logs WHERE action = 'USER_MFA_REQUIRED';
  ```
- **Con:** More storage, potential confusion
- **Safer:** Explicit over implicit. Let consumers filter if they want.

**Alternative (Compression):**
```sql
-- Would emit ONLY USER_MFA_REQUIRED (not USER_UPDATED)
INSERT INTO audit_logs (action='USER_MFA_REQUIRED', ...)
  -- Skip the generic USER_UPDATED row
```
**Not recommended** - loses auditability of actual DB change.

**Recommendation:** ✅ Keep verbose. Document in audit log schema guide.

---

### 1.3 auth.get_totp_status Returns Rows (Not Boolean) ✅

**Current Behavior:**
```sql
CREATE OR REPLACE FUNCTION auth.get_totp_status(p_user_id uuid)
RETURNS TABLE (user_id uuid)
-- Returns: row if exists (2FA enabled), empty set if not
```

**Usage in Application:**
```java
// Current (row-based)
Optional<UserId> has2FA = totpStatusReader.check2FAStatus(userId);

if (has2FA.isPresent()) {
    // 2FA enabled
}
```

**vs Alternative (boolean-based):**
```sql
CREATE OR REPLACE FUNCTION auth.is_totp_enabled(p_user_id uuid)
RETURNS boolean
-- Returns: true if enabled, false if disabled
```

```java
// Alternative (boolean)
boolean has2FA = totpStatusReader.isTotpEnabled(userId);

if (has2FA) {
    // 2FA enabled
}
```

**Design Decision: ✅ ROW-BASED (Current)**
- **Pro:** Extensible - can add more columns later if needed
  ```sql
  -- Future: return more data
  RETURNS TABLE (user_id uuid, verified_at timestamptz, last_used_at timestamptz)
  ```
- **Pro:** Consistent with other auth functions (auth.get_user, etc.)
- **Con:** Slight overhead (row deserialization vs boolean)

**Risk:** Careless implementation
```java
// ❌ WRONG: Ignores the Optional correctly checks if row exists
DisableTotpResult result = ...; // Always succeeds even if no TOTP
Optional<UserId> ignored = totpStatusReader.check2FAStatus(userId);

// ✅ RIGHT: Explicitly checks
Optional<UserId> has2FA = totpStatusReader.check2FAStatus(userId);
if (has2FA.isEmpty()) {
    return DisableTotpResult.failure("TOTP_NOT_ENABLED", "...");
}
```

**Recommendation:** ✅ Keep row-based. Just be careful in implementations to check Optional.

---

## ⚠️ Part 2: Real Risks (Not Structural Issues)

### 2.1 MFA Enforcement NOT at DB Level ⚠️

**Current Design:**
```
Application Layer:
  if (user.mfaRequiredAt != null && user.totpVerifiedAt == null) {
      return LoginResult.failure("MFA_REQUIRED_SETUP", "...");
  }

Database Layer:
  (no constraint prevents token issuance if mfa_required_at set and verified_at null)
```

**Risk Scenario:**
```
1. Rogue service bypasses LoginCommandHandler
2. Calls tokenService directly
3. Issues full JWT tokens even though 2FA not verified
4. Database has no constraint to prevent this
```

**Why This Is OK:**
- ✅ Trust boundary is explicit (application controls auth)
- ✅ This is standard for auth systems
- ✅ Database is not responsible for auth policy enforcement

**Why This Matters:**
- ❌ If someone writes a different auth endpoint, they might forget the check
- ❌ If you migrate to a different auth library, the check must move too
- ❌ Internal services that bypass auth won't be caught by DB

**Mitigation Strategies (Pick One):**

**Option A: Document Clearly (Recommended)**
```
AUTH_POLICY.md
===============
# MFA Enforcement Trust Boundary

⚠️ CRITICAL: MFA enforcement lives in LoginCommandHandler.checkTotpStatusAndProceed()
   NOT at database level.

If you are:
- Writing a new auth endpoint
- Using TokenService directly
- Creating a session service
- Integrating with external auth

YOU MUST CHECK:
  if (user.mfaRequiredAt != null && user.totpVerifiedAt == null) {
      reject the request
  }

Database will not enforce this. Application is responsible.
```

**Option B: Add DB Constraint (Not Recommended)**
```sql
-- Would prevent rogue code from issuing tokens
-- But enforcement logic would be in DB (bad separation of concerns)
ALTER TABLE invalidated_jwts
ADD CONSTRAINT chk_mfa_enforcement
  CHECK (user_id NOT IN (
    SELECT user_id FROM users u
    WHERE u.mfa_required_at IS NOT NULL
      AND NOT EXISTS (
        SELECT 1 FROM totp_secrets ts
        WHERE ts.user_id = u.user_id AND ts.verified_at IS NOT NULL
      )
  ));
```
❌ Not recommended - slow, complex, duplicates app logic

**Option C: Document in Code Comments**
```java
// In LoginCommandHandler.checkTotpStatusAndProceed()

/**
 * ⚠️ CRITICAL AUTH BOUNDARY
 * 
 * This is the ONLY place where 2FA enforcement is checked.
 * The database has NO constraint to prevent token issuance if 2FA is required.
 * 
 * If you are issuing tokens, you MUST call this method first.
 * Direct calls to TokenService bypass this check.
 * 
 * See: AUTH_POLICY.md for trust boundary definition
 */
private Optional<LoginResult> checkTotpStatusAndProceed(final UserId userId) {
    ...
}
```

**Recommendation:** ✅ Document explicitly (A + C)
- Add AUTH_POLICY.md with trust boundary section
- Add code comment in LoginCommandHandler
- Link from TokenService (where tokens are issued)

---

### 2.2 TOTP Disable Reason is Hardcoded ⚠️

**Current Implementation:**
```sql
CREATE OR REPLACE FUNCTION audit_totp_disabled()
  RETURNS TRIGGER AS $$
BEGIN
  ...
  jsonb_build_object(
    'reason', 'User disabled 2FA'  ← Always this string
  )
  ...
END;
```

**Problem:**
All disables show same reason. Can't distinguish:
- User voluntarily disabled
- Admin forced disable
- Security incident response
- Device loss recovery

**Scenarios Needing Different Reasons:**
```
1. User.disabled() 
   reason: 'User requested disable'
   
2. Admin.disabled(userId)
   reason: 'Admin disabled - policy compliance'
   
3. SecurityTeam.disabledAfterBreach()
   reason: 'Security incident response'
   
4. User.recoveryFlow(backupCode)
   reason: 'Device recovery - old secret invalidated'
```

**Fix Option A: Add Column to totp_secrets**
```sql
ALTER TABLE totp_secrets ADD COLUMN disable_reason VARCHAR(50)
  CHECK (disable_reason IN (
    'USER_REQUESTED',
    'ADMIN_FORCED',
    'SECURITY_INCIDENT',
    'RECOVERY_FLOW'
  ));

-- On disable:
UPDATE totp_secrets 
SET verified_at = NULL, disable_reason = 'USER_REQUESTED'
WHERE user_id = ?;
```

**Fix Option B: Pass Reason to Trigger (No Column)**
```java
// In DisableTotpCommandHandler
SET LOCAL app.disable_reason = 'USER_REQUESTED';
DELETE FROM totp_secrets WHERE user_id = ?;

// In audit_totp_disabled()
jsonb_build_object(
  'reason', current_setting('app.disable_reason', 'USER_REQUESTED')
)
```

**Fix Option C: Extend DisableTotpCommand**
```java
public enum DisableReason {
    USER_REQUESTED,
    ADMIN_FORCED,
    SECURITY_INCIDENT,
    RECOVERY_FLOW
}

public record DisableTotpCommand(
    UserId userId, 
    String password,
    DisableReason reason  ← New
)
```

**Recommendation:** ✅ Option C (DisableTotpCommand)
- **Why:** Explicit in application code
- **Safe:** Can't forget to set reason
- **Auditable:** Full chain visible

**Implementation Needed:**
```java
// Update DisableTotpCommand
public record DisableTotpCommand(
    UserId userId,
    String password,
    DisableReason reason
) {
    public DisableTotpCommand {
        // Validate reason
        Objects.requireNonNull(reason, "Disable reason required");
    }
}

// Update audit_totp_disabled()
'reason', current_setting('app.disable_reason', 'UNKNOWN')

// Update DisableTotpCommandHandler
SET LOCAL app.disable_reason = command.reason().name();
```

---

### 2.3 No Unverified Secret Expiry ⚠️

**Current Behavior:**
```sql
-- Secret created but never verified
INSERT INTO totp_secrets (user_id, secret_key_encrypted, verified_at)
VALUES ('user-123', <encrypted>, NULL);

-- Can sit forever
-- No expiry, no cleanup, no notification
```

**Scenarios:**
```
1. User scans QR code
2. User never confirms in authenticator
3. Secret languishes for months
4. DB accumulates dead secrets
```

**Why It Matters:**
- ❌ DB bloat (unverified secrets never cleaned up)
- ❌ Can't tell if enrollment failed or is pending
- ❌ User doesn't know if they need to retry
- ✅ Data exists to fix this (created_at timestamp)

**Fix Option A: Application-Level Cleanup**
```java
// In startup or scheduled task
@Scheduled(fixedRate = "1 hour")
public void cleanupUnverifiedSecrets() {
    Instant oneHourAgo = Instant.now().minus(Duration.ofHours(1));
    totp_secrets repository.deleteUnverifiedBefore(oneHourAgo);
}

// SQL
DELETE FROM totp_secrets
WHERE verified_at IS NULL 
  AND created_at < NOW() - INTERVAL '1 hour';
```

**Fix Option B: Database Trigger on created_at**
```sql
-- Don't do this - overly complex for what should be application logic
```

**Fix Option C: Document Current Behavior**
```
TOTP_SETUP.md
=============
## Unverified Secret Handling

When a user starts 2FA setup:
1. Server generates secret, creates totp_secrets row with verified_at=NULL
2. User scans QR code into authenticator
3. User enters 6-digit code
4. Server verifies and sets verified_at = NOW()

If user abandons setup:
- Secret remains in DB with verified_at=NULL forever
- No automatic cleanup
- Next setup attempt creates new secret

To clean up abandoned enrollments:
  DELETE FROM totp_secrets
  WHERE verified_at IS NULL 
    AND created_at < NOW() - INTERVAL '24 hours'
```

**Recommendation:** ✅ Option A (Application Cleanup)
- **Why:** Explicit, not tied to DB internals
- **Flexibility:** Can adjust expiry policy without migrations
- **Observable:** Cleanup is logged/monitored

**Implementation:**
```java
@Service
public class TotpMaintenanceService {
    
    private final TotpSecretRepository secretRepository;
    
    @Scheduled(fixedRate = "PT1H")  // Every hour
    public void cleanupUnverifiedSecrets() {
        final Instant expiryThreshold = Instant.now()
            .minus(Duration.ofHours(24));  // 24-hour expiry
        
        final int deleted = secretRepository
            .deleteUnverifiedBefore(expiryThreshold);
        
        if (deleted > 0) {
            LOGGER.info("Cleaned up {} unverified TOTP secrets", deleted);
        }
    }
}
```

---

## Summary: Risk Acceptance

| Risk | Impact | Mitigation | Action |
|------|--------|-----------|--------|
| **Multiple audit rows per UPDATE** | Low - understood behavior | Document for log consumers | 📝 Add to audit log guide |
| **Generic + specific audit events** | Low - design choice | Document intent | 📝 Note in code |
| **Row-based TOTP status** | Low - clear API | Careful implementation | ✅ Status quo |
| **MFA enforcement in app, not DB** | Medium - trust boundary | Document clearly | 📝 Create AUTH_POLICY.md |
| **Hardcoded disable reason** | Medium - lost context | Pass reason in command | ✅ Update DisableTotpCommand |
| **No unverified secret expiry** | Low - can cleanup later | Application task | ✅ Add TotpMaintenanceService |

---

## Recommendations (Ordered by Priority)

### Must Do (Before Production)
1. ✅ **Implement password verification** in DisableTotpCommandHandler
2. 📝 **Create AUTH_POLICY.md** documenting trust boundaries

### Should Do (Soon)
3. ✅ **Add DisableReason to DisableTotpCommand** for audit context
4. ✅ **Implement TotpMaintenanceService** for cleanup

### Nice to Have
5. 📝 **Document audit log behavior** for multi-row updates
6. 📝 **Add code comments** linking to AUTH_POLICY.md in TokenService

---

## Status: ✅ DESIGN SOUND

Your 2FA schema and policy design handles these edge cases correctly. Just need operational documentation and a couple of enhancements.

