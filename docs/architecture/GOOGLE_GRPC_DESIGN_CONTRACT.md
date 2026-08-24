# Google-Style gRPC Design Contract

## Purpose

This contract defines the API and implementation standards for Central Auth Service. It follows Google-style protobuf and gRPC guidance while preserving hexagonal architecture and explicit security boundaries.

## Public protobuf contract

- Protobuf messages and enums are the public contract.
- Do not wrap request or response data in a JSON string or generic JSON envelope.
- Use `oneof` for mutually exclusive response outcomes.
- Use enums for closed sets and reject `*_UNSPECIFIED` at the boundary.
- Preserve field numbers and wire types; evolve incompatible changes through new fields or versioned RPCs.
- Keep opaque textual credentials and tokens as protobuf `string` fields. Their security comes from boundary validation and typed conversion.
- Use `bytes` only for genuinely binary values such as encrypted data.
- Use `google.protobuf.Timestamp` for exposed timestamps.
- Use canonical gRPC statuses for transport and authentication failures; preserve the current response error branch for compatibility.
- For privileged administrative RPCs, return canonical statuses for malformed requests and authorization failures (`INVALID_ARGUMENT`, `PERMISSION_DENIED`, `UNAUTHENTICATED`) and reserve `INTERNAL` for unexpected failures.

The gRPC adapter is the only place where protobuf strings become Java values. The deprecated client `LoginRequest.ip_address` is ignored; rate limiting uses the trusted transport peer address.

## Internal type contract

- Raw strings, JSON maps, protobuf objects, and unvalidated external data stop at infrastructure boundaries.
- Internal commands, ports, handlers, and stores use self-validating intent-specific value objects.
- Token purposes use distinct types: `AccessToken`, `RefreshToken`, `TwoFactorVerificationToken`, and `MfaEnrollmentToken`.
- Value-object factories reject invalid values during construction; internal methods do not repeat those checks.
- Cryptographic validity remains the verifier's responsibility; construction validates only structure and representation.
- Functional/declarative composition is preferred when it improves intent. Imperative code remains appropriate for cryptographic cleanup, constant-time comparisons, SQL transactionality, and bounded resource management.

## Authentication and authorization

- `Login`, `Refresh`, and `VerifyTotp` are credential-exchange entry points.
- Protected RPCs require verified bearer authentication before dispatch.
- The interceptor derives the principal from the verified token; caller-supplied IDs never establish identity.
- Self-service operations require principal/target equality.
- Administrative operations require explicit permissions such as `manage_mfa` and a separate authorization path.
- Administrative MFA disablement is modeled as a dedicated RPC that can target a different user; it reauthenticates the authenticated administrator's own password before acting.
- Required-MFA bootstrap uses a short-lived, single-purpose enrollment token.
- Invalid, expired, revoked, wrong-purpose, or malformed tokens fail closed.

## Security and data handling

- Passwords and key passwords use clearable character arrays where APIs permit; protobuf, BCrypt, JDBC, environment, and library string boundaries are documented limitations.
- Temporary secret-derived arrays are cleared in `finally` blocks.
- Database access uses explicit API functions, locked search paths, non-login owner roles, and per-function grants.
- Audit payloads use allowlisted fields and exclude password hashes, token hashes, backup-code hashes, and encrypted secret material.
- Rate limits fail closed when their backing store is unavailable and distributed storage has an explicit cleanup policy.
- TLS is fail-closed by default with an explicit protocol/cipher allowlist. Certificate authentication is not application authorization without principal mapping.

## Testing and operations

- Change security contracts with focused behavioral tests.
- Keep unit tests deterministic; use integration tests for PostgreSQL, TLS, migrations, and transaction boundaries.
- Preserve 100 percent line coverage for included classes.
- Test missing, malformed, expired, revoked, mismatched, unauthorized, and unavailable-dependency paths.
- Apply Flyway migrations through the approved single migration job and never repair automatically.
- Document compatibility windows, residual risks, and operational prerequisites. Never describe planned controls as implemented.

## Review checklist

1. Is every external value validated at the adapter boundary?
2. Do internal APIs use types that express intent?
3. Can callers confuse token purposes or select another user's identity?
4. Are authorization checks centralized and fail closed?
5. Are sensitive buffers cleared without invalidating operations?
6. Are database grants explicit and audit payloads redacted?
7. Are compatibility and migration behaviors documented?
8. Are negative paths and integration boundaries tested?
9. Does documentation match the implementation?
10. Is the abstraction simpler and clearer than the code it replaces?
