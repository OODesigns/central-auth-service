package com.oodesigns.cas.domain.value;

/** Short-lived JWT usable only to complete an administrator-authorized account recovery. */
public final class RecoveryToken extends CompactToken {
    private RecoveryToken(final String value) {
        super(value);
    }

    public static RecoveryToken of(final String value) {
        return new RecoveryToken(value);
    }
}