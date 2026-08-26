# gRPC API Reference

This document describes the implemented `cas.v1.AuthService` contract in
[`src/main/proto/auth.proto`](../../src/main/proto/auth.proto). The service exposes eight unary RPCs.

The design rules for the protobuf boundary and typed internal values are defined in [GOOGLE_GRPC_DESIGN_CONTRACT.md](../architecture/GOOGLE_GRPC_DESIGN_CONTRACT.md).

The wire contract uses protobuf messages and enums, not JSON envelopes. Textual secrets and opaque signed tokens remain protobuf `string` fields because their contents are not safely representable as a more specific protobuf scalar. The gRPC adapter validates them immediately and converts them to self-validating Java value objects before calling application ports.

## Transport Semantics

- Successful RPCs return their existing response payload branches. All failures complete with canonical non-OK gRPC statuses.
- `google.rpc.Status` details include `ErrorInfo` with domain `central-auth-service` and the application error code in `reason`.
- Invalid value-object input maps to `INVALID_ARGUMENT`; invalid credentials or tokens map to `UNAUTHENTICATED`; authorization failures map to `PERMISSION_DENIED`; rate limits map to `RESOURCE_EXHAUSTED`; unexpected failures map to `INTERNAL`.
- `GrpcAuthInterceptor` enforces bearer authentication for protected RPCs before dispatch. When `REQUIRE_MACHINE_CLIENT=true`, it also requires a registered active machine-client certificate. The deprecated `LoginRequest.ip_address` field is ignored; rate limiting uses the transport peer address.

The standard gRPC health service is enabled by default. Reflection is disabled by default and must be explicitly enabled only in a trusted environment. Clients should set deadlines on every call; refresh-token rotation is not safely retryable after an ambiguous timeout.

## RPC Summary

| RPC | Request fields | Success or alternate result branches | Canonical failure statuses |
|---|---|---|---|
| `Login` | `username`, `password`, `ip_address` | `success`: access token, refresh token, user ID, permissions; `totp_required`: verification token and user ID; `mfa_enrollment_required`: short-lived enrollment token and user ID; `password_reset_required`: user ID | `INVALID_ARGUMENT`, `UNAUTHENTICATED`, `RESOURCE_EXHAUSTED`, `FAILED_PRECONDITION`, `INTERNAL` |
| `SetupTotp` | `user_id`, `username` | `success`: plaintext secret and `otpauth_uri` for one-time enrollment display | `INVALID_ARGUMENT`, `INTERNAL` |
| `EnableTotp` | `user_id`, `totp_code` | `success`: plaintext backup codes returned once | `INVALID_ARGUMENT`, `UNAUTHENTICATED`, `INTERNAL` |
| `VerifyTotp` | `verification_token`, `code` | `success`: access token, refresh token, user ID, permissions | `INVALID_ARGUMENT`, `UNAUTHENTICATED`, `RESOURCE_EXHAUSTED`, `INTERNAL` |
| `Refresh` | `refresh_token` | `success`: rotated access token, refresh token, user ID, permissions | `INVALID_ARGUMENT`, `UNAUTHENTICATED`, `INTERNAL` |
| `Logout` | `access_token` | `success`: empty acknowledgement after revocation is persisted | `INVALID_ARGUMENT`, `UNAUTHENTICATED`, `INTERNAL` |
| `DisableTotp` | `user_id`, `password`, `reason` | `success`: empty acknowledgement after the TOTP secret and backup codes are removed | `INVALID_ARGUMENT`, `UNAUTHENTICATED`, `PERMISSION_DENIED`, `INTERNAL` |
| `AdminDisableTotp` | `target_user_id`, `admin_password`, `reason` | `success`: empty acknowledgement after the target's TOTP secret and backup codes are removed | Canonical gRPC statuses: `UNAUTHENTICATED`, `PERMISSION_DENIED`, `INVALID_ARGUMENT`, `RESOURCE_EXHAUSTED`, `FAILED_PRECONDITION`, `INTERNAL` |

## Login

`Login` derives the peer IP from the gRPC transport and applies IP, username, and combined IP/username rate limits before credential verification. The request's `ip_address` field is retained for compatibility but is not trusted for rate limiting.
Successful password verification has three possible non-error outcomes:

- `success` when the account can receive a full token pair.
- `totp_required` when TOTP is enabled; the returned short-lived verification token is consumed by `VerifyTotp`.
- `password_reset_required` when password reset is required and TOTP does not take precedence.

`mfa_enrollment_required` is returned when policy requires MFA but the user has not enrolled. The response contains a short-lived, single-purpose enrollment token. If token generation is unavailable, the service fails closed with a canonical `FAILED_PRECONDITION` status whose `ErrorInfo.reason` is `MFA_SETUP_REQUIRED`.

## TOTP Enrollment And Challenge

`SetupTotp` requires a matching access-token principal or MFA enrollment token. It creates a pending encrypted secret. Its plaintext form and URI are sensitive one-time enrollment material and must not be logged or retained by clients after setup.

`EnableTotp` verifies the first six-digit TOTP code, activates the pending secret, and returns backup codes once. Clients must present and securely store those codes before discarding the response.

`VerifyTotp` accepts either a TOTP code or backup code in `code`. It validates the short-lived 2FA verification token, applies a per-user attempt limit, consumes a valid backup code, and returns the full token pair.

`DisableTotp` requires a matching access-token principal, password re-authentication, and a non-default `DisableReason`. `USER_REQUESTED` is self-service; privileged reasons require the `manage_mfa` permission. `DISABLE_REASON_UNSPECIFIED` maps to `INVALID_REQUEST`.

`AdminDisableTotp` is a separate privileged operation for cross-user disablement. It requires `manage_mfa`, authenticates the administrator's own password, rejects `USER_REQUESTED`, and targets `target_user_id`.

## Refresh And Logout

`Refresh` rotates the refresh token atomically. Reuse detection invalidates the affected token family and returns `UNAUTHENTICATED` with `REFRESH_TOKEN_REUSE_DETECTED` in `ErrorInfo.reason`; the caller must require a fresh login. Clients must not blindly retry refresh operations.

`Logout` accepts an access token, validates its signature, type, expiry, and revocation state, then stores its JTI, SHA-256 token hash, and expiry. A revoked token returns `INVALID_ACCESS_TOKEN` on later verification.

## Error Handling Example

Clients must inspect the response oneof for successful calls:

```text
response.result:
  success -> continue

non-OK call:
  unpack google.rpc.Status and ErrorInfo
```

All failures are returned as canonical non-OK gRPC statuses. Clients should inspect `google.rpc.Status` and unpack `ErrorInfo` for the service domain and status reason; response `error` fields remain only for protobuf source compatibility.