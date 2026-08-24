# gRPC API Reference

This document describes the implemented `cas.AuthService` contract in
[`src/main/proto/auth.proto`](../../src/main/proto/auth.proto). The service exposes eight unary RPCs.

The design rules for the protobuf boundary and typed internal values are defined in [GOOGLE_GRPC_DESIGN_CONTRACT.md](../architecture/GOOGLE_GRPC_DESIGN_CONTRACT.md).

The wire contract uses protobuf messages and enums, not JSON envelopes. Textual secrets and opaque signed tokens remain protobuf `string` fields because their contents are not safely representable as a more specific protobuf scalar. The gRPC adapter validates them immediately and converts them to self-validating Java value objects before calling application ports.

## Transport Semantics

- Every RPC returns exactly one branch of its response `oneof result`.
- Business and request-validation failures use the response `error` branch with an `error_code` and `error_message`.
- These failures complete with `onNext` followed by `onCompleted`; they are not gRPC non-OK status responses.
- Invalid value-object input, such as a malformed UUID or invalid field format, maps to `INVALID_REQUEST`.
- Unexpected handler failures map to `INTERNAL_ERROR`.
- Protected-RPC transport failures use canonical gRPC statuses (`UNAUTHENTICATED`, `PERMISSION_DENIED`).
- `AdminDisableTotp` request validation and authorization failures use canonical gRPC statuses (`INVALID_ARGUMENT`, `PERMISSION_DENIED`, `UNAUTHENTICATED`) and unexpected failures use `INTERNAL`.
- `GrpcAuthInterceptor` enforces bearer authentication for protected RPCs before dispatch. It binds the verified principal and permission snapshot to request context; callers must not treat possession of a user ID as authorization. The deprecated `LoginRequest.ip_address` field is ignored; rate limiting uses the transport peer address.

## RPC Summary

| RPC | Request fields | Success or alternate result branches | Error codes |
|---|---|---|---|
| `Login` | `username`, `password`, `ip_address` | `success`: access token, refresh token, user ID, permissions; `totp_required`: verification token and user ID; `mfa_enrollment_required`: short-lived enrollment token and user ID; `password_reset_required`: user ID | `INVALID_REQUEST`, `RATE_LIMITED`, `INVALID_CREDENTIALS`, `MFA_SETUP_REQUIRED`, `INTERNAL_ERROR` |
| `SetupTotp` | `user_id`, `username` | `success`: plaintext secret and `otpauth_uri` for one-time enrollment display | `INVALID_REQUEST`, `INTERNAL_ERROR` |
| `EnableTotp` | `user_id`, `totp_code` | `success`: plaintext backup codes returned once | `INVALID_REQUEST`, `INVALID_TOTP_CODE`, `TOTP_ALREADY_ENABLED`, `INTERNAL_ERROR` |
| `VerifyTotp` | `verification_token`, `code` | `success`: access token, refresh token, user ID, permissions | `INVALID_REQUEST`, `INVALID_VERIFICATION_TOKEN`, `RATE_LIMIT_EXCEEDED`, `INVALID_TOTP_CODE`, `USER_NOT_FOUND`, `INTERNAL_ERROR` |
| `Refresh` | `refresh_token` | `success`: rotated access token, refresh token, user ID, permissions | `INVALID_REQUEST`, `INVALID_REFRESH_TOKEN`, `USER_NOT_FOUND`, `REFRESH_TOKEN_EXPIRED`, `REFRESH_TOKEN_REUSE_DETECTED`, `INTERNAL_ERROR` |
| `Logout` | `access_token` | `success`: empty acknowledgement after revocation is persisted | `INVALID_REQUEST`, `INVALID_ACCESS_TOKEN`, `INTERNAL_ERROR` |
| `DisableTotp` | `user_id`, `password`, `reason` | `success`: empty acknowledgement after the TOTP secret and backup codes are removed | `INVALID_REQUEST`, `INVALID_PASSWORD`, `TOTP_NOT_ENABLED`, `INTERNAL_ERROR` |
| `AdminDisableTotp` | `target_user_id`, `admin_password`, `reason` | `success`: empty acknowledgement after the target's TOTP secret and backup codes are removed | gRPC `UNAUTHENTICATED`, `PERMISSION_DENIED`, `INVALID_ARGUMENT`, `INTERNAL`; business failures remain response `error` for compatibility |

## Login

`Login` derives the peer IP from the gRPC transport and applies IP, username, and combined IP/username rate limits before credential verification. The request's `ip_address` field is retained for compatibility but is not trusted for rate limiting.
Successful password verification has three possible non-error outcomes:

- `success` when the account can receive a full token pair.
- `totp_required` when TOTP is enabled; the returned short-lived verification token is consumed by `VerifyTotp`.
- `password_reset_required` when password reset is required and TOTP does not take precedence.

`mfa_enrollment_required` is returned when policy requires MFA but the user has not enrolled. The response contains a short-lived, single-purpose enrollment token. If token generation is unavailable, the service fails closed with `MFA_SETUP_REQUIRED` in the `error` branch.

## TOTP Enrollment And Challenge

`SetupTotp` requires a matching access-token principal or MFA enrollment token. It creates a pending encrypted secret. Its plaintext form and URI are sensitive one-time enrollment material and must not be logged or retained by clients after setup.

`EnableTotp` verifies the first six-digit TOTP code, activates the pending secret, and returns backup codes once. Clients must present and securely store those codes before discarding the response.

`VerifyTotp` accepts either a TOTP code or backup code in `code`. It validates the short-lived 2FA verification token, applies a per-user attempt limit, consumes a valid backup code, and returns the full token pair.

`DisableTotp` requires a matching access-token principal, password re-authentication, and a non-default `DisableReason`. `USER_REQUESTED` is self-service; privileged reasons require the `manage_mfa` permission. `DISABLE_REASON_UNSPECIFIED` maps to `INVALID_REQUEST`.

`AdminDisableTotp` is a separate privileged operation for cross-user disablement. It requires `manage_mfa`, authenticates the administrator's own password, rejects `USER_REQUESTED`, and targets `target_user_id`.

## Refresh And Logout

`Refresh` rotates the refresh token atomically. Reuse detection invalidates the affected token family and returns `REFRESH_TOKEN_REUSE_DETECTED`; the caller must require a fresh login.

`Logout` accepts an access token, validates its signature, type, expiry, and revocation state, then stores its JTI, SHA-256 token hash, and expiry. A revoked token returns `INVALID_ACCESS_TOKEN` on later verification.

## Error Handling Example

Clients must inspect the response oneof even when the gRPC call completes successfully:

```text
response.result:
  success -> continue
  error   -> branch on error.error_code
```

Do not rely on gRPC status codes for compatibility business errors returned in response `error` branches. Do handle canonical non-OK statuses for protected transport/authentication failures and administrative validation/authorization failures.