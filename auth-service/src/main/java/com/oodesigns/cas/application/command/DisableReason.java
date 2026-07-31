package com.oodesigns.cas.application.command;

/**
 * Enumeration of reasons why TOTP 2FA is being disabled.
 * Used for audit trail context to distinguish user-initiated vs admin-forced disables.
 */
public enum DisableReason {
    /**
     * User requested disabling their own 2FA.
     * Reason context: User no longer wants 2FA protection.
     */
    USER_REQUESTED,

    /**
     * Administrator forced disable of user's TOTP.
     * Reason context: Admin action, possibly due to MFA policy change or account recovery.
     */
    ADMIN_FORCED,

    /**
     * Emergency disable due to security incident.
     * Reason context: Account compromise, breach response, forced re-enrollment.
     */
    SECURITY_INCIDENT,

    /**
     * Device recovery flow disable.
     * Reason context: User lost authenticator device, using backup code.
     * Old secret invalidated, user must re-enroll.
     */
    RECOVERY_FLOW
}

