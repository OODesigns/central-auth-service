package com.oodesigns.cas.domain.value;

/** Short-lived token used only to complete a TOTP login challenge. */
public final class TwoFactorVerificationToken extends CompactToken {
    private TwoFactorVerificationToken(final String value) {
        super(value);
    }

    public static TwoFactorVerificationToken of(final String value) {
        return new TwoFactorVerificationToken(value);
    }
}
