package com.oodesigns.cas.domain.value;

/** Short-lived token used only to bootstrap required MFA enrollment. */
public final class MfaEnrollmentToken extends CompactToken {
    private MfaEnrollmentToken(final String value) {
        super(value);
    }

    public static MfaEnrollmentToken of(final String value) {
        return new MfaEnrollmentToken(value);
    }
}
