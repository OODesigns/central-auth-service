> **Historical planning document.** Proposed claims, replay-store behavior, WebAuthn references, and REST endpoint names below are not a current specification. See [docs/architecture/ARCHITECTURE_DIAGRAMS.md](../../../docs/architecture/ARCHITECTURE_DIAGRAMS.md).

Here’s a **practical, implementation-style spec** for the **MFA Challenge Token (restricted / MFA-scoped)**.

---

## MFA Challenge Token (restricted / MFA-scoped)

### When it is issued

Issue **after**:

* Password verified ✅
* MFA policy evaluated ✅
* User is MFA-enrolled / MFA enabled ✅
  Return: `MFA_CHALLENGE_REQUIRED` + this token.

---

## Concrete approach

### TTL & lifecycle

* **TTL:** **5 minutes** (recommended)

    * Rationale: short enough to reduce replay risk, long enough for typical MFA entry.
* **Single-use:** **Yes**

    * On MFA success or failure, token becomes invalid.
* **Absolute expiry:** cannot be extended.
* **Not refreshable.**
* **Invalidate** if:

    * Another MFA challenge is issued for the same user/session attempt (optional but recommended)
    * Password reset is triggered / password changed
    * Account disabled/locked

> Access token in your model is **15 minutes**; this must be *significantly shorter* than that.

---

## What it allows

✅ Only MFA endpoints, e.g.

* `POST /auth/verify-2fa`
* (optionally) `POST /auth/resend-2fa` if you support it

❌ Must NOT be accepted by:

* Any “normal” resource APIs
* Permission-protected endpoints
* Token refresh endpoints
* Permission-loading endpoints

---

## Token format and required claims

Use JWT or opaque token; either is fine. The important part is **claims + enforcement**.

### Required claims (JWT example)

* `sub`: `user_id`
* `typ`: `"mfa_challenge"`  *(hard gate)*
* `aud`: `"auth.mfa"` *(hard gate)*
* `iat`, `exp`: issued/expiry
* `jti`: unique token id (required for single-use)
* `amr`: `["pwd"]` *(signals password verified, MFA pending)*

### Optional but strong

* `sid`: session/login-attempt id (ties subsequent requests to the same attempt)
* `cnf` / binding:

    * `cnf.jkt` (DPoP key thumbprint) **or**
    * hash of device/session cookie id
* `mfa_factor`: `"totp"` / `"webauthn"` (if you want to lock to factor type)

---

## Server-side checks (non-negotiable)

On every MFA verification request authenticated by this token:

1. Validate signature + `exp`
2. Check `typ == mfa_challenge`
3. Check `aud == auth.mfa`
4. Check `sub` exists and user still active
5. **Single-use enforcement**:

    * Verify `jti` not used
    * Mark `jti` as used (atomic)
6. Optional but recommended:

    * Check `sid` matches the issued challenge record
    * Check binding (`cnf` / device hash) matches

If any fails → reject and **do not** proceed to issue access/refresh.

---

## Storage / revocation strategy (pick one)

### Option A (recommended): Opaque token + server record

* Store: `{token_id, user_id, sid, expires_at, used_at}`
* Pros: easy revoke, easy single-use, no JWT replay issues
* Cons: requires lookup

### Option B: JWT + `jti` replay store

* JWT is self-contained
* Store only `jti` usage + expiry
* Pros: lighter than full opaque store
* Cons: still need store for single-use

For auth flows, I’d pick **B** if you already run JWT infra + fast store (Redis), otherwise **A**.

---

## What it must never include

* Roles
* Permissions
* App scopes
* Anything that would let it pass authorization checks

This lines up with your model: permissions are only loaded late, and restricted tokens are “no login”.

---

## What happens next (state transition)

On **MFA success**:

* mark MFA token as consumed (single-use)
* continue to post-auth checks
* **only then** proceed toward permissions load and final token issuance

On **MFA failure**:

* consume or invalidate token (prevents brute force on same token)
* require a new challenge issuance if retry is allowed

---

If you want, I can do the same **tight spec blocks** (TTL + claims + allowed endpoints + invalidation rules) for:

* MFA Setup Token
* MFA Verification Token
* Password Reset Token (if you keep it)
* Access + Refresh tokens (including rotation rules)
