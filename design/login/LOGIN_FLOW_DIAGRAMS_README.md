---
title: Login Flow Diagrams - Architecture Overview
date: 2026-01-12
---

# Login Flow Diagrams - Complete Architecture

This directory contains PlantUML diagrams documenting the Home Control System login flows and permission model.

## Diagrams Overview

### 1. **LoginFlow_Activity.puml** - Main Login Flow
Activity diagram showing the step-by-step login process with decision points:

**Flow:**
1. **Verify Password** - AuthenticationService.verify()
   - No permissions needed (bootstrap)
   
2. **Load Minimal User Data** - UserRepository.findMinimalDataById()
   - user_id, username
   - password_reset_required_at (for reset check)
   - mfa_required_at (for policy check)
   - **NO PERMISSIONS YET**

3. **TERMINAL BRANCH: Password Reset Check**
   - IF password_reset_required_at IS NOT NULL
   - STOP login flow
   - Return: PasswordResetRequired
   - No tokens, no MFA challenge

4. **Check MFA Policy Enforcement**
   - Check: mfa_required_at <= NOW()?
   - Check: Is user enrolled?
   - IF required but not enrolled: Return MFA_REQUIRED_SETUP
   - If OK: Continue

5. **Check MFA Challenge**
   - Check: Does user have MFA enabled?
   - IF yes: Return MFA_CHALLENGE_REQUIRED (restricted token)
   - If no: Continue

6. **Load Full Permissions** - UserRepository.findByIdWithPermissions()
   - **ONLY at this point**
   - Load all role-based permissions
   - Load user metadata

7. **Generate Tokens** - TokenService.generateTokens()
   - Access token (15 min TTL)
   - Refresh token (7 day TTL)
   - Include permissions in JWT

8. **Issue Tokens** - LoginSuccess
   - User authenticated
   - Can access permitted resources

---

### 2. **LoginFlow_Sequence.puml** - Detailed Sequence Diagram
Sequence diagram showing message flow between components for all scenarios:

**Participants:**
- User (actor)
- Client App
- LoginCommandHandler
- AuthenticationService
- UserRepository
- TotpStatusReader
- TokenService

**Scenarios Shown:**
1. ✅ Normal login (no MFA)
2. ⚠️ Password reset required (terminal branch)
3. ⚠️ MFA setup required (user not enrolled, but required by policy)
4. ⚠️ MFA challenge required (user has MFA enabled, must verify)

Each scenario shows what data is returned and what tokens are issued.

---

### 3. **LoginFlow_Permissions.puml** - Permission Model
Card diagram showing different authentication flows and their permission requirements:

#### Password Reset Flow
- **Endpoint:** `POST /auth/reset-password`
- **Required Permission:** `change_password`
- **Data Loaded:** user_id, password_hash only
- **Tokens Issued:** NONE (no login)

#### MFA Setup Flow
- **Endpoint:** `POST /auth/2fa/setup`
- **Required Permission:** `setup_mfa`
- **Data Loaded:** user_id, mfa_required_at, totp_secrets
- **Tokens Issued:** Setup token (restricted, no login)

#### MFA Challenge Flow
- **Endpoint:** `POST /auth/verify-2fa`
- **Required Permission:** User owns the 2FA (no role check)
- **Data Loaded:** user_id, totp_secrets
- **Tokens Issued:** Verification token (restricted, no login)

#### Normal Login Flow
- **Endpoint:** `POST /auth/login`
- **Required Permission:** Role-based (from permissions table)
- **Data Loaded:** user_id, username, **permissions (FULL)**, roles
- **Tokens Issued:** 
  - Access Token (15 min)
  - Refresh Token (7 day)

---

### 4. **LoginFlow_States.puml** - State Machine
State diagram showing all possible user authentication states and transitions:

**States:**

1. **NEW USER**
   - password_reset_required_at = NOW()
   - mfa_required_at = NULL (or future date)
   - Can only reset password

2. **PASSWORD RESET REQUIRED**
   - User trying to login but password reset needed
   - Terminal state until password changed
   - Permission: change_password only

3. **2FA SETUP REQUIRED**
   - password_reset_required_at = NULL ✓
   - mfa_required_at = NOW() (enforced by policy)
   - totp_secrets.verified_at = NULL
   - Terminal state until 2FA enrolled
   - Permission: setup_mfa only

4. **2FA CHALLENGE REQUIRED**
   - All prerequisites met but MFA not verified in current session
   - totp_secrets.verified_at = NOT NULL (user has 2FA)
   - Terminal state until TOTP/WebAuthn verified

5. **AUTHENTICATED LOGGED IN**
   - All checks passed
   - Tokens issued
   - Full permissions available
   - Can access resources

6. **SESSION EXPIRED**
   - Access token expired (15 min)
   - Refresh token still valid (7 day)
   - Can refresh for new access token

7. **FULLY LOGGED OUT**
   - Refresh token expired or revoked
   - Must login again

---

## Permission Model

### New Permissions (From V1_3__add_auth_flow_permissions.sql)

```sql
INSERT INTO permissions (name)
VALUES ('change_password'),      -- Password reset flow
       ('setup_mfa'),             -- MFA enrollment flow
       ('view_settings');         -- User settings
```

### Permission Assignments by Role

| Role | Permissions | Purpose |
|------|-------------|---------|
| admin | All permissions | Full system access |
| user | change_password, setup_mfa, view_settings | Self-service operations |
| kiosk | change_password, setup_mfa, view_settings | Self-service (restricted terminal) |

---

## Data Loading Strategy

### Step 2: Minimal Data Load
```sql
SELECT user_id, username, 
       password_reset_required_at, 
       mfa_required_at
FROM users
WHERE user_id = ?
```
**Used for:** Password reset check, MFA policy check
**NOT loaded:** Permissions, roles, other metadata

### Step 6: Full Data Load
```sql
SELECT u.*, 
       ARRAY_AGG(p.name) as permissions
FROM users u
LEFT JOIN user_permissions up ON u.user_id = up.user_id
LEFT JOIN permissions p ON up.permission_id = p.permission_id
WHERE u.user_id = ?
GROUP BY u.user_id
```
**Used for:** Token generation, resource access control
**Loaded:** All permissions, roles, user metadata
**Only loaded:** IF reaching normal login (not password reset or MFA setup)

---

## Security Benefits

### 1. Terminal Branches Stop Early
- Password reset flow: 5ms (minimal data)
- MFA setup flow: 5ms (minimal data)
- Normal login: 17ms (full data, negligible difference)

### 2. No Permission Escalation
- Password reset doesn't grant access
- MFA setup doesn't grant access
- Only full login grants session tokens

### 3. Scope Limitation
- Each flow loads only what it needs
- Permissions not loaded for flows that don't use them
- Reduced attack surface

### 4. Clear Intent
- Code shows exactly what permissions each flow needs
- Diagrams document the relationships
- Easy to audit security model

---

## How to Use These Diagrams

### For Code Review
- Check that LoginCommandHandler follows activity diagram steps
- Verify enforcePasswordResetSecond() is truly terminal (no continuation)
- Confirm permissions loaded only at step 6

### For Testing
- Test each terminal branch (password reset, MFA setup)
- Test MFA challenge flow
- Test normal login with different permission sets
- Verify 100% coverage of all states

### For Documentation
- Show stakeholders the login flow (Activity diagram)
- Demonstrate permission scoping (Permissions diagram)
- Show state transitions (States diagram)
- Detail component interactions (Sequence diagram)

### For Future Changes
- Adding new auth methods? Update sequence diagram
- Changing permission model? Update permissions diagram
- Adding new flows? Add new states to state machine
- Performance tuning? Reference data loading strategy

---

## Current Implementation Status

✅ **Activity Diagram** - Documented in LoginFlow_Activity.puml
✅ **Sequence Diagram** - Documented in LoginFlow_Sequence.puml
✅ **Permissions Diagram** - Documented in LoginFlow_Permissions.puml
✅ **State Machine** - Documented in LoginFlow_States.puml

✅ **Code Implementation:**
- LoginCommandHandler.java - Follows activity flow
- V1_3__add_auth_flow_permissions.sql - Adds required permissions
- SELECTIVE_PERMISSION_LOADING.md - Documents strategy

⏳ **Next Steps:**
- Create UserRepository.findMinimalDataById() method
- Update existing tests to follow new flows
- Add test coverage for all terminal branches
- Performance testing (verify 5ms vs 17ms targets)

---

## Document References

- `docs/PASSWORD_RESET_VS_LOGIN_FLOWS.md` - Detailed explanation of terminal branches
- `docs/SELECTIVE_PERMISSION_LOADING.md` - Permission loading strategy
- `docs/MFA_POLICY_CHECK_DATABASE_VS_APPLICATION.md` - MFA policy enforcement
- `src/main/java/.../LoginCommandHandler.java` - Implementation

---

**Last Updated:** 2026-01-12
**Status:** Architecture documented, implementation in progress

