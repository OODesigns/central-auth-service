# Administrator-Issued Account Recovery Runbook

This runbook deploys and operates account recovery without a public reset-request RPC, email provider, or mail adapter. Recovery tokens are issued only by an authenticated administrator with the `manage_recovery` permission and are delivered through the organization's approved out-of-band support process.

## Deployment Prerequisites

1. Obtain a PostgreSQL backup and verify the most recent restore test succeeded.
2. Ensure the release image has passed the approved internal security gate and is recorded by immutable digest.
3. Assign named Security and Support owners for identity verification, token delivery, and account-takeover escalation.
4. Apply the Flyway migration through the single approved migration job:

```bash
flyway validate
flyway info
flyway migrate
flyway info
```

Confirm `V1_5_8__add_administrator_recovery.sql` reports success. Do not use `flyway repair` to bypass a validation error.

## Post-Migration Verification

Run these checks as a database administrator, substituting the deployed application role if it differs from `app_user`:

```sql
SELECT has_function_privilege(
  'app_user',
  'api_schema.issue_recovery_token(uuid, uuid, text)',
  'EXECUTE'
);

SELECT has_function_privilege(
  'app_user',
  'api_schema.consume_recovery_token(uuid, text, text)',
  'EXECUTE'
);
```

Both checks must return `true`. The application role must not receive direct table privileges on `private_schema.recovery_tokens`.

Deploy the exact scanned image digest and run the recovery smoke test against a dedicated test account:

1. Authenticate as an administrator with `manage_recovery`.
2. Use `IssueRecoveryToken` for the test account.
3. Deliver the token only through the approved test support channel.
4. Call `CompleteRecovery` with the token and a compliant new password.
5. Confirm the old password and previous refresh token fail.
6. Confirm the user must enroll MFA again before normal authentication completes.
7. Confirm audit records show `RECOVERY_ISSUED` and `RECOVERY_COMPLETED` without a raw recovery token.

## Support Procedure

Before issuing a token, the support administrator must verify the requester through the organization's approved identity process. The case record contains the case ID, verifier identity, target account, issue time, and outcome only. It must never contain the recovery token.

Use an authenticated support portal, approved corporate messaging system, or documented voice-verification process to deliver the token. Do not send it through general email, unverified chat, shared ticket text, application logs, or monitoring tools.

The token expires after 15 minutes, can be used once, and a new issuance invalidates earlier unused recovery tokens for that account. Escalate suspected account takeover, repeated recovery requests, or uncertain delivery to Security rather than issuing another token.

## Evidence and Rollback

Attach Flyway output, function privilege checks, smoke-test evidence, image digest, and Security/Support approval to the release record. If recovery smoke tests fail, stop promotion and use a forward corrective migration or application fix; do not roll back database migrations destructively.